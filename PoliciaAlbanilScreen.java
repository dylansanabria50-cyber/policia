package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;

// Pantalla del arbol de habilidades del Albanil: Pico/MURO-REFUGIO/AYUDANTE/
// LINTERNA/TRACTOR y sus mejoras, con zoom con la rueda del mouse.
public class PoliciaAlbanilScreen extends Screen {
    static final int PW = 460;
    static final int PH = 340;
    static final int LEFT_W = 104;
    static final String[] NAMES = new String[]{"Pico de albañil", "MURO", "REFUGIO", "AYUDANTE I", "AYUDANTE II", "AYUDANTE III", "LINTERNA", "TRACTOR", "REFUGIO II", "PICO II"};
    static final String[] DESC = new String[]{"Te da un pico de diamante durante 7 s, con Fuerza I, Prisa minera I y Fortuna II. No se puede soltar ni guardar en cofres; puedes encantarlo y conserva los encantamientos. Enfriamiento: 8 s.", "Levanta un muro de 3 de alto por 3 de largo frente a ti. Puedes hacer hasta 3 muros (pulsa J de nuevo para cada uno). Desaparecen a los 15 s. Enfriamiento: 5 s.", "Mejora de MURO: lo reemplaza. Casa sobre la superficie con puerta y piso de madera, cama y cofre, durante 35 s. Lo que dejes dentro (cofre, bloques, objetos) vuelve a estar en el mismo lugar cuando la invocas de nuevo. Enfriamiento: 40 s.", "PEDIR AYUDANTE: 1 ayudante con pico de piedra que sigue y defiende. H: orden (seguir, picar delante, buscar minerales). X: elegir área (rueda: largo, Mayús+rueda: ancho). Avisan de minerales (Y/N). U: su inventario. 60 s, enf. 30 s. Ves menas de carbón a 4 bloques.", "Nivel 2: 3 ayudantes con picos de hierro. Mientras están activos ves todos los minerales (menos diamante y netherita) a 4 bloques.", "Nivel 3: 4 ayudantes con picos de diamante. Mientras están activos ves todos los minerales a 4 bloques.", "LINTERNA: una luz que sigue tu mirada hasta 48 bloques durante 60 s (tecla J con la habilidad elegida, o tecla G). Enfriamiento: 20 s.", "TRACTOR: pala cargadora amarilla con cabina, inventario propio (54 casillas) y luces. J la invoca, te bajas o vuelves a subir; Mayús+J la retira. W/S avanzar, A/D girar, ESPACIO sube la pala, CTRL la baja, G luces, U inventario. Rompe lo natural que tiene delante (guarda lo que rompe), pero nunca construcciones de jugadores. Dura 45 s. Enfriamiento: 35 s.", "Mejora de REFUGIO (40 XP): una torre de madera de 4 pisos con anexo, camas, cofres, barriles, horno y jardín en el techo. La puerta mira hacia ti. Dura 3 minutos y luego desaparece. Lo que dejes en cualquier cofre, barril u horno se conserva para la próxima vez (el cofre principal de la planta baja recibe lo del REFUGIO anterior). Enfriamiento: 40 s.", "Mejora del Pico (25 XP): el pico dura 14 s en vez de 7 s y da Fuerza II y Prisa minera II (conserva Fortuna II y los encantamientos). Enfriamiento: 8 s."};
    static final int[] CX = new int[]{45, 45, 45, 130, 130, 130, 215, 300, 45, 215};
    static final int[] ROW = new int[]{0, 1, 2, 0, 1, 2, 0, 0, 3, 1};
    int px;
    int py;
    float zoom = 1.0f;

    public PoliciaAlbanilScreen() {
        super(Component.literal("Rama de habilidades"));
    }

