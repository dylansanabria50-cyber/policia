package com.example.policia;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.gui.overlay.ForgeGui;

// Animación pixel art del dado del carterismo (solo cliente).
public class PoliciaDadoScreen {
    static final int ROLL = 26;   // ticks rodando
    static final int AFTER = 30;  // ticks que queda el resultado final
    static final int PER = PoliciaDado.PER_ROLL;

    // Paleta: dado carmesí con puntos dorados, estilo dado de rol.
    static final int OUT = 0xFF1B0A0A;
    static final int BODY = 0xFFA4161A;
    static final int LIGHT = 0xFFD1343A;
    static final int SHINE = 0xFFF07167;
    static final int SHADE = 0xFF6A040F;
    static final int PIP = 0xFFFFD166;
    static final int PIP_D = 0xFFB07D12;

    // Posiciones de los puntos en una cara de 14x14 (coordenadas de 2x2).
    static final int[][][] PIPS = new int[][][]{
            {},
            {{6, 6}},
            {{3, 3}, {9, 9}},
            {{3, 3}, {6, 6}, {9, 9}},
            {{3, 3}, {9, 3}, {3, 9}, {9, 9}},
            {{3, 3}, {9, 3}, {6, 6}, {3, 9}, {9, 9}},
            {{3, 3}, {9, 3}, {3, 6}, {9, 6}, {3, 9}, {9, 9}}
    };

    static int[] rolls = new int[0];
    static long start = -1L;
    static int lastFace = -1;
    static int soundStep = -1;

    static void start(int[] r) {
        rolls = r;
        start = Util.getMillis();
        lastFace = -1;
        soundStep = -1;
    }

