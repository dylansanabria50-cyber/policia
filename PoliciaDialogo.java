package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// Ventana de charla con los jefes de oficio: el NPC a la izquierda, su
// texto que se va escribiendo letra por letra y a la derecha las preguntas
// que le podes hacer. Lo que dice depende de si sos de su oficio, si no
// tenes oficio o si sos de otro.
public class PoliciaDialogo extends Screen {
    static final String[] BOSS = new String[]{"Jefe de policía", "Capataz", "Jefe de ladrones", "Maestro herrero"};
    static final int[] COLOR = new int[]{0xFF6FA0FF, 0xFFFFC04A, 0xFFB98CFF, 0xFFFF9A4A};
    // Saludo segun la relacion: [oficio][0 = de su oficio, 1 = sin oficio, 2 = otro oficio]
    static final String[][] GREET = new String[][]{
            {"Agente, novedades? La ciudad no se cuida sola.", "Buscas trabajo? La policía siempre necesita gente valiente.", "Mmm... no sos de los nuestros. Habla rápido."},
            {"Eh, compañero! Venís a laburar o a mirar?", "Sabes poner un ladrillo derecho? Acá siempre falta mano de obra.", "Esta obra no es para cualquiera, eh."},
            {"Ah, sos vos. Nadie te siguió, no?", "Tenés cara de saber guardar secretos... Buscas un negocio?", "Yo a vos no te conozco. Y vos a mi tampoco, entendido?"},
            {"Aprendiz! El fuego ya está encendido.", "Te interesa el arte de la forja? Las manos se curten, pero vale la pena.", "Si no venís a forjar, al menos no me tapes la luz del horno."}
    };
    static final String[] TOPICS = new String[]{"Quien sos?", "Contame del oficio", "Tenés trabajo para mi?", "Algún consejo?"};
    // Respuestas [oficio][tema] (cada una puede tener varias lineas).
    static final String[][][] ANSWERS = new String[][][]{
            {
                    {"Soy el Jefe de policía de esta zona.", "Desde esta comisaría coordino a los agentes y los refuerzos."},
                    {"Un policía tiene escudo balístico, refuerzos, tanque, helicóptero, esposas, perro K9 y más.", "Todo se desbloquea con XP en tu árbol de habilidades."},
                    {},
                    {"Los bandidos acampan lejos de las aldeas.", "Y nunca subestimes a un francotirador de torre: usa el escudo balístico."}
            },
            {
                    {"Soy el Capataz. Si algo se construye por acá, pasa por mis manos."},
                    {"El albañil levanta muros y refugios, tiene ayudantes que pican por el y hasta un tractor.", "El pico de albañil es sagrado: cuidalo."},
                    {},
                    {"Sin andamios no hay altura.", "Y nunca rompas lo que construyó otro: eso no se hace."}
            },
            {
                    {"Nadie. Un empresario del entretenimiento.", "Este casino es mi oficina, y lo que pasa acá, acá se queda."},
                    {"Carterismo, ganzúas, silbidos para distraer y escapes de película.", "El Perista de arriba te compra lo que traigas, sin preguntas."},
                    {},
                    {"Agachate, acercate por la espalda y no mires atrás.", "Si la policía te busca, desaparece un rato."}
            },
            {
                    {"Soy el Maestro herrero. Llevo cuarenta años martillando acero.", "Cada yunque de esta herrería tiene una historia."},
                    {"El Herrero Legendario planta yunques que dan fuerza o frenan flechas, se cura con su forja interior y repara cualquier herramienta.", "Y con el Analizador aprende a crear objetos que nunca tuvo en la mano."},
                    {},
                    {"Golpe firme, mano tranquila: así se templa el acero.", "Y el yunque que plantas desaparece cuando termina tu habilidad: no lo busques después."}
            }
    };
    // "Tenes trabajo para mi?" segun la relacion.
    static final String[][][] WORK = new String[][][]{
            {{"Las misiones de patrulla te las da el Comisario de cada aldea, junto a la campana."}, {"Primero supera la prueba de reclutamiento: elimina 8 monstruos.", "Después volvé y te doy la placa. Usa 'Unirme al oficio' en mi menú."}, {"Para trabajar conmigo tendrías que dejar tu oficio actual."}},
            {{"Siempre hay obra. Abrí mi menú y mira las Misiones."}, {"Para entrar a la cuadrilla, supera la prueba de obra: coloca 64 bloques.", "Pedila con 'Unirme al oficio' en mi menú."}, {"Ya tenés patrón. Si renuncias, hablamos."}},
            {{"Tengo un par de encargos... discretos. Mira las Misiones en mi menú."}, {"Primero demostrá que sabes moverte: recorre 150 bloques agachado.", "Pedí la prueba con 'Unirme al oficio' en mi menú."}, {"No trabajo con gente de otro bando."}},
            {{"Siempre hay metal que fundir. Mira las Misiones en mi menú."}, {"Demostrame que sabes fundir: 16 lingotes de hierro en un horno.", "Pedí la prueba con 'Unirme al oficio' en mi menú."}, {"Un buen herrero no anda con dos oficios a la vez."}}
    };

