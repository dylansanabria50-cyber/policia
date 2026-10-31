package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.joml.Quaternionf;

// Reproduce en el cliente las animaciones de PoliciaAnim (etapa 3).
public class PoliciaAnimCliente {
    // id de entidad -> {animacion, tick de inicio, ultimo tick procesado}
    static final Map<Integer, int[]> ACT = new HashMap<Integer, int[]>();
    static float shake;
    static final Random RND = new Random();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PoliciaAnimCliente::tick);
        MinecraftForge.EVENT_BUS.addListener(PoliciaAnimCliente::camara);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false, net.minecraftforge.client.event.ScreenEvent.Render.Pre.class, e -> enGui = true);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, true, net.minecraftforge.client.event.ScreenEvent.Render.Post.class, e -> enGui = false);
    }

    // ---------------- Vista previa en los arboles de habilidades ----------------
    // Mientras se dibuja una pantalla (enGui), el muñeco del jugador repite la
    // animacion de la habilidad que se esta mirando. Las particulas se dibujan
    // en 2D alrededor del muñeco (guiFx) y los sonidos suenan bajitos.
    static boolean enGui;
    static int prevIdx = -1;
    static long prevStart;
    static long prevPedido;
    static float prevUltT = -1.0f;
    static long gfxT;
    static final java.util.List<float[]> GFX = new java.util.ArrayList<float[]>();

    // La pantalla la llama en cada cuadro con la habilidad que muestra
    // (null = ninguna).
    static void preview(String id) {
        Integer i = id == null ? null : PoliciaAnim.IDX.get(id);
        int n = i == null ? -1 : i;
        long ahora = System.currentTimeMillis();
        if (n != prevIdx) {
            prevIdx = n;
            prevStart = ahora;
            prevUltT = -1.0f;
            GFX.clear();
        }
        prevPedido = ahora;
    }

    static boolean previewActivo(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return enGui && prevIdx >= 0 && e == mc.player && System.currentTimeMillis() - prevPedido < 400;
    }

    // Tiempo de la vista previa: la animacion y despues una pausa, en bucle.
    static float previewT() {
        PoliciaAnim.Def d = PoliciaAnim.DEFS.get(prevIdx);
        float t = (System.currentTimeMillis() - prevStart) / 50.0f;
        return t % (d.len + 18);
    }

    static int colorDe(net.minecraft.core.particles.ParticleOptions p) {
        var t = p.getType();
        if (t == net.minecraft.core.particles.ParticleTypes.FLAME || t == net.minecraft.core.particles.ParticleTypes.LAVA) {
            return 0xFFA040;
        }
        if (t == net.minecraft.core.particles.ParticleTypes.ENCHANT) {
            return 0xB070FF;
        }
        if (t == net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK) {
            return 0x80E0FF;
        }
        if (t == net.minecraft.core.particles.ParticleTypes.NOTE || t == net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER) {
            return 0x60F060;
        }
        if (t == net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE) {
            return 0x606060;
        }
        if (t == net.minecraft.core.particles.ParticleTypes.CLOUD || t == net.minecraft.core.particles.ParticleTypes.POOF || t == net.minecraft.core.particles.ParticleTypes.WHITE_ASH) {
            return 0xD0D0D0;
        }
        if (t == net.minecraft.core.particles.ParticleTypes.END_ROD) {
            return 0xFFFFD0;
        }
        return 0xFFFFFF;
    }

    // Particulas, sonidos y "temblor" de la vista previa. cx/pies: donde
    // estan los pies del muñeco; esc: pixeles por bloque.
    static void guiFx(net.minecraft.client.gui.GuiGraphics g, int cx, int pies, float esc) {
        long ahora = System.currentTimeMillis();
        float dt = gfxT == 0 ? 0.0f : Math.min(0.1f, (ahora - gfxT) / 1000.0f);
        gfxT = ahora;
        if (prevIdx >= 0 && ahora - prevPedido < 400) {
            PoliciaAnim.Def d = PoliciaAnim.DEFS.get(prevIdx);
            float t = previewT();
            if (t < prevUltT) {
                prevUltT = -1.0f;
            }
            for (PoliciaAnim.Cue c : d.cues) {
                if (c.t > prevUltT && c.t <= t) {
                    if (c.snd != null) {
                        try {
                            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(c.snd.get(), c.pitch, c.vol * 0.3f));
                        } catch (Throwable ignored) {
                        }
                    }
                    if (c.fx != null) {
                        int col = colorDe(c.fx);
                        int n = Math.min(24, c.n);
                        for (int i = 0; i < n; i++) {
                            float x;
                            float y;
                            float vx;
                            float vy;
                            if (c.ring) {
                                double a = i * Math.PI * 2.0 / n;
                                x = cx + (float) Math.cos(a) * c.spread * esc * 0.7f;
                                y = pies - c.up * esc + (float) Math.sin(a) * c.spread * esc * 0.18f;
                                vx = (float) Math.cos(a) * 25.0f;
                                vy = -8.0f;
                            } else {
                                x = cx + c.fwd * esc * 0.5f + (RND.nextFloat() - 0.5f) * 2.0f * c.spread * esc;
                                y = pies - c.up * esc + (RND.nextFloat() - 0.5f) * 2.0f * c.spread * esc;
                                vx = (RND.nextFloat() - 0.5f) * 60.0f * (0.3f + c.speed * 4.0f);
                                vy = -RND.nextFloat() * 40.0f * (0.3f + c.speed * 4.0f);
                            }
                            GFX.add(new float[]{x, y, vx, vy, 0.0f, 0.6f + RND.nextFloat() * 0.5f, col});
                        }
                    }
                }
            }
            prevUltT = t;
        }
        java.util.Iterator<float[]> it = GFX.iterator();
        while (it.hasNext()) {
            float[] p = it.next();
            p[4] += dt;
            if (p[4] >= p[5]) {
                it.remove();
                continue;
            }
            p[0] += p[2] * dt;
            p[1] += p[3] * dt;
            p[3] += 30.0f * dt;
            int a = (int) (255 * (1.0f - p[4] / p[5]));
            int col = a << 24 | ((int) p[6] & 0xFFFFFF);
            g.fill((int) p[0], (int) p[1], (int) p[0] + 2, (int) p[1] + 2, col);
        }
    }

    static void start(int entityId, int idx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || idx < 0 || idx >= PoliciaAnim.DEFS.size()) {
            return;
        }
        Entity e = mc.level.getEntity(entityId);
        if (e == null) {
            return;
        }
        ACT.put(entityId, new int[]{idx, e.tickCount, e.tickCount - 1});
    }

    static PoliciaAnim.Def def(Entity e) {
        if (previewActivo(e)) {
            return PoliciaAnim.DEFS.get(prevIdx);
        }
        int[] a = ACT.get(e.getId());
        return a == null ? null : PoliciaAnim.DEFS.get(a[0]);
    }

    static boolean activo(Entity e) {
        return PoliciaAnimCliente.time(e, (float) e.tickCount) >= 0.0f;
    }

    static float time(Entity e, float age) {
        if (previewActivo(e)) {
            float t = previewT();
            return t <= PoliciaAnim.DEFS.get(prevIdx).len ? t : -1.0f;
        }
        int[] a = ACT.get(e.getId());
        if (a == null) {
            return -1.0f;
        }
        PoliciaAnim.Def d = PoliciaAnim.DEFS.get(a[0]);
        float t = age - (float) a[1];
        if (t < 0.0f || t > (float) d.len || e.isRemoved()) {
            return -1.0f;
        }
        return t;
    }

    static float ease(float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        return k * k * (3.0f - 2.0f * k);
    }

    // Valor del canal c en el tiempo t. "actual" es el valor normal del
    // juego, que se usa donde la pose clave no dice nada (NaN).
    static float valor(PoliciaAnim.Def d, int c, float t, float actual) {
        PoliciaAnim.K a = d.keys.get(0);
        PoliciaAnim.K b = null;
        for (PoliciaAnim.K k : d.keys) {
            if (k.t <= t) {
                a = k;
            } else {
                b = k;
                break;
            }
        }
        float va = Float.isNaN(a.v[c]) ? actual : a.v[c];
        if (b == null) {
            return va;
        }
        float vb = Float.isNaN(b.v[c]) ? actual : b.v[c];
        float k = (t - (float) a.t) / Math.max(1.0f, (float) (b.t - a.t));
        return Mth.lerp(ease(k), va, vb);
    }

    // Lo llama el modelo del jugador en cada cuadro.
    static void pose(PlayerModel<?> m, AbstractClientPlayer e, float age) {
        float t = PoliciaAnimCliente.time(e, age);
        if (t < 0.0f) {
            return;
        }
        PoliciaAnim.Def d = def(e);
        m.rightArm.xRot = valor(d, 0, t, m.rightArm.xRot);
        m.rightArm.yRot = valor(d, 1, t, m.rightArm.yRot);
        m.rightArm.zRot = valor(d, 2, t, m.rightArm.zRot);
        m.leftArm.xRot = valor(d, 3, t, m.leftArm.xRot);
        m.leftArm.yRot = valor(d, 4, t, m.leftArm.yRot);
        m.leftArm.zRot = valor(d, 5, t, m.leftArm.zRot);
        m.head.xRot = valor(d, 6, t, m.head.xRot);
        m.head.yRot = valor(d, 7, t, m.head.yRot);
        m.rightLeg.xRot = valor(d, 8, t, m.rightLeg.xRot);
        m.leftLeg.xRot = valor(d, 9, t, m.leftLeg.xRot);
        m.hat.copyFrom(m.head);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    // Inclinacion, agachada y giro de todo el cuerpo (dentro del push de
    // PoliciaMovimiento.pre).
    static void root(PoseStack ps, Player p, float pt) {
        float t = PoliciaAnimCliente.time(p, (float) p.tickCount + pt);
        if (t < 0.0f) {
            return;
        }
        PoliciaAnim.Def d = def(p);
        float lean = valor(d, 10, t, 0.0f);
        float crouch = valor(d, 11, t, 0.0f);
        float spin = valor(d, 12, t, 0.0f);
        float yaw = (float) Math.toRadians(Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
        if (crouch != 0.0f) {
            ps.translate(0.0f, -crouch, 0.0f);
        }
        if (lean != 0.0f) {
            ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(lean), Mth.cos(yaw), 0.0f, Mth.sin(yaw)));
        }
        if (spin != 0.0f) {
            ps.mulPose(Axis.YP.rotationDegrees(-spin));
        }
    }

    // Particulas, sonidos y temblor en los momentos de cada animacion.
    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            if (mc.level == null) {
                ACT.clear();
            }
            return;
        }
        Iterator<Map.Entry<Integer, int[]>> it = ACT.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, int[]> en = it.next();
            int[] a = en.getValue();
            Entity ent = mc.level.getEntity(en.getKey());
            PoliciaAnim.Def d = PoliciaAnim.DEFS.get(a[0]);
            if (ent == null || ent.isRemoved()) {
                it.remove();
                continue;
            }
            int t = ent.tickCount - a[1];
            if (t > d.len + 2) {
                it.remove();
                continue;
            }
            for (PoliciaAnim.Cue c : d.cues) {
                if (c.t > a[2] && c.t <= t) {
                    PoliciaAnimCliente.fire(mc, ent, c);
                }
            }
            a[2] = t;
        }
        shake *= 0.8f;
    }

    static void fire(Minecraft mc, Entity ent, PoliciaAnim.Cue c) {
        float yaw = (float) Math.toRadians(ent instanceof Player pl ? pl.yBodyRot : ent.getYRot());
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double x = ent.getX() + fx * c.fwd;
        double y = ent.getY() + c.up;
        double z = ent.getZ() + fz * c.fwd;
        if (c.snd != null) {
            try {
                mc.level.playLocalSound(x, y, z, c.snd.get(), SoundSource.PLAYERS, c.vol, c.pitch, false);
            } catch (Throwable ignored) {
            }
        }
        if (c.fx != null) {
            for (int i = 0; i < c.n; i++) {
                if (c.ring) {
                    double an = i * Math.PI * 2.0 / c.n;
                    double rx = Math.cos(an) * c.spread;
                    double rz = Math.sin(an) * c.spread;
                    mc.level.addParticle(c.fx, ent.getX() + rx, y, ent.getZ() + rz, rx * 0.08, 0.02, rz * 0.08);
                } else {
                    double ox = (RND.nextDouble() - 0.5) * 2.0 * c.spread;
                    double oy = (RND.nextDouble() - 0.5) * 2.0 * c.spread;
                    double oz = (RND.nextDouble() - 0.5) * 2.0 * c.spread;
                    mc.level.addParticle(c.fx, x + ox, y + oy, z + oz, (RND.nextDouble() - 0.5) * c.speed * 2.0 + fx * c.speed, (RND.nextDouble() - 0.2) * c.speed * 2.0, (RND.nextDouble() - 0.5) * c.speed * 2.0 + fz * c.speed);
                }
            }
        }
        if (c.shake > 0.0f && ent == mc.player) {
            shake = Math.max(shake, c.shake);
        }
    }

    static void camara(ViewportEvent.ComputeCameraAngles e) {
        if (shake < 0.02f) {
            return;
        }
        float s = shake * 1.6f;
        e.setYaw(e.getYaw() + (RND.nextFloat() - 0.5f) * s);
        e.setPitch(e.getPitch() + (RND.nextFloat() - 0.5f) * s);
        e.setRoll(e.getRoll() + (RND.nextFloat() - 0.5f) * s * 0.6f);
    }

    // Objeto en la mano durante la animacion (escudo, radio, linterna...).
    public static class PropLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public PropLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer e, float limbSwing, float limbAmount, float pt, float ageInTicks, float headYaw, float headPitch) {
            if (PoliciaAnimCliente.time(e, ageInTicks) < 0.0f) {
                return;
            }
            PoliciaAnim.Def d = def(e);
            if (d == null) {
                return;
            }
            if (d.propR != null && e.getMainHandItem().isEmpty()) {
                this.mano(ps, buf, light, e, d.propR.get(), true);
            }
            if (d.propL != null && e.getOffhandItem().isEmpty()) {
                this.mano(ps, buf, light, e, d.propL.get(), false);
            }
        }

        void mano(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer e, ItemStack st, boolean derecha) {
            PlayerModel<AbstractClientPlayer> m = this.getParentModel();
            ps.pushPose();
            if (derecha) {
                m.rightArm.translateAndRotate(ps);
            } else {
                m.leftArm.translateAndRotate(ps);
            }
            ps.mulPose(Axis.XP.rotationDegrees(-90.0f));
            ps.mulPose(Axis.YP.rotationDegrees(180.0f));
            ps.translate((derecha ? 1.0f : -1.0f) / 16.0f, 0.125f, -0.625f);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(e, st,
                    derecha ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, !derecha, ps, buf, light);
            ps.popPose();
        }
    }
}
