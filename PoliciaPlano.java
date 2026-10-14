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
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

// Plano del albañil: guarda una construcción (dos esquinas) y luego los
// ayudantes la reconstruyen en otro lugar usando los materiales del jugador
// (inventario y bolsa de los ayudantes).
public class PoliciaPlano {
    static final int MAX_SIDE = 32;
    static final int MAX_BLOCKS = 2048;
    public static final RegistryObject<Item> PLANO = PoliciaShield.ITEMS.register("plano_albanil", () -> new Plano(new Item.Properties().stacksTo(1)));
    static final Map<UUID, Job> JOBS = new HashMap<UUID, Job>();

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaPlano());
    }

    static class Job {
        ResourceDim dim;
        List<BlockPos> pos = new ArrayList<BlockPos>();
        List<BlockState> st = new ArrayList<BlockState>();
        int i;
        int wait;
        int placed;
        String missing = "";
    }

    record ResourceDim(net.minecraft.resources.ResourceKey<Level> key) {
    }

    static boolean saved(ItemStack it) {
        return it.hasTag() && it.getTag().contains("b");
    }

    public static class Plano extends Item {
        public Plano(Item.Properties p) {
            super(p);
        }

        @Override
        public boolean isFoil(ItemStack it) {
            return PoliciaPlano.saved(it);
        }

        @Override
        public void appendHoverText(ItemStack it, Level lv, List<Component> tip, TooltipFlag f) {
            if (PoliciaPlano.saved(it)) {
                CompoundTag t = it.getTag();
                tip.add(Component.literal("§bConstrucción guardada: " + t.getInt("n") + " bloques (" + t.getInt("sx") + "x" + t.getInt("sy") + "x" + t.getInt("sz") + ")"));
                tip.add(Component.literal("§7Clic derecho en el suelo: tus ayudantes la construyen ahí"));
                tip.add(Component.literal("§7Mayús + clic derecho al aire: borrar el plano"));
            } else if (it.hasTag() && it.getTag().contains("ax")) {
                tip.add(Component.literal("§eEsquina 1 marcada: clic derecho en la esquina opuesta"));
            } else {
                tip.add(Component.literal("§7Clic derecho en dos esquinas opuestas de tu construcción para copiarla"));
                tip.add(Component.literal("§7Máximo " + MAX_SIDE + " bloques por lado y " + MAX_BLOCKS + " bloques"));
            }
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack it = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer sp && sp.isShiftKeyDown()) {
                if (JOBS.remove(sp.getUUID()) != null) {
                    PoliciaMod.msg(sp, "Construcción cancelada");
                } else if (it.hasTag()) {
                    it.setTag(null);
                    PoliciaMod.msg(sp, "Plano borrado");
                }
            }
            return InteractionResultHolder.sidedSuccess(it, level.isClientSide);
        }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            if (c.getLevel().isClientSide || !(c.getPlayer() instanceof ServerPlayer sp)) {
                return InteractionResult.SUCCESS;
            }
            ItemStack it = c.getItemInHand();
            if (!"albanil".equals(PoliciaMod.job(sp))) {
                PoliciaMod.msg(sp, "Solo un albañil sabe leer planos");
                return InteractionResult.FAIL;
            }
            if (sp.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            BlockPos at = c.getClickedPos();
            if (PoliciaPlano.saved(it)) {
                PoliciaPlano.start(sp, it, at.above());
                return InteractionResult.SUCCESS;
            }
            CompoundTag t = it.getOrCreateTag();
            if (!t.contains("ax")) {
                t.putInt("ax", at.getX());
                t.putInt("ay", at.getY());
                t.putInt("az", at.getZ());
                PoliciaMod.msg(sp, "Esquina 1 marcada. Ahora clic derecho en la esquina opuesta");
                return InteractionResult.SUCCESS;
            }
            BlockPos a = new BlockPos(t.getInt("ax"), t.getInt("ay"), t.getInt("az"));
            t.remove("ax");
            t.remove("ay");
            t.remove("az");
            PoliciaPlano.capture(sp, it, a, at);
            return InteractionResult.SUCCESS;
        }
    }

    static boolean freePart(BlockState s) {
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        return s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    static void capture(ServerPlayer p, ItemStack it, BlockPos a, BlockPos b) {
        int x0 = Math.min(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ());
        int sx = Math.abs(a.getX() - b.getX()) + 1;
        int sy = Math.abs(a.getY() - b.getY()) + 1;
        int sz = Math.abs(a.getZ() - b.getZ()) + 1;
        if (sx > MAX_SIDE || sy > MAX_SIDE || sz > MAX_SIDE) {
            PoliciaMod.msg(p, "Demasiado grande: máximo " + MAX_SIDE + " bloques por lado. Volvé a marcar la esquina 1");
            return;
        }
        ServerLevel lv = p.serverLevel();
        ListTag pal = new ListTag();
        Map<BlockState, Integer> idx = new HashMap<BlockState, Integer>();
        ArrayList<Integer> data = new ArrayList<Integer>();
        int n = 0;
        for (int y = 0; y < sy; ++y) {
            for (int x = 0; x < sx; ++x) {
                for (int z = 0; z < sz; ++z) {
                    BlockState s = lv.getBlockState(new BlockPos(x0 + x, y0 + y, z0 + z));
                    if (s.isAir() || !s.getFluidState().isEmpty() && s.getBlock().asItem() == Items.AIR) {
                        continue;
                    }
                    if (s.getBlock().asItem() == Items.AIR && !PoliciaPlano.freePart(s)) {
                        continue;
                    }
                    if (++n > MAX_BLOCKS) {
                        PoliciaMod.msg(p, "Demasiados bloques: máximo " + MAX_BLOCKS + ". Volvé a marcar la esquina 1");
                        return;
                    }
                    Integer k = idx.get(s);
                    if (k == null) {
                        k = pal.size();
                        idx.put(s, k);
                        pal.add(NbtUtils.writeBlockState(s));
                    }
                    data.add(x);
                    data.add(y);
                    data.add(z);
                    data.add(k);
                }
            }
        }
        if (n == 0) {
            PoliciaMod.msg(p, "No hay nada construido entre esas dos esquinas");
            return;
        }
        CompoundTag t = it.getOrCreateTag();
        t.put("pal", pal);
        t.putIntArray("b", data);
        t.putInt("n", n);
        t.putInt("sx", sx);
        t.putInt("sy", sy);
        t.putInt("sz", sz);
        t.putInt("f", p.getDirection().get2DDataValue());
        it.setHoverName(Component.literal("Plano de albañil (" + n + " bloques)"));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
        p.sendSystemMessage(Component.literal("§bPlano guardado: " + n + " bloques. Llevalo adonde quieras construir y hacé clic derecho en el suelo (necesitás ayudantes activos)"));
    }

    static void start(ServerPlayer p, ItemStack it, BlockPos origin) {
        if (JOBS.containsKey(p.getUUID())) {
            PoliciaMod.msg(p, "Tus ayudantes ya están construyendo. Mayús + clic derecho al aire para cancelar");
            return;
        }
        if (PoliciaAyudante.mine(p).isEmpty()) {
            PoliciaMod.msg(p, "Necesitás ayudantes activos para construir el plano (habilidad AYUDANTE)");
            return;
        }
        CompoundTag t = it.getTag();
        ListTag pal = t.getList("pal", 10);
        ArrayList<BlockState> states = new ArrayList<BlockState>();
        for (Tag e : pal) {
            states.add(NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), (CompoundTag) e));
        }
        int[] b = t.getIntArray("b");
        int steps = ((p.getDirection().get2DDataValue() - t.getInt("f")) % 4 + 4) % 4;
        Rotation rot = steps == 1 ? Rotation.CLOCKWISE_90 : (steps == 2 ? Rotation.CLOCKWISE_180 : (steps == 3 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.NONE));
        Job j = new Job();
        j.dim = new ResourceDim(p.level().dimension());
        ArrayList<int[]> order = new ArrayList<int[]>();
        for (int i = 0; i + 3 < b.length; i += 4) {
            order.add(new int[]{b[i], b[i + 1], b[i + 2], b[i + 3]});
        }
        // primero lo que está más abajo y los bloques sólidos, después lo que va apoyado
        order.sort((u, v) -> {
            if (u[1] != v[1]) {
                return Integer.compare(u[1], v[1]);
            }
            boolean su = states.get(u[3]).isSolid();
            boolean sv = states.get(v[3]).isSolid();
            return su == sv ? 0 : (su ? -1 : 1);
        });
        for (int[] e : order) {
            BlockPos rel = new BlockPos(e[0], e[1], e[2]).rotate(rot);
            j.pos.add(origin.offset(rel));
            j.st.add(states.get(e[3]).rotate(rot));
        }
        JOBS.put(p.getUUID(), j);
        p.sendSystemMessage(Component.literal("§bTus ayudantes empiezan a construir (" + j.pos.size() + " bloques). Usan los materiales de tu inventario y de su bolsa."));
    }

    static boolean take(ServerPlayer p, Item item) {
        if (p.isCreative()) {
            return true;
        }
        for (int i = 0; i < p.getInventory().getContainerSize(); ++i) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) {
                s.shrink(1);
                return true;
            }
        }
        SimpleContainer bank = PoliciaAyudante.bank(p);
        for (int i = 0; i < bank.getContainerSize(); ++i) {
            ItemStack s = bank.getItem(i);
            if (s.is(item)) {
                s.shrink(1);
                bank.setChanged();
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) {
            return;
        }
        Job j = JOBS.get(p.getUUID());
        if (j == null) {
            return;
        }
        if (!p.isAlive() || !p.level().dimension().equals(j.dim.key())) {
            JOBS.remove(p.getUUID());
            PoliciaMod.msg(p, "Construcción cancelada");
            return;
        }
        int helpers = PoliciaAyudante.mine(p).size();
        if (helpers == 0) {
            if (p.tickCount % 60 == 0) {
                PoliciaMod.msg(p, "Construcción en pausa: invocá a tus ayudantes para seguir (" + j.i + "/" + j.pos.size() + ")");
            }
            return;
        }
        if (j.wait > 0) {
            --j.wait;
            return;
        }
        ServerLevel lv = p.serverLevel();
        int budget = helpers;
        while (budget > 0 && j.i < j.pos.size()) {
            BlockPos bp = j.pos.get(j.i);
            BlockState s = j.st.get(j.i);
            BlockState cur = lv.getBlockState(bp);
            if (cur.equals(s) || !cur.canBeReplaced() || !lv.isLoaded(bp)) {
                ++j.i;
                continue;
            }
            boolean free = PoliciaPlano.freePart(s);
            Item need = s.getBlock().asItem();
            if (!free && need != Items.AIR && !PoliciaPlano.take(p, need)) {
                String nm = new ItemStack(need).getHoverName().getString();
                if (!nm.equals(j.missing) || p.tickCount % 100 == 0) {
                    j.missing = nm;
                    PoliciaMod.msg(p, "Faltan materiales: " + nm + " (" + j.i + "/" + j.pos.size() + ")");
                }
                j.wait = 20;
                return;
            }
            j.missing = "";
            lv.setBlock(bp, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            SoundType snd = s.getSoundType();
            lv.playSound(null, bp, snd.getPlaceSound(), SoundSource.BLOCKS, 0.6f, snd.getPitch() * 0.9f);
            lv.sendParticles(ParticleTypes.CLOUD, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
            ++j.i;
            ++j.placed;
            --budget;
            if (j.placed % 25 == 0) {
                p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + 1);
            }
        }
        j.wait = 3;
        if (p.tickCount % 40 == 0) {
            PoliciaMod.msg(p, "Construyendo: " + j.i + "/" + j.pos.size());
        }
        if (j.i >= j.pos.size()) {
            JOBS.remove(p.getUUID());
            // actualizar formas y conexiones una vez terminada
            for (BlockPos bp : j.pos) {
                BlockState cur = lv.getBlockState(bp);
                BlockState up = Block.updateFromNeighbourShapes(cur, lv, bp);
                if (up != cur && !up.isAir()) {
                    lv.setBlock(bp, up, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            p.sendSystemMessage(Component.literal("§a¡Construcción terminada! " + j.placed + " bloques colocados"));
            lv.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            PoliciaMod.sync(p);
        }
    }

    // ---------------- Planos ya hechos (catálogo del Capataz) ----------------
    static final String[] PREFAB = new String[]{"Casa pequeña", "Torre de vigilancia", "Granja", "Puente"};
    static final int[] PREFAB_PRICE = new int[]{5, 6, 4, 3};

    static class Bld {
        final Map<BlockPos, BlockState> m = new java.util.LinkedHashMap<BlockPos, BlockState>();

        void set(int x, int y, int z, BlockState s) {
            this.m.put(new BlockPos(x, y, z), s);
        }

        void set(int x, int y, int z, Block b) {
            this.set(x, y, z, b.defaultBlockState());
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
            for (int x = x0; x <= x1; ++x) for (int y = y0; y <= y1; ++y) for (int z = z0; z <= z1; ++z) this.set(x, y, z, b);
        }

        void door(int x, int y, int z, Direction f) {
            BlockState d = net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, f);
            this.set(x, y, z, d.setValue(net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.LOWER));
            this.set(x, y + 1, z, d.setValue(net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }

    static Bld prefabBlocks(int k) {
        net.minecraft.world.level.block.Block air = net.minecraft.world.level.block.Blocks.AIR;
        Bld b = new Bld();
        if (k == 0) {
            b.box(0, 0, 0, 6, 0, 6, net.minecraft.world.level.block.Blocks.OAK_PLANKS);
            for (int y = 1; y <= 3; ++y) {
                for (int i = 0; i <= 6; ++i) {
                    b.set(i, y, 0, net.minecraft.world.level.block.Blocks.COBBLESTONE);
                    b.set(i, y, 6, net.minecraft.world.level.block.Blocks.COBBLESTONE);
                    b.set(0, y, i, net.minecraft.world.level.block.Blocks.COBBLESTONE);
                    b.set(6, y, i, net.minecraft.world.level.block.Blocks.COBBLESTONE);
                }
            }
            for (int[] w : new int[][]{{1, 0}, {5, 0}, {0, 3}, {6, 3}, {3, 6}}) {
                b.set(w[0], 2, w[1], net.minecraft.world.level.block.Blocks.GLASS);
            }
            b.m.remove(new BlockPos(3, 1, 0));
            b.m.remove(new BlockPos(3, 2, 0));
            b.door(3, 1, 0, Direction.SOUTH);
            b.box(0, 4, 0, 6, 4, 6, net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS);
            b.box(1, 5, 1, 5, 5, 5, net.minecraft.world.level.block.Blocks.SPRUCE_SLAB);
            BlockState bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH);
            b.set(4, 1, 4, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, BedPart.FOOT));
            b.set(4, 1, 5, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, BedPart.HEAD));
            b.set(1, 1, 5, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
            b.set(5, 1, 1, net.minecraft.world.level.block.Blocks.CHEST);
            b.set(1, 1, 1, net.minecraft.world.level.block.Blocks.TORCH);
            b.set(2, 1, 5, net.minecraft.world.level.block.Blocks.FURNACE);
        } else if (k == 1) {
            b.box(0, 0, 0, 4, 0, 4, net.minecraft.world.level.block.Blocks.STONE_BRICKS);
            for (int y = 1; y <= 9; ++y) {
                for (int i = 0; i <= 4; ++i) {
                    b.set(i, y, 0, net.minecraft.world.level.block.Blocks.STONE_BRICKS);
                    b.set(i, y, 4, net.minecraft.world.level.block.Blocks.STONE_BRICKS);
                    b.set(0, y, i, net.minecraft.world.level.block.Blocks.STONE_BRICKS);
                    b.set(4, y, i, net.minecraft.world.level.block.Blocks.STONE_BRICKS);
                }
            }
            b.m.remove(new BlockPos(2, 1, 0));
            b.m.remove(new BlockPos(2, 2, 0));
            b.door(2, 1, 0, Direction.SOUTH);
            b.set(2, 5, 0, net.minecraft.world.level.block.Blocks.GLASS);
            b.set(0, 6, 2, net.minecraft.world.level.block.Blocks.GLASS);
            b.set(4, 6, 2, net.minecraft.world.level.block.Blocks.GLASS);
            b.box(0, 10, 0, 4, 10, 4, net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS);
            BlockState lad = net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH);
            for (int y = 1; y <= 10; ++y) {
                b.set(2, y, 3, lad);
            }
            for (int i = 0; i <= 4; ++i) {
                b.set(i, 11, 0, net.minecraft.world.level.block.Blocks.SPRUCE_FENCE);
                b.set(i, 11, 4, net.minecraft.world.level.block.Blocks.SPRUCE_FENCE);
                b.set(0, 11, i, net.minecraft.world.level.block.Blocks.SPRUCE_FENCE);
                b.set(4, 11, i, net.minecraft.world.level.block.Blocks.SPRUCE_FENCE);
            }
            for (int[] c : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
                b.set(c[0], 12, c[1], net.minecraft.world.level.block.Blocks.LANTERN);
            }
        } else if (k == 2) {
            for (int x = 1; x <= 7; ++x) {
                for (int z = 1; z <= 7; ++z) {
                    if (x == 4 && z == 4) {
                        b.set(x, 0, z, net.minecraft.world.level.block.Blocks.WATER);
                        continue;
                    }
                    b.set(x, 0, z, net.minecraft.world.level.block.Blocks.FARMLAND);
                    b.set(x, 1, z, net.minecraft.world.level.block.Blocks.WHEAT);
                }
            }
            for (int i = 0; i <= 8; ++i) {
                b.set(i, 1, 0, net.minecraft.world.level.block.Blocks.OAK_FENCE);
                b.set(i, 1, 8, net.minecraft.world.level.block.Blocks.OAK_FENCE);
                b.set(0, 1, i, net.minecraft.world.level.block.Blocks.OAK_FENCE);
                b.set(8, 1, i, net.minecraft.world.level.block.Blocks.OAK_FENCE);
            }
            b.set(4, 1, 0, net.minecraft.world.level.block.Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.SOUTH));
            b.set(0, 2, 0, net.minecraft.world.level.block.Blocks.TORCH);
            b.set(8, 2, 0, net.minecraft.world.level.block.Blocks.TORCH);
            b.set(0, 2, 8, net.minecraft.world.level.block.Blocks.TORCH);
            b.set(8, 2, 8, net.minecraft.world.level.block.Blocks.TORCH);
        } else {
            b.box(0, 0, 0, 2, 0, 12, net.minecraft.world.level.block.Blocks.OAK_PLANKS);
            for (int z = 0; z <= 12; ++z) {
                b.set(0, 1, z, net.minecraft.world.level.block.Blocks.OAK_FENCE);
                b.set(2, 1, z, net.minecraft.world.level.block.Blocks.OAK_FENCE);
            }
            for (int z : new int[]{0, 6, 12}) {
                b.set(0, 2, z, net.minecraft.world.level.block.Blocks.LANTERN);
                b.set(2, 2, z, net.minecraft.world.level.block.Blocks.LANTERN);
            }
        }
        return b;
    }

    static ItemStack prefab(int k) {
        Bld b = PoliciaPlano.prefabBlocks(k);
        ItemStack it = new ItemStack((net.minecraft.world.level.ItemLike) PLANO.get());
        CompoundTag t = it.getOrCreateTag();
        ListTag pal = new ListTag();
        Map<BlockState, Integer> idx = new HashMap<BlockState, Integer>();
        ArrayList<Integer> data = new ArrayList<Integer>();
        int mx = 0;
        int my = 0;
        int mz = 0;
        for (Map.Entry<BlockPos, BlockState> e : b.m.entrySet()) {
            BlockState s = e.getValue();
            Integer i = idx.get(s);
            if (i == null) {
                i = pal.size();
                idx.put(s, i);
                pal.add(NbtUtils.writeBlockState(s));
            }
            BlockPos p = e.getKey();
            data.add(p.getX());
            data.add(p.getY());
            data.add(p.getZ());
            data.add(i);
            mx = Math.max(mx, p.getX());
            my = Math.max(my, p.getY());
            mz = Math.max(mz, p.getZ());
        }
        t.put("pal", pal);
        t.putIntArray("b", data);
        t.putInt("n", b.m.size());
        t.putInt("sx", mx + 1);
        t.putInt("sy", my + 1);
        t.putInt("sz", mz + 1);
        // se construye alejándose de vos, con la entrada de tu lado
        t.putInt("f", Direction.SOUTH.get2DDataValue());
        it.setHoverName(Component.literal("Plano: " + PREFAB[k]));
        return it;
    }

    static Direction dir(int v) {
        return Direction.from2DDataValue(v);
    }
}
