package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;

// Etapa 8 (cliente): mapa de viaje en carruaje (tecla M) y reloj del dia.
public class PoliciaViajeScreen extends Screen {
    static final int W = 320;
    static final int H = 220;
    int x0;
    int y0;
    final List<String[]> paradas = new ArrayList<String[]>();

    public PoliciaViajeScreen() {
        super(Component.literal("Mapa de viaje"));
    }

    @Override
    protected void init() {
        this.x0 = (this.width - W) / 2;
        this.y0 = (this.height - H) / 2;
        this.paradas.clear();
        CompoundTag d = PoliciaRPG.CLIENTE == null ? new CompoundTag() : PoliciaRPG.CLIENTE;
        CompoundTag p = d.getCompound("paradas");
        for (String k : p.getAllKeys()) {
            CompoundTag t = p.getCompound(k);
            this.paradas.add(new String[]{k, t.getString("n"), String.valueOf(t.getInt("x")), String.valueOf(t.getInt("z"))});
        }
        this.paradas.sort((a, b) -> a[1].compareTo(b[1]));
        int y = this.y0 + 30;
        this.addRenderableWidget(Button.builder(Component.literal("⌂ Mi casa (gratis)"), b -> this.ir("casa")).bounds(this.x0 + W - 122, y, 112, 16).build());
        for (int i = 0; i < Math.min(9, this.paradas.size()); i++) {
            String[] s = this.paradas.get(i);
            this.addRenderableWidget(Button.builder(Component.literal(s[1] + " §7(" + this.costo(s) + ")"), b -> this.ir(s[0])).bounds(this.x0 + W - 122, y + 18 + i * 18, 112, 16).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose()).bounds(this.x0 + W - 82, this.y0 + H - 24, 70, 16).build());
    }

    int costo(String[] s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 1;
        }
        double dx = Integer.parseInt(s[2]) - mc.player.getX();
        double dz = Integer.parseInt(s[3]) - mc.player.getZ();
        return Math.max(1, (int) (Math.sqrt(dx * dx + dz * dz) / 250.0));
    }

    void ir(String clave) {
        PoliciaRed.CH.sendToServer(new PoliciaRed.ViajeIr(clave));
        this.onClose();
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
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF5A4022);
        g.fill(x, y, x + W, y + H, 0xF0E4D2A8);
        g.fill(x, y, x + W, y + 22, 0xFF3A2B18);
        g.drawCenteredString(this.font, "§6§lMAPA DE VIAJE", x + W / 2, y + 7, 0xFFFFFF);
        // mapa (a la izquierda): paradas relativas al jugador
        int mx0 = x + 10;
        int my0 = y + 28;
        int mw = 180;
        int mh = 160;
        g.fill(mx0, my0, mx0 + mw, my0 + mh, 0xFFC8B888);
        for (int i = 0; i < 6; i++) {
            g.fill(mx0 + i * mw / 6, my0, mx0 + i * mw / 6 + 1, my0 + mh, 0x20000000);
            g.fill(mx0, my0 + i * mh / 6, mx0 + mw, my0 + i * mh / 6 + 1, 0x20000000);
        }
        Minecraft mc = Minecraft.getInstance();
        double px = mc.player != null ? mc.player.getX() : 0;
        double pz = mc.player != null ? mc.player.getZ() : 0;
        double max = 300;
        for (String[] s : this.paradas) {
            max = Math.max(max, Math.max(Math.abs(Integer.parseInt(s[2]) - px), Math.abs(Integer.parseInt(s[3]) - pz)));
        }
        double esc = (mw / 2.0 - 10) / max;
        int cx = mx0 + mw / 2;
        int cz = my0 + mh / 2;
        for (String[] s : this.paradas) {
            int sx = cx + (int) ((Integer.parseInt(s[2]) - px) * esc);
            int sz = cz + (int) ((Integer.parseInt(s[3]) - pz) * esc);
            g.fill(cx, cz, cx + 1, cz + 1, 0);
            // camino punteado
            for (int k = 1; k < 12; k++) {
                int qx = cx + (sx - cx) * k / 12;
                int qz = cz + (sz - cz) * k / 12;
                g.fill(qx, qz, qx + 1, qz + 1, 0x60402010);
            }
            g.fill(sx - 2, sz - 2, sx + 3, sz + 3, 0xFF8A2A1A);
            g.drawString(this.font, s[1], sx + 4, sz - 4, 0xFF3A2B18, false);
        }
        g.fill(cx - 2, cz - 2, cx + 3, cz + 3, 0xFF2A5AD8);
        g.drawString(this.font, "vos", cx + 4, cz + 2, 0xFF2A3A8A, false);
        g.drawString(this.font, "N ↑", mx0 + mw - 22, my0 + 3, 0xFF3A2B18, false);
        if (this.paradas.isEmpty()) {
            g.drawCenteredString(this.font, "§8Entrá a una aldea para descubrir", mx0 + mw / 2, my0 + mh / 2 + 14, 0xFFFFFF);
            g.drawCenteredString(this.font, "§8su parada de carruaje", mx0 + mw / 2, my0 + mh / 2 + 24, 0xFFFFFF);
        }
        g.drawString(this.font, "Costo en esmeraldas entre paréntesis. Salís desde una aldea o tu casa.", x + 10, y + H - 20, 0xFF5A4A30, false);
        super.render(g, mx, my, pt);
    }

    // ---------------- Reloj (arriba a la izquierda) ----------------
    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null || !PoliciaConfig.hudReloj()) {
            return;
        }
        if (mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        long t = Math.floorMod(mc.level.getDayTime() + 6000, 24000L);
        int hh = (int) (t / 1000);
        int mm = (int) ((t % 1000) * 60 / 1000);
        int f = PoliciaDiaNoche.franja(mc.level.getDayTime());
        String ic = f == 3 || f == 2 ? "§9☾" : "§e☀";
        String s = ic + " §f" + String.format("%02d:%02d", hh, mm) + " §7" + PoliciaDiaNoche.FRANJA[f];
        g.drawString(mc.font, s, 4, 4, 0xFFFFFF);
    }
}
