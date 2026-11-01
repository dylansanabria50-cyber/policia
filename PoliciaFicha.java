package com.example.policia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;

// Etapa 4: ficha de personaje (tecla I) y barra de nivel en el HUD.
// Lee la copia de datos RPG que manda el servidor (PoliciaRPG.CLIENTE).
public class PoliciaFicha extends Screen {
    static final int W = 300;
    static final int H = 252;
    static final int[] COLOR = {0xFFE0563B, 0xFF4CC274, 0xFF47A8E5, 0xFFE7B84A, 0xFFA77BE8};
    static final String[] EFECTO = {
            "+2 % de daño cuerpo a cuerpo por punto",
            "+½ corazón de vida máxima por punto",
            "+0,5 % de velocidad por punto",
            "1 % de golpe crítico (x1,5) por punto",
            "+2 % de XP ganada por punto"};
    final Button[] mas = new Button[5];
    static final String[] CASILLEROS = {"anillo", "anillo2", "amuleto", "capa", "cinturon"};
    static final String[] ICONO = {"○", "○", "◊", "▲", "═"};
    int x0;
    int y0;

    public PoliciaFicha() {
        super(Component.literal("Ficha de personaje"));
    }

    static CompoundTag d() {
        CompoundTag d = PoliciaRPG.CLIENTE;
        return d == null ? new CompoundTag() : d;
    }