    final int entityId;
    final int job;
    final int rel;
    List<String> lines = new ArrayList<String>();
    int line;
    float shown;
    float ticks;
    int lastSound;

    PoliciaDialogo(int entityId, int job, int rel) {
        super(Component.literal(BOSS[Mth.clamp(job, 0, 3)]));
        this.entityId = entityId;
        this.job = Mth.clamp(job, 0, 3);
        this.rel = Mth.clamp(rel, 0, 2);
        this.say(GREET[this.job][this.rel]);
    }

    static void open(int entityId, int job, int rel) {
        Minecraft.getInstance().setScreen(new PoliciaDialogo(entityId, job, rel));
    }

    void say(String... ls) {
        this.lines = new ArrayList<String>(List.of(ls));
        this.line = 0;
        this.shown = 0.0f;
    }

    String current() {
        return this.lines.isEmpty() ? "" : this.lines.get(Math.min(this.line, this.lines.size() - 1));
    }

    boolean lineDone() {
        return this.shown >= (float) this.current().length();
    }

    @Override
    public void tick() {
        this.ticks += 1.0f;
        if (!this.lineDone()) {
            this.shown += 1.6f;
            int n = (int) this.shown;
            if (n / 4 != this.lastSound) {
                this.lastSound = n / 4;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.4f + 0.1f * (float) this.job, 0.25f));
            }
        }
    }

    int boxW() {
        return Math.min(380, this.width - 20);
    }

    int boxX() {
        return (this.width - this.boxW()) / 2;
    }

    int boxY() {
        return this.height - 120;
    }

    int optX() {
        return this.boxX() + this.boxW() - 130;
    }

    int optY(int i) {
        return this.boxY() + 22 + i * 18;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float t = this.ticks + pt;
        float k = Mth.clamp(t / 8.0f, 0.0f, 1.0f);
        k = 1.0f - (1.0f - k) * (1.0f - k);
        g.fill(0, 0, this.width, this.height, (int) (k * 120.0f) << 24);
        int bx = this.boxX();
        int by = this.boxY() + (int) ((1.0f - k) * 60.0f);
        int bw = this.boxW();
        int bh = 110;
        int acc = COLOR[this.job];
        // NPC hablando, a la izquierda del cuadro.
        Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(this.entityId);
        if (e instanceof LivingEntity le) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, bx + 40, by - 4, 52, (float) (bx + 40 - mx), (float) (by - 90 - my), le);
        }
        g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, acc);
        g.fill(bx, by, bx + bw, by + bh, 0xF0101216);
        // Nombre del jefe.
        String nm = BOSS[this.job];
        g.fill(bx + 8, by - 10, bx + 16 + this.font.width(nm), by + 4, acc);
        g.drawString(this.font, nm, bx + 12, by - 7, 0xFF101010, false);
        // Texto con efecto de maquina de escribir.
        String cur = this.current();
        String vis = cur.substring(0, Math.min(cur.length(), (int) this.shown));
        int tw = this.optX() - bx - 18;
        List<FormattedCharSequence> wrapped = this.font.split(FormattedText.of(vis), tw);
        for (int i = 0; i < wrapped.size() && i < 7; ++i) {
            g.drawString(this.font, wrapped.get(i), bx + 10, by + 14 + i * 11, 0xFFEDEDED, false);
        }
        if (this.lineDone() && this.line < this.lines.size() - 1 && ((int) t / 8) % 2 == 0) {
            g.drawString(this.font, "clic para seguir >", bx + 10, by + bh - 12, 0xFFA0A0A0, false);
        }
        // Preguntas.
        int ox = this.optX();
        for (int i = 0; i <= TOPICS.length; ++i) {
            String label = i < TOPICS.length ? TOPICS[i] : "Adios";
            int oy = this.optY(i) + (int) ((1.0f - k) * 60.0f);
            boolean over = mx >= ox && mx < ox + 122 && my >= oy && my < oy + 16;
            g.fill(ox, oy, ox + 122, oy + 16, over ? (acc & 0x00FFFFFF | 0x88000000) : 0x66303540);
            g.drawString(this.font, (over ? "> " : "") + label, ox + 5, oy + 4, over ? 0xFFFFFFFF : 0xFFC8C8D0, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) {
            return super.mouseClicked(mx, my, btn);
        }
        int ox = this.optX();
        for (int i = 0; i <= TOPICS.length; ++i) {
            int oy = this.optY(i);
            if (mx < ox || mx >= ox + 122 || my < oy || my >= oy + 16) continue;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            if (i == TOPICS.length) {
                this.onClose();
            } else if (i == 2) {
                this.say(WORK[this.job][this.rel]);
            } else {
                this.say(ANSWERS[this.job][i]);
            }
            return true;
        }
        // Clic en el cuadro: completa la linea o pasa a la siguiente.
        if (!this.lineDone()) {
            this.shown = (float) this.current().length();
        } else if (this.line < this.lines.size() - 1) {
            ++this.line;
            this.shown = 0.0f;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
