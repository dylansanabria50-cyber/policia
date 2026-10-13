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
    static final String[] SN = new String[]{"comisaria", "obra", "casino", "bandidos", "ladrones", "atalaya", "cueva"};
    static final int[] W = new int[]{31, 17, 13, 19, 19, 11, 15};
    static final int[] D = new int[]{23, 13, 13, 19, 19, 11, 15};
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

    static boolean genNearVillage(ServerPlayer sp, ServerLevel sl) {
        List<Villager> vs = sl.getEntitiesOfClass(Villager.class, sp.getBoundingBox().inflate(90.0, 60.0, 90.0));
        BlockPos v = null;
        if (!vs.isEmpty()) {
            Villager an = vs.get(0);
            for (Villager vl : vs) {
                if (!(vl.distanceToSqr((Entity) sp) < an.distanceToSqr((Entity) sp))) continue;
                an = vl;
            }
            double sx = 0.0;
            double sz = 0.0;
            int n = 0;
            for (Villager vl : vs) {
                if (vl.distanceToSqr((Entity) an) > 2304.0) continue;
                sx += vl.getX();
                sz += vl.getZ();
                ++n;
            }
            v = new BlockPos((int) (sx / (double) n), an.getBlockY(), (int) (sz / (double) n));
        }
        if (v == null) {
            return false;
        }
        if (Sites.get(sl).near(0, v, 140)) {
            return false;
        }
        for (int i = 0; i < 30; ++i) {
            int cz;
            int z0;
            double ang = sl.random.nextDouble() * Math.PI * 2.0;
            double dd = 28.0 + sl.random.nextDouble() * 47.0;
            int cx = v.getX() + (int) (Math.cos(ang) * dd);
            int x0 = cx - W[0] / 2;
            if (!PoliciaStruct.natural(sl, x0 - 1, (z0 = (cz = v.getZ() + (int) (Math.sin(ang) * dd)) - D[0] / 2) - 1, W[0] + 2, D[0] + 2) || !PoliciaStruct.place(sl, 0, cx, cz, false)) continue;
            return true;
        }
        return false;
    }

    static void comisaria(ServerLevel sl, int x0, int z0, int g) {
        PoliciaStruct.prepareH(sl, x0 - 1, z0 - 1, 33, 25, g, 24);
        PoliciaStruct.fill(sl, x0 + 8, g, z0 + 2, x0 + 28, g, z0 + 12, Blocks.STONE_BRICKS);
        PoliciaSchem.paste(sl, x0, g + 1, z0);
        BlockPos home = new BlockPos(x0 + 14, g + 1, z0 + 9);
        PoliciaStruct.npc(sl, 0, (double) x0 + 24.5, (double) g + 1.0, (double) z0 + 9.5, 180.0f, null, 0);
        for (int i = 0; i < AGX.length; ++i) {
            PoliciaStruct.npc(sl, 5, (double) (x0 + AGX[i]) + 0.5, g + 1, (double) (z0 + AGZ[i]) + 0.5, 0.0f, home, 24);
        }
        Sites.get(sl).add(0, new BlockPos(x0 + 18, g + 1, z0 + 15));
    }

    static void obra(ServerLevel sl, int x0, int z0, int g) {
        int x;
        PoliciaStruct.prepare(sl, x0 - 1, z0 - 1, 19, 15, g);
        int x1 = x0 + 16;
        int z1 = z0 + 12;
        PoliciaStruct.fill(sl, x0, g, z0, x1, g, z1, Blocks.DIRT_PATH);
        for (x = x0; x <= x1; x += 2) {
            for (int z = z0; z <= z1; z += 3) {
                PoliciaStruct.set(sl, x, g, z, Blocks.COARSE_DIRT);
            }
        }
        for (x = x0; x <= x1; ++x) {
            if (x < x0 + 7 || x > x0 + 9) {
                sl.setBlock(new BlockPos(x, g + 1, z1), Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
            }
            sl.setBlock(new BlockPos(x, g + 1, z0), Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
        }
        for (int z = z0; z <= z1; ++z) {
            sl.setBlock(new BlockPos(x0, g + 1, z), Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
            sl.setBlock(new BlockPos(x1, g + 1, z), Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
        }
        PoliciaStruct.fill(sl, x0 + 2, g + 1, z0 + 2, x0 + 6, g + 3, z0 + 6, Blocks.SPRUCE_LOG);
        PoliciaStruct.fill(sl, x0 + 3, g + 1, z0 + 3, x0 + 5, g + 3, z0 + 5, Blocks.AIR);
        PoliciaStruct.fill(sl, x0 + 2, g + 4, z0 + 2, x0 + 6, g + 4, z0 + 6, Blocks.OAK_PLANKS);
        PoliciaStruct.fill(sl, x0 + 4, g + 1, z0 + 6, x0 + 4, g + 2, z0 + 6, Blocks.AIR);
        PoliciaStruct.set(sl, x0 + 3, g + 2, z0 + 2, Blocks.GLASS_PANE);
        PoliciaStruct.set(sl, x0 + 5, g + 2, z0 + 2, Blocks.GLASS_PANE);
        PoliciaStruct.set(sl, x0 + 2, g + 2, z0 + 4, Blocks.GLASS_PANE);
        PoliciaStruct.set(sl, x0 + 4, g + 3, z0 + 4, Blocks.LANTERN);
        PoliciaStruct.set(sl, x0 + 3, g + 1, z0 + 3, Blocks.OAK_DOOR);
        PoliciaStruct.set(sl, x0 + 5, g + 1, z0 + 3, Blocks.SPRUCE_PLANKS);
        int bx = x0 + 9;
        int bz = z0 + 2;
        for (int[] c : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
            PoliciaStruct.fill(sl, bx + c[0], g + 1, bz + c[1], bx + c[0], g + 5, bz + c[1], Blocks.SPRUCE_FENCE);
        }
        PoliciaStruct.fill(sl, bx, g, bz, bx + 6, g, bz + 6, Blocks.STONE_BRICKS);
        PoliciaStruct.fill(sl, bx, g + 1, bz, bx + 6, g + 2, bz, Blocks.SPRUCE_FENCE);
        PoliciaStruct.fill(sl, bx, g + 1, bz, bx, g + 2, bz + 6, Blocks.SPRUCE_FENCE);
        PoliciaStruct.fill(sl, bx + 6, g + 1, bz + 3, bx + 6, g + 1, bz + 6, Blocks.SPRUCE_FENCE);
        PoliciaStruct.fill(sl, bx + 1, g + 6, bz, bx + 5, g + 6, bz, Blocks.SCAFFOLDING);
        for (int y = g + 1; y <= g + 7; ++y) {
            PoliciaStruct.set(sl, bx + 7, y, bz + 1, Blocks.OAK_FENCE);
            PoliciaStruct.set(sl, bx + 7, y, bz + 5, Blocks.OAK_FENCE);
        }
        PoliciaStruct.fill(sl, bx + 7, g + 7, bz + 1, bx + 7, g + 7, bz + 5, Blocks.OAK_FENCE);
        PoliciaStruct.chest(sl, x0 + 1, g + 1, z0 + 9, "minecraft:chests/village/village_mason");
        PoliciaStruct.chest(sl, x0 + 2, g + 1, z0 + 9, "minecraft:chests/village/village_toolsmith");
        PoliciaStruct.fill(sl, x0 + 4, g + 1, z0 + 10, x0 + 5, g + 2, z0 + 10, Blocks.SPRUCE_LOG);
        PoliciaStruct.set(sl, x0 + 7, g + 1, z0 + 10, Blocks.SPRUCE_PLANKS);
        PoliciaStruct.set(sl, x0 + 14, g + 1, z0 + 10, Blocks.STONE_BRICKS);
        PoliciaStruct.set(sl, x0 + 14, g + 1, z0 + 11, Blocks.STONE_BRICKS);
        BlockPos home = new BlockPos(x0 + 8, g + 1, z0 + 7);
        PoliciaStruct.npc(sl, 1, (double) x0 + 4.5, g + 1, (double) z0 + 4.5, 0.0f, null, 0);
        PoliciaStruct.npc(sl, 6, (double) x0 + 11.5, g + 1, (double) z0 + 9.5, 0.0f, home, 8);
        PoliciaStruct.npc(sl, 6, (double) x0 + 13.5, g + 1, (double) z0 + 8.5, 0.0f, home, 8);
        PoliciaStruct.npc(sl, 6, (double) x0 + 8.5, g + 1, (double) z0 + 10.5, 0.0f, home, 8);
        Sites.get(sl).add(1, new BlockPos(x0 + 8, g + 1, z1));
    }

    static void casino(ServerLevel sl, int x0, int z0, int g) {
        PoliciaStruct.prepare(sl, x0 - 1, z0 - 1, 15, 15, g);
        int x1 = x0 + 12;
        int z1 = z0 + 12;
        PoliciaStruct.fill(sl, x0, g - 18, z0, x1, g + 5, z1, Blocks.STONE_BRICKS);
        for (int k = 0; k < 4; ++k) {
            int fy = g - 6 * k;
            PoliciaStruct.fill(sl, x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 4, z1 - 1, Blocks.AIR);
            Block carpet = k == 3 ? Blocks.BLACK_CARPET : Blocks.RED_CARPET;
            PoliciaStruct.fill(sl, x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 1, z1 - 1, carpet);
            for (int lx : new int[]{4, 8}) {
                for (int lz : new int[]{4, 8}) {
                    PoliciaStruct.set(sl, x0 + lx, fy + 4, z0 + lz, Blocks.SEA_LANTERN);
                }
            }
            if (k >= 3) continue;
            PoliciaStruct.fill(sl, x0 + 5, fy + 1, z0 + 5, x0 + 7, fy + 1, z0 + 7, Blocks.GREEN_CARPET);
            PoliciaStruct.set(sl, x0 + 6, fy + 1, z0 + 6, Blocks.SMOOTH_STONE);
        }
        for (int[] c : new int[][]{{0, 0}, {12, 0}, {0, 12}, {12, 12}}) {
            PoliciaStruct.fill(sl, x0 + c[0], g + 1, z0 + c[1], x0 + c[0], g + 4, z0 + c[1], Blocks.SMOOTH_STONE);
        }
        PoliciaStruct.fill(sl, x0 + 5, g + 1, z1, x0 + 7, g + 3, z1, Blocks.AIR);
        PoliciaStruct.set(sl, x0 + 4, g + 3, z1 + 1, Blocks.LANTERN);
        PoliciaStruct.set(sl, x0 + 8, g + 3, z1 + 1, Blocks.LANTERN);
        PoliciaStruct.fill(sl, x0 + 4, g + 4, z1, x0 + 8, g + 4, z1, Blocks.SMOOTH_STONE);
        BlockState lad = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = g - 17; y <= g + 4; ++y) {
            PoliciaStruct.set(sl, x0 + 1, y, z0 + 1, lad);
        }
        int f1 = g - 6;
        PoliciaStruct.chest(sl, x0 + 3, f1 + 1, z0 + 9, "minecraft:chests/village/village_toolsmith");
        PoliciaStruct.chest(sl, x0 + 9, f1 + 1, z0 + 3, "minecraft:chests/village/village_toolsmith");
        PoliciaStruct.chest(sl, x0 + 10, f1 + 1, z0 + 10, "minecraft:chests/village/village_weaponsmith");
        int f2 = g - 12;
        BlockPos h2 = new BlockPos(x0 + 6, f2 + 1, z0 + 6);
        PoliciaStruct.npc(sl, 4, (double) x0 + 3.5, f2 + 1, (double) z0 + 8.5, 0.0f, h2, 6);
        PoliciaStruct.npc(sl, 4, (double) x0 + 9.5, f2 + 1, (double) z0 + 4.5, 90.0f, h2, 6);
        PoliciaStruct.npc(sl, 4, (double) x0 + 8.5, f2 + 1, (double) z0 + 9.5, 180.0f, h2, 6);
        PoliciaStruct.chest(sl, x0 + 2, f2 + 1, z0 + 10, "minecraft:chests/simple_dungeon");
        PoliciaStruct.chest(sl, x0 + 10, f2 + 1, z0 + 2, "minecraft:chests/simple_dungeon");
        int f3 = g - 18;
        PoliciaStruct.set(sl, x0 + 10, f3 + 1, z0 + 10, Blocks.EMERALD_BLOCK);
        PoliciaStruct.set(sl, x0 + 9, f3 + 1, z0 + 11, Blocks.SMOOTH_STONE);
        PoliciaStruct.set(sl, x0 + 11, f3 + 1, z0 + 11, Blocks.SMOOTH_STONE);
        BlockPos h3 = new BlockPos(x0 + 9, f3 + 1, z0 + 9);
        PoliciaStruct.npc(sl, 2, (double) x0 + 10.5, (double) f3 + 1.5, (double) z0 + 10.5, 135.0f, null, 0);
        PoliciaStruct.npc(sl, 4, (double) x0 + 8.5, f3 + 1, (double) z0 + 10.5, 135.0f, h3, 4);
        PoliciaStruct.npc(sl, 4, (double) x0 + 10.5, f3 + 1, (double) z0 + 8.5, 135.0f, h3, 4);
        PoliciaStruct.set(sl, x0 + 9, f3 + 1, z0 + 2, Blocks.SPRUCE_PLANKS);
        PoliciaStruct.npc(sl, 3, (double) x0 + 10.5, f3 + 1, (double) z0 + 2.5, 90.0f, null, 0);
        for (int x = x0 + 2; x <= x0 + 6; ++x) {
            if (x == x0 + 4) continue;
            PoliciaStruct.set(sl, x, f3 + 1, z0 + 9, Blocks.IRON_BARS);
            PoliciaStruct.set(sl, x, f3 + 2, z0 + 9, Blocks.IRON_BARS);
        }
        for (int z = z0 + 10; z <= z0 + 11; ++z) {
            for (int y = f3 + 1; y <= f3 + 2; ++y) {
                PoliciaStruct.set(sl, x0 + 2, y, z, Blocks.IRON_BARS);
                PoliciaStruct.set(sl, x0 + 6, y, z, Blocks.IRON_BARS);
            }
        }
        PoliciaStruct.captive(sl, (double) x0 + 3.5, f3 + 1, (double) z0 + 10.5);
        PoliciaStruct.captive(sl, (double) x0 + 5.5, f3 + 1, (double) z0 + 10.5);
        PoliciaStruct.chest(sl, x0 + 11, f3 + 1, z0 + 5, "minecraft:chests/buried_treasure");
        PoliciaStruct.chest(sl, x0 + 11, f3 + 1, z0 + 6, "minecraft:chests/simple_dungeon");
        PoliciaStruct.chest(sl, x0 + 7, f3 + 1, z0 + 11, "minecraft:chests/buried_treasure");
        Sites.get(sl).add(2, new BlockPos(x0 + 6, g + 1, z1 + 1));
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
            default: {
                PoliciaStruct.casino(sl, x0, z0, g);
            }
        }
        return true;
    }

    static void tryGen(ServerPlayer sp, ServerLevel sl) {
        Sites st = Sites.get(sl);
        for (int k = 0; k < 7; ++k) {
            if (k == 0 || st.near(k, sp.blockPosition(), 320) || sl.random.nextInt(100) >= 45) continue;
            for (int i = 0; i < 4; ++i) {
                int z;
                double ang = sl.random.nextDouble() * Math.PI * 2.0;
                double d = 70.0 + sl.random.nextDouble() * 60.0;
                int x = sp.getBlockX() + (int) (Math.cos(ang) * d);
                if (!PoliciaStruct.place(sl, k, x, z = sp.getBlockZ() + (int) (Math.sin(ang) * d), false)) continue;
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
                    PoliciaStruct.genNearVillage(sp, sp.serverLevel());
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
                        for (int i = 0; i < 7; ++i) {
                            if (!SN[i].equals(t)) continue;
                            k = i;
                        }
                        if (k < 0) {
                            c.getSource().sendFailure(Component.literal("Use: comisaria, obra, casino, bandidos, ladrones, atalaya o cueva"));
                            return 0;
                        }
                        if (k == 0 && PoliciaStruct.genNearVillage(p, p.serverLevel())) {
                            c.getSource().sendSuccess(() -> Component.literal("Comisaria colocada junto a la aldea"), false);
                            return 1;
                        }
                        Direction f = p.getDirection();
                        PoliciaStruct.place(p.serverLevel(), k, p.getBlockX() + f.getStepX() * 14, p.getBlockZ() + f.getStepZ() * 14, true);
                        c.getSource().sendSuccess(() -> Component.literal("Construccion colocada"), false);
                        return 1;
                    })));
        }
    }

    public static class Sites extends SavedData {
        final List<List<Long>> l = new ArrayList<>();

        Sites() {
            for (int i = 0; i < 7; ++i) {
                this.l.add(new ArrayList<>());
            }
        }

        static Sites get(ServerLevel sl) {
            return sl.getServer().overworld().getDataStorage().computeIfAbsent(Sites::load, Sites::new, "policia_sites");
        }

        static Sites load(CompoundTag t) {
            Sites s = new Sites();
            for (int i = 0; i < 7; ++i) {
                for (long v : t.getLongArray("k" + i)) {
                    s.l.get(i).add(v);
                }
            }
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            for (int i = 0; i < 7; ++i) {
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
