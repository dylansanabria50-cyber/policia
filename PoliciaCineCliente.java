package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;

// Escena de llegada en carruaje: tomas de camara de pelicula (aerea,
// travelling de costado, de frente y grua sobre el pueblo), franjas negras,
// titulos, sonido de cascos y polvo. El carruaje es el modelo de la vista
// previa con sus animaciones horneadas y la fisica de faroles, colas y crines.
public class PoliciaCineCliente {
    static final ResourceLocation TEX = new ResourceLocation("policia", "textures/entity/criatura_carruaje.png");
    static final ResourceLocation BRILLO = new ResourceLocation("policia", "textures/entity/criatura_carruaje_brillo.png");
    static final float VEL = 4.2f;
    static final float RADIO_RUEDA = 6f * 1.3f / 16f;

    static boolean activo;
    // 0 = llegada al empezar el mundo, 1 = llegada de un viaje, 2 = salida de un viaje
    static int modo;
    static int tick;
    static float[] px;
    static float[] py;
    static float[] pz;
    static float largo;
    static Vec3 adelante;
    static Vec3 derecha;
    static Vec3 centro;
    static String nombre = "";
    static float tLlegada;
    static float tFin;
    static Marker cam;
    static CameraType camaraAntes;
    static int saltar;
    static int tomaAntes = -1;
    static boolean partido;
    static int despues;

