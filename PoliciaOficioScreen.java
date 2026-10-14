package com.example.policia;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

// Ventana flotante "¿Cual es tu trabajo sonado?" que aparece la primera vez
// que entras al mundo. El panel baja con rebote, las tarjetas aparecen una
// por una, se levantan al pasar el mouse y la elegida brilla antes de
// cerrarse. No se puede cerrar con ESC: hay que elegir (DESEMPLEADO deja
// el personaje vanilla).
public class PoliciaOficioScreen extends Screen {
    static boolean pending;
    static final String[] NAMES = new String[]{"Policía", "Albañil", "Ladrón", "Herrero", "DESEMPLEADO"};
    static final String[] DESC = new String[]{
            "Escudo, refuerzos, tanque y helicóptero",
            "Muros, refugios, ayudantes y tractor",
            "Carterismo, sigilo y escapes",
            "Yunques, forja y el Analizador",
            "Seguís vanilla, sin ningún cambio"
    };
    static final int[] ACCENT = new int[]{0xFF3A6FD8, 0xFFE0A030, 0xFF8A4FD0, 0xFFD06A2A, 0xFF8A8A8A};
    static final int CW = 76;
    static final int CH = 112;
    static final int GAP = 6;
    float ticks;
    int picked = -1;
    float pickedAt;

    public PoliciaOficioScreen() {
        super(Component.literal("Tu trabajo soñado"));
    }

    static ItemStack icon(int i) {
        return switch (i) {
            case 0 -> new ItemStack((ItemLike) PoliciaPlaca.PLACA.get());
            case 1 -> new ItemStack((ItemLike) PoliciaPlaca.BALDE.get());
            case 2 -> new ItemStack((ItemLike) PoliciaPlaca.GUANTE.get());
            case 3 -> new ItemStack((ItemLike) PoliciaPlaca.MARTILLO.get());
            default -> new ItemStack((ItemLike) Items.GRASS_BLOCK);
        };
    }

    static float easeOutBack(float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(k - 1.0f, 3) + c1 * (float) Math.pow(k - 1.0f, 2);
    }

    static float easeOut(float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        return 1.0f - (1.0f - k) * (1.0f - k);
    }

    static int alpha(int argb, float a) {
        int al = Mth.clamp((int) ((float) (argb >>> 24) * a), 0, 255);
        return al << 24 | argb & 0xFFFFFF;
    }

    int panelW() {
        return 5 * CW + 4 * GAP + 24;
    }

    int panelH() {
        return CH + 74;
    }

    int cardX(int i) {
        return (this.width - this.panelW()) / 2 + 12 + i * (CW + GAP);
    }

    int cardY() {
        return (this.height - this.panelH()) / 2 + 52;
    }

