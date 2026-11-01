package com.example.policia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

// Facciones (desde la ficha): reputacion con cada una, su rango y las
// recompensas de Amistoso, Honrado y Exaltado.
public class PoliciaFaccionesScreen extends Screen {
    static final int W = 320;
    static final int H = 236;
    static final int[] COLOR = {0xFF4A7AD8, 0xFF8A4AC8, 0xFFD08A30, 0xFF3AA860, 0xFF30B0C8};
    int x0;
    int y0;
    final Button[][] reclamar = new Button[5][3];

    public PoliciaFaccionesScreen() {
        super(Component.literal("Facciones"));
    }

    static CompoundTag fac() {
        CompoundTag d = PoliciaRPG.CLIENTE == null ? new CompoundTag() : PoliciaRPG.CLIENTE;
        return d.getCompound("fac");
    }

    @Override
    protected void init() {
        this.x0 = Math.max(2, (this.width - W) / 2);
        this.y0 = Math.max(2, (this.height - H) / 2);
        for (int i = 0; i < 5; i++) {
            for (int n = 0; n < 3; n++) {
                final String id = PoliciaFacciones.IDS[i];
                final int nivel = n;
                this.reclamar[i][n] = this.addRenderableWidget(Button.builder(Component.literal(""), b -> PoliciaRed.CH.sendToServer(new PoliciaRed.FaccionReclamar(id, nivel)))
                        .bounds(this.x0 + 12 + n * 100, this.y0 + 30 + i * 38 + 22, 96, 12).build());
            }
        }
        this.addRenderableWidget(Button.builder(Component.literal("Ficha"), b -> Minecraft.getInstance().setScreen(new PoliciaFicha())).bounds(this.x0 + 12, this.y0 + H - 24, 70, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose()).bounds(this.x0 + W - 82, this.y0 + H - 24, 70, 18).build());
    }

    @Override
    public void tick() {
        CompoundTag f = fac();
        for (int i = 0; i < 5; i++) {
            String id = PoliciaFacciones.IDS[i];
            for (int n = 0; n < 3; n++) {
                Button b = this.reclamar[i][n];
                boolean hecho = f.getBoolean("r_" + id + n);
                boolean puede = f.getInt(id) >= PoliciaFacciones.UMBRAL[n];
                b.active = puede && !hecho;
                b.setMessage(Component.literal(hecho ? "§a✔ Reclamada" : puede ? "§eReclamar" : "§8" + PoliciaFacciones.UMBRAL[n] + " rep."));
            }
        }
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
        g.fill(x, y, x + W, y + H, 0xF0201A14);
        g.fill(x, y, x + W, y + 22, 0xFF3A2B18);
        g.drawCenteredString(this.font, "§6§lFACCIONES", x + W / 2, y + 7, 0xFFFFFF);
        CompoundTag f = fac();
        for (int i = 0; i < 5; i++) {
            String id = PoliciaFacciones.IDS[i];
            int r = f.getInt(id);
            int ry = y + 30 + i * 38;
            g.fill(x + 8, ry - 2, x + 11, ry + 34, COLOR[i]);
            g.drawString(this.font, "§f" + PoliciaFacciones.NOMBRES[i], x + 14, ry, 0xFFFFFF);
            String rg = PoliciaFacciones.rango(r) + " §7(" + r + ")";
            g.drawString(this.font, rg, x + W - 12 - this.font.width(rg), ry, 0xFFFFFF);
            int bx = x + 14;
            int bw = W - 28;
            g.fill(bx, ry + 11, bx + bw, ry + 17, 0xFF0E0B08);
            int mid = bx + bw / 2;
            int fin = mid + (int) (bw / 2.0 * r / 100.0);
            g.fill(Math.min(mid, fin), ry + 12, Math.max(mid, fin), ry + 16, r >= 0 ? COLOR[i] : 0xFFC03030);
            g.fill(mid, ry + 10, mid + 1, ry + 18, 0xFF8A7A50);
            for (int u : PoliciaFacciones.UMBRAL) {
                int ux = mid + (int) (bw / 2.0 * u / 100.0);
                g.fill(ux, ry + 11, ux + 1, ry + 17, 0x80FFFFFF);
            }
        }
        super.render(g, mx, my, pt);
        for (int i = 0; i < 5; i++) {
            for (int n = 0; n < 3; n++) {
                Button b = this.reclamar[i][n];
                if (b.isHovered()) {
                    g.renderTooltip(this.font, Component.literal("§7Recompensa: §f" + PoliciaFacciones.textoRecompensa(PoliciaFacciones.IDS[i], n)), mx, my);
                }
            }
        }
    }
}
