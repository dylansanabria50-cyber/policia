package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;

// Combate RPG (cliente): numeros de dano flotantes, barra de vida sobre los
// monstruos, tecla de esquivar y barra de energia.
public class PoliciaCombateCliente {
    static final KeyMapping K_ESQUIVAR = new KeyMapping("RPG: esquivar (rodar)", 342, "Policia");
    static volatile float ENERGIA = 100.0f;
    static volatile float ENERGIA_MAX = 100.0f;

    // numero flotante: x, y, z, vy, vida (ticks), cant, tipo, dx
    static final List<double[]> NUMS = new ArrayList<double[]>();
    // barras: id -> {vida mostrada, ultimo golpe (tick), rastro}
    static final Map<Integer, float[]> BARRAS = new HashMap<Integer, float[]>();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PoliciaCombateCliente::tick);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCombateCliente::dibujarNumeros);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCombateCliente::barra);
    }

    static void numero(int ent, float cant, int tipo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity e = mc.level.getEntity(ent);
        if (e == null || (e == mc.player && tipo == 5 && mc.options.getCameraType().isFirstPerson())) {
            return;
        }
        var r = mc.level.random;
        NUMS.add(new double[]{e.getX() + (r.nextDouble() - 0.5) * 0.6, e.getY() + e.getBbHeight() + 0.2, e.getZ() + (r.nextDouble() - 0.5) * 0.6, 0.06, 0, cant, tipo, (r.nextDouble() - 0.5) * 0.03});
        float[] b = BARRAS.computeIfAbsent(ent, k -> new float[]{-1, 0, -1});
        b[1] = mc.level.getGameTime();
        if (NUMS.size() > 80) {
            NUMS.remove(0);
        }
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            NUMS.clear();
            BARRAS.clear();
            return;
        }
        Iterator<double[]> it = NUMS.iterator();
        while (it.hasNext()) {
            double[] n = it.next();
            n[4] += 1;
            n[1] += n[3];
            n[0] += n[7];
            n[3] *= 0.85;
            if (n[4] > 26) {
                it.remove();
            }
        }
        // Esquivar: hacia donde apretás las teclas (o para atras).
        while (K_ESQUIVAR.consumeClick()) {
            if (mc.screen != null) {
                continue;
            }
            float f = mc.player.input.forwardImpulse;
            float s = mc.player.input.leftImpulse;
            double yaw = Math.toRadians(mc.player.getYRot());
            float dx = (float) (-Math.sin(yaw) * f + Math.cos(yaw) * s);
            float dz = (float) (Math.cos(yaw) * f + Math.sin(yaw) * s);
            PoliciaRed.CH.sendToServer(new PoliciaRed.Esquivar(dx, dz));
        }
        if (mc.level.getGameTime() % 100 == 0) {
            BARRAS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    static int color(int tipo) {
        return switch (tipo) {
            case 1 -> 0xFFE040;
            case 2 -> 0xFF9A30;
            case 3 -> 0x60E0FF;
            case 4 -> 0xC0C0C0;
            case 5 -> 0xFF5050;
            default -> 0xFFFFFF;
        };
    }

    static String texto(double cant, int tipo) {
        String n = cant >= 10 ? String.valueOf((int) Math.round(cant)) : String.format("%.1f", cant).replace(".0", "");
        return switch (tipo) {
            case 1 -> "¡" + n + "!";
            case 2 -> n + " ✦";
            case 3 -> "¡PARADA!";
            case 4 -> "Esquivado";
            case 5 -> "-" + n;
            default -> n;
        };
    }

    // ---------------- Numeros en el mundo ----------------
    static void dibujarNumeros(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || NUMS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float pt = e.getPartialTick();
        for (double[] n : NUMS) {
            double y = n[1] + n[3] * pt;
            float vida = (float) ((n[4] + pt) / 26.0);
            int tipo = (int) n[6];
            float esc = 0.025f * (tipo == 1 ? 1.5f : tipo == 2 ? 1.3f : 1.0f) * (vida < 0.15f ? 0.6f + vida / 0.15f * 0.4f : 1.0f);
            int alfa = (int) (255 * Mth.clamp((1.0f - vida) * 3.0f, 0.0f, 1.0f));
            if (alfa < 8) {
                continue;
            }
            ps.pushPose();
            ps.translate(n[0] - cp.x, y - cp.y, n[2] - cp.z);
            ps.mulPose(cam.rotation());
            ps.scale(-esc, -esc, esc);
            String t = texto(n[5], tipo);
            float x = -font.width(t) / 2.0f;
            Matrix4f m = ps.last().pose();
            int col = alfa << 24 | color(tipo);
            font.drawInBatch(t, x + 1, 1, alfa << 24, false, m, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            font.drawInBatch(t, x, 0, col, false, m, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();
    }

    // ---------------- Barra de vida sobre los monstruos ----------------
    static void barra(RenderLivingEvent.Post<?, ?> e) {
        LivingEntity le = e.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (le instanceof Player || le.isInvisible() || mc.player == null || mc.level == null || !le.isAlive() || mc.options.hideGui) {
            return;
        }
        boolean mirando = mc.crosshairPickEntity == le;
        float[] b = BARRAS.get(le.getId());
        long ahora = mc.level.getGameTime();
        boolean reciente = b != null && ahora - (long) b[1] < 100;
        boolean elite = le.hasCustomName() && le.getCustomName().getString().contains("★");
        if (!mirando && !reciente) {
            return;
        }
        if (le.distanceToSqr(mc.player) > 32 * 32) {
            return;
        }
        if (b == null) {
            b = new float[]{-1, 0, -1};
            BARRAS.put(le.getId(), b);
        }
        float k = Mth.clamp(le.getHealth() / le.getMaxHealth(), 0.0f, 1.0f);
        if (b[0] < 0) {
            b[0] = k;
            b[2] = k;
        }
        b[0] += (k - b[0]) * 0.3f;
        b[2] = b[2] > b[0] ? b[2] - 0.006f : b[0];
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(0.0, le.getBbHeight() + (le.hasCustomName() || mirando ? 0.75 : 0.5), 0.0);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.textBackgroundSeeThrough());
        int w = 20;
        caja(vc, m, -w - 1, -1, w + 1, 3, 0xC0000000, 0.0f);
        caja(vc, m, -w, 0, -w + 2 * w * b[2], 2, 0xFFFFFFFF, -0.01f);
        int col = elite ? 0xFFFFA020 : k > 0.5f ? 0xFF50D050 : k > 0.25f ? 0xFFE0C030 : 0xFFE04030;
        caja(vc, m, -w, 0, -w + 2 * w * b[0], 2, col, -0.02f);
        ps.popPose();
    }

    static void caja(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb, float z) {
        int a = argb >>> 24;
        int r = argb >> 16 & 255;
        int g = argb >> 8 & 255;
        int b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(0xF000F0).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(0xF000F0).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(0xF000F0).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(0xF000F0).endVertex();
    }

    // ---------------- Barra de energia (abajo a la izquierda) ----------------
    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || !PoliciaConfig.hudNivel()) {
            return;
        }
        float k = ENERGIA_MAX <= 0 ? 1.0f : Mth.clamp(ENERGIA / ENERGIA_MAX, 0.0f, 1.0f);
        int x = 6;
        int y = sh - 12;
        g.fill(x, y, x + 70, y + 4, 0xAA000000);
        g.fill(x + 1, y + 1, x + 1 + (int) (68 * k), y + 3, k < 0.2f ? 0xFF6060FF : 0xFF40A0FF);
        if (k < 1.0f) {
            g.drawString(mc.font, "§9" + (int) ENERGIA, x + 74, y - 2, 0xFFFFFF);
        }
    }
}
