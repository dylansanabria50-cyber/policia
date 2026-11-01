package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.gui.overlay.ForgeGui;

// Charla de la historia: el NPC a la izquierda, su texto que se escribe
// letra por letra (clic o espacio para seguir) y, al final, las opciones.
// Tambien dibuja el objetivo actual de la historia arriba a la derecha.
public class PoliciaHistoriaScreen extends Screen {
    final int ent;
    final String nombre;
    final String[] lineas;
    final String[] opciones;
    int linea;
    float letras;
    int ultimoSonido;
    boolean botones;

    PoliciaHistoriaScreen(int ent, String nombre, String texto, String opciones) {
        super(Component.literal(nombre));
        this.ent = ent;
        this.nombre = nombre;
        this.lineas = texto.split("\n");
        this.opciones = opciones.isEmpty() ? new String[0] : opciones.split("\n");
    }

    static void abrir(int ent, String nombre, String texto, String opciones) {
        Minecraft.getInstance().setScreen(new PoliciaHistoriaScreen(ent, nombre, texto, opciones));
    }

    int caja() {
        return Math.min(400, this.width - 20);
    }

    boolean lineaLista() {
        return this.letras >= this.lineas[this.linea].length();
    }

    boolean ultima() {
        return this.linea >= this.lineas.length - 1;
    }

    @Override
    public void tick() {
        if (!this.lineaLista()) {
            this.letras += 1.8f;
            int n = (int) this.letras;
            if (n / 4 != this.ultimoSonido) {
                this.ultimoSonido = n / 4;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2f, 0.2f));
            }
        } else if (this.ultima() && !this.botones) {
            this.mostrarOpciones();
        }
    }

    void avanzar() {
        if (!this.lineaLista()) {
            this.letras = this.lineas[this.linea].length();
        } else if (!this.ultima()) {
            this.linea++;
            this.letras = 0;
        }
    }

    void mostrarOpciones() {
        this.botones = true;
        int w = this.caja();
        int x = (this.width - w) / 2;
        int y = this.height - 70 - this.opciones.length * 22;
        for (int i = 0; i < this.opciones.length; i++) {
            final int op = i;
            this.addRenderableWidget(Button.builder(Component.literal("§e▸ §f" + this.opciones[i]), b -> {
                PoliciaRed.CH.sendToServer(new PoliciaRed.HistoriaElegir(op));
                this.onClose();
            }).bounds(x + w - 220, y + i * 22, 220, 20).build());
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (super.mouseClicked(mx, my, b)) {
            return true;
        }
        this.avanzar();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int sc, int mod) {
        if (key == 32 || key == 257) {
            this.avanzar();
            return true;
        }
        return super.keyPressed(key, sc, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int w = this.caja();
        int x = (this.width - w) / 2;
        int h = 74;
        int y = this.height - h - 10;
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level != null ? mc.level.getEntity(this.ent) : null;
        if (e instanceof LivingEntity le) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 40, y - 6, 48, (float) (x + 40 - mx), (float) (y - 70 - my), le);
        }
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF8A6234);
        g.fill(x, y, x + w, y + h, 0xF0201A14);
        g.fill(x, y - 14, x + 8 + this.font.width(this.nombre) + 8, y, 0xFF8A6234);
        g.drawString(this.font, "§e" + this.nombre, x + 8, y - 11, 0xFFFFFF);
        String t = this.lineas[this.linea];
        String vis = t.substring(0, Math.min(t.length(), (int) this.letras));
        List<FormattedCharSequence> ls = this.font.split(FormattedText.of(vis), w - 20);
        for (int i = 0; i < Math.min(5, ls.size()); i++) {
            g.drawString(this.font, ls.get(i), x + 10, y + 8 + i * 11, 0xFFEFE2C0, false);
        }
        if (this.lineaLista() && !this.ultima() && (System.currentTimeMillis() / 400) % 2 == 0) {
            g.drawString(this.font, "§6▼", x + w - 14, y + h - 12, 0xFFFFFF);
        }
        super.render(g, mx, my, pt);
    }

    // ---------------- Objetivo de la historia (arriba a la derecha) ----------------
    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || !PoliciaConfig.hudDiarias()) {
            return;
        }
        CompoundTag d = PoliciaRPG.CLIENTE == null ? new CompoundTag() : PoliciaRPG.CLIENTE;
        CompoundTag h = d.getCompound("historia");
        String obj = h.getString("obj");
        int cap = h.getInt("cap");
        if (obj.isEmpty() || cap >= 6) {
            return;
        }
        String tit = cap == 0 ? "§6Historia" : "§6Cap. " + cap + " · La campana del alba";
        List<FormattedCharSequence> ls = new ArrayList<FormattedCharSequence>(mc.font.split(FormattedText.of(obj), 140));
        int x = sw - 150;
        int y = sh / 2 - 60 - ls.size() * 10;
        g.fill(x - 4, y - 4, sw - 4, y + 12 + ls.size() * 10, 0x60000000);
        g.drawString(mc.font, tit, x, y, 0xFFFFFF);
        for (int i = 0; i < ls.size(); i++) {
            g.drawString(mc.font, ls.get(i), x, y + 11 + i * 10, 0xFFD0D0D0);
        }
    }
}
