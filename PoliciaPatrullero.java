package com.example.policia;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegistryObject;

// Patrullero de policía: auto rápido con sirena y luces (G), y un asiento
// trasero para llevar a un detenido esposado hasta la comisaría.
public class PoliciaPatrullero {
    public static final RegistryObject<EntityType<Car>> CAR = PoliciaRefuerzo.ENTITIES.register("patrullero", () -> EntityType.Builder.<Car>of(Car::new, MobCategory.MISC).sized(2.4f, 1.8f).fireImmune().clientTrackingRange(12).updateInterval(1).build("policia:patrullero"));
    public static final RegistryObject<Item> KEY = PoliciaShield.ITEMS.register("llave_patrullero", () -> new Key(new Item.Properties().stacksTo(1)));
    static final Map<UUID, Car> ACTIVE = new HashMap<UUID, Car>();
    static final double MAX = 0.62;

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaPatrullero());
    }

    static Car find(ServerPlayer p) {
        Car c = ACTIVE.get(p.getUUID());
        if (c != null && (c.isRemoved() || !c.isAlive())) {
            ACTIVE.remove(p.getUUID());
            c = null;
        }
        return c;
    }

    public static class Key extends Item {
        public Key(Item.Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack it, Level lv, List<Component> tip, TooltipFlag f) {
            tip.add(Component.literal("§7Clic derecho: llamar al patrullero"));
            tip.add(Component.literal("§7Mayús + clic derecho: guardarlo"));
            tip.add(Component.literal("§7Adentro: G = sirena y luces"));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack it = player.getItemInHand(hand);
            if (level.isClientSide || !(player instanceof ServerPlayer p)) {
                return InteractionResultHolder.sidedSuccess(it, level.isClientSide);
            }
            if (!"policia".equals(PoliciaMod.job(p))) {
                PoliciaMod.msg(p, "Solo un policía puede manejar el patrullero");
                return InteractionResultHolder.fail(it);
            }
            Car c = PoliciaPatrullero.find(p);
            if (p.isShiftKeyDown()) {
                if (c != null) {
                    c.ejectPassengers();
                    c.discard();
                    ACTIVE.remove(p.getUUID());
                    PoliciaMod.msg(p, "Patrullero guardado");
                }
                return InteractionResultHolder.success(it);
            }
            Vec3 look = p.getLookAngle();
            double x = p.getX() + look.x * 3.5;
            double z = p.getZ() + look.z * 3.5;
            if (c == null) {
                c = new Car(CAR.get(), level);
                c.owner = p.getUUID();
                level.addFreshEntity(c);
                ACTIVE.put(p.getUUID(), c);
            } else if (c.level() != level) {
                c.discard();
                c = new Car(CAR.get(), level);
                c.owner = p.getUUID();
                level.addFreshEntity(c);
                ACTIVE.put(p.getUUID(), c);
            } else if (c.isVehicle()) {
                PoliciaMod.msg(p, "El patrullero está ocupado");
                return InteractionResultHolder.fail(it);
            }
            double g = c.ground(x, z, p.getY() + 2.0);
            c.moveTo(x, g, z, p.getYRot(), 0.0f);
            level.playSound(null, x, g, z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0f, 0.8f);
            PoliciaMod.msg(p, "Patrullero listo. Clic derecho para subir");
            return InteractionResultHolder.success(it);
        }
    }

    // Un detenido esposado no se puede bajar solo del patrullero.
    @SubscribeEvent
    public void mount(EntityMountEvent e) {
        if (e.isDismounting() && e.getEntityBeingMounted() instanceof Car c && !c.releasing && !e.getLevel().isClientSide) {
            Entity r = e.getEntityMounting();
            if (!(r instanceof Player pl && c.owner != null && c.owner.equals(pl.getUUID())) && r.getPersistentData().getInt("pol_cuff") > 0 && c.isAlive()) {
                e.setCanceled(true);
            }
        }
    }

    static void siren(ServerPlayer p) {
        if (p.getVehicle() instanceof Car c) {
            boolean on = !c.siren();
            c.entityData.set(Car.SIREN, on);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.8f, on ? 1.4f : 0.9f);
            PoliciaMod.msg(p, on ? "Sirena y luces encendidas" : "Sirena apagada");
        }
    }

    public static class Car extends Entity {
        static final EntityDataAccessor<Boolean> SIREN = SynchedEntityData.defineId(Car.class, EntityDataSerializers.BOOLEAN);
        static final EntityDataAccessor<Float> WH = SynchedEntityData.defineId(Car.class, EntityDataSerializers.FLOAT);
        static final EntityDataAccessor<Float> STEER = SynchedEntityData.defineId(Car.class, EntityDataSerializers.FLOAT);
        UUID owner;
        double speed;
        boolean releasing;
        public float wh;
        public float prevWH;
        public float steer;
        public float prevSteer;
        double lx;
        double ly;
        double lz;
        float lyr;
        int lsteps;

        public Car(EntityType<? extends Car> t, Level l) {
            super(t, l);
            this.noPhysics = true;
            this.setNoGravity(true);
            this.noCulling = true;
        }

        @Override
        protected void defineSynchedData() {
            this.entityData.define(SIREN, false);
            this.entityData.define(WH, 0.0f);
            this.entityData.define(STEER, 0.0f);
        }

        public boolean siren() {
            return this.entityData.get(SIREN);
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag t) {
            if (t.hasUUID("owner")) {
                this.owner = t.getUUID("owner");
            }
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag t) {
            if (this.owner != null) {
                t.putUUID("owner", this.owner);
            }
        }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() {
            return NetworkHooks.getEntitySpawningPacket((Entity) this);
        }

        @Override
        public boolean isPickable() {
            return !this.isRemoved();
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public void push(double x, double y, double z) {
        }

        @Override
        public boolean hurt(DamageSource s, float a) {
            return false;
        }

        @Override
        public boolean isInvulnerableTo(DamageSource s) {
            return true;
        }

        @Override
        public boolean isControlledByLocalInstance() {
            return false;
        }

        @Override
        protected boolean canAddPassenger(Entity e) {
            return this.getPassengers().size() < 2;
        }

        @Override
        public double getPassengersRidingOffset() {
            return 0.45;
        }

        Entity driver() {
            for (Entity e : this.getPassengers()) {
                if (e instanceof Player p && this.owner != null && this.owner.equals(p.getUUID())) {
                    return e;
                }
            }
            return null;
        }

        @Override
        public LivingEntity getControllingPassenger() {
            Entity d = this.driver();
            return d instanceof LivingEntity le ? le : null;
        }

        @Override
        public void positionRider(Entity e, Entity.MoveFunction cb) {
            if (!this.hasPassenger(e)) {
                return;
            }
            boolean front = e == this.driver();
            double rad = Math.toRadians(this.getYRot());
            double fx = -Math.sin(rad);
            double fz = Math.cos(rad);
            double sx = Math.cos(rad);
            double sz = Math.sin(rad);
            double fwd = front ? 0.35 : -0.75;
            double side = front ? 0.42 : -0.1;
            cb.accept(e, this.getX() + fx * fwd + sx * side, this.getY() + this.getPassengersRidingOffset() + e.getMyRidingOffset(), this.getZ() + fz * fwd + sz * side);
        }

        @Override
        public Vec3 getDismountLocationForPassenger(LivingEntity p) {
            double rad = Math.toRadians(this.getYRot());
            double x = this.getX() + Math.cos(rad) * 2.0;
            double z = this.getZ() + Math.sin(rad) * 2.0;
            return new Vec3(x, this.ground(x, z, this.getY() + 2.0) + 0.05, z);
        }

        @Override
        public InteractionResult interact(Player pl, InteractionHand hand) {
            if (pl.isPassenger() || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (this.level().isClientSide || !(pl instanceof ServerPlayer sp)) {
                return InteractionResult.SUCCESS;
            }
            if (this.owner == null || !this.owner.equals(sp.getUUID())) {
                PoliciaMod.msg(sp, "Este patrullero es de otro policía");
                return InteractionResult.FAIL;
            }
            if (sp.isShiftKeyDown()) {
                // subir al detenido esposado que arrastrás, o bajarlo
                Entity back = null;
                for (Entity e : this.getPassengers()) {
                    if (e != this.driver()) back = e;
                }
                if (back != null) {
                    this.releasing = true;
                    back.stopRiding();
                    this.releasing = false;
                    PoliciaMod.msg(sp, "Bajaste al detenido");
                    return InteractionResult.SUCCESS;
                }
                List<LivingEntity> l = this.level().getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(10.0), x -> x != sp && x.getPersistentData().getInt("pol_cuff") > 0 && x.getPersistentData().hasUUID("pol_cuffo") && x.getPersistentData().getUUID("pol_cuffo").equals(sp.getUUID()));
                if (l.isEmpty()) {
                    PoliciaMod.msg(sp, "No tenés a nadie esposado cerca para subir atrás");
                    return InteractionResult.SUCCESS;
                }
                LivingEntity t = l.get(0);
                t.startRiding(this, true);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0f, 1.0f);
                PoliciaMod.msg(sp, "Subiste a " + t.getDisplayName().getString() + " al asiento de atrás. Llevalo a la comisaría");
                return InteractionResult.SUCCESS;
            }
            if (this.driver() == null) {
                sp.startRiding(this, true);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0f, 1.2f);
                PoliciaMod.msg(sp, "W/S: acelerar y frenar · A/D: girar · G: sirena · Mayús: bajarte");
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
            this.lx = x;
            this.ly = y;
            this.lz = z;
            this.lyr = yr;
            this.lsteps = 3;
        }

        double ground(double x, double z, double refY) {
            int bx = Mth.floor(x);
            int bz = Mth.floor(z);
            int top = Mth.floor(refY) + 2;
            int bot = Math.max(Mth.floor(refY) - 12, this.level().getMinBuildHeight());
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int y = top; y >= bot; --y) {
                mp.set(bx, y, bz);
                BlockState bs = this.level().getBlockState(mp);
                if (bs.is(BlockTags.LEAVES)) continue;
                FluidState fs = bs.getFluidState();
                if (!fs.isEmpty()) {
                    return -1.0E9;
                }
                VoxelShape sh = bs.getCollisionShape((BlockGetter) this.level(), mp);
                if (sh.isEmpty()) continue;
                return (double) y + sh.max(Direction.Axis.Y);
            }
            return refY - 12.0;
        }

        // Altura del suelo bajo las cuatro ruedas; -1e9 si hay agua.
        double wheels(double cx, double cz, float yaw, double ref) {
            double rad = Math.toRadians(yaw);
            double fx = -Math.sin(rad);
            double fz = Math.cos(rad);
            double sx = Math.cos(rad);
            double sz = Math.sin(rad);
            double best = -1.0E9;
            for (int a = -1; a <= 1; a += 2) {
                for (int b = -1; b <= 1; b += 2) {
                    double g = this.ground(cx + sx * a * 0.9 + fx * b * 1.6, cz + sz * a * 0.9 + fz * b * 1.6, ref);
                    if (g < -1.0E8) return -1.0E9;
                    best = Math.max(best, g);
                }
            }
            return best;
        }

        @Override
        public void tick() {
            super.tick();
            this.prevWH = this.wh;
            this.prevSteer = this.steer;
            if (this.level().isClientSide) {
                if (this.lsteps > 0) {
                    double k = 1.0 / this.lsteps;
                    this.setPos(this.getX() + (this.lx - this.getX()) * k, this.getY() + (this.ly - this.getY()) * k, this.getZ() + (this.lz - this.getZ()) * k);
                    this.setYRot(this.getYRot() + Mth.wrapDegrees(this.lyr - this.getYRot()) * (float) k);
                    --this.lsteps;
                }
                this.wh = this.entityData.get(WH);
                this.steer = this.entityData.get(STEER);
                if (this.siren() && this.tickCount % 2 == 0) {
                    double rad = Math.toRadians(this.getYRot());
                    boolean red = this.tickCount / 6 % 2 == 0;
                    double side = red ? 0.45 : -0.45;
                    this.level().addParticle(red ? net.minecraft.core.particles.DustParticleOptions.REDSTONE : new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.2f, 0.4f, 1.0f), 1.0f), this.getX() + Math.cos(rad) * side, this.getY() + 1.95, this.getZ() + Math.sin(rad) * side, 0.0, 0.0, 0.0);
                }
                return;
            }
            ServerLevel sl = (ServerLevel) this.level();
            if (this.owner != null && !PoliciaPatrullero.ACTIVE.containsKey(this.owner)) {
                PoliciaPatrullero.ACTIVE.put(this.owner, this);
            }
            Entity d = this.driver();
            float fw = 0.0f;
            float st = 0.0f;
            if (d instanceof LivingEntity le) {
                fw = le.zza;
                st = le.xxa;
            }
            if (Math.abs(fw) < 0.05f) fw = 0.0f;
            if (Math.abs(st) < 0.05f) st = 0.0f;
            if (fw > 0) {
                this.speed += 0.035;
            } else if (fw < 0) {
                this.speed -= this.speed > 0.05 ? 0.06 : 0.025;
            } else {
                this.speed *= 0.93;
            }
            this.speed = Mth.clamp(this.speed, -0.2, MAX);
            if (Math.abs(this.speed) < 0.004) this.speed = 0.0;
            float turn = (float) (Math.min(1.0, Math.abs(this.speed) / 0.15) * 4.2);
            float dyaw = -st * turn * (this.speed < 0 ? -1.0f : 1.0f);
            float nyaw = this.getYRot() + dyaw;
            double rad = Math.toRadians(nyaw);
            double nx = this.getX() - Math.sin(rad) * this.speed;
            double nz = this.getZ() + Math.cos(rad) * this.speed;
            double g = this.wheels(nx, nz, nyaw, this.getY() + 1.2);
            if (g < -1.0E8 || g - this.getY() > 1.1) {
                // pared o agua: frena de golpe
                if (Math.abs(this.speed) > 0.35) {
                    sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.6f, 1.6f);
                }
                this.speed = 0.0;
                nx = this.getX();
                nz = this.getZ();
                g = this.wheels(nx, nz, nyaw, this.getY() + 1.2);
                if (g < -1.0E8 || g - this.getY() > 1.1) {
                    nyaw = this.getYRot();
                    g = this.getY();
                }
            }
            double ny = this.getY() + Mth.clamp(g - this.getY(), -0.6, 0.6);
            this.setPos(nx, ny, nz);
            this.setYRot(nyaw);
            this.wh += (float) (this.speed / 0.4);
            this.steer += (st * 25.0f - this.steer) * 0.35f;
            this.entityData.set(WH, this.wh);
            this.entityData.set(STEER, this.steer);
            // apartar lo que se cruza
            if (Math.abs(this.speed) > 0.2) {
                for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.3), x -> !this.hasPassenger(x) && x.isAlive())) {
                    Vec3 away = le.position().subtract(this.position()).normalize().scale(0.8);
                    le.push(away.x, 0.35, away.z);
                    le.hurtMarked = true;
                }
            }
            if (d != null && Math.abs(this.speed) > 0.05 && this.tickCount % 6 == 0) {
                sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, 0.25f, 0.6f + (float) Math.abs(this.speed));
            }
            if (this.siren()) {
                if (this.tickCount % 60 == 0) {
                    sl.playSound(null, this.getX(), this.getY(), this.getZ(), PoliciaSonido.SIRENA.get(), SoundSource.NEUTRAL, 2.0f, 1.0f);
                }
                if (this.tickCount % 20 == 0) {
                    // las luces delatan a los ladrones buscados cercanos
                    for (ServerPlayer th : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(32.0), x -> "ladron".equals(PoliciaMod.job(x)) && PoliciaLadron.wanted(x) > 0)) {
                        th.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
                    }
                }
            }
            // detenido atrás: sigue esposado; entregarlo en la comisaría
            for (Entity e : this.getPassengers()) {
                if (e == d || !(e instanceof LivingEntity le)) continue;
                le.getPersistentData().putInt("pol_cuff", 300);
                if (!le.hasEffect((MobEffect) PoliciaExtra.CUFF.get()) || this.tickCount % 100 == 0) {
                    le.addEffect(new MobEffectInstance((MobEffect) PoliciaExtra.CUFF.get(), 300, 0, false, false, false));
                }
                if (this.tickCount % 20 == 0 && d instanceof ServerPlayer cop && sl.dimension() == Level.OVERWORLD && PoliciaStruct.Sites.get(sl).near(0, this.blockPosition(), 24)) {
                    this.deliver(sl, cop, le);
                }
            }
        }

        void deliver(ServerLevel sl, ServerPlayer cop, LivingEntity t) {
            this.releasing = true;
            t.stopRiding();
            this.releasing = false;
            String nm = t.getDisplayName().getString();
            if (t instanceof ServerPlayer tp) {
                tp.getPersistentData().putInt("pol_cuff", 1);
                tp.getPersistentData().putInt("pol_wnt", 0);
                PoliciaMod.msg(tp, "Te entregaron en la comisaría: se borraron tus estrellas");
                PoliciaMod.sync(tp);
            } else if (t instanceof Mob) {
                sl.sendParticles(ParticleTypes.POOF, t.getX(), t.getY() + 1.0, t.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                t.discard();
            }
            cop.getPersistentData().putInt("pol_xp", PoliciaMod.xp(cop) + 4);
            PoliciaBoss.give(cop, new ItemStack(net.minecraft.world.item.Items.EMERALD, 3));
            sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.NEUTRAL, 1.0f, 1.0f);
            cop.sendSystemMessage(Component.literal("§aEntregaste a " + nm + " en la comisaría: +4 XP y 3 esmeraldas"));
            PoliciaDiario.add(sl, "El oficial " + cop.getName().getString() + " entregó a " + nm + " en la comisaría");
            PoliciaMod.sync(cop);
        }
    }
}
