package com.example.policia;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

// Etapa 7: rarezas, minijuego de forja y anillos.
// - Rareza (NBT "pol_rareza" 0-4) en armas, herramientas y armaduras: sale
//   al fabricarlas (mejor con Destreza y si sos herrero) o en el minijuego
//   de forja. Suma dano o armadura y se ve en el nombre y el tooltip.
// - Forja: agachado + clic derecho en un yunque con un lingote en la mano y
//   el objeto en la otra mano. Calentar, martillar 5 veces y templar.
// - Anillo: clic derecho para ponerlo en su casillero (se ve en la ficha).
public class PoliciaRareza {
    public static final String[] NOMBRE = {"Común", "Poco común", "Raro", "Épico", "Legendario"};
    public static final ChatFormatting[] COLOR = {ChatFormatting.WHITE, ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD};
    public static final int[] ARGB = {0xFFFFFFFF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF55FF, 0xFFFFAA00};
    static final float[] DANO = {0.0f, 0.5f, 1.0f, 2.0f, 3.0f};
    static final float[] ARMADURA = {0.0f, 0.5f, 1.0f, 1.5f, 2.0f};
    static final UUID MOD_DANO = UUID.fromString("3e8f1b2a-6c4d-4a1e-9b7f-0c2d4e6f8a01");
    static final UUID[] MOD_ARM = {
            UUID.fromString("3e8f1b2a-6c4d-4a1e-9b7f-0c2d4e6f8a10"), UUID.fromString("3e8f1b2a-6c4d-4a1e-9b7f-0c2d4e6f8a11"),
            UUID.fromString("3e8f1b2a-6c4d-4a1e-9b7f-0c2d4e6f8a12"), UUID.fromString("3e8f1b2a-6c4d-4a1e-9b7f-0c2d4e6f8a13")};

    // ---------------- Anillos ----------------
    static final UUID MOD_ANILLO = UUID.fromString("3e8f1b2a-6c4d-4a1e-9b7f-0c2d4e6f8a20");
    public static final RegistryObject<Item> ANILLO_VIDA = PoliciaShield.ITEMS.register("anillo_vida", () -> new Anillo(0));
    public static final RegistryObject<Item> ANILLO_FUERZA = PoliciaShield.ITEMS.register("anillo_fuerza", () -> new Anillo(1));
    public static final RegistryObject<Item> ANILLO_VELOCIDAD = PoliciaShield.ITEMS.register("anillo_velocidad", () -> new Anillo(2));
    public static final RegistryObject<Item> ANILLO_SUERTE = PoliciaShield.ITEMS.register("anillo_suerte", () -> new Anillo(3));
    static final String[] EFECTO_ANILLO = {"+4 de vida máxima", "+1,5 de daño", "+8 % de velocidad", "+2 de suerte y +10 % de XP"};

