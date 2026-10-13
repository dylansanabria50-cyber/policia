package com.example.policia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

// Pantalla del tablon de misiones: dibuja un "papel" procedural con textura
// de arrugas y una estrella de sello, y los botones de cada encargo.
public class PoliciaBoardScreen extends Screen {
    static final int PW = 320;
    static final int PH = 240;
    final PoliciaBoard.Open o;
    int px;
    int py;
    final int[][] rects = new int[7][4];

    public PoliciaBoardScreen(PoliciaBoard.Open o) {
        super(Component.literal("Tablon del comisario"));
        this.o = o;
    }

    public static void open(PoliciaBoard.Open m) {
        Minecraft.getInstance().setScreen(new PoliciaBoardScreen(m));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        this.px = (this.width - 320) / 2;
        this.py = (this.height - 240) / 2;
        for (int i = 0; i < 3; ++i) {
            this.rects[i] = new int[]{this.px + 22, this.py + 60 + i * 42, 276, 38};
        }
        this.rects[4] = new int[]{this.px + 18, this.py + 190, 70, 18};
        this.rects[3] = new int[]{this.px + 94, this.py + 190, 70, 18};
        this.rects[5] = new int[]{this.px + 170, this.py + 190, 70, 18};
        this.rects[6] = new int[]{this.px + 246, this.py + 190, 56, 18};
    }

    static void polyFill(GuiGraphics g, double[] xs, double[] ys, int col) {
        int n = xs.length;
        double mn = 1.0E9;
        double mx = -1.0E9;
        for (double y : ys) {
            mn = Math.min(mn, y);
            mx = Math.max(mx, y);
        }
        for (int y = (int) Math.floor(mn); y <= (int) Math.ceil(mx); ++y) {
            double yy = (double) y + 0.5;
            ArrayList<Double> xi = new ArrayList<>();
            for (int i = 0; i < n; ++i) {
                int j = (i + 1) % n;
                if (!(ys[i] <= yy && ys[j] > yy) && (!(ys[j] <= yy) || !(ys[i] > yy))) continue;
                xi.add(xs[i] + (yy - ys[i]) / (ys[j] - ys[i]) * (xs[j] - xs[i]));
            }
            Collections.sort(xi);
            int k = 0;
            while (k + 1 < xi.size()) {
                g.fill((int) Math.floor(xi.get(k)), y, (int) Math.ceil(xi.get(k + 1)), y + 1, col);
                k += 2;
            }
        }
    }

    void paper(GuiGraphics g) {
        int i;
        int i2;
        Random r = new Random(77L);
        int[] lef = new int[240];
        int[] rig = new int[240];
        for (i2 = 0; i2 < 240; ++i2) {
            lef[i2] = r.nextInt(4);
            rig[i2] = r.nextInt(4);
        }
        for (int y = 0; y < 240; ++y) {
            g.fill(this.px + lef[y], this.py + y, this.px + 320 - rig[y], this.py + y + 1, -3561110);
        }
        g.fill(this.px + 5, this.py + 5, this.px + 320 - 5, this.py + 240 - 5, -1781344);
        g.fill(this.px + 10, this.py + 10, this.px + 320 - 10, this.py + 240 - 10, -1320016);
        for (i2 = 0; i2 < 9; ++i2) {
            int cx = this.px + 12 + r.nextInt(280);
            int cy = this.py + 12 + r.nextInt(200);
            int s = 8 + r.nextInt(18);
            g.fill(cx, cy + s / 4, cx + s, cy + s - s / 4, 411064864);
            g.fill(cx + s / 4, cy, cx + s - s / 4, cy + s, 411064864);
        }
        for (i2 = 0; i2 < 14; ++i2) {
            int a = 240136208;
            g.fill(this.px + 5 + i2, this.py + 5 + i2, this.px + 6 + i2, this.py + 240 - 5 - i2, a);
            g.fill(this.px + 320 - 6 - i2, this.py + 5 + i2, this.px + 320 - 5 - i2, this.py + 240 - 5 - i2, a);
            g.fill(this.px + 5 + i2, this.py + 5 + i2, this.px + 320 - 5 - i2, this.py + 6 + i2, a);
            g.fill(this.px + 5 + i2, this.py + 240 - 6 - i2, this.px + 320 - 5 - i2, this.py + 240 - 5 - i2, a);
        }
        for (i2 = 0; i2 < 16; ++i2) {
            int x = this.px + 10 + r.nextInt(300);
            int y = this.py + 10 + r.nextInt(220);
            int len = 30 + r.nextInt(90);
            boolean horiz = r.nextBoolean();
            int c1 = 812666928;
            int c2 = 1090515664;
            int cx = x;
            int cy = y;
            for (int k = 0; k < len && cx >= this.px + 8 && cx <= this.px + 320 - 8 && cy >= this.py + 8 && cy <= this.py + 240 - 8; ++k) {
                g.fill(cx, cy, cx + 1, cy + 1, c1);
                g.fill(cx + (horiz ? 0 : 1), cy + (horiz ? 1 : 0), cx + (horiz ? 1 : 2), cy + (horiz ? 2 : 1), c2);
                if (horiz) {
                    ++cx;
                    if (r.nextInt(4) != 0) continue;
                    cy += r.nextInt(3) - 1;
                    continue;
                }
                ++cy;
                if (r.nextInt(4) != 0) continue;
                cx += r.nextInt(3) - 1;
            }
        }
        double cx = (double) this.px + 160.0;
        double cy = (double) this.py + 120.0 + 6.0;
        double[] xs = new double[10];
        double[] ys = new double[10];
        for (i = 0; i < 10; ++i) {
            double ang = Math.toRadians(-90 + 36 * i);
            double rad = i % 2 == 0 ? 78.0 : 32.0;
            xs[i] = cx + Math.cos(ang) * rad;
            ys[i] = cy + Math.sin(ang) * rad;
        }
        PoliciaBoardScreen.polyFill(g, xs, ys, 576733216);
        for (i = 0; i < 10; i += 2) {
            double ang = Math.toRadians(-90 + 36 * i);
            int bx = (int) (cx + Math.cos(ang) * 82.0);
            int by = (int) (cy + Math.sin(ang) * 82.0);
            g.fill(bx - 3, by - 3, bx + 3, by + 3, 576733216);
        }
        for (int[] p : new int[][]{{14, 14}, {302, 14}, {14, 222}, {302, 222}}) {
            g.fill(this.px + p[0], this.py + p[1], this.px + p[0] + 4, this.py + p[1] + 4, -7661030);
            g.fill(this.px + p[0] + 1, this.py + p[1] + 1, this.px + p[0] + 2, this.py + p[1] + 2, -2072480);
        }
    }

