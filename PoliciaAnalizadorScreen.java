package com.example.policia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

// Ventana del Analizador del Forjador (tecla O): a la izquierda todos los
// items del juego (incluidos los de otros mods), a la derecha los que ya
// se analizaron, y abajo el detalle del elegido con sus botones: Pedir
// mision / Cancelar mision para los que no tenes, y Otra receta /
// Confirmar / Craftear para los analizados. Borde blanco = sin analizar,
// verde = mision en curso, naranja = receta sin confirmar, dorado =
// receta confirmada.
public class PoliciaAnalizadorScreen extends Screen {
    static PoliciaAnalizador.State LAST;
    static final List<Item> ALL = new ArrayList<Item>();
    static final int W = 520;
    static final int H = 360;
    int px;
    int py;
    int scrollL;
    int scrollR;
    String selected = "";
    float open = 0.0f;
    // Botones del panel de detalle: {x0, y0, x1, y1, accion}. Se recalculan
    // en cada render y los usa mouseClicked.
    final List<int[]> btns = new ArrayList<int[]>();

    public PoliciaAnalizadorScreen() {
        super(Component.literal("Analizador del Forjador"));
        if (ALL.isEmpty()) {
            for (Item it : ForgeRegistries.ITEMS.getValues()) {
                if (it != Items.AIR) {
                    ALL.add(it);
                }
            }
        }
    }

    static void onState(PoliciaAnalizador.State m) {
        LAST = m;
    }

    static String id(Item it) {
        return BuiltInRegistries.ITEM.getKey(it).toString();
    }

    static Item item(String id) {
        try {
            return BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        } catch (Exception ex) {
            return Items.AIR;
        }
    }

    // El blob es "item:confirmada,ingrediente x cantidad,...;..." y los ids
    // llevan ':' (minecraft:diamond), por eso se corta por el ULTIMO ':'.
    static void parse(String blob, Map<String, List<String>> outItems, Map<String, Boolean> outConf) {
        if (blob.isEmpty()) {
            return;
        }
        for (String entry : blob.split(";")) {
            String[] parts = entry.split(",");
            int c = parts[0].lastIndexOf(':');
            if (c <= 0) continue;
            String item = parts[0].substring(0, c);
            outConf.put(item, parts[0].substring(c + 1).equals("1"));
            ArrayList<String> ings = new ArrayList<String>();
            for (int i = 1; i < parts.length; ++i) {
                ings.add(parts[i]);
            }
            outItems.put(item, ings);
        }
    }

