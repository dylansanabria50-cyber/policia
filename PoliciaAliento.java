package com.example.policia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

// Etapa 11 (cliente): barra de aliento en las persecuciones. Policias y
// ladrones con el modo activo gastan aliento al correr; si se vacia no
// pueden correr hasta recuperar un 30 %. Agilidad da mas aliento. La barra
// sale debajo de la mira solo cuando no esta llena.
public class PoliciaAliento {
    static float aliento = -1;
    static boolean agotado;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PoliciaAliento::tick);
    }

    static boolean activo(Minecraft mc) {
        CompoundTag d = PoliciaRPG.CLIENTE;
        PoliciaMod.Sync s = PoliciaClient.mine();
        if (d == null || s == null || !s.on) {
            return false;
        }
        String o = d.getString("oficio");
        return "policia".equals(o) || "ladron".equals(o);
    }

    static float maximo() {
        CompoundTag d = PoliciaRPG.CLIENTE;
        int ag = d == null ? 5 : d.getInt("agilidad");
        return 100 + Math.max(0, ag - 5) * 4;
    }

    static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) {
            return;
        }
        if (!activo(mc) || mc.player.isCreative() || mc.player.isSpectator()) {
            aliento = -1;
            agotado = false;
            return;
        }
        float max = maximo();
        if (aliento < 0) {
            aliento = max;
        }
        if (e.phase == TickEvent.Phase.START) {
            if (agotado) {
                mc.options.keySprint.setDown(false);
                mc.player.setSprinting(false);
            }
            return;
        }
        if (mc.player.isSprinting()) {
            aliento -= 0.55f;
        } else {
            aliento += mc.player.getDeltaMovement().horizontalDistanceSqr() < 0.001 ? 0.9f : 0.45f;
        }
        aliento = Math.max(0, Math.min(max, aliento));
        if (aliento <= 0) {
            agotado = true;
            mc.player.setSprinting(false);
        } else if (agotado && aliento >= max * 0.3f) {
            agotado = false;
        }
    }

    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (aliento < 0 || mc.options.hideGui || mc.screen != null) {
            return;
        }
        float max = maximo();
        if (aliento >= max - 0.01f) {
            return;
        }
        int w = 40;
        int x = sw / 2 - w / 2;
        int y = sh / 2 + 12;
        g.fill(x - 1, y - 1, x + w + 1, y + 4, 0x90000000);
        int color = agotado ? 0xFFE04040 : aliento < max * 0.3f ? 0xFFE0A030 : 0xFF60D0FF;
        g.fill(x, y, x + (int) (w * aliento / max), y + 3, color);
    }
}
