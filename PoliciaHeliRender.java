package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// Dibuja el helicoptero del oficio Policia a base de cajas (sin modelo
// externo), con luces, rotores animados y antena de radar.
public class PoliciaHeliRender extends EntityRenderer<PoliciaHeli.HeliEntity> {
    static final int FULL = 0xF000F0;
    static final int[] W = new int[]{232, 234, 240};
    static final int[] K = new int[]{34, 36, 42};
    static final int[] D = new int[]{66, 70, 80};
    static final int[] BL = new int[]{28, 52, 130};
    static final int[] GL = new int[]{40, 70, 110};
    static final int[] RED = new int[]{240, 30, 30};
    static final int[] RED_OFF = new int[]{100, 18, 18};
    static final int[] BLU = new int[]{60, 110, 255};
    static final int[] BLU_OFF = new int[]{22, 40, 100};
    static final int[] SCR1 = new int[]{70, 210, 255};
    static final int[] SCR2 = new int[]{255, 175, 40};
    static final int[] LAMP = new int[]{255, 255, 225};

    public PoliciaHeliRender(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.8f;
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaHeli.HeliEntity e) {
        return PoliciaExtraRender.WHITE;
    }

    static void b(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int[] c, int light) {
        PoliciaExtraRender.box(ps, vc, x0, y0, z0, x1, y1, z1, c[0], c[1], c[2], light);
    }

