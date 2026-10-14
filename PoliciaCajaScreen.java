package com.example.policia;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

// Minijuego de la caja fuerte (pixel art): girás la perilla con A/D, las
// flechas o la rueda del mouse. En el número correcto se escucha un "clac"
// más fuerte y la perilla destella. ESPACIO fija el número. Tres errores =
// alarma. ESC = te rendís sin hacer ruido.
public class PoliciaCajaScreen extends Screen {
    static final int N = 40;
    static final int TIME = 45 * 20;
    static final int[][] DIGITS = new int[][]{
            {7, 5, 5, 5, 7}, {2, 6, 2, 2, 7}, {7, 1, 7, 4, 7}, {7, 1, 7, 1, 7}, {5, 5, 7, 1, 1},
            {7, 4, 7, 1, 7}, {7, 4, 7, 5, 7}, {7, 1, 1, 1, 1}, {7, 5, 7, 5, 7}, {7, 5, 7, 1, 7}};
    final BlockPos pos;
    final int lv;
    final int[] combo;
    final int[] got;
    int step;
    int errors;
    int dial;
    float shown;
    long start;
    long flash;
    long wrongFlash;
    boolean sent;

    PoliciaCajaScreen(BlockPos pos, int lv, int[] combo) {
        super(Component.literal("Caja fuerte"));
        this.pos = pos;
        this.lv = lv;
        this.combo = combo;
        this.got = new int[combo.length];
        this.start = Util.getMillis();
        this.dial = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.random.nextInt(N) : 0;
        this.shown = this.dial;
    }

