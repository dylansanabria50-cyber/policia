package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

// Etapa 3b: la pala cargadora del Albanil con el modelo de la vista previa
// (ruedas traseras grandes, cabina de vidrio, brazo de dos vigas con pala).
// Se dibuja en las mismas unidades que la vista previa y se escala con S
// para que entre en la caja del tractor y el asiento quede donde se sienta
// el jugador (positionRider en PoliciaTractor).
public class PoliciaTractorRender extends EntityRenderer<PoliciaTractor.TractorEntity> {
    static final int FULL = 0xF000F0;
    static final float S = 0.12f;
    // Corrimiento hacia atras para que el asiento quede en z = -0.25.
    static final float DZ = -0.73f;

    static final int[] AMA = {242, 184, 28};
    static final int[] AMA2 = {205, 150, 10};
    static final int[] OSC = {42, 42, 42};
    static final int[] GRIS = {120, 124, 132};

    public PoliciaTractorRender(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.9f;
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaTractor.TractorEntity e) {
        return PoliciaExtraRender.WHITE;
    }

    // Caja como en la vista previa: ancho, alto, largo y centro (x, y, z).
    static void bx(PoseStack ps, VertexConsumer vc, float w, float h, float d, float x, float y, float z, int[] c, int light) {
        PoliciaExtraRender.box(ps, vc, x - w / 2, y - h / 2, z - d / 2, x + w / 2, y + h / 2, z + d / 2, c[0], c[1], c[2], light);
    }

    static void bx(PoseStack ps, VertexConsumer vc, float w, float h, float d, float x, float y, float z, int r, int g, int b, int light) {
        PoliciaExtraRender.box(ps, vc, x - w / 2, y - h / 2, z - d / 2, x + w / 2, y + h / 2, z + d / 2, r, g, b, light);
    }

    // Caja semitransparente (vidrios de la cabina).
    static void glass(PoseStack ps, VertexConsumer vc, float w, float h, float d, float x, float y, float z, int light) {
        float x0 = x - w / 2, y0 = y - h / 2, z0 = z - d / 2, x1 = x + w / 2, y1 = y + h / 2, z1 = z + d / 2;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        q(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, light);
        q(vc, m, n, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, light);
        q(vc, m, n, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, light);
        q(vc, m, n, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, light);
    }

    static void q(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, int light) {
        v(vc, m, n, ax, ay, az, nx, ny, nz, light);
        v(vc, m, n, bx, by, bz, nx, ny, nz, light);
        v(vc, m, n, cx, cy, cz, nx, ny, nz, light);
        v(vc, m, n, dx, dy, dz, nx, ny, nz, light);
    }