    @Override
    public void render(PoliciaHeli.HeliEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        int s;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() == e && mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PoliciaExtraRender.WHITE));
        float hy = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float rot = Mth.lerp(pt, e.prevRotor, e.rotor);
        float tilt = Mth.lerp(pt, e.prevTilt, e.tilt);
        float roll = Mth.lerp(pt, e.prevRoll, e.roll);
        int sh = e.getEntityData().get(PoliciaHeli.HeliEntity.SHRINK);
        float k = sh < 0 ? 1.0f : Mth.clamp(1.0f - ((float) sh + pt) / 30.0f, 0.0f, 1.0f);
        boolean ph = (e.tickCount / 4 & 1) == 0;
        boolean blink = (e.tickCount / 10 & 1) == 0;
        ps.pushPose();
        ps.scale(k, k, k);
        ps.mulPose(Axis.YP.rotationDegrees(-hy));
        ps.translate(0.0f, 2.0f, 0.0f);
        ps.mulPose(Axis.XP.rotationDegrees(tilt));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.translate(0.0f, -2.0f, 0.0f);
        for (s = -1; s <= 1; s += 2) {
            float x = (float) s * 1.05f;
            b(ps, vc, x - 0.07f, 0.0f, -2.3f, x + 0.07f, 0.14f, 2.7f, K, light);
            b(ps, vc, x - 0.05f, 0.14f, -1.2f, x + 0.05f, 0.5f, -1.05f, K, light);
            b(ps, vc, x - 0.05f, 0.14f, 1.1f, x + 0.05f, 0.5f, 1.25f, K, light);
        }
        b(ps, vc, -1.25f, 0.4f, -2.0f, 1.25f, 0.75f, 2.0f, K, light);
        b(ps, vc, -1.25f, 0.75f, -2.0f, 1.25f, 1.0f, 2.0f, W, light);
        for (s = -1; s <= 1; s += 2) {
            float xa = (float) s * 1.15f;
            float xb = (float) s * 1.25f;
            float xc = (float) s * 1.26f;
            b(ps, vc, Math.min(xa, xb), 1.0f, -2.0f, Math.max(xa, xb), 1.7f, 1.9f, W, light);
            b(ps, vc, Math.min(xb, xc), 1.25f, -1.9f, Math.max(xb, xc), 1.4f, 1.6f, BL, light);
            b(ps, vc, Math.min(xa, xb), 1.7f, 1.8f, Math.max(xa, xb), 3.1f, 1.95f, W, light);
            b(ps, vc, Math.min(xa, xb), 1.7f, -1.0f, Math.max(xa, xb), 3.1f, -0.85f, W, light);
        }
        b(ps, vc, -1.25f, 1.0f, -2.0f, 1.25f, 3.1f, -1.85f, W, light);
        b(ps, vc, -1.25f, 3.1f, -2.0f, 1.25f, 3.25f, 2.0f, W, light);
        b(ps, vc, -0.05f, 1.7f, 1.9f, 0.05f, 3.1f, 2.0f, W, light);
        b(ps, vc, -1.25f, 1.0f, 1.85f, 1.25f, 1.7f, 2.05f, W, light);
        b(ps, vc, -1.0f, 0.4f, 2.0f, 1.0f, 1.5f, 3.1f, W, light);
        b(ps, vc, -0.85f, 1.5f, 2.0f, 0.85f, 1.8f, 2.7f, W, light);
        b(ps, vc, -0.9f, 0.3f, 2.0f, 0.9f, 0.45f, 3.15f, K, light);
        b(ps, vc, -0.8f, 0.8f, 3.1f, 0.8f, 1.3f, 3.12f, GL, light);
        b(ps, vc, -1.0f, 1.4f, 1.2f, 1.0f, 1.95f, 1.55f, D, light);
        b(ps, vc, -1.1f, 1.95f, 1.2f, 1.1f, 2.05f, 1.95f, K, light);
        b(ps, vc, -0.75f, 1.6f, 1.19f, -0.1f, 1.85f, 1.2f, SCR1, FULL);
        b(ps, vc, 0.1f, 1.6f, 1.19f, 0.75f, 1.85f, 1.2f, SCR2, FULL);
        b(ps, vc, -0.04f, 1.0f, 0.75f, 0.04f, 1.45f, 0.83f, K, light);
        b(ps, vc, -0.08f, 1.45f, 0.72f, 0.08f, 1.55f, 0.86f, D, light);
        b(ps, vc, 0.6f, 1.0f, -0.3f, 0.68f, 1.3f, 0.3f, K, light);
        b(ps, vc, -0.5f, 1.0f, -0.65f, 0.5f, 1.12f, 0.35f, D, light);
        b(ps, vc, -0.5f, 1.0f, -0.85f, 0.5f, 1.9f, -0.65f, D, light);
        b(ps, vc, -0.9f, 3.25f, 1.1f, -0.05f, 3.4f, 1.45f, ph ? RED : RED_OFF, ph ? FULL : light);
        b(ps, vc, 0.05f, 3.25f, 1.1f, 0.9f, 3.4f, 1.45f, ph ? BLU_OFF : BLU, ph ? light : FULL);
        b(ps, vc, -1.05f, 1.2f, 2.5f, -0.95f, 1.45f, 2.8f, ph ? RED : RED_OFF, ph ? FULL : light);
        b(ps, vc, 0.95f, 1.2f, 2.5f, 1.05f, 1.45f, 2.8f, ph ? BLU_OFF : BLU, ph ? light : FULL);
        b(ps, vc, -0.25f, 0.15f, 2.35f, 0.25f, 0.4f, 2.85f, D, light);
        b(ps, vc, -0.18f, 0.17f, 2.85f, 0.18f, 0.37f, 2.9f, LAMP, FULL);
        b(ps, vc, -0.95f, 3.25f, -1.8f, 0.95f, 3.85f, 0.3f, W, light);
        b(ps, vc, -0.7f, 3.45f, -2.1f, -0.35f, 3.7f, -1.8f, K, light);
        b(ps, vc, 0.35f, 3.45f, -2.1f, 0.7f, 3.7f, -1.8f, K, light);
        b(ps, vc, -0.96f, 3.4f, -1.4f, 0.96f, 3.55f, -0.3f, BL, light);
        b(ps, vc, -0.4f, 2.35f, -3.8f, 0.4f, 3.05f, -2.0f, W, light);
        b(ps, vc, -0.28f, 2.45f, -5.8f, 0.28f, 2.95f, -3.8f, W, light);
        b(ps, vc, -0.41f, 2.6f, -3.8f, 0.41f, 2.75f, -2.0f, BL, light);
        b(ps, vc, -0.08f, 2.95f, -6.2f, 0.08f, 4.2f, -5.4f, W, light);
        b(ps, vc, -0.9f, 2.6f, -5.7f, 0.9f, 2.66f, -5.2f, W, light);
        b(ps, vc, -0.09f, 4.2f, -6.2f, 0.09f, 4.3f, -6.05f, blink ? RED : RED_OFF, blink ? FULL : light);
        ps.pushPose();
        ps.translate(0.22f, 3.4f, -5.95f);
        ps.mulPose(Axis.XP.rotationDegrees(rot * 1.5f));
        b(ps, vc, -0.03f, -0.55f, -0.06f, 0.03f, 0.55f, 0.06f, K, light);
        b(ps, vc, -0.03f, -0.06f, -0.55f, 0.03f, 0.06f, 0.55f, K, light);
        ps.popPose();
        b(ps, vc, -0.1f, 3.85f, -0.65f, 0.1f, 4.1f, -0.45f, D, light);
        ps.pushPose();
        ps.translate(0.0f, 4.12f, -0.55f);
        ps.mulPose(Axis.YP.rotationDegrees(rot));
        b(ps, vc, -0.3f, -0.05f, -0.3f, 0.3f, 0.12f, 0.3f, D, light);
        b(ps, vc, -4.6f, 0.0f, -0.2f, 4.6f, 0.07f, 0.2f, K, light);
        b(ps, vc, -0.2f, 0.0f, -4.6f, 0.2f, 0.07f, 4.6f, K, light);
        b(ps, vc, -4.6f, 0.0f, -0.2f, -4.0f, 0.07f, 0.2f, RED, light);
        b(ps, vc, 4.0f, 0.0f, -0.2f, 4.6f, 0.07f, 0.2f, RED, light);
        b(ps, vc, -0.2f, 0.0f, -4.6f, 0.2f, 0.07f, -4.0f, RED, light);
        b(ps, vc, -0.2f, 0.0f, 4.0f, 0.2f, 0.07f, 4.6f, RED, light);
        ps.popPose();
        ps.popPose();
    }
}
