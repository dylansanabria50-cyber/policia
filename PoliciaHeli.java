package com.example.policia;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

// Helicoptero policial pilotable: invocacion, vuelo manual con WASD/
// espacio/ctrl, linterna que sigue la mirada, aterrizaje automatico y
// retirada con encogimiento.
public class PoliciaHeli {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create((IForgeRegistry<EntityType<?>>) ForgeRegistries.ENTITY_TYPES, "policia");
    public static final RegistryObject<EntityType<HeliEntity>> HELI = ENTITIES.register("helicoptero", () -> EntityType.Builder.of(HeliEntity::new, MobCategory.MISC).sized(3.0f, 3.4f).noSummon().fireImmune().clientTrackingRange(16).updateInterval(1).build("policia:helicoptero"));
    public static final int DURATION = 2400;
    public static final int LAND_T = 240;
    public static final int COOLDOWN = 1200;
    public static final int SHRINK_T = 30;

    public static void use(ServerPlayer p) {
        if (p.getVehicle() instanceof HeliEntity) {
            PoliciaMod.msg(p, "Ya vas en el helicóptero (Shift para bajar)");
            return;
        }
        if (p.isPassenger()) {
            PoliciaMod.msg(p, "Ya vas montado en algo");
            return;
        }
        List<HeliEntity> mine = p.level().getEntitiesOfClass(HeliEntity.class, p.getBoundingBox().inflate(64.0), e -> !e.isRemoved() && p.getUUID().equals(e.owner));
        if (!mine.isEmpty()) {
            PoliciaMod.msg(p, "Haz clic derecho sobre el helicóptero para montarte");
            return;
        }
        long left = p.getPersistentData().getLong("pol_hcd_t") - p.level().getGameTime();
        if (left > 0L) {
            PoliciaMod.msg(p, "Helicóptero en enfriamiento: " + (left + 19L) / 20L + " s");
            return;
        }
        if (PoliciaMod.shield(p) > 0) {
            PoliciaMod.msg(p, "Espera a que termine el escudo");
            return;
        }
        PoliciaHeli.summon(p);
    }

    public static void toggleLight(ServerPlayer p) {
        Entity entity = p.getVehicle();
        if (!(entity instanceof HeliEntity)) {
            PoliciaMod.msg(p, "El modo linterna es del helicóptero");
            return;
        }
        HeliEntity h = (HeliEntity) entity;
        boolean on = h.getEntityData().get(HeliEntity.LIGHT) == false;
        h.getEntityData().set(HeliEntity.LIGHT, on);
        if (!on) {
            h.clearLight();
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1.0f, on ? 1.4f : 0.8f);
        PoliciaMod.msg(p, on ? "Modo linterna: la luz sigue tu mirada y puedes seguir volando" : "Modo linterna desactivado");
    }

    public static void vert(ServerPlayer p, int d) {
        Entity entity = p.getVehicle();
        if (entity instanceof HeliEntity) {
            HeliEntity h = (HeliEntity) entity;
            h.vert = d;
            h.vertT = h.tickCount;
        }
    }

