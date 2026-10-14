package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Generador de campamentos de bandidos/torres/cuevas donde aparecen las
// pandillas y los rehenes de las misiones. Los bloques decorativos exactos
// (lana, madera, antorchas) son una aproximacion razonable; la estructura y
// la logica de aparicion/reabastecimiento son fieles al original.
public class PoliciaCamp {
    static final int N = 19;
    static final int[][] SPOTS = new int[][]{{1, -1}, {-1, -1}, {1, 1}, {-1, 1}, {2, -2}, {-2, -2}, {-2, 3}, {0, 4}, {-4, 4}, {1, 6}, {-3, 6}, {0, -3}};
    static final int[][] CAVE = new int[][]{{2, 0}, {-2, 0}, {0, 2}, {3, 3}, {-3, 3}, {4, -1}, {-4, -1}};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    static int hash(int x, int z) {
        return (x * 73856093 ^ z * 19349663) >>> 3 & 7;
    }

    static void build(ServerLevel sl, int kind, int x0, int z0, int g) {
        Block wool;
        if (kind == 2) {
            PoliciaCamp.tower(sl, x0, z0, g);
            return;
        }
        if (kind == 3) {
            PoliciaCamp.cave(sl, x0, z0, g);
            return;
        }
        int cx = x0 + 9;
        int cz = z0 + 9;
        boolean bandit = kind == 0;
        boolean desert = sl.getBlockState(new BlockPos(cx, g, cz)).is(BlockTags.SAND);
        PoliciaStruct.prepareH(sl, x0 - 1, z0 - 1, 21, 21, g, 6);
        for (int x = x0; x < x0 + 19; ++x) {
            for (int z = z0; z < z0 + 19; ++z) {
                int dx = x - cx;
                int dz = z - cz;
                int d2 = dx * dx + dz * dz;
                if (d2 > 100) continue;
                int h = PoliciaCamp.hash(x, z);
                Block fl = desert ? (h < 5 ? Blocks.SAND : Blocks.SANDSTONE) : (h < 3 ? Blocks.DIRT_PATH : (h < 5 ? Blocks.COARSE_DIRT : (h < 7 ? Blocks.PODZOL : Blocks.MUD)));
                PoliciaStruct.set(sl, x, g, z, fl);
                if (d2 < 72 || d2 > 98 || dz > 0 && Math.abs(dx) <= 1) continue;
                if (bandit) {
                    int ht = 3 + (h & 1);
                    for (int y = 1; y <= ht; ++y) {
                        PoliciaStruct.set(sl, x, g + y, z, desert ? Blocks.SANDSTONE : Blocks.COBBLESTONE);
                    }
                    if ((x + z) % 5 != 0) continue;
                    PoliciaStruct.set(sl, x, g + ht + 1, z, Blocks.TORCH);
                    continue;
                }
                PoliciaStruct.set(sl, x, g + 1, z, h % 3 == 0 ? Blocks.SPRUCE_PLANKS : Blocks.OAK_PLANKS);
                if ((x + z) % 6 != 0) continue;
                PoliciaStruct.set(sl, x, g + 2, z, Blocks.LANTERN);
            }
        }
        PoliciaStruct.set(sl, cx, g + 1, cz, bandit ? Blocks.CAMPFIRE : Blocks.SOUL_CAMPFIRE);
        PoliciaStruct.set(sl, cx - 2, g + 1, cz, Blocks.OAK_FENCE);
        PoliciaStruct.set(sl, cx + 2, g + 1, cz, Blocks.OAK_FENCE);
        PoliciaStruct.set(sl, cx, g + 1, cz + 2, Blocks.OAK_FENCE);
        wool = bandit ? (desert ? Blocks.ORANGE_WOOL : Blocks.RED_WOOL) : Blocks.WHITE_WOOL;
        Block wool2 = bandit ? (desert ? Blocks.YELLOW_WOOL : Blocks.BROWN_WOOL) : Blocks.LIGHT_GRAY_WOOL;
        PoliciaCamp.tent(sl, cx - 7, cz - 3, g, wool, 0, "minecraft:chests/simple_dungeon");
        PoliciaCamp.tent(sl, cx + 3, cz - 3, g, wool, 1, bandit ? "minecraft:chests/village/village_weaponsmith" : "minecraft:chests/village/village_toolsmith");
        PoliciaCamp.tent(sl, cx - 2, cz - 8, g, wool2, 2, bandit ? "minecraft:chests/abandoned_mineshaft" : "minecraft:chests/buried_treasure");
        if (!bandit) {
            PoliciaStruct.chest(sl, cx - 4, g + 1, cz + 5, "minecraft:chests/village/village_plains_house");
        }
        PoliciaCamp.cage(sl, cx + 3, cz + 3, g);
        PoliciaStruct.captive(sl, (double) cx + 4.5, (double) g + 1.0, (double) cz + 4.5);
        BlockPos c = new BlockPos(cx, g + 1, cz);
        PoliciaStruct.Sites.get(sl).add(3 + kind, c);
        PoliciaCamp.spawnAll(sl, kind, c);
        if (!bandit) {
            PoliciaCamp.put(sl, 12, new BlockPos(cx, g + 1, cz - 6), c);
        }
    }