    @Override
    protected void init() {
        this.px = (this.width - W) / 2;
        this.py = (this.height - H) / 2 + 6;
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.px + W - 60, this.py + H + 4, 60, 20).build());
        PoliciaAnalizadorScreen.act(4, "");
    }

    static void act(int kind, String item) {
        PoliciaMod.NET.sendToServer((Object) new PoliciaAnalizador.Act(kind, item));
    }

    @Override
    public void tick() {
        if (this.open < 1.0f) {
            this.open = Math.min(1.0f, this.open + 0.12f);
        }
    }

    static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, -11183002);
        g.fill(x0, y0, x1, y1, -16118766);
    }

    int state(String item) {
        if (LAST == null) {
            return 0;
        }
        if (item.equals(LAST.questItem)) {
            return 1;
        }
        if (LAST.confirmed.contains(item)) {
            return 3;
        }
        if (LAST.known.contains(item)) {
            return 2;
        }
        return 0;
    }

    int color(int st) {
        return switch (st) {
            case 1 -> -10440449;
            case 2 -> -19712;
            case 3 -> -181;
            default -> -7707118;
        };
    }

    void grid(GuiGraphics g, int x0, int y0, int x1, int y1, List<Item> items, int scroll, int mx, int my) {
        PoliciaAnalizadorScreen.panel(g, x0, y0, x1, y1);
        int cols = (x1 - x0 - 4) / 20;
        int rows = (y1 - y0 - 4) / 20;
        int start = scroll * cols;
        for (int row = 0; row < rows; ++row) {
            for (int col = 0; col < cols; ++col) {
                int idx = start + row * cols + col;
                if (idx >= items.size()) continue;
                Item it = items.get(idx);
                String id = PoliciaAnalizadorScreen.id(it);
                int nx = x0 + 2 + col * 20;
                int ny = y0 + 2 + row * 20;
                int st = this.state(id);
                boolean hover = mx >= nx && mx < nx + 18 && my >= ny && my < ny + 18;
                int border = id.equals(this.selected) ? -1 : (hover ? -5728 : this.color(st));
                g.fill(nx - 1, ny - 1, nx + 19, ny + 19, border);
                g.fill(nx, ny, nx + 18, ny + 18, st == 0 ? -16777216 : -14540254);
                g.renderItem(new ItemStack(it), nx + 1, ny + 1);
            }
        }
    }

    Item itemAt(int x0, int y0, int x1, int y1, List<Item> items, int scroll, int mx, int my) {
        int cols = (x1 - x0 - 4) / 20;
        int rows = (y1 - y0 - 4) / 20;
        int start = scroll * cols;
        for (int row = 0; row < rows; ++row) {
            for (int col = 0; col < cols; ++col) {
                int idx = start + row * cols + col;
                if (idx >= items.size()) continue;
                int nx = x0 + 2 + col * 20;
                int ny = y0 + 2 + row * 20;
                if (mx < nx || mx >= nx + 18 || my < ny || my >= ny + 18) continue;
                return items.get(idx);
            }
        }
        return null;
    }

    List<Item> known() {
        ArrayList<Item> out = new ArrayList<Item>();
        if (LAST != null) {
            for (String s : LAST.known) {
                Item it = PoliciaAnalizadorScreen.item(s);
                if (it != Items.AIR) {
                    out.add(it);
                }
            }
        }
        return out;
    }

    // Texto de la mision para un tipo/objetivo, con los nombres reales del
    // bloque, mob o material.
    static String questText(int type, String target, int need) {
        String tn = "";
        try {
            if (type == 0) {
                tn = BuiltInRegistries.BLOCK.get(new ResourceLocation(target)).getName().getString();
            } else if (type == 1) {
                tn = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(target)).getDescription().getString();
            } else if (type == 4) {
                tn = new ItemStack(PoliciaAnalizadorScreen.item(target)).getHoverName().getString();
            }
        } catch (Exception ignored) {
        }
        return switch (type) {
            case 0 -> "Mina " + need + " de " + tn;
            case 1 -> "Mata " + need + " " + tn;
            case 2 -> "Camina " + need + " bloques";
            case 3 -> "Sobrevive " + need / 20 + " s sin recibir danio";
            case 4 -> "Junta " + need + " de " + tn + " (se gastan al terminar)";
            case 5 -> "Pesca " + need + " veces";
            case 6 -> "Coloca " + need + " bloques";
            case 7 -> "Nada bajo el agua " + need / 20 + " s";
            case 8 -> "Tala " + need + " troncos";
            case 9 -> "Cosecha " + need + " cultivos maduros";
            case 10 -> "Cria " + need + " animales";
            case 11 -> "Comercia " + need + " veces con aldeanos";
            case 12 -> "Funde " + need + " items en un horno";
            case 13 -> "Craftea " + need + " objetos en la mesa";
            case 14 -> "Domestica " + need + " animales";
            case 15 -> "Subi hasta la altura 120";
            case 16 -> "Baja hasta la altura -30";
            case 17 -> "Come " + need + " veces";
            case 18 -> "Dormi en una cama hasta la manana";
            case 19 -> "Mata " + need + " monstruos con arco o ballesta";
            default -> "Salta " + need + " veces";
        };
    }

    static String progText(int type, int prog, int need) {
        if (type == 3 || type == 7) {
            return prog / 20 + "/" + need / 20 + " s";
        }
        return prog + "/" + need;
    }

    void button(GuiGraphics g, int x, int y, int w, String label, boolean enabled, int action, int mx, int my) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + 18;
        g.fill(x - 1, y - 1, x + w + 1, y + 19, enabled ? (hover ? -5728 : -11183002) : -13421773);
        g.fill(x, y, x + w, y + 18, enabled ? (hover ? -7696487 : -10066330) : -14540254);
        int tw = this.font.width(label);
        g.drawString(this.font, label, x + (w - tw) / 2, y + 5, enabled ? -1 : -8355712, true);
        if (enabled) {
            this.btns.add(new int[]{x, y, x + w, y + 18, action});
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        this.btns.clear();
        int lx0 = this.px;
        int lx1 = this.px + 250;
        int rx0 = this.px + 260;
        int rx1 = this.px + W;
        int gy1 = this.py + 200;
        g.drawString(this.font, "Todos los items (rueda: bajar)", lx0, this.py - 11, -1, true);
        this.grid(g, lx0, this.py, lx1, gy1, ALL, this.scrollL, mx, my);
        g.drawString(this.font, "Analizados", rx0, this.py - 11, -1, true);
        this.grid(g, rx0, this.py, rx1, gy1, this.known(), this.scrollR, mx, my);
        int dy0 = gy1 + 8;
        int dy1 = this.py + H;
        PoliciaAnalizadorScreen.panel(g, this.px, dy0, this.px + W, dy1);
        int tx = this.px + 8;
        int bx = this.px + W - 128;
        if (LAST != null && !LAST.questItem.isEmpty()) {
            String qn = new ItemStack(PoliciaAnalizadorScreen.item(LAST.questItem)).getHoverName().getString();
            g.drawString(this.font, "Mision en curso: " + qn + " - " + PoliciaAnalizadorScreen.questText(LAST.questType, LAST.questTarget, LAST.questNeed) + " (" + PoliciaAnalizadorScreen.progText(LAST.questType, LAST.questProg, LAST.questNeed) + ")", tx, dy1 - 12, -10440449, false);
        }
        if (this.selected.isEmpty()) {
            g.drawString(this.font, "Hace clic en un item para ver su receta o pedir una mision.", tx, dy0 + 6, -6642512, false);
        } else {
            Item it = PoliciaAnalizadorScreen.item(this.selected);
            ItemStack big = new ItemStack(it);
            g.renderItem(big, tx, dy0 + 6);
            g.drawString(this.font, big.getHoverName().getString(), tx + 22, dy0 + 10, -1, true);
            int st = this.state(this.selected);
            boolean questing = st == 1;
            boolean otherQuest = LAST != null && !LAST.questItem.isEmpty() && !questing;
            if (st == 0 || questing) {
                String[] q = PoliciaAnalizador.questFor(this.selected);
                int type = Integer.parseInt(q[0]);
                int need = PoliciaAnalizador.QUEST_NEED[type];
                g.drawString(this.font, "Sin analizar. Sostenelo 2 s en la mano secundaria para aprenderlo,", tx, dy0 + 30, -6642512, false);
                g.drawString(this.font, "o completa su mision:", tx, dy0 + 41, -6642512, false);
                g.drawString(this.font, PoliciaAnalizador.QUEST_NAMES[type] + ": " + PoliciaAnalizadorScreen.questText(type, q[1], need), tx, dy0 + 55, -10440449, false);
                if (questing) {
                    int pw = 220;
                    float f = LAST.questNeed <= 0 ? 0.0f : (float) LAST.questProg / (float) LAST.questNeed;
                    g.fill(tx, dy0 + 70, tx + pw, dy0 + 78, -14540254);
                    g.fill(tx, dy0 + 70, tx + (int) ((float) pw * f), dy0 + 78, -10440449);
                    g.drawString(this.font, PoliciaAnalizadorScreen.progText(type, LAST.questProg, LAST.questNeed), tx + pw + 6, dy0 + 70, -1, false);
                    this.button(g, bx, dy0 + 8, 120, "Cancelar mision", true, 5, mx, my);
                } else {
                    this.button(g, bx, dy0 + 8, 120, "Pedir mision", !otherQuest, 0, mx, my);
                    if (otherQuest) {
                        g.drawString(this.font, "Ya tenes otra mision", bx, dy0 + 30, -1525712, false);
                        this.button(g, bx, dy0 + 42, 120, "Cancelar la otra", true, 5, mx, my);
                    }
                }
            } else {
                Map<String, List<String>> ingMap = new HashMap<String, List<String>>();
                Map<String, Boolean> confMap = new HashMap<String, Boolean>();
                PoliciaAnalizadorScreen.parse(LAST.recipeBlob.toString(), ingMap, confMap);
                List<String> ings = ingMap.getOrDefault(this.selected, List.of());
                boolean conf = st == 3;
                g.drawString(this.font, conf ? "Receta confirmada:" : "Receta (sin confirmar, podes cambiarla):", tx, dy0 + 30, conf ? -181 : -19712, false);
                boolean all = !ings.isEmpty();
                int ix = tx;
                Minecraft mc = Minecraft.getInstance();
                for (String ing : ings) {
                    int x = ing.lastIndexOf('x');
                    if (x <= 0) continue;
                    String iid = ing.substring(0, x);
                    int n;
                    try {
                        n = Integer.parseInt(ing.substring(x + 1));
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    int have = mc.player == null ? 0 : PoliciaAnalizador.count(mc.player.getInventory(), iid);
                    if (have < n) {
                        all = false;
                    }
                    ItemStack is = new ItemStack(PoliciaAnalizadorScreen.item(iid), n);
                    g.fill(ix - 1, dy0 + 43, ix + 17, dy0 + 61, have >= n ? -14513374 : -10477022);
                    g.renderItem(is, ix, dy0 + 44);
                    g.renderItemDecorations(this.font, is, ix, dy0 + 44);
                    g.drawString(this.font, Math.min(have, 99) + "/" + n, ix, dy0 + 64, have >= n ? -10428320 : -1525712, false);
                    if (mx >= ix && mx < ix + 16 && my >= dy0 + 44 && my < dy0 + 60) {
                        this.hoverStack = is;
                    }
                    ix += 30;
                }
                g.drawString(this.font, all ? "Tenes todo: apreta Craftear" : "Te faltan materiales (rojo)", tx, dy0 + 78, all ? -10428320 : -1525712, false);
                this.button(g, bx, dy0 + 8, 120, "Craftear", all, 3, mx, my);
                if (!conf) {
                    this.button(g, bx, dy0 + 32, 120, "Otra receta", true, 1, mx, my);
                    this.button(g, bx, dy0 + 56, 120, "Confirmar receta", true, 2, mx, my);
                }
            }
        }
        super.render(g, mx, my, pt);
        Item hov = this.itemAt(lx0, this.py, lx1, gy1, ALL, this.scrollL, mx, my);
        if (hov == null) {
            hov = this.itemAt(rx0, this.py, rx1, gy1, this.known(), this.scrollR, mx, my);
        }
        if (hov != null) {
            g.renderTooltip(this.font, new ItemStack(hov), mx, my);
        } else if (this.hoverStack != null) {
            g.renderTooltip(this.font, this.hoverStack, mx, my);
        }
        this.hoverStack = null;
    }

    ItemStack hoverStack;

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) {
            return super.mouseClicked(mx, my, btn);
        }
        int lx0 = this.px;
        int lx1 = this.px + 250;
        int rx0 = this.px + 260;
        int rx1 = this.px + W;
        int gy1 = this.py + 200;
        Item l = this.itemAt(lx0, this.py, lx1, gy1, ALL, this.scrollL, (int) mx, (int) my);
        if (l != null) {
            this.selected = PoliciaAnalizadorScreen.id(l);
            return true;
        }
        Item r = this.itemAt(rx0, this.py, rx1, gy1, this.known(), this.scrollR, (int) mx, (int) my);
        if (r != null) {
            this.selected = PoliciaAnalizadorScreen.id(r);
            return true;
        }
        for (int[] b : this.btns) {
            if (mx < b[0] || mx >= b[2] || my < b[1] || my >= b[3]) continue;
            PoliciaAnalizadorScreen.act(b[4], b[4] == 5 ? "" : this.selected);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int cols = (250 - 4) / 20;
        if (mx < (double) (this.px + 255)) {
            int maxL = Math.max(0, (ALL.size() + cols - 1) / cols - 9);
            this.scrollL = Math.max(0, Math.min(maxL, this.scrollL - (int) Math.signum(delta) * 2));
        } else {
            int rc = (W - 260 - 4) / 20;
            int maxR = Math.max(0, (this.known().size() + rc - 1) / rc - 9);
            this.scrollR = Math.max(0, Math.min(maxR, this.scrollR - (int) Math.signum(delta) * 2));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