    boolean in(int i, int mx, int my) {
        int[] r = this.rects[i];
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    boolean enabled(int i) {
        switch (i) {
            case 0:
            case 1:
            case 2: {
                return true;
            }
            case 3: {
                return (this.o.flags & 2) != 0;
            }
            case 4: {
                return (this.o.flags & 1) != 0;
            }
            case 5: {
                return (this.o.flags & 8) != 0;
            }
        }
        return this.o.active > 0;
    }

    int type(int i) {
        return i == 0 ? this.o.a : (i == 1 ? this.o.b : this.o.c);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        this.paper(g);
        int ink = -12901360;
        int soft = -9745880;
        g.pose().pushPose();
        g.pose().translate((double) this.px + 160.0, (double) (this.py + 20), 0.0);
        g.pose().scale(1.5f, 1.5f, 1.0f);
        String t = "TABLON DEL COMISARIO";
        g.drawString(this.font, t, -this.font.width(t) / 2, 0, ink, false);
        g.pose().popPose();
        String sub = this.o.active > 0 ? "Mision en curso: " + PoliciaMision.TITLE[this.o.active] : "Elija un encargo, agente";
        g.drawString(this.font, sub, this.px + 160 - this.font.width(sub) / 2, this.py + 42, this.o.active > 0 ? -7661030 : soft, false);
        g.fill(this.px + 22, this.py + 53, this.px + 320 - 22, this.py + 54, 1615867920);
        for (int i = 0; i < 3; ++i) {
            int[] r = this.rects[i];
            int ty = this.type(i);
            boolean h = this.in(i, mx, my);
            if (h) {
                g.fill(r[0] - 2, r[1] - 1, r[0] + r[2] + 2, r[1] + r[3] + 1, 1084256304);
            }
            g.fill(r[0] - 2, r[1] + r[3] + 1, r[0] + r[2] + 2, r[1] + r[3] + 2, 810561552);
            g.drawString(this.font, i + 1 + ". " + PoliciaMision.TITLE[ty], r[0] + 2, r[1] + 2, ink, false);
            g.drawWordWrap(this.font, Component.literal(PoliciaMision.DESC[ty]), r[0] + 2, r[1] + 13, r[2] - 4, soft);
            int xp = PoliciaMision.XP[ty];
            String rw = xp + " XP  " + PoliciaMision.TIME[ty] / 1200 + " min";
            g.drawString(this.font, rw, r[0] + r[2] - this.font.width(rw) - 2, r[1] + 2, -13735122, false);
        }
        String[] names = new String[]{null, null, null, "Cerebro", "Jefe banda", "Casino", "Cancelar"};
        for (int i = 3; i < 7; ++i) {
            int[] r = this.rects[i];
            boolean en = this.enabled(i);
            boolean h = en && this.in(i, mx, my);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], h ? -4683202 : (en ? -3563430 : -4872826));
            g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, -9745880);
            g.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], -9745880);
            g.fill(r[0], r[1], r[0] + 1, r[1] + r[3], -9745880);
            g.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], -9745880);
            String s = names[i];
            if (i == 3 && (this.o.flags & 4) != 0) {
                s = "Cerebro: hecho";
            }
            g.drawString(this.font, s, r[0] + r[2] / 2 - this.font.width(s) / 2, r[1] + 5, en ? ink : -7701926, false);
        }
        String st = "Cumplidas: " + this.o.done + "   Jefes: " + this.o.bd + (this.o.need > 0 ? "   Faltan " + this.o.need + " para el jefe de banda" : "");
        g.drawString(this.font, st, this.px + 160 - this.font.width(st) / 2, this.py + 214, soft, false);
        for (int i = 3; i < 7; ++i) {
            if (!this.in(i, mx, my)) continue;
            String tip = i == 3 ? "Mision especial: El Cerebro (necesita 1 jefe cazado)" : (i == 4 ? "Cazar al jefe de banda" : (i == 5 ? (this.enabled(5) ? "Rescate en el casino subterraneo" : "No se conoce ningun casino todavia") : "Abandonar la mision actual"));
            g.drawString(this.font, tip, this.px + 160 - this.font.width(tip) / 2, this.py + 226, -7661030, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (int i = 0; i < 7; ++i) {
            if (!this.in(i, (int) mx, (int) my) || !this.enabled(i)) continue;
            PoliciaMod.NET.sendToServer(new PoliciaBoard.Pick(i));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent) SoundEvents.UI_BUTTON_CLICK, 1.0f));
            this.onClose();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }
}