    // Sesiones de forja abiertas: jugador -> {tiempo de inicio, mano del lingote}.
    static final Map<UUID, Long> FORJA = new HashMap<UUID, Long>();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Eventos());
    }

    public static class Anillo extends Item {
        final int tipo;

        Anillo(int tipo) {
            super(new Item.Properties().stacksTo(1));
            this.tipo = tipo;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            ItemStack st = p.getItemInHand(hand);
            if (!level.isClientSide && p instanceof ServerPlayer sp) {
                CompoundTag d = PoliciaRPG.datos(sp);
                ItemStack viejo = d.contains("anillo") ? ItemStack.of(d.getCompound("anillo")) : ItemStack.EMPTY;
                d.put("anillo", st.copyWithCount(1).save(new CompoundTag()));
                p.setItemInHand(hand, viejo);
                aplicarAnillo(sp);
                PoliciaRPG.sync(sp);
                level.playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD, SoundSource.PLAYERS, 1.0f, 1.3f);
                PoliciaMod.msg(sp, "Te pusiste el " + st.getHoverName().getString() + " (" + EFECTO_ANILLO[this.tipo] + ")");
            }
            return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack st, Level level, java.util.List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
            tip.add(Component.literal("§7Con el anillo puesto: §a" + EFECTO_ANILLO[this.tipo]));
            tip.add(Component.literal("§8Clic derecho para ponértelo (se ve en la ficha, tecla I)"));
        }

        @Override
        public boolean isFoil(ItemStack st) {
            return true;
        }
    }

    static int tipoAnillo(Player p) {
        CompoundTag d = PoliciaRPG.datos(p);
        if (!d.contains("anillo")) {
            return -1;
        }
        ItemStack st = ItemStack.of(d.getCompound("anillo"));
        return st.getItem() instanceof Anillo a ? a.tipo : -1;
    }

    static void poner(Player p, Attribute at, double v, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(at);
        if (inst != null) {
            inst.removeModifier(MOD_ANILLO);
            if (v != 0) {
                inst.addPermanentModifier(new AttributeModifier(MOD_ANILLO, "anillo", v, op));
            }
        }
    }

    static void aplicarAnillo(ServerPlayer p) {
        int t = tipoAnillo(p);
        for (Attribute at : new Attribute[]{Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.LUCK}) {
            AttributeInstance inst = p.getAttribute(at);
            if (inst != null) {
                inst.removeModifier(MOD_ANILLO);
            }
        }
        switch (t) {
            case 0 -> poner(p, Attributes.MAX_HEALTH, 4.0, AttributeModifier.Operation.ADDITION);
            case 1 -> poner(p, Attributes.ATTACK_DAMAGE, 1.5, AttributeModifier.Operation.ADDITION);
            case 2 -> poner(p, Attributes.MOVEMENT_SPEED, 0.08, AttributeModifier.Operation.MULTIPLY_BASE);
            case 3 -> poner(p, Attributes.LUCK, 2.0, AttributeModifier.Operation.ADDITION);
            default -> {
            }
        }
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    static void quitarAnillo(ServerPlayer p) {
        CompoundTag d = PoliciaRPG.datos(p);
        if (!d.contains("anillo")) {
            return;
        }
        ItemStack st = ItemStack.of(d.getCompound("anillo"));
        d.remove("anillo");
        if (!p.getInventory().add(st)) {
            p.drop(st, false);
        }
        aplicarAnillo(p);
        PoliciaRPG.sync(p);
    }

    // ---------------- Rareza ----------------
    static boolean equipo(ItemStack st) {
        Item it = st.getItem();
        return !st.isEmpty() && st.isDamageableItem() && (it instanceof SwordItem || it instanceof DiggerItem || it instanceof ArmorItem
                || it instanceof BowItem || it instanceof CrossbowItem || it instanceof TridentItem || it instanceof ShieldItem);
    }

    public static int rareza(ItemStack st) {
        return st.hasTag() ? Math.max(0, Math.min(4, st.getTag().getInt("pol_rareza"))) : 0;
    }

    static void ponerRareza(ItemStack st, int r) {
        st.getOrCreateTag().putInt("pol_rareza", Math.max(0, Math.min(4, r)));
    }

    // Tirada al fabricar: base 70/20/8/2 (sin legendario); Destreza y ser
    // herrero mejoran la suerte.
    static int tirar(ServerPlayer p) {
        int des = Math.max(0, PoliciaRPG.atributo(p, "destreza") - 5);
        boolean herrero = "herrero".equals(PoliciaMod.job(p));
        double bono = 1.0 + des * 0.04 + (herrero ? 1.0 : 0.0);
        double r = p.getRandom().nextDouble() * 100.0;
        if (herrero && r < 0.5 * bono) {
            return 4;
        }
        if (r < 2 * bono) {
            return 3;
        }
        if (r < 10 * bono) {
            return 2;
        }
        if (r < 30 * bono) {
            return 1;
        }
        return 0;
    }

    static MutableComponent etiqueta(int r) {
        String estrellas = "★".repeat(r + 1) + "☆".repeat(4 - r);
        return Component.literal(estrellas + " " + NOMBRE[r]).withStyle(COLOR[r]);
    }

    // ---------------- Forja (servidor) ----------------
    static boolean lingote(ItemStack st) {
        return st.is(Items.IRON_INGOT) || st.is(Items.GOLD_INGOT) || st.is(Items.NETHERITE_INGOT) || st.is(Items.COPPER_INGOT) || st.is(Items.DIAMOND);
    }

    // El cliente manda la puntuacion (0-100). Se valida: tiene que haber una
    // sesion abierta hace al menos 5 s (el minijuego dura mas que eso).
    static void resultado(ServerPlayer p, int puntos) {
        Long inicio = FORJA.remove(p.getUUID());
        if (inicio == null) {
            return;
        }
        long dur = p.level().getGameTime() - inicio;
        if (dur < 100 || dur > 20 * 120) {
            PoliciaMod.msg(p, "La pieza se enfrió: la forja no salió");
            return;
        }
        ItemStack obj = p.getOffhandItem();
        if (!equipo(obj)) {
            PoliciaMod.msg(p, "Necesitás el objeto a forjar en la otra mano");
            return;
        }
        puntos = Math.max(0, Math.min(100, puntos));
        int r = puntos >= 93 ? 4 : puntos >= 80 ? 3 : puntos >= 60 ? 2 : puntos >= 40 ? 1 : 0;
        if (r == 4 && !"herrero".equals(PoliciaMod.job(p))) {
            r = 3;
        }
        int antes = rareza(obj);
        if (r > antes) {
            ponerRareza(obj, r);
        }
        obj.setDamageValue(Math.max(0, obj.getDamageValue() - obj.getMaxDamage() / 4));
        p.serverLevel().sendParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.3, 0.3, 0);
        p.serverLevel().playSound(null, p.blockPosition(), r >= 3 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        String txt = "§6Forja: §f" + puntos + " puntos → ";
        p.displayClientMessage(Component.literal(txt).append(etiqueta(Math.max(r, antes))).append(Component.literal(r > antes ? " §a(¡mejoró!)" : " §7(se quedó como estaba, pero se reparó)")), false);
        PoliciaNivel.dar(p, 10 + puntos / 5);
        if (r == 4) {
            PoliciaNivel.logro(p, "legendario");
        }
    }

    public static class Eventos {
        @SubscribeEvent
        public void fabricar(PlayerEvent.ItemCraftedEvent e) {
            if (e.getEntity() instanceof ServerPlayer p && equipo(e.getCrafting()) && !e.getCrafting().hasTag()) {
                int r = tirar(p);
                if (r > 0) {
                    ponerRareza(e.getCrafting(), r);
                    p.displayClientMessage(Component.literal("§7Te salió ").append(etiqueta(r)).append(Component.literal("§7: " + e.getCrafting().getHoverName().getString())), true);
                    if (r == 4) {
                        PoliciaNivel.logro(p, "legendario");
                    }
                }
            }
        }

        @SubscribeEvent
        public void atributos(ItemAttributeModifierEvent e) {
            ItemStack st = e.getItemStack();
            int r = rareza(st);
            if (r <= 0) {
                return;
            }
            Item it = st.getItem();
            if (it instanceof ArmorItem ai) {
                if (e.getSlotType() == ai.getEquipmentSlot()) {
                    int idx = ai.getEquipmentSlot().getIndex();
                    e.addModifier(Attributes.ARMOR, new AttributeModifier(MOD_ARM[idx & 3], "rareza", ARMADURA[r], AttributeModifier.Operation.ADDITION));
                }
            } else if ((it instanceof SwordItem || it instanceof DiggerItem || it instanceof TridentItem) && e.getSlotType() == EquipmentSlot.MAINHAND) {
                e.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(MOD_DANO, "rareza", DANO[r], AttributeModifier.Operation.ADDITION));
            }
        }

        // Agachado + clic derecho a un yunque con un lingote en la mano
        // principal y el objeto en la otra: abre el minijuego (gasta el lingote).
        @SubscribeEvent
        public void yunque(PlayerInteractEvent.RightClickBlock e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown() || e.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            if (!(p.level().getBlockState(e.getPos()).getBlock() instanceof AnvilBlock)) {
                return;
            }
            ItemStack mano = p.getMainHandItem();
            if (!lingote(mano)) {
                return;
            }
            e.setCanceled(true);
            if (!equipo(p.getOffhandItem())) {
                PoliciaMod.msg(p, "Para forjar: lingote en la mano y el arma, herramienta o armadura en la otra mano");
                return;
            }
            if (!p.isCreative()) {
                mano.shrink(1);
            }
            FORJA.put(p.getUUID(), p.level().getGameTime());
            PoliciaRed.toPlayer(p, new PoliciaRed.ForjaAbrir(rareza(p.getOffhandItem())));
            p.serverLevel().playSound(null, e.getPos(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 1.4f);
        }

        @SubscribeEvent
        public void entrar(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                aplicarAnillo(p);
            }
        }

        @SubscribeEvent
        public void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                aplicarAnillo(p);
            }
        }
    }
}