    @Override
    protected void init() {
        this.x0 = (this.width - W) / 2;
        this.y0 = Math.max(2, (this.height - H) / 2);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            this.mas[i] = this.addRenderableWidget(Button.builder(Component.literal("+"), b -> PoliciaRed.CH.sendToServer(new PoliciaRed.SubirAtributo(idx)))
                    .bounds(this.x0 + W - 30, this.y0 + 74 + i * 24, 18, 16).build());
        }
        int bw = 66;
        int by = this.y0 + H - 26;
        this.addRenderableWidget(Button.builder(Component.literal("Habilidades"), b -> Minecraft.getInstance().setScreen(new PoliciaHabScreen()))
                .bounds(this.x0 + 10, by, bw, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Misiones"), b -> Minecraft.getInstance().setScreen(new PoliciaMisionesCliente.Diario()))
                .bounds(this.x0 + 10 + (bw + 4), by, bw, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Logros"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setScreen(new AdvancementsScreen(mc.player.connection.getAdvancements()));
            }
        }).bounds(this.x0 + 10 + 2 * (bw + 4), by, bw, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.x0 + 10 + 3 * (bw + 4), by, bw, 18).build());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        for (int i = 0; i < CASILLEROS.length; i++) {
            int cx = this.x0 + W - 112 + i * 20;
            int cy = this.y0 + 56;
            if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) {
                if (d().contains(CASILLEROS[i])) {
                    PoliciaRed.CH.sendToServer(new PoliciaRed.QuitarCasillero(CASILLEROS[i]));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public void tick() {
        CompoundTag d = d();
        int pts = d.getInt("puntos");
        for (int i = 0; i < 5; i++) {
            this.mas[i].active = pts > 0 && d.getInt(PoliciaRPG.ATRIBUTOS[i]) < PoliciaRPG.MAX_ATRIBUTO;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (PoliciaClient.K_FICHA.matches(key, scan)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    static String oficio(String o) {
        if (o == null || o.isEmpty()) {
            return "Sin oficio";
        }
        return switch (o) {
            case "policia" -> "Policía";
            case "albanil" -> "Albañil";
            case "ladron" -> "Ladrón";
            case "herrero" -> "Herrero";
            case "medico" -> "Médico";
            case "mercader" -> "Mercader";
            default -> Character.toUpperCase(o.charAt(0)) + o.substring(1);
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        CompoundTag d = d();
        int x = this.x0;
        int y = this.y0;
        // marco de pergamino oscuro
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF6B4B22);
        g.fill(x, y, x + W, y + H, 0xF0201A14);
        g.fill(x, y, x + W, y + 22, 0xFF3A2B18);
        g.drawCenteredString(this.font, "§6§lFICHA DE PERSONAJE", x + W / 2, y + 7, 0xFFFFFF);
        Minecraft mc = Minecraft.getInstance();
        String nombre = mc.player != null ? mc.player.getName().getString() : "";
        int nivel = Math.max(1, d.getInt("nivel"));
        g.drawString(this.font, "§f" + nombre + " §7· " + oficio(d.getString("oficio")), x + 12, y + 28, 0xFFFFFF);
        g.drawString(this.font, "§6Nivel " + nivel, x + W - 12 - this.font.width("Nivel " + nivel), y + 28, 0xFFFFFF);
        // barra de XP
        int xp = d.getInt("xp");
        int need = nivel >= PoliciaRPG.NIVEL_MAX ? 1 : PoliciaRPG.xpParaSubir(nivel);
        float k = nivel >= PoliciaRPG.NIVEL_MAX ? 1.0f : Math.min(1.0f, xp / (float) need);
        int bx = x + 12;
        int bw = W - 24;
        g.fill(bx, y + 42, bx + bw, y + 50, 0xFF0E0B08);
        g.fillGradient(bx + 1, y + 43, bx + 1 + (int) ((bw - 2) * k), y + 49, 0xFFFFD45A, 0xFFE08A1C);
        String xs = nivel >= PoliciaRPG.NIVEL_MAX ? "Nivel máximo" : xp + " / " + need + " XP";
        g.drawCenteredString(this.font, xs, x + W / 2, y + 52, 0xC8B89A);
        int pts = d.getInt("puntos");
        g.drawString(this.font, pts > 0 ? "§ePuntos libres: " + pts : "§7Puntos libres: 0", x + 12, y + 62, 0xFFFFFF);
        // casilleros: dos anillos, amuleto, capa y cinturon (clic = sacar)
        net.minecraft.world.item.ItemStack tip = null;
        String tipNombre = null;
        for (int i = 0; i < CASILLEROS.length; i++) {
            int cx = x + W - 112 + i * 20;
            int cy = y + 56;
            boolean sobre = mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18;
            g.fill(cx, cy, cx + 18, cy + 18, sobre ? 0xFFC89A48 : 0xFF6B4B22);
            g.fill(cx + 1, cy + 1, cx + 17, cy + 17, 0xFF0E0B08);
            if (d.contains(CASILLEROS[i])) {
                net.minecraft.world.item.ItemStack it = net.minecraft.world.item.ItemStack.of(d.getCompound(CASILLEROS[i]));
                g.renderItem(it, cx + 1, cy + 1);
                if (sobre) {
                    tip = it;
                }
            } else {
                g.drawCenteredString(this.font, "§8" + ICONO[i], cx + 9, cy + 5, 0xFFFFFF);
                if (sobre) {
                    tipNombre = PoliciaBotin.nombreCasillero(CASILLEROS[i]) + " §8(vacío)";
                }
            }
        }
        // atributos
        for (int i = 0; i < 5; i++) {
            int ry = y + 74 + i * 24;
            int v = d.getInt(PoliciaRPG.ATRIBUTOS[i]);
            boolean hover = mx >= x + 8 && mx <= x + W - 8 && my >= ry - 2 && my <= ry + 20;
            if (hover) {
                g.fill(x + 8, ry - 2, x + W - 8, ry + 20, 0x22FFFFFF);
            }
            g.fill(x + 12, ry + 1, x + 16, ry + 15, COLOR[i]);
            g.drawString(this.font, PoliciaRPG.NOMBRES[i], x + 22, ry + 1, 0xFFFFFF);
            g.drawString(this.font, "§8" + EFECTO[i], x + 22, ry + 11, 0xFFFFFF);
            int px = x + 150;
            int pw = 100;
            g.fill(px, ry + 3, px + pw, ry + 9, 0xFF0E0B08);
            g.fill(px + 1, ry + 4, px + 1 + (int) ((pw - 2) * (v / (float) PoliciaRPG.MAX_ATRIBUTO)), ry + 8, COLOR[i]);
            g.drawString(this.font, String.valueOf(v), px + pw + 6 - 18, ry + 11, 0xFFFFFF);
        }
        // estadisticas
        int fu = d.getInt("fuerza") - PoliciaRPG.BASE_ATRIBUTO;
        int vi = d.getInt("vitalidad") - PoliciaRPG.BASE_ATRIBUTO;
        int ag = d.getInt("agilidad") - PoliciaRPG.BASE_ATRIBUTO;
        int de = d.getInt("destreza");
        int in = d.getInt("ingenio") - PoliciaRPG.BASE_ATRIBUTO;
        CompoundTag c = d.getCompound("cont");
        String st = "§7Vida §f+" + vi * 0.5f + "❤  §7Daño §f+" + fu * 2 + "%  §7Vel. §f+" + ag * 0.5f + "%  §7Crít. §f" + de + "%  §7XP §f+" + Math.max(0, in) * 2 + "%";
        g.drawCenteredString(this.font, st, x + W / 2, y + H - 52, 0xFFFFFF);
        g.drawCenteredString(this.font, "§8Habilidades usadas: " + c.getInt("habilidades") + " · Enemigos: " + c.getInt("muertes") + " · Menas: " + c.getInt("menas"), x + W / 2, y + H - 40, 0xFFFFFF);
        super.render(g, mx, my, pt);
        if (tip != null) {
            java.util.List<Component> l = new java.util.ArrayList<Component>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), tip));
            l.add(Component.literal("§eClic para sacártelo"));
            g.renderComponentTooltip(this.font, l, mx, my);
        } else if (tipNombre != null) {
            g.renderTooltip(this.font, Component.literal(tipNombre), mx, my);
        }
    }

    // ---------------- HUD: nivel y barra de XP abajo a la izquierda ----------------
    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || !PoliciaConfig.hudNivel()) {
            return;
        }
        CompoundTag d = d();
        if (!d.contains("nivel")) {
            return;
        }
        int nivel = d.getInt("nivel");
        int need = nivel >= PoliciaRPG.NIVEL_MAX ? 1 : PoliciaRPG.xpParaSubir(nivel);
        float k = nivel >= PoliciaRPG.NIVEL_MAX ? 1.0f : Math.min(1.0f, d.getInt("xp") / (float) need);
        int x = 6;
        int y = sh - 20;
        String t = "Nv " + nivel + (d.getInt("puntos") > 0 ? " §e+" : "");
        g.drawString(mc.font, "§6" + t, x, y - 10, 0xFFFFFF);
        g.fill(x, y, x + 70, y + 5, 0xAA000000);
        g.fill(x + 1, y + 1, x + 1 + (int) (68 * k), y + 4, 0xFFF0B030);
    }
}
