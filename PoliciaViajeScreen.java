package com.example.policia;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.gui.overlay.ForgeGui;

// Mapa de viaje en carruaje (tecla M) y reloj del dia.
// El mapa dibuja el terreno de verdad (lo que tiene cargado el cliente) con
// relieve y agua, teñido como un pergamino viejo. Encima van los caminos a
// cada parada, banderitas, tu casa, tu flecha, rosa de los vientos y escala.
// Pasando el mouse por una parada se ven distancia y costo; con clic (en el
// mapa o en la lista) se viaja.
public class PoliciaViajeScreen extends Screen {
    static final int W = 344;
    static final int H = 236;
    static final int MW = 200;
    static final int MH = 172;
    static final ResourceLocation TEX = new ResourceLocation(PoliciaMod.ID, "mapa_viaje");
    static final int PERG = 0xE4D2A8;

    int x0;
    int y0;
    int pagina;
    final List<String[]> paradas = new ArrayList<String[]>();
    final List<Button> botones = new ArrayList<Button>();
    DynamicTexture tex;
    double ox;
    double oz;
    double esc = 1.0;
    int hoverLista = -1;

    public PoliciaViajeScreen() {
        super(Component.literal("Mapa de viaje"));
    }

    static CompoundTag rpg() {
        return PoliciaRPG.CLIENTE == null ? new CompoundTag() : PoliciaRPG.CLIENTE;
    }

    @Override
    protected void init() {
        this.x0 = Math.max(2, (this.width - W) / 2);
        this.y0 = Math.max(2, (this.height - H) / 2);
        this.paradas.clear();
        CompoundTag p = rpg().getCompound("paradas");
        for (String k : p.getAllKeys()) {
            CompoundTag t = p.getCompound(k);
            this.paradas.add(new String[]{k, t.getString("n"), String.valueOf(t.getInt("x")), String.valueOf(t.getInt("z"))});
        }
        Minecraft mc = Minecraft.getInstance();
        double px = mc.player != null ? mc.player.getX() : 0;
        double pz = mc.player != null ? mc.player.getZ() : 0;
        // las paradas mas cercanas primero
        this.paradas.sort((a, b) -> Double.compare(dist(a, px, pz), dist(b, px, pz)));
        this.armarBotones();
        this.armarMapa();
    }

