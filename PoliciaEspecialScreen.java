package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

// Pantalla de especializacion: dos tarjetas con los caminos del oficio.
public class PoliciaEspecialScreen extends Screen {
    static final int W = 320;
    static final int H = 200;
    final Screen padre;
    int x0;
    int y0;
    final List<String[]> opciones = new ArrayList<String[]>();
    final Button[] botones = new Button[2];

    public PoliciaEspecialScreen(Screen padre) {
        super(Component.literal("Especialización"));
        this.padre = padre;
    }

    static CompoundTag d() {
        CompoundTag d = PoliciaRPG.CLIENTE;
        return d == null ? new CompoundTag() : d;
    }

    @Override
    protected void init() {
        this.x0 = (this.width - W) / 2;
        this.y0 = Math.max(4, (this.height - H) / 2);
        this.opciones.clear();
        String of = d().getString("oficio");
        for (String[] f : PoliciaEspecial.TABLA) {
            if (f[1].equals(of)) {
                this.opciones.add(f);
            }
        }
        for (int i = 0; i < this.opciones.size() && i < 2; i++) {
            final String id = this.opciones.get(i)[0];
            this.botones[i] = this.addRenderableWidget(Button.builder(Component.literal("Elegir"), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("id", id);
                PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("espec", t));
            }).bounds(this.x0 + 12 + i * 152 + 34, this.y0 + H - 50, 80, 18).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Volver"), b -> this.onClose()).bounds(this.x0 + W / 2 - 40, this.y0 + H - 24, 80, 18).build());
    }

    @Override
    public void tick() {
        CompoundTag d = d();
        String actual = d.getString("espec");
        boolean nivel = d.getInt("nivel") >= PoliciaEspecial.NIVEL;
        for (int i = 0; i < 2; i++) {
            if (this.botones[i] == null) {
                continue;
            }
            String id = this.opciones.get(i)[0];
            boolean esta = id.equals(actual);
            this.botones[i].active = nivel && !esta;
            this.botones[i].setMessage(Component.literal(esta ? "Elegida" : actual.isEmpty() || PoliciaEspecial.fila(actual) == null || !PoliciaEspecial.fila(actual)[1].equals(d.getString("oficio")) ? "Elegir" : "Cambiar (" + PoliciaEspecial.COSTO_CAMBIO + " ◆)"));
        }
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
        g.drawCenteredString(this.font, "§6§lESPECIALIZACIÓN", x + W / 2, y + 7, 0xFFFFFF);
        CompoundTag d = d();
        if (this.opciones.isEmpty()) {
            g.drawCenteredString(this.font, "§7Primero elegí un oficio.", x + W / 2, y + 80, 0xFFFFFF);
        } else if (d.getInt("nivel") < PoliciaEspecial.NIVEL) {
            g.drawCenteredString(this.font, "§cSe desbloquea en el nivel " + PoliciaEspecial.NIVEL + " §7(estás en el " + Math.max(1, d.getInt("nivel")) + ")", x + W / 2, y + 28, 0xFFFFFF);
        } else {
            g.drawCenteredString(this.font, "§7Elegí un camino. Cambiarlo después cuesta " + PoliciaEspecial.COSTO_CAMBIO + " esmeraldas.", x + W / 2, y + 28, 0xFFFFFF);
        }
        String actual = d.getString("espec");
        for (int i = 0; i < this.opciones.size() && i < 2; i++) {
            String[] f = this.opciones.get(i);
            int cx = x + 12 + i * 152;
            int cy = y + 42;
            boolean esta = f[0].equals(actual);
            g.fill(cx - 1, cy - 1, cx + 145, cy + 105, esta ? 0xFFE7B84A : 0xFF4A3A22);
            g.fill(cx, cy, cx + 144, cy + 104, 0xFF2A2218);
            g.drawCenteredString(this.font, (esta ? "§e★ " : "§6") + f[2], cx + 72, cy + 6, 0xFFFFFF);
            int ly = cy + 24;
            for (int k = 3; k < 6; k++) {
                for (var line : this.font.split(Component.literal("§a• §f" + f[k]), 136)) {
                    g.drawString(this.font, line, cx + 5, ly, 0xFFFFFF);
                    ly += 10;
                }
                ly += 3;
            }
        }
        super.render(g, mx, my, pt);
    }
}
