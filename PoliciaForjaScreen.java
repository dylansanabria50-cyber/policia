package com.example.policia;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

// Etapa 7 (cliente): minijuego de forja en tres pasos y la rareza en los
// objetos (color del nombre, estrellas en el tooltip y una marquita de
// color en el casillero).
public class PoliciaForjaScreen extends Screen {
    static final int W = 260;
    static final int H = 150;
    int fase;
    float temp;
    boolean apretado;
    int golpes;
    float tiempo;
    float puntos;
    float marca;
    final int rarezaAntes;
    long inicio;
    String aviso = "";
    float avisoT;
    boolean enviado;

    public PoliciaForjaScreen(int rarezaAntes) {
        super(Component.literal("Forja"));
        this.rarezaAntes = rarezaAntes;
        this.inicio = System.currentTimeMillis();
    }

    static void abrir(int rareza) {
        Minecraft.getInstance().setScreen(new PoliciaForjaScreen(rareza));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.fase >= 3;
    }

    void sonido(net.minecraft.sounds.SoundEvent s, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, 0.7f));
    }

    void decir(String s) {
        this.aviso = s;
        this.avisoT = 30;
    }

    @Override
    public void tick() {
        this.tiempo += 1;
        if (this.avisoT > 0) {
            this.avisoT--;
        }
        if (this.fase == 0) {
            this.temp += this.apretado ? 1.25f : -0.7f;
            this.temp = Mth.clamp(this.temp, 0, 100);
            if (this.temp >= 100) {
                this.decir("¡Se recalentó!");
                this.fase = 1;
                this.tiempo = 0;
                this.sonido(SoundEvents.FIRE_EXTINGUISH, 1.0f);
            }
        } else if (this.fase == 3 && !this.enviado) {
            // El servidor pide al menos 5 s: si fuiste muy rapido se espera un poco.
            if (System.currentTimeMillis() - this.inicio >= 6000) {
                this.enviado = true;
                PoliciaRed.CH.sendToServer(new PoliciaRed.ForjaResultado(Math.round(this.puntos)));
            }
        }
    }

    float marcador() {
        // va y viene de 0 a 1; cada golpe es un poco mas rapido
        float vel = 0.09f + this.golpes * 0.012f;
        return 0.5f + 0.5f * Mth.sin(this.tiempo * vel * 2.2f);
    }

    void accion(boolean abajo) {
        if (this.fase == 0) {
            if (abajo) {
                this.apretado = true;
                return;
            }
            this.apretado = false;
            if (this.temp >= 55) {
                float p = Math.max(0, 30 - Math.abs(this.temp - 77.5f) * 1.6f);
                this.puntos += p;
                this.decir(p >= 24 ? "¡Temperatura justa!" : p >= 12 ? "Bien caliente" : "Un poco fuera de punto");
                this.fase = 1;
                this.tiempo = 0;
                this.sonido(SoundEvents.FIRECHARGE_USE, 1.4f);
            }
            return;
        }
        if (!abajo) {
            return;
        }
        if (this.fase == 1) {
            float m = this.marcador();
            float p = Math.max(0, 10 - Math.abs(m - 0.5f) * 26);
            this.puntos += p;
            this.golpes++;
            this.decir(p >= 8 ? "¡Perfecto!" : p >= 4 ? "Bien" : "Torcido...");
            this.sonido(SoundEvents.ANVIL_USE, 0.9f + p * 0.05f);
            if (this.golpes >= 5) {
                this.fase = 2;
                this.tiempo = 0;
            }
        } else if (this.fase == 2) {
            float m = Mth.clamp(this.tiempo / 50.0f, 0, 1);
            float p = Math.max(0, 20 - Math.abs(m - 0.53f) * 80);
            this.puntos += p;
            this.decir(p >= 15 ? "¡Templado perfecto!" : p >= 6 ? "Templado" : "Se templó mal");
            this.sonido(SoundEvents.LAVA_EXTINGUISH, 1.2f);
            this.fase = 3;
            this.marca = this.tiempo;
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            if (!this.apretado || this.fase != 0) {
                this.accion(true);
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            if (this.fase == 0) {
                this.accion(false);
            }
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (this.fase >= 3 && this.enviado) {
            this.onClose();
            return true;
        }
        this.accion(true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        if (this.fase == 0) {
            this.accion(false);
        }
        return true;
    }

    static int resultado(float pts) {
        return pts >= 93 ? 4 : pts >= 80 ? 3 : pts >= 60 ? 2 : pts >= 40 ? 1 : 0;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int x = (this.width - W) / 2;
        int y = (this.height - H) / 2;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF8A4A1A);
        g.fill(x, y, x + W, y + H, 0xF0201410);
        g.drawCenteredString(this.font, "§6§lFORJA", x + W / 2, y + 6, 0xFFFFFF);
        String[] pasos = {"1. Calentá: mantené ESPACIO y soltá en la zona", "2. Martillá: ESPACIO con la marca en el centro (" + this.golpes + "/5)", "3. Templá: ESPACIO cuando la aguja pase lo azul", "Listo"};
        g.drawCenteredString(this.font, "§7" + pasos[Math.min(3, this.fase)], x + W / 2, y + 20, 0xFFFFFF);
        int bx = x + 20;
        int bw = W - 40;
        int by = y + 50;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 15, 0xFF000000);
        if (this.fase == 0) {
            // barra de temperatura con zona buena
            for (int i = 0; i < bw; i++) {
                float k = i / (float) bw;
                int r = (int) (60 + 195 * k);
                int gg = (int) (40 + 120 * Math.max(0, k - 0.5f));
                g.fill(bx + i, by, bx + i + 1, by + 14, 0xFF000000 | r << 16 | gg << 8 | 20);
            }
            g.fill(bx + (int) (bw * 0.55f), by - 3, bx + (int) (bw * 0.95f), by - 1, 0xFF3A6A3A);
            g.fill(bx + (int) (bw * 0.70f), by - 3, bx + (int) (bw * 0.85f), by - 1, 0xFF6AE06A);
            int px = bx + (int) (bw * this.temp / 100f);
            g.fill(px - 1, by - 4, px + 2, by + 18, 0xFFFFFFFF);
        } else if (this.fase == 1) {
            g.fill(bx, by, bx + bw, by + 14, 0xFF3A2A1A);
            g.fill(bx + bw / 2 - 12, by, bx + bw / 2 + 12, by + 14, 0xFF6A5A30);
            g.fill(bx + bw / 2 - 4, by, bx + bw / 2 + 4, by + 14, 0xFFE0C060);
            int px = bx + (int) (bw * this.marcador());
            g.fill(px - 2, by - 4, px + 2, by + 18, 0xFFFFFFFF);
            for (int i = 0; i < 5; i++) {
                g.fill(x + W / 2 - 30 + i * 13, by + 24, x + W / 2 - 22 + i * 13, by + 32, i < this.golpes ? 0xFFE0A030 : 0xFF404040);
            }
        } else {
            for (int i = 0; i < bw; i++) {
                float k = i / (float) bw;
                int c = k < 0.45f ? 0xFFB04020 : k < 0.6f ? 0xFF3A7AD8 : 0xFF6A6A6A;
                g.fill(bx + i, by, bx + i + 1, by + 14, c);
            }
            float m = this.fase == 2 ? Mth.clamp(this.tiempo / 50.0f, 0, 1) : Mth.clamp(this.marca / 50.0f, 0, 1);
            int px = bx + (int) (bw * m);
            g.fill(px - 1, by - 4, px + 2, by + 18, 0xFFFFFFFF);
        }
        if (this.avisoT > 0) {
            g.drawCenteredString(this.font, "§e" + this.aviso, x + W / 2, by + 40, 0xFFFFFF);
        }
        int r = resultado(this.puntos);
        g.drawCenteredString(this.font, "§fPuntos: §6" + Math.round(this.puntos) + "  §7→  ", x + W / 2 - 30, y + H - 34, 0xFFFFFF);
        g.drawString(this.font, PoliciaRareza.etiqueta(r), x + W / 2 + 20, y + H - 34, 0xFFFFFF);
        if (this.fase >= 3) {
            g.drawCenteredString(this.font, this.enviado ? "§aListo. Clic para cerrar." : "§7Enfriando la pieza...", x + W / 2, y + H - 18, 0xFFFFFF);
        } else {
            g.drawCenteredString(this.font, "§8Antes: " + PoliciaRareza.NOMBRE[this.rarezaAntes] + " · Leyenda solo para herreros", x + W / 2, y + H - 18, 0xFFFFFF);
        }
        super.render(g, mx, my, pt);
    }

    // ---------------- Rareza en los objetos ----------------
    static void tooltip(ItemTooltipEvent e) {
        ItemStack st = e.getItemStack();
        int r = PoliciaRareza.rareza(st);
        List<Component> l = e.getToolTip();
        if (r <= 0 || l.isEmpty()) {
            return;
        }
        l.set(0, l.get(0).copy().withStyle(PoliciaRareza.COLOR[r]));
        l.add(1, PoliciaRareza.etiqueta(r));
    }

    static void decoradores(RegisterItemDecorationsEvent e) {
        IItemDecorator d = (GuiGraphics g, Font f, ItemStack st, int x, int y) -> {
            int r = PoliciaRareza.rareza(st);
            if (r <= 0) {
                return false;
            }
            int c = PoliciaRareza.ARGB[r];
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x, y, x + 3, y + 1, c);
            g.fill(x, y, x + 1, y + 3, c);
            if (r >= 3) {
                g.fill(x + 13, y + 15, x + 16, y + 16, c);
                g.fill(x + 15, y + 13, x + 16, y + 16, c);
            }
            g.pose().popPose();
            return false;
        };
        for (net.minecraft.world.item.Item it : ForgeRegistries.ITEMS) {
            if (PoliciaRareza.equipo(new ItemStack(it))) {
                e.register(it, d);
            }
        }
    }
}
