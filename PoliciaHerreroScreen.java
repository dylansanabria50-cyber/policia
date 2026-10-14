package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

// Pantalla de habilidades del Herrero Legendario (se abre con el boton del
// inventario, igual que la de los otros oficios) y su HUD de arriba a la
// derecha. Las habilidades no se compran: se desbloquean solas al llegar
// al XP de cada una. Clic en una desbloqueada para elegirla (J la usa).
public class PoliciaHerreroScreen extends Screen {
    static final int PW = 460;
    static final int PH = 340;

    // Iconos dibujados a mano (16x16), uno por habilidad, como los del
    // arbol de policia. Cada fila es {x, y, ancho, alto, color}.
    static final int SKIN = 0xFFC68642, DARK = 0xFF2A2A2E, GRAY = 0xFF8A8A92, GRAY_L = 0xFFB8B8C0, GRAY_D = 0xFF55555D;
    static final int WOOD = 0xFF6B4423, WOOD_L = 0xFF8B5A2B, FIRE = 0xFFFF8C1A, FIRE_L = 0xFFFFB347, YEL = 0xFFFFE066;
    static final int[][][] ICONS = new int[][][]{
            // 0 Grito del Forjador: cabeza con la boca abierta y ondas de sonido
            {{1, 2, 7, 2, 0xFF4A2F1B}, {1, 3, 7, 8, SKIN}, {5, 5, 1, 1, DARK}, {5, 8, 3, 3, 0xFF6B0F0F}, {1, 11, 5, 3, 0xFF5A3A22},
             {9, 6, 1, 4, YEL}, {11, 4, 1, 8, YEL}, {13, 2, 1, 12, 0xFFFFF4C2}},
            // 1 Yunque Portatil: yunque con flecha (se lleva y se planta)
            {{0, 5, 3, 1, GRAY}, {1, 4, 11, 3, GRAY}, {1, 4, 11, 1, GRAY_L}, {4, 7, 5, 3, GRAY_D}, {2, 10, 9, 3, GRAY}, {2, 12, 9, 1, DARK},
             {13, 0, 2, 6, 0xFF4CC84C}, {12, 1, 4, 1, 0xFF4CC84C}, {11, 2, 6, 1, 0xFF4CC84C}},
            // 2 Marca del Herrero: mira roja
            {{3, 3, 10, 1, 0xFFE03030}, {3, 12, 10, 1, 0xFFE03030}, {3, 3, 1, 10, 0xFFE03030}, {12, 3, 1, 10, 0xFFE03030},
             {7, 0, 2, 3, 0xFFE03030}, {7, 13, 2, 3, 0xFFE03030}, {0, 7, 3, 2, 0xFFE03030}, {13, 7, 3, 2, 0xFFE03030}, {7, 7, 2, 2, 0xFFFF6060}},
            // 3 Forja Interior: corazon con una llama adentro
            {{3, 3, 3, 1, 0xFFD92B2B}, {10, 3, 3, 1, 0xFFD92B2B}, {2, 4, 12, 3, 0xFFD92B2B}, {3, 7, 10, 1, 0xFFD92B2B}, {4, 8, 8, 1, 0xFFD92B2B},
             {5, 9, 6, 1, 0xFFD92B2B}, {6, 10, 4, 1, 0xFFD92B2B}, {7, 11, 2, 1, 0xFFD92B2B}, {3, 4, 2, 1, 0xFFFF8080},
             {7, 5, 2, 4, FIRE}, {7, 4, 1, 1, YEL}, {7, 7, 2, 1, YEL}},
            // 4 Faro de Forja: fogata
            {{1, 13, 14, 2, WOOD}, {3, 11, 10, 2, WOOD_L}, {5, 5, 6, 6, FIRE}, {6, 3, 4, 3, FIRE_L}, {7, 1, 2, 2, YEL}, {7, 7, 2, 4, YEL},
             {2, 4, 1, 1, YEL}, {13, 3, 1, 1, YEL}},
            // 5 Vinculo de Acero: dos eslabones de cadena enganchados
            {{1, 2, 8, 2, GRAY}, {1, 6, 8, 2, GRAY_D}, {1, 2, 2, 6, GRAY}, {7, 2, 2, 6, GRAY_D}, {1, 2, 8, 1, GRAY_L},
             {6, 6, 8, 2, GRAY}, {6, 11, 8, 2, GRAY_D}, {6, 6, 2, 7, GRAY}, {12, 6, 2, 7, GRAY_D}, {6, 6, 8, 1, GRAY_L},
             {7, 5, 2, 3, GRAY_D}},
            // 6 Reparacion Maestra: martillo golpeando el yunque, con chispas
            {{2, 11, 12, 2, GRAY}, {2, 11, 12, 1, GRAY_L}, {6, 13, 4, 1, GRAY_D}, {4, 14, 8, 2, GRAY},
             {8, 1, 6, 3, GRAY_D}, {8, 1, 6, 1, GRAY_L}, {7, 4, 2, 2, WOOD_L}, {6, 6, 2, 2, WOOD_L}, {5, 8, 2, 2, WOOD},
             {11, 7, 1, 1, YEL}, {13, 9, 1, 1, YEL}, {12, 6, 1, 1, FIRE_L}, {2, 8, 1, 1, YEL}, {3, 6, 1, 1, FIRE_L}},
            // 7 Yunque Guardian: escudo con un yunque
            {{2, 1, 12, 9, 0xFF4A6FA5}, {3, 10, 10, 2, 0xFF4A6FA5}, {5, 12, 6, 2, 0xFF4A6FA5}, {7, 14, 2, 1, 0xFF4A6FA5}, {2, 1, 12, 1, 0xFF8FB3E0},
             {2, 1, 1, 9, 0xFF8FB3E0}, {4, 4, 8, 2, GRAY_L}, {6, 6, 4, 2, GRAY}, {5, 8, 6, 2, GRAY}},
            // 8 Analizador del Forjador: lupa
            {{4, 1, 5, 2, 0xFFD4AF37}, {4, 8, 5, 2, 0xFFD4AF37}, {2, 3, 2, 5, 0xFFD4AF37}, {9, 3, 2, 5, 0xFFD4AF37}, {4, 3, 5, 5, 0xFF9FD8F0},
             {5, 4, 1, 1, 0xFFFFFFFF}, {10, 9, 2, 2, 0xFF5A3A22}, {11, 10, 2, 2, 0xFF5A3A22}, {12, 11, 2, 2, 0xFF5A3A22}, {13, 12, 2, 2, 0xFF5A3A22}}
    };

