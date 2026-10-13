package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

// Dibuja el tanque del oficio Policia leyendo un modelo binario propio
// (assets/policia/tank/tank.bin) en vez de un modelo de bloques estandar.
public class PoliciaTankRender extends EntityRenderer<PoliciaTank.TankEntity> {
    static final ResourceLocation TEX = new ResourceLocation("policia", "textures/entity/tanque.png");
    static final ResourceLocation MODEL = new ResourceLocation("policia", "tank/tank.bin");
    static Part[] PARTS;
    static Part WHEEL;
    static float[] WHEELS = new float[0];

    public PoliciaTankRender(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.4f;
    }

    static Part readPart(DataInputStream in) throws IOException {
        Part p = new Part();
        int nv = in.readInt();
        p.v = new float[nv * 8];
        for (int i = 0; i < p.v.length; ++i) {
            p.v[i] = in.readFloat();
        }
        int ni = in.readInt();
        p.idx = new int[ni];
        for (int i = 0; i < ni; ++i) {
            p.idx[i] = in.readUnsignedShort();
        }
        return p;
    }

    static void load() {
        if (PARTS != null) {
            return;
        }
        Part[] ps = new Part[]{new Part(), new Part(), new Part()};
        Part wheel = new Part();
        float[] wh = new float[]{};
        try (InputStream raw = Minecraft.getInstance().getResourceManager().getResourceOrThrow(MODEL).open();
             DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
            for (int i = 0; i < 6; ++i) {
                in.readFloat();
            }
            for (int k = 0; k < 3; ++k) {
                ps[k] = readPart(in);
            }
            wheel = readPart(in);
            int n = in.readInt();
            wh = new float[n * 3];
            for (int i = 0; i < wh.length; ++i) {
                wh[i] = in.readFloat();
            }
        } catch (Exception exception) {
            // vacio: si falta el modelo, se dibuja con partes vacias
        }
        WHEEL = wheel;
        WHEELS = wh;
        PARTS = ps;
    }

    static void draw(Part p, PoseStack ps, VertexConsumer vc, int light) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int t = 0;
        while (t + 2 < p.idx.length) {
            vert(p, p.idx[t], m, n, vc, light);
            vert(p, p.idx[t + 1], m, n, vc, light);
            vert(p, p.idx[t + 2], m, n, vc, light);
            vert(p, p.idx[t + 2], m, n, vc, light);
            t += 3;
        }
    }

    static void vert(Part p, int i, Matrix4f m, Matrix3f n, VertexConsumer vc, int light) {
        int o = i * 8;
        vc.vertex(m, p.v[o], p.v[o + 1], p.v[o + 2]).color(255, 255, 255, 255).uv(p.v[o + 3], p.v[o + 4])
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, p.v[o + 5], p.v[o + 6], p.v[o + 7]).endVertex();
    }

    @Override
    public void render(PoliciaTank.TankEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        load();
        float hy = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float ty = Mth.lerp(pt, e.prevTurretYaw, e.turretYaw);
        float pitch = Mth.lerp(pt, e.prevPitch, e.pitch);
        float wlDeg = (float) Math.toDegrees(Mth.lerp(pt, e.prevWL, e.wl));
        float wrDeg = (float) Math.toDegrees(Mth.lerp(pt, e.prevWR, e.wr));
        int fire = e.getEntityData().get(PoliciaTank.TankEntity.FIRE);
        float age = (float) e.tickCount + pt;
        float k = Mth.clamp(age / 8.0f, 0.0f, 1.0f);
        if (fire >= 54) {
            k = Math.min(k, Mth.clamp(((float) (62 - fire) - pt) / 8.0f, 0.0f, 1.0f));
        }
        float gr = 0.2f + 0.8f * k;
        float recoil = 0.0f;
        if (fire >= 12) {
            float t = (float) (fire - 12) + pt;
            recoil = t < 2.0f ? 1.6f * (t / 2.0f) : 1.6f * Math.max(0.0f, 1.0f - (t - 2.0f) / 14.0f);
        }
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - hy));
        ps.scale(0.96f * gr, 1.24f * gr, 0.96f * gr);
        draw(PARTS[0], ps, vc, light);
        int i = 0;
        while (i + 2 < WHEELS.length) {
            float cx = WHEELS[i];
            ps.pushPose();
            ps.translate(cx, WHEELS[i + 1], WHEELS[i + 2]);
            if (cx < 0.0f) {
                ps.mulPose(Axis.YP.rotationDegrees(180.0f));
                ps.mulPose(Axis.XP.rotationDegrees(wlDeg));
            } else {
                ps.mulPose(Axis.XP.rotationDegrees(-wrDeg));
            }
            draw(WHEEL, ps, vc, light);
            ps.popPose();
            i += 3;
        }
        ps.pushPose();
        ps.translate(PoliciaTank.PT[0], PoliciaTank.PT[1], PoliciaTank.PT[2]);
        ps.mulPose(Axis.YP.rotationDegrees(hy - ty));
        ps.translate(-PoliciaTank.PT[0], -PoliciaTank.PT[1], -PoliciaTank.PT[2]);
        draw(PARTS[1], ps, vc, light);
        ps.pushPose();
        ps.translate(PoliciaTank.PB[0], PoliciaTank.PB[1], PoliciaTank.PB[2]);
        ps.mulPose(Axis.XP.rotationDegrees(-pitch));
        ps.translate(0.0f, 0.0f, recoil);
        ps.translate(-PoliciaTank.PB[0], -PoliciaTank.PB[1], -PoliciaTank.PB[2]);
        draw(PARTS[2], ps, vc, light);
        ps.popPose();
        ps.popPose();
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaTank.TankEntity e) {
        return TEX;
    }

    static class Part {
        float[] v = new float[0];
        int[] idx = new int[0];

        Part() {
        }
    }
}
