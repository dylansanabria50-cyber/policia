package com.example.policia;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

// Clips de las habilidades: las mismas animaciones 3D de la vista previa,
// grabadas cuadro por cuadro en una hoja (textures/gui/clips/<clave>.png).
// En los arboles de habilidades, al pasar el mouse por una habilidad, se
// abre un cuadro al lado del mouse con su clip en bucle.
public class PoliciaClips {
    // clave -> {cuadros, columnas, ancho, alto, ticks por cuadro}
    static final Map<String, int[]> INFO = new HashMap<String, int[]>();
    static final Map<String, String> NOMBRE = new HashMap<String, String>();
    // habilidad nueva (Def.anim) -> clave del clip
    static final Map<String, String> HAB = new HashMap<String, String>();

    static String pedido;
    static long pedidoT;
    static String actual;
    static long inicio;

    static void init() {
        PoliciaClipsDatos.cargar();
        MinecraftForge.EVENT_BUS.addListener(PoliciaClips::dibujarPedido);
    }

    static boolean existe(String clave) {
        return clave != null && INFO.containsKey(clave);
    }

    static String deHab(String anim) {
        return anim == null ? null : HAB.get(anim);
    }

    // La pantalla lo llama en cada cuadro con el clip que hay que mostrar.
    static void pedir(String clave) {
        pedido = existe(clave) ? clave : null;
        pedidoT = System.currentTimeMillis();
    }

    // Dibuja el clip en (x, y) con ese tamaño.
    static void dibujar(GuiGraphics g, String clave, int x, int y, int w, int h) {
        int[] in = INFO.get(clave);
        if (in == null) {
            return;
        }
        long ahora = System.currentTimeMillis();
        if (!clave.equals(actual)) {
            actual = clave;
            inicio = ahora;
        }
        int n = in[0];
        int cols = in[1];
        int fw = in[2];
        int fh = in[3];
        int paso = Math.max(1, in[4]);
        int pausa = 12;
        int f = (int) ((ahora - inicio) / (paso * 50L) % (n + pausa));
        if (f >= n) {
            f = n - 1;
        }
        int filas = (n + cols - 1) / cols;
        ResourceLocation tex = new ResourceLocation(PoliciaMod.ID, "textures/gui/clips/" + clave + ".png");
        g.blit(tex, x, y, w, h, (float) ((f % cols) * fw), (float) ((f / cols) * fh), fw, fh, fw * cols, fh * filas);
    }

    // Cuadro flotante junto al mouse (lo piden los arboles clasicos).
    static void dibujarPedido(ScreenEvent.Render.Post e) {
        if (pedido == null || System.currentTimeMillis() - pedidoT > 250) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        int sw = e.getScreen().width;
        int sh = e.getScreen().height;
        int w = Math.min(208, sw / 2 - 10);
        int h = w * 10 / 16;
        int mx = e.getMouseX();
        int my = e.getMouseY();
        int x = mx + 18;
        if (x + w + 4 > sw) {
            x = mx - w - 18;
        }
        x = Math.max(4, x);
        int y = Math.max(16, Math.min(sh - h - 6, my - h / 2));
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        caja(g, x, y, w, h, NOMBRE.getOrDefault(pedido, ""));
        dibujar(g, pedido, x, y, w, h);
        g.pose().popPose();
    }

    static void caja(GuiGraphics g, int x, int y, int w, int h, String titulo) {
        Minecraft mc = Minecraft.getInstance();
        g.fill(x - 3, y - 15, x + w + 3, y + h + 3, 0xFF2E4A6B);
        g.fill(x - 2, y - 14, x + w + 2, y + h + 2, 0xFF101820);
        g.drawString(mc.font, "§b▶ §f" + titulo, x + 2, y - 11, 0xFFFFFF);
    }
}