    @Override
    public void tick() {
        this.ticks += 1.0f;
        if (this.picked >= 0 && this.ticks - this.pickedAt >= 10.0f) {
            PoliciaClient.send(70 + this.picked);
            this.onClose();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float t = this.ticks + pt;
        // Fondo que se oscurece de a poco.
        float bg = easeOut(t / 10.0f);
        g.fill(0, 0, this.width, this.height, alpha(0xC0080A10, bg));
        // Panel que baja desde arriba con rebote.
        float k = easeOutBack(t / 14.0f);
        int pw = this.panelW();
        int ph = this.panelH();
        int x0 = (this.width - pw) / 2;
        int y0 = (this.height - ph) / 2;
        int drop = (int) ((1.0f - k) * -(float) (y0 + ph));
        g.pose().pushPose();
        g.pose().translate(0.0f, (float) drop, 0.0f);
        g.fill(x0 - 2, y0 - 2, x0 + pw + 2, y0 + ph + 2, 0xFF7A5A24);
        g.fill(x0 - 1, y0 - 1, x0 + pw + 1, y0 + ph + 1, 0xFFE7C066);
        g.fill(x0, y0, x0 + pw, y0 + ph, 0xF0151820);
        g.fill(x0, y0, x0 + pw, y0 + 30, 0xF0222836);
        // Brillo que recorre el titulo.
        float sweep = (t * 6.0f) % (float) (pw + 120) - 60.0f;
        for (int i = 0; i < 24; ++i) {
            int sx = x0 + (int) sweep + i;
            if (sx < x0 || sx >= x0 + pw) continue;
            g.fill(sx, y0, sx + 1, y0 + 30, alpha(0x30FFFFFF, 1.0f - Math.abs((float) i - 12.0f) / 12.0f));
        }
        String title = "¿Cuál es tu trabajo soñado?";
        g.pose().pushPose();
        g.pose().translate((float) (this.width / 2), (float) (y0 + 10), 0.0f);
        g.pose().scale(1.5f, 1.5f, 1.0f);
        g.drawString(this.font, title, -this.font.width(title) / 2, 0, 0xFFFFE08A, true);
        g.pose().popPose();
        String sub = "Elegí bien: solo se elige una vez";
        g.drawString(this.font, sub, (this.width - this.font.width(sub)) / 2, y0 + 36, 0xFFB8B8C8, false);
        int hover = -1;
        for (int i = 0; i < 5; ++i) {
            // Cada tarjeta aparece 3 ticks despues de la anterior.
            float ck = easeOutBack((t - 8.0f - (float) i * 3.0f) / 10.0f);
            float ca = easeOut((t - 8.0f - (float) i * 3.0f) / 6.0f);
            if (ca <= 0.0f) continue;
            int cx = this.cardX(i);
            int cy = this.cardY();
            boolean over = this.picked < 0 && mx >= cx && mx < cx + CW && my >= cy + drop && my < cy + CH + drop;
            if (over) {
                hover = i;
            }
            float lift = over ? 4.0f + Mth.sin(t * 0.3f) : 0.0f;
            boolean chosen = this.picked == i;
            boolean dim = this.picked >= 0 && !chosen;
            g.pose().pushPose();
            g.pose().translate((float) (cx + CW / 2), (float) (cy + CH / 2) - lift, 0.0f);
            float sc = Mth.lerp(ck, 0.6f, 1.0f) * (chosen ? 1.0f + 0.06f * Mth.sin((t - this.pickedAt) * 0.8f) : 1.0f);
            g.pose().scale(sc, sc, 1.0f);
            g.pose().translate((float) (-CW / 2), (float) (-CH / 2), 0.0f);
            int acc = ACCENT[i];
            float a = ca * (dim ? 0.35f : 1.0f);
            int border = chosen ? 0xFFFFF0A0 : (over ? 0xFFFFE08A : acc);
            g.fill(-1, -1, CW + 1, CH + 1, alpha(border, a));
            g.fill(0, 0, CW, CH, alpha(0xF0101318, a));
            g.fill(0, 0, CW, 4, alpha(acc, a));
            if (over || chosen) {
                g.fill(0, 4, CW, CH, alpha(acc & 0x00FFFFFF | 0x22000000, a));
            }
            // Icono grande que flota.
            float bob = Mth.sin(t * 0.15f + (float) i) * 1.5f;
            g.pose().pushPose();
            g.pose().translate((float) (CW / 2), 30.0f + bob, 0.0f);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            if (a > 0.5f) {
                g.renderItem(PoliciaOficioScreen.icon(i), -8, -8);
            }
            g.pose().popPose();
            String nm = NAMES[i];
            g.drawString(this.font, nm, (CW - this.font.width(nm)) / 2, 56, alpha(i == 4 ? 0xFFD0D0D0 : 0xFFFFFFFF, a), true);
            List<FormattedCharSequence> lines = this.font.split(FormattedText.of(DESC[i]), CW - 8);
            for (int l = 0; l < lines.size() && l < 3; ++l) {
                int lw = this.font.width(lines.get(l));
                g.drawString(this.font, lines.get(l), (CW - lw) / 2, 70 + l * 10, alpha(0xFFA8A8B8, a), false);
            }
            g.pose().popPose();
        }
        String foot = hover >= 0 ? (hover == 4 ? "Seguir sin oficio" : "Elegir " + NAMES[hover]) : (this.picked >= 0 ? "¡Buena elección!" : "Hacé clic en una tarjeta");
        g.drawString(this.font, foot, (this.width - this.font.width(foot)) / 2, y0 + ph - 14, 0xFFE7C066, false);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0 || this.picked >= 0 || this.ticks < 14.0f) {
            return true;
        }
        for (int i = 0; i < 5; ++i) {
            int cx = this.cardX(i);
            int cy = this.cardY();
            if (mx < cx || mx >= cx + CW || my < cy || my >= cy + CH) continue;
            this.picked = i;
            this.pickedAt = this.ticks;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(i == 4 ? SoundEvents.UI_BUTTON_CLICK.value() : SoundEvents.PLAYER_LEVELUP, 1.0f));
            return true;
        }
        return true;
    }
}