    static void tent(ServerLevel sl, int xa, int za, int g, Block wool, int door, String table) {
        int dx;
        int xb = xa + 4;
        int zb = za + 4;
        for (int x = xa; x <= xb; ++x) {
            for (int z = za; z <= zb; ++z) {
                boolean edge = x == xa || x == xb || z == za || z == zb;
                PoliciaStruct.set(sl, x, g, z, Blocks.OAK_PLANKS);
                PoliciaStruct.set(sl, x, g + 1, z, edge ? wool : Blocks.AIR);
                PoliciaStruct.set(sl, x, g + 2, z, edge ? wool : Blocks.AIR);
                PoliciaStruct.set(sl, x, g + 3, z, wool);
            }
        }
        int mx = (xa + xb) / 2;
        int mz = (za + zb) / 2;
        dx = door == 0 ? xb : (door == 1 ? xa : mx);
        int dz = door == 2 ? zb : (door == 3 ? za : mz);
        PoliciaStruct.set(sl, dx, g + 1, dz, Blocks.AIR);
        PoliciaStruct.set(sl, dx, g + 2, dz, Blocks.AIR);
        PoliciaStruct.chest(sl, xa + 1, g + 1, za + 1, table);
        PoliciaStruct.set(sl, xb - 1, g + 1, za + 1, Blocks.CRAFTING_TABLE);
        PoliciaStruct.set(sl, xb - 1, g + 1, zb - 1, Blocks.SPRUCE_PLANKS);
        PoliciaStruct.set(sl, xa + 1, g + 1, zb - 1, Blocks.TORCH);
    }

    static void cage(ServerLevel sl, int xa, int za, int g) {
        for (int x = xa; x <= xa + 2; ++x) {
            for (int z = za; z <= za + 2; ++z) {
                boolean edge = x == xa || x == xa + 2 || z == za || z == za + 2;
                PoliciaStruct.set(sl, x, g, z, Blocks.IRON_BARS);
                PoliciaStruct.set(sl, x, g + 1, z, edge ? Blocks.IRON_BARS : Blocks.AIR);
                PoliciaStruct.set(sl, x, g + 2, z, edge ? Blocks.IRON_BARS : Blocks.AIR);
                PoliciaStruct.set(sl, x, g + 3, z, Blocks.IRON_BARS);
            }
        }
        PoliciaStruct.set(sl, xa, g + 1, za + 1, Blocks.IRON_BARS);
        PoliciaStruct.set(sl, xa, g + 2, za + 1, Blocks.IRON_BARS);
    }

