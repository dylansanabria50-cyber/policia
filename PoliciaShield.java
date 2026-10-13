package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

// Escudo balistico de Policia: item tipo escudo con modelo y textura
// propios, renderizado tanto en mano como de pie en el mundo.
public class PoliciaShield extends ShieldItem {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create((IForgeRegistry<Item>) ForgeRegistries.ITEMS, "policia");
    public static final RegistryObject<Item> SHIELD = ITEMS.register("escudo_policial", () -> new PoliciaShield(new Item.Properties().stacksTo(1)));

    public PoliciaShield(Item.Properties p) {
        super(p);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return Render.get();
            }
        });
    }

    static class Render extends BlockEntityWithoutLevelRenderer {
        static final ResourceLocation TEX = new ResourceLocation("policia", "textures/entity/escudo_policial.png");
        static Render INSTANCE;
        ShieldModel model;

        static Render get() {
            if (INSTANCE == null) {
                Minecraft mc = Minecraft.getInstance();
                INSTANCE = new Render(mc);
            }
            return INSTANCE;
        }

        Render(Minecraft mc) {
            super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }

        static void drawWorld(PoseStack ps, MultiBufferSource buf, int light) {
            Render r = Render.get();
            if (r.model == null) {
                r.model = new ShieldModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SHIELD));
            }
            VertexConsumer vc = buf.getBuffer(RenderType.entitySolid(TEX));
            r.model.plate().render(ps, vc, light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
            r.model.handle().render(ps, vc, light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            if (this.model == null) {
                this.model = new ShieldModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SHIELD));
            }
            ps.pushPose();
            ps.scale(1.0f, -1.0f, -1.0f);
            VertexConsumer vc = buf.getBuffer(RenderType.entitySolid(TEX));
            this.model.plate().render(ps, vc, light, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
            this.model.handle().render(ps, vc, light, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
            ps.popPose();
        }
    }
}
