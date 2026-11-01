package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.UseAnim;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

// Codos y rodillas: cada brazo y pierna se dibuja como dos tramos unidos por
// una articulacion en el medio (como un modelo con huesos). La malla es
// continua: los vertices de la articulacion quedan a mitad del angulo, asi
// no se abre ningun hueco y la textura de la piel sigue igual. Lo mismo se
// hace con las mangas, los pantalones y la armadura; el objeto en la mano
// sigue al antebrazo (AnimModel.translateToHand).
//
// Angulos (radianes): rodilla positiva = la canilla va hacia atras; codo
// negativo = el antebrazo va hacia adelante.
public class PoliciaHuesos {
    // Doblez del jugador que se esta dibujando ahora (lo usa la armadura).
    static float curCodoD, curCodoI, curRodD, curRodI;
    static boolean curActivo;

    static final Map<Integer, float[]> SUAVE = new HashMap<Integer, float[]>();
    static final Map<Integer, Long> ULTIMO = new HashMap<Integer, Long>();
    static final Map<ModelPart, List<ModelPart.Cube>> CUBOS = new IdentityHashMap<ModelPart, List<ModelPart.Cube>>();

    static boolean activo() {
        return PoliciaConfig.articulaciones();
    }

    // ---------------- Cuanto se dobla cada articulacion ----------------
    // Se llama al final de setupAnim, con los brazos y piernas ya puestos.
    static void calcular(PoliciaHerreroAnim.AnimModel m, AbstractClientPlayer e, float limbSwing, float limbAmount, float age) {
        if (!activo() || e.isSleeping()) {
            m.codoD = m.codoI = m.rodD = m.rodI = 0.0f;
            curActivo = false;
            return;
        }
        float amt = Math.min(1.0f, limbAmount);
        float ph = limbSwing * 0.6662f;
        PoliciaMovimiento.St st = PoliciaMovimiento.ST.get(e.getId());
        float run = st != null ? st.run : e.isSprinting() ? 1.0f : 0.0f;
        float vy = st != null ? st.vy : (float) e.getDeltaMovement().y * 20.0f;
        boolean suelo = e.onGround();
        float rD;
        float rI;
        float cD;
        float cI;
        if (e.isPassenger()) {
            // Sentado: las canillas cuelgan y las manos van a las riendas.
            rD = rI = 1.35f;
            cD = cI = -0.55f;
        } else if (e.isSwimming() || e.isVisuallySwimming()) {
            rD = 0.25f + Math.max(0.0f, Mth.sin(age * 0.5f)) * 0.5f;
            rI = 0.25f + Math.max(0.0f, -Mth.sin(age * 0.5f)) * 0.5f;
            cD = cI = -0.35f;
        } else if (e.isFallFlying()) {
            rD = rI = 0.1f;
            cD = cI = -0.1f;
        } else if (e.onClimbable() && !suelo) {
            float s = Mth.sin(age * 0.45f);
            rD = 0.55f + s * 0.45f;
            rI = 0.55f - s * 0.45f;
            cD = -0.7f - s * 0.5f;
            cI = -0.7f + s * 0.5f;
        } else if (!suelo) {
            // En el aire: rodillas recogidas al subir; en la zancada del
            // salto corriendo, la pierna de atras mas doblada.
            if (vy > 1.0f && run > 0.3f) {
                rD = 0.45f;
                rI = 1.1f;
            } else if (vy > -6.0f) {
                rD = rI = 0.65f;
            } else {
                rD = rI = 0.3f;
            }
            cD = cI = -0.45f - 0.4f * run;
        } else {
            // En el piso: la rodilla se dobla cuando la pierna viene hacia
            // adelante (fase de vuelo del paso), mas al correr.
            float k = (0.5f + 0.75f * run) * amt;
            rD = 0.06f + 0.08f * amt + Math.max(0.0f, Mth.sin(ph)) * k;
            rI = 0.06f + 0.08f * amt + Math.max(0.0f, -Mth.sin(ph)) * k;
            // Codos: casi rectos al caminar, en 90 grados al correr; un poco
            // mas cuando el brazo va adelante.
            float base = 0.12f + 0.22f * amt + 1.05f * run * amt;
            cD = -(base + Math.max(0.0f, -m.rightArm.xRot) * 0.25f);
            cI = -(base + Math.max(0.0f, -m.leftArm.xRot) * 0.25f);
            if (e.isCrouching()) {
                rD += 0.55f;
                rI += 0.55f;
                cD -= 0.3f;
                cI -= 0.3f;
            }
            if (st != null && st.sq > 0.0f) {
                // aterrizaje: amortigua con las rodillas
                rD += st.sq * 3.0f;
                rI += st.sq * 3.0f;
            }
        }
        // Objeto en la mano (sin pelear): el brazo lo lleva un poco doblado.
        if (!e.getMainHandItem().isEmpty() && m.attackTime <= 0.0f && !e.isUsingItem()) {
            if (e.getMainArm() == HumanoidArm.RIGHT) {
                cD -= 0.15f;
            } else {
                cI -= 0.15f;
            }
        }
        // Pelea: el brazo que pega se carga doblado y se estira en el golpe;
        // el otro queda en guardia.
        if (m.attackTime > 0.0f) {
            float p = m.attackTime;
            float codo = p < 0.25f ? Mth.lerp(p / 0.25f, -0.6f, -1.5f) : p < 0.5f ? Mth.lerp((p - 0.25f) / 0.25f, -1.5f, -0.05f) : Mth.lerp((p - 0.5f) / 0.5f, -0.05f, -0.4f);
            boolean derecha = (e.swingingArm == net.minecraft.world.InteractionHand.MAIN_HAND) == (e.getMainArm() == HumanoidArm.RIGHT);
            if (derecha) {
                cD = codo;
                cI = Math.min(cI, -0.9f);
            } else {
                cI = codo;
                cD = Math.min(cD, -0.9f);
            }
        }
        // Usando un objeto: cubrirse con escudo, comer, tensar el arco...
        if (e.isUsingItem()) {
            UseAnim ua = e.getUseItem().getUseAnimation();
            boolean manoDerecha = (e.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND) == (e.getMainArm() == HumanoidArm.RIGHT);
            float usa;
            float otra;
            switch (ua) {
                case BLOCK -> {
                    usa = -1.35f;
                    otra = -1.0f;
                }
                case EAT, DRINK -> {
                    usa = -1.7f;
                    otra = -0.3f;
                }
                case BOW -> {
                    // el brazo que tensa la cuerda se dobla, el del arco recto
                    float tension = Math.min(1.0f, e.getTicksUsingItem() / 20.0f);
                    usa = -0.05f;
                    otra = -0.4f - 1.4f * tension;
                }
                case CROSSBOW -> {
                    usa = -0.6f;
                    otra = -1.1f;
                }
                case SPEAR -> {
                    usa = -0.35f;
                    otra = -0.5f;
                }
                case SPYGLASS -> {
                    usa = -1.9f;
                    otra = -0.3f;
                }
                default -> {
                    usa = -0.6f;
                    otra = -0.3f;
                }
            }
            if (manoDerecha) {
                cD = usa;
                cI = Math.min(cI, otra);
            } else {
                cI = usa;
                cD = Math.min(cD, otra);
            }
        }
        // Al recibir un golpe se cubre: brazos arriba y doblados un instante.
        if (e.hurtTime > 0) {
            float k = e.hurtTime / 10.0f;
            m.rightArm.xRot = Mth.lerp(k * 0.8f, m.rightArm.xRot, -1.6f);
            m.leftArm.xRot = Mth.lerp(k * 0.8f, m.leftArm.xRot, -1.6f);
            m.rightArm.yRot = Mth.lerp(k * 0.8f, m.rightArm.yRot, -0.35f);
            m.leftArm.yRot = Mth.lerp(k * 0.8f, m.leftArm.yRot, 0.35f);
            m.rightSleeve.copyFrom(m.rightArm);
            m.leftSleeve.copyFrom(m.leftArm);
            cD = Mth.lerp(k, cD, -1.7f);
            cI = Mth.lerp(k, cI, -1.7f);
        }
        // Suavizado por jugador (que no salte de golpe entre poses), igual a
        // cualquier cantidad de cuadros por segundo.
        if (SUAVE.size() > 256) {
            SUAVE.clear();
            ULTIMO.clear();
        }
        float[] sv = SUAVE.get(e.getId());
        long ahora = System.nanoTime();
        if (sv == null) {
            sv = new float[]{cD, cI, rD, rI, 0.0f};
            SUAVE.put(e.getId(), sv);
            ULTIMO.put(e.getId(), ahora);
        }
        Long antes = ULTIMO.put(e.getId(), ahora);
        float dt = antes == null ? 0.016f : Mth.clamp((ahora - antes) / 1.0E9f, 0.0f, 0.1f);
        float f = 1.0f - (float) Math.exp(-dt * (m.attackTime > 0.0f || e.hurtTime > 0 ? 30.0f : 16.0f));
        sv[0] += (cD - sv[0]) * f;
        sv[1] += (cI - sv[1]) * f;
        sv[2] += (rD - sv[2]) * f;
        sv[3] += (rI - sv[3]) * f;
        m.codoD = Mth.clamp(sv[0], -2.2f, 0.0f);
        m.codoI = Mth.clamp(sv[1], -2.2f, 0.0f);
        m.rodD = Mth.clamp(sv[2], 0.0f, 2.0f);
        m.rodI = Mth.clamp(sv[3], 0.0f, 2.0f);
        curCodoD = m.codoD;
        curCodoI = m.codoI;
        curRodD = m.rodD;
        curRodI = m.rodI;
        curActivo = true;
    }

