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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

// Renderizador por cajas de los NPC de mision (civiles, bandidos, jefes,
// rehenes) y de la furgoneta (kind==6); todos los demas kind usan el
// renderer de skin de jugador (PoliciaMisionSkinRender).
public class PoliciaMisionRender extends EntityRenderer<PoliciaMision.Pj> {
    static final int[] SKIN = new int[]{232, 190, 150};
    PoliciaMisionSkinRender skin;
    static final int[] BLACK = new int[]{22, 22, 26};
    static final int[] BROWN = new int[]{110, 75, 40};
    static final int[] GOLD = new int[]{230, 190, 40};
    static final int[] GREY = new int[]{95, 95, 100};

    public PoliciaMisionRender(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.45f;
        this.skin = new PoliciaMisionSkinRender(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaMision.Pj e) {
        return PoliciaExtraRender.WHITE;
    }

    static void b(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int[] c, int light) {
        PoliciaExtraRender.box(ps, vc, x0, y0, z0, x1, y1, z1, c[0], c[1], c[2], light);
    }

    static int[] shirt(int k) {
        switch (k) {
            case 0: {
                return new int[]{30, 30, 34};
            }
            case 1: {
                return new int[]{170, 40, 40};
            }
            case 2: {
                return new int[]{60, 120, 60};
            }
            case 3: {
                return new int[]{110, 50, 150};
            }
            case 4: {
                return new int[]{25, 25, 28};
            }
            case 5: {
                return new int[]{70, 85, 50};
            }
            case 7: {
                return new int[]{230, 120, 20};
            }
            case 8: {
                return new int[]{235, 120, 20};
            }
            case 9: {
                return new int[]{130, 90, 60};
            }
            case 10: {
                return new int[]{30, 45, 95};
            }
            case 11: {
                return new int[]{50, 20, 70};
            }
        }
        return new int[]{100, 100, 100};
    }

    static int[] pants(int k) {
        switch (k) {
            case 0: {
                return new int[]{40, 40, 48};
            }
            case 1: {
                return new int[]{50, 50, 60};
            }
            case 2: {
                return new int[]{80, 60, 40};
            }
            case 3: {
                return new int[]{60, 30, 90};
            }
            case 4: {
                return new int[]{15, 15, 18};
            }
            case 5: {
                return new int[]{60, 70, 45};
            }
            case 7: {
                return new int[]{90, 90, 95};
            }
            case 8: {
                return new int[]{235, 120, 20};
            }
            case 9: {
                return new int[]{100, 70, 45};
            }
            case 10: {
                return new int[]{22, 34, 75};
            }
            case 11: {
                return new int[]{20, 10, 30};
            }
        }
        return new int[]{60, 60, 60};
    }

    @Override
    public void render(PoliciaMision.Pj e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PoliciaExtraRender.WHITE));
        int k = e.kind();
        if (k != 6 && k != 9) {
            this.skin.render((Mob) e, yaw, pt, ps, buf, light);
            return;
        }
        float body = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-body));
        if (e.deathTime > 0) {
            float f = Math.min(1.0f, ((float) e.deathTime + pt - 1.0f) / 20.0f * 1.6f);
            ps.mulPose(Axis.XP.rotationDegrees(f * 90.0f));
        }
        if (k == 6) {
            this.van(ps, vc, e, pt, light);
        } else {
            this.human(ps, vc, e, k, pt, light);
        }
        ps.popPose();
        super.render((Entity) e, yaw, pt, ps, buf, light);
    }

    void human(PoseStack ps, VertexConsumer vc, PoliciaMision.Pj e, int k, float pt, int light) {
        if (k == 4) {
            ps.scale(1.2f, 1.2f, 1.2f);
        } else if (k == 11) {
            ps.scale(1.3f, 1.3f, 1.3f);
        }
        float pos = e.walkAnimation.position(pt);
        float spd = Math.min(1.0f, e.walkAnimation.speed(pt) * 1.6f);
        float sw = Mth.cos(pos * 0.6662f) * 55.0f * spd;
        int[] sh = PoliciaMisionRender.shirt(k);
        int[] pa = PoliciaMisionRender.pants(k);
        boolean aim = e.isAggressive() && k != 9 && k != 10;
        boolean tied = k == 9 && e.captive();
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate((float) s * 0.125f, 0.75f, 0.0f);
            ps.mulPose(Axis.XP.rotationDegrees((float) s * sw));
            PoliciaMisionRender.b(ps, vc, -0.125f, -0.75f, -0.125f, 0.125f, 0.0f, 0.125f, pa, light);
            PoliciaMisionRender.b(ps, vc, -0.13f, -0.75f, -0.13f, 0.13f, -0.6f, 0.17f, BLACK, light);
            ps.popPose();
        }
        PoliciaMisionRender.b(ps, vc, -0.25f, 0.75f, -0.125f, 0.25f, 1.5f, 0.125f, sh, light);
        float atk = e.getAttackAnim(pt);
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate((float) s * 0.375f, 1.4f, 0.0f);
            float ang = tied ? 12.0f : (aim ? -90.0f + (s > 0 ? -Mth.sin(atk * 3.1416f) * 50.0f : 0.0f) : (k == 10 ? (s > 0 ? 0.0f : 0.0f) : (float) (-s) * sw * 0.8f));
            ps.mulPose(Axis.XP.rotationDegrees(ang));
            PoliciaMisionRender.b(ps, vc, -0.125f, -0.65f, -0.125f, 0.125f, 0.1f, 0.125f, sh, light);
            PoliciaMisionRender.b(ps, vc, -0.115f, -0.65f, -0.115f, 0.115f, -0.45f, 0.115f, SKIN, light);
            if (s > 0) {
                if (k == 1 || k == 3) {
                    PoliciaMisionRender.b(ps, vc, -0.06f, -0.95f, -0.06f, 0.06f, -0.45f, 0.06f, BROWN, light);
                }
                if (k == 4) {
                    PoliciaMisionRender.b(ps, vc, -0.07f, -1.0f, -0.07f, 0.07f, -0.45f, 0.07f, GREY, light);
                }
                if (k == 11) {
                    PoliciaMisionRender.b(ps, vc, -0.05f, -1.3f, -0.05f, 0.05f, -0.45f, 0.05f, new int[]{120, 40, 170}, light);
                }
                if (k == 7) {
                    PoliciaMisionRender.b(ps, vc, -0.14f, -0.85f, -0.14f, 0.14f, -0.57f, 0.14f, BLACK, light);
                    PoliciaMisionRender.b(ps, vc, -0.03f, -0.57f, -0.03f, 0.03f, -0.47f, 0.03f, new int[]{220, 40, 30}, light);
                }
            } else if (k == 2) {
                PoliciaMisionRender.b(ps, vc, -0.03f, -1.0f, -0.08f, 0.03f, -0.35f, 0.08f, BROWN, light);
            }
            ps.popPose();
        }
        ps.pushPose();
        float hy = Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        ps.translate(0.0f, 1.5f, 0.0f);
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.clamp(hy, -60.0f, 60.0f)));
        PoliciaMisionRender.b(ps, vc, -0.25f, 0.0f, -0.25f, 0.25f, 0.5f, 0.25f, SKIN, light);
        PoliciaMisionRender.b(ps, vc, -0.12f, 0.27f, 0.25f, -0.06f, 0.33f, 0.26f, BLACK, light);
        PoliciaMisionRender.b(ps, vc, 0.06f, 0.27f, 0.25f, 0.12f, 0.33f, 0.26f, BLACK, light);
        switch (k) {
            case 0: {
                PoliciaMisionRender.b(ps, vc, -0.26f, 0.22f, -0.26f, 0.26f, 0.4f, 0.26f, BLACK, light);
                break;
            }
            case 1: {
                PoliciaMisionRender.b(ps, vc, -0.26f, 0.38f, -0.26f, 0.26f, 0.5f, 0.26f, new int[]{190, 30, 30}, light);
                break;
            }
            case 2: {
                PoliciaMisionRender.b(ps, vc, -0.27f, 0.42f, -0.27f, 0.27f, 0.58f, 0.27f, new int[]{40, 90, 40}, light);
                break;
            }
            case 3: {
                PoliciaMisionRender.b(ps, vc, -0.26f, 0.5f, -0.26f, 0.26f, 0.6f, 0.26f, GOLD, light);
                PoliciaMisionRender.b(ps, vc, -0.2f, 0.6f, -0.2f, -0.12f, 0.72f, -0.12f, GOLD, light);
                PoliciaMisionRender.b(ps, vc, 0.12f, 0.6f, 0.12f, 0.2f, 0.72f, 0.2f, GOLD, light);
                break;
            }
            case 4: {
                PoliciaMisionRender.b(ps, vc, -0.4f, 0.5f, -0.4f, 0.4f, 0.55f, 0.4f, BLACK, light);
                PoliciaMisionRender.b(ps, vc, -0.26f, 0.55f, -0.26f, 0.26f, 0.8f, 0.26f, BLACK, light);
                PoliciaMisionRender.b(ps, vc, -0.26f, -0.05f, -0.26f, 0.26f, 0.12f, 0.26f, new int[]{170, 30, 30}, light);
                break;
            }
            case 5: {
                PoliciaMisionRender.b(ps, vc, -0.27f, 0.2f, -0.27f, 0.27f, 0.56f, 0.27f, new int[]{60, 75, 45}, light);
                PoliciaMisionRender.b(ps, vc, -0.2f, 0.2f, 0.26f, 0.2f, 0.4f, 0.27f, SKIN, light);
                break;
            }
            case 7: {
                PoliciaMisionRender.b(ps, vc, -0.26f, 0.4f, -0.26f, 0.26f, 0.55f, 0.26f, new int[]{240, 200, 30}, light);
                break;
            }
            case 8: {
                PoliciaMisionRender.b(ps, vc, -0.255f, 0.45f, -0.255f, 0.255f, 0.5f, 0.255f, new int[]{235, 120, 20}, light);
                break;
            }
            case 9: {
                PoliciaMisionRender.b(ps, vc, -0.05f, 0.12f, 0.25f, 0.05f, 0.3f, 0.4f, new int[]{190, 140, 110}, light);
                break;
            }
            case 10: {
                PoliciaMisionRender.b(ps, vc, -0.27f, 0.45f, -0.27f, 0.27f, 0.58f, 0.27f, new int[]{20, 30, 70}, light);
                PoliciaMisionRender.b(ps, vc, -0.27f, 0.42f, 0.2f, 0.27f, 0.47f, 0.5f, new int[]{20, 30, 70}, light);
                PoliciaMisionRender.b(ps, vc, -0.2f, 0.26f, 0.25f, 0.2f, 0.34f, 0.27f, BLACK, light);
                break;
            }
            case 11: {
                PoliciaMisionRender.b(ps, vc, -0.26f, 0.5f, -0.26f, 0.26f, 0.58f, 0.26f, GOLD, light);
                PoliciaMisionRender.b(ps, vc, -0.22f, 0.58f, -0.22f, -0.14f, 0.74f, -0.14f, GOLD, light);
                PoliciaMisionRender.b(ps, vc, 0.14f, 0.58f, -0.22f, 0.22f, 0.74f, -0.14f, GOLD, light);
                PoliciaMisionRender.b(ps, vc, -0.22f, 0.58f, 0.14f, -0.14f, 0.74f, 0.22f, GOLD, light);
                PoliciaMisionRender.b(ps, vc, 0.14f, 0.58f, 0.14f, 0.22f, 0.74f, 0.22f, GOLD, light);
                PoliciaMisionRender.b(ps, vc, -0.14f, 0.27f, 0.25f, -0.05f, 0.33f, 0.27f, new int[]{255, 60, 220}, light);
                PoliciaMisionRender.b(ps, vc, 0.05f, 0.27f, 0.25f, 0.14f, 0.33f, 0.27f, new int[]{255, 60, 220}, light);
                break;
            }
        }
        ps.popPose();
        if (k == 11) {
            PoliciaMisionRender.b(ps, vc, -0.3f, 0.2f, -0.2f, 0.3f, 1.5f, -0.13f, new int[]{90, 30, 130}, light);
            PoliciaMisionRender.b(ps, vc, -0.45f, 1.4f, -0.15f, -0.25f, 1.52f, 0.15f, GOLD, light);
            PoliciaMisionRender.b(ps, vc, 0.25f, 1.4f, -0.15f, 0.45f, 1.52f, 0.15f, GOLD, light);
        }
        switch (k) {
            case 0: {
                PoliciaMisionRender.b(ps, vc, -0.2f, 0.8f, -0.32f, 0.2f, 1.35f, -0.13f, new int[]{150, 110, 40}, light);
                break;
            }
            case 3: {
                PoliciaMisionRender.b(ps, vc, -0.27f, 0.3f, -0.2f, 0.27f, 1.5f, -0.13f, new int[]{60, 20, 90}, light);
                break;
            }
            case 4: {
                PoliciaMisionRender.b(ps, vc, -0.26f, 0.76f, 0.12f, -0.2f, 1.45f, 0.13f, GOLD, light);
                PoliciaMisionRender.b(ps, vc, 0.2f, 0.76f, 0.12f, 0.26f, 1.45f, 0.13f, GOLD, light);
                break;
            }
            case 5: {
                PoliciaMisionRender.b(ps, vc, 0.1f, 1.18f, 0.0f, 0.18f, 1.26f, 1.1f, BLACK, light);
                PoliciaMisionRender.b(ps, vc, 0.12f, 1.26f, 0.35f, 0.16f, 1.34f, 0.6f, GREY, light);
                break;
            }
            case 7: {
                PoliciaMisionRender.b(ps, vc, -0.26f, 0.76f, 0.12f, 0.26f, 1.45f, 0.13f, new int[]{240, 200, 30}, light);
                break;
            }
            case 8: {
                PoliciaMisionRender.b(ps, vc, -0.26f, 0.9f, -0.13f, 0.26f, 0.98f, 0.13f, BLACK, light);
                PoliciaMisionRender.b(ps, vc, -0.26f, 1.2f, -0.13f, 0.26f, 1.28f, 0.13f, BLACK, light);
                PoliciaMisionRender.b(ps, vc, -0.3f, 0.0f, 0.3f, -0.1f, 0.2f, 0.5f, new int[]{40, 40, 40}, light);
                break;
            }
            case 9: {
                if (!tied) break;
                PoliciaMisionRender.b(ps, vc, -0.27f, 1.1f, -0.14f, 0.27f, 1.2f, 0.14f, new int[]{200, 180, 120}, light);
                PoliciaMisionRender.b(ps, vc, -0.27f, 0.85f, -0.14f, 0.27f, 0.95f, 0.14f, new int[]{200, 180, 120}, light);
                break;
            }
            case 10: {
                PoliciaMisionRender.b(ps, vc, 0.08f, 1.2f, 0.12f, 0.2f, 1.35f, 0.14f, GOLD, light);
                PoliciaMisionRender.b(ps, vc, -0.04f, 1.0f, 0.12f, 0.04f, 1.5f, 0.14f, new int[]{235, 235, 240}, light);
                PoliciaMisionRender.b(ps, vc, -0.26f, 0.98f, -0.126f, 0.26f, 1.04f, 0.126f, BLACK, light);
                break;
            }
        }
    }

    void van(PoseStack ps, VertexConsumer vc, PoliciaMision.Pj e, float pt, int light) {
        float[][] w;
        int[] body = new int[]{60, 80, 70};
        int[] glass = new int[]{120, 170, 210};
        float pos = e.walkAnimation.position(pt) * 1.2f;
        PoliciaMisionRender.b(ps, vc, -0.95f, 0.4f, -1.5f, 0.95f, 1.8f, 0.5f, body, light);
        PoliciaMisionRender.b(ps, vc, -0.95f, 0.4f, 0.5f, 0.95f, 1.1f, 1.45f, new int[]{50, 68, 60}, light);
        PoliciaMisionRender.b(ps, vc, -0.85f, 1.1f, 0.5f, 0.85f, 1.65f, 0.55f, glass, light);
        PoliciaMisionRender.b(ps, vc, -0.85f, 1.1f, 0.5f, -0.5f, 1.7f, 0.5f, glass, light);
        PoliciaMisionRender.b(ps, vc, -0.9f, 0.45f, 1.45f, 0.9f, 0.7f, 1.55f, BLACK, light);
        PoliciaMisionRender.b(ps, vc, -0.8f, 0.6f, 1.45f, -0.55f, 0.8f, 1.56f, new int[]{255, 240, 160}, light);
        PoliciaMisionRender.b(ps, vc, 0.55f, 0.6f, 1.45f, 0.8f, 0.8f, 1.56f, new int[]{255, 240, 160}, light);
        PoliciaMisionRender.b(ps, vc, -0.9f, 0.9f, -1.52f, 0.9f, 1.0f, -1.5f, new int[]{150, 30, 30}, light);
        PoliciaMisionRender.b(ps, vc, -0.97f, 0.9f, -1.0f, -0.94f, 1.5f, 0.3f, new int[]{40, 55, 48}, light);
        PoliciaMisionRender.b(ps, vc, 0.94f, 0.9f, -1.0f, 0.97f, 1.5f, 0.3f, new int[]{40, 55, 48}, light);
        for (float[] ww : w = new float[][]{{-0.95f, 0.95f}, {0.95f, 0.95f}, {-0.95f, -0.95f}, {0.95f, -0.95f}}) {
            ps.pushPose();
            ps.translate(ww[0], 0.4f, ww[1]);
            ps.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(pos)));
            PoliciaMisionRender.b(ps, vc, -0.12f, -0.4f, -0.4f, 0.12f, 0.4f, 0.4f, new int[]{25, 25, 28}, light);
            PoliciaMisionRender.b(ps, vc, -0.14f, -0.15f, -0.15f, 0.14f, 0.15f, 0.15f, GREY, light);
            ps.popPose();
        }
    }
}
