package com.example.policia;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;

// Etapa 6 (cliente): arbol de habilidades nuevas del oficio. Cada tarjeta
// muestra nombre, descripcion, nivel pedido y costo; se desbloquea y se
// equipa con un clic. La equipada se usa con Z.
public class PoliciaHabScreen extends Screen {
    static final int W = 330;
    static final int H = 250;
    int x0;
    int y0;
    int pagina;

    public PoliciaHabScreen() {
        super(Component.literal("Habilidades del oficio"));
    }

    static CompoundTag rpg() {
        CompoundTag d = PoliciaRPG.CLIENTE;
        return d == null ? new CompoundTag() : d;
    }

    static CompoundTag hab() {
        return rpg().getCompound("hab");
    }

    List<PoliciaHab.Def> lista() {
        return PoliciaHab.deOficio(rpg().getString("oficio"));
    }

    @Override
    protected void init() {
        this.x0 = (this.width - W) / 2;
        this.y0 = Math.max(2, (this.height - H) / 2);
        this.rebuildWidgets2();
    }

    void rebuildWidgets2() {
        this.clearWidgets();
        List<PoliciaHab.Def> l = this.lista();
        int desde = this.pagina * 4;
        for (int i = desde; i < Math.min(l.size(), desde + 4); i++) {
            PoliciaHab.Def d = l.get(i);
            int fila = i - desde;
            int bx = this.x0 + W - 84;
            int by = this.y0 + 36 + fila * 44 + 22;
            this.addRenderableWidget(Button.builder(Component.literal("..."), b -> {
                boolean tiene = PoliciaHab.tiene(hab(), d.id);
                PoliciaRed.CH.sendToServer(new PoliciaRed.HabAccion(tiene ? 1 : 0, d.id));
            }).bounds(bx, by, 72, 16).build());
        }
        if (l.size() > 4) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                this.pagina = Math.max(0, this.pagina - 1);
                this.rebuildWidgets2();
            }).bounds(this.x0 + 110, this.y0 + H - 26, 20, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                this.pagina = Math.min((l.size() - 1) / 4, this.pagina + 1);
                this.rebuildWidgets2();
            }).bounds(this.x0 + 200, this.y0 + H - 26, 20, 18).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Ficha"), b -> Minecraft.getInstance().setScreen(new PoliciaFicha()))
                .bounds(this.x0 + 12, this.y0 + H - 26, 70, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.x0 + W - 82, this.y0 + H - 26, 70, 18).build());
    }

    @Override
    public void tick() {
        List<PoliciaHab.Def> l = this.lista();
        CompoundTag h = hab();
        int nivel = Math.max(1, rpg().getInt("nivel"));
        int libres = PoliciaHab.puntosTotales(nivel) - PoliciaHab.gastados(h);
        int desde = this.pagina * 4;
        int k = 0;
        for (var w : this.children()) {
            if (!(w instanceof Button b) || k >= Math.min(4, l.size() - desde)) {
                continue;
            }
            PoliciaHab.Def d = l.get(desde + k);
            k++;
            boolean tiene = PoliciaHab.tiene(h, d.id);
            boolean equipada = d.id.equals(h.getString("sel"));
            if (tiene) {
                b.setMessage(Component.literal(equipada ? "§aEquipada" : "Equipar"));
                b.active = !equipada;
            } else {
                b.setMessage(Component.literal("Desbloquear"));
                b.active = nivel >= d.nivel && libres >= d.costo;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static String nombreOficio(String o) {
        return PoliciaFicha.oficio(o);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int x = this.x0;
        int y = this.y0;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF2E4A6B);
        g.fill(x, y, x + W, y + H, 0xF0141A22);
        g.fill(x, y, x + W, y + 22, 0xFF1C2C40);
        CompoundTag d = rpg();
        String oficio = d.getString("oficio");
        g.drawCenteredString(this.font, "§b§lHABILIDADES · " + nombreOficio(oficio).toUpperCase(), x + W / 2, y + 7, 0xFFFFFF);
        int nivel = Math.max(1, d.getInt("nivel"));
        CompoundTag h = hab();
        int libres = PoliciaHab.puntosTotales(nivel) - PoliciaHab.gastados(h);
        g.drawString(this.font, "§7Puntos de habilidad: " + (libres > 0 ? "§e" : "§f") + libres + " §8(1 + nivel/3)", x + 12, y + 26, 0xFFFFFF);
        List<PoliciaHab.Def> l = this.lista();
        if (l.isEmpty()) {
            g.drawCenteredString(this.font, "§7Tu oficio todavía no tiene habilidades nuevas.", x + W / 2, y + 90, 0xFFFFFF);
            g.drawCenteredString(this.font, "§8Elegí un oficio para ver su árbol.", x + W / 2, y + 102, 0xFFFFFF);
        }
        long ahora = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        int desde = this.pagina * 4;
        for (int i = desde; i < Math.min(l.size(), desde + 4); i++) {
            PoliciaHab.Def def = l.get(i);
            int ry = y + 36 + (i - desde) * 44;
            boolean tiene = PoliciaHab.tiene(h, def.id);
            boolean equipada = def.id.equals(h.getString("sel"));
            int borde = equipada ? 0xFF4CE070 : tiene ? 0xFF4A8AC8 : nivel >= def.nivel ? 0xFF8A7A50 : 0xFF3A3A3A;
            g.fill(x + 8, ry, x + W - 8, ry + 40, borde);
            g.fill(x + 9, ry + 1, x + W - 9, ry + 39, 0xFF1A222C);
            g.drawString(this.font, (tiene ? "§f" : "§7") + def.nombre + (def.costo > 1 ? " §6★" : ""), x + 14, ry + 4, 0xFFFFFF);
            String info = "Nv " + def.nivel + " · " + def.costo + (def.costo == 1 ? " punto" : " puntos") + " · " + def.cd / 20 + " s";
            long hasta = h.getCompound("cd").getLong(def.id);
            if (tiene && hasta > ahora) {
                info = "§cEnfriamiento: " + (hasta - ahora + 19) / 20 + " s";
            }
            g.drawString(this.font, "§8" + info, x + W - 88 - this.font.width(info) + 2, ry + 4, 0xFFFFFF);
            List<FormattedCharSequence> ls = this.font.split(Component.literal(def.desc), W - 110);
            for (int k = 0; k < Math.min(2, ls.size()); k++) {
                g.drawString(this.font, ls.get(k), x + 14, ry + 15 + k * 10, 0xFF9AA8B8, false);
            }
        }
        if (l.size() > 4) {
            g.drawCenteredString(this.font, "§7" + (this.pagina + 1) + " / " + ((l.size() + 3) / 4), x + 165, y + H - 21, 0xFFFFFF);
        }
        super.render(g, mx, my, pt);
        // Vista previa: al pasar el mouse por una tarjeta, tu personaje hace
        // la habilidad en un cuadrito al costado.
        PoliciaHab.Def mira = null;
        for (int i = desde; i < Math.min(l.size(), desde + 4); i++) {
            int ry = y + 36 + (i - desde) * 44;
            if (mx >= x + 8 && mx <= x + W - 8 && my >= ry && my <= ry + 40) {
                mira = l.get(i);
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (mira != null && mira.anim != null && !mira.anim.isEmpty() && mc.player != null) {
            int bw = 92;
            int bh = 128;
            int bx = x + W + 6;
            if (bx + bw > this.width) {
                bx = x - bw - 6;
            }
            if (bx < 0) {
                bx = Math.min(this.width - bw - 2, mx + 14);
            }
            int by = Math.max(2, Math.min(this.height - bh - 2, my - bh / 2));
            g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xFF2E4A6B);
            g.fill(bx, by, bx + bw, by + bh, 0xF0101820);
            g.drawCenteredString(this.font, "§b▶ Vista previa", bx + bw / 2, by + 4, 0xFFFFFF);
            PoliciaAnimCliente.preview(mira.anim);
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g, bx + bw / 2, by + bh - 12, 42, (float) (bx + bw / 2 - mx) * 0.2f, (float) (by + 40 - my) * 0.2f, mc.player);
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            PoliciaAnimCliente.guiFx(g, bx + bw / 2, by + bh - 12, 42.0f);
            g.pose().popPose();
        } else {
            PoliciaAnimCliente.preview(null);
        }
    }

    // ---------------- HUD: la habilidad equipada (Z) ----------------
    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || !PoliciaConfig.hudNivel()) {
            return;
        }
        CompoundTag h = hab();
        PoliciaHab.Def d = PoliciaHab.buscar(h.getString("sel"));
        if (d == null || !PoliciaHab.tiene(h, d.id) || !d.oficio.equals(rpg().getString("oficio"))) {
            return;
        }
        long ahora = mc.level != null ? mc.level.getGameTime() : 0;
        long hasta = h.getCompound("cd").getLong(d.id);
        String s = hasta > ahora ? "§8Z §7" + d.nombre + " §c" + (hasta - ahora + 19) / 20 + "s" : "§eZ §f" + d.nombre;
        g.drawString(mc.font, s, 6, sh - 42, 0xFFFFFF);
    }
}
