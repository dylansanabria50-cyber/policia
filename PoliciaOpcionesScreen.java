package com.example.policia;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

// Opciones del mod dentro del juego (Opciones -> "Oficios..." o Mods ->
// Policia -> Configurar). Se guardan en config/policia-cliente.toml.
// Apagar los movimientos NO apaga las animaciones de las habilidades.
public class PoliciaOpcionesScreen extends Screen {
    final Screen padre;

    public PoliciaOpcionesScreen(Screen padre) {
        super(Component.literal("Opciones de Oficios"));
        this.padre = padre;
    }

    static String si(boolean b) {
        return b ? "§aSí" : "§cNo";
    }

    static String intensidad() {
        double v = PoliciaConfig.intensidad();
        return v <= 0.6 ? "Suave" : v <= 1.1 ? "Normal" : v <= 1.6 ? "Marcada" : "Exagerada";
    }

    static void cambiar(ForgeConfigSpec.BooleanValue v) {
        try {
            v.set(!v.get());
            v.save();
        } catch (Throwable ignored) {
        }
    }

    Button toggle(int fila, String nombre, ForgeConfigSpec.BooleanValue v, Supplier<Boolean> leer) {
        int x = this.width / 2 - 155 + (fila % 2) * 160;
        int y = this.height / 6 + 24 + (fila / 2) * 24;
        Button[] b = new Button[1];
        b[0] = Button.builder(Component.literal(nombre + ": " + si(leer.get())), x2 -> {
            cambiar(v);
            b[0].setMessage(Component.literal(nombre + ": " + si(leer.get())));
        }).bounds(x, y, 150, 20).build();
        return this.addRenderableWidget(b[0]);
    }

    @Override
    protected void init() {
        this.toggle(0, "Movimientos del personaje", PoliciaConfig.MOVIMIENTOS, PoliciaConfig::mov);
        Button[] in = new Button[1];
        in[0] = this.addRenderableWidget(Button.builder(Component.literal("Intensidad: " + intensidad()), b -> {
            double v = PoliciaConfig.intensidad();
            double n = v < 0.75 ? 1.0 : v < 1.25 ? 1.5 : v < 1.75 ? 2.0 : 0.5;
            try {
                PoliciaConfig.INTENSIDAD.set(n);
                PoliciaConfig.INTENSIDAD.save();
            } catch (Throwable ignored) {
            }
            in[0].setMessage(Component.literal("Intensidad: " + intensidad()));
        }).bounds(this.width / 2 + 5, this.height / 6 + 24, 150, 20).build());
        this.toggle(2, "Primera persona con inercia", PoliciaConfig.PRIMERA_PERSONA, PoliciaConfig::primera);
        this.toggle(3, "Líneas de velocidad", PoliciaConfig.LINEAS_VELOCIDAD, PoliciaConfig::lineas);
        this.toggle(4, "Estaciones visibles", PoliciaConfig.ESTACIONES, PoliciaConfig::estaciones);
        this.toggle(5, "Nivel en pantalla", PoliciaConfig.HUD_NIVEL, PoliciaConfig::hudNivel);
        this.toggle(6, "Misiones diarias", PoliciaConfig.HUD_DIARIAS, PoliciaConfig::hudDiarias);
        this.toggle(7, "Reloj en pantalla", PoliciaConfig.HUD_RELOJ, PoliciaConfig::hudReloj);
        this.addRenderableWidget(Button.builder(Component.literal("Listo"), b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height / 6 + 24 + 4 * 24 + 40, 200, 20).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.padre);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, "§6§lOpciones de Oficios", this.width / 2, this.height / 6 - 4, 0xFFFFFF);
        g.drawCenteredString(this.font, "§7Las animaciones de las habilidades se ven siempre, aunque apagues los movimientos.", this.width / 2, this.height / 6 + 10, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }
}
