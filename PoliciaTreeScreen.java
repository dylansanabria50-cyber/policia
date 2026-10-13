package com.example.policia;

import java.util.List;
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

// Pantalla del arbol de habilidades de Policia: Escudo/REFUERZO/Tanque/
// Helicoptero/Esposas/Perro K9/Sirena/Dron/Burbuja, con zoom con la rueda.
public class PoliciaTreeScreen extends Screen {
    static final int PW = 460;
    static final int PH = 340;
    static final int LEFT_W = 104;
    static final String[] NAMES = new String[]{"Escudo balistico", "REFUERZO", "Tanque", "Helicoptero", "Tanque movil", "Esposas", "Perro K9", "Sirena y torreta", "Dron de vigilancia", "Escudo de burbuja"};
    static final String[] DESC = new String[]{"Despliega un escudo antidisturbios durante 12 s. Bloquea los golpes de frente.", "Policias de refuerzo que te protegen y atacan a tus enemigos.", "Invoca un tanque bajo tus pies. Apunta y dispara con clic derecho una sola vez; luego se retira. Enfriamiento: 30 s.", "Invoca un helicoptero policial pilotable: W/S avanzar, A/D desplazar, ESPACIO subir, CTRL bajar. Tecla de linterna: luz que sigue tu mirada. Dura 2 min y aterriza solo. Enfriamiento: 60 s.", "Mejora del Tanque: lo reemplaza. Conduce con WASD, 3 disparos con clic derecho; baja con Mayus y vuelve a subir con clic derecho. Enfriamiento: 30 s.", "Apunta a un objetivo (hasta 12 m) y lo esposas 15 s: no puede atacar ni interactuar y tu puedes arrastrarlo. Enfriamiento: 30 s.", "Invoca un perro policia que te protege, ataca amenazas y marca con brillo a los enemigos cercanos. Dura 45 s. Enfriamiento: 60 s.", "Despliega una patrulla con sirena: los mobs hostiles cercanos huyen de miedo y la torreta dispara a los que esten a tiro. Dura 20 s. Enfriamiento: 40 s.", "Un dron vigila sobre ti 45 s y marca con brillo, incluso tras las paredes, a las amenazas en 32 m. Enfriamiento: 60 s.", "Recompensa de la mision especial El cerebro de la organizacion. Una esfera azul de 14 bloques te sigue 23 s: todo lo que entra (enemigos, flechas, otros jugadores) se mueve muy lento, menos tu. Bloquea proyectiles y reduce el dano cuerpo a cuerpo. Enfriamiento: 26 s."};
    static final int[] CX = new int[]{174, 72, 174, 276, 174, 38, 106, 242, 310, 276};
    static final int[] ROW = new int[]{0, 1, 1, 1, 2, 2, 2, 2, 2, 0};
    int px;
    int py;
    float zoom = 1.0f;

    public PoliciaTreeScreen() {
        super(Component.literal("Rama de habilidades"));
    }

