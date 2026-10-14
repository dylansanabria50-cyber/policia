package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

// Aprendiz del herrero: modelo de jugador con capas exteriores (pañuelo,
// delantal de cuero, brazalete y rodilleras en relieve) y martillo en mano.
public class PoliciaAprendizRender extends HumanoidMobRenderer<PoliciaAprendiz.Apprentice, PlayerModel<PoliciaAprendiz.Apprentice>> {
    static final ResourceLocation TEX = new ResourceLocation("policia", "textures/entity/aprendiz.png");

    public PoliciaAprendizRender(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaAprendiz.Apprentice e) {
        return TEX;
    }

    @Override
    public void render(PoliciaAprendiz.Apprentice e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        this.model.rightArmPose = HumanoidModel.ArmPose.ITEM;
        this.model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        this.model.setAllVisible(true);
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    protected void scale(PoliciaAprendiz.Apprentice e, PoseStack ps, float pt) {
        ps.scale(0.9375f, 0.9375f, 0.9375f);
    }
}