    static void drawIcon(GuiGraphics g, int i, int x, int y) {
        for (int[] r : ICONS[i]) {
            g.fill(x + r[0], y + r[1], x + r[0] + r[2], y + r[1] + r[3], r[4]);
        }
    }

    int px;
    int py;

    public PoliciaHerreroScreen() {
        super(Component.literal("Habilidades del Herrero Legendario"));
    }

    @Override
    protected void init() {
        this.px = (this.width - PW) / 2;
        this.py = (this.height - PH) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.px + PW - 66, this.py + PH + 4, 60, 20).build());
    }

    static int need(int i) {
        return i < PoliciaHerrero.THRESH.length ? PoliciaHerrero.THRESH[i] : PoliciaHerrero.ANALIZADOR_XP;
    }

    static int xp() {
        PoliciaMod.Sync s = PoliciaClient.mine();
        return s == null ? 0 : s.xp;
    }

    // Igual que el servidor: las 8 activas se desbloquean en orden; la
    // pasiva (Analizador) solo depende de su propio XP.
    static boolean open(int i, int xp) {
        if (i == 8) {
            return xp >= PoliciaHerrero.ANALIZADOR_XP;
        }
        for (int k = 0; k <= i; ++k) {
            if (xp < PoliciaHerrero.THRESH[k]) {
                return false;
            }
        }
        return true;
    }

    int nodeX(int i) {
        return this.px + 112 + 40 + (i % 3) * 110;
    }

    int nodeY(int i) {
        return this.py + 40 + (i / 3) * 72;
    }

    void tag(GuiGraphics g, String text, int x, int y) {
        int w = this.font.width(text) + 8;
        g.fill(x - 1, y - 1, x + w + 1, y + 13, -11183002);
        g.fill(x, y, x + w, y + 12, -7696487);
        g.drawString(this.font, text, x + 4, y + 2, -1, true);
    }

    static String desc(int i) {
        String[] pg = PoliciaBook.HERRERO;
        if (i + 1 >= pg.length) {
            return "";
        }
        String t = pg[i + 1];
        int nl = t.indexOf("\n\n");
        if (nl >= 0) {
            t = t.substring(nl + 2);
        }
        return t.replace("\n", " ");
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        PoliciaHerrero.HerSync h = PoliciaHerrero.CL;
        int xp = xp();
        PoliciaLadronScreen.panel(g, this.px, this.py, this.px + 104, this.py + PH);
        this.tag(g, "Herrero", this.px + 6, this.py - 6);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, this.px + 52, this.py + PH - 60, 55, (float) (this.px + 52 - mx), (float) (this.py + 60 - my), this.minecraft.player);
        }
        g.drawString(this.font, "K: cambiar", this.px + 8, this.py + PH - 40, -6642512, false);
        g.drawString(this.font, "J: usar", this.px + 8, this.py + PH - 30, -6642512, false);
        g.drawString(this.font, "O: analizador", this.px + 8, this.py + PH - 20, -6642512, false);
        int rx = this.px + 112;
        PoliciaLadronScreen.panel(g, rx, this.py, this.px + PW, this.py + PH);
        this.tag(g, "Habilidades", rx + 6, this.py - 6);
        g.drawString(this.font, "XP: " + xp, rx + 8, this.py + 10, -1523648, true);
        g.drawString(this.font, "Se desbloquean solas con XP - clic para elegir", rx + 8, this.py + 21, -6642512, false);
        for (int i = 0; i < 9; ++i) {
            if (i % 3 == 2) continue;
            int x0 = this.nodeX(i) + 24;
            int x1 = this.nodeX(i + 1);
            int y = this.nodeY(i) + 11;
            g.fill(x0, y, x1, y + 2, open(i + 1, xp) ? -2068264 : -9815450);
        }
        int hov = -1;
        for (int i = 0; i < 9; ++i) {
            int nx = this.nodeX(i);
            int y = this.nodeY(i);
            boolean op = open(i, xp);
            boolean hover = mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24;
            if (hover) {
                hov = i;
            }
            boolean chosen = i < 8 && h.sel == i;
            int border = hover ? -5728 : (chosen ? -1523648 : -9745908);
            g.fill(nx - 1, y - 1, nx + 25, y + 25, border);
            g.fill(nx, y, nx + 24, y + 24, op ? -3632612 : -7707118);
            PoliciaHerreroScreen.drawIcon(g, i, nx + 4, y + 4);
            if (!op) {
                g.fill(nx, y, nx + 24, y + 24, 0x77000000);
            }
            List<FormattedCharSequence> nl = this.font.split(FormattedText.of(PoliciaHerrero.SKILLS[i]), 96);
            for (int k = 0; k < nl.size() && k < 2; ++k) {
                int w = this.font.width(nl.get(k));
                g.drawString(this.font, nl.get(k), nx + 12 - w / 2, y + 27 + k * 9, op ? -1 : -6250336);
            }
            String st;
            int col;
            if (!op) {
                st = need(i) + " XP";
                col = -1525712;
            } else if (i == 8) {
                st = "Pasiva (O)";
                col = -10428320;
            } else if (i < 8 && running(h, i)) {
                st = "Activa";
                col = 0xFFFFD24A;
            } else if (i < 8 && h.cd[i] > 0) {
                st = (h.cd[i] + 19) / 20 + " s";
                col = -8331009;
            } else {
                st = chosen ? "ELEGIDA" : "Lista";
                col = chosen ? -1523648 : -10428320;
            }
            int sw = this.font.width(st);
            g.drawString(this.font, st, nx + 12 - sw / 2, y + 47, col, false);
        }
        int show = hov >= 0 ? hov : Math.max(0, Math.min(7, h.sel));
        int bx0 = rx + 6;
        int by0 = this.py + 258;
        int bx1 = this.px + PW - 6;
        int by1 = this.py + PH - 6;
        PoliciaLadronScreen.panel(g, bx0, by0, bx1, by1);
        g.drawString(this.font, PoliciaHerrero.SKILLS[show], bx0 + 5, by0 + 4, -1, true);
        List<FormattedCharSequence> lines = this.font.split(FormattedText.of(desc(show)), bx1 - bx0 - 10);
        for (int k = 0; k < lines.size() && k < 5; ++k) {
            g.drawString(this.font, lines.get(k), bx0 + 5, by0 + 15 + k * 9, -3616548, false);
        }
        boolean op = open(show, xp);
        String foot = !op ? "Bloqueada - se desbloquea sola al llegar a " + need(show) + " XP (tenes " + xp + ")"
                : (show == 8 ? "Desbloqueada - siempre activa, tecla O abre el Analizador" : "Desbloqueada - clic para elegirla, J para usarla");
        g.drawString(this.font, foot, bx0 + 5, by1 - 11, op ? -10428320 : -1525712, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            for (int i = 0; i < 8; ++i) {
                int nx = this.nodeX(i);
                int y = this.nodeY(i);
                if (mx < nx || mx > nx + 24 || my < y || my > y + 24) continue;
                PoliciaClient.send(10 + i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // HUD de arriba a la derecha, con el mismo formato que los otros oficios.
    // Si la habilidad esta funcionando ahora (su enfriamiento todavia no
    // empezo: arranca cuando termina).
    static boolean running(PoliciaHerrero.HerSync h, int i) {
        return switch (i) {
            case 1 -> h.anvilT > 0 && h.anvilType == 0;
            case 7 -> h.anvilT > 0 && h.anvilType == 1;
            case 2 -> h.markT > 0;
            case 4 -> h.faroT > 0;
            case 5 -> h.linkT > 0;
            case 6 -> h.repT > 0;
            default -> false;
        };
    }

    static int runLeft(PoliciaHerrero.HerSync h, int i) {
        return switch (i) {
            case 1, 7 -> h.anvilT;
            case 2 -> h.markT;
            case 4 -> h.faroT;
            case 5 -> h.linkT;
            default -> 0;
        };
    }

    // HUD de arriba a la derecha, con el mismo formato que los otros oficios.
    static void hud(GuiGraphics g, Minecraft mc, PoliciaMod.Sync ms, int sw, int sh) {
        PoliciaHerrero.HerSync h = PoliciaHerrero.CL;
        int xp = ms.xp;
        ArrayList<String> ls = new ArrayList<>();
        ArrayList<Integer> cs = new ArrayList<>();
        ls.add("OFICIO HERRERO LEGENDARIO - XP " + xp);
        cs.add(-1523648);
        for (int i = 0; i < 9; ++i) {
            boolean op = open(i, xp);
            boolean chosen = i < 8 && h.sel == i && op;
            String line = (chosen ? "> " : "  ") + (i + 1) + ". " + PoliciaHerrero.SKILLS[i];
            if (!op) {
                line += "  [" + need(i) + " XP]";
            } else if (i == 8) {
                line += "  (pasiva, O)";
            } else if (running(h, i)) {
                line += "  (activa)";
            } else if (h.cd[i] > 0) {
                line += "  (" + (h.cd[i] + 19) / 20 + " s)";
            }
            ls.add(line);
            cs.add(op ? (running(h, i) ? 0xFFFFD24A : (chosen ? -1 : -5192482)) : -9408400);
        }
        int s = Math.max(0, Math.min(7, h.sel));
        String nm = PoliciaHerrero.SKILLS[s];
        String st;
        if (running(h, s)) {
            int left = runLeft(h, s);
            st = nm + " activa" + (left > 0 ? ": " + (left + 19) / 20 + " s" : "") + " (el enfriamiento empieza al terminar)";
        } else if (h.cd[s] > 0) {
            st = nm + " en enfriamiento: " + (h.cd[s] + 19) / 20 + " s";
        } else {
            st = nm + " lista (J)";
        }
        ls.add(st);
        cs.add(-8331009);
        float sc = 0.75f;
        int wmax = 0;
        for (String string : ls) {
            wmax = Math.max(wmax, mc.font.width(string));
        }
        g.pose().pushPose();
        g.pose().translate((float) (sw - 6) - (float) wmax * sc, 6.0f, 0.0f);
        g.pose().scale(sc, sc, 1.0f);
        for (int i = 0; i < ls.size(); ++i) {
            int n = i == 0 ? 0 : (i == ls.size() - 1 ? i * 10 + 6 : i * 10 + 2);
            g.drawString(mc.font, ls.get(i), 0, n, cs.get(i).intValue(), true);
        }
        g.pose().popPose();
    }
}