    static void summon(ServerPlayer p) {
        HeliEntity h = HELI.get().create(p.level());
        if (h == null) {
            return;
        }
        h.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
        h.owner = p.getUUID();
        p.level().addFreshEntity(h);
        p.startRiding(h, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.7f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
        p.serverLevel().sendParticles((ParticleOptions) ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 0.1, p.getZ(), 16, 1.5, 0.1, 1.5, 0.03);
        PoliciaMod.msg(p, "Helicóptero listo: W/S avanzar, A/D desplazarte, ESPACIO subir, CTRL bajar, Shift para salir");
    }

    public static class HeliEntity extends Entity {
        static final EntityDataAccessor<Integer> SHRINK = SynchedEntityData.defineId(HeliEntity.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> LIGHT = SynchedEntityData.defineId(HeliEntity.class, EntityDataSerializers.BOOLEAN);
        BlockPos lightPos;
        int vert;
        int vertT;
        public float rotor;
        public float prevRotor;
        public float tilt;
        public float prevTilt;
        public float roll;
        public float prevRoll;
        UUID owner;
        double vx;
        double vy;
        double vz;
        int life;
        int idle;
        int landedT;
        boolean landMsg;
        double lx;
        double ly;
        double lz;
        float lyr;
        int lsteps;

        public HeliEntity(EntityType<? extends HeliEntity> type, Level level) {
            super(type, level);
            this.noPhysics = true;
            this.noCulling = true;
            this.setNoGravity(true);
        }

        @Override
        protected void defineSynchedData() {
            this.entityData.define(SHRINK, -1);
            this.entityData.define(LIGHT, false);
        }

        public boolean isLight() {
            return this.entityData.get(LIGHT);
        }

        @Override
        public void remove(Entity.RemovalReason reason) {
            this.clearLight();
            super.remove(reason);
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

        @Override
        public double getPassengersRidingOffset() {
            return 1.4;
        }

        @Override
        protected boolean canAddPassenger(Entity e) {
            return this.getPassengers().isEmpty();
        }

        @Override
        public boolean isPickable() {
            return this.getFirstPassenger() == null && !this.isRemoved() && this.entityData.get(SHRINK) < 0;
        }

        @Override
        public InteractionResult interact(Player pl, InteractionHand hand) {
            if (this.getFirstPassenger() != null || pl.isPassenger() || this.entityData.get(SHRINK) >= 0) {
                return InteractionResult.PASS;
            }
            if (this.owner != null && !this.owner.equals(pl.getUUID())) {
                return InteractionResult.PASS;
            }
            if (!this.level().isClientSide && pl instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) pl;
                sp.startRiding(this, true);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.7f);
                PoliciaMod.msg(sp, "Subiste al helicóptero");
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        @Override
        public void positionRider(Entity e, Entity.MoveFunction cb) {
            super.positionRider(e, cb);
        }

        @Override
        public void push(double x, double y, double z) {
        }

        @Override
        public boolean isPushable() {
            return false;
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
        public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
            this.lx = x;
            this.ly = y;
            this.lz = z;
            this.lyr = yr;
            this.lsteps = 3;
        }

        boolean free(double x, double y, double z) {
            AABB b = new AABB(x - 1.5, y + 0.05, z - 1.5, x + 1.5, y + 3.2, z + 1.5);
            for (VoxelShape s : this.level().getCollisions(this, b)) {
                if (s.isEmpty()) continue;
                return false;
            }
            return true;
        }

        double ground(double x, double z, double refY) {
            int bx = Mth.floor(x);
            int bz = Mth.floor(z);
            int top = Mth.floor(refY) + 2;
            int bot = Math.max(Mth.floor(refY) - 96, this.level().getMinBuildHeight());
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int y = top; y >= bot; --y) {
                mp.set(bx, y, bz);
                BlockState bs = this.level().getBlockState(mp);
                VoxelShape sh = bs.getCollisionShape((BlockGetter) this.level(), mp);
                if (sh.isEmpty()) continue;
                return (double) y + sh.max(Direction.Axis.Y);
            }
            return refY;
        }

        Vec3 side() {
            double rad = Math.toRadians(this.getYRot());
            double x = this.getX() - Math.cos(rad) * 2.6;
            double z = this.getZ() - Math.sin(rad) * 2.6;
            double g = this.ground(x, z, this.getY() + 1.0);
            return new Vec3(x, g + 0.05, z);
        }

        @Override
        public Vec3 getDismountLocationForPassenger(LivingEntity p) {
            return this.side();
        }

        @Override
        protected void addPassenger(Entity e) {
            super.addPassenger(e);
            e.fallDistance = 0.0f;
            if (e instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) e;
                sp.getPersistentData().putInt("pol_nofall", 100);
                if (!this.level().isClientSide) {
                    Vec3 d = this.side();
                    sp.connection.teleport(d.x, d.y, d.z, sp.getYRot(), sp.getXRot());
                }
            }
        }

        void pushOut(Entity r) {
            double rad = Math.toRadians(this.getYRot());
            double c = Math.cos(rad);
            double s = Math.sin(rad);
            for (Player pl : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(6.0, 1.0, 6.0), e -> e != r && !e.isSpectator())) {
                double of;
                if (pl.getY() + 1.8 < this.getY() + 0.2 || pl.getY() > this.getY() + 3.2) continue;
                double dx = pl.getX() - this.getX();
                double dz = pl.getZ() - this.getZ();
                double lat = dx * c + dz * s;
                double fw = -dx * s + dz * c - 0.55;
                if (Math.abs(lat) >= 1.5 || Math.abs(fw) >= 2.65) continue;
                double nl = lat;
                double nf = fw;
                double ol = 1.6 - Math.abs(lat);
                if (ol <= (of = 2.7 - Math.abs(fw))) {
                    nl = (double) (lat < 0.0 ? -1 : 1) * 1.6;
                } else {
                    nf = (double) (fw < 0.0 ? -1 : 1) * 2.7;
                }
                double nx = this.getX() + nl * c - (nf += 0.55) * s;
                double nz = this.getZ() + nl * s + nf * c;
                if (pl instanceof ServerPlayer) {
                    ServerPlayer sp = (ServerPlayer) pl;
                    sp.connection.teleport(nx, pl.getY(), nz, pl.getYRot(), pl.getXRot());
                    continue;
                }
                pl.setPos(nx, pl.getY(), nz);
            }
        }

        void fly(Entity r, boolean landing) {
            boolean auto;
            double tx = 0.0;
            double ty = 0.0;
            double tz = 0.0;
            float yawNew = this.getYRot();
            boolean bl = auto = landing || r == null;
            if (!auto && r instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) r;
                boolean lamp = false;
                float fw = le.zza;
                float st = le.xxa;
                if (Math.abs(fw) < 0.05f || lamp) {
                    fw = 0.0f;
                }
                if (Math.abs(st) < 0.05f || lamp) {
                    st = 0.0f;
                }
                float turn = lamp ? 8.0f : 5.0f;
                yawNew = this.getYRot() + Mth.clamp(Mth.wrapDegrees(r.getYRot() - this.getYRot()), -turn, turn);
                double rad = Math.toRadians(yawNew);
                double fx = -Math.sin(rad);
                double fz = Math.cos(rad);
                double sx = Math.cos(rad);
                double sz = Math.sin(rad);
                tx = (fx * (double) fw + sx * (double) st) * 0.55;
                tz = (fz * (double) fw + sz * (double) st) * 0.55;
                if (this.tickCount - this.vertT <= 2) {
                    ty = (double) this.vert * 0.4;
                }
            } else {
                double dist = this.getY() - this.ground(this.getX(), this.getZ(), this.getY());
                ty = -Mth.clamp(dist * 0.12, 0.05, 0.3);
            }
            this.vx += (tx - this.vx) * 0.15;
            this.vy += (ty - this.vy) * 0.15;
            this.vz += (tz - this.vz) * 0.15;
            double x = this.getX();
            double y = this.getY();
            double z = this.getZ();
            if (this.free(x + this.vx, y, z)) {
                x += this.vx;
            } else {
                this.vx = 0.0;
            }
            if (this.free(x, y + this.vy, z)) {
                y += this.vy;
            } else {
                this.vy = 0.0;
            }
            if (this.free(x, y, z + this.vz)) {
                z += this.vz;
            } else {
                this.vz = 0.0;
            }
            y = Math.min(y, (double) (this.level().getMaxBuildHeight() - 6));
            this.setPos(x, y, z);
            this.setYRot(yawNew);
        }

