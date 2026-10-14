package com.example.policia;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

// Ladrón: mercado negro nocturno (un contrabandista que aparece de noche cerca
// del ladrón y vende cosas ilegales) y placa falsa (copiás la placa de un
// policía jugador y por 3 minutos los agentes no te persiguen y la policía no
// te rastrea; arriba de tu nombre aparece "Policía").
public class PoliciaMercado {
    public static final RegistryObject<Item> KIT_FALSO = PoliciaShield.ITEMS.register("kit_falsificacion", () -> new Forgery(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MAPA = PoliciaShield.ITEMS.register("mapa_boveda", () -> new VaultMap(new Item.Properties().stacksTo(4)));
    static final int FAKE_T = 3600;

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaMercado());
    }

    static boolean disguised(Player p) {
        return p.getPersistentData().getInt("fake_t") > 0;
    }

    static boolean night(ServerLevel sl) {
        long t = sl.getDayTime() % 24000L;
        return t >= 13000L && t <= 22500L;
    }

    // ---------------- Placa falsa ----------------
    public static class Forgery extends Item {
        public Forgery(Item.Properties p) {
            super(p);
        }

        @Override
        public boolean isFoil(ItemStack it) {
            return it.hasTag() && it.getTag().contains("cop");
        }

        @Override
        public void appendHoverText(ItemStack it, Level lv, List<Component> tip, TooltipFlag f) {
            if (it.hasTag() && it.getTag().contains("cop")) {
                tip.add(Component.literal("§bPlaca falsa de " + it.getTag().getString("cop")));
                tip.add(Component.literal("§7Clic derecho: disfrazarte de policía 3 minutos (un uso)"));
            } else {
                tip.add(Component.literal("§7Acercate por detrás a un policía (jugador) y hacé clic derecho sobre él"));
                tip.add(Component.literal("§7para copiar su placa sin que se dé cuenta"));
            }
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack it = player.getItemInHand(hand);
            if (level.isClientSide || !(player instanceof ServerPlayer p)) {
                return InteractionResultHolder.sidedSuccess(it, level.isClientSide);
            }
            if (!it.hasTag() || !it.getTag().contains("cop")) {
                PoliciaMod.msg(p, "Primero copiá la placa de un policía (clic derecho sobre él, por detrás)");
                return InteractionResultHolder.fail(it);
            }
            if (!"ladron".equals(PoliciaMod.job(p))) {
                return InteractionResultHolder.fail(it);
            }
            p.getPersistentData().putInt("fake_t", FAKE_T);
            p.getPersistentData().putString("fake_cop", it.getTag().getString("cop"));
            it.shrink(1);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0f, 1.0f);
            for (PoliciaBoss.BossNpc b : p.serverLevel().getEntitiesOfClass(PoliciaBoss.BossNpc.class, p.getBoundingBox().inflate(48.0), x -> x.kind() == 5 && x.getTarget() == p)) {
                b.setTarget(null);
            }
            p.sendSystemMessage(Component.literal("§bTe pusiste la placa falsa: 3 minutos los agentes te creen policía y no te rastrean."));
            PoliciaMod.sync(p);
            return InteractionResultHolder.success(it);
        }
    }

    @SubscribeEvent
    public void copy(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof ServerPlayer cop)) {
            return;
        }
        ItemStack it = p.getMainHandItem();
        if (!it.is(KIT_FALSO.get()) || !"ladron".equals(PoliciaMod.job(p)) || p.isShiftKeyDown()) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!"policia".equals(PoliciaMod.job(cop))) {
            PoliciaMod.msg(p, "Ese jugador no es policía");
            return;
        }
        if (it.hasTag() && it.getTag().contains("cop")) {
            PoliciaMod.msg(p, "Ya copiaste una placa");
            return;
        }
        // si el policía te está mirando de frente, se da cuenta
        net.minecraft.world.phys.Vec3 look = cop.getLookAngle();
        net.minecraft.world.phys.Vec3 to = p.position().subtract(cop.position()).normalize();
        if (look.x * to.x + look.z * to.z > 0.5) {
            PoliciaMod.msg(p, "§c¡Te vio! Acercate por detrás");
            PoliciaMod.msg(cop, "§c" + p.getName().getString() + " intentó copiar tu placa");
            PoliciaLadron.crime(p, 1, "intento de falsificación");
            return;
        }
        it.getOrCreateTag().putString("cop", cop.getName().getString());
        it.setHoverName(Component.literal("Placa falsa (" + cop.getName().getString() + ")"));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6f, 1.6f);
        PoliciaMod.msg(p, "§aCopiaste la placa de " + cop.getName().getString());
    }

    // ---------------- Mapa de bóveda ----------------
    public static class VaultMap extends Item {
        public VaultMap(Item.Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack it, Level lv, List<Component> tip, TooltipFlag f) {
            tip.add(Component.literal("§7Clic derecho: borra la espera para el próximo golpe grande"));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack it = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer p) {
                p.getPersistentData().putLong("gh_cd", 0L);
                it.shrink(1);
                PoliciaMod.msg(p, "Estudiaste el mapa: ya podés pedir otro golpe grande");
            }
            return InteractionResultHolder.sidedSuccess(it, level.isClientSide);
        }
    }

    // ---------------- Mercado negro nocturno ----------------
    static MerchantOffers offers() {
        MerchantOffers o = new MerchantOffers();
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 5), PoliciaBoss.single("l_ganzua", "Ganzúa", "60 s para forzar puertas de hierro y cofres"), 3, 2, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 6), PoliciaBoss.single("l_humo", "Humo", "Invisible y rápido 8 s, los enemigos te pierden"), 3, 2, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 5), PoliciaBoss.single("l_carterismo", "Carterismo maestro", "Tu próximo carterismo en 60 s no falla"), 2, 2, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 8), new ItemStack(MAPA.get()), 1, 3, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 10), new ItemStack(KIT_FALSO.get()), 1, 3, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 4), new ItemStack(Items.ENDER_PEARL, 2), 4, 1, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 7), PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.LONG_INVISIBILITY), 2, 2, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 3), new ItemStack(Items.TNT, 2), 3, 1, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.GOLD_INGOT, 6), new ItemStack(Items.EMERALD, 3), 8, 1, 0.05f));
        return o;
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        int ft = d.getInt("fake_t");
        if (ft > 0) {
            d.putInt("fake_t", --ft);
            if (ft == 0) {
                PoliciaMod.msg(p, "§7Se te cayó la placa falsa: ya no te creen policía");
                PoliciaMod.sync(p);
            } else if (ft % 1200 == 0) {
                PoliciaMod.msg(p, "§bPlaca falsa: " + ft / 1200 + " min");
            }
        }
        if (p.tickCount % 200 != 0 || !"ladron".equals(PoliciaMod.job(p)) || !PoliciaMod.on(p) || p.level().dimension() != Level.OVERWORLD) {
            return;
        }
        ServerLevel sl = p.serverLevel();
        long night = sl.getDayTime() / 24000L;
        if (!PoliciaMercado.night(sl) || d.getLong("bm_night") == night + 1L) {
            return;
        }
        d.putLong("bm_night", night + 1L);
        double a = p.getRandom().nextDouble() * Math.PI * 2.0;
        BlockPos at = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos((int) (p.getX() + Math.cos(a) * 18), 0, (int) (p.getZ() + Math.sin(a) * 18)));
        WanderingTrader t = EntityType.WANDERING_TRADER.create(sl);
        if (t == null) return;
        t.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        t.setCustomName(Component.literal("Contrabandista"));
        t.setCustomNameVisible(true);
        long left = 23000L - sl.getDayTime() % 24000L;
        t.setDespawnDelay((int) Math.max(1200L, left));
        t.getPersistentData().putBoolean("bm", true);
        t.getOffers().clear();
        t.getOffers().addAll(PoliciaMercado.offers());
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, false));
        sl.addFreshEntity(t);
        sl.playSound(null, at, SoundEvents.WANDERING_TRADER_AMBIENT, SoundSource.NEUTRAL, 1.0f, 0.7f);
        p.sendSystemMessage(Component.literal("§8[Mercado negro] §7Un contrabandista se instaló cerca de vos esta noche. Se va al amanecer."));
    }

    // El contrabandista solo le vende a ladrones.
    @SubscribeEvent
    public void trade(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || !(e.getTarget() instanceof WanderingTrader t) || !t.getPersistentData().getBoolean("bm") || !(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        if (!"ladron".equals(PoliciaMod.job(p))) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            PoliciaMod.msg(p, "<Contrabandista> No sé de qué me hablás...");
            if ("policia".equals(PoliciaMod.job(p))) {
                PoliciaMod.msg(p, "§7(Huele a contrabando. Pero sin pruebas no podés hacer nada.)");
            }
        }
    }
}