    static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float nx, float ny, float nz, int light) {
        vc.vertex(m, x, y, z).color(150, 190, 225, 80).uv(0.5f, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    // Rueda de radio r y ancho 5 con la franja amarilla que muestra el giro.
    static void wheel(PoseStack ps, VertexConsumer vc, float r, float x, float y, float z, float spin, float steer, int light) {
        ps.pushPose();
        ps.translate(x, y, z);
        if (steer != 0.0f) {
            ps.mulPose(Axis.YP.rotationDegrees(steer));
        }
        ps.mulPose(Axis.XP.rotationDegrees(spin));
        float side = r * 0.83f;
        for (int k = 0; k < 4; ++k) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(k * 45.0f + 22.5f));
            bx(ps, vc, 5.0f, r * 2.0f, side, 0, 0, 0, (k & 1) == 0 ? 34 : 46, (k & 1) == 0 ? 34 : 46, (k & 1) == 0 ? 36 : 50, light);
            ps.popPose();
        }
        // tacos de la cubierta
        for (int k = 0; k < 8; ++k) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(k * 45.0f));
            bx(ps, vc, 5.4f, 1.0f, 1.6f, 0, r - 0.2f, 0, 26, 26, 28, light);
            ps.popPose();
        }
        bx(ps, vc, 5.2f, r * 0.9f, 2.0f, 0, 0, 0, AMA, light);
        bx(ps, vc, 5.6f, 2.2f, 2.2f, 0, 0, 0, GRIS, light);
        ps.popPose();
    }

    // Piston hidraulico entre dos puntos (y, z) a un costado x.
    static void strut(PoseStack ps, VertexConsumer vc, float x, float y0, float z0, float y1, float z1, int light) {
        float dy = y1 - y0;
        float dz = z1 - z0;
        float len = Mth.sqrt(dy * dy + dz * dz);
        float ang = (float) Math.toDegrees(Math.atan2(-dy, dz));
        ps.pushPose();
        ps.translate(x, y0, z0);
        ps.mulPose(Axis.XP.rotationDegrees(ang));
        PoliciaExtraRender.box(ps, vc, -0.5f, -0.5f, 0.0f, 0.5f, 0.5f, len, 165, 170, 178, light);
        PoliciaExtraRender.box(ps, vc, -0.8f, -0.8f, 0.0f, 0.8f, 0.8f, len * 0.55f, 40, 42, 48, light);
        ps.popPose();
    }

    @Override
    public void render(PoliciaTractor.TractorEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PoliciaExtraRender.WHITE));
        float hy = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float arm = Mth.lerp(pt, e.prevArm, e.arm);
        float spin = (float) Math.toDegrees(Mth.lerp(pt, e.prevWH, e.wh));
        float steer = Mth.lerp(pt, e.prevSteer, e.steer);
        boolean lamp = e.lampOn();
        int hl = lamp ? FULL : light;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-hy));
        ps.translate(0.0f, 0.0f, DZ);
        ps.scale(S, S, S);

        // ---- chasis y motor ----
        bx(ps, vc, 22, 10, 38, 0, 14, 0, AMA, light);
        bx(ps, vc, 22.4f, 1.2f, 38.4f, 0, 9.4f, 0, AMA2, light);
        bx(ps, vc, 18, 9, 13, 0, 23.5f, -12, AMA, light);
        bx(ps, vc, 18.4f, 1.0f, 13.4f, 0, 28.2f, -12, AMA2, light);
        // rejilla del motor y parrilla delantera
        for (int i = 0; i < 4; ++i) {
            bx(ps, vc, 0.6f, 6.0f, 9.0f, 9.3f, 23.5f, -12 - 3.0f + i * 2.0f, OSC, light);
            bx(ps, vc, 0.6f, 6.0f, 9.0f, -9.3f, 23.5f, -12 - 3.0f + i * 2.0f, OSC, light);
        }
        bx(ps, vc, 14, 6, 0.8f, 0, 14, 19.2f, OSC, light);
        // escape
        bx(ps, vc, 2, 12, 2, 6, 33, -15, OSC, light);
        bx(ps, vc, 2.6f, 1.2f, 2.6f, 6, 39.4f, -15, GRIS, light);
        // guardabarros traseros
        bx(ps, vc, 6, 1.2f, 20, 13, 19.2f, -10, AMA2, light);
        bx(ps, vc, 6, 1.2f, 20, -13, 19.2f, -10, AMA2, light);
        // luces traseras
        bx(ps, vc, 3, 2, 0.6f, 7, 16, -19.3f, 220, 20, 20, lamp ? FULL : light);
        bx(ps, vc, 3, 2, 0.6f, -7, 16, -19.3f, 220, 20, 20, lamp ? FULL : light);

        // ---- cabina ----
        float[][] posts = {{-10, -1}, {10, -1}, {-10, 12}, {10, 12}};
        for (float[] p : posts) {
            bx(ps, vc, 1.6f, 24, 1.6f, p[0], 31, p[1], AMA, light);
        }
        bx(ps, vc, 22, 2, 16, 0, 44, 5.5f, AMA, light);
        bx(ps, vc, 23, 0.8f, 17, 0, 45.2f, 5.5f, AMA2, light);
        // asiento, respaldo y volante
        bx(ps, vc, 8, 5, 7, 0, 21.5f, 4, OSC, light);
        bx(ps, vc, 8, 9, 1.6f, 0, 28, 0.6f, OSC, light);
        bx(ps, vc, 1, 7, 1, 0, 23, 10, OSC, light);
        ps.pushPose();
        ps.translate(0, 26.8f, 10.2f);
        ps.mulPose(Axis.XP.rotationDegrees(-35));
        bx(ps, vc, 5, 0.6f, 0.8f, 0, 0, 0, OSC, light);
        bx(ps, vc, 0.8f, 0.6f, 5, 0, 0, 0, OSC, light);
        ps.popPose();
        // baliza naranja del techo (titila)
        boolean blink = (e.tickCount / 6 & 1) == 0;
        bx(ps, vc, 2.2f, 2.0f, 2.2f, 0, 46.6f, 0, blink ? 255 : 120, blink ? 150 : 70, 10, blink ? FULL : light);
        // faros
        int fr = lamp ? 255 : 230, fg = lamp ? 246 : 222, fb = lamp ? 200 : 170;
        bx(ps, vc, 3, 2, 1, -7, 42, 13.6f, fr, fg, fb, hl);
        bx(ps, vc, 3, 2, 1, 7, 42, 13.6f, fr, fg, fb, hl);
        bx(ps, vc, 3, 2, 1, -7, 14, 19.4f, fr, fg, fb, hl);
        bx(ps, vc, 3, 2, 1, 7, 14, 19.4f, fr, fg, fb, hl);

        // ---- ruedas (grandes atras, chicas adelante que doblan) ----
        wheel(ps, vc, 9, -13, 9, -10, spin, 0.0f, light);
        wheel(ps, vc, 9, 13, 9, -10, spin, 0.0f, light);
        wheel(ps, vc, 7, -13, 7, 12, spin * 9.0f / 7.0f, steer, light);
        wheel(ps, vc, 7, 13, 7, 12, spin * 9.0f / 7.0f, steer, light);

        // ---- brazo y pala ----
        float phi = Mth.lerp(arm, 0.62f, -0.6f);
        float sinP = Mth.sin(phi);
        float cosP = Mth.cos(phi);
        ps.pushPose();
        ps.translate(0, 20, 8);
        ps.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(phi)));
        bx(ps, vc, 2, 2.5f, 28, -11, 0, 14, AMA, light);
        bx(ps, vc, 2, 2.5f, 28, 11, 0, 14, AMA, light);
        bx(ps, vc, 24, 2, 2, 0, 0, 20, AMA2, light);
        bx(ps, vc, 2.6f, 3.2f, 3.2f, -11, 0, 0, GRIS, light);
        bx(ps, vc, 2.6f, 3.2f, 3.2f, 11, 0, 0, GRIS, light);
        // la pala se mantiene casi derecha y se inclina hacia atras al subir
        ps.translate(0, 0, 28);
        ps.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(-phi) + Mth.lerp(arm, 0.0f, -22.0f)));
        bx(ps, vc, 28, 10, 1.5f, 0, 0, 0, OSC, light);
        bx(ps, vc, 28, 1.5f, 9, 0, -5, 4, OSC, light);
        bx(ps, vc, 1.5f, 10, 9, -14, 0, 4, OSC, light);
        bx(ps, vc, 1.5f, 10, 9, 14, 0, 4, OSC, light);
        for (int i = 0; i < 7; ++i) {
            bx(ps, vc, 1.6f, 1.2f, 2.0f, -12 + i * 4.0f, -5.2f, 9.2f, 150, 154, 162, light);
        }
        ps.popPose();
        // pistones del brazo
        for (int s = -1; s <= 1; s += 2) {
            strut(ps, vc, s * 9.0f, 12, 6, 20 - 14 * sinP, 8 + 14 * cosP, light);
        }

        // ---- vidrios (al final, semitransparentes) ----
        VertexConsumer gl = buf.getBuffer(RenderType.entityTranslucent(PoliciaExtraRender.WHITE));
        glass(ps, gl, 20, 20, 13, 0, 31, 5.5f, light);
        ps.popPose();
    }
}
