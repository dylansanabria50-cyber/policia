package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

// Animacion del Herrero Legendario sobre el modelo normal del jugador (sin
// GeckoLib y sin reemplazar su skin ni su armadura):
//  - kind 0 (Yunque Portatil / Guardian): estira el brazo hacia la
//    espalda, saca el yunque, lo baja con las dos manos y lo apoya.
//  - kind 1 (Reparacion Maestra): lo mismo, y despues tres martillazos
//    sobre el yunque con el Martillo del Herrero en la mano.
// El modelo (AnimModel) solo cambia los angulos de los brazos y la cabeza
// mientras dura la animacion; la capa (AnimLayer) dibuja el yunque y el
// martillo. Se ve en tercera persona (F5) y la ven los demas jugadores.
public class PoliciaHerreroAnim {
    static final Map<Integer, int[]> ACTIVE = new HashMap<Integer, int[]>();

    static void start(int entityId, int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity e = mc.level.getEntity(entityId);
        if (e == null) {
            return;
        }
        ACTIVE.put(entityId, new int[]{e.tickCount, kind});
    }

    static int length(int kind) {
        return kind == 1 ? PoliciaHerrero.ANIM_REPAIR : PoliciaHerrero.ANIM_PLACE;
    }

    // Tiempo de animacion en ticks (con fraccion), o -1 si no hay.
    static float time(Entity e, float ageInTicks) {
        int[] a = ACTIVE.get(e.getId());
        if (a == null) {
            return -1.0f;
        }
        float t = ageInTicks - (float) a[0];
        if (t < 0.0f || t > (float) PoliciaHerreroAnim.length(a[1]) || e.isRemoved()) {
            if (t > (float) PoliciaHerreroAnim.length(a[1]) + 5.0f || e.isRemoved()) {
                ACTIVE.remove(e.getId());
            }
            return -1.0f;
        }
        return t;
    }

    static int kind(Entity e) {
        int[] a = ACTIVE.get(e.getId());
        return a == null ? 0 : a[1];
    }

    static float ease(float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        return k * k * (3.0f - 2.0f * k);
    }

    static void pose(PlayerModel<?> m, float t, int kind) {
        if (t < 8.0f) {
            // Busca el yunque por arriba del hombro, en la espalda.
            float k = ease(t / 8.0f);
            m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -3.0f);
            m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, -0.3f);
            m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, -0.15f);
            m.head.xRot = Mth.lerp(k, m.head.xRot, -0.2f);
        } else if (t < 16.0f) {
            // Lo trae adelante y lo baja con las dos manos.
            float k = ease((t - 8.0f) / 8.0f);
            m.rightArm.xRot = Mth.lerp(k, -3.0f, -0.75f);
            m.rightArm.yRot = Mth.lerp(k, -0.3f, 0.15f);
            m.rightArm.zRot = Mth.lerp(k, -0.15f, 0.0f);
            m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -0.75f);
            m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, -0.15f);
            m.head.xRot = Mth.lerp(k, -0.2f, 0.5f);
        } else if (kind == 1) {
            // Tres martillazos de 9 ticks: sube el martillo y lo baja de golpe.
            float c = ((t - 16.0f) % 9.0f) / 9.0f;
            float x;
            if (c < 0.6f) {
                x = Mth.lerp(ease(c / 0.6f), -0.6f, -2.7f);
            } else if (c < 0.8f) {
                float k = (c - 0.6f) / 0.2f;
                x = Mth.lerp(k * k, -2.7f, -0.45f);
            } else {
                x = -0.45f;
            }
            m.rightArm.xRot = x;
            m.rightArm.yRot = -0.1f;
            m.rightArm.zRot = 0.0f;
            // La izquierda sostiene la pieza sobre el yunque.
            m.leftArm.xRot = -0.8f;
            m.leftArm.yRot = 0.35f;
            m.head.xRot = 0.55f;
            m.head.yRot = 0.0f;
        } else {
            // Se endereza despues de apoyar el yunque.
            float k = ease((t - 16.0f) / 2.0f);
            m.rightArm.xRot = Mth.lerp(k, -0.75f, m.rightArm.xRot);
            m.leftArm.xRot = Mth.lerp(k, -0.75f, m.leftArm.xRot);
            m.head.xRot = Mth.lerp(k, 0.5f, m.head.xRot);
        }
    }

    public static class AnimModel extends PlayerModel<AbstractClientPlayer> {
        public AnimModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(AbstractClientPlayer e, float limbSwing, float limbAmount, float ageInTicks, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, ageInTicks, headYaw, headPitch);
            float t = PoliciaHerreroAnim.time(e, ageInTicks);
            if (t < 0.0f) {
                return;
            }
            PoliciaHerreroAnim.pose(this, t, PoliciaHerreroAnim.kind(e));
            this.rightSleeve.copyFrom(this.rightArm);
            this.leftSleeve.copyFrom(this.leftArm);
            this.hat.copyFrom(this.head);
        }
    }

    static void block(PoseStack ps, MultiBufferSource buf, int light, BlockState st, float s) {
        // El espacio del modelo tiene la Y invertida: se vuelve a dar vuelta
        // para que el bloque quede derecho, y se centra en X/Z.
        ps.scale(-s, -s, s);
        ps.translate(-0.5f, 0.0f, -0.5f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
    }

    public static class AnimLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public AnimLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer e, float limbSwing, float limbAmount, float pt, float ageInTicks, float headYaw, float headPitch) {
            float t = PoliciaHerreroAnim.time(e, ageInTicks);
            if (t < 0.0f) {
                return;
            }
            int kind = PoliciaHerreroAnim.kind(e);
            BlockState anvil = Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST);
            PlayerModel<AbstractClientPlayer> m = this.getParentModel();
            if (t < 8.0f) {
                // Yunque colgado en la espalda.
                ps.pushPose();
                m.body.translateAndRotate(ps);
                ps.translate(0.0f, 0.42f, 0.32f);
                PoliciaHerreroAnim.block(ps, buf, light, anvil, 0.42f);
                ps.popPose();
            } else if (t < 15.0f) {
                // En la mano, mientras lo baja.
                ps.pushPose();
                m.rightArm.translateAndRotate(ps);
                ps.translate(0.1f, 0.7f, 0.0f);
                PoliciaHerreroAnim.block(ps, buf, light, anvil, 0.45f);
                ps.popPose();
            } else if (kind == 1) {
                // Apoyado en el piso adelante (en Reparacion no queda un
                // bloque real: es solo la escena), y el martillo en la mano.
                ps.pushPose();
                ps.translate(0.0f, 1.5f, -0.95f);
                PoliciaHerreroAnim.block(ps, buf, light, anvil, 0.6f);
                ps.popPose();
                ps.pushPose();
                m.rightArm.translateAndRotate(ps);
                ps.mulPose(Axis.XP.rotationDegrees(-90.0f));
                ps.mulPose(Axis.YP.rotationDegrees(180.0f));
                ps.translate(1.0f / 16.0f, 0.125f, -0.625f);
                Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(e, new ItemStack(PoliciaPlaca.MARTILLO.get()), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buf, light);
                ps.popPose();
            }
        }
    }
}
