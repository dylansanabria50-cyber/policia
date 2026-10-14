package com.example.policia;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

// Vision de rayos X de minerales cercanos para el nivel III de AYUDANTE del
// Albanil, mas el recuadro de seleccion de area de REFUGIO.
public class PoliciaXray {
    static final List<BlockPos> POS = new ArrayList<>();
    static final List<float[]> COL = new ArrayList<>();
    static final int R = 4;
    static boolean zoning;
    static int zl = 8;
    static int zw = 3;

    static void scroll(InputEvent.MouseScrollingEvent e) {
        if (!zoning) {
            return;
        }
        e.setCanceled(true);
        int s = e.getScrollDelta() > 0.0 ? 1 : -1;
        if (Screen.hasShiftDown()) {
            zw = Math.max(1, Math.min(9, zw + s));
        } else {
            zl = Math.max(1, Math.min(24, zl + s));
        }
    }

    // Menas que muestran los ayudantes (radio 4, segun su nivel).
    static boolean helpers() {
        PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
        PoliciaMod.Sync ms = PoliciaClient.mine();
        return s != null && s.ht > 0 && s.hl >= 1 && ms != null && ms.jb == 2;
    }

    // Pasiva Ojo de minero: con el modo albanil activo ve todas las menas a 3 bloques.
    static boolean eye() {
        PoliciaMod.Sync ms = PoliciaClient.mine();
        return ms != null && ms.jb == 2 && ms.on;
    }

    static boolean active() {
        return PoliciaXray.helpers() || PoliciaXray.eye();
    }

    static float[] color(String p) {
        if (p.contains("coal")) {
            return new float[]{0.75f, 0.75f, 0.75f};
        }
        if (p.contains("iron")) {
            return new float[]{1.0f, 0.75f, 0.55f};
        }
        if (p.contains("copper")) {
            return new float[]{1.0f, 0.5f, 0.2f};
        }
        if (p.contains("gold")) {
            return new float[]{1.0f, 0.9f, 0.1f};
        }
        if (p.contains("redstone")) {
            return new float[]{1.0f, 0.1f, 0.1f};
        }
        if (p.contains("lapis")) {
            return new float[]{0.25f, 0.45f, 1.0f};
        }
        if (p.contains("emerald")) {
            return new float[]{0.1f, 1.0f, 0.3f};
        }
        if (p.contains("diamond")) {
            return new float[]{0.2f, 1.0f, 1.0f};
        }
        if (p.contains("quartz")) {
            return new float[]{1.0f, 1.0f, 1.0f};
        }
        if (p.contains("debris")) {
            return new float[]{0.7f, 0.3f, 0.9f};
        }
        return new float[]{1.0f, 0.4f, 1.0f};
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !active()) {
            POS.clear();
            COL.clear();
            return;
        }
        if (mc.player.tickCount % 5 != 0) {
            return;
        }
        boolean hp = PoliciaXray.helpers();
        boolean ey = PoliciaXray.eye();
        int lv = hp ? Math.max(1, Math.min(3, PoliciaAlbanil.CL.hl)) : 0;
        POS.clear();
        COL.clear();
        BlockPos c = mc.player.blockPosition();
        for (int dx = -4; dx <= 4; ++dx) {
            for (int dy = -4; dy <= 4; ++dy) {
                for (int dz = -4; dz <= 4; ++dz) {
                    BlockPos bp = c.offset(dx, dy, dz);
                    BlockState st = mc.level.getBlockState(bp);
                    if (st.isAir() || (!st.is(Tags.Blocks.ORES) && !st.is(Blocks.ANCIENT_DEBRIS))) {
                        continue;
                    }
                    ResourceLocation key = ForgeRegistries.BLOCKS.getKey(st.getBlock());
                    if (key == null) continue;
                    String p = key.getPath();
                    boolean byHelpers = hp && (lv == 1 ? p.contains("coal") : (lv == 2 ? !p.contains("diamond") && !p.contains("debris") && !p.contains("netherite") : true));
                    boolean byEye = ey && Math.abs(dx) <= 3 && Math.abs(dy) <= 3 && Math.abs(dz) <= 3;
                    boolean ok = byHelpers || byEye;
                    if (!ok) continue;
                    POS.add(bp);
                    COL.add(color(p));
                }
            }
        }
    }

    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || (POS.isEmpty() && !zoning)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        RenderSystem.disableDepthTest();
        VertexConsumer vc = bs.getBuffer(RenderType.lines());
        for (int i = 0; i < POS.size() && i < COL.size(); ++i) {
            BlockPos b = POS.get(i);
            float[] c = COL.get(i);
            LevelRenderer.renderLineBox(ps, vc, b.getX() - cam.x, b.getY() - cam.y, b.getZ() - cam.z,
                    (b.getX() + 1) - cam.x, (b.getY() + 1) - cam.y, (b.getZ() + 1) - cam.z, c[0], c[1], c[2], 1.0f);
        }
        if (zoning && mc.player != null) {
            Direction f = mc.player.getDirection();
            Direction r = f.getClockWise();
            BlockPos o = mc.player.blockPosition();
            int lo = -((zw - 1) / 2);
            int hi = zw / 2;
            BlockPos a = PoliciaAlbanil.at(o, f, r, 1, lo, 0);
            BlockPos b = PoliciaAlbanil.at(o, f, r, zl, hi, 2);
            double x0 = Math.min(a.getX(), b.getX());
            double y0 = Math.min(a.getY(), b.getY());
            double z0 = Math.min(a.getZ(), b.getZ());
            double x1 = Math.max(a.getX(), b.getX()) + 1;
            double y1 = Math.max(a.getY(), b.getY()) + 1;
            double z1 = Math.max(a.getZ(), b.getZ()) + 1;
            LevelRenderer.renderLineBox(ps, vc, x0 - cam.x, y0 - cam.y, z0 - cam.z, x1 - cam.x, y1 - cam.y, z1 - cam.z, 0.2f, 1.0f, 0.3f, 1.0f);
        }
        bs.endBatch(RenderType.lines());
        RenderSystem.enableDepthTest();
    }
}
