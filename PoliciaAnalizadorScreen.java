package com.example.policia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

// Ventana del Analizador del Forjador (tecla O), armada como el inventario
// creativo pero con los colores del Analizador (dorado de forja y el azul
// de la lente): pestanas por categoria (las mismas del creativo, incluidas
// las de otros mods) con paginas, buscador, "Analizados" y "Todos", grilla
// de 9x5 con barra de desplazamiento, y abajo el detalle del objeto
// elegido: su receta con Craftear / Otra receta / Confirmar, o su mision
// con Pedir mision / Cancelar.
public class PoliciaAnalizadorScreen extends Screen {
    static PoliciaAnalizador.State LAST;
    // Paleta del Analizador.
    static final int OUTLINE = 0xFF2A1C07;
    static final int PANEL = 0xFFD8BE82;
    static final int P_LIGHT = 0xFFFFF1C4;
    static final int P_DARK = 0xFF8A6526;
    static final int TAB_OFF = 0xFFB0904F;
    static final int SLOT = 0xFF9A7738;
    static final int S_DARK = 0xFF574013;
    static final int S_LIGHT = 0xFFF3DEA6;
    static final int TEXT = 0xFF3A2606;
    static final int LENS = 0xFF9FD8F0;
    static final int LENS_D = 0xFF3B7C97;
    static final int C_QUEST = 0xFF4CD964;
    static final int C_UNCONF = 0xFFFF9F2E;
    static final int C_CONF = 0xFFFFE14A;
    static final int PW = 195;
    static final int PH = 136;
    static final int DH = 72;
    static final int COLS = 9;
    static final int ROWS = 5;
    static final int TABS_PER_PAGE = 7;

    static class Tab {
        final String name;
        final ItemStack icon;
        final List<Item> items;
        final int kind;

        Tab(String name, ItemStack icon, List<Item> items, int kind) {
            this.name = name;
            this.icon = icon;
            this.items = items;
            this.kind = kind;
        }
    }

    static final int K_NORMAL = 0;
    static final int K_SEARCH = 1;
    static final int K_KNOWN = 2;
    static List<Tab> TABS;
    static int lastTab = 2;
    static int lastPage = 0;

    int tab;
    int page;
    int scroll;
    boolean dragging;
    String selected = "";
    EditBox search;
    List<Item> shown = new ArrayList<Item>();
    final List<int[]> btns = new ArrayList<int[]>();
    int x0;
    int ay;
    int py;
    int dy;

