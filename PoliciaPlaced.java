package com.example.policia;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Recuerda que bloques fueron puestos por un jugador (en vez de generados
// por el mundo), para que las misiones de la policia no confundan terreno
// natural con construcciones.
public class PoliciaPlaced extends SavedData {
    final Set<Long> set = new HashSet<>();

    static PoliciaPlaced get(Level l) {
        if (!(l instanceof ServerLevel sl)) {
            return null;
        }
        return sl.getDataStorage().computeIfAbsent(PoliciaPlaced::load, PoliciaPlaced::new, "policia_placed");
    }

    static PoliciaPlaced load(CompoundTag t) {
        PoliciaPlaced p = new PoliciaPlaced();
        for (long l : t.getLongArray("p")) {
            p.set.add(l);
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        long[] a = new long[this.set.size()];
        int i = 0;
        for (long l : this.set) {
            a[i++] = l;
        }
        t.putLongArray("p", a);
        return t;
    }

    static boolean has(Level l, BlockPos bp) {
        PoliciaPlaced p = get(l);
        return p != null && p.set.contains(bp.asLong());
    }

    // Heuristica aproximada: bloques tipicos de terreno/vegetacion natural
    // o decoracion generada por estructuras del juego, nunca puestos por
    // un jugador a mano.
    static boolean natural(BlockState st) {
        if (st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES) || st.is(BlockTags.DIRT)
                || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.SAND)
                || st.is(BlockTags.ICE) || st.is(BlockTags.SNOW) || st.is(BlockTags.FLOWERS)
                || st.is(BlockTags.STONE_ORE_REPLACEABLES) || st.is(Tags.Blocks.ORES)
                || st.is(BlockTags.CORAL_BLOCKS)) {
            return true;
        }
        Block b = st.getBlock();
        return b == Blocks.GRASS || b == Blocks.TALL_GRASS || b == Blocks.FERN
                || b == Blocks.DEAD_BUSH || b == Blocks.VINE || b == Blocks.LILY_PAD
                || b == Blocks.SUGAR_CANE || b == Blocks.BAMBOO || b == Blocks.SEAGRASS
                || b == Blocks.KELP || b == Blocks.WATER || b == Blocks.LAVA
                || b == Blocks.GRAVEL || b == Blocks.CLAY || b == Blocks.MOSS_CARPET
                || b == Blocks.BROWN_MUSHROOM || b == Blocks.RED_MUSHROOM
                || b == Blocks.AZALEA || b == Blocks.FLOWERING_AZALEA || b == Blocks.GLOW_LICHEN;
    }

    public static class Ev {
        @SubscribeEvent
        public void place(BlockEvent.EntityPlaceEvent e) {
            if (!(e.getEntity() instanceof Player) || !(e.getLevel() instanceof ServerLevel sl)) {
                return;
            }
            PoliciaPlaced p = get(sl);
            if (p == null) {
                return;
            }
            BlockPos bp = e.getPos();
            p.set.add(bp.asLong());
            BlockState st = e.getPlacedBlock();
            if (st.hasProperty(BlockStateProperties.HALF)) {
                p.set.add(bp.above().asLong());
            }
            if (st.getBlock() instanceof BedBlock && st.hasProperty((Property) BedBlock.FACING)) {
                p.set.add(bp.relative(st.getValue(BedBlock.FACING)).asLong());
            }
            p.setDirty();
        }

        @SubscribeEvent
        public void brk(BlockEvent.BreakEvent e) {
            LevelAccessor levelAccessor = e.getLevel();
            if (!(levelAccessor instanceof ServerLevel sl)) {
                return;
            }
            PoliciaPlaced p = get(sl);
            if (p != null && p.set.remove(e.getPos().asLong())) {
                p.setDirty();
            }
        }
    }
}
