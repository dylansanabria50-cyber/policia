package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class PoliciaMercenarioRender extends HumanoidMobRenderer<PoliciaMercenario.Merc, PlayerModel<PoliciaMercenario.Merc>> {
    static final ResourceLocation[] TEX = {
            new ResourceLocation("policia", "textures/entity/pj_0.png"),
            new ResourceLocation("policia", "textures/entity/pj_1.png"),
            new ResourceLocation("policia", "textures/entity/pj_2.png"),
            new ResourceLocation("policia", "textures/entity/pj_3.png")};

    public PoliciaMercenarioRender(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaMercenario.Merc e) {
        return TEX[Math.floorMod(e.clase(), TEX.length)];
    }

    @Override
    public void render(PoliciaMercenario.Merc e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        this.model.rightArmPose = e.apunta() ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.ITEM;
        this.model.leftArmPose = e.apunta() ? HumanoidModel.ArmPose.BOW_AND_ARROW : e.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        super.render(e, yaw, pt, ps, buf, luz);
    }
}