    // ---------------- Dibujo de un miembro doblado ----------------
    static List<ModelPart.Cube> cubos(ModelPart p) {
        return CUBOS.computeIfAbsent(p, k -> {
            List<ModelPart.Cube> l = new ArrayList<ModelPart.Cube>();
            k.visit(new PoseStack(), (pose, path, idx, cube) -> l.add(cube));
            return l;
        });
    }

    // Caras de un cubo (posicion y UV de sus 4 vertices + normal). Minecraft
    // las tiene privadas: se leen una sola vez por reflexion y se guardan.
    static final class Cara {
        final float[][] v = new float[4][5];
        final Vector3f n = new Vector3f();
    }

    static final Map<ModelPart.Cube, List<Cara>> CARAS = new IdentityHashMap<ModelPart.Cube, List<Cara>>();

    static List<Cara> caras(ModelPart.Cube c) {
        return CARAS.computeIfAbsent(c, k -> {
            List<Cara> l = new ArrayList<Cara>();
            try {
                Object[] polys = null;
                for (java.lang.reflect.Field f : ModelPart.Cube.class.getDeclaredFields()) {
                    if (f.getType().isArray()) {
                        f.setAccessible(true);
                        polys = (Object[]) f.get(k);
                    }
                }
                if (polys == null) {
                    return l;
                }
                for (Object p : polys) {
                    Object[] verts = null;
                    Vector3f normal = null;
                    for (java.lang.reflect.Field f : p.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        if (f.getType().isArray()) {
                            verts = (Object[]) f.get(p);
                        } else if (f.getType() == Vector3f.class) {
                            normal = (Vector3f) f.get(p);
                        }
                    }
                    if (verts == null || verts.length != 4 || normal == null) {
                        continue;
                    }
                    Cara cara = new Cara();
                    cara.n.set(normal);
                    for (int i = 0; i < 4; i++) {
                        Object v = verts[i];
                        Vector3f pos = null;
                        float[] uv = new float[2];
                        int nf = 0;
                        for (java.lang.reflect.Field f : v.getClass().getDeclaredFields()) {
                            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                                continue;
                            }
                            f.setAccessible(true);
                            if (f.getType() == Vector3f.class) {
                                pos = (Vector3f) f.get(v);
                            } else if (f.getType() == float.class && nf < 2) {
                                uv[nf++] = f.getFloat(v);
                            }
                        }
                        if (pos == null) {
                            break;
                        }
                        cara.v[i][0] = pos.x();
                        cara.v[i][1] = pos.y();
                        cara.v[i][2] = pos.z();
                        cara.v[i][3] = uv[0];
                        cara.v[i][4] = uv[1];
                    }
                    l.add(cara);
                }
            } catch (Throwable ignored) {
            }
            return l;
        });
    }

    // true si se pudieron leer las caras (si no, se dibuja normal).
    static boolean listo(ModelPart p) {
        List<ModelPart.Cube> l = cubos(p);
        return !l.isEmpty() && caras(l.get(0)).size() >= 6;
    }

    static void miembro(ModelPart part, PoseStack ps, VertexConsumer vc, int luz, int overlay, float r, float g, float b, float a, float doblez) {
        if (!part.visible || part.skipDraw) {
            return;
        }
        ps.pushPose();
        part.translateAndRotate(ps);
        Matrix4f mat = ps.last().pose();
        Matrix3f nor = ps.last().normal();
        for (ModelPart.Cube c : cubos(part)) {
            float ym = (c.minY + c.maxY) * 0.5f;
            float zc = (c.minZ + c.maxZ) * 0.5f;
            for (Cara cara : caras(c)) {
                float[][] vs = cara.v;
                float y0 = vs[0][1];
                boolean tapa = Math.abs(vs[1][1] - y0) < 0.01f && Math.abs(vs[2][1] - y0) < 0.01f;
                if (tapa) {
                    float ang = y0 > ym ? doblez : 0.0f;
                    float[][] q = new float[4][];
                    for (int i = 0; i < 4; i++) {
                        q[i] = mover(vs[i][0], vs[i][1], vs[i][2], vs[i][3], vs[i][4], ym, zc, ang, y0 > ym ? 2 : 0);
                    }
                    emitir(vc, mat, nor, q, cara.n, ang, luz, overlay, r, g, b, a);
                    continue;
                }
                float[][] arriba = new float[4][];
                float[][] abajo = new float[4][];
                for (int i = 0; i < 4; i++) {
                    float[] v = vs[i];
                    float[] par = v;
                    for (float[] w : vs) {
                        if (w != v && Math.abs(w[0] - v[0]) < 0.01f && Math.abs(w[2] - v[2]) < 0.01f) {
                            par = w;
                        }
                    }
                    float dy = par[1] - v[1];
                    float t = Math.abs(dy) < 1.0E-4f ? 0.0f : (ym - v[1]) / dy;
                    float mu = v[3] + (par[3] - v[3]) * t;
                    float mv = v[4] + (par[4] - v[4]) * t;
                    float[] medio = mover(v[0], ym, v[2], mu, mv, ym, zc, doblez, 1);
                    if (v[1] < ym) {
                        arriba[i] = mover(v[0], v[1], v[2], v[3], v[4], ym, zc, doblez, 0);
                        abajo[i] = medio;
                    } else {
                        arriba[i] = medio;
                        abajo[i] = mover(v[0], v[1], v[2], v[3], v[4], ym, zc, doblez, 2);
                    }
                }
                emitir(vc, mat, nor, arriba, cara.n, doblez * 0.25f, luz, overlay, r, g, b, a);
                emitir(vc, mat, nor, abajo, cara.n, doblez * 0.75f, luz, overlay, r, g, b, a);
            }
        }
        ps.popPose();
    }

    // modo 0: tramo de arriba (sin girar); 1: articulacion (mitad del angulo,
    // manteniendo el grosor); 2: tramo de abajo (angulo completo).
    static float[] mover(float x, float y, float z, float u, float v, float ym, float zc, float ang, int modo) {
        float dy = y - ym;
        float dz = z - zc;
        if (modo == 1) {
            float tg = (float) Math.tan(ang * 0.5f);
            return new float[]{x, ym - dz * tg, zc + dz, u, v};
        }
        if (modo == 2) {
            float cs = Mth.cos(ang);
            float sn = Mth.sin(ang);
            return new float[]{x, ym + dy * cs - dz * sn, zc + dy * sn + dz * cs, u, v};
        }
        return new float[]{x, y, z, u, v};
    }

    static void emitir(VertexConsumer vc, Matrix4f mat, Matrix3f nor, float[][] q, Vector3f n0, float ang, int luz, int overlay, float r, float g, float b, float a) {
        float cs = Mth.cos(ang);
        float sn = Mth.sin(ang);
        Vector3f n = nor.transform(new Vector3f(n0.x(), n0.y() * cs - n0.z() * sn, n0.y() * sn + n0.z() * cs));
        for (float[] p : q) {
            Vector4f v = mat.transform(new Vector4f(p[0] / 16.0f, p[1] / 16.0f, p[2] / 16.0f, 1.0f));
            vc.vertex(v.x(), v.y(), v.z(), r, g, b, a, p[3], p[4], overlay, luz, n.x(), n.y(), n.z());
        }
    }

    // ---------------- Armadura que se dobla igual ----------------
    public static class ArmaduraModel extends HumanoidModel<LivingEntity> {
        public ArmaduraModel(ModelPart root) {
            super(root);
        }

        @Override
        public void renderToBuffer(PoseStack ps, VertexConsumer vc, int luz, int overlay, float r, float g, float b, float a) {
            if (!curActivo || !activo() || !listo(this.rightArm)) {
                super.renderToBuffer(ps, vc, luz, overlay, r, g, b, a);
                return;
            }
            boolean ad = this.rightArm.visible;
            boolean ai = this.leftArm.visible;
            boolean pd = this.rightLeg.visible;
            boolean pi = this.leftLeg.visible;
            this.rightArm.visible = false;
            this.leftArm.visible = false;
            this.rightLeg.visible = false;
            this.leftLeg.visible = false;
            super.renderToBuffer(ps, vc, luz, overlay, r, g, b, a);
            this.rightArm.visible = ad;
            this.leftArm.visible = ai;
            this.rightLeg.visible = pd;
            this.leftLeg.visible = pi;
            miembro(this.rightArm, ps, vc, luz, overlay, r, g, b, a, curCodoD);
            miembro(this.leftArm, ps, vc, luz, overlay, r, g, b, a, curCodoI);
            miembro(this.rightLeg, ps, vc, luz, overlay, r, g, b, a, curRodD);
            miembro(this.leftLeg, ps, vc, luz, overlay, r, g, b, a, curRodI);
        }
    }
}