    static void tower(ServerLevel sl, int x0, int z0, int g) {
        int y;
        PoliciaStruct.prepareH(sl, x0 - 1, z0 - 1, 13, 13, g, 14);
        int cx = x0 + 5;
        int cz = z0 + 5;
        for (int x = x0; x < x0 + 11; ++x) {
            for (int z = z0; z < z0 + 11; ++z) {
                int dx = x - cx;
                int dz = z - cz;
                if (dx * dx + dz * dz > 30) continue;
                PoliciaStruct.set(sl, x, g, z, PoliciaCamp.hash(x, z) < 4 ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT);
            }
        }
        for (y = 1; y <= 8; ++y) {
            for (int sx = -2; sx <= 2; sx += 4) {
                for (int sz = -2; sz <= 2; sz += 4) {
                    PoliciaStruct.set(sl, cx + sx, g + y, cz + sz, Blocks.COBBLESTONE);
                }
            }
        }
        for (int i = -1; i <= 1; ++i) {
            PoliciaStruct.set(sl, cx + i, g + 4, cz - 2, Blocks.OAK_PLANKS);
            PoliciaStruct.set(sl, cx + i, g + 4, cz + 2, Blocks.OAK_PLANKS);
            PoliciaStruct.set(sl, cx - 2, g + 4, cz + i, Blocks.OAK_PLANKS);
            PoliciaStruct.set(sl, cx + 2, g + 4, cz + i, Blocks.OAK_PLANKS);
        }
        for (int dx = -3; dx <= 3; ++dx) {
            for (int dz = -3; dz <= 3; ++dz) {
                PoliciaStruct.set(sl, cx + dx, g + 9, cz + dz, Blocks.OAK_PLANKS);
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 3) continue;
                PoliciaStruct.set(sl, cx + dx, g + 10, cz + dz, Blocks.OAK_PLANKS);
            }
        }
        for (int sx = -3; sx <= 3; sx += 6) {
            for (int sz = -3; sz <= 3; sz += 6) {
                PoliciaStruct.set(sl, cx + sx, g + 11, cz + sz, Blocks.TORCH);
            }
        }
        for (y = 1; y <= 9; ++y) {
            PoliciaStruct.set(sl, cx, g + y, cz + 3, Blocks.OAK_PLANKS);
            PoliciaStruct.set(sl, cx, g + y, cz + 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        PoliciaStruct.chest(sl, cx - 2, g + 10, cz - 2, "minecraft:chests/pillager_outpost");
        BlockPos c = new BlockPos(cx, g + 10, cz);
        PoliciaStruct.Sites.get(sl).add(5, c);
        PoliciaCamp.spawnAll(sl, 2, c);
    }

    static void cave(ServerLevel sl, int x0, int z0, int g) {
        int cx = x0 + 7;
        int cz = z0 + 7;
        int bot = g - 13;
        int top = g - 8;
        for (int x = x0; x < x0 + 15; ++x) {
            for (int z = z0; z < z0 + 15; ++z) {
                int dx = x - cx;
                int dz = z - cz;
                int d2 = dx * dx + dz * dz;
                if (d2 > 64) continue;
                for (int y = bot; y <= top + 1; ++y) {
                    if (d2 <= 49 && y > bot && y <= top) {
                        PoliciaStruct.set(sl, x, y, z, Blocks.AIR);
                        continue;
                    }
                    if (y == bot) {
                        PoliciaStruct.set(sl, x, y, z, PoliciaCamp.hash(x, z) < 3 ? Blocks.IRON_BARS : Blocks.STONE);
                        continue;
                    }
                    PoliciaStruct.set(sl, x, y, z, Blocks.STONE);
                }
            }
        }
        int sx = cx;
        int sz = cz - 6;
        for (int y = top + 1; y <= g; ++y) {
            PoliciaStruct.set(sl, sx, y, sz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            PoliciaStruct.set(sl, sx, y, sz - 1, Blocks.STONE_BRICKS);
            PoliciaStruct.set(sl, sx - 1, y, sz, Blocks.STONE_BRICKS);
            PoliciaStruct.set(sl, sx + 1, y, sz, Blocks.STONE_BRICKS);
        }
        PoliciaStruct.set(sl, sx, g + 1, sz - 1, Blocks.TORCH);
        int f = bot + 1;
        PoliciaStruct.set(sl, cx, f, cz, Blocks.CAMPFIRE);
        PoliciaStruct.set(sl, cx - 2, f, cz + 1, Blocks.CRAFTING_TABLE);
        PoliciaStruct.set(sl, cx + 2, f, cz + 1, Blocks.CRAFTING_TABLE);
        PoliciaStruct.chest(sl, cx - 5, f, cz, "minecraft:chests/simple_dungeon");
        PoliciaStruct.chest(sl, cx + 5, f, cz, "minecraft:chests/abandoned_mineshaft");
        PoliciaStruct.chest(sl, cx, f, cz + 5, "minecraft:chests/pillager_outpost");
        PoliciaStruct.chest(sl, cx + 1, f, cz - 5, "minecraft:chests/buried_treasure");
        PoliciaStruct.set(sl, cx - 4, f, cz + 3, Blocks.SPRUCE_PLANKS);
        PoliciaStruct.set(sl, cx + 4, f, cz + 3, Blocks.SPRUCE_PLANKS);
        PoliciaStruct.set(sl, cx - 4, f, cz - 3, Blocks.TORCH);
        PoliciaStruct.set(sl, cx + 4, f, cz - 3, Blocks.TORCH);
        PoliciaStruct.set(sl, cx - 3, f, cz + 4, Blocks.TORCH);
        PoliciaStruct.set(sl, cx + 3, f, cz + 4, Blocks.TORCH);
        BlockPos c = new BlockPos(cx, f, cz);
        PoliciaStruct.Sites.get(sl).add(6, c);
        PoliciaCamp.spawnAll(sl, 3, c);
    }

    static void spawnAll(ServerLevel sl, int kind, BlockPos c) {
        int[] nArray;
        if (kind == 2) {
            PoliciaCamp.put(sl, 5, c.offset(2, 0, 2), c);
            PoliciaCamp.put(sl, 5, c.offset(-2, 0, 2), c);
            PoliciaCamp.put(sl, 5, c.offset(2, 0, -2), c);
            PoliciaCamp.put(sl, 1, c.offset(4, -9, 0), c);
            PoliciaCamp.put(sl, 1, c.offset(-4, -9, 0), c);
            return;
        }
        if (kind == 3) {
            int[] ks = new int[]{1, 1, 1, 2, 2, 3, 1};
            for (int i = 0; i < ks.length; ++i) {
                PoliciaCamp.put(sl, ks[i], c.offset(CAVE[i][0], 0, CAVE[i][1]), c);
            }
            PoliciaCamp.put(sl, 4, c.offset(0, 0, -3), c);
            return;
        }
        if (kind == 0) {
            int[] nArray2 = new int[7];
            nArray2[0] = 1;
            nArray2[1] = 1;
            nArray2[2] = 1;
            nArray2[3] = 1;
            nArray2[4] = 2;
            nArray2[5] = 2;
            nArray = nArray2;
            nArray2[6] = 3;
        } else {
            int[] nArray3 = new int[8];
            nArray3[0] = 0;
            nArray3[1] = 0;
            nArray3[2] = 0;
            nArray3[3] = 0;
            nArray3[4] = 1;
            nArray3[5] = 1;
            nArray3[6] = 2;
            nArray = nArray3;
            nArray3[7] = 2;
        }
        int[] ks = nArray;
        for (int i = 0; i < ks.length; ++i) {
            PoliciaCamp.put(sl, ks[i], new BlockPos(c.getX() + SPOTS[i][0], c.getY(), c.getZ() + SPOTS[i][1]), c);
        }
        if (kind == 0 && sl.random.nextInt(3) == 0) {
            PoliciaCamp.put(sl, 4, new BlockPos(c.getX(), c.getY(), c.getZ() - 6), c);
        }
    }

    static void put(ServerLevel sl, int k, BlockPos p, BlockPos home) {
        PoliciaMision.Pj e = PoliciaMision.spawn(sl, null, k, p);
        e.life = Integer.MAX_VALUE;
        if (k != 12) {
            e.getPersistentData().putBoolean("pol_camp", true);
        }
        e.restrictTo(home, k == 12 ? 4 : 11);
    }

    static void restock(ServerLevel sl, int kind, BlockPos c) {
        AABB bb = new AABB(c).inflate(16.0, 8.0, 16.0);
        for (PoliciaMision.Pj e : sl.getEntitiesOfClass(PoliciaMision.Pj.class, bb)) {
            if (!e.isAlive() || !e.getPersistentData().getBoolean("pol_camp")) continue;
            return;
        }
        PoliciaCamp.spawnAll(sl, kind, c);
    }

    public static class Ev {
        @SubscribeEvent
        public void cTick(TickEvent.PlayerTickEvent e) {
            Player player;
            if (e.phase != TickEvent.Phase.END || !((player = e.player) instanceof ServerPlayer)) {
                return;
            }
            ServerPlayer sp = (ServerPlayer) player;
            if (sp.tickCount % 200 != 55) {
                return;
            }
            ServerLevel sl = sp.serverLevel();
            if (sl.dimension() != Level.OVERWORLD || sl.getGameTime() % 12000L >= 200L) {
                return;
            }
            try {
                PoliciaStruct.Sites st = PoliciaStruct.Sites.get(sl);
                for (int k = 0; k < 4; ++k) {
                    BlockPos s = st.nearest(3 + k, sp.blockPosition());
                    if (s == null || !(s.distSqr((Vec3i) sp.blockPosition()) < 2304.0)) continue;
                    PoliciaCamp.restock(sl, k, s);
                }
            } catch (RuntimeException runtimeException) {
                // vacio: ignorar errores transitorios de generacion
            }
        }
    }
}