    @Override
    protected void init() {
        this.px = (this.width - 460) / 2;
        this.py = (this.height - 340) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.px + 460 - 66, this.py + 340 + 4, 60, 20).build());
    }

    static boolean has(PoliciaAlbanil.AlbSync s, int i) {
        int lv;
        int n = lv = s == null ? 0 : s.lv;
        if (i == 9) {
            return s != null && s.pl >= 1;
        }
        if (i == 8) {
            return s != null && s.lv >= 3;
        }
        if (i == 7) {
            return s != null && s.tu >= 1;
        }
        if (i == 6) {
            return s != null && s.lu >= 1;
        }
        if (i >= 3) {
            return s != null && s.hl >= i - 2;
        }
        return i == 0 || lv >= i;
    }

    int nodeX(int i) {
        return this.px + 104 + 8 + CX[i] - 12;
    }

    int nodeY(int i) {
        return this.py + 40 + ROW[i] * 58;
    }

    static void pickIcon(GuiGraphics g, int x, int y) {
        for (int k = 0; k < 10; ++k) {
            int hx = x + 8 - k / 3;
            g.fill(hx, y + 5 + k, hx + 2, y + 6 + k, -7710165);
        }
        g.fill(x + 1, y + 3, x + 15, y + 5, -13580096);
        g.fill(x + 3, y + 2, x + 13, y + 3, -9768728);
        g.fill(x, y + 4, x + 2, y + 7, -14638944);
        g.fill(x + 14, y + 4, x + 16, y + 7, -14638944);
        g.fill(x + 6, y + 5, x + 10, y + 6, -14910344);
    }

    static void wallIcon(GuiGraphics g, int x, int y) {
        g.fill(x, y + 2, x + 16, y + 14, -10855846);
        for (int r = 0; r < 4; ++r) {
            int yy = y + 2 + r * 3;
            int off = r % 2 * 4;
            for (int c = -1; c < 3; ++c) {
                int bx = x + off + c * 8;
                int x0 = Math.max(x, bx);
                int x1 = Math.min(x + 16, bx + 7);
                if (x1 > x0) {
                    g.fill(x0, yy, x1, yy + 2, -5723992);
                }
                if (x1 <= x0) continue;
                g.fill(x0, yy, x1, yy + 1, -3881788);
            }
        }
    }

    static void houseIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 3, y + 8, x + 13, y + 15, -2510214);
        for (int k = 0; k < 7; ++k) {
            g.fill(x + 7 - k, y + 2 + k, x + 9 + k, y + 3 + k, -5227986);
        }
        g.fill(x + 7, y + 10, x + 10, y + 15, -9745878);
        g.fill(x + 4, y + 9, x + 6, y + 11, -8400656);
        g.fill(x + 11, y + 9, x + 12, y + 11, -8400656);
        g.fill(x + 3, y + 14, x + 13, y + 15, -7706054);
    }

    static void helperIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 4, y + 6, x + 12, y + 14, -2050934);
        g.fill(x + 3, y + 3, x + 13, y + 7, -999392);
        g.fill(x + 2, y + 6, x + 14, y + 7, -3630576);
        g.fill(x + 5, y + 9, x + 7, y + 10, -14671840);
        g.fill(x + 9, y + 9, x + 11, y + 10, -14671840);
        g.fill(x + 6, y + 12, x + 10, y + 13, -7714262);
        g.fill(x + 4, y + 14, x + 12, y + 16, -12952920);
    }

    static void lampIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 6, x + 5, y + 10, -11183002);
        g.fill(x + 5, y + 4, x + 9, y + 12, -7696487);
        g.fill(x + 9, y + 3, x + 11, y + 13, -991120);
        g.fill(x + 11, y + 5, x + 15, y + 11, -2130708304);
        g.fill(x + 12, y + 7, x + 16, y + 9, -1593835521);
    }

    static void tractorIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 5, y + 6, x + 13, y + 11, -1001970);
        g.fill(x + 8, y + 2, x + 13, y + 7, -3631600);
        g.fill(x + 9, y + 3, x + 12, y + 5, -6631184);
        g.fill(x + 3, y + 8, x + 6, y + 9, -1001970);
        g.fill(x, y + 9, x + 4, y + 13, -1001970);
        g.fill(x, y + 12, x + 4, y + 13, -7696487);
        g.fill(x + 5, y + 10, x + 9, y + 15, -14671840);
        g.fill(x + 10, y + 10, x + 14, y + 15, -14671840);
        g.fill(x + 6, y + 11, x + 8, y + 14, -7696487);
        g.fill(x + 11, y + 11, x + 13, y + 14, -7696487);
    }

    void padlock(GuiGraphics g, int x, int y) {
        g.fill(x + 8, y + 3, x + 10, y + 11, -2565928);
        g.fill(x + 14, y + 3, x + 16, y + 11, -2565928);
        g.fill(x + 8, y + 3, x + 16, y + 5, -2565928);
        g.fill(x + 6, y + 10, x + 18, y + 20, -1525712);
        g.fill(x + 11, y + 13, x + 13, y + 17, -14013910);
    }

    void tag(GuiGraphics g, String text, int x, int y) {
        int w = this.font.width(text) + 8;
        g.fill(x - 1, y - 1, x + w + 1, y + 13, -11183002);
        g.fill(x, y, x + w, y + 12, -7696487);
        g.drawString(this.font, text, x + 4, y + 2, -1, true);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int xp;
        this.renderBackground(g);
        int rawMx = mx;
        int rawMy = my;
        mx = (int) (((float) mx - (float) this.width / 2.0f) / this.zoom + (float) this.width / 2.0f);
        my = (int) (((float) my - (float) this.height / 2.0f) / this.zoom + (float) this.height / 2.0f);
        g.pose().pushPose();
        g.pose().translate((float) this.width / 2.0f, (float) this.height / 2.0f, 0.0f);
        g.pose().scale(this.zoom, this.zoom, 1.0f);
        g.pose().translate((float) (-this.width) / 2.0f, (float) (-this.height) / 2.0f, 0.0f);
        PoliciaMod.Sync ms = PoliciaClient.mine();
        PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
        int n = xp = ms == null ? 0 : ms.xp;
        int sel = s.sel == 1 ? (s.lv >= 3 ? 8 : (s.lv >= 2 ? 2 : 1)) : (s.sel == 2 ? 2 + Math.max(1, s.hl) : (s.sel == 3 ? 6 : (s.sel == 4 ? 7 : 0)));
        PoliciaTreeScreen.panel(g, this.px, this.py, this.px + 104, this.py + 340);
        this.tag(g, "Albañil", this.px + 6, this.py - 6);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, this.px + 52, this.py + 340 - 30, 55, (float) (this.px + 52 - mx), (float) (this.py + 60 - my), this.minecraft.player);
        }
        int rx = this.px + 104 + 8;
        PoliciaTreeScreen.panel(g, rx, this.py, this.px + 460, this.py + 340);
        this.tag(g, "Habilidades", rx + 6, this.py - 6);
        g.drawString(this.font, "XP: " + xp, rx + 8, this.py + 10, -1523648, true);
        g.drawString(this.font, "Desbloquear: clic izquierdo en el icono", rx + 8, this.py + 20, -6642512, false);
        for (int i = 1; i < 6; ++i) {
            if (i == 3) continue;
            int col = PoliciaAlbanilScreen.has(s, i) ? -2047888 : -9807306;
            int x0 = this.nodeX(i - 1) + 11;
            int y0 = this.nodeY(i - 1) + 44;
            int y1 = this.nodeY(i);
            g.fill(x0, y0, x0 + 2, y1, col);
        }
        int col = PoliciaAlbanilScreen.has(s, 8) ? -2047888 : -9807306;
        int x0 = this.nodeX(2) + 11;
        int y0 = this.nodeY(2) + 44;
        int y1 = this.nodeY(8);
        g.fill(x0, y0, x0 + 2, y1, col);
        int hov = -1;
        for (int i = 0; i < 10; ++i) {
            boolean hover;
            int nx = this.nodeX(i);
            int y = this.nodeY(i);
            boolean open = PoliciaAlbanilScreen.has(s, i);
            boolean bl = hover = mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24;
            if (hover) {
                hov = i;
            }
            int border = open && i == sel ? -1 : (hover ? -5728 : -9745908);
            g.fill(nx - 1, y - 1, nx + 25, y + 25, border);
            g.fill(nx, y, nx + 24, y + 24, open ? -3632612 : -7707118);
            if (i == 0 || i == 9) {
                PoliciaAlbanilScreen.pickIcon(g, nx + 4, y + 4);
            } else if (i == 1) {
                PoliciaAlbanilScreen.wallIcon(g, nx + 4, y + 4);
            } else if (i == 2 || i == 8) {
                PoliciaAlbanilScreen.houseIcon(g, nx + 4, y + 4);
            } else if (i == 6) {
                PoliciaAlbanilScreen.lampIcon(g, nx + 4, y + 4);
            } else if (i == 7) {
                PoliciaAlbanilScreen.tractorIcon(g, nx + 4, y + 4);
            } else {
                PoliciaAlbanilScreen.helperIcon(g, nx + 4, y + 4);
            }
            if (!open) {
                g.fill(nx, y, nx + 24, y + 24, -1728053248);
                this.padlock(g, nx, y);
            }
            g.drawString(this.font, NAMES[i], nx + 12, y + 27, open ? -1 : -6250336);
        }
        int show = hov >= 0 ? hov : sel;
        int bx0 = rx + 6;
        int by0 = this.py + 258;
        int bx1 = this.px + 460 - 6;
        int by1 = this.py + 340 - 6;
        PoliciaTreeScreen.panel(g, bx0, by0, bx1, by1);
        boolean open = PoliciaAlbanilScreen.has(s, show);
        g.drawString(this.font, NAMES[show], bx0 + 5, by0 + 4, -1, true);
        List<FormattedCharSequence> lines = this.font.split(FormattedText.of(DESC[show]), bx1 - bx0 - 10);
        for (int k = 0; k < lines.size() && k < 5; ++k) {
            g.drawString(this.font, lines.get(k), bx0 + 5, by0 + 15 + k * 9, open ? -3616548 : -8353648, false);
        }
        if (!open) {
            g.drawString(this.font, "Bloqueada - cuesta " + (show == 9 ? 25 : (show == 8 ? 40 : (show == 7 ? 15 : (show >= 3 && show < 6 ? 10 + 5 * (show - 3) : 10)))) + " XP", bx0 + 5, by1 - 11, -1525712, false);
        } else if (show == sel) {
            g.drawString(this.font, "Elegida (tecla J)", bx0 + 5, by1 - 11, -10428320, false);
        } else {
            g.drawString(this.font, "Clic para elegir", bx0 + 5, by1 - 11, -6642512, false);
        }
        g.pose().popPose();
        super.render(g, rawMx, rawMy, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        double rmx = mx;
        double rmy = my;
        mx = (mx - (double) this.width / 2.0) / (double) this.zoom + (double) this.width / 2.0;
        my = (my - (double) this.height / 2.0) / (double) this.zoom + (double) this.height / 2.0;
        if (btn == 0) {
            PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
            for (int i = 0; i < 10; ++i) {
                int nx = this.nodeX(i);
                int y = this.nodeY(i);
                if (!(mx >= (double) nx) || !(mx <= (double) (nx + 24)) || !(my >= (double) y) || !(my <= (double) (y + 24))) continue;
                if (i == 9) {
                    PoliciaClient.send(PoliciaAlbanilScreen.has(s, i) ? 10 : 44);
                } else if (i == 8) {
                    PoliciaClient.send(PoliciaAlbanilScreen.has(s, i) ? 11 : 43);
                } else if (i == 7) {
                    PoliciaClient.send(PoliciaAlbanilScreen.has(s, i) ? 15 : 42);
                } else if (i == 6) {
                    PoliciaClient.send(PoliciaAlbanilScreen.has(s, i) ? 14 : 41);
                } else if (i >= 3) {
                    if (i - 2 == s.hl + 1) {
                        PoliciaClient.send(40);
                    } else if (PoliciaAlbanilScreen.has(s, i)) {
                        PoliciaClient.send(13);
                    }
                } else {
                    PoliciaClient.send(PoliciaAlbanilScreen.has(s, i) ? 10 + i : 20 + i);
                }
                return true;
            }
        }
        return super.mouseClicked(rmx, rmy, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        this.zoom = Math.max(0.5f, Math.min(2.0f, this.zoom + (float) delta * 0.1f));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static void hud(GuiGraphics g, Minecraft mc, PoliciaMod.Sync ms, int sw, int sh) {
        String st;
        PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
        ArrayList<String> ls = new ArrayList<>();
        ArrayList<Integer> cs = new ArrayList<>();
        ls.add("OFICIO ALBAÑIL - XP " + ms.xp);
        cs.add(-1523648);
        String n1 = s.lv >= 3 ? "REFUGIO II" : (s.lv >= 2 ? "REFUGIO" : "MURO");
        ls.add((s.sel == 0 ? "> " : "  ") + "1. Pico de albañil" + (s.pl >= 1 ? " II" : ""));
        cs.add(s.sel == 0 ? -1 : -5192482);
        boolean o1 = s.lv >= 1;
        ls.add((s.sel == 1 && o1 ? "> " : "  ") + "2. " + n1 + (o1 ? "" : "  [10 XP]"));
        cs.add(o1 ? (s.sel == 1 ? -1 : -5192482) : -9408400);
        boolean o2 = s.hl >= 1;
        ls.add((s.sel == 2 && o2 ? "> " : "  ") + "3. AYUDANTE" + (o2 ? " " + s.hl : "  [10 XP]"));
        cs.add(o2 ? (s.sel == 2 ? -1 : -5192482) : -9408400);
        boolean o3 = s.lu >= 1;
        ls.add((s.sel == 3 && o3 ? "> " : "  ") + "4. LINTERNA" + (o3 ? "" : "  [10 XP]"));
        cs.add(o3 ? (s.sel == 3 ? -1 : -5192482) : -9408400);
        boolean o4 = s.tu >= 1;
        ls.add((s.sel == 4 && o4 ? "> " : "  ") + "5. TRACTOR" + (o4 ? "" : "  [15 XP]"));
        cs.add(o4 ? (s.sel == 4 ? -1 : -5192482) : -9408400);
        if (s.sel == 4 && o4) {
            st = s.ta > 0 ? "Tractor: " + (s.ta + 19) / 20 + " s (J: bajar o subir, Mayús+J: retirar)" : (s.tc > 0 ? "Tractor en enfriamiento: " + (s.tc + 19) / 20 + " s" : "Tractor listo (J)");
        } else if (s.sel == 3 && o3) {
            st = s.lt > 0 ? "Linterna: " + (s.lt + 19) / 20 + " s (J o G: apagar)" : (s.lc > 0 ? "Linterna en enfriamiento: " + (s.lc + 19) / 20 + " s" : "Linterna lista (J o G)");
        } else if (s.sel == 2 && o2) {
            String ord = PoliciaAyudante.ORDERS[Math.max(0, Math.min(2, s.mode))] + (s.mode == 1 ? (s.zl > 0 ? " " + s.zl + "x" + s.zw : " (X: área)") : "");
            st = s.ht > 0 ? "Ayudantes: " + (s.ht + 19) / 20 + " s - orden: " + ord + " (H)" : (s.hc > 0 ? "Ayudantes en enfriamiento: " + (s.hc + 19) / 20 + " s" : "Ayudantes listos (J) - orden: " + ord + " (H)");
        } else {
            st = s.sel == 0 || !o1 ? (s.pickT > 0 ? "Pico activo: " + (s.pickT + 19) / 20 + " s" : (s.c0 > 0 ? "Pico en enfriamiento: " + (s.c0 + 19) / 20 + " s" : "Pico listo (J)")) : (s.structT > 0 ? n1 + " activo: " + (s.structT + 19) / 20 + " s" + (s.lv < 2 ? " - muros " + s.walls + "/3 (J: otro)" : "") : (s.c1 > 0 ? n1 + " en enfriamiento: " + (s.c1 + 19) / 20 + " s" : n1 + " listo (J)"));
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
