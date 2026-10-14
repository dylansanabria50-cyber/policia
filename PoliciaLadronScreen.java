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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

// Pantalla del arbol de habilidades del Ladron: muestra el muneco del
// jugador a la izquierda y el arbol de nodos (Carterismo/Silbido/Suerte/
// Humo/Salto/Instinto/Polvo/Ganzua/Escape) a la derecha.
public class PoliciaLadronScreen extends Screen {
    static final int PW = 460;
    static final int PH = 340;
    static final int LEFT_W = 104;
    static final String[] NAMES = new String[]{"Carterismo", "Silbido", "Suerte", "Humo", "Salto", "Instinto", "Polvo", "Ganzúa", "Escape"};
    static final String[] DESC = new String[]{"Con el modo ladrón (B) activo, agachate (Mayús) detrás de un jugador o mob y haz clic derecho. Si sale bien abres sus bolsillos y te llevas lo que quieras. Siempre activa.", "Los mobs a 12 bloques miran para otro lado 7 s, quedan lentos y no te atacan: facilita acercarte por la espalda. Tecla J. Enfriamiento: 15 s.", "Trébol de cuatro hojas: inclina el dado del carterismo. Cada nivel da +7% de que un 1-4 se convierta en 6 (hasta 28%). Clic para mejorar.", "Bomba de humo: te vuelves invisible 8 s, corres más rápido y los mobs que te perseguían te pierden. Se elige con K y se usa con J. Enfriamiento: 30 s. Requiere Silbido.", "Salto de sombra: das un gran salto hacia donde miras y caes sin daño. Se elige con K y se usa con J. Enfriamiento: 12 s. Requiere Silbido.", "Sentido de ladrón: todo ser vivo a 24 bloques brilla 10 s, incluso a través de las paredes. Se elige con K y se usa con J. Enfriamiento: 25 s. Requiere Suerte nivel 1.", "Polvo cegador: los mobs a 6 bloques quedan ciegos, lentos y débiles 7 s y pierden su objetivo. Se elige con K y se usa con J. Enfriamiento: 20 s. Requiere Suerte nivel 1.", "Ganzúa: en las aldeas con comisaría hay cofres con llave (sin abrir) que nadie más puede abrir, y puertas de hierro. Agachate (Mayús) y clic derecho: 50% de éxito, +10% por cada nivel de Suerte. Si falla salta la alarma y la policía te busca. Requiere Suerte nivel 1.", "Escape: durante 10 s te vuelves invisible y más rápido, la policía te pierde de vista y, si no cometes ningún delito, tu nivel de Buscado vuelve a cero. Se elige con K y se usa con J. Enfriamiento: 90 s. Requiere Silbido."};
    static final int[] CX = new int[]{174, 90, 258, 40, 140, 208, 308, 258, 90};
    static final int[] ROW = new int[]{0, 1, 1, 2, 2, 2, 2, 2, 2};
    static final int[] PRE = new int[]{-1, 0, 0, 1, 1, 2, 2, 2, 1};
    static final int NN = 9;
    int px;
    int py;

    public PoliciaLadronScreen() {
        super(Component.literal("Rama del ladrón"));
    }

