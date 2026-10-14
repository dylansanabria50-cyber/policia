package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// Patrullero blanco y negro con barra de luces roja y azul.
public class PoliciaPatrulleroRender extends EntityRenderer<PoliciaPatrullero.Car> {
    static final int FULL = 0xF000F0;

    public PoliciaPatrulleroRender(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaPatrullero.Car e) {
        return PoliciaExtraRender.WHITE;
    }

    static void b(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int r, int g, int bl, int light) {
        PoliciaExtraRender.box(ps, vc, x0, y0, z0, x1, y1, z1, r, g, bl, light);
    }

    static void wheel(PoseStack ps, VertexConsumer vc, float x, float z, float spin, float steer, int light) {
        ps.pushPose();
        ps.translate(x, 0.36f, z);
        if (steer != 0.0f) {
            ps.mulPose(Axis.YP.rotationDegrees(steer));
        }
        ps.mulPose(Axis.XP.rotationDegrees(spin));
        for (int k = 0; k < 4; ++k) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(k * 22.5f));
            b(ps, vc, -0.14f, -0.36f, -0.15f, 0.14f, 0.36f, 0.15f, 25, 25, 28, light);
            ps.popPose();
        }
        b(ps, vc, -0.16f, -0.16f, -0.16f, 0.16f, 0.16f, 0.16f, 170, 172, 178, light);
        ps.popPose();
    }

    @Override
    public void render(PoliciaPatrullero.Car e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PoliciaExtraRender.WHITE));
        float hy = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float spin = (float) Math.toDegrees(Mth.lerp(pt, e.prevWH, e.wh));
        float steer = Mth.lerp(pt, e.prevSteer, e.steer);
        boolean on = e.siren();
        boolean phase = (e.tickCount / 6) % 2 == 0;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-hy));
        // ruedas
        wheel(ps, vc, -0.95f, 1.45f, spin, -steer, light);
        wheel(ps, vc, 0.95f, 1.45f, spin, -steer, light);
        wheel(ps, vc, -0.95f, -1.35f, spin, 0.0f, light);
        wheel(ps, vc, 0.95f, -1.35f, spin, 0.0f, light);
        // chasis y carrocería (negro abajo, blanco en puertas, capó negro)
        b(ps, vc, -1.0f, 0.3f, -2.2f, 1.0f, 0.55f, 2.25f, 22, 22, 26, light);
        b(ps, vc, -1.02f, 0.55f, -2.2f, 1.02f, 1.05f, 2.25f, 236, 238, 242, light);
        b(ps, vc, -1.0f, 1.05f, 1.15f, 1.0f, 1.12f, 2.25f, 20, 20, 24, light);
        b(ps, vc, -1.0f, 1.05f, -2.2f, 1.0f, 1.12f, -1.5f, 20, 20, 24, light);
        b(ps, vc, -1.03f, 0.6f, -1.6f, -1.02f, 0.75f, 1.6f, 30, 70, 170, light);
        b(ps, vc, 1.02f, 0.6f, -1.6f, 1.03f, 0.75f, 1.6f, 30, 70, 170, light);
        // cabina con ventanas
        b(ps, vc, -0.95f, 1.05f, -1.45f, 0.95f, 1.12f, 1.1f, 236, 238, 242, light);
        b(ps, vc, -0.92f, 1.12f, 0.95f, 0.92f, 1.75f, 1.1f, 120, 170, 210, light);
        b(ps, vc, -0.92f, 1.12f, -1.45f, 0.92f, 1.75f, -1.3f, 120, 170, 210, light);
        b(ps, vc, -0.95f, 1.12f, -1.3f, -0.9f, 1.75f, 0.95f, 120, 170, 210, light);
        b(ps, vc, 0.9f, 1.12f, -1.3f, 0.95f, 1.75f, 0.95f, 120, 170, 210, light);
        b(ps, vc, -0.95f, 1.12f, -0.2f, -0.89f, 1.75f, -0.1f, 236, 238, 242, light);
        b(ps, vc, 0.89f, 1.12f, -0.2f, 0.95f, 1.75f, -0.1f, 236, 238, 242, light);
        b(ps, vc, -0.97f, 1.75f, -1.47f, 0.97f, 1.85f, 1.12f, 236, 238, 242, light);
        // barra de luces
        b(ps, vc, -0.7f, 1.85f, -0.25f, 0.7f, 1.9f, 0.15f, 30, 30, 34, light);
        b(ps, vc, -0.65f, 1.9f, -0.2f, -0.05f, 2.05f, 0.1f, on && phase ? 255 : 120, on && phase ? 30 : 20, on && phase ? 30 : 20, on && phase ? FULL : light);
        b(ps, vc, 0.05f, 1.9f, -0.2f, 0.65f, 2.05f, 0.1f, on && !phase ? 40 : 20, on && !phase ? 90 : 30, on && !phase ? 255 : 120, on && !phase ? FULL : light);
        // paragolpes, faros y luces traseras
        b(ps, vc, -1.0f, 0.3f, 2.25f, 1.0f, 0.55f, 2.35f, 40, 40, 44, light);
        b(ps, vc, -1.0f, 0.3f, -2.3f, 1.0f, 0.55f, -2.2f, 40, 40, 44, light);
        b(ps, vc, 0.55f, 0.7f, 2.25f, 0.9f, 0.88f, 2.28f, 255, 250, 210, FULL);
        b(ps, vc, -0.9f, 0.7f, 2.25f, -0.55f, 0.88f, 2.28f, 255, 250, 210, FULL);
        b(ps, vc, 0.55f, 0.7f, -2.23f, 0.9f, 0.88f, -2.2f, 200, 20, 20, light);
        b(ps, vc, -0.9f, 0.7f, -2.23f, -0.55f, 0.88f, -2.2f, 200, 20, 20, light);
        // asientos
        b(ps, vc, -0.85f, 0.55f, -1.2f, 0.85f, 0.75f, -0.4f, 50, 50, 55, light);
        b(ps, vc, 0.05f, 0.55f, 0.1f, 0.8f, 0.75f, 0.7f, 50, 50, 55, light);
        b(ps, vc, -0.8f, 0.55f, 0.1f, -0.05f, 0.75f, 0.7f, 50, 50, 55, light);
        // escudo azul en las puertas
        b(ps, vc, -1.04f, 0.78f, -0.15f, -1.03f, 0.98f, 0.25f, 30, 70, 170, light);
        b(ps, vc, 1.03f, 0.78f, -0.15f, 1.04f, 0.98f, 0.25f, 30, 70, 170, light);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }
}
