package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

public class PoliciaRefuerzoRender extends HumanoidMobRenderer<PoliciaRefuerzo.AgentEntity, PlayerModel<PoliciaRefuerzo.AgentEntity>> {
    static final ResourceLocation TEX = new ResourceLocation("policia", "textures/entity/policia.png");

    public PoliciaRefuerzoRender(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaRefuerzo.AgentEntity e) {
        return TEX;
    }

    @Override
    public void render(PoliciaRefuerzo.AgentEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        this.model.rightArmPose = HumanoidModel.ArmPose.ITEM;
        this.model.leftArmPose = e.isUsingItem() ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.ITEM;
        super.render(e, yaw, pt, ps, buf, light);
    }
}
