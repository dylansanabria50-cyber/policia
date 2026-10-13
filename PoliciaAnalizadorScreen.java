package com.example.policia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

// Ventana del Analizador del Forjador: a la izquierda todos los items del
// juego (incluidos los de otros mods), a la derecha los que ya se
// analizaron. Blanco = sin analizar, verde = mision en curso, naranja =
// analizado pero con receta sin confirmar, dorado = receta confirmada.
public class PoliciaAnalizadorScreen extends Screen {
    static PoliciaAnalizador.State LAST;
    static final List<Item> ALL = new ArrayList<Item>();
    int px;
    int py;
    int scrollL;
    int scrollR;
    String selected = "";
    float open = 0.0f;

    public PoliciaAnalizadorScreen() {
        super(Component.literal("Analizador del Forjador"));
        if (ALL.isEmpty()) {
            ALL.addAll(ForgeRegistries.ITEMS.getValues());
        }
    }

    static void onState(PoliciaAnalizador.State m) {
        LAST = m;
    }

    static String id(Item it) {
        return BuiltInRegistries.ITEM.getKey(it).toString();
    }

    static Map<String, int[]> parseCounts(String blob, Map<String, List<String>> outItems, Map<String, Boolean> outConf) {
        Map<String, int[]> dummy = new HashMap<String, int[]>();
        if (blob.isEmpty()) {
            return dummy;
        }
        for (String entry : blob.split(";")) {
            String[] parts = entry.split(",");
            String[] head = parts[0].split(":");
            String item = head[0];
            outConf.put(item, head.length > 1 && head[1].equals("1"));
            ArrayList<String> ings = new ArrayList<String>();
            for (int i = 1; i < parts.length; ++i) {
                ings.add(parts[i]);
            }
            outItems.put(item, ings);
        }
        return dummy;
    }

    @Override
    protected void init() {
        this.px = (this.width - 520) / 2;
        this.py = (this.height - 360) / 2;
        this.addRenderableWidget((GuiEventListener) Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.px + 520 - 66, this.py + 360 + 4, 60, 20).build());
        PoliciaMod.NET.sendToServer((Object) new PoliciaAnalizador.Act(4, ""));
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

    void grid(GuiGraphics g, int x0, int y0, int x1, int y1, List<Item> items, int scroll, int mx, int my, boolean leftSide) {
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
                if (leftSide && st == 0) continue;
                if (!leftSide && st != 0) continue;
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
                try {
                    out.add(BuiltInRegistries.ITEM.get(new ResourceLocation(s)));
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        float s = 0.85f + 0.15f * this.open;
        g.pose().pushPose();
        g.pose().translate((float) this.width / 2.0f, (float) this.height / 2.0f, 0.0f);
        g.pose().scale(s, s, 1.0f);
        g.pose().translate((float) (-this.width) / 2.0f, (float) (-this.height) / 2.0f, 0.0f);
        int lx0 = this.px;
        int lx1 = this.px + 250;
        int rx0 = this.px + 260;
        int rx1 = this.px + 520;
        int gy1 = this.py + 220;
        g.drawString(this.font, "Todos los items", lx0, this.py - 10, -1, true);
        this.grid(g, lx0, this.py, lx1, gy1, ALL, this.scrollL, mx, my, true);
        g.drawString(this.font, "Analizados", rx0, this.py - 10, -1, true);
        this.grid(g, rx0, this.py, rx1, gy1, this.known(), this.scrollR, mx, my, false);
        int dy0 = gy1 + 10;
        int dy1 = this.py + 360;
        PoliciaAnalizadorScreen.panel(g, this.px, dy0, this.px + 520, dy1);
        if (!this.selected.isEmpty()) {
            Item it;
            try {
                it = BuiltInRegistries.ITEM.get(new ResourceLocation(this.selected));
            } catch (Exception ex) {
                it = null;
            }
            String name = it == null ? this.selected : new ItemStack(it).getHoverName().getString();
            int st = this.state(this.selected);
            g.drawString(this.font, name, this.px + 6, dy0 + 4, -1, true);
            if (LAST != null && this.selected.equals(LAST.questItem)) {
                g.drawString(this.font, "Mision: " + PoliciaAnalizador.QUEST_NAMES[LAST.questType] + " " + LAST.questProg + "/" + LAST.questNeed, this.px + 6, dy0 + 16, -10440449, false);
            } else if (st == 0) {
                g.drawString(this.font, "Sin analizar. Sostenelo 2 s en la mano secundaria, o pedi una mision.", this.px + 6, dy0 + 16, -6642512, false);
            } else {
                Map<String, List<String>> ingMap = new HashMap<String, List<String>>();
                Map<String, Boolean> confMap = new HashMap<String, Boolean>();
                if (LAST != null) {
                    PoliciaAnalizadorScreen.parseCounts(LAST.recipeBlob.toString(), ingMap, confMap);
                }
                List<String> ings = ingMap.getOrDefault(this.selected, List.of());
                int ix = this.px + 6;
                for (String ing : ings) {
                    String[] p = ing.split("x");
                    try {
                        Item ii = BuiltInRegistries.ITEM.get(new ResourceLocation(p[0]));
                        g.renderItem(new ItemStack(ii, Integer.parseInt(p[1])), ix, dy0 + 16);
                        g.renderItemDecorations(this.font, new ItemStack(ii, Integer.parseInt(p[1])), ix, dy0 + 16);
                    } catch (Exception ignored) {
                    }
                    ix += 20;
                }
                g.drawString(this.font, st == 2 ? "Receta sin confirmar" : "Receta confirmada", this.px + 6, dy0 + 40, st == 2 ? -19712 : -181, false);
            }
        } else {
            g.drawString(this.font, "Hace clic en un item para ver sus detalles.", this.px + 6, dy0 + 4, -6642512, false);
        }
        g.pose().popPose();
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) {
            return super.mouseClicked(mx, my, btn);
        }
        int lx0 = this.px;
        int lx1 = this.px + 250;
        int rx0 = this.px + 260;
        int rx1 = this.px + 520;
        int gy1 = this.py + 220;
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
        int dy0 = gy1 + 10;
        if (!this.selected.isEmpty() && mx >= this.px && mx <= this.px + 520 && my >= dy0 && my <= this.py + 360) {
            int st = this.state(this.selected);
            boolean questing = LAST != null && this.selected.equals(LAST.questItem);
            if (st == 0 && !questing && mx >= this.px + 360 && my >= dy0 + 60) {
                PoliciaAnalizadorScreen.act(0, this.selected);
                return true;
            }
            if (st == 2 && mx >= this.px + 360 && my < dy0 + 60) {
                PoliciaAnalizadorScreen.act(my < dy0 + 70 ? 1 : 2, this.selected);
                return true;
            }
            if (st == 3 && mx >= this.px + 360) {
                PoliciaAnalizadorScreen.act(3, this.selected);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < (double) (this.px + 255)) {
            this.scrollL = Math.max(0, this.scrollL - (int) delta);
        } else {
            this.scrollR = Math.max(0, this.scrollR - (int) delta);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