    @Override
    protected void init() {
        this.px = (this.width - 460) / 2;
        this.py = (this.height - 340) / 2;
        this.addRenderableWidget((GuiEventListener) Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.px + 460 - 66, this.py + 340 + 4, 60, 20).build());
    }

    static ItemStack icon(int i) {
        if (i == 0) {
            return new ItemStack((ItemLike) PoliciaShield.SHIELD.get());
        }
        if (i == 2) {
            return new ItemStack((ItemLike) Items.IRON_INGOT);
        }
        return new ItemStack((ItemLike) Items.IRON_SWORD);
    }

    static boolean has(PoliciaMod.Sync s, int i) {
        return i == 0 || s != null && (s.un >> i & 1) == 1;
    }

    int nodeX(int i) {
        return this.px + 104 + 8 + CX[i] - 12;
    }

    int nodeY(int i) {
        return this.py + 40 + ROW[i] * 70;
    }

    static String cost(int i) {
        return PoliciaMod.LEVEL_COST[i] > 0 ? PoliciaMod.LEVEL_COST[i] + " niveles de experiencia" : "10 XP";
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

    static void tankIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 10, x + 15, y + 14, -14276309);
        g.fill(x + 2, y + 11, x + 14, y + 13, -11907246);
        for (int k = 0; k < 4; ++k) {
            g.fill(x + 3 + k * 3, y + 11, x + 4 + k * 3, y + 13, -7696234);
        }
        g.fill(x + 2, y + 7, x + 14, y + 10, -10588096);
        g.fill(x + 2, y + 7, x + 14, y + 8, -8613546);
        g.fill(x + 5, y + 4, x + 11, y + 7, -9732280);
        g.fill(x + 5, y + 4, x + 11, y + 5, -7692190);
        g.fill(x + 7, y + 2, x + 9, y + 4, -12760536);
        g.fill(x + 11, y + 5, x + 16, y + 6, -13748192);
        g.fill(x + 15, y + 4, x + 16, y + 7, -14735850);
    }

    static void mobileIcon(GuiGraphics g, int x, int y) {
        PoliciaTreeScreen.tankIcon(g, x, y);
        g.fill(x, y + 15, x + 16, y + 16, -10428161);
        g.fill(x + 13, y + 14, x + 14, y + 17, -10428161);
        g.fill(x + 14, y + 15, x + 15, y + 16, -10428161);
    }

    static void reinforcementIcon(GuiGraphics g, int x, int y) {
        for (int k = 0; k < 2; ++k) {
            int ox = x + k * 9;
            g.fill(ox + 1, y + 1, ox + 6, y + 3, -14931366);
            g.fill(ox, y + 3, ox + 7, y + 4, -15722438);
            g.fill(ox + 1, y + 4, ox + 6, y + 7, -1525616);
            g.fill(ox + 2, y + 5, ox + 3, y + 6, -14671840);
            g.fill(ox + 4, y + 5, ox + 5, y + 6, -14671840);
            g.fill(ox, y + 7, ox + 7, y + 14, -13874534);
            g.fill(ox + 4, y + 8, ox + 6, y + 10, -1523648);
            g.fill(ox, y + 11, ox + 7, y + 12, -14671840);
            g.fill(ox + 1, y + 14, ox + 3, y + 16, -14933448);
            g.fill(ox + 4, y + 14, ox + 6, y + 16, -14933448);
        }
    }

    static void heliIcon(GuiGraphics g, int x, int y) {
        g.fill(x, y + 1, x + 16, y + 2, -5196616);
        g.fill(x + 7, y + 2, x + 9, y + 4, -11512486);
        g.fill(x + 3, y + 4, x + 12, y + 10, -10588096);
        g.fill(x + 3, y + 4, x + 12, y + 5, -8613546);
        g.fill(x + 3, y + 5, x + 6, y + 8, -8400656);
        g.fill(x + 12, y + 5, x + 16, y + 7, -11641292);
        g.fill(x + 14, y + 3, x + 16, y + 5, -12760536);
        g.fill(x + 15, y + 7, x + 16, y + 10, -5196616);
        g.fill(x + 5, y + 10, x + 6, y + 11, -14276309);
        g.fill(x + 9, y + 10, x + 10, y + 11, -14276309);
        g.fill(x + 2, y + 11, x + 13, y + 12, -14276309);
    }

    static void cuffIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 3, x + 6, y + 6, -6643544);
        g.fill(x, y + 5, x + 7, y + 13, -3091238);
        g.fill(x + 2, y + 7, x + 5, y + 11, -3632612);
        g.fill(x + 10, y + 3, x + 15, y + 6, -6643544);
        g.fill(x + 9, y + 5, x + 16, y + 13, -3091238);
        g.fill(x + 11, y + 7, x + 14, y + 11, -3632612);
        g.fill(x + 7, y + 8, x + 9, y + 10, -12565432);
        g.fill(x + 6, y + 9, x + 10, y + 10, -10460056);
        g.fill(x + 1, y + 12, x + 6, y + 13, -8353648);
        g.fill(x + 10, y + 12, x + 15, y + 13, -8353648);
    }

    static void dogIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 2, y + 1, x + 5, y + 6, -12965350);
        g.fill(x + 4, y + 3, x + 12, y + 11, -5211590);
        g.fill(x + 11, y + 6, x + 16, y + 11, -2578336);
        g.fill(x + 15, y + 6, x + 16, y + 8, -15724528);
        g.fill(x + 8, y + 5, x + 10, y + 7, -15724528);
        g.fill(x + 4, y + 11, x + 13, y + 13, -13874534);
        g.fill(x + 7, y + 13, x + 9, y + 15, -1523648);
        g.fill(x + 2, y + 11, x + 4, y + 16, -12965350);
    }

    static void sirenIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 3, y + 4, x + 8, y + 9, -2084816);
        g.fill(x + 8, y + 4, x + 13, y + 9, -13606672);
        g.fill(x + 3, y + 4, x + 13, y + 5, -1);
        g.fill(x + 2, y + 9, x + 14, y + 12, -7696234);
        g.fill(x + 1, y + 12, x + 15, y + 14, -14276309);
        g.fill(x, y + 3, x + 1, y + 4, -8080);
        g.fill(x + 15, y + 3, x + 16, y + 4, -8080);
        g.fill(x + 7, y + 1, x + 9, y + 3, -8080);
    }

    static void droneIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 4, x + 6, y + 5, -3090722);
        g.fill(x + 10, y + 4, x + 15, y + 5, -3090722);
        g.fill(x + 3, y + 5, x + 4, y + 8, -11512224);
        g.fill(x + 12, y + 5, x + 13, y + 8, -11512224);
        g.fill(x + 2, y + 8, x + 14, y + 9, -11512224);
        g.fill(x + 5, y + 7, x + 11, y + 11, -13881288);
        g.fill(x + 5, y + 7, x + 11, y + 8, -11512226);
        g.fill(x + 7, y + 11, x + 9, y + 13, -2084816);
        g.fill(x + 6, y + 13, x + 10, y + 14, -11512226);
    }

    static void bubbleIcon(GuiGraphics g, int x, int y) {
        for (int r = 0; r < 16; ++r) {
            double dy = (double) r - 7.5;
            int w = (int) Math.round(Math.sqrt(Math.max(0.0, 64.0 - dy * dy)));
            g.fill(x + 8 - w, y + r, x + 8 + w, y + r + 1, -13664288);
            if (w <= 2) continue;
            g.fill(x + 8 - w + 1, y + r, x + 8 + w - 1, y + r + 1, -9455873);
        }
        g.fill(x + 4, y + 3, x + 7, y + 5, -1);
        g.fill(x + 4, y + 5, x + 5, y + 7, -1);
        g.fill(x + 10, y + 11, x + 12, y + 12, -2559745);
    }

    void padlock(GuiGraphics g, int x, int y) {
        g.fill(x + 8, y + 3, x + 10, y + 11, -2565928);
        g.fill(x + 14, y + 3, x + 16, y + 11, -2565928);
        g.fill(x + 8, y + 3, x + 16, y + 5, -2565928);
        g.fill(x + 6, y + 10, x + 18, y + 20, -1525712);
        g.fill(x + 11, y + 13, x + 13, y + 17, -14013910);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int k;
        this.renderBackground(g);
        int rawMx = mx;
        int rawMy = my;
        mx = (int) (((float) mx - (float) this.width / 2.0f) / this.zoom + (float) this.width / 2.0f);
        my = (int) (((float) my - (float) this.height / 2.0f) / this.zoom + (float) this.height / 2.0f);
        g.pose().pushPose();
        g.pose().translate((float) this.width / 2.0f, (float) this.height / 2.0f, 0.0f);
        g.pose().scale(this.zoom, this.zoom, 1.0f);
        g.pose().translate((float) (-this.width) / 2.0f, (float) (-this.height) / 2.0f, 0.0f);
        PoliciaMod.Sync s = PoliciaClient.mine();
        int xp = s == null ? 0 : s.xp;
        int sel = s == null ? 0 : s.sel;
        int lvl = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.experienceLevel : 0;
        PoliciaTreeScreen.panel(g, this.px, this.py, this.px + 104, this.py + 340);
        this.tag(g, "Policia", this.px + 6, this.py - 6);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, this.px + 52, this.py + 340 - 30, 55, (float) (this.px + 52 - mx), (float) (this.py + 60 - my), this.minecraft.player);
        }
        int rx = this.px + 104 + 8;
        PoliciaTreeScreen.panel(g, rx, this.py, this.px + 460, this.py + 340);
        this.tag(g, "Habilidades", rx + 6, this.py - 6);
        g.drawString(this.font, "XP: " + xp + "   Niveles: " + lvl, rx + 8, this.py + 10, -1523648, true);
        g.drawString(this.font, "Desbloquear: clic izquierdo en el icono", rx + 8, this.py + 20, -6642512, false);
        int n = PoliciaMod.SKILLS.length;
        for (int i = 1; i < n; ++i) {
            int pre = PoliciaMod.PRE[i];
            if (i == 9) {
                int cy = this.nodeY(9) + 12;
                g.fill(this.nodeX(0) + 25, cy, this.nodeX(9) - 1, cy + 2, PoliciaTreeScreen.has(s, 9) ? -10440449 : -14005654);
                continue;
            }
            int col = PoliciaTreeScreen.has(s, i) ? -2068264 : -9815450;
            int x0 = this.nodeX(pre) + 11;
            int x1 = this.nodeX(i) + 11;
            int y0 = this.nodeY(pre) + 44;
            int y1 = this.nodeY(i);
            int ym = y1 - 10;
            g.fill(x0, y0, x0 + 2, ym + 2, col);
            g.fill(Math.min(x0, x1), ym, Math.max(x0, x1) + 2, ym + 2, col);
            g.fill(x1, ym, x1 + 2, y1, col);
        }
        int hov = -1;
        for (int i = 0; i < n; ++i) {
            boolean hover;
            int nx = this.nodeX(i);
            int y = this.nodeY(i);
            boolean open = PoliciaTreeScreen.has(s, i);
            boolean bl = hover = mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24;
            if (hover) {
                hov = i;
            }
            int border = open && i == sel ? -1 : (hover ? -5728 : -9745908);
            g.fill(nx - 1, y - 1, nx + 25, y + 25, border);
            g.fill(nx, y, nx + 24, y + 24, open ? -3632612 : -7707118);
            if (i == 1) {
                PoliciaTreeScreen.reinforcementIcon(g, nx + 4, y + 4);
            } else if (i == 2) {
                PoliciaTreeScreen.tankIcon(g, nx + 4, y + 4);
            } else if (i == 3) {
                PoliciaTreeScreen.heliIcon(g, nx + 4, y + 4);
            } else if (i == 4) {
                PoliciaTreeScreen.mobileIcon(g, nx + 4, y + 4);
            } else if (i == 5) {
                PoliciaTreeScreen.cuffIcon(g, nx + 4, y + 4);
            } else if (i == 6) {
                PoliciaTreeScreen.dogIcon(g, nx + 4, y + 4);
            } else if (i == 7) {
                PoliciaTreeScreen.sirenIcon(g, nx + 4, y + 4);
            } else if (i == 8) {
                PoliciaTreeScreen.droneIcon(g, nx + 4, y + 4);
            } else if (i == 9) {
                PoliciaTreeScreen.bubbleIcon(g, nx + 4, y + 4);
            } else {
                g.renderItem(PoliciaTreeScreen.icon(i), nx + 4, y + 4);
            }
            if (!open) {
                g.fill(nx, y, nx + 24, y + 24, -1728053248);
                this.padlock(g, nx, y);
            }
            List<FormattedCharSequence> nl = this.font.split(FormattedText.of(NAMES[i]), 64);
            for (k = 0; k < nl.size() && k < 2; ++k) {
                g.drawString(this.font, nl.get(k), nx + 12, y + 27 + k * 9, open ? -1 : -6250336);
            }
            if (i != 1 || !open) continue;
            g.drawString(this.font, "Nv " + Math.max(1, s == null ? 1 : s.rl) + "/4", nx + 12, y - 10, -1523648);
        }
        int show = hov >= 0 ? hov : (PoliciaTreeScreen.has(s, sel) ? sel : 0);
        int bx0 = rx + 6;
        int by0 = this.py + 258;
        int bx1 = this.px + 460 - 6;
        int by1 = this.py + 340 - 6;
        PoliciaTreeScreen.panel(g, bx0, by0, bx1, by1);
        boolean open = PoliciaTreeScreen.has(s, show);
        g.drawString(this.font, NAMES[show], bx0 + 5, by0 + 4, -1, true);
        List<FormattedCharSequence> lines = this.font.split(FormattedText.of(DESC[show]), bx1 - bx0 - 10);
        for (k = 0; k < lines.size() && k < (show == 1 && open ? 2 : 5); ++k) {
            g.drawString(this.font, lines.get(k), bx0 + 5, by0 + 15 + k * 9, open ? -3616548 : -8353648, false);
        }
        if (show == 1 && open) {
            int lv = Math.max(1, s == null ? 1 : s.rl);
            g.drawString(this.font, "Nv " + lv + "/4 - " + PoliciaRefuerzo.COUNT[lv] + " NPC - espada " + PoliciaRefuerzo.SWORD[lv], bx0 + 5, by0 + 36, -1523648, false);
            String upg = lv >= 4 ? "Nivel maximo" : "Clic derecho: mejorar (" + 10 * lv + " XP)";
            g.drawString(this.font, upg, bx0 + 5, by0 + 46, -6642512, false);
        }
        if (!open) {
            g.drawString(this.font, show == 9 ? "Bloqueada - se gana con la mision especial del comisario" : "Bloqueada - cuesta " + PoliciaTreeScreen.cost(show), bx0 + 5, by1 - 11, -1525712, false);
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
        if (btn == 1) {
            PoliciaMod.Sync s1 = PoliciaClient.mine();
            int nx1 = this.nodeX(1);
            int y1 = this.nodeY(1);
            if (PoliciaTreeScreen.has(s1, 1) && mx >= (double) nx1 && mx <= (double) (nx1 + 24) && my >= (double) y1 && my <= (double) (y1 + 24)) {
                PoliciaClient.send(30);
                return true;
            }
        }
        if (btn == 0) {
            PoliciaMod.Sync s = PoliciaClient.mine();
            for (int i = 0; i < PoliciaMod.SKILLS.length; ++i) {
                int nx = this.nodeX(i);
                int y = this.nodeY(i);
                if (!(mx >= (double) nx) || !(mx <= (double) (nx + 24)) || !(my >= (double) y) || !(my <= (double) (y + 24))) continue;
                PoliciaClient.send(PoliciaTreeScreen.has(s, i) ? 10 + i : 20 + i);
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
}