    static void open(BlockPos pos, int lv, int[] combo) {
        Minecraft.getInstance().setScreen(new PoliciaCajaScreen(pos, lv, combo));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static void play(SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    void send(int[] guess) {
        if (this.sent) return;
        this.sent = true;
        PoliciaMod.NET.sendToServer(new PoliciaCaja.Try(this.pos, guess));
    }

    void turn(int d) {
        this.dial = Math.floorMod(this.dial + d, N);
        if (this.step < this.combo.length && this.dial == this.combo[this.step]) {
            play(SoundEvents.IRON_TRAPDOOR_CLOSE, 2.0f, 0.9f);
            this.flash = Util.getMillis();
        } else {
            play(SoundEvents.UI_BUTTON_CLICK.value(), 1.9f + (this.dial % 3) * 0.05f, 0.12f);
        }
    }

    void lock() {
        if (this.step >= this.combo.length) return;
        if (this.dial == this.combo[this.step]) {
            this.got[this.step] = this.dial;
            ++this.step;
            play(SoundEvents.IRON_DOOR_OPEN, 1.5f, 0.6f);
            if (this.step >= this.combo.length) {
                play(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f);
                this.send(this.got.clone());
                this.onClose();
            }
        } else {
            ++this.errors;
            this.wrongFlash = Util.getMillis();
            play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.9f);
            if (this.errors >= PoliciaCaja.TRIES) {
                this.send(new int[]{-1});
                this.onClose();
            }
        }
    }

    @Override
    public boolean keyPressed(int key, int sc, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.send(new int[0]);
            this.onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_LEFT) {
            this.turn(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_D || key == GLFW.GLFW_KEY_RIGHT) {
            this.turn(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            this.lock();
            return true;
        }
        return super.keyPressed(key, sc, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        this.turn(d > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0) {
            this.lock();
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public void removed() {
        this.send(new int[0]);
        super.removed();
    }

    void px(GuiGraphics g, int ox, int oy, int p, int x, int y, int w, int h, int c) {
        g.fill(ox + x * p, oy + y * p, ox + (x + w) * p, oy + (y + h) * p, c);
    }

    void digit(GuiGraphics g, int x, int y, int p, int n, int c) {
        String s = Integer.toString(n);
        for (int k = 0; k < s.length(); ++k) {
            int[] rows = DIGITS[s.charAt(k) - '0'];
            for (int r = 0; r < 5; ++r) {
                for (int b = 0; b < 3; ++b) {
                    if ((rows[r] >> (2 - b) & 1) != 0) {
                        g.fill(x + (k * 4 + b) * p, y + r * p, x + (k * 4 + b + 1) * p, y + (r + 1) * p, c);
                    }
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        long now = Util.getMillis();
        float t = (now - this.start) / 50.0f;
        int left = (int) Math.max(0, TIME - t);
        if (left <= 0 && !this.sent) {
            this.send(new int[]{-1});
            this.onClose();
            return;
        }
        // la perilla gira suave hacia el número elegido
        float diff = ((this.dial - this.shown) % N + N + N / 2) % N - N / 2;
        this.shown += diff * 0.35f;
        int p = Math.max(2, Math.min(4, this.height / 80));
        int R = 26;
        int size = (R * 2 + 1) * p;
        int cx = this.width / 2;
        int cy = this.height / 2 + 6 * p;
        int ox = cx - size / 2;
        int oy = cy - size / 2;
        // marco de la caja (metal oscuro)
        int fw = size + 24 * p;
        int fh = size + 44 * p;
        int fx = cx - fw / 2;
        int fy = cy - size / 2 - 30 * p;
        g.fill(fx, fy, fx + fw, fy + fh, 0xFF2B2E33);
        g.fill(fx + p, fy + p, fx + fw - p, fy + fh - p, 0xFF4A4F57);
        g.fill(fx + 2 * p, fy + 2 * p, fx + fw - 2 * p, fy + fh - 2 * p, 0xFF3A3E45);
        for (int[] c : new int[][]{{4, 4}, {fw / p - 6, 4}, {4, fh / p - 6}, {fw / p - 6, fh / p - 6}}) {
            this.px(g, fx, fy, p, c[0], c[1], 2, 2, 0xFF8A9099);
        }
        // perilla: disco pixelado
        boolean glint = now - this.flash < 220L;
        boolean bad = now - this.wrongFlash < 300L;
        for (int y = -R; y <= R; ++y) {
            for (int x = -R; x <= R; ++x) {
                int d2 = x * x + y * y;
                if (d2 > R * R) continue;
                int col;
                if (d2 > (R - 2) * (R - 2)) {
                    col = 0xFF16181B;
                } else if (d2 > (R - 6) * (R - 6)) {
                    col = (x - y) > 0 ? 0xFF9AA1AB : 0xFFB9C0C9;
                } else if (d2 > (R - 8) * (R - 8)) {
                    col = 0xFF5E646D;
                } else {
                    col = (x + y) < -6 ? 0xFF7B828C : 0xFF6A717A;
                }
                if (glint && d2 <= (R - 8) * (R - 8)) col = 0xFFE8D27A;
                if (bad && d2 <= (R - 8) * (R - 8)) col = 0xFFB23A3A;
                g.fill(ox + (x + R) * p, oy + (y + R) * p, ox + (x + R + 1) * p, oy + (y + R + 1) * p, col);
            }
        }
        // marcas alrededor (cada número), números cada 5
        for (int i = 0; i < N; ++i) {
            double a = Math.toRadians((i - this.shown) * (360.0 / N) - 90.0);
            int r1 = i % 5 == 0 ? R - 6 : R - 4;
            for (int r = r1; r <= R - 3; ++r) {
                int x = (int) Math.round(Math.cos(a) * r);
                int y = (int) Math.round(Math.sin(a) * r);
                g.fill(ox + (x + R) * p, oy + (y + R) * p, ox + (x + R + 1) * p, oy + (y + R + 1) * p, 0xFF20232A);
            }
            if (i % 5 == 0) {
                int x = (int) Math.round(Math.cos(a) * (R - 12));
                int y = (int) Math.round(Math.sin(a) * (R - 12));
                int q = Math.max(1, p / 2);
                int w = (i >= 10 ? 7 : 3) * q;
                this.digit(g, ox + (x + R) * p - w / 2, oy + (y + R) * p - 5 * q / 2, q, i, 0xFF101215);
            }
        }
        // manija central
        this.px(g, ox, oy, p, R - 3, R - 3, 7, 7, 0xFF2C3036);
        this.px(g, ox, oy, p, R - 2, R - 2, 5, 5, 0xFF8F96A0);
        this.px(g, ox, oy, p, R - 1, R - 9, 3, 7, 0xFF8F96A0);
        // indicador fijo arriba
        this.px(g, ox, oy, p, R - 1, -4, 3, 1, 0xFFFF4040);
        this.px(g, ox, oy, p, R, -3, 1, 2, 0xFFFF4040);
        // número actual grande
        this.digit(g, cx - (this.dial >= 10 ? 7 : 3) * p / 2, fy + 6 * p, p, this.dial, glint ? 0xFFFFE27A : 0xFFE6E8EB);
        // progreso: casillas de la combinación
        int bw = 9 * p;
        int bx = cx - (this.combo.length * (bw + 2 * p)) / 2;
        int by = oy + size + 4 * p;
        for (int i = 0; i < this.combo.length; ++i) {
            int x = bx + i * (bw + 2 * p);
            g.fill(x, by, x + bw, by + 7 * p, 0xFF15171A);
            g.fill(x + p / 2, by + p / 2, x + bw - p / 2, by + 7 * p - p / 2, i < this.step ? 0xFF2E7D32 : (i == this.step ? 0xFF5A5F68 : 0xFF2A2D32));
            if (i < this.step) {
                int q = Math.max(1, p / 2);
                this.digit(g, x + 2 * p, by + 2 * p, q, this.got[i], 0xFFE8F5E9);
            }
        }
        // vidas (tres luces)
        for (int i = 0; i < PoliciaCaja.TRIES; ++i) {
            int x = fx + fw - (6 + i * 4) * p;
            g.fill(x, fy + 4 * p, x + 3 * p, fy + 7 * p, i < PoliciaCaja.TRIES - this.errors ? 0xFF43D17A : 0xFF6B1F1F);
        }
        // tiempo
        int tw = (int) ((fw - 8 * p) * (left / (float) TIME));
        g.fill(fx + 4 * p, fy + fh - 4 * p, fx + 4 * p + tw, fy + fh - 3 * p, left < 200 ? 0xFFE53935 : 0xFFE0B040);
        g.drawCenteredString(this.font, "Caja fuerte nivel " + this.lv + " · número " + Math.min(this.step + 1, this.combo.length) + " de " + this.combo.length, cx, fy - 12, 0xFFE8C170);
        g.drawCenteredString(this.font, "A/D o rueda: girar · ESPACIO o clic: fijar · ESC: rendirse", cx, fy + fh + 4, 0xFFB0B4BA);
        g.drawCenteredString(this.font, "Escuchá el «clac» fuerte: ese es el número", cx, fy + fh + 15, 0xFF8E939A);
    }
}
