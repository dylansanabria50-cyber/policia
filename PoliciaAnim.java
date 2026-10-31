package com.example.policia;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;

// Etapa 3: animaciones de las habilidades de los oficios, como en la vista
// previa. Cada animacion es una lista de poses clave (brazos, cabeza,
// piernas, inclinacion, agachada y giro del cuerpo) mas "momentos" donde
// salen particulas, sonidos o tiembla la camara, y un objeto en la mano.
// El servidor solo avisa "este jugador empezo tal animacion" (PoliciaRed,
// mensaje 1) a todos los que lo ven; cada cliente la reproduce igual.
public class PoliciaAnim {
    static final float N = Float.NaN;

    // Pose clave en el tick t. NaN = dejar el valor normal del juego.
    // Canales: 0-2 brazo der (x,y,z), 3-5 brazo izq, 6-7 cabeza (x,y),
    // 8-9 piernas (der, izq), 10 inclinacion (grados), 11 agachada
    // (bloques), 12 giro (grados).
    public static class K {
        final int t;
        final float[] v = new float[13];

        K(int t) {
            this.t = t;
            Arrays.fill(this.v, N);
        }

        K ra(float x, float y, float z) {
            this.v[0] = x;
            this.v[1] = y;
            this.v[2] = z;
            return this;
        }

        K la(float x, float y, float z) {
            this.v[3] = x;
            this.v[4] = y;
            this.v[5] = z;
            return this;
        }

        K h(float x, float y) {
            this.v[6] = x;
            this.v[7] = y;
            return this;
        }

        K legs(float r, float l) {
            this.v[8] = r;
            this.v[9] = l;
            return this;
        }

        K lean(float d) {
            this.v[10] = d;
            return this;
        }

        K crouch(float c) {
            this.v[11] = c;
            return this;
        }

        K spin(float d) {
            this.v[12] = d;
            return this;
        }
    }

    public static class Cue {
        int t;
        Supplier<SoundEvent> snd;
        float vol = 1.0f;
        float pitch = 1.0f;
        ParticleOptions fx;
        int n;
        float spread = 0.4f;
        float speed = 0.05f;
        float fwd;
        float up = 1.0f;
        float shake;
        boolean ring;
    }

    public static class Def {
        final String id;
        final int len;
        final List<K> keys = new ArrayList<K>();
        final List<Cue> cues = new ArrayList<Cue>();
        Supplier<ItemStack> propR;
        Supplier<ItemStack> propL;

        Def(String id, int len) {
            this.id = id;
            this.len = len;
        }

        Def k(K k) {
            this.keys.add(k);
            return this;
        }

        Def snd(int t, Supplier<SoundEvent> s, float vol, float pitch) {
            Cue c = new Cue();
            c.t = t;
            c.snd = s;
            c.vol = vol;
            c.pitch = pitch;
            this.cues.add(c);
            return this;
        }

        Def fx(int t, ParticleOptions p, int n, float fwd, float up, float spread, float speed) {
            Cue c = new Cue();
            c.t = t;
            c.fx = p;
            c.n = n;
            c.fwd = fwd;
            c.up = up;
            c.spread = spread;
            c.speed = speed;
            this.cues.add(c);
            return this;
        }

        // Anillo de particulas alrededor del jugador.
        Def ring(int t, ParticleOptions p, int n, float radio, float up) {
            Cue c = new Cue();
            c.t = t;
            c.fx = p;
            c.n = n;
            c.spread = radio;
            c.up = up;
            c.ring = true;
            this.cues.add(c);
            return this;
        }

        Def shake(int t, float a) {
            Cue c = new Cue();
            c.t = t;
            c.shake = a;
            this.cues.add(c);
            return this;
        }

        Def right(Supplier<ItemStack> s) {
            this.propR = s;
            return this;
        }

        Def left(Supplier<ItemStack> s) {
            this.propL = s;
            return this;
        }
    }

    static final List<Def> DEFS = new ArrayList<Def>();
    static final Map<String, Integer> IDX = new HashMap<String, Integer>();

    static Def def(String id, int len) {
        Def d = new Def(id, len);
        IDX.put(id, DEFS.size());
        DEFS.add(d);
        d.k(new K(0));
        return d;
    }

    static K k(int t) {
        return new K(t);
    }

