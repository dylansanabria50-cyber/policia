package com.example.policia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

// Cartel del tutorial (a la izquierda) y deteccion de cada paso.
public class PoliciaTutorialCliente {
    static int enviado = -1;
    static int monstruosAntes = -1;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PoliciaTutorialCliente::tick);
    }

    static int paso() {
        CompoundTag d = PoliciaRPG.CLIENTE;
        return d == null || !d.contains("nivel") ? PoliciaTutorial.PASOS.length : d.getInt("tut");
    }

    static int monstruos() {
        CompoundTag b = PoliciaRPG.CLIENTE == null ? new CompoundTag() : PoliciaRPG.CLIENTE.getCompound("bestiario");
        int n = 0;
        for (String k : b.getAllKeys()) {
            n += b.getInt(k);
        }
        return n;
    }

    static boolean cumplido(Minecraft mc, int p) {
        Screen s = mc.screen;
        return switch (p) {
            case 0 -> !PoliciaRPG.CLIENTE.getString("oficio").isEmpty();
            case 1 -> s instanceof PoliciaFicha;
            case 2 -> s instanceof PoliciaHabScreen;
            case 3 -> PoliciaClient.K_HAB.isDown() && s == null;
            case 4 -> {
                if (monstruosAntes < 0) {
                    monstruosAntes = monstruos();
                }
                yield monstruos() - monstruosAntes >= 3;
            }
            case 5 -> PoliciaUICliente.K_CONSUMIR.isDown() && s == null;
            case 6 -> s instanceof PoliciaViajeScreen || s != null && s.getClass().getName().startsWith("folk.sisby.antique_atlas");
            case 7 -> s instanceof PoliciaObjetivosScreen;
            default -> false;
        };
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            enviado = -1;
            monstruosAntes = -1;
            return;
        }
        int p = paso();
        if (p >= PoliciaTutorial.PASOS.length || p == enviado) {
            return;
        }
        if (p != 4) {
            monstruosAntes = -1;
        }
        if (cumplido(mc, p)) {
            enviado = p;
            CompoundTag t = new CompoundTag();
            t.putInt("paso", p);
            PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("tut_ok", t));
        }
    }

    static void hud(Minecraft mc, GuiGraphics g, int w, int h) {
        int p = paso();
        if (p >= PoliciaTutorial.PASOS.length) {
            return;
        }
        int x = 6;
        int y = h / 2 - 30;
        int ancho = 150;
        java.util.List<FormattedCharSequence> l = mc.font.split(Component.literal(PoliciaTutorial.PASOS[p]), ancho - 10);
        int alto = 16 + l.size() * 10 + 12;
        g.fill(x, y, x + ancho, y + alto, 0xA0101010);
        g.fill(x, y, x + 2, y + alto, 0xFFE7B84A);
        g.drawString(mc.font, "§6Tutorial §7" + (p + 1) + "/" + PoliciaTutorial.PASOS.length, x + 6, y + 4, 0xFFFFFF);
        int ly = y + 16;
        for (FormattedCharSequence s : l) {
            g.drawString(mc.font, s, x + 6, ly, 0xFFFFFF);
            ly += 10;
        }
        g.drawString(mc.font, "§8Ficha → Opciones para ocultarlo", x + 6, ly + 1, 0xFFFFFF);
        if (p == 4 && monstruosAntes >= 0) {
            g.drawString(mc.font, "§e" + Math.min(3, monstruos() - monstruosAntes) + "/3", x + ancho - 22, y + 4, 0xFFFFFF);
        }
    }
}
