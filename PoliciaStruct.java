package com.example.policia;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Generador de las estructuras principales (comisaria, obra, casino) y
// utilidades compartidas de construccion/registro de sitios. Los bloques
// decorativos exactos son una aproximacion razonable; la disposicion, los
// NPC y la logica de generacion son fieles al original.
public class PoliciaStruct {
    static final String[] SN = new String[]{"comisaria", "obra", "casino", "bandidos", "ladrones", "atalaya", "cueva", "herreria"};
    static final int[] W = new int[]{31, 17, 13, 19, 19, 11, 15, 13};
    static final int[] D = new int[]{23, 13, 13, 19, 19, 11, 15, 11};
    static final int KINDS = 8;
    static final int[] AGX = new int[]{10, 12, 10, 12, 14, 16, 18, 20, 15};
    static final int[] AGZ = new int[]{5, 5, 9, 9, 9, 9, 9, 9, 10};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Ev());
        PoliciaCamp.init();
    }

    static void set(ServerLevel sl, int x, int y, int z, Block b) {
        sl.setBlock(new BlockPos(x, y, z), b.defaultBlockState(), 2);
    }

    static void set(ServerLevel sl, int x, int y, int z, BlockState b) {
        sl.setBlock(new BlockPos(x, y, z), b, 2);
    }

    static void fill(ServerLevel sl, int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        BlockState s = b.defaultBlockState();
        for (int x = x0; x <= x1; ++x) {
            for (int y = y0; y <= y1; ++y) {
                for (int z = z0; z <= z1; ++z) {
                    sl.setBlock(new BlockPos(x, y, z), s, 2);
                }
            }
        }
    }

    static int ground(ServerLevel sl, int x, int z) {
        sl.getChunk(x >> 4, z >> 4);
        return sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    static int survey(ServerLevel sl, int x0, int z0, int w, int d) {
        int mn = 9999;
        int mx = -9999;
        int[] xs = new int[]{x0, x0 + w / 2, x0 + w - 1};
        int[] zs = new int[]{z0, z0 + d / 2, z0 + d - 1};
        for (int x : xs) {
            for (int z : zs) {
                int y = PoliciaStruct.ground(sl, x, z);
                BlockState b = sl.getBlockState(new BlockPos(x, y, z));
                if (b.isAir() || !b.getFluidState().isEmpty()) {
                    return -999;
                }
                mn = Math.min(mn, y);
                mx = Math.max(mx, y);
            }
        }
        if (mx - mn > (w > 25 ? 8 : 5) || mx < sl.getMinBuildHeight() + 30) {
            return -999;
        }
        return mx;
    }

    static void prepare(ServerLevel sl, int x0, int z0, int w, int d, int g) {
        PoliciaStruct.prepareH(sl, x0, z0, w, d, g, 10);
    }

    static void prepareH(ServerLevel sl, int x0, int z0, int w, int d, int g, int h) {
        for (int x = x0; x < x0 + w; ++x) {
            for (int z = z0; z < z0 + d; ++z) {
                BlockPos p;
                BlockState b;
                int y;
                // Aproximacion: se rellena con piedra todo lo que sea aire, fluido
                // o follaje/tronco hasta encontrar terreno solido real.
                for (y = g; y > g - 14 && ((b = sl.getBlockState(p = new BlockPos(x, y, z))).isAir() || !b.getFluidState().isEmpty() || b.is(BlockTags.LEAVES) || b.is(BlockTags.LOGS)); --y) {
                    sl.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
                for (y = g + 1; y <= g + h; ++y) {
                    p = new BlockPos(x, y, z);
                    if (sl.getBlockState(p).isAir()) continue;
                    sl.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    static PoliciaBoss.BossNpc npc(ServerLevel sl, int kind, double x, double y, double z, float yaw, BlockPos home, int r) {
        PoliciaBoss.BossNpc b = new PoliciaBoss.BossNpc((EntityType<? extends PoliciaBoss.BossNpc>) (EntityType<?>) PoliciaBoss.BOSS.get(), (Level) sl);
        b.setKind(kind);
        b.moveTo(x, y, z, yaw, 0.0f);
        b.setYRot(yaw);
        b.setYHeadRot(yaw);
        if (home != null) {
            b.setHome(home, r);
        }
        sl.addFreshEntity(b);
        return b;
    }

    static void captive(ServerLevel sl, double x, double y, double z) {
        PoliciaMision.Pj c = new PoliciaMision.Pj((EntityType<? extends PoliciaMision.Pj>) (EntityType<?>) PoliciaMision.COM.get(), (Level) sl);
        c.setKind(9);
        c.setCaptive(true);
        c.getPersistentData().putBoolean("pol_cas", true);
        c.moveTo(x, y, z, 0.0f, 0.0f);
        sl.addFreshEntity(c);
    }

    static void chest(ServerLevel sl, int x, int y, int z, String table) {
        BlockPos p = new BlockPos(x, y, z);
        sl.setBlock(p, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = sl.getBlockEntity(p);
        if (be instanceof RandomizableContainerBlockEntity c) {
            c.setLootTable(new ResourceLocation(table), sl.random.nextLong());
        }
    }

    static void restock(ServerLevel sl, BlockPos entry) {
        int ox = entry.getX() - 18;
        int oz = entry.getZ() - 15;
        int y = entry.getY();
        if (!sl.hasChunkAt(new BlockPos(ox + 15, y, oz + 11))) {
            return;
        }
        if (!(sl.getBlockState(new BlockPos(ox + 24, y, oz + 9)).getBlock() instanceof StairBlock)) {
            return;
        }
        AABB bb = new AABB((double) (ox - 30), (double) (y - 4), (double) (oz - 30), (double) (ox + 62), (double) (y + 26), (double) (oz + 54));
        int have = 0;
        for (PoliciaBoss.BossNpc b : sl.getEntitiesOfClass(PoliciaBoss.BossNpc.class, bb)) {
            if (b.kind() != 5) continue;
            ++have;
        }
        BlockPos home = new BlockPos(ox + 14, y, oz + 9);
        for (int i = 0; i < AGX.length && have < AGX.length; ++i) {
            BlockPos p = new BlockPos(ox + AGX[i], y, oz + AGZ[i]);
            if (!sl.hasChunkAt(p) || !sl.getBlockState(p).isAir()) continue;
            PoliciaStruct.npc(sl, 5, (double) p.getX() + 0.5, y, (double) p.getZ() + 0.5, sl.random.nextFloat() * 360.0f, home, 24);
            ++have;
        }
    }

    static boolean natural(ServerLevel sl, int x0, int z0, int w, int d) {
        for (int x = x0; x < x0 + w; x += 4) {
            for (int z = z0; z < z0 + d; z += 4) {
                boolean ok;
                BlockState b = sl.getBlockState(new BlockPos(x, PoliciaStruct.ground(sl, x, z), z));
                // Aproximacion: conjunto razonable de bloques/etiquetas de terreno natural
                boolean bl = ok = b.is(BlockTags.DIRT) || b.is(BlockTags.SAND) || b.is(BlockTags.BASE_STONE_OVERWORLD) || b.is(BlockTags.LEAVES) || b.is(BlockTags.SNOW) || b.is(Blocks.COARSE_DIRT) || b.is(Blocks.MYCELIUM) || b.is(Blocks.GRAVEL) || b.is(Blocks.CLAY) || b.is(Blocks.SANDSTONE) || b.is(Blocks.RED_SAND);
                if (ok) continue;
                return false;
            }
        }
        return true;
    }

    // Comisaria y herreria aparecen junto a las aldeas.
    static boolean genNearVillage(ServerPlayer sp, ServerLevel sl) {
        return PoliciaStruct.genNearVillage(sp, sl, 0);
    }

    // Una comisaria (y una herreria) por aldea de verdad: la aldea se busca
    // como estructura del juego (no por aldeanos sueltos, que pueden ser
    // NPC del mod en el medio de la nada) y se anota por su clave, asi no se
    // repite aunque los aldeanos se muevan.
    static boolean genNearVillage(ServerPlayer sp, ServerLevel sl, int k) {
        net.minecraft.world.level.levelgen.structure.StructureStart aldea = sl.structureManager().getStructureWithPieceAt(sp.blockPosition(), net.minecraft.tags.StructureTags.VILLAGE);
        if (aldea == null || !aldea.isValid()) {
            aldea = null;
            for (Villager vl : sl.getEntitiesOfClass(Villager.class, sp.getBoundingBox().inflate(90.0, 60.0, 90.0))) {
                net.minecraft.world.level.levelgen.structure.StructureStart st = sl.structureManager().getStructureWithPieceAt(vl.blockPosition(), net.minecraft.tags.StructureTags.VILLAGE);
                if (st != null && st.isValid()) {
                    aldea = st;
                    break;
                }
            }
        }
        if (aldea == null) {
            return false;
        }
        String clave = "v_" + aldea.getChunkPos().x + "_" + aldea.getChunkPos().z + "_" + k;
        CompoundTag hechas = PoliciaMundo.get(sl.getServer()).getSeccion("estructuras_aldea");
        if (hechas.getBoolean(clave)) {
            return false;
        }
        net.minecraft.world.level.levelgen.structure.BoundingBox bb = aldea.getBoundingBox();
        BlockPos v = bb.getCenter();
        int radio = Math.max(bb.getXSpan(), bb.getZSpan()) / 2;
        // si ya hay una de este tipo en la aldea o muy cerca (mundos viejos), se anota y listo
        if (Sites.get(sl).near(k, v, radio + 60)) {
            hechas.putBoolean(clave, true);
            PoliciaMundo.get(sl.getServer()).setDirty();
            return false;
        }
        for (int i = 0; i < 40; ++i) {
            double ang = sl.random.nextDouble() * Math.PI * 2.0;
            double dd = Math.min(radio, 70) + 6 + sl.random.nextDouble() * 22.0;
            int cx = v.getX() + (int) (Math.cos(ang) * dd);
            int cz = v.getZ() + (int) (Math.sin(ang) * dd);
            int x0 = cx - W[k] / 2;
            int z0 = cz - D[k] / 2;
            if (!PoliciaStruct.natural(sl, x0 - 1, z0 - 1, W[k] + 2, D[k] + 2) || !PoliciaStruct.clear(sl, k, cx, cz, true) || !PoliciaStruct.place(sl, k, cx, cz, false)) continue;
            hechas.putBoolean(clave, true);
            PoliciaMundo.get(sl.getServer()).setDirty();
            return true;
        }
        return false;
    }


    // Distancia mínima entre dos estructuras del MISMO tipo (el casino es
    // el más espaciado) y entre estructuras de cualquier tipo.
    static final int[] SAME = new int[]{120, 600, 1200, 700, 700, 700, 700, 120};
    static final int ANY = 72;

    // ¿Se puede construir acá sin chocar con otra estructura del mod ni con
    // estructuras del juego (aldeas, templos, etc.)?
    static boolean clear(ServerLevel sl, int k, int cx, int cz, boolean nearVillage) {
        BlockPos c = new BlockPos(cx, 64, cz);
        Sites st = Sites.get(sl);
        for (int j = 0; j < KINDS; ++j) {
            int r = j == k && SAME[k] > 0 ? SAME[k] : ANY;
            if (st.near(j, c, r)) {
                return false;
            }
        }
        int hw = W[k] / 2 + 6;
        int hd = D[k] / 2 + 6;
        for (int[] o : new int[][]{{0, 0}, {-hw, -hd}, {hw, -hd}, {-hw, hd}, {hw, hd}, {0, -hd}, {0, hd}, {-hw, 0}, {hw, 0}}) {
            BlockPos p = sl.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, new BlockPos(cx + o[0], 0, cz + o[1]));
            try {
                if (nearVillage) {
                    if (sl.structureManager().getStructureWithPieceAt(p, net.minecraft.tags.StructureTags.VILLAGE).isValid()) {
                        return false;
                    }
                } else if (sl.structureManager().hasAnyStructureAt(p)) {
                    return false;
                }
            } catch (RuntimeException ex) {
                return false;
            }
        }
        if (!nearVillage && !sl.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(cx - 80, -64, cz - 80, cx + 80, 320, cz + 80)).isEmpty()) {
            return false;
        }
        return true;
    }

    static boolean place(ServerLevel sl, int k, int cx, int cz, boolean force) {
        int x0 = cx - W[k] / 2;
        int z0 = cz - D[k] / 2;
        int g = PoliciaStruct.survey(sl, x0 - 1, z0 - 1, W[k] + 2, D[k] + 2);
        if (g < 0) {
            if (!force) {
                return false;
            }
            g = PoliciaStruct.ground(sl, cx, cz);
        }
        switch (k) {
            case 0: {
                PoliciaStruct.comisaria(sl, x0, z0, g);
                break;
            }
            case 1: {
                PoliciaStruct.obra(sl, x0, z0, g);
                break;
            }
            case 3: {
                PoliciaCamp.build(sl, 0, x0, z0, g);
                break;
            }
            case 4: {
                PoliciaCamp.build(sl, 1, x0, z0, g);
                break;
            }
            case 5: {
                PoliciaCamp.build(sl, 2, x0, z0, g);
                break;
            }
            case 6: {
                PoliciaCamp.build(sl, 3, x0, z0, g);
                break;
            }
            case 7: {
                PoliciaStruct.herreria(sl, x0, z0, g);
                break;
            }
            default: {
                PoliciaStruct.casino(sl, x0, z0, g);
            }
        }
        return true;
    }

    static void tryGen(ServerPlayer sp, ServerLevel sl) {
        Sites st = Sites.get(sl);
        for (int k = 0; k < KINDS; ++k) {
            int chance = k == 2 ? 12 : 25;
            if (k == 0 || k == 7 || st.near(k, sp.blockPosition(), SAME[k]) || sl.random.nextInt(100) >= chance) continue;
            for (int i = 0; i < 4; ++i) {
                int z;
                double ang = sl.random.nextDouble() * Math.PI * 2.0;
                double d = 90.0 + sl.random.nextDouble() * 60.0;
                int x = sp.getBlockX() + (int) (Math.cos(ang) * d);
                z = sp.getBlockZ() + (int) (Math.sin(ang) * d);
                if (!PoliciaStruct.clear(sl, k, x, z, false) || !PoliciaStruct.place(sl, k, x, z, false)) continue;
                return;
            }
        }
    }

    public static class Ev {
        @SubscribeEvent
        public void sPtick(TickEvent.PlayerTickEvent e) {
            Player player;
            if (e.phase != TickEvent.Phase.END || !((player = e.player) instanceof ServerPlayer)) {
                return;
            }
            ServerPlayer sp = (ServerPlayer) player;
            if (sp.tickCount % 200 == 77 && sp.serverLevel().dimension() == Level.OVERWORLD) {
                try {
                    BlockPos s = Sites.get(sp.serverLevel()).nearest(0, sp.blockPosition());
                    if (s != null && s.distSqr((Vec3i) sp.blockPosition()) < 8100.0) {
                        PoliciaStruct.restock(sp.serverLevel(), s);
                    }
                } catch (RuntimeException s) {
                    // vacio: ignorar errores transitorios
                }
            }
            if (sp.tickCount % 200 == 133 && sp.serverLevel().dimension() == Level.OVERWORLD) {
                try {
                    PoliciaStruct.genNearVillage(sp, sp.serverLevel(), 0);
                    PoliciaStruct.genNearVillage(sp, sp.serverLevel(), 7);
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                }
            }
            if (sp.tickCount % 600 != 321) {
                return;
            }
            ServerLevel sl = sp.serverLevel();
            if (sl.dimension() != Level.OVERWORLD) {
                return;
            }
            try {
                PoliciaStruct.tryGen(sp, sl);
            } catch (RuntimeException runtimeException) {
                // vacio: ignorar errores transitorios
            }
        }

        @SubscribeEvent
        public void sCmd(RegisterCommandsEvent e) {
            e.getDispatcher().register(((LiteralArgumentBuilder<CommandSourceStack>) Commands.literal("pestructura").requires(s -> s.hasPermission(2)))
                    .then(Commands.argument("tipo", (ArgumentType<?>) StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String t = StringArgumentType.getString(c, "tipo");
                        int k = -1;
                        for (int i = 0; i < KINDS; ++i) {
                            if (!SN[i].equals(t)) continue;
                            k = i;
                        }
                        if (k < 0) {
                            c.getSource().sendFailure(Component.literal("Use: comisaría, obra, casino, bandidos, ladrones, atalaya, cueva o herrería"));
                            return 0;
                        }
                        String vnm = k == 0 ? "Comisaría" : "Herrería";
                        if ((k == 0 || k == 7) && PoliciaStruct.genNearVillage(p, p.serverLevel(), k)) {
                            c.getSource().sendSuccess(() -> Component.literal(vnm + " colocada junto a la aldea"), false);
                            return 1;
                        }
                        Direction f = p.getDirection();
                        PoliciaStruct.place(p.serverLevel(), k, p.getBlockX() + f.getStepX() * 14, p.getBlockZ() + f.getStepZ() * 14, true);
                        c.getSource().sendSuccess(() -> Component.literal("Construcción colocada"), false);
                        return 1;
                    })));
        }
    }

    public static class Sites extends SavedData {
        final List<List<Long>> l = new ArrayList<>();

        Sites() {
            for (int i = 0; i < KINDS; ++i) {
                this.l.add(new ArrayList<>());
            }
        }

        static Sites get(ServerLevel sl) {
            return sl.getServer().overworld().getDataStorage().computeIfAbsent(Sites::load, Sites::new, "policia_sites");
        }

        static Sites load(CompoundTag t) {
            Sites s = new Sites();
            for (int i = 0; i < KINDS; ++i) {
                for (long v : t.getLongArray("k" + i)) {
                    s.l.get(i).add(v);
                }
            }
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            for (int i = 0; i < KINDS; ++i) {
                long[] a = new long[this.l.get(i).size()];
                for (int j = 0; j < a.length; ++j) {
                    a[j] = this.l.get(i).get(j);
                }
                t.putLongArray("k" + i, a);
            }
            return t;
        }

        void add(int k, BlockPos p) {
            this.l.get(k).add(p.asLong());
            this.setDirty();
        }

        BlockPos nearest(int k, BlockPos from) {
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (long v : this.l.get(k)) {
                BlockPos p = BlockPos.of(v);
                double d = p.distSqr((Vec3i) from);
                if (!(d < bd)) continue;
                bd = d;
                best = p;
            }
            return best;
        }

        boolean near(int k, BlockPos from, int r) {
            BlockPos p = this.nearest(k, from);
            return p != null && p.distSqr((Vec3i) from) < (double) r * (double) r;
        }
    }
}
