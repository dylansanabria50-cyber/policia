package com.example.policia;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

// Renderer GeckoLib del prop del Yunque/Martillo (vease PoliciaForja).
public class PoliciaForjaRender extends GeoEntityRenderer<PoliciaForja.ForjaPropEntity> {
    public PoliciaForjaRender(EntityRendererProvider.Context ctx) {
        super(ctx, new PoliciaForja.ForjaPropModel());
        this.shadowRadius = 0.0f;
    }
}
