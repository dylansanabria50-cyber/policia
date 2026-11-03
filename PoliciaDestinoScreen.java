package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

// El cochero pregunta a donde vas: lista de ciudades que ya conociste (con
// distancia y costo) y tu casa. Cerrar con ESC o "Me quedo" = cancelar.
public class PoliciaDestinoScreen extends Screen {
    final ListTag destinos;
    final String parada;
    int pagina;
    boolean enviado;
    final List<Button> botones = new ArrayList<Button>();

    public PoliciaDestinoScreen(ListTag destinos, String parada) {
        super(Component.literal("¿A dónde vamos?"));
        this.destinos = destinos;
        this.parada = parada == null ? "" : parada;
    }

    int porPagina() {
        int filas = Math.max(2, (this.height - 120) / 24);
        return Math.min(10, filas) * 2;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int pp = porPagina();
        int paginas = Math.max(1, (destinos.size() + pp - 1) / pp);
        pagina = Math.min(pagina, paginas - 1);
        int x0 = this.width / 2 - 155;
        int y0 = 74;
        for (int i = 0; i < pp; i++) {
            int idx = pagina * pp + i;
            if (idx >= destinos.size()) {
                break;
            }
            CompoundTag e = destinos.getCompound(idx);
            int c = e.getInt("c");
            int m = e.getInt("m");
            String dist = m >= 1000 ? String.format("%.1f km", m / 1000f) : m + " m";
            String costo = c <= 0 ? "§agratis" : "§e" + c + " esm.";
            String txt = ("casa".equals(e.getString("k")) ? "§b⌂ " : "§f") + e.getString("n") + " §7· " + dist + " · " + costo;
            final String k = e.getString("k");
            int col = i % 2;
            int fila = i / 2;
            this.addRenderableWidget(Button.builder(Component.literal(txt), b -> elegir(k)).bounds(x0 + col * 160, y0 + fila * 24, 150, 20).build());
        }
        int yb = this.height - 30;
        if (paginas > 1) {
            this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
                pagina = (pagina - 1 + paginas) % paginas;
                this.init();
            }).bounds(this.width / 2 - 155, yb, 30, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                pagina = (pagina + 1) % paginas;
                this.init();
            }).bounds(this.width / 2 + 125, yb, 30, 20).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Me quedo acá"), b -> this.onClose()).bounds(this.width / 2 - 60, yb, 120, 20).build());
    }

    void elegir(String k) {
        if (enviado) {
            return;
        }
        enviado = true;
        CompoundTag d = new CompoundTag();
        d.putString("k", k);
        PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("viaje_elegir", d));
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        if (!enviado) {
            enviado = true;
            PoliciaCineCliente.cancelar(this.minecraft);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, this.width, this.height, 0x60000000, 0xA0000000);
        int x0 = this.width / 2 - 165;
        g.fill(x0, 18, x0 + 330, 66, 0xB0201510);
        g.fill(x0, 18, x0 + 330, 19, 0xFFB08D57);
        g.fill(x0, 65, x0 + 330, 66, 0xFFB08D57);
        g.drawCenteredString(this.font, "§7" + (parada.isEmpty() ? "Parada de carruaje" : parada), this.width / 2, 24, 0xFFFFFF);
        g.drawCenteredString(this.font, "§6El cochero se asoma:", this.width / 2, 38, 0xFFFFFF);
        g.drawCenteredString(this.font, "§f«¿A dónde vamos, viajero? Solo voy a lugares que ya conocés.»", this.width / 2, 50, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }
}
