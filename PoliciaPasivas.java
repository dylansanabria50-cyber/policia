package com.example.policia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Pasivas de cada oficio: funcionan solas mientras el oficio esta en modo
// activo (tecla B). No aparecen en el HUD; se explican en el libro.
//  Policia: Chaleco reglamentario, Autoridad, Recompensa.
//  Albanil: Manos de obra, Ahorro de materiales, Andamio seguro, Ojo de
//           minero (este ultimo se dibuja en el cliente, PoliciaXray).
//  Ladron:  Pies livianos, Bolsillos profundos, Mano rapida, Huida.
//  Herrero: Piel de hierro, Manos de forja, Durabilidad, Ojo de herrero.
public class PoliciaPasivas {
    static final UUID IRON_SKIN = UUID.fromString("5f2a8c4e-91b3-4d7a-8e21-3c6b9d0f1a72");
    // Herrero, Durabilidad: dano conocido de lo que lleva puesto/en mano.
    static final Map<UUID, Map<EquipmentSlot, int[]>> WEAR = new HashMap<UUID, Map<EquipmentSlot, int[]>>();
    static final Map<UUID, Map<EquipmentSlot, Item>> WEAR_ITEM = new HashMap<UUID, Map<EquipmentSlot, Item>>();
    // Herrero, Manos de forja: hornos cerca de cada herrero.
    static final Map<UUID, List<BlockPos>> FURNACES = new HashMap<UUID, List<BlockPos>>();

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaPasivas());
    }

    static boolean is(Player p, String job) {
        return p instanceof ServerPlayer && job.equals(PoliciaMod.job(p)) && PoliciaMod.on(p);
    }

    // ---------------- Danio recibido ----------------

    @SubscribeEvent
    public void hurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        // Policia - Chaleco reglamentario: -15% de flechas y proyectiles.
        if (PoliciaPasivas.is(p, "policia") && e.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
            e.setAmount(e.getAmount() * 0.85f);
        }
        // Herrero - Piel de hierro: -30% de fuego y quemaduras.
        if (PoliciaPasivas.is(p, "herrero") && e.getSource().is(DamageTypeTags.IS_FIRE)) {
            e.setAmount(e.getAmount() * 0.7f);
        }
    }

    // Ladron - Huida: con poca vida, velocidad unos segundos (enfriamiento 60 s).
    @SubscribeEvent
    public void damaged(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !PoliciaPasivas.is(p, "ladron")) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        if (d.getInt("pas_huida_cd") > 0 || p.getHealth() - e.getAmount() > 6.0f || p.getHealth() - e.getAmount() <= 0.0f) {
            return;
        }
        d.putInt("pas_huida_cd", 1200);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, true, true));
        p.serverLevel().sendParticles((ParticleOptions) ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
        PoliciaMod.msg(p, "Huida! Corre 5 s");
    }

    // Albanil - Andamio seguro: sin danio por caidas de hasta 6 bloques.
    @SubscribeEvent
    public void fall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && PoliciaPasivas.is(p, "albanil") && e.getDistance() <= 6.0f) {
            e.setDamageMultiplier(0.0f);
        }
    }

    // Ladron - Pies livianos: agachado, los monstruos te detectan a un
    // cuarto de la distancia normal.
    @SubscribeEvent
    public void visibility(LivingEvent.LivingVisibilityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.isCrouching() && PoliciaPasivas.is(p, "ladron")) {
            // Vanilla ya aplica 0.8 al estar agachado: 0.8 * 0.3125 = 0.25.
            e.modifyVisibility(0.3125);
        }
    }

    // ---------------- Bloques ----------------

    // Albanil - Manos de obra: piedra, ladrillos y tierra 20% mas rapido.
    @SubscribeEvent
    public void breakSpeed(PlayerEvent.BreakSpeed e) {
        Player p = e.getEntity();
        // Se calcula igual en servidor y cliente para que el picado no salte.
        boolean alb = p instanceof ServerPlayer ? PoliciaPasivas.is(p, "albanil") : p.level().isClientSide && PoliciaPasivas.clientAlb(p);
        if (!alb) {
            return;
        }
        BlockState st = e.getState();
        if (st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.STONE_BRICKS) || st.is(Blocks.BRICKS) || st.is(Blocks.COBBLESTONE) || st.is(BlockTags.DIRT) || st.is(Blocks.GRAVEL) || st.is(BlockTags.SAND)) {
            e.setNewSpeed(e.getNewSpeed() * 1.2f);
        }
    }

    // El cliente tambien calcula la velocidad de picado: usa el estado
    // sincronizado para que no "salte" el bloque.
    static boolean clientAlb(Player p) {
        try {
            return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() && PoliciaPasivas.ClientCheck.alb(p);
        } catch (Throwable t) {
            return false;
        }
    }

    static class ClientCheck {
        static boolean alb(Player p) {
            PoliciaMod.Sync s = PoliciaClient.STATE.get(p.getUUID());
            return s != null && s.jb == 2 && s.on;
        }
    }

    // Albanil - Ahorro de materiales: 10% de no gastar el bloque colocado.
    @SubscribeEvent
    public void placed(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !PoliciaPasivas.is(p, "albanil") || p.isCreative()) {
            return;
        }
        if (p.getRandom().nextInt(100) >= 10) {
            return;
        }
        Item it = e.getPlacedBlock().getBlock().asItem();
        if (it == Items.AIR) {
            return;
        }
        p.getServer().execute(() -> {
            if (!p.getInventory().add(new ItemStack((ItemLike) it))) {
                p.drop(new ItemStack((ItemLike) it), false);
            }
        });
    }

    // Herrero - Ojo de herrero: menas de hierro u oro a veces dan extra.
    @SubscribeEvent
    public void broke(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !PoliciaPasivas.is(p, "herrero") || e.isCanceled() || p.isCreative()) {
            return;
        }
        BlockState st = e.getState();
        boolean iron = st.is(BlockTags.IRON_ORES);
        boolean gold = st.is(BlockTags.GOLD_ORES);
        if (!iron && !gold) {
            return;
        }
        int r = p.getRandom().nextInt(100);
        ItemStack extra;
        if (r < 10) {
            extra = new ItemStack((ItemLike) (iron ? Items.IRON_INGOT : Items.GOLD_INGOT));
        } else if (r < 35) {
            extra = new ItemStack((ItemLike) (iron ? Items.IRON_NUGGET : Items.GOLD_NUGGET), 1 + p.getRandom().nextInt(3));
        } else {
            return;
        }
        BlockPos bp = e.getPos();
        ServerLevel sl = p.serverLevel();
        ItemEntity ie = new ItemEntity(sl, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, extra);
        ie.setDefaultPickUpDelay();
        sl.addFreshEntity(ie);
        sl.sendParticles((ParticleOptions) ParticleTypes.WAX_ON, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
    }

    // ---------------- Muertes ----------------

    @SubscribeEvent
    public void kill(LivingDeathEvent e) {
        // Policia - Recompensa: XP de oficio extra por monstruos cerca de una aldea.
        if (e.getSource().getEntity() instanceof ServerPlayer p && PoliciaPasivas.is(p, "policia") && e.getEntity() instanceof Enemy) {
            if (p.serverLevel().isVillage(e.getEntity().blockPosition()) || p.serverLevel().isCloseToVillage(e.getEntity().blockPosition(), 3)) {
                p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + 2);
                PoliciaMod.msg(p, "Recompensa: +2 XP por proteger la aldea");
                PoliciaMod.sync(p);
            }
        }
    }

    // Ladron - Bolsillos profundos: al morir se guarda hasta 3 objetos.
    @SubscribeEvent
    public void drops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !PoliciaPasivas.is(p, "ladron")) {
            return;
        }
        List<ItemEntity> all = new ArrayList<ItemEntity>(e.getDrops());
        ListTag kept = new ListTag();
        for (int i = 0; i < 3 && !all.isEmpty(); ++i) {
            ItemEntity ie = all.remove(p.getRandom().nextInt(all.size()));
            kept.add((Tag) ie.getItem().save(new CompoundTag()));
            e.getDrops().remove(ie);
        }
        if (!kept.isEmpty()) {
            p.getPersistentData().put("pas_kept", (Tag) kept);
        }
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.Clone e) {
        if (!e.isWasDeath()) {
            return;
        }
        CompoundTag o = e.getOriginal().getPersistentData();
        if (!o.contains("pas_kept")) {
            return;
        }
        Player n = e.getEntity();
        int c = 0;
        for (Tag t : o.getList("pas_kept", 10)) {
            ItemStack s = ItemStack.of((CompoundTag) t);
            if (s.isEmpty()) continue;
            if (!n.getInventory().add(s)) {
                n.drop(s, false);
            }
            ++c;
        }
        if (n instanceof ServerPlayer sp && c > 0) {
            PoliciaMod.msg(sp, "Bolsillos profundos: conservaste " + c + (c == 1 ? " objeto" : " objetos"));
        }
    }

    // ---------------- Cada tick ----------------

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        if (d.getInt("pas_huida_cd") > 0) {
            d.putInt("pas_huida_cd", d.getInt("pas_huida_cd") - 1);
        }
        boolean herrero = PoliciaPasivas.is(p, "herrero");
        // Herrero - Piel de hierro: +2 de armadura natural.
        AttributeInstance arm = p.getAttribute(Attributes.ARMOR);
        if (arm != null) {
            boolean has = arm.getModifier(IRON_SKIN) != null;
            if (herrero && !has) {
                arm.addTransientModifier(new AttributeModifier(IRON_SKIN, "pas_iron_skin", 2.0, AttributeModifier.Operation.ADDITION));
            } else if (!herrero && has) {
                arm.removeModifier(IRON_SKIN);
            }
        }
        if (herrero) {
            PoliciaPasivas.durability(p);
            PoliciaPasivas.forge(p);
        } else {
            WEAR.remove(p.getUUID());
            WEAR_ITEM.remove(p.getUUID());
            FURNACES.remove(p.getUUID());
        }
        // Policia - Autoridad: Heroe de la aldea I y los saqueadores se asustan.
        if (PoliciaPasivas.is(p, "policia")) {
            if (p.tickCount % 20 == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 60, 0, false, false, true));
                long now = p.level().getGameTime();
                for (Raider r : p.level().getEntitiesOfClass(Raider.class, p.getBoundingBox().inflate(10.0), x -> x.isAlive() && x.hasLineOfSight(p))) {
                    if (r.getPersistentData().getLong("pas_fear_until") > now - 200L) continue;
                    r.getPersistentData().putLong("pas_fear_until", now + 60L);
                    r.setTarget(null);
                    Vec3 away = r.position().subtract(p.position()).normalize().scale(8.0).add(r.position());
                    r.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
                    r.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, true, false));
                    p.serverLevel().sendParticles((ParticleOptions) ParticleTypes.ANGRY_VILLAGER, r.getX(), r.getY() + 2.1, r.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    // Herrero - Durabilidad: sus herramientas y armadura se gastan un 25% menos.
    static void durability(ServerPlayer p) {
        Map<EquipmentSlot, int[]> known = WEAR.computeIfAbsent(p.getUUID(), k -> new HashMap<EquipmentSlot, int[]>());
        Map<EquipmentSlot, Item> items = WEAR_ITEM.computeIfAbsent(p.getUUID(), k -> new HashMap<EquipmentSlot, Item>());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.isEmpty() || !s.isDamageableItem()) {
                known.remove(slot);
                items.remove(slot);
                continue;
            }
            int now = s.getDamageValue();
            int[] prev = known.get(slot);
            if (prev != null && items.get(slot) == s.getItem() && now > prev[0] && now - prev[0] <= 8) {
                int refund = 0;
                for (int i = 0; i < now - prev[0]; ++i) {
                    if (p.getRandom().nextInt(4) == 0) {
                        ++refund;
                    }
                }
                if (refund > 0) {
                    now -= refund;
                    s.setDamageValue(now);
                }
            }
            known.put(slot, new int[]{now});
            items.put(slot, s.getItem());
        }
    }

    // Herrero - Manos de forja: hornos a 6 bloques funden al doble.
    static void forge(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        if (p.tickCount % 40 == 0 || !FURNACES.containsKey(p.getUUID())) {
            List<BlockPos> found = new ArrayList<BlockPos>();
            BlockPos c = p.blockPosition();
            for (BlockPos bp : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 3, 6))) {
                if (sl.getBlockEntity(bp) instanceof AbstractFurnaceBlockEntity) {
                    found.add(bp.immutable());
                }
            }
            FURNACES.put(p.getUUID(), found);
        }
        for (BlockPos bp : FURNACES.get(p.getUUID())) {
            BlockEntity be = sl.getBlockEntity(bp);
            if (!(be instanceof AbstractFurnaceBlockEntity fb)) continue;
            BlockState st = sl.getBlockState(bp);
            if (!st.hasProperty(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT) || !st.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT)) continue;
            AbstractFurnaceBlockEntity.serverTick(sl, bp, st, fb);
            if (p.tickCount % 20 == 0) {
                sl.sendParticles((ParticleOptions) ParticleTypes.FLAME, bp.getX() + 0.5, bp.getY() + 1.05, bp.getZ() + 0.5, 2, 0.2, 0.0, 0.2, 0.01);
            }
        }
    }
}
