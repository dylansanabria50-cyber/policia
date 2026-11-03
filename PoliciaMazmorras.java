package com.example.policia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Mazmorras nuevas (ademas de la Cueva del Eco): tres templos bajo tierra,
// cada uno con su tema, salas que se arman al azar con oleadas de monstruos
// y un jefe con tres fases:
//  - Cripta Helada (nivel 28): Reina de Escarcha.
//  - Forja Abandonada (nivel 35): Coloso de Escoria.
//  - Templo Hundido (nivel 42): Oraculo Ahogado.
// Aparecen en el mapa de viaje cuando te acercas a su nivel; el jefe vuelve
// a los 3 dias. Datos en PoliciaMundo, seccion "mazmorras".
public class PoliciaMazmorras {
    static final String[] ID = {"cripta", "forja", "templo"};
    static final String[] NOMBRE = {"Cripta Helada", "Forja Abandonada", "Templo Hundido"};
    static final String[] JEFE = {"Reina de Escarcha", "Coloso de Escoria", "Oráculo Ahogado"};
    static final int[] NIVEL = {28, 35, 42};
    static final int[] DIST = {650, 900, 1150};
    static final BossEvent.BossBarColor[] COLOR = {BossEvent.BossBarColor.BLUE, BossEvent.BossBarColor.RED, BossEvent.BossBarColor.GREEN};
    static final Map<String, ServerBossEvent> BARRAS = new HashMap<String, ServerBossEvent>();
    static final Map<String, UUID> JEFES = new HashMap<String, UUID>();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosMazmorras());
    }

    static int indice(String id) {
        for (int i = 0; i < ID.length; i++) {
            if (ID[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    static CompoundTag estado(MinecraftServer s, int i) {
        CompoundTag todo = PoliciaMundo.get(s).getSeccion("mazmorras");
        if (!todo.contains(ID[i])) {
            ServerLevel ow = s.overworld();
            BlockPos sp = ow.getSharedSpawnPos();
            double a = new java.util.Random(ow.getSeed() ^ (0x51D0L + i * 977L)).nextDouble() * Math.PI * 2;
            CompoundTag t = new CompoundTag();
            t.putInt("x", sp.getX() + (int) (Math.cos(a) * DIST[i]));
            t.putInt("z", sp.getZ() + (int) (Math.sin(a) * DIST[i]));
            t.putInt("y", -999);
            todo.put(ID[i], t);
            PoliciaMundo.get(s).setDirty();
        }
        return todo.getCompound(ID[i]);
    }

    // Lo usa PoliciaZonas: adentro de una mazmorra la zona es la mazmorra.
    static PoliciaZonas.Zona zona(ServerLevel sl, BlockPos p) {
        if (sl.dimension() != Level.OVERWORLD) {
            return null;
        }
        CompoundTag todo = PoliciaMundo.get(sl.getServer()).getSeccion("mazmorras");
        for (int i = 0; i < ID.length; i++) {
            if (!todo.contains(ID[i])) {
                continue;
            }
            CompoundTag t = todo.getCompound(ID[i]);
            if (t.getBoolean("hecha") && adentro(t, p)) {
                return new PoliciaZonas.Zona(NOMBRE[i], NIVEL[i]);
            }
        }
        return null;
    }

    static boolean adentro(CompoundTag t, BlockPos p) {
        int x = t.getInt("x");
        int z = t.getInt("z");
        return p.getX() >= x - 4 && p.getX() <= t.getInt("xfin") + 2 && Math.abs(p.getZ() - z) <= 13 && p.getY() < t.getInt("y") + 12 && p.getY() > t.getInt("y") - 3;
    }

    // ---------------- Tema de cada mazmorra ----------------
    static BlockState pared(int i, java.util.Random r) {
        int k = r.nextInt(10);
        return switch (i) {
            case 0 -> k < 4 ? Blocks.PACKED_ICE.defaultBlockState() : k < 7 ? Blocks.POLISHED_DIORITE.defaultBlockState() : k < 9 ? Blocks.CALCITE.defaultBlockState() : Blocks.BLUE_ICE.defaultBlockState();
            case 1 -> k < 4 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : k < 6 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : k < 9 ? Blocks.NETHER_BRICKS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            default -> k < 5 ? Blocks.PRISMARINE_BRICKS.defaultBlockState() : k < 8 ? Blocks.PRISMARINE.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState();
        };
    }

    static BlockState piso(int i, java.util.Random r) {
        int k = r.nextInt(16);
        return switch (i) {
            case 0 -> k < 10 ? Blocks.POLISHED_DIORITE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
            case 1 -> k == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : k < 9 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
            default -> k < 12 ? Blocks.DARK_PRISMARINE.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState();
        };
    }

    static BlockState columna(int i) {
        return switch (i) {
            case 0 -> Blocks.QUARTZ_PILLAR.defaultBlockState();
            case 1 -> Blocks.BASALT.defaultBlockState();
            default -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
        };
    }

    static BlockState adorno(int i, java.util.Random r) {
        return switch (i) {
            case 0 -> r.nextBoolean() ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
            case 1 -> r.nextInt(3) == 0 ? Blocks.ANVIL.defaultBlockState() : r.nextBoolean() ? Blocks.BLAST_FURNACE.defaultBlockState() : Blocks.CAULDRON.defaultBlockState();
            default -> r.nextBoolean() ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        };
    }

    static void luz(ServerLevel sl, int i, BlockPos techo) {
        if (i == 2) {
            sl.setBlock(techo, Blocks.SEA_LANTERN.defaultBlockState(), 2);
        } else {
            sl.setBlock(techo.below(), (i == 0 ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        }
    }

    // Caja hueca: paredes, piso y techo del tema; adentro aire.
    static void sala(ServerLevel sl, int i, java.util.Random r, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos q = new BlockPos(x, y, z);
                    boolean borde = x == x0 || x == x1 || z == z0 || z == z1;
                    if (y == y0) {
                        sl.setBlock(q, piso(i, r), 2);
                    } else if (y == y1 || borde) {
                        sl.setBlock(q, pared(i, r), 2);
                    } else {
                        sl.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    static void abrir(ServerLevel sl, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    sl.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    static void construir(ServerLevel sl, int i, CompoundTag t) {
        java.util.Random r = new java.util.Random(sl.getSeed() ^ (i * 31L + 7));
        int x = t.getInt("x");
        int z = t.getInt("z");
        int sup = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int y0 = Math.max(sl.getMinBuildHeight() + 8, sup - 24);
        t.putInt("y", y0);
        t.putInt("sup", sup);
        // ---- entrada: arco en la superficie y pozo con escalera ----
        for (int dy = -1; dy <= 4; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos q = new BlockPos(x + dx, sup + dy, z + dz);
                    boolean poste = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    if (dy == -1) {
                        sl.setBlock(q, Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? Blocks.AIR.defaultBlockState() : piso(i, r), 2);
                    } else if (poste && dy < 4) {
                        sl.setBlock(q, columna(i), 2);
                    } else if (dy == 4 && (Math.abs(dx) == 2 || Math.abs(dz) == 2)) {
                        sl.setBlock(q, pared(i, r), 2);
                    } else {
                        sl.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        luz(sl, i, new BlockPos(x - 2, sup + 4, z));
        luz(sl, i, new BlockPos(x + 2, sup + 4, z));
        for (int y = y0 + 1; y < sup; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos q = new BlockPos(x + dx, y, z + dz);
                    boolean borde = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    sl.setBlock(q, borde ? pared(i, r) : Blocks.AIR.defaultBlockState(), 2);
                }
            }
            sl.setBlock(new BlockPos(x - 1, y, z), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST), 2);
            if (y % 6 == 0) {
                luz(sl, i, new BlockPos(x + 1, y + 1, z + 1));
            }
        }
        // ---- sala de entrada ----
        sala(sl, i, r, x - 4, y0, z - 4, x + 4, y0 + 6, z + 4);
        abrir(sl, x - 1, y0 + 1, z - 1, x + 1, y0 + 5, z + 1);
        for (int y = y0 + 1; y <= y0 + 6; y++) {
            sl.setBlock(new BlockPos(x - 2, y, z), pared(i, r), 2);
            sl.setBlock(new BlockPos(x - 1, y, z), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST), 2);
        }
        luz(sl, i, new BlockPos(x + 3, y0 + 6, z + 3));
        // ---- salas ----
        int cx = x + 4;
        int n = 3 + r.nextInt(2);
        ListTag salas = new ListTag();
        for (int s = 0; s < n; s++) {
            int largo = 4 + r.nextInt(3);
            sala(sl, i, r, cx, y0, z - 2, cx + largo, y0 + 4, z + 2);
            abrir(sl, cx, y0 + 1, z - 1, cx + largo, y0 + 3, z + 1);
            cx += largo;
            int w = 9 + r.nextInt(5);
            int d = 4 + r.nextInt(3);
            int h = 6 + r.nextInt(2);
            sala(sl, i, r, cx, y0, z - d, cx + w, y0 + h, z + d);
            abrir(sl, cx, y0 + 1, z - 1, cx, y0 + 3, z + 1);
            abrir(sl, cx + w, y0 + 1, z - 1, cx + w, y0 + 3, z + 1);
            for (int[] c : new int[][]{{cx + 2, z - d + 2}, {cx + w - 2, z - d + 2}, {cx + 2, z + d - 2}, {cx + w - 2, z + d - 2}}) {
                for (int y = y0 + 1; y < y0 + h; y++) {
                    sl.setBlock(new BlockPos(c[0], y, c[1]), columna(i), 2);
                }
            }
            for (int k = 0; k < 3; k++) {
                int ax = cx + 1 + r.nextInt(w - 1);
                int az = r.nextBoolean() ? z - d + 1 : z + d - 1;
                sl.setBlock(new BlockPos(ax, y0 + 1, az), adorno(i, r), 2);
            }
            luz(sl, i, new BlockPos(cx + w / 2, y0 + h, z));
            CompoundTag c = new CompoundTag();
            c.putInt("x0", cx);
            c.putInt("x1", cx + w);
            c.putInt("d", d);
            salas.add(c);
            cx += w;
        }
        // ---- pasillo final y sala del jefe ----
        sala(sl, i, r, cx, y0, z - 2, cx + 5, y0 + 4, z + 2);
        abrir(sl, cx, y0 + 1, z - 1, cx + 5, y0 + 3, z + 1);
        cx += 5;
        int bx = cx;
        sala(sl, i, r, bx, y0 - 1, z - 11, bx + 22, y0 + 10, z + 11);
        abrir(sl, bx, y0 + 1, z - 1, bx, y0 + 3, z + 1);
        for (int[] c : new int[][]{{bx + 5, z - 6}, {bx + 17, z - 6}, {bx + 5, z + 6}, {bx + 17, z + 6}}) {
            for (int y = y0; y < y0 + 10; y++) {
                sl.setBlock(new BlockPos(c[0], y, c[1]), columna(i), 2);
            }
            luz(sl, i, new BlockPos(c[0], y0 + 10, c[1] + (c[1] < z ? 2 : -2)));
        }
        luz(sl, i, new BlockPos(bx + 11, y0 + 10, z));
        t.put("salas", salas);
        t.putInt("bx", bx);
        t.putInt("xfin", bx + 22);
        t.putBoolean("hecha", true);
        PoliciaMundo.get(sl.getServer()).setDirty();
    }

    static void revelar(ServerPlayer p, int i) {
        CompoundTag par = PoliciaViaje.paradas(p);
        if (par.contains("md_" + ID[i])) {
            return;
        }
        CompoundTag t = estado(p.server, i);
        CompoundTag c = new CompoundTag();
        c.putString("n", NOMBRE[i]);
        c.putInt("x", t.getInt("x"));
        c.putInt("y", t.contains("sup") ? t.getInt("sup") + 1 : 80);
        c.putInt("z", t.getInt("z"));
        par.put("md_" + ID[i], c);
        p.displayClientMessage(Component.literal("§5§l" + NOMBRE[i] + " §7— se habla de " + JEFE[i] + ", cerca de §f" + t.getInt("x") + ", " + t.getInt("z") + "§7. Es nivel " + NIVEL[i] + ". §8(Ya está en tu mapa de viaje)"), false);
        PoliciaRPG.sync(p);
    }

    // ---------------- Monstruos ----------------
    @SuppressWarnings("unchecked")
    static EntityType<? extends Mob>[] tipos(int i) {
        return switch (i) {
            case 0 -> new EntityType[]{EntityType.STRAY, EntityType.SKELETON, EntityType.ZOMBIE};
            case 1 -> new EntityType[]{EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON};
            default -> new EntityType[]{EntityType.DROWNED, EntityType.VINDICATOR, EntityType.PILLAGER};
        };
    }

    static void fortalecer(Mob m, int nivel, double extraVida) {
        PoliciaCombate.mod(m, Attributes.MAX_HEALTH, UUID.fromString("7c1e2d3f-1001-4a5b-8c9d-0e1f2a3b4c11"), nivel * 0.04 + extraVida, AttributeModifier.Operation.MULTIPLY_BASE);
        PoliciaCombate.mod(m, Attributes.ATTACK_DAMAGE, UUID.fromString("7c1e2d3f-1002-4a5b-8c9d-0e1f2a3b4c12"), nivel * 0.03, AttributeModifier.Operation.MULTIPLY_BASE);
        m.setHealth(m.getMaxHealth());
    }

    static void oleada(ServerLevel sl, int i, double cx, double cy, double cz, int n) {
        EntityType<? extends Mob>[] ts = tipos(i);
        for (int k = 0; k < n; k++) {
            Mob m = ts[k % ts.length].create(sl);
            if (m == null) {
                continue;
            }
            m.moveTo(cx + (sl.random.nextDouble() - 0.5) * 6, cy, cz + (sl.random.nextDouble() - 0.5) * 5, sl.random.nextFloat() * 360, 0);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.STRUCTURE, null, null);
            if (m.getType() == EntityType.DROWNED) {
                m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(sl.random.nextBoolean() ? Items.TRIDENT : Items.IRON_SWORD));
            }
            m.setPersistenceRequired();
            fortalecer(m, NIVEL[i], 0);
            if (k == 0 && m instanceof Monster mo) {
                PoliciaCombate.volverElite(mo, sl);
            }
            sl.addFreshEntity(m);
            sl.sendParticles(i == 0 ? ParticleTypes.SNOWFLAKE : i == 1 ? ParticleTypes.FLAME : ParticleTypes.BUBBLE_POP, m.getX(), m.getY() + 1, m.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        }
    }

    // ---------------- Jefes ----------------
    static void invocarJefe(ServerLevel sl, int i, CompoundTag t) {
        Mob m = switch (i) {
            case 0 -> EntityType.STRAY.create(sl);
            case 1 -> EntityType.RAVAGER.create(sl);
            default -> EntityType.EVOKER.create(sl);
        };
        if (m == null) {
            return;
        }
        int bx = t.getInt("bx");
        int y0 = t.getInt("y");
        int z = t.getInt("z");
        m.moveTo(bx + 15.5, y0 + 1, z + 0.5, 90, 0);
        if (i == 0) {
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            m.setDropChance(EquipmentSlot.MAINHAND, 0);
            m.setDropChance(EquipmentSlot.HEAD, 0);
        }
        int jugadores = Math.max(1, sl.getEntitiesOfClass(ServerPlayer.class, new AABB(bx, y0 - 2, z - 12, bx + 22, y0 + 11, z + 12)).size());
        double base = i == 0 ? 280 : i == 1 ? 420 : 520;
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(base * (1 + 0.5 * (jugadores - 1)) * PoliciaBalance.v(PoliciaBalance.VIDA_JEFES));
        if (m.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            m.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(i == 1 ? 14 : 9);
        }
        m.getAttribute(Attributes.ARMOR).setBaseValue(i == 1 ? 12 : 8);
        if (m.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
        }
        m.setHealth(m.getMaxHealth());
        m.setCustomName(Component.literal("§l" + JEFE[i]));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        m.getPersistentData().putString("pol_jefe_md", ID[i]);
        sl.addFreshEntity(m);
        JEFES.put(ID[i], m.getUUID());
        sl.playSound(null, m.blockPosition(), i == 0 ? SoundEvents.GLASS_BREAK : i == 1 ? SoundEvents.RAVAGER_ROAR : SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.0f, 0.6f);
        sl.sendParticles(ParticleTypes.EXPLOSION, m.getX(), m.getY() + 1, m.getZ(), 3, 0.5, 0.5, 0.5, 0);
        final int fi = i;
        PoliciaHab.tarea(k -> pelea(sl, fi, m, t, k));
    }

    static ServerBossEvent barra(int i) {
        return BARRAS.computeIfAbsent(ID[i], k -> new ServerBossEvent(Component.literal(JEFE[i]), COLOR[i], BossEvent.BossBarOverlay.NOTCHED_10));
    }

    static void titulo(List<ServerPlayer> ps, String t, String sub) {
        for (ServerPlayer p : ps) {
            PoliciaHistoria.titulo(p, t, sub);
        }
    }

    static boolean pelea(ServerLevel sl, int i, Mob m, CompoundTag t, int k) {
        ServerBossEvent b = barra(i);
        int bx = t.getInt("bx");
        int y0 = t.getInt("y");
        int z = t.getInt("z");
        AABB arena = new AABB(bx, y0 - 2, z - 12, bx + 23, y0 + 11, z + 12);
        if (!m.isAlive() || m.isRemoved()) {
            b.removeAllPlayers();
            return true;
        }
        List<ServerPlayer> cerca = sl.getEntitiesOfClass(ServerPlayer.class, arena.inflate(8), p -> p.isAlive() && !p.isSpectator());
        for (ServerPlayer p : new ArrayList<ServerPlayer>(b.getPlayers())) {
            if (!cerca.contains(p)) {
                b.removePlayer(p);
            }
        }
        for (ServerPlayer p : cerca) {
            b.addPlayer(p);
        }
        float frac = m.getHealth() / m.getMaxHealth();
        b.setProgress(frac);
        // sin nadie cerca un rato: se cura y espera
        if (cerca.isEmpty()) {
            int solo = m.getPersistentData().getInt("solo") + 1;
            m.getPersistentData().putInt("solo", solo);
            if (solo > 400) {
                m.setHealth(m.getMaxHealth());
                m.getPersistentData().putInt("fase", 1);
            }
            return false;
        }
        m.getPersistentData().putInt("solo", 0);
        if (!arena.contains(m.position())) {
            m.teleportTo(bx + 11.5, y0 + 1, z + 0.5);
        }
        ServerPlayer obj = cerca.get(0);
        for (ServerPlayer p : cerca) {
            if (p.distanceToSqr(m) < obj.distanceToSqr(m)) {
                obj = p;
            }
        }
        if (m.getTarget() == null || !m.getTarget().isAlive()) {
            m.setTarget(obj);
        }
        int fase = frac > 0.6f ? 1 : frac > 0.25f ? 2 : 3;
        int antes = Math.max(1, m.getPersistentData().getInt("fase"));
        if (fase > antes) {
            m.getPersistentData().putInt("fase", fase);
            titulo(cerca, (fase == 2 ? "§e" : "§c") + JEFE[i], fase == 2 ? "§7Segunda fase" : "§7¡Última fase!");
            sl.playSound(null, m.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.4f);
            sl.sendParticles(ParticleTypes.SONIC_BOOM, m.getX(), m.getY() + 1.5, m.getZ(), 1, 0, 0, 0, 0);
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1000000, fase - 2, false, false));
            if (fase == 3) {
                m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1000000, 0, false, false));
            }
        } else if (antes == 0) {
            m.getPersistentData().putInt("fase", 1);
        }
        switch (i) {
            case 0 -> reina(sl, m, obj, cerca, fase, k);
            case 1 -> coloso(sl, m, obj, cerca, fase, k);
            default -> oraculo(sl, m, obj, cerca, fase, k, t);
        }
        return false;
    }

    // Reina de Escarcha: rafagas de flechas que congelan, jaulas de hielo y ventisca.
    static void reina(ServerLevel sl, Mob m, ServerPlayer obj, List<ServerPlayer> cerca, int fase, int k) {
        int cada = fase == 1 ? 60 : fase == 2 ? 50 : 40;
        if (k % cada == 0) {
            Vec3 d = obj.getEyePosition().subtract(m.getEyePosition());
            int n = fase == 1 ? 5 : 7;
            for (int a = 0; a < n; a++) {
                double ang = (a - (n - 1) / 2.0) * 0.12;
                double cs = Math.cos(ang);
                double sn = Math.sin(ang);
                Arrow f = new Arrow(sl, m);
                f.setPos(m.getX(), m.getEyeY() - 0.1, m.getZ());
                f.shoot(d.x * cs - d.z * sn, d.y + d.horizontalDistance() * 0.12, d.x * sn + d.z * cs, 1.7f, 2.0f);
                f.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                f.setBaseDamage(3.0);
                sl.addFreshEntity(f);
            }
            sl.playSound(null, m.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.5f, 0.7f);
        }
        if (fase >= 2 && k % 140 == 70) {
            BlockPos c = obj.blockPosition();
            List<BlockPos> puestos = new ArrayList<BlockPos>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        boolean pared = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == 2;
                        BlockPos q = c.offset(dx, dy, dz);
                        if (pared && sl.getBlockState(q).isAir()) {
                            sl.setBlock(q, Blocks.PACKED_ICE.defaultBlockState(), 3);
                            puestos.add(q);
                        }
                    }
                }
            }
            obj.displayClientMessage(Component.literal("§b¡Jaula de hielo! Rompela o esperá"), true);
            sl.playSound(null, c, SoundEvents.GLASS_PLACE, SoundSource.HOSTILE, 1.5f, 0.6f);
            PoliciaHab.tarea(t2 -> {
                if (t2 < 60) {
                    return false;
                }
                for (BlockPos q : puestos) {
                    if (sl.getBlockState(q).is(Blocks.PACKED_ICE)) {
                        sl.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                return true;
            });
            for (int s = 0; s < 2; s++) {
                Mob e = EntityType.STRAY.create(sl);
                if (e != null) {
                    e.moveTo(m.getX() + (s == 0 ? 2 : -2), m.getY(), m.getZ(), 0, 0);
                    e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    fortalecer(e, NIVEL[0], 0);
                    sl.addFreshEntity(e);
                }
            }
        }
        if (fase == 3 && k % 20 == 0) {
            for (ServerPlayer p : cerca) {
                p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 60, p.getTicksFrozen() + 30));
                sl.sendParticles(p, ParticleTypes.SNOWFLAKE, true, p.getX(), p.getY() + 2, p.getZ(), 40, 4, 2, 4, 0.05);
            }
            if (k % 60 == 0) {
                for (ServerPlayer p : cerca) {
                    p.hurt(sl.damageSources().freeze(), 2.0f);
                }
            }
        }
    }

    // Coloso de Escoria: pisoton, anillo de fuego y lluvia de meteoros.
    static void coloso(ServerLevel sl, Mob m, ServerPlayer obj, List<ServerPlayer> cerca, int fase, int k) {
        if (k % (fase == 1 ? 80 : 60) == 0) {
            sl.sendParticles(ParticleTypes.EXPLOSION, m.getX(), m.getY() + 0.2, m.getZ(), 6, 2, 0.1, 2, 0);
            sl.sendParticles(ParticleTypes.LAVA, m.getX(), m.getY() + 0.2, m.getZ(), 20, 2.5, 0.1, 2.5, 0);
            sl.playSound(null, m.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0f, 0.6f);
            for (ServerPlayer p : cerca) {
                if (p.distanceTo(m) < 5.5) {
                    p.hurt(m.damageSources().mobAttack(m), 6.0f);
                    Vec3 v = p.position().subtract(m.position()).multiply(1, 0, 1).normalize().scale(1.0);
                    p.setDeltaMovement(v.x, 0.7, v.z);
                    p.hurtMarked = true;
                }
            }
        }
        if (fase >= 2 && k % 90 == 45) {
            final Vec3 c = m.position();
            sl.playSound(null, m.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0f, 0.5f);
            PoliciaHab.tarea(t2 -> {
                double r = 1 + t2 * 0.5;
                for (int a = 0; a < 24; a++) {
                    double ang = a * Math.PI / 12;
                    sl.sendParticles(ParticleTypes.FLAME, c.x + Math.cos(ang) * r, c.y + 0.3, c.z + Math.sin(ang) * r, 1, 0, 0.1, 0, 0.01);
                }
                for (ServerPlayer p : cerca) {
                    double d = p.position().subtract(c).horizontalDistance();
                    if (Math.abs(d - r) < 0.8 && p.onGround() && p.invulnerableTime == 0) {
                        p.hurt(sl.damageSources().inFire(), 5.0f);
                        p.setSecondsOnFire(3);
                    }
                }
                return t2 >= 22;
            });
            for (int s = 0; s < 2; s++) {
                Mob e = EntityType.MAGMA_CUBE.create(sl);
                if (e != null) {
                    e.moveTo(m.getX() + (s == 0 ? 3 : -3), m.getY() + 1, m.getZ(), 0, 0);
                    sl.addFreshEntity(e);
                }
            }
        }
        if (fase == 3 && k % 50 == 25) {
            for (ServerPlayer p : cerca) {
                final Vec3 q = p.position();
                PoliciaHab.tarea(t2 -> {
                    if (t2 < 25) {
                        sl.sendParticles(ParticleTypes.SMALL_FLAME, q.x, q.y + 0.1, q.z, 4, 1.2, 0, 1.2, 0);
                        sl.sendParticles(ParticleTypes.LAVA, q.x, q.y + 8 - t2 * 0.3, q.z, 1, 0.2, 0.2, 0.2, 0);
                        return false;
                    }
                    sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, q.x, q.y + 0.5, q.z, 1, 0, 0, 0, 0);
                    sl.playSound(null, BlockPos.containing(q), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.8f);
                    for (ServerPlayer o : cerca) {
                        if (o.position().distanceTo(q) < 2.5) {
                            o.hurt(sl.damageSources().mobAttack(m), 8.0f);
                            o.setSecondsOnFire(4);
                        }
                    }
                    return true;
                });
            }
        }
    }

    // Oraculo Ahogado: geiseres de agua, teletransporte con ahogados y ola.
    static void oraculo(ServerLevel sl, Mob m, ServerPlayer obj, List<ServerPlayer> cerca, int fase, int k, CompoundTag t) {
        if (k % (fase == 3 ? 50 : 80) == 0) {
            int n = fase == 3 ? 3 : 1;
            for (int g = 0; g < n; g++) {
                ServerPlayer p = cerca.get(sl.random.nextInt(cerca.size()));
                final Vec3 q = p.position();
                PoliciaHab.tarea(t2 -> {
                    if (t2 < 20) {
                        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, q.x, q.y + 0.1, q.z, 6, 0.6, 0, 0.6, 0.1);
                        sl.sendParticles(ParticleTypes.SPLASH, q.x, q.y + 0.1, q.z, 4, 0.6, 0, 0.6, 0);
                        return false;
                    }
                    sl.sendParticles(ParticleTypes.FALLING_WATER, q.x, q.y + 3, q.z, 80, 0.5, 2.5, 0.5, 0);
                    sl.playSound(null, BlockPos.containing(q), PoliciaSonido.anim("geiser").get(), SoundSource.HOSTILE, 1.5f, 1.0f);
                    for (ServerPlayer o : cerca) {
                        if (o.position().distanceTo(q) < 1.8) {
                            o.hurt(m.damageSources().indirectMagic(m, m), 4.0f);
                            o.setDeltaMovement(o.getDeltaMovement().x, 1.25, o.getDeltaMovement().z);
                            o.hurtMarked = true;
                        }
                    }
                    return true;
                });
            }
        }
        if (fase >= 2 && k % 120 == 60) {
            int bx = t.getInt("bx");
            int z = t.getInt("z");
            double nx = bx + 4 + sl.random.nextInt(15);
            double nz = z - 8 + sl.random.nextInt(17);
            sl.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1, m.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
            m.teleportTo(nx, t.getInt("y") + 1, nz);
            sl.playSound(null, m.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.7f);
            for (int s = 0; s < 2; s++) {
                Mob e = EntityType.DROWNED.create(sl);
                if (e != null) {
                    e.moveTo(m.getX() + (s == 0 ? 2 : -2), m.getY(), m.getZ(), 0, 0);
                    e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                    fortalecer(e, NIVEL[2], 0);
                    e.setPersistenceRequired();
                    sl.addFreshEntity(e);
                }
            }
        }
        if (fase == 3 && k % 100 == 30) {
            sl.playSound(null, m.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 1.0f);
            for (int a = 0; a < 48; a++) {
                double ang = a * Math.PI / 24;
                sl.sendParticles(ParticleTypes.SPLASH, m.getX() + Math.cos(ang) * 3, m.getY() + 0.5, m.getZ() + Math.sin(ang) * 3, 3, 0.2, 0.2, 0.2, 0.3);
            }
            for (ServerPlayer p : cerca) {
                if (p.distanceTo(m) < 9) {
                    Vec3 v = p.position().subtract(m.position()).multiply(1, 0, 1).normalize().scale(1.4);
                    p.setDeltaMovement(v.x, 0.4, v.z);
                    p.hurtMarked = true;
                    p.hurt(m.damageSources().indirectMagic(m, m), 5.0f);
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                }
            }
        }
    }

    static void botin(ServerLevel sl, int i, CompoundTag t) {
        BlockPos c = new BlockPos(t.getInt("bx") + 11, t.getInt("y"), t.getInt("z"));
        sl.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), 3);
        if (sl.getBlockEntity(c) instanceof ChestBlockEntity ch) {
            java.util.Random r = new java.util.Random();
            int nv = NIVEL[i];
            ch.setItem(4, PoliciaBotin.equipoDeZona(sl.getRandom(), nv + 5));
            ch.setItem(10, PoliciaBotin.equipoDeZona(sl.getRandom(), nv));
            ch.setItem(12, new ItemStack(Items.EMERALD, 20 + r.nextInt(20) + i * 10));
            ch.setItem(14, new ItemStack(Items.DIAMOND, 3 + r.nextInt(4) + i));
            ch.setItem(16, PoliciaBotin.piezaConjunto(sl.getRandom(), nv));
            ch.setItem(20, new ItemStack(PoliciaBotin.gema(sl.getRandom().nextInt(PoliciaBotin.GEMAS.length)), 2 + i));
            ch.setItem(22, new ItemStack(i == 0 ? Items.BLUE_ICE : i == 1 ? Items.NETHERITE_SCRAP : Items.HEART_OF_THE_SEA, i == 0 ? 8 : 1 + i / 2));
            ch.setItem(24, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1));
            ch.setItem(26, PoliciaCocina.elixir(r.nextInt(PoliciaCocina.ELIXIRES), 2));
        }
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 80, 0.6, 0.8, 0.6, 0.4);
    }

    public static class EventosMazmorras {
        @SubscribeEvent
        public void tickMazmorras(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 11) {
                return;
            }
            ServerLevel sl = p.serverLevel();
            if (sl.dimension() != Level.OVERWORLD) {
                return;
            }
            for (int i = 0; i < ID.length; i++) {
                if (p.tickCount % 200 == 11 && PoliciaRPG.nivel(p) >= NIVEL[i] - 8) {
                    revelar(p, i);
                }
                CompoundTag t = estado(p.server, i);
                int x = t.getInt("x");
                int z = t.getInt("z");
                if (Math.abs(p.getX() - x) > 110 || Math.abs(p.getZ() - z) > 80) {
                    continue;
                }
                if (!t.getBoolean("hecha")) {
                    if (Math.abs(p.getX() - x) < 70 && Math.abs(p.getZ() - z) < 70) {
                        construir(sl, i, t);
                        p.displayClientMessage(Component.literal("§5Sentís algo antiguo bajo tus pies: §f" + NOMBRE[i]), true);
                    }
                    continue;
                }
                if (!adentro(t, p.blockPosition())) {
                    continue;
                }
                int y0 = t.getInt("y");
                long dia = PoliciaMundo.get(p.server).dia(p.server);
                if (t.getBoolean("muerto") && dia >= t.getLong("dia_muerte") + 3) {
                    t.putBoolean("muerto", false);
                    ListTag salas = t.getList("salas", Tag.TAG_COMPOUND);
                    for (int s = 0; s < salas.size(); s++) {
                        salas.getCompound(s).putBoolean("h", false);
                    }
                }
                ListTag salas = t.getList("salas", Tag.TAG_COMPOUND);
                for (int s = 0; s < salas.size(); s++) {
                    CompoundTag c = salas.getCompound(s);
                    if (!c.getBoolean("h") && p.getX() > c.getInt("x0") + 1 && p.getX() < c.getInt("x1")) {
                        c.putBoolean("h", true);
                        oleada(sl, i, (c.getInt("x0") + c.getInt("x1")) / 2.0, y0 + 1, z + 0.5, 4 + s + Math.min(3, sl.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(20)).size() - 1));
                        p.displayClientMessage(Component.literal("§7" + NOMBRE[i] + " · sala " + (s + 1) + " de " + salas.size()), true);
                        PoliciaMundo.get(p.server).setDirty();
                    }
                }
                int bx = t.getInt("bx");
                if (p.getX() > bx + 2 && !t.getBoolean("muerto")) {
                    UUID j = JEFES.get(ID[i]);
                    boolean vivo = j != null && sl.getEntity(j) instanceof Mob mj && mj.isAlive();
                    if (!vivo) {
                        final int fi = i;
                        List<Mob> l = sl.getEntitiesOfClass(Mob.class, new AABB(bx, y0 - 2, z - 12, bx + 23, y0 + 11, z + 12), m -> ID[fi].equals(m.getPersistentData().getString("pol_jefe_md")));
                        if (l.isEmpty()) {
                            invocarJefe(sl, i, t);
                            PoliciaHistoria.titulo(p, "§5" + JEFE[i], "§7Nivel " + NIVEL[i] + " · " + NOMBRE[i]);
                        } else {
                            Mob m = l.get(0);
                            JEFES.put(ID[i], m.getUUID());
                            PoliciaHab.tarea(k -> pelea(sl, fi, m, t, k));
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public void muereJefe(LivingDeathEvent e) {
            LivingEntity v = e.getEntity();
            String id = v.getPersistentData().getString("pol_jefe_md");
            if (id.isEmpty() || !(v.level() instanceof ServerLevel sl)) {
                return;
            }
            int i = indice(id);
            if (i < 0) {
                return;
            }
            CompoundTag t = estado(sl.getServer(), i);
            t.putBoolean("muerto", true);
            t.putLong("dia_muerte", PoliciaMundo.get(sl.getServer()).dia(sl.getServer()));
            PoliciaMundo.get(sl.getServer()).setDirty();
            JEFES.remove(id);
            barra(i).removeAllPlayers();
            botin(sl, i, t);
            List<ServerPlayer> ps = sl.getEntitiesOfClass(ServerPlayer.class, v.getBoundingBox().inflate(40));
            for (ServerPlayer p : ps) {
                PoliciaRPG.sumarXp(p, 400 + i * 200);
                PoliciaHistoria.titulo(p, "§6¡" + JEFE[i] + " derrotado!", "§7El cofre del botín apareció en el centro");
                String fac = PoliciaFacciones.deOficio(PoliciaMod.job(p));
                if (!fac.isEmpty()) {
                    PoliciaFacciones.sumar(p, fac, 30);
                }
            }
            String quien = ps.stream().map(x -> x.getName().getString()).reduce((a, b) -> a + ", " + b).orElse("unos valientes");
            try {
                PoliciaDiario.add(sl, JEFE[i] + " cayó en " + NOMBRE[i] + " a manos de " + quien);
            } catch (Throwable ignored) {
            }
        }
    }
}