    @Override
    protected void init() {
        this.px = (this.width - 460) / 2;
        this.py = (this.height - 340) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.px + 460 - 66, this.py + 340 + 4, 60, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    int nodeX(int i) {
        return this.px + 104 + 8 + CX[i] - 12;
    }

    int nodeY(int i) {
        return this.py + 40 + ROW[i] * 70;
    }

    static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, -11183002);
        g.fill(x0, y0, x1, y1, -16118766);
    }

    void tag(GuiGraphics g, String text, int x, int y) {
        int w = this.font.width(text) + 8;
        g.fill(x - 1, y - 1, x + w + 1, y + 13, -11183002);
        g.fill(x, y, x + w, y + 12, -7696487);
        g.drawString(this.font, text, x + 4, y + 2, -1, true);
    }

    static void clover(GuiGraphics g, int x, int y, int s, int col) {
        int h = s / 2;
        g.fill(x + h / 2, y, x + h / 2 + h, y + h, col);
        g.fill(x + h + h / 2 - 1, y + h / 2, x + s, y + h / 2 + h, col);
        g.fill(x, y + h / 2, x + h, y + h / 2 + h, col);
        g.fill(x + h / 2, y + h, x + h / 2 + h, y + s, col);
        g.fill(x + h - 1, y + h - 1, x + h + 1, y + s + 3, -13730510);
    }

    boolean open(int i, boolean sil, int lv, int mask) {
        if (i == 0) {
            return true;
        }
        if (i == 1) {
            return sil;
        }
        if (i == 2) {
            return lv > 0;
        }
        return (mask >> i - 2 & 1) == 1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        PoliciaMod.Sync s = PoliciaClient.mine();
        int lv = s == null ? 0 : Math.max(0, Math.min(4, s.rl));
        boolean sil = s != null && (s.un & 1) != 0;
        int mask = s == null ? 0 : s.un;
        int xp = s == null ? 0 : s.xp;
        PoliciaLadronScreen.panel(g, this.px, this.py, this.px + 104, this.py + 340);
        this.tag(g, "Ladrón", this.px + 6, this.py - 6);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, this.px + 52, this.py + 340 - 30, 55, (float) (this.px + 52 - mx), (float) (this.py + 60 - my), this.minecraft.player);
        }
        int rx = this.px + 104 + 8;
        PoliciaLadronScreen.panel(g, rx, this.py, this.px + 460, this.py + 340);
        this.tag(g, "Habilidades", rx + 6, this.py - 6);
        g.drawString(this.font, "XP: " + xp, rx + 8, this.py + 10, -1523648, true);
        g.drawString(this.font, "Desbloquear / mejorar: clic izquierdo en el icono", rx + 8, this.py + 20, -6642512, false);
        for (int i = 1; i < 9; ++i) {
            int col = this.open(i, sil, lv, mask) ? -2068264 : -9815450;
            int x0 = this.nodeX(PRE[i]) + 11;
            int x1 = this.nodeX(i) + 11;
            int y0 = this.nodeY(PRE[i]) + 44;
            int y1 = this.nodeY(i);
            int ym = y1 - 10;
            g.fill(x0, y0, x0 + 2, ym + 2, col);
            g.fill(Math.min(x0, x1), ym, Math.max(x0, x1) + 2, ym + 2, col);
            g.fill(x1, ym, x1 + 2, y1, col);
        }
        int hov = -1;
        for (int i = 0; i < 9; ++i) {
            boolean hover;
            int nx = this.nodeX(i);
            int y = this.nodeY(i);
            boolean open = this.open(i, sil, lv, mask);
            boolean bl = hover = mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24;
            if (hover) {
                hov = i;
            }
            int border = hover ? -5728 : -9745908;
            g.fill(nx - 1, y - 1, nx + 25, y + 25, border);
            g.fill(nx, y, nx + 24, y + 24, open ? -3632612 : -7707118);
            if (i == 0) {
                g.renderItem(new ItemStack((ItemLike) PoliciaPlaca.GUANTE.get()), nx + 4, y + 4);
            } else if (i == 1) {
                // Aproximacion: icono de cuerno de cabra para representar el Silbido
                g.renderItem(new ItemStack((ItemLike) Items.GOAT_HORN), nx + 4, y + 4);
            } else if (i == 2) {
                PoliciaLadronScreen.clover(g, nx + 5, y + 4, 14, lv > 0 ? -12345273 : -12887234);
            } else {
                // Aproximacion de iconos: pedernal, polvora, pluma, catalejo, polvo de fuego, gancho de hilo, perla de ender
                g.renderItem(new ItemStack((ItemLike) (i == 3 ? Items.GUNPOWDER : (i == 4 ? Items.FEATHER : (i == 5 ? Items.SPYGLASS : (i == 6 ? Items.BLAZE_POWDER : (i == 7 ? Items.TRIPWIRE_HOOK : Items.ENDER_PEARL)))))), nx + 4, y + 4);
            }
            if (i == 1 && !sil) {
                g.fill(nx, y, nx + 24, y + 24, -1728053248);
            }
            if (i == 2 && lv == 0 || i >= 3 && !open) {
                g.fill(nx, y, nx + 24, y + 24, 0x55000000);
            }
            List<FormattedCharSequence> nl = this.font.split(FormattedText.of(NAMES[i]), 64);
            for (int k = 0; k < nl.size() && k < 2; ++k) {
                g.drawString(this.font, nl.get(k), nx + 12, y + 27 + k * 9, open ? -1 : -6250336);
            }
            if (i != 2) continue;
            g.drawString(this.font, "Nv " + lv + "/4", nx + 12, y - 10, -1523648);
        }
        int show = hov >= 0 ? hov : 0;
        int bx0 = rx + 6;
        int by0 = this.py + 258;
        int bx1 = this.px + 460 - 6;
        int by1 = this.py + 340 - 6;
        PoliciaLadronScreen.panel(g, bx0, by0, bx1, by1);
        g.drawString(this.font, NAMES[show], bx0 + 5, by0 + 4, -1, true);
        List<FormattedCharSequence> lines = this.font.split(FormattedText.of(DESC[show]), bx1 - bx0 - 10);
        for (int k = 0; k < lines.size() && k < 5; ++k) {
            g.drawString(this.font, lines.get(k), bx0 + 5, by0 + 15 + k * 9, -3616548, false);
        }
        if (show == 0) {
            g.drawString(this.font, "Probabilidad actual: " + PoliciaLadron.CHANCE[lv] + "%", bx0 + 5, by1 - 11, -10428320, false);
        } else if (show == 1) {
            g.drawString(this.font, sil ? "Desbloqueada (tecla J)" : "Bloqueada - cuesta 10 XP", bx0 + 5, by1 - 11, sil ? -10428320 : -1525712, false);
        } else if (show >= 3) {
            boolean op = this.open(show, sil, lv, mask);
            g.drawString(this.font, op ? (show == 7 ? "Desbloqueada (agachado + clic derecho)" : "Desbloqueada (K elige, J usa)") : "Bloqueada - cuesta 20 XP", bx0 + 5, by1 - 11, op ? -10428320 : -1525712, false);
        } else {
            g.drawString(this.font, lv >= 4 ? "Nivel máximo" : "Siguiente nivel: " + PoliciaLadron.CHANCE[lv + 1] + "% - cuesta " + 10 * (lv + 1) + " XP", bx0 + 5, by1 - 11, -1525712, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            PoliciaMod.Sync s = PoliciaClient.mine();
            boolean sil = s != null && (s.un & 1) != 0;
            int mask = s == null ? 0 : s.un;
            for (int i = 1; i < 9; ++i) {
                int nx = this.nodeX(i);
                int y = this.nodeY(i);
                if (!(mx >= (double) nx) || !(mx <= (double) (nx + 24)) || !(my >= (double) y) || !(my <= (double) (y + 24))) continue;
                if (i == 1 && !sil) {
                    PoliciaClient.send(81);
                } else if (i == 2) {
                    PoliciaClient.send(80);
                } else if (i >= 3 && (mask >> i - 2 & 1) == 0) {
                    PoliciaClient.send(82 + (i - 3));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    static void hud(GuiGraphics g, Minecraft mc, PoliciaMod.Sync s, int sw, int sh) {
        int lv = Math.max(0, Math.min(4, s.rl));
        ArrayList<String> ls = new ArrayList<>();
        ArrayList<Integer> cs = new ArrayList<>();
        ls.add("OFICIO LADRÓN - XP " + s.xp);
        cs.add(-1523648);
        ls.add("Carterismo: Mayús + clic der. (dado" + (lv > 0 ? ", suerte +" + lv * 7 + "%" : "") + ")");
        cs.add(-1);
        if (s.tcd > 0) {
            ls.add("BUSCADO " + "*".repeat(Math.min(5, s.tcd)));
            cs.add(-44976);
        }
        int[] cds = new int[]{s.c5, s.c6, s.c7, s.c8, s.c9, 0, s.rcd};
        boolean any = false;
        for (int a = 0; a < 7; ++a) {
            String pre;
            if ((s.un >> a & 1) == 0) continue;
            any = true;
            if (a == 5) {
                ls.add("  Ganzúa: agachado + clic");
                cs.add(-5187400);
                continue;
            }
            pre = a == s.sel ? "> " : "  ";
            if (cds[a] > 0) {
                ls.add(pre + PoliciaLadron.AN[a] + ": " + (cds[a] + 19) / 20 + " s");
                cs.add(-32672);
                continue;
            }
            ls.add(pre + PoliciaLadron.AN[a] + " listo");
            cs.add(a == s.sel ? -8323184 : -5187400);
        }
        if (any) {
            ls.add("J usar - K cambiar");
            cs.add(-6642512);
        } else {
            ls.add("Habilidades: desbloquea el Silbido [10 XP]");
            cs.add(-9408400);
        }
        int w = 0;
        for (String string : ls) {
            w = Math.max(w, mc.font.width(string));
        }
        int x = sw - w - 8;
        int n = 8;
        g.fill(x - 4, n - 3, sw - 4, n + ls.size() * 11 + 1, -1879048192);
        for (int i = 0; i < ls.size(); ++i) {
            g.drawString(mc.font, ls.get(i), x, n + i * 11, cs.get(i).intValue());
        }
    }
}
