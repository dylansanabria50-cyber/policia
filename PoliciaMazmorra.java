package com.example.policia;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 9: mazmorra "Cueva del Eco". Se arma bajo tierra a unos 400 bloques
// del spawn la primera vez que alguien se acerca. Tres salas: dos con
// monstruos y la del jefe, el Guardian del Eco, con dos fases (a la mitad de
// vida entra en furia: mas rapido, salta y golpea el piso con una onda, y a
// un cuarto llama a sus ecos). Al morir deja un cofre con botin. Vuelve a
// aparecer a los 3 dias. Se revela al llegar al nivel 10 (parada de carruaje).
public class PoliciaMazmorra {
    static ServerBossEvent barra;
    static UUID jefe;
    static final int NIVEL = 20;

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Eventos());
    }

    static CompoundTag estado(MinecraftServer s) {
        CompoundTag t = PoliciaMundo.get(s).getSeccion("mazmorra");
        if (!t.contains("x")) {
            ServerLevel ow = s.overworld();
            BlockPos sp = ow.getSharedSpawnPos();
            double a = new java.util.Random(ow.getSeed() ^ 0xEC0L).nextDouble() * Math.PI * 2;
            int x = sp.getX() + (int) (Math.cos(a) * 400);
            int z = sp.getZ() + (int) (Math.sin(a) * 400);
            t.putInt("x", x);
            t.putInt("z", z);
            t.putInt("y", -999);
            PoliciaMundo.get(s).setDirty();
        }
        return t;
    }

    static boolean cerca(CompoundTag t, BlockPos p, int r) {
        int dx = p.getX() - (t.getInt("x") + 40);
        int dz = p.getZ() - t.getInt("z");
        return Math.abs(dx) <= r && Math.abs(dz) <= r;
    }

    // Lo usa PoliciaZonas: la cueva es zona de nivel 20.
    static boolean enCueva(MinecraftServer s, BlockPos p) {
        CompoundTag t = PoliciaMundo.get(s).getSeccion("mazmorra");
        return t.contains("x") && t.getBoolean("hecha") && cerca(t, p, 60) && p.getY() < t.getInt("y") + 4;
    }

    static void revelar(ServerPlayer p) {
        CompoundTag t = estado(p.server);
        CompoundTag par = PoliciaViaje.paradas(p);
        if (par.contains("cueva")) {
            return;
        }
        CompoundTag c = new CompoundTag();
        c.putString("n", "Cueva del Eco");
        c.putInt("x", t.getInt("x"));
        c.putInt("y", t.getInt("y") == -999 ? 80 : t.getInt("y"));
        c.putInt("z", t.getInt("z"));
        par.put("cueva", c);
        p.displayClientMessage(Component.literal("§5§lCueva del Eco §7— dicen que bajo tierra, cerca de §f" + t.getInt("x") + ", " + t.getInt("z") + "§7, duerme un guardián. Es nivel " + NIVEL + ". §8(Ya está en tu mapa de viaje)"), false);
        PoliciaRPG.sync(p);
    }

    // ---------------- Construccion ----------------
    static BlockState ladrillo(java.util.Random r) {
        int k = r.nextInt(10);
        return k < 2 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : k < 4 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : k < 5 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
    }

    static void caja(ServerLevel sl, java.util.Random r, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    boolean borde = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                    BlockPos q = new BlockPos(x, y, z);
                    if (borde) {
                        if (!sl.getBlockState(q).isAir() || y == y0 || y == y1) {
                            sl.setBlock(q, ladrillo(r), 2);
                        }
                    } else {
                        sl.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    // Farol colgado del techo que haya arriba de q.
    static void farol(ServerLevel sl, BlockPos q) {
        for (int i = 0; i < 10; i++) {
            BlockPos u = q.above(i);
            if (!sl.getBlockState(u.above()).isAir()) {
                if (sl.getBlockState(u).isAir()) {
                    sl.setBlock(u, Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 2);
                }
                return;
            }
        }
    }

    static void construir(ServerLevel sl, CompoundTag t) {
        java.util.Random r = new java.util.Random(sl.getSeed());
        int ex = t.getInt("x");
        int ez = t.getInt("z");
        int ey = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ex, ez);
        int y0 = ey - 18;
        // escalera de bajada
        for (int i = 0; i <= 18; i++) {
            int yy = ey - i;
            caja(sl, r, ex + i - 1, yy - 1, ez - 2, ex + i + 1, yy + 4, ez + 2);
        }
        for (int i = 0; i <= 18; i++) {
            int yy = ey - i;
            for (int y = 0; y <= 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    sl.setBlock(new BlockPos(ex + i, yy + y, ez + z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            for (int z = -1; z <= 1; z++) {
                sl.setBlock(new BlockPos(ex + i, yy - 1, ez + z), Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.WEST), 2);
            }
            sl.setBlock(new BlockPos(ex + i, yy - 1, ez), Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.WEST), 2);
            if (i % 5 == 2) {
                farol(sl, new BlockPos(ex + i, yy + 3, ez - 1));
            }
        }
        // arco de entrada
        for (int y = 0; y < 5; y++) {
            sl.setBlock(new BlockPos(ex - 1, ey + y, ez - 2), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
            sl.setBlock(new BlockPos(ex - 1, ey + y, ez + 2), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        }
        for (int z = -2; z <= 2; z++) {
            sl.setBlock(new BlockPos(ex - 1, ey + 5, ez + z), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 2);
        }
        sl.setBlock(new BlockPos(ex - 1, ey + 6, ez), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
        // sala 1, pasillo, sala 2, pasillo, sala del jefe
        caja(sl, r, ex + 17, y0 - 1, ez - 6, ex + 29, y0 + 5, ez + 6);
        caja(sl, r, ex + 29, y0 - 1, ez - 2, ex + 33, y0 + 4, ez + 2);
        caja(sl, r, ex + 33, y0 - 1, ez - 7, ex + 47, y0 + 6, ez + 7);
        caja(sl, r, ex + 47, y0 - 1, ez - 2, ex + 51, y0 + 4, ez + 2);
        caja(sl, r, ex + 51, y0 - 1, ez - 10, ex + 71, y0 + 10, ez + 10);
        // aberturas entre salas
        for (int[] pz : new int[][]{{29}, {33}, {47}, {51}}) {
            for (int y = 0; y <= 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    sl.setBlock(new BlockPos(ex + pz[0], y0 + y, ez + z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int[] l : new int[][]{{19, -5}, {27, 5}, {35, -6}, {45, 6}, {53, -9}, {69, 9}, {53, 9}, {69, -9}}) {
            farol(sl, new BlockPos(ex + l[0], y0 + 3, ez + l[1]));
        }
        // columnas y tumba en la sala del jefe
        for (int[] c : new int[][]{{55, -6}, {55, 6}, {67, -6}, {67, 6}}) {
            for (int y = 0; y < 10; y++) {
                sl.setBlock(new BlockPos(ex + c[0], y0 + y, ez + c[1]), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                sl.setBlock(new BlockPos(ex + 61 + x, y0 - 1, ez + z), (x + z) % 2 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState(), 2);
            }
        }
        // cofrecito en la sala 2
        BlockPos cofre = new BlockPos(ex + 46, y0, ez - 6);
        sl.setBlock(cofre, Blocks.CHEST.defaultBlockState(), 2);
        if (sl.getBlockEntity(cofre) instanceof ChestBlockEntity ch) {
            ch.setItem(4, new ItemStack(Items.GOLDEN_APPLE, 2));
            ch.setItem(11, new ItemStack(Items.ARROW, 24));
            ch.setItem(13, new ItemStack(Items.EMERALD, 6));
            ch.setItem(15, new ItemStack(Items.IRON_INGOT, 8));
        }
        t.putInt("y", y0);
        t.putInt("ey", ey);
        t.putBoolean("hecha", true);
        PoliciaMundo.get(sl.getServer()).setDirty();
    }

    // ---------------- Monstruos y jefe ----------------
    static void oleada(ServerLevel sl, double cx, double cy, double cz, EntityType<? extends Mob>[] tipos, int n) {
        for (int i = 0; i < n; i++) {
            Mob m = tipos[i % tipos.length].create(sl);
            if (m == null) {
                continue;
            }
            m.moveTo(cx + (sl.random.nextDouble() - 0.5) * 6, cy, cz + (sl.random.nextDouble() - 0.5) * 6, sl.random.nextFloat() * 360, 0);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.STRUCTURE, null, null);
            m.setPersistenceRequired();
            sl.addFreshEntity(m);
            sl.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 1, m.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
        }
    }

    static void invocarJefe(ServerLevel sl, CompoundTag t) {
        WitherSkeleton w = EntityType.WITHER_SKELETON.create(sl);
        if (w == null) {
            return;
        }
        int ex = t.getInt("x");
        int ez = t.getInt("z");
        int y0 = t.getInt("y");
        w.moveTo(ex + 61.5, y0, ez + 0.5, 270, 0);
        w.setCustomName(Component.literal("§5Guardián del Eco"));
        w.setCustomNameVisible(true);
        w.setPersistenceRequired();
        w.getAttribute(Attributes.MAX_HEALTH).setBaseValue(320.0);
        w.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(11.0);
        w.getAttribute(Attributes.ARMOR).setBaseValue(12.0);
        w.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40.0);
        w.setHealth(320.0f);
        ItemStack hacha = new ItemStack(Items.NETHERITE_AXE);
        w.setItemSlot(EquipmentSlot.MAINHAND, hacha);
        w.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        w.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        w.setDropChance(EquipmentSlot.HEAD, 0.0f);
        w.getPersistentData().putBoolean("pol_jefe_eco", true);
        w.getPersistentData().putBoolean("pol_zona", true);
        sl.addFreshEntity(w);
        jefe = w.getUUID();
        sl.playSound(null, w.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.6f);
        sl.sendParticles(ParticleTypes.SCULK_SOUL, w.getX(), w.getY() + 1, w.getZ(), 60, 1.0, 1.5, 1.0, 0.05);
        if (barra == null) {
            barra = new ServerBossEvent(Component.literal("Guardián del Eco"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        barra.setName(Component.literal("Guardián del Eco"));
        barra.setColor(BossEvent.BossBarColor.PURPLE);
        barra.setProgress(1.0f);
        t.putBoolean("fase2", false);
        t.putBoolean("ecos", false);
        PoliciaHab.tarea(k -> PoliciaMazmorra.pelea(sl, w, t, k));
    }

    static boolean pelea(ServerLevel sl, WitherSkeleton w, CompoundTag t, int k) {
        if (!w.isAlive()) {
            if (barra != null) {
                barra.removeAllPlayers();
            }
            return true;
        }
        Vec3 c = w.position();
        float pv = w.getHealth() / w.getMaxHealth();
        if (barra != null) {
            barra.setProgress(pv);
            if (k % 20 == 0) {
                for (ServerPlayer p : sl.players()) {
                    if (p.position().distanceTo(c) < 40) {
                        barra.addPlayer(p);
                    } else {
                        barra.removePlayer(p);
                    }
                }
            }
        }
        if (!t.getBoolean("fase2") && pv <= 0.5f) {
            t.putBoolean("fase2", true);
            w.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1));
            w.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 0));
            if (barra != null) {
                barra.setColor(BossEvent.BossBarColor.RED);
                barra.setName(Component.literal("Guardián del Eco — ¡Furia!"));
            }
            sl.playSound(null, w.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.6f);
            sl.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 1, c.z, 80, 1.2, 1.5, 1.2, 0.08);
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(30))) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§c¡FURIA!")));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7El guardián salta y golpea el piso: alejate de la onda")));
            }
        }
        if (!t.getBoolean("ecos") && pv <= 0.25f) {
            t.putBoolean("ecos", true);
            @SuppressWarnings("unchecked")
            EntityType<? extends Mob>[] ecos = new EntityType[]{EntityType.VEX, EntityType.SKELETON};
            oleada(sl, c.x, c.y, c.z, ecos, 4);
            sl.playSound(null, w.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.0f, 0.7f);
        }
        // Fase 2: cada 6 s salta y al caer manda una onda por el piso.
        if (t.getBoolean("fase2") && k % 120 == 60) {
            w.setDeltaMovement(w.getDeltaMovement().x, 1.0, w.getDeltaMovement().z);
            w.hurtMarked = true;
            PoliciaHab.tarea(j -> {
                if (!w.isAlive()) {
                    return true;
                }
                if (j > 6 && w.onGround() || j > 40) {
                    Vec3 q = w.position();
                    BlockParticleOption piedra = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState());
                    for (int i = 0; i < 48; i++) {
                        double a = i * Math.PI / 24;
                        for (double r = 1.5; r <= 6; r += 1.5) {
                            sl.sendParticles(piedra, q.x + Math.cos(a) * r, q.y + 0.1, q.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.05);
                        }
                    }
                    sl.playSound(null, w.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.6f);
                    for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(6, 2, 6), e -> e instanceof ServerPlayer)) {
                        if (e.onGround()) {
                            e.hurt(w.damageSources().mobAttack(w), 7.0f);
                            Vec3 d = e.position().subtract(q).normalize().scale(1.2);
                            e.setDeltaMovement(d.x, 0.5, d.z);
                            e.hurtMarked = true;
                        }
                    }
                    return true;
                }
                return false;
            });
        }
        return false;
    }

    static void botin(ServerLevel sl, CompoundTag t) {
        BlockPos c = new BlockPos(t.getInt("x") + 61, t.getInt("y"), t.getInt("z"));
        sl.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.WEST), 3);
        if (sl.getBlockEntity(c) instanceof ChestBlockEntity ch) {
            java.util.Random r = new java.util.Random();
            ch.setItem(10, new ItemStack(Items.DIAMOND, 3 + r.nextInt(4)));
            ch.setItem(12, new ItemStack(Items.EMERALD, 16 + r.nextInt(16)));
            ch.setItem(14, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1));
            ch.setItem(16, new ItemStack(Items.EXPERIENCE_BOTTLE, 16));
            ItemStack[] eq = {new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.BOW)};
            ItemStack e = eq[r.nextInt(eq.length)];
            PoliciaRareza.ponerRareza(e, r.nextInt(4) == 0 ? 4 : 3);
            ch.setItem(4, e);
            ItemStack[] an = {new ItemStack(PoliciaRareza.ANILLO_VIDA.get()), new ItemStack(PoliciaRareza.ANILLO_FUERZA.get()), new ItemStack(PoliciaRareza.ANILLO_VELOCIDAD.get()), new ItemStack(PoliciaRareza.ANILLO_SUERTE.get())};
            ch.setItem(22, an[r.nextInt(an.length)]);
        }
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 80, 0.6, 0.8, 0.6, 0.4);
    }

    public static class Eventos {
        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 15) {
                return;
            }
            ServerLevel sl = p.serverLevel();
            if (sl.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                return;
            }
            if (p.tickCount % 200 == 15 && PoliciaRPG.nivel(p) >= 10) {
                revelar(p);
            }
            CompoundTag t = PoliciaMundo.get(p.server).getSeccion("mazmorra");
            if (!t.contains("x") || !cerca(t, p.blockPosition(), 64)) {
                return;
            }
            if (!t.getBoolean("hecha")) {
                construir(sl, t);
                p.displayClientMessage(Component.literal("§5Sentís un eco que viene de abajo de la tierra..."), true);
                return;
            }
            int ex = t.getInt("x");
            int ez = t.getInt("z");
            int y0 = t.getInt("y");
            if (p.getY() > y0 + 8) {
                return;
            }
            double rx = p.getX() - ex;
            double rz = p.getZ() - ez;
            long dia = PoliciaMundo.get(p.server).dia(p.server);
            if (rx > 18 && rx < 29 && Math.abs(rz) < 6 && !t.getBoolean("r1h")) {
                t.putBoolean("r1h", true);
                @SuppressWarnings("unchecked")
                EntityType<? extends Mob>[] tipos = new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK};
                oleada(sl, ex + 23, y0, ez, tipos, 5);
                p.displayClientMessage(Component.literal("§7Sala de los Susurros"), true);
            }
            if (rx > 34 && rx < 47 && Math.abs(rz) < 7 && !t.getBoolean("r2h")) {
                t.putBoolean("r2h", true);
                @SuppressWarnings("unchecked")
                EntityType<? extends Mob>[] tipos = new EntityType[]{EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.STRAY};
                oleada(sl, ex + 40, y0, ez, tipos, 6);
                p.displayClientMessage(Component.literal("§7Galería de las Telarañas"), true);
            }
            if (rx > 52 && rx < 71 && Math.abs(rz) < 10) {
                boolean muerto = t.getBoolean("muerto");
                if (muerto && dia >= t.getLong("dia_muerte") + 3) {
                    t.putBoolean("muerto", false);
                    t.putBoolean("r1h", false);
                    t.putBoolean("r2h", false);
                    muerto = false;
                }
                boolean vivo = jefe != null && sl.getEntity(jefe) instanceof WitherSkeleton ws && ws.isAlive();
                if (!muerto && !vivo) {
                    List<WitherSkeleton> l = sl.getEntitiesOfClass(WitherSkeleton.class, new AABB(ex + 51, y0 - 1, ez - 10, ex + 71, y0 + 10, ez + 10), w -> w.getPersistentData().getBoolean("pol_jefe_eco"));
                    if (l.isEmpty()) {
                        invocarJefe(sl, t);
                        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
                        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§5Guardián del Eco")));
                        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7Nivel " + NIVEL + " · Cueva del Eco")));
                    } else {
                        jefe = l.get(0).getUUID();
                        WitherSkeleton w = l.get(0);
                        PoliciaHab.tarea(k -> PoliciaMazmorra.pelea(sl, w, t, k));
                        if (barra == null) {
                            barra = new ServerBossEvent(Component.literal("Guardián del Eco"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public void muerte(LivingDeathEvent e) {
            if (!(e.getEntity() instanceof WitherSkeleton w) || !w.getPersistentData().getBoolean("pol_jefe_eco") || !(w.level() instanceof ServerLevel sl)) {
                return;
            }
            CompoundTag t = PoliciaMundo.get(sl.getServer()).getSeccion("mazmorra");
            t.putBoolean("muerto", true);
            t.putLong("dia_muerte", PoliciaMundo.get(sl.getServer()).dia(sl.getServer()));
            PoliciaMundo.get(sl.getServer()).setDirty();
            jefe = null;
            if (barra != null) {
                barra.removeAllPlayers();
            }
            botin(sl, t);
            sl.playSound(null, w.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 0.8f);
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, w.getBoundingBox().inflate(40))) {
                PoliciaNivel.dar(p, 300);
                PoliciaNivel.logro(p, "mazmorra");
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6¡Guardián derrotado!")));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7El cofre del tesoro apareció en la tumba · vuelve en 3 días")));
            }
            {
                String quien = sl.getEntitiesOfClass(ServerPlayer.class, w.getBoundingBox().inflate(40)).stream().map(x -> x.getName().getString()).reduce((a, b) -> a + ", " + b).orElse("unos valientes");
                PoliciaDiario.add(sl, "¡" + quien + " derrotó al Guardián del Eco en la cueva!");
            }
        }
    }
}
