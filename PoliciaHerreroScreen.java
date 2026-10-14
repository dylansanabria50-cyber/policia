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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

// Pantalla de habilidades del Herrero Legendario (se abre con el boton del
// inventario, igual que la de los otros oficios) y su HUD de arriba a la
// derecha. Las habilidades no se compran: se desbloquean solas al llegar
// al XP de cada una. Clic en una desbloqueada para elegirla (J la usa).
public class PoliciaHerreroScreen extends Screen {
    static final int PW = 460;
    static final int PH = 340;
    static final Item[] ICON = new Item[]{
            Items.GOAT_HORN, Items.ANVIL, Items.SPECTRAL_ARROW, Items.GOLDEN_APPLE, Items.CAMPFIRE,
            Items.CHAIN, Items.SMITHING_TABLE, Items.SHIELD, Items.SPYGLASS
    };
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
            g.renderItem(new ItemStack((ItemLike) ICON[i]), nx + 4, y + 4);
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
            } else if (h.cd[i] > 0) {
                line += "  (" + (h.cd[i] + 19) / 20 + " s)";
            }
            ls.add(line);
            cs.add(op ? (chosen ? -1 : -5192482) : -9408400);
        }
        int s = Math.max(0, Math.min(7, h.sel));
        String nm = PoliciaHerrero.SKILLS[s];
        String st;
        if ((s == 1 || s == 7) && h.anvilT > 0 && h.anvilType == (s == 1 ? 0 : 1)) {
            st = nm + " plantado: " + (h.anvilT + 19) / 20 + " s";
        } else if (s == 4 && h.faroT > 0) {
            st = "Faro encendido: " + (h.faroT + 19) / 20 + " s";
        } else if (s == 5 && h.linkT > 0) {
            st = "Vinculo activo: " + (h.linkT + 19) / 20 + " s";
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
