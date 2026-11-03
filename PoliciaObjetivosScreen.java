package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

// Elegir que seguir con la brujula de objetivo.
public class PoliciaObjetivosScreen extends Screen {
    static final int W = 300;
    static final int H = 230;
    final Screen padre;
    int x0;
    int y0;
    int scroll;
    String firma = "";
    final List<Button> dinamicos = new ArrayList<Button>();

    public PoliciaObjetivosScreen(Screen padre) {
        super(Component.literal("Objetivo"));
        this.padre = padre;
    }

    @Override
    protected void init() {
        this.x0 = (this.width - W) / 2;
        this.y0 = Math.max(4, (this.height - H) / 2);
        PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("obj_pedir", new CompoundTag()));
        this.addRenderableWidget(Button.builder(Component.literal("Automático"), b -> seguir("")).bounds(this.x0 + 10, this.y0 + H - 24, 84, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Ocultar"), b -> seguir("no")).bounds(this.x0 + 100, this.y0 + H - 24, 84, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Volver"), b -> this.onClose()).bounds(this.x0 + W - 94, this.y0 + H - 24, 84, 18).build());
        this.firma = "";
    }

    static void seguir(String k) {
        CompoundTag t = new CompoundTag();
        t.putString("k", k);
        PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("obj_seguir", t));
    }

    void rehacer() {
        for (Button b : this.dinamicos) {
            this.removeWidget(b);
        }
        this.dinamicos.clear();
        ListTag l = PoliciaUICliente.OBJ.getList("l", Tag.TAG_COMPOUND);
        for (int i = 0; i < 8; i++) {
            int k = i + this.scroll;
            if (k >= l.size()) {
                break;
            }
            final String clave = l.getCompound(k).getString("k");
            this.dinamicos.add(this.addRenderableWidget(Button.builder(Component.literal("Seguir"), b -> seguir(clave)).bounds(this.x0 + W - 66, this.y0 + 40 + i * 20, 56, 16).build()));
        }
    }

    @Override
    public void tick() {
        String f = PoliciaUICliente.OBJ.toString() + this.scroll;
        if (!f.equals(this.firma)) {
            this.firma = f;
            this.rehacer();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        int n = PoliciaUICliente.OBJ.getList("l", Tag.TAG_COMPOUND).size();
        this.scroll = Math.max(0, Math.min(Math.max(0, n - 8), this.scroll - (int) Math.signum(d)));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.padre);
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
        g.drawCenteredString(this.font, "§6§lOBJETIVO", x + W / 2, y + 7, 0xFFFFFF);
        String sel = PoliciaUICliente.OBJ.getString("sel");
        g.drawString(this.font, "§7Brújula: §f" + (sel.isEmpty() ? "automática (historia)" : sel.equals("no") ? "oculta" : "elegida"), x + 10, y + 27, 0xFFFFFF);
        ListTag l = PoliciaUICliente.OBJ.getList("l", Tag.TAG_COMPOUND);
        if (l.isEmpty()) {
            g.drawString(this.font, "§7No hay lugares todavía. Descubrí aldeas y paradas.", x + 10, y + 44, 0xFFFFFF);
        }
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 8; i++) {
            int k = i + this.scroll;
            if (k >= l.size()) {
                break;
            }
            CompoundTag c = l.getCompound(k);
            int ly = y + 44 + i * 20;
            boolean activo = c.getString("k").equals(sel) || sel.isEmpty() && c.getString("k").equals("historia");
            String n = c.getString("n");
            if (this.font.width(n) > 150) {
                n = this.font.plainSubstrByWidth(n, 146) + "…";
            }
            g.drawString(this.font, (activo ? "§e▶ " : "§f") + n, x + 10, ly, 0xFFFFFF);
            if (mc.player != null) {
                double dx = c.getInt("x") - mc.player.getX();
                double dz = c.getInt("z") - mc.player.getZ();
                int dist = (int) Math.sqrt(dx * dx + dz * dz);
                g.drawString(this.font, "§7" + (dist >= 1000 ? String.format("%.1f km", dist / 1000.0) : dist + " m"), x + 172, ly, 0xFFFFFF);
            }
        }
        super.render(g, mx, my, pt);
    }
}