    static void play(net.minecraft.sounds.SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    // Cara "falsa" mientras rueda: cambia cada vez más lento.
    static int tumbleFace(int idx, float t) {
        float k = t / ROLL;
        int step = (int) (t / (1.0f + k * 4.0f));
        int f = (int) ((step * 7L + idx * 3L + 1L) % 6L) + 1;
        return f;
    }

    static void px(GuiGraphics g, int ox, int oy, int p, int x, int y, int w, int h, int c) {
        g.fill(ox + x * p, oy + y * p, ox + (x + w) * p, oy + (y + h) * p, c);
    }

    // Cara del dado de frente: 16x16 con esquinas redondeadas, brillo y sombra.
    static void face(GuiGraphics g, int ox, int oy, int p, int v, boolean dim) {
        px(g, ox, oy, p, 2, 0, 12, 1, OUT);
        px(g, ox, oy, p, 2, 15, 12, 1, OUT);
        px(g, ox, oy, p, 0, 2, 1, 12, OUT);
        px(g, ox, oy, p, 15, 2, 1, 12, OUT);
        px(g, ox, oy, p, 1, 1, 1, 1, OUT);
        px(g, ox, oy, p, 14, 1, 1, 1, OUT);
        px(g, ox, oy, p, 1, 14, 1, 1, OUT);
        px(g, ox, oy, p, 14, 14, 1, 1, OUT);
        px(g, ox, oy, p, 1, 2, 14, 12, BODY);
        px(g, ox, oy, p, 2, 1, 12, 14, BODY);
        px(g, ox, oy, p, 2, 1, 11, 1, LIGHT);
        px(g, ox, oy, p, 1, 2, 1, 11, LIGHT);
        px(g, ox, oy, p, 2, 2, 2, 1, SHINE);
        px(g, ox, oy, p, 2, 3, 1, 1, SHINE);
        px(g, ox, oy, p, 3, 14, 11, 1, SHADE);
        px(g, ox, oy, p, 14, 3, 1, 11, SHADE);
        px(g, ox, oy, p, 13, 13, 1, 1, SHADE);
        for (int[] q : PIPS[Math.max(0, Math.min(6, v))]) {
            int x = q[0] + 1;
            int y = q[1] + 1;
            px(g, ox, oy, p, x, y, 2, 2, PIP);
            px(g, ox, oy, p, x + 1, y + 1, 1, 1, PIP_D);
        }
        if (dim) {
            g.fill(ox + p, oy + p, ox + 15 * p, oy + 15 * p, 0x66000000);
        }
    }

    // Dado de canto (rombo) para los cuadros intermedios del giro.
    static void edge(GuiGraphics g, int ox, int oy, int p, int v) {
        for (int r = 0; r < 16; ++r) {
            int hw = r < 8 ? r + 1 : 16 - r;
            int x0 = 8 - hw;
            px(g, ox, oy, p, x0, r, hw * 2, 1, OUT);
            if (hw > 1) {
                px(g, ox, oy, p, x0 + 1, r, hw - 1, 1, r < 8 ? LIGHT : BODY);
                px(g, ox, oy, p, 8, r, hw - 1, 1, r < 8 ? BODY : SHADE);
            }
        }
        px(g, ox, oy, p, 1, 8, 14, 1, OUT);
        int[][] dots = v % 2 == 0 ? new int[][]{{5, 4}, {10, 4}, {7, 11}} : new int[][]{{7, 3}, {4, 11}, {10, 11}};
        for (int[] d : dots) {
            px(g, ox, oy, p, d[0], d[1], 2, 1, PIP);
        }
    }

    static void panel(GuiGraphics g, int x, int y, int w, int h, int p) {
        g.fill(x, y, x + w, y + h, 0xE0201812);
        g.fill(x, y, x + w, y + p, 0xFF6B4A2B);
        g.fill(x, y + h - p, x + w, y + h, 0xFF3A2614);
        g.fill(x, y, x + p, y + h, 0xFF6B4A2B);
        g.fill(x + w - p, y, x + w, y + h, 0xFF3A2614);
        g.fill(x + p, y + p, x + 2 * p, y + 2 * p, 0xFFC89B5E);
        g.fill(x + w - 2 * p, y + p, x + w - p, y + 2 * p, 0xFFC89B5E);
        g.fill(x + p, y + h - 2 * p, x + 2 * p, y + h - p, 0xFFC89B5E);
        g.fill(x + w - 2 * p, y + h - 2 * p, x + w - p, y + h - p, 0xFFC89B5E);
    }

    static void bigText(GuiGraphics g, Font f, String s, int cx, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        int w = f.width(s);
        g.drawString(f, s, -w / 2 + 1, 1, 0xFF000000, false);
        g.drawString(f, s, -w / 2, 0, color, false);
        g.pose().popPose();
    }

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        if (start < 0L || rolls.length == 0) {
            return;
        }
        float t = (Util.getMillis() - start) / 50.0f;
        float total = rolls.length * PER + AFTER;
        if (t > total) {
            start = -1L;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int idx = Math.min(rolls.length - 1, (int) (t / PER));
        float lt = t - idx * PER;
        boolean finalHold = t >= rolls.length * PER;
        if (finalHold) {
            idx = rolls.length - 1;
            lt = PER;
        }
        int v = rolls[idx];
        boolean rolling = !finalHold && lt < ROLL;

        int p = Math.max(3, Math.min(6, sh / 70));
        int dieW = 16 * p;
        int pw = dieW + 26 * p;
        int ph = dieW + 22 * p;
        int px0 = sw / 2 - pw / 2;
        int py0 = sh / 2 - ph - 8 * p;
        float fade = total - t < 8.0f ? (total - t) / 8.0f : 1.0f;
        if (fade < 0.15f) {
            return;
        }
        panel(g, px0, py0, pw, ph, p);
        bigText(g, font, "CARTERISMO", sw / 2, py0 + 2 * p + 1, 0xFFE8C170, 1.0f);
        String tirada = rolls.length > 1 ? "Tirada " + (idx + 1) + " de " + rolls.length : "Tirada del dado";
        bigText(g, font, tirada, sw / 2, py0 + 2 * p + 11, 0xFFB8A58A, 0.75f);

        int cx = sw / 2 - dieW / 2;
        int baseY = py0 + 6 * p + 14;
        int dx = 0;
        int dy = 0;
        int cur;
        if (rolling) {
            float k = lt / ROLL;
            // rebotes que se apagan
            float bounce = (float) Math.abs(Math.sin(k * Math.PI * 3.0)) * (1.0f - k) * 4.0f * p;
            dy = -(int) bounce;
            dx = (int) (Math.sin(lt * 1.3) * (1.0f - k) * 3.0f * p);
            cur = tumbleFace(idx, lt);
            if (cur != lastFace) {
                lastFace = cur;
                play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f + (cur % 3) * 0.15f, 0.25f);
            }
            int frame = ((int) (lt / (1.0f + k * 4.0f))) % 3;
            // sombra
            int sh2 = Math.max(2, 7 - (int) (bounce / p));
            g.fill(cx + (8 - sh2) * p + dx, baseY + 16 * p + p, cx + (8 + sh2) * p + dx, baseY + 17 * p + p / 2, 0x88000000);
            if (frame == 1) {
                edge(g, cx + dx, baseY + dy, p, cur);
            } else {
                face(g, cx + dx, baseY + dy, p, cur, false);
            }
            bigText(g, font, "Rodando...", sw / 2, baseY + 18 * p + 2, 0xFFE0D6C0, 1.0f);
        } else {
            float since = finalHold ? t - (rolls.length - 1) * PER - ROLL : lt - ROLL;
            int step = (int) (idx * 1000 + 1);
            if (soundStep != step) {
                soundStep = step;
                if (v == 6) {
                    play(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.6f);
                } else if (v == 5) {
                    play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 0.7f);
                } else {
                    play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.8f);
                }
            }
            // aplastado al caer
            if (since < 3.0f) {
                dy = p;
            }
            if (v <= 4 && since < 10.0f) {
                dx = ((int) since % 2 == 0 ? 1 : -1) * p;
            }
            g.fill(cx + p, baseY + 16 * p + p, cx + 15 * p, baseY + 17 * p + p / 2, 0x88000000);
            if (v == 6) {
                // destellos pixelados dorados
                for (int i = 0; i < 8; ++i) {
                    double a = i * Math.PI / 4.0 + since * 0.05;
                    float r = (10.0f + Math.min(since, 20.0f) * 0.6f) * p;
                    int sx = (int) (sw / 2 + Math.cos(a) * r);
                    int sy = (int) (baseY + 8 * p + Math.sin(a) * r);
                    int c = ((int) since / 3 + i) % 2 == 0 ? 0xFFFFD166 : 0xFFFFF3B0;
                    g.fill(sx - p / 2, sy - p * 3 / 2, sx + p / 2 + 1, sy + p * 3 / 2 + 1, c);
                    g.fill(sx - p * 3 / 2, sy - p / 2, sx + p * 3 / 2 + 1, sy + p / 2 + 1, c);
                }
            }
            face(g, cx + dx, baseY + dy, p, v, v <= 4);
            if (v <= 4) {
                // cruz roja pixelada encima
                for (int i = 0; i < 12; ++i) {
                    px(g, cx + dx, baseY + dy, p, 2 + i, 2 + i, 2, 1, 0xCCFF2A2A);
                    px(g, cx + dx, baseY + dy, p, 12 - i, 2 + i, 2, 1, 0xCCFF2A2A);
                }
            }
            String msg;
            int col;
            if (v == 6) {
                msg = "¡6! ¡ROBO EXITOSO!";
                col = 0xFFFFD166;
            } else if (v == 5) {
                msg = "¡5! Tirá de nuevo";
                col = 0xFFFFF27A;
            } else {
                msg = "¡" + v + "! Fallaste";
                col = 0xFFFF5555;
            }
            float pop = since < 4.0f ? 1.0f + (4.0f - since) * 0.12f : 1.0f;
            bigText(g, font, msg, sw / 2, baseY + 18 * p + 2, col, pop);
        }
        bigText(g, font, "6 robás · 5 otra tirada · 1-4 fallás", sw / 2, py0 + ph - 2 * p - 7, 0xFF8E7B62, 0.7f);
    }
}
