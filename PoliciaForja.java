package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.util.GeckoLibUtil;

// Prop decorativo del Herrero Legendario: el yunque que sale de la espalda,
// se planta en el piso, y el martillo que lo golpea forjando. Es un
// adorno que se spawnea un instante al usar Yunque Portatil, Yunque
// Guardian o Reparacion Maestra y se autodestruye solo al terminar la
// animacion; la mecanica real (buff, interceptar proyectiles, reparar el
// item) ya la aplica PoliciaHerrero por su cuenta, esto es puramente
// visual. Requiere la libreria GeckoLib instalada (ver mods.toml).
public class PoliciaForja {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create((IForgeRegistry<EntityType<?>>) ForgeRegistries.ENTITY_TYPES, "policia");
    public static final RegistryObject<EntityType<ForjaPropEntity>> PROP = ENTITIES.register("forja_prop",
            () -> EntityType.Builder.of(ForjaPropEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.2f).noSummon().noSave().fireImmune()
                    .clientTrackingRange(10).updateInterval(1).build("policia:forja_prop"));

    static void spawn(ServerLevel sl, BlockPos bp) {
        try {
            ForjaPropEntity e = new ForjaPropEntity(PROP.get(), sl);
            e.moveTo(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, 0.0f, 0.0f);
            sl.addFreshEntity(e);
        } catch (Throwable ignored) {
            // Si GeckoLib no esta instalado o algo de la animacion falla,
            // el resto de la habilidad (buff/reparacion/interceptar) ya
            // se aplico igual en PoliciaHerrero: esto es solo decoracion.
        }
    }

    public static class ForjaPropEntity extends Entity implements GeoEntity {
        static final int LIFE = 50;
        final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
        int age0;

        public ForjaPropEntity(EntityType<?> t, Level l) {
            super(t, l);
            this.noPhysics = true;
            this.setNoGravity(true);
        }

        @Override
        protected void defineSynchedData() {
        }

        @Override
        public void tick() {
            super.tick();
            if (this.level().isClientSide) {
                return;
            }
            if (++this.age0 > LIFE) {
                this.discard();
            }
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<ForjaPropEntity>(this, "principal", 0, state -> {
                state.getController().setAnimation(RawAnimation.begin().thenPlay("secuencia"));
                return PlayState.CONTINUE;
            }));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return this.geoCache;
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag t) {
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag t) {
        }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() {
            return NetworkHooks.getEntitySpawningPacket(this);
        }
    }

    public static class ForjaPropModel extends GeoModel<ForjaPropEntity> {
        @Override
        public ResourceLocation getModelResource(ForjaPropEntity e) {
            return new ResourceLocation("policia", "geo/policia_forjaprop.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(ForjaPropEntity e) {
            return new ResourceLocation("policia", "textures/entity/policia_forjaprop.png");
        }

        @Override
        public ResourceLocation getAnimationResource(ForjaPropEntity e) {
            return new ResourceLocation("policia", "animations/policia_forjaprop.animation.json");
        }
    }
}
