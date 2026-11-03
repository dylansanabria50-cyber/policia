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

// Pantalla de mercenarios: los que tenes contratados (vida, dias que
// quedan, orden) y las cuatro clases para contratar.
public class PoliciaMercenarioScreen extends Screen {
    static final int W = 320;
    static final int H = 232;
    final Screen padre;
    int x0;
    int y0;
    final List<Button> dinamicos = new ArrayList<Button>();
    String firma = "";

    public PoliciaMercenarioScreen(Screen padre) {
        super(Component.literal("Mercenarios"));
        this.padre = padre;
    }

    @Override
    protected void init() {
        this.x0 = (this.width - W) / 2;
        this.y0 = Math.max(4, (this.height - H) / 2);
        PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("merc_pedir", new CompoundTag()));
        for (int c = 0; c < PoliciaMercenario.CLASE.length; c++) {
            final int clase = c;
            this.addRenderableWidget(Button.builder(Component.literal("Contratar"), b -> {
                CompoundTag t = new CompoundTag();
                t.putInt("clase", clase);
                PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("merc_contratar", t));
            }).bounds(this.x0 + W - 84, this.y0 + 118 + c * 22, 72, 18).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Volver"), b -> this.onClose()).bounds(this.x0 + W / 2 - 40, this.y0 + H - 24, 80, 18).build());
        this.firma = "";
    }

    void rehacer() {
        for (Button b : this.dinamicos) {
            this.removeWidget(b);
        }
        this.dinamicos.clear();
        ListTag l = PoliciaUICliente.MERC.getList("l", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size() && i < 2; i++) {
            CompoundTag c = l.getCompound(i);
            final String id = c.getString("id");
            boolean quieto = c.getBoolean("quieto");
            Button a = Button.builder(Component.literal(quieto ? "Seguime" : "Quedate"), b -> orden(id, quieto ? 0 : 1)).bounds(this.x0 + W - 150, this.y0 + 40 + i * 30, 66, 18).build();
            Button d = Button.builder(Component.literal("Despedir"), b -> orden(id, 2)).bounds(this.x0 + W - 80, this.y0 + 40 + i * 30, 68, 18).build();
            this.dinamicos.add(this.addRenderableWidget(a));
            this.dinamicos.add(this.addRenderableWidget(d));
        }
    }

    static void orden(String id, int o) {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putInt("orden", o);
        PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("merc_orden", t));
    }

    @Override
    public void tick() {
        String f = PoliciaUICliente.MERC.toString();
        if (!f.equals(this.firma)) {
            this.firma = f;
            this.rehacer();
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
        g.drawCenteredString(this.font, "§6§lMERCENARIOS", x + W / 2, y + 7, 0xFFFFFF);
        CompoundTag d = PoliciaUICliente.MERC;
        ListTag l = d.getList("l", Tag.TAG_COMPOUND);
        g.drawString(this.font, "§eTu compañía §7(" + l.size() + "/" + PoliciaMercenario.MAX + ")", x + 12, y + 28, 0xFFFFFF);
        if (l.isEmpty()) {
            g.drawString(this.font, "§7Todavía no contrataste a nadie.", x + 12, y + 44, 0xFFFFFF);
        }
        for (int i = 0; i < l.size() && i < 2; i++) {
            CompoundTag c = l.getCompound(i);
            int ly = y + 40 + i * 30;
            g.drawString(this.font, c.getString("n"), x + 12, ly, 0xFFFFFF);
            float k = c.getFloat("vida") / Math.max(1, c.getFloat("max"));
            g.fill(x + 12, ly + 11, x + 112, ly + 15, 0xFF401010);
            g.fill(x + 12, ly + 11, x + 12 + (int) (100 * k), ly + 15, 0xFF40C040);
            long resta = c.getLong("resta");
            String t = resta >= 24000 ? (resta / 24000 + 1) + " días" : (resta / 1200) + " min";
            g.drawString(this.font, "§7" + t + (c.getBoolean("quieto") ? " · quieto" : " · te sigue"), x + 118, ly + 9, 0xFFFFFF);
        }
        g.fill(x + 8, y + 102, x + W - 8, y + 103, 0xFF4A3A22);
        boolean aldea = d.getBoolean("aldea");
        g.drawString(this.font, aldea ? "§eContratar §7(3 días de juego)" : "§cContratar: andá a una aldea", x + 12, y + 106, 0xFFFFFF);
        int[] pr = d.getIntArray("precios");
        for (int c = 0; c < PoliciaMercenario.CLASE.length; c++) {
            int ly = y + 118 + c * 22;
            g.drawString(this.font, "§f" + PoliciaMercenario.CLASE[c] + " §a" + (c < pr.length ? pr[c] : PoliciaMercenario.PRECIO[c]) + " ◆", x + 12, ly, 0xFFFFFF);
            g.drawString(this.font, "§7" + PoliciaMercenario.DESC[c], x + 12, ly + 10, 0xFFFFFF);
        }
        super.render(g, mx, my, pt);
    }
}