    static double dist(String[] s, double px, double pz) {
        double dx = Integer.parseInt(s[2]) - px;
        double dz = Integer.parseInt(s[3]) - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    void armarBotones() {
        this.clearWidgets();
        this.botones.clear();
        int lx = this.x0 + W - 130;
        int y = this.y0 + 40;
        this.addRenderableWidget(Button.builder(Component.literal("⌂ Mi casa §7(gratis)"), b -> this.ir("casa")).bounds(lx, y, 118, 16).build());
        int por = 8;
        int desde = this.pagina * por;
        for (int i = desde; i < Math.min(this.paradas.size(), desde + por); i++) {
            String[] s = this.paradas.get(i);
            Button b = Button.builder(Component.literal(corto(s[1], 13) + " §2" + this.costo(s) + "◆"), x -> this.ir(s[0])).bounds(lx, y + 20 + (i - desde) * 18, 118, 16).build();
            this.botones.add(b);
            this.addRenderableWidget(b);
        }
        if (this.paradas.size() > por) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                this.pagina = Math.max(0, this.pagina - 1);
                this.armarBotones();
            }).bounds(lx, this.y0 + H - 46, 20, 14).build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                this.pagina = Math.min((this.paradas.size() - 1) / por, this.pagina + 1);
                this.armarBotones();
            }).bounds(lx + 98, this.y0 + H - 46, 20, 14).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose()).bounds(lx + 24, this.y0 + H - 26, 70, 18).build());
    }

    static String corto(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    int costo(String[] s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 1;
        }
        return Math.max(1, (int) (dist(s, mc.player.getX(), mc.player.getZ()) / 250.0));
    }

    void ir(String clave) {
        PoliciaRed.CH.sendToServer(new PoliciaRed.ViajeIr(clave));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------- Terreno ----------------
    // Arma una textura con el terreno alrededor del jugador. Las zonas que
    // el cliente no tiene cargadas quedan como pergamino con manchitas.
    void armarMapa() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        double px = mc.player != null ? mc.player.getX() : 0;
        double pz = mc.player != null ? mc.player.getZ() : 0;
        double max = 96;
        for (String[] s : this.paradas) {
            max = Math.max(max, Math.max(Math.abs(Integer.parseInt(s[2]) - px), Math.abs(Integer.parseInt(s[3]) - pz)) + 24);
        }
        CompoundTag r = rpg();
        if (r.getBoolean("casa")) {
            max = Math.max(max, Math.max(Math.abs(r.getInt("casa_x") - px), Math.abs(r.getInt("casa_z") - pz)) + 24);
        }
        this.esc = (MH / 2.0 - 8) / max;
        this.ox = px;
        this.oz = pz;
        NativeImage img = new NativeImage(MW, MH, true);
        int[] alto = new int[MW];
        boolean[] hay = new boolean[MW];
        java.util.Random rnd = new java.util.Random(1234);
        for (int j = 0; j < MH; j++) {
            for (int i = 0; i < MW; i++) {
                int wx = (int) Math.floor(px + (i - MW / 2.0) / this.esc);
                int wz = (int) Math.floor(pz + (j - MH / 2.0) / this.esc);
                int col;
                int h = 0;
                boolean cargado = lv != null && lv.hasChunk(wx >> 4, wz >> 4);
                if (cargado) {
                    h = lv.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                    BlockPos bp = new BlockPos(wx, h - 1, wz);
                    BlockState st = lv.getBlockState(bp);
                    int prof = 0;
                    if (st.getFluidState().is(FluidTags.WATER)) {
                        BlockPos q = bp;
                        while (prof < 10 && lv.getBlockState(q).getFluidState().is(FluidTags.WATER)) {
                            q = q.below();
                            prof++;
                        }
                        col = mezclar(0x4E8FD0, 0x1C3F78, Math.min(1.0f, prof / 10.0f));
                    } else {
                        MapColor m = st.getMapColor(lv, bp);
                        if (m == MapColor.NONE) {
                            m = lv.getBlockState(bp.below()).getMapColor(lv, bp.below());
                        }
                        col = m == MapColor.NONE ? 0x7FB238 : m.col;
                        // relieve: comparado con el pixel de arriba (norte)
                        if (j > 0 && hay[i]) {
                            float k = h > alto[i] ? 1.12f : h < alto[i] ? 0.82f : 1.0f;
                            col = brillo(col, k);
                        }
                    }
                    // tono de pergamino
                    col = mezclar(col, PERG, 0.28f);
                } else {
                    float n = rnd.nextFloat();
                    col = n < 0.04f ? 0xD6C292 : n < 0.08f ? 0xEADBB8 : PERG;
                }
                alto[i] = h;
                hay[i] = cargado;
                img.setPixelRGBA(i, j, abgr(col));
            }
        }
        // borde quemado del pergamino
        for (int j = 0; j < MH; j++) {
            for (int i = 0; i < MW; i++) {
                int e = Math.min(Math.min(i, MW - 1 - i), Math.min(j, MH - 1 - j));
                if (e < 6) {
                    int c = img.getPixelRGBA(i, j);
                    int rgb = (c & 0xFF) << 16 | (c >> 8 & 0xFF) << 8 | (c >> 16 & 0xFF);
                    img.setPixelRGBA(i, j, abgr(mezclar(rgb, 0x8A6A3A, (6 - e) / 10.0f)));
                }
            }
        }
        if (this.tex != null) {
            this.tex.close();
        }
        this.tex = new DynamicTexture(img);
        mc.getTextureManager().register(TEX, this.tex);
    }

    static int abgr(int rgb) {
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }

    static int mezclar(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return r << 16 | g << 8 | bl;
    }

    static int brillo(int c, float k) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * k));
        int g = Math.min(255, (int) (((c >> 8) & 255) * k));
        int b = Math.min(255, (int) ((c & 255) * k));
        return r << 16 | g << 8 | b;
    }

    @Override
    public void removed() {
        Minecraft.getInstance().getTextureManager().release(TEX);
        this.tex = null;
        super.removed();
    }

    // Posicion en pantalla de un punto del mundo.
    int sx(double wx) {
        return this.x0 + 12 + MW / 2 + (int) Math.round((wx - this.ox) * this.esc);
    }

    int sz(double wz) {
        return this.y0 + 40 + MH / 2 + (int) Math.round((wz - this.oz) * this.esc);
    }

    int paradaBajoMouse(int mx, int my) {
        for (int i = 0; i < this.paradas.size(); i++) {
            String[] s = this.paradas.get(i);
            int x = this.sx(Integer.parseInt(s[2]));
            int z = this.sz(Integer.parseInt(s[3]));
            if (Math.abs(mx - x) <= 5 && my >= z - 9 && my <= z + 3) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int i = this.paradaBajoMouse((int) mx, (int) my);
        if (b == 0 && i >= 0) {
            this.ir(this.paradas.get(i)[0]);
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }

    // ---------------- Dibujo ----------------
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int x = this.x0;
        int y = this.y0;
        Minecraft mc = Minecraft.getInstance();
        // marco de madera con doble filete
        g.fill(x - 3, y - 3, x + W + 3, y + H + 3, 0xFF3A2614);
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF8A6234);
        g.fill(x, y, x + W, y + H, 0xFFE9DAB4);
        g.fill(x + 3, y + 3, x + W - 3, y + H - 3, 0xFFEFE2C0);
        // titulo
        g.fill(x, y, x + W, y + 22, 0xFF4A3218);
        g.fill(x, y + 22, x + W, y + 23, 0xFFC89A48);
        g.drawCenteredString(this.font, "§6§l✦ MAPA DE VIAJE ✦", x + W / 2, y + 7, 0xFFFFFF);
        for (int[] c : new int[][]{{x + 4, y + 4}, {x + W - 8, y + 4}, {x + 4, y + H - 8}, {x + W - 8, y + H - 8}}) {
            g.fill(c[0], c[1], c[0] + 4, c[1] + 4, 0xFFC89A48);
        }
        // linea con dia y estacion
        String sub = "";
        if (mc.level != null) {
            long dia = mc.level.getDayTime() / 24000L + 1;
            sub = "Comarca · día " + dia + " · " + PoliciaEstacion.NAME[PoliciaEstacionCliente.estacion()];
        }
        g.drawString(this.font, sub, x + 12, y + 28, 0xFF6A5232, false);

        // ---- mapa ----
        int mx0 = x + 12;
        int my0 = y + 40;
        g.fill(mx0 - 2, my0 - 2, mx0 + MW + 2, my0 + MH + 2, 0xFF5A4022);
        g.blit(TEX, mx0, my0, 0, 0, MW, MH, MW, MH);
        // cuadricula muy suave
        for (int i = 1; i < 5; i++) {
            g.fill(mx0 + i * MW / 5, my0, mx0 + i * MW / 5 + 1, my0 + MH, 0x14000000);
            g.fill(mx0, my0 + i * MH / 5, mx0 + MW, my0 + i * MH / 5 + 1, 0x14000000);
        }
        g.enableScissor(mx0, my0, mx0 + MW, my0 + MH);
        int cx = this.sx(this.ox);
        int cz = this.sz(this.oz);
        int hov = this.paradaBajoMouse(mx, my);
        this.hoverLista = -1;
        for (int b = 0; b < this.botones.size(); b++) {
            if (this.botones.get(b).isHoveredOrFocused()) {
                this.hoverLista = this.pagina * 8 + b;
            }
        }
        // caminos (punteados, color tierra)
        for (int i = 0; i < this.paradas.size(); i++) {
            String[] s = this.paradas.get(i);
            int tx = this.sx(Integer.parseInt(s[2]));
            int tz = this.sz(Integer.parseInt(s[3]));
            boolean act = i == hov || i == this.hoverLista;
            int pasos = Math.max(6, (int) (Math.hypot(tx - cx, tz - cz) / 3));
            for (int k = 1; k < pasos; k += 2) {
                int qx = cx + (tx - cx) * k / pasos;
                int qz = cz + (tz - cz) * k / pasos;
                g.fill(qx, qz, qx + 2, qz + 2, act ? 0xFFB02818 : 0xB07A4A22);
            }
        }
        // casa
        CompoundTag r = rpg();
        if (r.getBoolean("casa")) {
            int hx = this.sx(r.getInt("casa_x"));
            int hz = this.sz(r.getInt("casa_z"));
            g.fill(hx - 3, hz - 1, hx + 4, hz + 4, 0xFF2E6A2A);
            g.fill(hx - 4, hz - 2, hx + 5, hz - 1, 0xFF1E4A1A);
            g.fill(hx - 2, hz - 3, hx + 3, hz - 2, 0xFF1E4A1A);
            g.fill(hx - 1, hz - 4, hx + 2, hz - 3, 0xFF1E4A1A);
            g.fill(hx, hz + 1, hx + 1, hz + 4, 0xFFE9DAB4);
        }
        // paradas: banderita + cartelito
        for (int i = 0; i < this.paradas.size(); i++) {
            String[] s = this.paradas.get(i);
            int tx = this.sx(Integer.parseInt(s[2]));
            int tz = this.sz(Integer.parseInt(s[3]));
            boolean act = i == hov || i == this.hoverLista;
            if (act) {
                int pul = (int) (System.currentTimeMillis() / 120 % 4);
                g.fill(tx - 4 - pul, tz - 1 - pul, tx + 5 + pul, tz, 0x40B02818);
            }
            g.fill(tx, tz - 8, tx + 1, tz + 1, 0xFF3A2614);
            g.fill(tx + 1, tz - 8, tx + 6, tz - 5, act ? 0xFFFF4A2A : 0xFFC0301E);
            g.fill(tx + 1, tz - 5, tx + 4, tz - 4, act ? 0xFFFF4A2A : 0xFFC0301E);
            g.fill(tx - 2, tz, tx + 3, tz + 1, 0x80000000);
            String n = corto(s[1], 14);
            int w = this.font.width(n);
            int lx = tx + 7;
            int ly = tz - 9;
            if (lx + w + 4 > mx0 + MW) {
                lx = tx - w - 6;
            }
            g.fill(lx - 2, ly - 1, lx + w + 2, ly + 9, act ? 0xF0FFF4D0 : 0xD0F2E6C4);
            g.fill(lx - 2, ly + 9, lx + w + 2, ly + 10, 0x805A4022);
            g.drawString(this.font, n, lx, ly, act ? 0xFF8A1A10 : 0xFF3A2B18, false);
        }
        // vos: flecha azul hacia donde mirás
        float yaw = mc.player != null ? mc.player.getYRot() : 0;
        g.pose().pushPose();
        g.pose().translate(cx + 0.5f, cz + 0.5f, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(yaw + 180.0f));
        for (int k = 0; k < 6; k++) {
            g.fill(-k / 2 - 1, -4 + k, k / 2 + 1, -3 + k, 0xFF0E2A80);
        }
        for (int k = 0; k < 4; k++) {
            g.fill(-k / 2, -3 + k, k / 2, -2 + k, 0xFF4A8AFF);
        }
        g.pose().popPose();
        g.disableScissor();

        // rosa de los vientos (arriba a la derecha del mapa)
        int rx = mx0 + MW - 18;
        int ry = my0 + 18;
        g.fill(rx - 1, ry - 11, rx + 1, ry + 11, 0xC05A4022);
        g.fill(rx - 11, ry - 1, rx + 11, ry + 1, 0xC05A4022);
        g.fill(rx - 1, ry - 11, rx + 1, ry - 2, 0xFFC0301E);
        g.fill(rx - 2, ry - 2, rx + 2, ry + 2, 0xFF3A2614);
        g.drawCenteredString(this.font, "N", rx, ry - 21, 0xFF8A1A10);
        // escala (abajo a la izquierda del mapa)
        int[] opciones = {25, 50, 100, 200, 250, 500, 1000, 2000};
        int bloques = opciones[0];
        for (int o : opciones) {
            if (o * this.esc <= 60) {
                bloques = o;
            }
        }
        int lw = Math.max(8, (int) (bloques * this.esc));
        int bx = mx0 + 8;
        int by = my0 + MH - 10;
        g.fill(bx - 3, by - 11, bx + lw + 3, by + 5, 0xB0EFE2C0);
        g.fill(bx, by, bx + lw, by + 2, 0xFF3A2614);
        g.fill(bx, by - 3, bx + 1, by + 2, 0xFF3A2614);
        g.fill(bx + lw - 1, by - 3, bx + lw, by + 2, 0xFF3A2614);
        g.fill(bx + lw / 2, by - 2, bx + lw / 2 + 1, by + 2, 0xFF3A2614);
        g.drawString(this.font, bloques + " bloques", bx, by - 11, 0xFF3A2B18, false);

        // ---- columna derecha ----
        int lx = x + W - 130;
        g.drawString(this.font, "§lDestinos", lx, y + 28, 0xFF4A3218, false);
        if (this.paradas.isEmpty()) {
            g.drawString(this.font, "Entrá a una aldea", lx, y + 66, 0xFF6A5232, false);
            g.drawString(this.font, "para descubrir su", lx, y + 76, 0xFF6A5232, false);
            g.drawString(this.font, "parada de carruaje.", lx, y + 86, 0xFF6A5232, false);
        }
        if (this.paradas.size() > 8) {
            g.drawCenteredString(this.font, (this.pagina + 1) + " / " + ((this.paradas.size() + 7) / 8), lx + 59, y + H - 43, 0xFF4A3218);
        }
        // nota al pie, debajo del mapa (no tapa botones)
        g.drawString(this.font, "§2◆§r = esmeraldas · salida: aldea o casa", x + 12, y + H - 14, 0xFF6A5232, false);
        if (mc.player != null) {
            String pos = "X " + (int) mc.player.getX() + "  Z " + (int) mc.player.getZ();
            int pw = this.font.width(pos);
            g.fill(mx0 + MW - pw - 9, my0 + MH - 14, mx0 + MW - 3, my0 + MH - 3, 0xB0EFE2C0);
            g.drawString(this.font, pos, mx0 + MW - pw - 6, my0 + MH - 12, 0xFF3A2B18, false);
        }
        super.render(g, mx, my, pt);
        // detalle de la parada bajo el mouse
        if (hov >= 0) {
            String[] s = this.paradas.get(hov);
            int d = mc.player != null ? (int) dist(s, mc.player.getX(), mc.player.getZ()) : 0;
            List<Component> l = new ArrayList<Component>();
            l.add(Component.literal("§6" + s[1]));
            l.add(Component.literal("§7Distancia: §f" + d + " bloques"));
            l.add(Component.literal("§7Viaje: §a" + this.costo(s) + " esmeraldas"));
            l.add(Component.literal("§eClic para viajar"));
            g.renderComponentTooltip(this.font, l, mx, my);
        }
    }

    // ---------------- Reloj (arriba a la izquierda) ----------------
    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null || !PoliciaConfig.hudReloj()) {
            return;
        }
        if (mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        long t = Math.floorMod(mc.level.getDayTime() + 6000, 24000L);
        int hh = (int) (t / 1000);
        int mm = (int) ((t % 1000) * 60 / 1000);
        int f = PoliciaDiaNoche.franja(mc.level.getDayTime());
        String ic = f == 3 || f == 2 ? "§9☾" : "§e☀";
        String s = ic + " §f" + String.format("%02d:%02d", hh, mm) + " §7" + PoliciaDiaNoche.FRANJA[f];
        g.drawString(mc.font, s, 4, 4, 0xFFFFFF);
    }
}