    // Avisa a todos los que ven al jugador (y a el mismo).
    public static void play(ServerPlayer p, String id) {
        Integer i = IDX.get(id);
        if (i == null || p == null) {
            return;
        }
        PoliciaRed.CH.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new PoliciaRed.AnimPlay(p.getId(), i));
    }

    // Juega la animacion solo si la habilidad cambio algo en los datos del
    // jugador (enfriamiento, duracion...). Asi un intento fallido ("en
    // enfriamiento", "ya esta activo") no se anima.
    public static void siCambio(ServerPlayer p, net.minecraft.nbt.CompoundTag antes, String id) {
        if (id != null && !antes.equals(p.getPersistentData())) {
            PoliciaAnim.play(p, id);
        }
    }

    static Supplier<ItemStack> it(net.minecraft.world.item.Item item) {
        return () -> new ItemStack(item);
    }

    static {
        // ---------- Transformacion (todos los oficios) ----------
        def("modo", 22)
                .k(k(5).ra(-1.7f, 0, 0.35f).la(-1.7f, 0, -0.35f).h(0.25f, 0).crouch(0.05f))
                .k(k(11).ra(-0.25f, 0, 0.12f).la(-0.25f, 0, -0.12f).h(-0.15f, 0).lean(-4))
                .k(k(22))
                .snd(5, () -> SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 1.2f)
                .ring(5, ParticleTypes.CRIT, 18, 0.9f, 1.0f)
                .ring(11, ParticleTypes.END_ROD, 12, 0.7f, 1.6f);

        // ---------- Policia ----------
        def("pol_escudo", 36).left(it(Items.SHIELD))
                .k(k(5).la(-1.3f, -0.6f, 0).ra(-0.95f, 0.65f, 0).legs(-0.45f, 0.35f).lean(8).crouch(0.06f))
                .k(k(30).la(-1.3f, -0.6f, 0).ra(-0.95f, 0.65f, 0).legs(-0.45f, 0.35f).lean(8).crouch(0.06f))
                .k(k(36))
                .snd(4, () -> SoundEvents.SHIELD_BLOCK, 0.8f, 1.4f)
                .fx(5, ParticleTypes.CRIT, 10, 0.6f, 1.2f, 0.3f, 0.2f)
                .shake(5, 0.6f);
        def("pol_refuerzo", 44).left(it(Items.RECOVERY_COMPASS))
                .k(k(5).la(-2.3f, 0.35f, 0).h(0.15f, 0.3f))
                .k(k(16).la(-2.3f, 0.35f, 0).h(0.15f, 0.3f))
                .k(k(21).la(-0.4f, 0, 0).ra(-1.65f, 0, 0).h(0, 0).lean(4))
                .k(k(34).ra(-1.65f, 0, 0).lean(4))
                .k(k(44))
                .snd(6, () -> SoundEvents.NOTE_BLOCK_BIT.value(), 0.8f, 1.6f)
                .snd(10, () -> SoundEvents.NOTE_BLOCK_BIT.value(), 0.8f, 1.2f)
                .snd(21, () -> SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1.5f)
                .fx(21, ParticleTypes.END_ROD, 6, 1.2f, 1.4f, 0.1f, 0.05f);
        def("pol_tanque", 30)
                .k(k(6).ra(-2.9f, 0, 0.25f).h(-0.35f, 0))
                .k(k(14).ra(-1.6f, 0, 0).h(0, 0).lean(6))
                .k(k(24).ra(-1.6f, 0, 0).lean(6))
                .k(k(30))
                .snd(6, () -> SoundEvents.NOTE_BLOCK_FLUTE.value(), 0.9f, 2.0f)
                .snd(14, () -> SoundEvents.RAID_HORN.value(), 0.5f, 1.6f)
                .fx(14, ParticleTypes.CLOUD, 14, 2.0f, 0.2f, 0.6f, 0.05f)
                .shake(14, 0.5f);
        def("pol_heli", 32)
                .k(k(5).ra(-3.0f, 0, 0.5f).h(-0.6f, 0))
                .k(k(10).ra(-3.0f, 0, 0.1f).h(-0.6f, 0))
                .k(k(15).ra(-3.0f, 0, 0.5f).h(-0.6f, 0))
                .k(k(20).ra(-3.0f, 0, 0.1f).h(-0.6f, 0))
                .k(k(26).ra(-3.0f, 0, 0.4f).h(-0.5f, 0))
                .k(k(32))
                .snd(5, () -> SoundEvents.PHANTOM_FLAP, 0.8f, 0.6f)
                .snd(15, () -> SoundEvents.PHANTOM_FLAP, 0.8f, 0.5f)
                .fx(10, ParticleTypes.CLOUD, 16, 0, 0.1f, 1.4f, 0.08f);
        def("pol_esposas", 22)
                .k(k(4).ra(-1.0f, -0.2f, 0).la(-1.0f, 0.2f, 0).lean(4))
                .k(k(7).ra(-1.55f, -0.35f, 0).la(-1.55f, 0.35f, 0).lean(12).legs(-0.5f, 0.4f))
                .k(k(16).ra(-1.4f, -0.35f, 0).la(-1.4f, 0.35f, 0).lean(8))
                .k(k(22))
                .snd(7, () -> SoundEvents.CHAIN_PLACE, 1.0f, 1.4f)
                .fx(7, ParticleTypes.CRIT, 8, 1.1f, 1.0f, 0.2f, 0.15f);
        def("pol_k9", 30)
                .k(k(5).crouch(0.18f).lean(16).ra(-0.6f, 0, 0).la(-0.4f, 0, 0).h(0.4f, 0))
                .k(k(12).crouch(0.18f).lean(16).ra(-2.2f, 0.5f, 0).h(0.2f, 0))
                .k(k(18).ra(-1.6f, 0, 0).lean(5))
                .k(k(26).ra(-1.6f, 0, 0).lean(5))
                .k(k(30))
                .snd(12, () -> SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.0f, 2.0f)
                .snd(15, () -> SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.0f, 1.7f)
                .snd(19, () -> SoundEvents.WOLF_AMBIENT, 0.8f, 1.1f);
        def("pol_sirena", 34)
                .k(k(6).ra(-3.0f, 0, 0.2f).h(-0.4f, 0))
                .k(k(26).ra(-3.0f, 0, 0.2f).h(-0.4f, 0))
                .k(k(34))
                .snd(6, () -> SoundEvents.NOTE_BLOCK_BIT.value(), 1.0f, 2.0f)
                .snd(11, () -> SoundEvents.NOTE_BLOCK_BIT.value(), 1.0f, 1.5f)
                .snd(16, () -> SoundEvents.NOTE_BLOCK_BIT.value(), 1.0f, 2.0f)
                .snd(21, () -> SoundEvents.NOTE_BLOCK_BIT.value(), 1.0f, 1.5f)
                .fx(6, ParticleTypes.ELECTRIC_SPARK, 12, 0, 2.6f, 0.3f, 0.1f);
        def("pol_dron", 28)
                .k(k(6).ra(-0.4f, 0, 0).crouch(0.06f))
                .k(k(11).ra(-3.0f, 0, 0).h(-0.5f, 0).lean(-4))
                .k(k(20).ra(-2.6f, 0, 0).h(-0.6f, 0))
                .k(k(28))
                .snd(11, () -> SoundEvents.ENDER_PEARL_THROW, 0.8f, 1.4f)
                .snd(14, () -> SoundEvents.BEE_LOOP, 0.8f, 1.8f)
                .fx(11, ParticleTypes.CLOUD, 6, 0.3f, 2.2f, 0.2f, 0.05f);
        def("pol_burbuja", 32)
                .k(k(7).ra(-2.2f, 0, 0.6f).la(-2.2f, 0, -0.6f).h(-0.3f, 0))
                .k(k(24).ra(-2.4f, 0, 0.9f).la(-2.4f, 0, -0.9f).h(-0.3f, 0))
                .k(k(32))
                .snd(7, () -> SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f)
                .ring(7, ParticleTypes.END_ROD, 24, 1.4f, 1.0f)
                .ring(14, ParticleTypes.END_ROD, 24, 1.8f, 1.8f);

        // ---------- Ladron ----------
        def("lad_silbido", 24)
                .k(k(5).ra(-2.5f, 0.55f, 0).h(-0.1f, 0))
                .k(k(18).ra(-2.5f, 0.55f, 0).h(-0.1f, 0))
                .k(k(24))
                .snd(6, () -> SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.0f, 2.0f)
                .snd(10, () -> SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.0f, 2.0f)
                .fx(6, ParticleTypes.NOTE, 4, 0.4f, 1.8f, 0.3f, 0.5f);
        def("lad_humo", 26)
                .k(k(5).ra(-2.6f, 0, 0.2f).crouch(0.05f))
                .k(k(9).ra(0.4f, 0, 0).lean(12).crouch(0.15f))
                .k(k(20).lean(4).crouch(0.1f))
                .k(k(26))
                .fx(9, ParticleTypes.LARGE_SMOKE, 30, 0.4f, 0.4f, 0.6f, 0.05f)
                .shake(9, 0.3f);
        def("lad_salto", 18)
                .k(k(3).crouch(0.28f).lean(22).ra(0.6f, 0, 0).la(0.6f, 0, 0))
                .k(k(7).lean(-6).ra(0.9f, 0, 0.3f).la(0.9f, 0, -0.3f).legs(0.5f, -0.3f))
                .k(k(18))
                .fx(3, ParticleTypes.CLOUD, 10, 0, 0.1f, 0.4f, 0.05f);
        def("lad_instinto", 30)
                .k(k(6).ra(-2.2f, 0.6f, -0.3f).h(0.15f, 0.4f))
                .k(k(14).ra(-2.2f, 0.6f, -0.3f).h(0.15f, -0.4f))
                .k(k(22).ra(-2.2f, 0.6f, -0.3f).h(0, 0))
                .k(k(30))
                .ring(8, ParticleTypes.ENCHANT, 30, 1.6f, 1.6f);
        def("lad_polvo", 24)
                .k(k(6).ra(-1.9f, 0.4f, 0).la(-1.9f, -0.4f, 0).h(0.1f, 0))
                .k(k(12).ra(-1.4f, 0, 0).la(-1.4f, 0, 0).lean(9))
                .k(k(24))
                .snd(12, () -> SoundEvents.SAND_BREAK, 1.0f, 1.3f)
                .fx(12, ParticleTypes.WHITE_ASH, 40, 1.5f, 1.4f, 0.6f, 0.08f);
        def("lad_escape", 26)
                .k(k(10).spin(360).crouch(0.22f).ra(-0.3f, 0, 0.5f).la(-0.3f, 0, -0.5f))
                .k(k(16).spin(360).crouch(0.1f))
                .k(k(26).spin(360))
                .fx(10, ParticleTypes.LARGE_SMOKE, 30, 0, 0.6f, 0.6f, 0.03f);

        // ---------- Albanil ----------
        def("alb_pico", 24)
                .k(k(6).ra(-2.9f, 0, 0.1f).h(-0.2f, 0).lean(-4))
                .k(k(10).ra(-0.3f, 0, 0).lean(12).crouch(0.06f).h(0.35f, 0))
                .k(k(18).ra(-0.5f, 0, 0).lean(6))
                .k(k(24))
                .snd(10, () -> SoundEvents.STONE_BREAK, 1.0f, 0.8f)
                .fx(10, ParticleTypes.POOF, 10, 1.2f, 0.2f, 0.4f, 0.05f)
                .shake(10, 0.4f);
        def("alb_muro", 32)
                .k(k(8).ra(-2.8f, 0, 0.2f).la(-2.8f, 0, -0.2f).crouch(0.05f).h(-0.3f, 0))
                .k(k(13).ra(-0.4f, 0, 0).la(-0.4f, 0, 0).crouch(0.22f).lean(16).h(0.3f, 0))
                .k(k(24).ra(-0.4f, 0, 0).la(-0.4f, 0, 0).crouch(0.1f).lean(6))
                .k(k(32))
                .snd(13, () -> SoundEvents.STONE_PLACE, 1.0f, 0.7f)
                .snd(13, () -> SoundEvents.GENERIC_EXPLODE, 0.3f, 1.6f)
                .fx(13, ParticleTypes.POOF, 24, 1.4f, 0.1f, 0.8f, 0.06f)
                .shake(13, 0.8f);
        def("alb_ayudante", 30)
                .k(k(5).ra(-3.0f, 0, 0.6f).h(-0.1f, 0))
                .k(k(10).ra(-3.0f, 0, 0.15f))
                .k(k(15).ra(-3.0f, 0, 0.6f))
                .k(k(20).ra(-3.0f, 0, 0.15f))
                .k(k(30))
                .snd(5, () -> SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.0f, 1.8f)
                .snd(9, () -> SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.0f, 2.0f)
                .fx(8, ParticleTypes.HAPPY_VILLAGER, 8, 0.5f, 2.2f, 0.4f, 0.05f);
        def("alb_linterna", 18).right(it(Items.LANTERN))
                .k(k(5).ra(-1.7f, 0, 0).h(-0.1f, 0))
                .k(k(12).ra(-1.7f, 0, 0))
                .k(k(18))
                .snd(5, () -> SoundEvents.FLINTANDSTEEL_USE, 1.0f, 1.2f)
                .fx(5, ParticleTypes.FLAME, 6, 0.6f, 1.3f, 0.1f, 0.01f);
        def("alb_tractor", 28)
                .k(k(6).ra(-2.9f, 0, 0.25f).h(-0.3f, 0))
                .k(k(14).ra(-1.6f, 0, 0).lean(6))
                .k(k(28))
                .snd(6, () -> SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.0f, 2.0f)
                .fx(14, ParticleTypes.LARGE_SMOKE, 12, 1.8f, 0.4f, 0.5f, 0.03f);

        // ---------- Herrero ----------
        def("her_grito", 34)
                .k(k(4).ra(-0.6f, 0, 0.3f).la(-0.6f, 0, -0.3f).crouch(0.08f).lean(8))
                .k(k(8).ra(-2.6f, 0, 0.6f).la(-2.6f, 0, -0.6f).h(-0.55f, 0).lean(-8))
                .k(k(24).ra(-2.6f, 0, 0.6f).la(-2.6f, 0, -0.6f).h(-0.55f, 0).lean(-8))
                .k(k(34))
                .snd(8, () -> SoundEvents.RAVAGER_ROAR, 0.7f, 1.3f)
                .ring(8, ParticleTypes.CRIT, 30, 1.6f, 0.4f)
                .ring(12, ParticleTypes.FLAME, 20, 2.2f, 0.3f)
                .shake(8, 1.0f);
        def("her_marca", 26)
                .k(k(6).ra(-2.7f, 0, 0).h(-0.2f, 0))
                .k(k(10).ra(-0.5f, 0, 0).lean(10).h(0.2f, 0))
                .k(k(20).ra(-1.5f, 0, 0).lean(4))
                .k(k(26))
                .snd(10, () -> SoundEvents.ANVIL_LAND, 0.6f, 1.6f)
                .fx(10, ParticleTypes.FLAME, 10, 1.6f, 1.0f, 0.2f, 0.05f);
        def("her_forja", 34)
                .k(k(6).ra(-1.2f, 0, 0.3f).la(-1.2f, 0, -0.3f).crouch(0.1f).lean(6))
                .k(k(26).ra(-1.3f, 0, 0.35f).la(-1.3f, 0, -0.35f).crouch(0.1f).lean(6).h(0.2f, 0))
                .k(k(34))
                .snd(6, () -> SoundEvents.FIRECHARGE_USE, 0.8f, 1.0f)
                .ring(6, ParticleTypes.FLAME, 20, 0.8f, 0.8f)
                .ring(14, ParticleTypes.LAVA, 6, 0.6f, 1.2f);
        def("her_faro", 30)
                .k(k(6).crouch(0.25f).lean(18).ra(-0.6f, 0, 0).la(-0.6f, 0, 0))
                .k(k(14).crouch(0.25f).lean(18).ra(-0.4f, 0, 0).la(-0.4f, 0, 0))
                .k(k(20).ra(-2.6f, 0, 0.3f).h(-0.4f, 0))
                .k(k(30))
                .snd(14, () -> SoundEvents.BEACON_ACTIVATE, 0.8f, 1.2f)
                .fx(14, ParticleTypes.END_ROD, 20, 1.2f, 0.4f, 0.2f, 0.15f);
        def("her_vinculo", 28)
                .k(k(6).ra(-1.3f, 0.7f, 0).h(0.1f, 0))
                .k(k(14).ra(-1.6f, 0, 0).lean(5))
                .k(k(28))
                .snd(6, () -> SoundEvents.CHAIN_PLACE, 1.0f, 1.0f)
                .snd(14, () -> SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.4f)
                .fx(14, ParticleTypes.ENCHANT, 24, 1.6f, 1.2f, 0.3f, 0.5f);
        def("her_analizador", 28).left(it(Items.SPYGLASS))
                .k(k(6).la(-1.9f, 0.25f, 0).h(0.3f, 0))
                .k(k(22).la(-1.9f, 0.25f, 0).h(0.3f, 0.25f))
                .k(k(28))
                .snd(6, () -> SoundEvents.SPYGLASS_USE, 1.0f, 1.0f)
                .fx(10, ParticleTypes.ENCHANT, 12, 1.0f, 1.5f, 0.3f, 0.3f);
    }
}