    public PoliciaAnalizadorScreen() {
        super(Component.literal("Analizador del Forjador"));
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

    // Pestanas: Buscar, Analizados, Todos y las categorias del creativo.
    static void buildTabs() {
        List<Tab> out = new ArrayList<Tab>();
        List<Item> all = new ArrayList<Item>();
        for (Item it : ForgeRegistries.ITEMS.getValues()) {
            if (it != Items.AIR) {
                all.add(it);
            }
        }
        out.add(new Tab("Buscar", new ItemStack(Items.COMPASS), all, K_SEARCH));
        out.add(new Tab("Analizados", new ItemStack(Items.KNOWLEDGE_BOOK), new ArrayList<Item>(), K_KNOWN));
        out.add(new Tab("Todos los objetos", new ItemStack(Items.SPYGLASS), all, K_NORMAL));
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());
            }
            for (CreativeModeTab t : net.minecraftforge.common.CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
                if (t.getType() != CreativeModeTab.Type.CATEGORY) continue;
                LinkedHashSet<Item> set = new LinkedHashSet<Item>();
                for (ItemStack st : t.getDisplayItems()) {
                    if (!st.isEmpty()) {
                        set.add(st.getItem());
                    }
                }
                if (set.isEmpty()) continue;
                out.add(new Tab(t.getDisplayName().getString(), t.getIconItem(), new ArrayList<Item>(set), K_NORMAL));
            }
        } catch (Throwable ignored) {
            // Sin categorias: quedan Buscar, Analizados y Todos.
        }
        TABS = out;
    }

    @Override
    protected void init() {
        if (TABS == null) {
            PoliciaAnalizadorScreen.buildTabs();
        }
        this.tab = Mth.clamp(lastTab, 0, TABS.size() - 1);
        this.page = this.tab / TABS_PER_PAGE;
        this.x0 = (this.width - PW) / 2;
        int total = 20 + 28 + PH + 4 + DH;
        this.ay = Math.max(2, (this.height - total) / 2);
        this.py = this.ay + 20 + 28;
        this.dy = this.py + PH + 4;
        this.search = new EditBox(this.font, this.x0 + 82, this.py + 6, 88, 9, Component.literal("Buscar"));
        this.search.setMaxLength(50);
        this.search.setBordered(false);
        this.search.setTextColor(0xFFFFFF);
        this.search.setResponder(s -> this.refresh());
        this.addRenderableWidget(this.search);
        this.refresh();
        PoliciaAnalizadorScreen.act(4, "");
    }

    static void act(int kind, String item) {
        PoliciaMod.NET.sendToServer((Object) new PoliciaAnalizador.Act(kind, item));
    }

    Tab cur() {
        return TABS.get(Mth.clamp(this.tab, 0, TABS.size() - 1));
    }

    void refresh() {
        Tab t = this.cur();
        boolean isSearch = t.kind == K_SEARCH;
        this.search.visible = isSearch;
        this.search.setFocused(isSearch);
        this.setFocused(isSearch ? this.search : null);
        if (t.kind == K_KNOWN) {
            this.shown = this.known();
        } else if (isSearch) {
            String q = this.search.getValue().trim().toLowerCase(Locale.ROOT);
            List<Item> out = new ArrayList<Item>();
            for (Item it : t.items) {
                if (q.isEmpty() || new ItemStack(it).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q) || PoliciaAnalizadorScreen.id(it).contains(q)) {
                    out.add(it);
                }
            }
            this.shown = out;
        } else {
            this.shown = t.items;
        }
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());
    }

    int maxScroll() {
        return Math.max(0, (this.shown.size() + COLS - 1) / COLS - ROWS);
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


    // ---------------- Dibujo estilo inventario ----------------

    static void bevel(GuiGraphics g, int x0, int y0, int x1, int y1, int fill, int light, int dark) {
        g.fill(x0, y0, x1, y1, OUTLINE);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        g.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 3, light);
        g.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 2, light);
        g.fill(x0 + 3, y1 - 3, x1 - 1, y1 - 1, dark);
        g.fill(x1 - 3, y0 + 3, x1 - 1, y1 - 1, dark);
    }

    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT);
        g.fill(x, y, x + 17, y + 1, S_DARK);
        g.fill(x, y, x + 1, y + 17, S_DARK);
        g.fill(x + 1, y + 17, x + 18, y + 18, S_LIGHT);
        g.fill(x + 17, y + 1, x + 18, y + 18, S_LIGHT);
    }

    static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    int tabX(int i) {
        return this.x0 + (i % TABS_PER_PAGE) * 28;
    }

    int pages() {
        return (TABS.size() + TABS_PER_PAGE - 1) / TABS_PER_PAGE;
    }

    void drawTab(GuiGraphics g, int i, boolean sel) {
        int x = this.tabX(i);
        int y = this.py - 28;
        if (sel) {
            g.fill(x, y, x + 26, this.py + 2, OUTLINE);
            g.fill(x + 1, y + 1, x + 25, this.py + 2, PANEL);
            g.fill(x + 1, y + 1, x + 24, y + 3, P_LIGHT);
            g.fill(x + 1, y + 1, x + 3, this.py + 2, P_LIGHT);
            g.fill(x + 23, y + 3, x + 25, this.py, P_DARK);
        } else {
            g.fill(x, y + 2, x + 26, this.py, OUTLINE);
            g.fill(x + 1, y + 3, x + 25, this.py, TAB_OFF);
            g.fill(x + 1, y + 3, x + 24, y + 5, PANEL);
            g.fill(x + 23, y + 5, x + 25, this.py, P_DARK);
        }
        g.renderItem(TABS.get(i).icon, x + 5, y + (sel ? 7 : 9));
    }

    void button(GuiGraphics g, int x, int y, int w, String label, boolean enabled, int action, int mx, int my) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + 14;
        if (enabled) {
            PoliciaAnalizadorScreen.bevel(g, x, y, x + w, y + 14, hover ? LENS : 0xFFC9A34E, hover ? 0xFFE6F7FF : 0xFFFFE9A8, hover ? LENS_D : 0xFF6E4E17);
        } else {
            PoliciaAnalizadorScreen.bevel(g, x, y, x + w, y + 14, 0xFF8F8A7E, 0xFFB5B0A3, 0xFF5E5A50);
        }
        int tw = this.font.width(label);
        g.drawString(this.font, label, x + (w - tw) / 2, y + 3, enabled ? TEXT : 0xFF4E4A40, false);
        if (enabled) {
            this.btns.add(new int[]{x, y, x + w, y + 14, action});
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        this.btns.clear();
        if (this.cur().kind == K_KNOWN) {
            this.shown = this.known();
        }
        int x0 = this.x0;
        int py = this.py;
        // Paginas de pestanas, como el "1 / 2" del creativo.
        int pages = this.pages();
        if (pages > 1) {
            String pg = (this.page + 1) + " / " + pages;
            g.drawString(this.font, pg, x0 + PW / 2 - this.font.width(pg) / 2, this.ay + 4, 0xFFFFFFFF, true);
            this.button(g, x0, this.ay, 18, "<", this.page > 0, 100, mx, my);
            this.button(g, x0 + PW - 18, this.ay, 18, ">", this.page < pages - 1, 101, mx, my);
        }
        // Pestanas sin elegir (detras del panel).
        int first = this.page * TABS_PER_PAGE;
        int last = Math.min(TABS.size(), first + TABS_PER_PAGE);
        for (int i = first; i < last; ++i) {
            if (i != this.tab) {
                this.drawTab(g, i, false);
            }
        }
        // Panel principal.
        PoliciaAnalizadorScreen.bevel(g, x0, py, x0 + PW, py + PH, PANEL, P_LIGHT, P_DARK);
        if (this.tab >= first && this.tab < last) {
            this.drawTab(g, this.tab, true);
        }
        Tab t = this.cur();
        if (t.kind == K_SEARCH) {
            g.drawString(this.font, "Buscar:", x0 + 8, py + 6, TEXT, false);
            g.fill(x0 + 80, py + 4, x0 + 172, py + 16, OUTLINE);
            g.fill(x0 + 81, py + 5, x0 + 171, py + 15, 0xFF2B2B2B);
        } else {
            String title = this.font.plainSubstrByWidth(t.name + "  (" + this.shown.size() + ")", PW - 16);
            g.drawString(this.font, title, x0 + 8, py + 6, TEXT, false);
        }
        // Grilla 9x5.
        int gx = x0 + 9;
        int gy = py + 18;
        Item hovered = null;
        int start = this.scroll * COLS;
        for (int r = 0; r < ROWS; ++r) {
            for (int c = 0; c < COLS; ++c) {
                int sx = gx + c * 18;
                int sy = gy + r * 18;
                PoliciaAnalizadorScreen.slot(g, sx - 1, sy - 1);
                int idx = start + r * COLS + c;
                if (idx >= this.shown.size()) continue;
                Item it = this.shown.get(idx);
                String id = PoliciaAnalizadorScreen.id(it);
                g.renderItem(new ItemStack(it), sx, sy);
                int st = this.state(id);
                if (st != 0) {
                    PoliciaAnalizadorScreen.outline(g, sx - 1, sy - 1, 18, 18, st == 1 ? C_QUEST : (st == 2 ? C_UNCONF : C_CONF));
                }
                if (id.equals(this.selected)) {
                    PoliciaAnalizadorScreen.outline(g, sx - 1, sy - 1, 18, 18, 0xFFFFFFFF);
                    PoliciaAnalizadorScreen.outline(g, sx, sy, 16, 16, LENS);
                }
                if (mx >= sx - 1 && mx < sx + 17 && my >= sy - 1 && my < sy + 17) {
                    hovered = it;
                    g.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                }
            }
        }
        if (this.shown.isEmpty()) {
            String msg = t.kind == K_KNOWN ? "Todavia no analizaste nada" : "No hay objetos";
            g.drawString(this.font, msg, gx + 81 - this.font.width(msg) / 2, gy + 40, TEXT, false);
        }
        // Barra de desplazamiento (con la lente del Analizador).
        int bx = x0 + 175;
        g.fill(bx, gy - 1, bx + 14, gy + 89, S_DARK);
        g.fill(bx + 1, gy, bx + 14, gy + 89, SLOT);
        int ms = this.maxScroll();
        int thumbY = gy + (ms == 0 ? 0 : (int) ((float) this.scroll / (float) ms * 74.0f));
        if (ms > 0) {
            PoliciaAnalizadorScreen.bevel(g, bx + 1, thumbY, bx + 13, thumbY + 15, LENS, 0xFFE6F7FF, LENS_D);
        } else {
            PoliciaAnalizadorScreen.bevel(g, bx + 1, thumbY, bx + 13, thumbY + 15, 0xFFB5A27A, 0xFFD8CBA9, 0xFF7A6A48);
        }
        // Leyenda / mision en curso, debajo de la grilla.
        int ly = gy + 92;
        if (LAST != null && !LAST.questItem.isEmpty()) {
            String qn = new ItemStack(PoliciaAnalizadorScreen.item(LAST.questItem)).getHoverName().getString();
            String line = "Mision: " + qn + " " + PoliciaAnalizadorScreen.progText(LAST.questType, LAST.questProg, LAST.questNeed);
            g.fill(x0 + 8, ly + 3, x0 + 14, ly + 9, C_QUEST);
            g.drawString(this.font, this.font.plainSubstrByWidth(line, PW - 26), x0 + 18, ly + 2, TEXT, false);
            ly += 12;
        }
        g.pose().pushPose();
        g.pose().translate((float) (x0 + 8), (float) (ly + 2), 0.0f);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        int lx = 0;
        int[] lc = new int[]{C_UNCONF, C_CONF, C_QUEST};
        String[] ln = new String[]{"sin confirmar", "confirmada", "mision"};
        for (int i = 0; i < 3; ++i) {
            g.fill(lx, 1, lx + 7, 8, OUTLINE);
            g.fill(lx + 1, 2, lx + 6, 7, lc[i]);
            g.drawString(this.font, ln[i], lx + 10, 0, TEXT, false);
            lx += 18 + this.font.width(ln[i]);
        }
        g.pose().popPose();
        // Detalle del objeto elegido.
        this.details(g, mx, my);
        super.render(g, mx, my, pt);
        // Tooltips arriba de todo.
        if (hovered != null) {
            g.renderTooltip(this.font, new ItemStack(hovered), mx, my);
        } else if (this.hoverStack != null) {
            g.renderTooltip(this.font, this.hoverStack, mx, my);
        } else {
            for (int i = first; i < last; ++i) {
                int x = this.tabX(i);
                if (mx >= x && mx < x + 26 && my >= py - 28 && my < py) {
                    g.renderTooltip(this.font, Component.literal(TABS.get(i).name), mx, my);
                }
            }
        }
        this.hoverStack = null;
    }

    ItemStack hoverStack;

    void details(GuiGraphics g, int mx, int my) {
        int x0 = this.x0;
        int dy = this.dy;
        PoliciaAnalizadorScreen.bevel(g, x0, dy, x0 + PW, dy + DH, PANEL, P_LIGHT, P_DARK);
        int bx = x0 + PW - 64;
        if (this.selected.isEmpty()) {
            List<FormattedCharSequence> ls = this.font.split(FormattedText.of("Elegi un objeto para ver su receta o la mision para aprenderlo. Sostener un objeto 2 s en la mano secundaria lo analiza al instante."), PW - 16);
            for (int i = 0; i < ls.size() && i < 5; ++i) {
                g.drawString(this.font, ls.get(i), x0 + 8, dy + 8 + i * 10, TEXT, false);
            }
            return;
        }
        Item it = PoliciaAnalizadorScreen.item(this.selected);
        ItemStack big = new ItemStack(it);
        PoliciaAnalizadorScreen.slot(g, x0 + 7, dy + 6);
        g.renderItem(big, x0 + 8, dy + 7);
        g.drawString(this.font, this.font.plainSubstrByWidth(big.getHoverName().getString(), bx - x0 - 32), x0 + 29, dy + 7, TEXT, false);
        int st = this.state(this.selected);
        if (st == 0 || st == 1) {
            String[] q = PoliciaAnalizador.questFor(this.selected);
            int type = Integer.parseInt(q[0]);
            int need = PoliciaAnalizador.QUEST_NEED[type];
            g.drawString(this.font, "Sin analizar", x0 + 29, dy + 17, 0xFF6B4A12, false);
            List<FormattedCharSequence> ls = this.font.split(FormattedText.of(PoliciaAnalizador.QUEST_NAMES[type] + ": " + PoliciaAnalizadorScreen.questText(type, q[1], need)), bx - x0 - 12);
            for (int i = 0; i < ls.size() && i < 3; ++i) {
                g.drawString(this.font, ls.get(i), x0 + 8, dy + 29 + i * 10, 0xFF1F6B2E, false);
            }
            boolean otherQuest = LAST != null && !LAST.questItem.isEmpty() && st != 1;
            if (st == 1) {
                int pw = bx - x0 - 16;
                float f = LAST.questNeed <= 0 ? 0.0f : (float) LAST.questProg / (float) LAST.questNeed;
                g.fill(x0 + 8, dy + 60, x0 + 8 + pw, dy + 66, OUTLINE);
                g.fill(x0 + 9, dy + 61, x0 + 9 + (int) ((float) (pw - 2) * f), dy + 65, C_QUEST);
                this.button(g, bx, dy + 6, 58, "Cancelar", true, 5, mx, my);
                g.drawString(this.font, PoliciaAnalizadorScreen.progText(type, LAST.questProg, LAST.questNeed), bx + 2, dy + 26, TEXT, false);
            } else {
                this.button(g, bx, dy + 6, 58, "Pedir mision", !otherQuest, 0, mx, my);
                if (otherQuest) {
                    g.drawString(this.font, "Ya tenes otra", bx + 2, dy + 24, 0xFF9A2A10, false);
                    this.button(g, bx, dy + 36, 58, "Cancelarla", true, 5, mx, my);
                }
            }
            return;
        }
        Map<String, List<String>> ingMap = new HashMap<String, List<String>>();
        Map<String, Boolean> confMap = new HashMap<String, Boolean>();
        PoliciaAnalizadorScreen.parse(LAST.recipeBlob.toString(), ingMap, confMap);
        List<String> ings = ingMap.getOrDefault(this.selected, List.of());
        boolean conf = st == 3;
        g.drawString(this.font, conf ? "Receta confirmada" : "Receta sin confirmar", x0 + 29, dy + 17, conf ? 0xFF8A6A00 : 0xFFA4510E, false);
        boolean all = !ings.isEmpty();
        int ix = x0 + 8;
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
            PoliciaAnalizadorScreen.slot(g, ix - 1, dy + 32);
            if (have < n) {
                g.fill(ix, dy + 33, ix + 16, dy + 49, 0x55FF2020);
            }
            g.renderItem(is, ix, dy + 33);
            g.renderItemDecorations(this.font, is, ix, dy + 33);
            String hv = Math.min(have, 99) + "/" + n;
            g.pose().pushPose();
            g.pose().translate((float) ix, (float) (dy + 53), 0.0f);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            g.drawString(this.font, hv, 0, 0, have >= n ? 0xFF1F6B2E : 0xFF9A2A10, false);
            g.pose().popPose();
            if (mx >= ix && mx < ix + 16 && my >= dy + 33 && my < dy + 49) {
                this.hoverStack = is;
            }
            ix += 22;
        }
        this.button(g, bx, dy + 6, 58, "Craftear", all, 3, mx, my);
        if (!conf) {
            this.button(g, bx, dy + 24, 58, "Otra receta", true, 1, mx, my);
            this.button(g, bx, dy + 42, 58, "Confirmar", true, 2, mx, my);
        } else {
            g.drawString(this.font, "Fija para", bx + 4, dy + 28, TEXT, false);
            g.drawString(this.font, "siempre", bx + 4, dy + 38, TEXT, false);
        }
    }

    void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
    }

    void selectTab(int i) {
        if (i == this.tab || i < 0 || i >= TABS.size()) {
            return;
        }
        this.tab = i;
        lastTab = i;
        this.scroll = 0;
        this.refresh();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) {
            return super.mouseClicked(mx, my, btn);
        }
        for (int[] b : this.btns) {
            if (mx < b[0] || mx >= b[2] || my < b[1] || my >= b[3]) continue;
            this.click();
            if (b[4] == 100) {
                this.page = Math.max(0, this.page - 1);
            } else if (b[4] == 101) {
                this.page = Math.min(this.pages() - 1, this.page + 1);
            } else {
                PoliciaAnalizadorScreen.act(b[4], b[4] == 5 ? "" : this.selected);
            }
            return true;
        }
        // Pestanas.
        int first = this.page * TABS_PER_PAGE;
        int last = Math.min(TABS.size(), first + TABS_PER_PAGE);
        for (int i = first; i < last; ++i) {
            int x = this.tabX(i);
            if (mx >= x && mx < x + 26 && my >= this.py - 28 && my < this.py) {
                this.click();
                this.selectTab(i);
                return true;
            }
        }
        // Grilla.
        int gx = this.x0 + 9;
        int gy = this.py + 18;
        if (mx >= gx - 1 && mx < gx + COLS * 18 - 1 && my >= gy - 1 && my < gy + ROWS * 18 - 1) {
            int c = (int) ((mx - gx + 1) / 18.0);
            int r = (int) ((my - gy + 1) / 18.0);
            int idx = (this.scroll + r) * COLS + c;
            if (idx >= 0 && idx < this.shown.size()) {
                this.selected = PoliciaAnalizadorScreen.id(this.shown.get(idx));
                return true;
            }
        }
        // Barra de desplazamiento.
        int bx = this.x0 + 175;
        if (mx >= bx && mx < bx + 14 && my >= gy && my < gy + 89) {
            this.dragging = true;
            this.dragTo(my);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    void dragTo(double my) {
        int gy = this.py + 18;
        float f = Mth.clamp((float) (my - gy - 7.5) / 74.0f, 0.0f, 1.0f);
        this.scroll = Math.round(f * (float) this.maxScroll());
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dyy) {
        if (this.dragging) {
            this.dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dyy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        this.dragging = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(delta), 0, this.maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // Con el buscador abierto, las letras van al buscador (salvo ESC).
        if (this.search.visible && this.search.isFocused() && key != 256) {
            return this.search.keyPressed(key, scan, mods) || this.search.canConsumeInput() || super.keyPressed(key, scan, mods);
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