        void updateTilt() {
            this.prevTilt = this.tilt;
            this.prevRoll = this.roll;
            double dx = this.getX() - this.xo;
            double dz = this.getZ() - this.zo;
            double rad = Math.toRadians(this.getYRot());
            double fs = -dx * Math.sin(rad) + dz * Math.cos(rad);
            double ls = dx * Math.cos(rad) + dz * Math.sin(rad);
            this.tilt += (Mth.clamp((float) (fs * 22.0), -10.0f, 14.0f) - this.tilt) * 0.2f;
            this.roll += (Mth.clamp((float) (-ls * 20.0), -12.0f, 12.0f) - this.roll) * 0.2f;
        }

        void notifyOwner(ServerLevel sl, String m) {
            if (this.owner == null) {
                return;
            }
            ServerPlayer o = sl.getServer().getPlayerList().getPlayer(this.owner);
            if (o != null) {
                PoliciaMod.msg(o, m);
            }
        }

        @Override
        public void tick() {
            boolean landing;
            ServerPlayer o;
            super.tick();
            this.prevRotor = this.rotor;
            this.rotor += 52.0f;
            if (this.level().isClientSide) {
                if (this.lsteps > 0) {
                    double k = 1.0 / (double) this.lsteps;
                    this.setPos(this.getX() + (this.lx - this.getX()) * k, this.getY() + (this.ly - this.getY()) * k, this.getZ() + (this.lz - this.getZ()) * k);
                    this.setYRot(this.getYRot() + Mth.wrapDegrees(this.lyr - this.getYRot()) * (float) k);
                    --this.lsteps;
                }
                this.updateTilt();
                return;
            }
            this.setDeltaMovement(Vec3.ZERO);
            ServerLevel sl = (ServerLevel) this.level();
            ++this.life;
            if (!(this.tickCount % 20 != 0 || this.owner == null || (o = sl.getServer().getPlayerList().getPlayer(this.owner)) != null && o.isAlive())) {
                this.ejectPassengers();
                this.discard();
                return;
            }
            int sh = this.entityData.get(SHRINK);
            if (sh >= 0) {
                this.entityData.set(SHRINK, sh + 1);
                if (sh + 1 >= 30) {
                    this.finish(sl);
                }
                return;
            }
            Entity r = this.getFirstPassenger();
            boolean bl = landing = this.life >= 2160;
            if (landing && !this.landMsg) {
                this.landMsg = true;
                this.notifyOwner(sl, "El helicóptero va a aterrizar");
            }
            if (landing && this.entityData.get(LIGHT)) {
                this.entityData.set(LIGHT, false);
                this.clearLight();
            }
            this.fly(r, landing);
            if (this.entityData.get(LIGHT) && r != null) {
                this.lamp(sl, r);
            }
            boolean landed = !this.free(this.getX(), this.getY() - 0.08, this.getZ());
            this.pushOut(r);
            this.idle = r == null && landed ? this.idle + 1 : 0;
            int n = this.landedT = landing && landed ? this.landedT + 1 : 0;
            if (this.tickCount % 4 == 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.4f, 0.5f);
            }
            if (this.landedT > 25 || this.idle > 600 || this.life > 2800) {
                this.startShrink(sl);
            }
        }

        void clearLight() {
            if (this.lightPos != null) {
                if (this.level().getBlockState(this.lightPos).is(Blocks.LIGHT)) {
                    this.level().setBlock(this.lightPos, Blocks.AIR.defaultBlockState(), 3);
                }
                this.lightPos = null;
            }
        }

        void lamp(ServerLevel sl, Entity r) {
            BlockPos bp;
            Vec3 hit;
            Vec3 end;
            Vec3 look = r.getLookAngle();
            Vec3 o = this.position().add(0.0, 0.7, 0.0).add(look.scale(1.8));
            BlockHitResult hr = sl.clip(new ClipContext(o, end = o.add(look.scale(48.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 vec3 = hit = hr.getType() == HitResult.Type.MISS ? end : hr.getLocation();
            if (this.tickCount % 4 == 0 && !(bp = BlockPos.containing((Position) hit.subtract(look.scale(0.6)))).equals(this.lightPos) && sl.getBlockState(bp).isAir()) {
                this.clearLight();
                sl.setBlock(bp, Blocks.LIGHT.defaultBlockState(), 3);
                this.lightPos = bp;
            }
        }

        void startShrink(ServerLevel sl) {
            this.entityData.set(SHRINK, 0);
            this.ejectPassengers();
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHULKER_CLOSE, SoundSource.PLAYERS, 1.0f, 0.6f);
        }

        void finish(ServerLevel sl) {
            ServerPlayer o;
            sl.sendParticles((ParticleOptions) ParticleTypes.POOF, this.getX(), this.getY() + 0.8, this.getZ(), 18, 1.0, 0.5, 1.0, 0.03);
            if (this.owner != null && (o = sl.getServer().getPlayerList().getPlayer(this.owner)) != null) {
                o.getPersistentData().putLong("pol_hcd_t", sl.getGameTime() + 1200L);
                PoliciaMod.msg(o, "Helicóptero retirado");
            }
            this.ejectPassengers();
            this.discard();
        }
    }
}
