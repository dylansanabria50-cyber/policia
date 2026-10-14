package com.example.policia;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

// Dinamita de demolición controlada del albañil: marcás dos esquinas y al
// detonarla rompe SOLO los bloques de esa zona, sin dañar nada alrededor.
// Recuperás la mitad de los bloques.
public class PoliciaDemolicion {
    public static final RegistryObject<Item> DINAMITA = PoliciaShield.ITEMS.register("dinamita_albanil", () -> new Dyn(new Item.Properties().stacksTo(16)));
    static final int MAX_SIDE = 16;
    static final Map<ServerPlayer, Fuse> FUSES = new HashMap<ServerPlayer, Fuse>();
    static final DustParticleOptions MARK = new DustParticleOptions(new Vector3f(1.0f, 0.35f, 0.1f), 1.0f);

    static class Fuse {
        BlockPos a;
        BlockPos b;
        int t = 60;
    }

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaDemolicion());
    }

    public static class Dyn extends Item {
        public Dyn(Item.Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack it, Level lv, List<Component> tip, TooltipFlag f) {
            tip.add(Component.literal("§7Clic derecho en dos esquinas: marca la zona (máx. " + MAX_SIDE + " por lado)"));
            tip.add(Component.literal("§7Mayús + clic derecho al aire: detonar"));
            tip.add(Component.literal("§7Solo rompe lo marcado y devuelve la mitad de los bloques"));
            if (it.hasTag() && it.getTag().contains("bx")) {
                tip.add(Component.literal("§6Zona lista para detonar"));
            } else if (it.hasTag() && it.getTag().contains("ax")) {
                tip.add(Component.literal("§eEsquina 1 marcada"));
            }
        }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            if (c.getLevel().isClientSide || !(c.getPlayer() instanceof ServerPlayer p)) {
                return InteractionResult.SUCCESS;
            }
            if (!"albanil".equals(PoliciaMod.job(p))) {
                PoliciaMod.msg(p, "Solo un albañil sabe usar la dinamita de demolición");
                return InteractionResult.FAIL;
            }
            if (p.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            CompoundTag t = c.getItemInHand().getOrCreateTag();
            BlockPos at = c.getClickedPos();
            if (!t.contains("ax") || t.contains("bx")) {
                t.remove("bx");
                t.remove("by");
                t.remove("bz");
                t.putInt("ax", at.getX());
                t.putInt("ay", at.getY());
                t.putInt("az", at.getZ());
                PoliciaMod.msg(p, "Esquina 1 marcada. Ahora la esquina opuesta");
            } else {
                int ax = t.getInt("ax");
                int ay = t.getInt("ay");
                int az = t.getInt("az");
                if (Math.abs(ax - at.getX()) >= MAX_SIDE || Math.abs(ay - at.getY()) >= MAX_SIDE || Math.abs(az - at.getZ()) >= MAX_SIDE) {
                    PoliciaMod.msg(p, "Demasiado grande: máximo " + MAX_SIDE + " bloques por lado");
                    return InteractionResult.FAIL;
                }
                t.putInt("bx", at.getX());
                t.putInt("by", at.getY());
                t.putInt("bz", at.getZ());
                PoliciaMod.msg(p, "Zona marcada. Alejate y hacé Mayús + clic derecho al aire para detonar");
            }
            p.level().playSound(null, at, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.8f, 1.4f);
            return InteractionResult.SUCCESS;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack it = player.getItemInHand(hand);
            if (level.isClientSide || !(player instanceof ServerPlayer p) || !p.isShiftKeyDown()) {
                return InteractionResultHolder.pass(it);
            }
            CompoundTag t = it.getTag();
            if (t == null || !t.contains("bx")) {
                PoliciaMod.msg(p, "Primero marcá las dos esquinas de lo que querés demoler");
                return InteractionResultHolder.fail(it);
            }
            if (FUSES.containsKey(p)) {
                return InteractionResultHolder.fail(it);
            }
            Fuse f = new Fuse();
            f.a = new BlockPos(t.getInt("ax"), t.getInt("ay"), t.getInt("az"));
            f.b = new BlockPos(t.getInt("bx"), t.getInt("by"), t.getInt("bz"));
            if (p.distanceToSqr(f.a.getX(), f.a.getY(), f.a.getZ()) > 64.0 * 64.0) {
                PoliciaMod.msg(p, "Estás demasiado lejos de la zona marcada");
                return InteractionResultHolder.fail(it);
            }
            FUSES.put(p, f);
            it.shrink(1);
            if (!it.isEmpty()) {
                it.setTag(null);
            }
            PoliciaMod.msg(p, "§c¡Mecha encendida! 3 segundos...");
            return InteractionResultHolder.success(it);
        }
    }

    static void outline(ServerLevel sl, BlockPos a, BlockPos b) {
        int x0 = Math.min(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX()) + 1;
        int y1 = Math.max(a.getY(), b.getY()) + 1;
        int z1 = Math.max(a.getZ(), b.getZ()) + 1;
        for (int[] c : new int[][]{{x0, y0, z0}, {x1, y0, z0}, {x0, y0, z1}, {x1, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x0, y1, z1}, {x1, y1, z1}}) {
            sl.sendParticles(MARK, c[0], c[1], c[2], 2, 0.05, 0.05, 0.05, 0.0);
            sl.sendParticles(ParticleTypes.SMOKE, c[0], c[1], c[2], 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    static void blow(ServerPlayer p, Fuse f) {
        ServerLevel sl = p.serverLevel();
        int x0 = Math.min(f.a.getX(), f.b.getX());
        int y0 = Math.min(f.a.getY(), f.b.getY());
        int z0 = Math.min(f.a.getZ(), f.b.getZ());
        int x1 = Math.max(f.a.getX(), f.b.getX());
        int y1 = Math.max(f.a.getY(), f.b.getY());
        int z1 = Math.max(f.a.getZ(), f.b.getZ());
        Map<Item, Integer> got = new HashMap<Item, Integer>();
        int n = 0;
        for (int y = y1; y >= y0; --y) {
            for (int x = x0; x <= x1; ++x) {
                for (int z = z0; z <= z1; ++z) {
                    BlockPos bp = new BlockPos(x, y, z);
                    BlockState s = sl.getBlockState(bp);
                    if (s.isAir() || s.getDestroySpeed(sl, bp) < 0.0f || !s.getFluidState().isEmpty() && s.getBlock().asItem() == Items.AIR) continue;
                    BlockEntity be = sl.getBlockEntity(bp);
                    if (be instanceof Container ct) {
                        Containers.dropContents(sl, bp, ct);
                    }
                    Item it = s.getBlock().asItem();
                    if (it != Items.AIR && !PoliciaPlano.freePart(s)) {
                        got.merge(it, 1, Integer::sum);
                    }
                    sl.setBlock(bp, Blocks.AIR.defaultBlockState(), 2 | 16);
                    ++n;
                }
            }
        }
        // actualizar vecinos una vez
        for (int x = x0 - 1; x <= x1 + 1; ++x) {
            for (int z = z0 - 1; z <= z1 + 1; ++z) {
                for (int y = y0 - 1; y <= y1 + 1; ++y) {
                    if (x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1) continue;
                    BlockPos bp = new BlockPos(x, y, z);
                    sl.updateNeighborsAt(bp, sl.getBlockState(bp).getBlock());
                }
            }
        }
        for (Map.Entry<Item, Integer> e : got.entrySet()) {
            int half = e.getValue() / 2;
            while (half > 0) {
                int c = Math.min(64, half);
                PoliciaBoss.give(p, new ItemStack(e.getKey(), c));
                half -= c;
            }
        }
        double cx = (x0 + x1) / 2.0 + 0.5;
        double cy = (y0 + y1) / 2.0 + 0.5;
        double cz = (z0 + z1) / 2.0 + 0.5;
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0);
        sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 40, (x1 - x0) / 2.0, (y1 - y0) / 2.0, (z1 - z0) / 2.0, 0.02);
        sl.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0f, 0.9f);
        p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + Math.min(5, n / 100 + 1));
        p.sendSystemMessage(Component.literal("§6Demolición controlada: " + n + " bloques. Recuperaste la mitad de los materiales."));
        if (n >= 300) {
            PoliciaDiario.add(sl, "El albañil " + p.getName().getString() + " demolió una construcción de " + n + " bloques");
        }
        PoliciaMod.sync(p);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || FUSES.isEmpty()) {
            return;
        }
        java.util.Iterator<Map.Entry<ServerPlayer, Fuse>> it = FUSES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServerPlayer, Fuse> en = it.next();
            ServerPlayer p = en.getKey();
            Fuse f = en.getValue();
            if (p.isRemoved()) {
                it.remove();
                continue;
            }
            ServerLevel sl = p.serverLevel();
            if (f.t % 4 == 0) {
                PoliciaDemolicion.outline(sl, f.a, f.b);
            }
            if (f.t % 20 == 0) {
                sl.playSound(null, f.a, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0f, 1.0f + (60 - f.t) / 40.0f);
                PoliciaMod.msg(p, "§c" + (f.t / 20) + "...");
            }
            if (f.t == 40) {
                sl.playSound(null, f.a, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            if (--f.t <= 0) {
                it.remove();
                PoliciaDemolicion.blow(p, f);
            }
        }
    }
}
