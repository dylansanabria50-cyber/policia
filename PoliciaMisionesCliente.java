package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

// Etapa 5 (cliente): diario de misiones, HUD de las diarias y los
// marcadores ! / ? que flotan sobre los jefes de oficio.
public class PoliciaMisionesCliente {
    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PoliciaMisionesCliente::marcador);
    }

    static CompoundTag d() {
        CompoundTag d = PoliciaRPG.CLIENTE;
        return d == null ? new CompoundTag() : d;
    }

    static String texto(String id) {
        PoliciaDiarias.Def def = PoliciaDiarias.buscar(id);
        return def == null ? "?" : def.texto;
    }

    static int meta(String id) {
        PoliciaDiarias.Def def = PoliciaDiarias.buscar(id);
        return def == null ? 1 : def.meta;
    }

    // ---------------- Marcadores sobre los jefes ----------------
    // Devuelve el tipo de jefe (0 policia, 1 capataz, 2 ladrones, 7 herrero)
    // o -1 si la entidad no es un jefe que da misiones.
    static int jefe(LivingEntity e) {
        if (e instanceof PoliciaBoss.BossNpc b) {
            int k = b.kind();
            return k == 0 || k == 1 || k == 2 || k == 7 ? k : -1;
        }
        if (e instanceof PoliciaMision.Pj pj && pj.kind() == 10) {
            return 0;
        }
        return -1;
    }

    static int jefeDe(String oficio) {
        return switch (oficio) {
            case "policia" -> 0;
            case "albanil" -> 1;
            case "ladron" -> 2;
            case "herrero" -> 7;
            default -> -1;
        };
    }

    static void marcador(RenderLivingEvent.Post<?, ?> e) {
        LivingEntity ent = e.getEntity();
        int k = jefe(ent);
        if (k < 0) {
            return;
        }
        CompoundTag d = d();
        String oficio = d.getString("oficio");
        if ("medico".equals(oficio) || "mercader".equals(oficio)) {
            return;
        }
        int mio = jefeDe(oficio);
        if (mio >= 0 && mio != k) {
            return;
        }
        int estado = d.getInt("jm_estado");
        String s = estado == 0 ? "!" : "?";
        int color = estado == 1 ? 0xFFB0B0B0 : 0xFFFFD02A;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(ent) > 48 * 48) {
            return;
        }
        float t = ent.tickCount + e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(0.0, ent.getBbHeight() + 0.95 + Math.sin(t * 0.12) * 0.08, 0.0);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float sc = 0.075f;
        ps.scale(-sc, -sc, sc);
        Font f = mc.font;
        Matrix4f m = ps.last().pose();
        float x = -f.width(s) / 2.0f;
        // contorno oscuro y la letra brillante encima
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    f.drawInBatch(s, x + dx * 0.6f, dy * 0.6f, 0xFF3A2A00, false, m, e.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
                }
            }
        }
        ps.translate(0, 0, -0.02f);
        f.drawInBatch(s, x, 0, color, false, ps.last().pose(), e.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
        ps.popPose();
    }

    // ---------------- HUD de las diarias (a la derecha) ----------------
    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || !PoliciaConfig.hudDiarias()) {
            return;
        }
        CompoundTag di = d().getCompound("diarias");
        if (!di.contains("dia")) {
            return;
        }
        int x = sw - 150;
        int y = sh / 2 - 40;
        g.fill(x - 4, y - 4, sw - 4, y + 48, 0x60000000);
        g.drawString(mc.font, "§6Diarias" + (di.getInt("racha") > 0 ? " §7· racha " + di.getInt("racha") : ""), x, y, 0xFFFFFF);
        for (int i = 0; i < 3; i++) {
            CompoundTag t = di.getCompound("t" + i);
            String id = t.getString("id");
            boolean ok = t.getBoolean("hecha");
            String linea = (ok ? "§a✔ §7" : "§f• ") + mc.font.plainSubstrByWidth(texto(id), 104) + (ok ? "" : " §e" + t.getInt("prog") + "/" + meta(id));
            g.drawString(mc.font, linea, x, y + 12 + i * 11, 0xFFFFFF);
        }
    }

    // ---------------- Diario de misiones ----------------
    public static class Diario extends Screen {
        static final int W = 300;
        static final int H = 210;
        int x0;
        int y0;

        public Diario() {
            super(Component.literal("Diario de misiones"));
        }

        @Override
        protected void init() {
            this.x0 = (this.width - W) / 2;
            this.y0 = (this.height - H) / 2;
            this.addRenderableWidget(Button.builder(Component.literal("Ficha"), b -> Minecraft.getInstance().setScreen(new PoliciaFicha()))
                    .bounds(this.x0 + 12, this.y0 + H - 26, 80, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                    .bounds(this.x0 + W - 92, this.y0 + H - 26, 80, 18).build());
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            this.renderBackground(g);
            int x = this.x0;
            int y = this.y0;
            g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF6B4B22);
            g.fill(x, y, x + W, y + H, 0xF0E8D9B5);
            g.fill(x, y, x + W, y + 22, 0xFF3A2B18);
            g.drawCenteredString(this.font, "§6§lDIARIO DE MISIONES", x + W / 2, y + 7, 0xFFFFFF);
            CompoundTag d = d();
            // mision del jefe
            g.drawString(this.font, "Misión del jefe", x + 12, y + 30, 0xFF5A3A10, false);
            int estado = d.getInt("jm_estado");
            if (estado == 0) {
                g.drawString(this.font, "Sin misión. Buscá el ! sobre tu jefe de oficio.", x + 12, y + 42, 0xFF4A4030, false);
            } else {
                String desc = d.getString("jm_desc");
                int yy = y + 42;
                for (var l : this.font.split(Component.literal(desc), W - 24)) {
                    g.drawString(this.font, l, x + 12, yy, 0xFF2A2418, false);
                    yy += 10;
                    if (yy > y + 70) {
                        break;
                    }
                }
                String est = estado == 2 ? "Lista: volvé con tu jefe (?) para cobrar" : "Progreso: " + d.getInt("jm_prog") + " / " + d.getInt("jm_need");
                g.drawString(this.font, est, x + 12, y + 74, estado == 2 ? 0xFF1E7A2A : 0xFF6A5A40, false);
            }
            // diarias
            CompoundTag di = d.getCompound("diarias");
            g.fill(x + 10, y + 88, x + W - 10, y + 89, 0x40000000);
            g.drawString(this.font, "Misiones diarias" + (di.getInt("racha") > 0 ? "  ·  racha " + di.getInt("racha") + (di.getInt("racha") == 1 ? " día" : " días") : ""), x + 12, y + 94, 0xFF5A3A10, false);
            if (!di.contains("dia")) {
                g.drawString(this.font, "Se sortean en unos segundos...", x + 12, y + 108, 0xFF4A4030, false);
            } else {
                for (int i = 0; i < 3; i++) {
                    CompoundTag t = di.getCompound("t" + i);
                    String id = t.getString("id");
                    int ry = y + 108 + i * 20;
                    boolean ok = t.getBoolean("hecha");
                    g.fill(x + 12, ry, x + 22, ry + 10, 0xFF3A2B18);
                    if (ok) {
                        g.drawString(this.font, "✔", x + 13, ry + 1, 0xFF4CE070, false);
                    }
                    g.drawString(this.font, texto(id), x + 28, ry + 1, ok ? 0xFF7A7060 : 0xFF2A2418, false);
                    int m = meta(id);
                    float k = Math.min(1.0f, t.getInt("prog") / (float) m);
                    g.fill(x + 28, ry + 11, x + 200, ry + 14, 0x40000000);
                    g.fill(x + 28, ry + 11, x + 28 + (int) (172 * k), ry + 14, ok ? 0xFF4CA060 : 0xFFD08A1C);
                    g.drawString(this.font, t.getInt("prog") + "/" + m, x + 206, ry + 6, 0xFF6A5A40, false);
                }
                g.drawString(this.font, "Cada una: +40 XP y 3 esmeraldas. Las 3: cofre del día.", x + 12, y + 170, 0xFF6A5A40, false);
            }
            super.render(g, mx, my, pt);
        }
    }
}