    static PoliciaCriaturasCliente.Modelo modelo;
    static PoliciaCriaturasCliente.Datos datos;
    static PoliciaCriaturasCliente.Estado estado;
    static float relojAnim;
    static long nanos;
    static int[] ruedas;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PoliciaCineCliente::tick);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCineCliente::angulos);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCineCliente::fov);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCineCliente::dibujar);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCineCliente::hud);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCineCliente::ocultarHud);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCineCliente::mano);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCineCliente::jugador);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCineCliente::quieto);
    }

    // ---------------- Modelo del carruaje ----------------
    static void cargarModelo(Minecraft mc) {
        if (modelo != null) {
            return;
        }
        PoliciaCriaturasCliente.Datos d = new PoliciaCriaturasCliente.Datos();
        d.huesos = PoliciaCriaturasModelos.HUESOS_CARRUAJE;
        d.padres = PoliciaCriaturasModelos.PADRES_CARRUAJE;
        d.resortes = PoliciaCriaturasModelos.RESORTES_CARRUAJE;
        d.estelas = PoliciaCriaturasModelos.ESTELAS_CARRUAJE;
        d.escala = PoliciaCriaturasModelos.ESCALA_CARRUAJE;
        modelo = new PoliciaCriaturasCliente.Modelo(PoliciaCriaturasModelos.carruaje().bakeRoot(), d);
        float[][] rest = new float[d.huesos.length][3];
        for (int i = 0; i < rest.length; i++) {
            rest[i][0] = modelo.reposo[i][3];
            rest[i][1] = modelo.reposo[i][4];
            rest[i][2] = modelo.reposo[i][5];
        }
        PoliciaCriaturasCliente.cargarArchivo(d, mc.getResourceManager(), "carruaje", rest);
        datos = d;
        ruedas = new int[4];
        String[] rn = {"ruedaTD", "ruedaTI", "ruedaDD", "ruedaDI"};
        for (int k = 0; k < 4; k++) {
            ruedas[k] = -1;
            for (int i = 0; i < d.huesos.length; i++) {
                if (d.huesos[i].equals(rn[k])) {
                    ruedas[k] = i;
                }
            }
        }
    }

    static void nuevoEstado() {
        PoliciaCriaturasCliente.Estado s = new PoliciaCriaturasCliente.Estado();
        int n = datos.resortes.length;
        s.ang = new float[n][3];
        s.vel = new float[n][3];
        s.prevP = new float[n][3];
        s.prevV = new float[n][3];
        s.estela = new Vec3[0][16];
        s.mundo = new Matrix4f[datos.huesos.length];
        for (int i = 0; i < s.mundo.length; i++) {
            s.mundo[i] = new Matrix4f();
        }
        estado = s;
    }

    // ---------------- Arranque ----------------
    public static void recibir(String tipo, CompoundTag d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        cargarModelo(mc);
        if (datos.anims == null) {
            terminar(mc, true);
            return;
        }
        int[] ys = d.getIntArray("ys");
        int n = ys.length;
        px = new float[n];
        py = new float[n];
        pz = new float[n];
        Vec3 a = new Vec3(d.getDouble("x0"), 0, d.getDouble("z0"));
        Vec3 b = new Vec3(d.getDouble("x1"), 0, d.getDouble("z1"));
        for (int i = 0; i < n; i++) {
            Vec3 q = a.lerp(b, i / (float) (n - 1));
            px[i] = (float) q.x;
            pz[i] = (float) q.z;
            py[i] = ys[i] / 100f;
        }
        largo = (float) a.distanceTo(b);
        adelante = b.subtract(a).normalize();
        derecha = new Vec3(-adelante.z, 0, adelante.x);
        centro = new Vec3(d.getDouble("cx"), d.getDouble("cy"), d.getDouble("cz"));
        nombre = d.getString("nombre");
        modo = "cine_salida".equals(tipo) ? 2 : d.getBoolean("viaje") ? 1 : 0;
        // llega: 1 s de espera, 1,5 s acelerando, crucero y 3 s frenando
        tLlegada = 1f + 1.5f + Math.max(0, largo - 3.15f - 6.3f) / VEL + 3f;
        tFin = tLlegada + 4.8f;
        if (modo == 2) {
            tLlegada = 99f;
            tFin = 9f;
        }
        tick = 0;
        saltar = 0;
        tomaAntes = -1;
        relojAnim = 0;
        nanos = 0;
        despues = 0;
        partido = false;
        nuevoEstado();
        cam = new Marker(EntityType.MARKER, mc.level);
        Vec3 c0 = camara(0);
        cam.setPos(c0.x, c0.y, c0.z);
        cam.xo = c0.x;
        cam.yo = c0.y;
        cam.zo = c0.z;
        if (!activo) {
            camaraAntes = mc.options.getCameraType();
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(cam);
        activo = true;
    }

    static void terminar(Minecraft mc, boolean avisar) {
        if (activo) {
            mc.setCameraEntity(mc.player);
            if (camaraAntes != null) {
                mc.options.setCameraType(camaraAntes);
            }
        }
        activo = false;
        cam = null;
        if (avisar) {
            PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("cine_fin", new CompoundTag()));
        }
    }

    // ---------------- Camino y movimiento ----------------
    // Distancia recorrida por el carruaje en el instante t (segundos).
    static float recorrido(float t) {
        if (modo == 2) {
            // salida: espera parado y arranca acelerando
            return t <= 1.4f ? 0 : 1.4f * (t - 1.4f) * (t - 1.4f);
        }
        if (t <= 1f) {
            return 0;
        }
        float u = t - 1f;
        if (u < 1.5f) {
            return 1.4f * u * u;
        }
        float dConst = Math.max(0, largo - 9.45f);
        float tConst = dConst / VEL;
        if (u < 1.5f + tConst) {
            return 3.15f + (u - 1.5f) * VEL;
        }
        float w = u - 1.5f - tConst;
        if (w < 3f) {
            return 3.15f + dConst + VEL * w - 0.7f * w * w;
        }
        if (t <= tFin) {
            return largo;
        }
        // despues de bajarte, el carruaje sigue viaje
        float z = t - tFin;
        return largo + 0.6f * z * z;
    }

    static float velocidad(float t) {
        return (recorrido(t + 0.05f) - recorrido(t - 0.05f)) / 0.1f;
    }

    static Vec3 punto(float s) {
        if (s >= largo) {
            int n = px.length - 1;
            return new Vec3(px[n], py[n], pz[n]).add(adelante.scale(s - largo));
        }
        float f = Mth.clamp(s / largo, 0, 1) * (px.length - 1);
        int i = Math.min(px.length - 2, (int) Math.floor(f));
        float k = f - i;
        return new Vec3(Mth.lerp(k, px[i], px[i + 1]), Mth.lerp(k, py[i], py[i + 1]), Mth.lerp(k, pz[i], pz[i + 1]));
    }

    static float pendiente(float s) {
        Vec3 a = punto(Math.max(0, s - 2));
        Vec3 b = punto(s + 2);
        return (float) Math.atan2(b.y - a.y, Math.max(0.1, Math.hypot(b.x - a.x, b.z - a.z)));
    }

    // ---------------- Tomas ----------------
    static int toma(float t) {
        if (modo == 2) {
            return t < 2.2f ? 10 : 11;
        }
        if (t < 0.3f * tLlegada) {
            return 0;
        }
        if (t < 0.55f * tLlegada) {
            return 1;
        }
        if (t < 0.74f * tLlegada) {
            return 2;
        }
        if (t < tLlegada + 0.6f) {
            return 3;
        }
        return 4;
    }

    static Vec3[] pose(float t) {
        Vec3 c = punto(recorrido(t)).add(0, 1.0, 0);
        Vec3 up = new Vec3(0, 1, 0);
        int k = toma(t);
        Vec3 ojo;
        Vec3 mira;
        switch (k) {
            case 0 -> {
                float u = t / (0.3f * tLlegada);
                Vec3 medio = punto(largo * 0.45f);
                ojo = medio.add(derecha.scale(-26)).add(up.scale(20 - 3 * u)).add(adelante.scale(-14 + 14 * u));
                mira = c;
            }
            case 1 -> {
                float u = (t - 0.3f * tLlegada) / (0.25f * tLlegada);
                ojo = c.add(derecha.scale(4.6)).add(up.scale(0.4)).add(adelante.scale(-2.2 + 3.0 * u));
                mira = c.add(adelante.scale(1.2)).add(up.scale(0.2));
            }
            case 2 -> {
                Vec3 fijo = punto(recorrido(0.78f * tLlegada) + 4.5f).add(derecha.scale(-1.6)).add(up.scale(1.0));
                ojo = fijo;
                mira = c.add(up.scale(0.4));
            }
            case 3 -> {
                float u = Mth.clamp((t - 0.74f * tLlegada) / (0.26f * tLlegada + 0.6f), 0, 1);
                float su = u * u * (3 - 2 * u);
                ojo = c.add(adelante.scale(-7 + 1.5 * su)).add(up.scale(2.5 + 11 * su)).add(derecha.scale(-5 - 3 * su));
                mira = c.lerp(centro.add(0, 2, 0), su * 0.6);
            }
            case 10 -> {
                Vec3 c0 = punto(0).add(0, 1.0, 0);
                ojo = c0.add(derecha.scale(4.8)).add(adelante.scale(2.4)).add(up.scale(0.7));
                mira = c0.add(derecha.scale(0.6)).add(adelante.scale(-0.4));
            }
            case 11 -> {
                Vec3 c0 = punto(0).add(0, 1.0, 0);
                ojo = c0.add(adelante.scale(-6)).add(derecha.scale(-3.5)).add(up.scale(3.2));
                mira = c;
            }
            default -> {
                Vec3 parada = punto(largo).add(0, 1.0, 0);
                float u = Mth.clamp((t - tLlegada - 0.6f) / 4f, 0, 1);
                ojo = parada.add(derecha.scale(5.2 - 0.8 * u)).add(adelante.scale(-2.2 + 0.6 * u)).add(up.scale(0.9 + 0.3 * u));
                mira = parada.add(derecha.scale(1.6)).add(up.scale(0.1));
            }
        }
        return new Vec3[]{ojo, mira};
    }

    // si la toma cae adentro de un cerro o una pared, la camara sube
    static Vec3 libre(Minecraft mc, Vec3 o) {
        for (int i = 0; i < 12; i++) {
            BlockPos b = BlockPos.containing(o);
            if (mc.level.getBlockState(b).getCollisionShape(mc.level, b).isEmpty() && mc.level.getBlockState(b.below()).getCollisionShape(mc.level, b.below()).isEmpty() || i == 11) {
                return o;
            }
            o = o.add(0, 1, 0);
        }
        return o;
    }

    static Vec3 camara(float t) {
        return pose(t)[0];
    }

    // ---------------- Eventos ----------------
    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            activo = false;
            despues = 0;
            return;
        }
        if (!activo) {
            if (despues > 0) {
                despues--;
                tick++;
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        tick++;
        float t = tick / 20f;
        if (mc.options.keyJump.isDown()) {
            saltar++;
            if (saltar == 20 && t < tLlegada && modo != 2) {
                tick = (int) ((tLlegada + 0.6f) * 20);
                t = tick / 20f;
            }
        } else {
            saltar = 0;
        }
        // camara: posicion por tick (Minecraft la interpola)
        Vec3 o = libre(mc, camara(t));
        int k = toma(t);
        cam.xo = cam.getX();
        cam.yo = cam.getY();
        cam.zo = cam.getZ();
        cam.setPos(o.x, o.y, o.z);
        if (k != tomaAntes) {
            cam.xo = o.x;
            cam.yo = o.y;
            cam.zo = o.z;
            tomaAntes = k;
        }
        // sonidos y polvo del carruaje
        float v = velocidad(t);
        Vec3 c = punto(recorrido(t));
        if (v > 0.5f) {
            if (tick % 6 == 0) {
                mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.HORSE_STEP, SoundSource.NEUTRAL, 0.8f, 0.9f + (tick % 12 == 0 ? 0.15f : 0), false);
            }
            if (tick % 11 == 0) {
                mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.WOOD_STEP, SoundSource.NEUTRAL, 0.35f, 0.6f, false);
            }
            BlockState suelo = mc.level.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
            if (!suelo.isAir() && tick % 2 == 0) {
                BlockParticleOption polvo = new BlockParticleOption(ParticleTypes.BLOCK, suelo);
                for (int i = 0; i < 2; i++) {
                    Vec3 r = c.add(derecha.scale(i == 0 ? 0.8 : -0.8)).add(adelante.scale(-0.9));
                    mc.level.addParticle(polvo, r.x, r.y + 0.1, r.z, -adelante.x * 0.1, 0.08, -adelante.z * 0.1);
                }
            }
        }
        if (tick == (int) (tLlegada * 20)) {
            mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 1.0f, 0.9f, false);
        }
        if (tick == (int) ((tLlegada + 0.8f) * 20)) {
            mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.NEUTRAL, 0.9f, 1.0f, false);
        }
        if (tick == 30 || tick == (int) ((tLlegada - 2.4f) * 20)) {
            mc.player.playSound(PoliciaSonido.anim("chime").get(), 0.6f, tick == 30 ? 0.8f : 1.1f);
        }
        if (t >= tFin) {
            despues = 200;
            terminar(mc, true);
        }
    }

    static void angulos(ViewportEvent.ComputeCameraAngles e) {
        if (!activo) {
            return;
        }
        float t = (tick + (float) e.getPartialTick()) / 20f;
        Vec3[] p = pose(t);
        Vec3 o = new Vec3(Mth.lerp((float) e.getPartialTick(), cam.xo, cam.getX()), Mth.lerp((float) e.getPartialTick(), cam.yo, cam.getY()), Mth.lerp((float) e.getPartialTick(), cam.zo, cam.getZ()));
        Vec3 d = p[1].subtract(o);
        float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
        float pitch = (float) (-Math.atan2(d.y, Math.hypot(d.x, d.z)) * 180 / Math.PI);
        e.setYaw(yaw);
        e.setPitch(pitch);
        // un balanceo leve de camara en mano en el travelling
        e.setRoll(toma(t) == 1 ? Mth.sin(t * 2.1f) * 0.8f : 0);
    }

    static void fov(ViewportEvent.ComputeFov e) {
        if (activo) {
            int k = toma(tick / 20f);
            e.setFOV(k == 0 ? 55 : k == 2 ? 45 : 60);
        }
    }

    static void quieto(MovementInputUpdateEvent e) {
        if (activo) {
            e.getInput().forwardImpulse = 0;
            e.getInput().leftImpulse = 0;
            e.getInput().up = false;
            e.getInput().down = false;
            e.getInput().left = false;
            e.getInput().right = false;
            e.getInput().jumping = false;
            e.getInput().shiftKeyDown = false;
        }
    }

    static void mano(RenderHandEvent e) {
        if (activo) {
            e.setCanceled(true);
        }
    }

    // el jugador no se ve hasta que baja del carruaje
    static void jugador(RenderPlayerEvent.Pre e) {
        if (activo && e.getEntity() == Minecraft.getInstance().player && tick / 20f < tLlegada + 1.4f) {
            e.setCanceled(true);
        }
    }

    static void ocultarHud(RenderGuiOverlayEvent.Pre e) {
        if (activo) {
            e.setCanceled(true);
        }
    }

    // ---------------- Carruaje en el mundo ----------------
    static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || (!activo && despues <= 0) || modelo == null || px == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float pt = e.getPartialTick();
        float t = (tick + pt) / 20f;
        float s = recorrido(t);
        float v = velocidad(t);
        Vec3 c = punto(s);
        float yaw = (float) (Math.atan2(-adelante.x, adelante.z) * 180 / Math.PI);
        float pend = pendiente(s);
        // animacion: trote si se mueve, quieto si no (con mezcla)
        long nn = System.nanoTime();
        float dt = nanos == 0 ? 0.016f : Mth.clamp((nn - nanos) / 1.0e9f, 0, 0.05f);
        nanos = nn;
        relojAnim += dt * Math.max(0.35f, v / VEL);
        float peso = Mth.clamp(v / 1.5f, 0, 1);
        float[] va = new float[9];
        float[] vb = new float[9];
        int nb = datos.huesos.length;
        for (int tb = 0; tb < nb; tb++) {
            muestra(0, relojAnim, tb, va);
            muestra(1, relojAnim, tb, vb);
            ModelPart p = modelo.partes[tb];
            float[] r = modelo.reposo[tb];
            float[] m = new float[9];
            for (int k = 0; k < 9; k++) {
                m[k] = va[k] + (vb[k] - va[k]) * peso;
            }
            p.x = r[0] + m[0];
            p.y = r[1] + m[1];
            p.z = r[2] + m[2];
            p.xRot = m[3];
            p.yRot = m[4];
            p.zRot = m[5];
            p.xScale = m[6];
            p.yScale = m[7];
            p.zScale = m[8];
        }
        for (int k : ruedas) {
            if (k >= 0) {
                modelo.partes[k].xRot = s / RADIO_RUEDA;
            }
        }
        // fisica de faroles, colas, crines y orejas
        final float esc = datos.escala;
        PoliciaCriaturasCliente.Render.resortes(modelo, datos, estado, desde -> {
            Matrix4f base = new Matrix4f();
            base.translate((float) c.x, (float) c.y, (float) c.z);
            base.rotateY((180f - yaw) * Mth.DEG_TO_RAD);
            base.rotateX(pend);
            base.scale(-1, -1, 1);
            base.scale(esc);
            base.translate(0, -1.501f, 0);
            PoliciaCriaturasCliente.Render.huesos(modelo, datos, estado, base, desde);
        }, dt);
        Vec3 cp = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(c.x - cp.x, c.y - cp.y, c.z - cp.z);
        ps.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        ps.mulPose(Axis.XP.rotation(pend));
        ps.scale(-1, -1, 1);
        ps.scale(esc, esc, esc);
        ps.translate(0, -1.501f, 0);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        int luz = LevelRenderer.getLightColor(mc.level, BlockPos.containing(c.x, c.y + 1.2, c.z));
        modelo.renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(TEX)), luz, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        modelo.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(BRILLO)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        ps.popPose();
        buf.endBatch();
    }

    static void muestra(int a, float t, int tb, float[] out) {
        float[][][] fr = datos.anims[a];
        int n = fr.length - 1;
        float f = n > 0 ? (t * 20f) % n : 0;
        int i = (int) Math.floor(f);
        int j = Math.min(n, i + 1);
        float k = f - i;
        float[] x = fr[i][tb];
        float[] y = fr[j][tb];
        for (int c = 0; c < 9; c++) {
            out[c] = x[c] + (y[c] - x[c]) * k;
        }
    }

    // ---------------- Franjas, fundidos y titulos ----------------
    static void hud(RenderGuiEvent.Post e) {
        if (!activo) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float t = (tick + e.getPartialTick()) / 20f;
        float barra = Math.min(Mth.clamp(t / 1.2f, 0, 1), Mth.clamp((tFin - t) / 0.8f, 0, 1));
        int alto = (int) (h * 0.12f * barra);
        g.fill(0, 0, w, alto, 0xFF000000);
        g.fill(0, h - alto, w, h, 0xFF000000);
        // fundido desde negro al empezar y a negro al bajarse
        float negro = 0;
        if (t < 1.4f && modo != 2) {
            negro = 1 - Mth.clamp((t - 0.2f) / 1.2f, 0, 1);
        }
        if (modo == 2) {
            negro = Mth.clamp((t - 3.9f) / 0.7f, 0, 1);
        } else if (t > tFin - 0.9f) {
            float u = (t - (tFin - 0.9f)) / 0.9f;
            negro = u < 0.5f ? u * 2 : 2 - u * 2;
        }
        if (negro > 0.01f) {
            g.fill(0, 0, w, h, ((int) (Mth.clamp(negro, 0, 1) * 255) << 24));
        }
        // titulos
        if (modo == 0) {
            titulo(g, mc, "OFICIOS", "Después de muchos días de camino...", t, 1.6f, 5.2f, w, h);
            if (!nombre.isEmpty()) {
                titulo(g, mc, nombre.toUpperCase(), "Tu nueva vida empieza acá", t, tLlegada - 2.6f, tLlegada + 1.2f, w, h);
            }
        } else if (modo == 1) {
            titulo(g, mc, nombre.toUpperCase(), "Llegaste en carruaje", t, tLlegada - 2.6f, tLlegada + 1.2f, w, h);
        } else {
            titulo(g, mc, "VIAJE A " + nombre.toUpperCase(), "Subís al carruaje y salen al trote", t, 0.4f, 4.0f, w, h);
        }
        if (t < tLlegada && modo != 2) {
            String s = saltar > 0 ? "Saltando... " + Math.min(100, saltar * 5) + "%" : "Mantené ESPACIO para saltar";
            g.drawString(mc.font, "§7" + s, w - mc.font.width(s) - 10, h - alto + Math.max(2, (alto - 9) / 2), 0xFFFFFF);
        }
    }

    static void titulo(GuiGraphics g, Minecraft mc, String grande, String chico, float t, float desde, float hasta, int w, int h) {
        if (t < desde || t > hasta) {
            return;
        }
        float a = Math.min(Mth.clamp((t - desde) / 0.8f, 0, 1), Mth.clamp((hasta - t) / 0.8f, 0, 1));
        int alfa = (int) (a * 255);
        if (alfa < 8) {
            return;
        }
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(w / 2f, h * 0.38f, 0);
        float esc = 3.0f + (t - desde) * 0.03f;
        ps.scale(esc, esc, 1);
        int ancho = mc.font.width(grande);
        g.drawString(mc.font, grande, -ancho / 2, 0, (alfa << 24) | 0xF3E7C9, true);
        ps.popPose();
        ps.pushPose();
        ps.translate(w / 2f, h * 0.38f + 34, 0);
        ps.scale(1.3f, 1.3f, 1);
        int a2 = mc.font.width(chico);
        g.drawString(mc.font, chico, -a2 / 2, 0, (alfa << 24) | 0xC9B98F, true);
        ps.popPose();
    }
}
