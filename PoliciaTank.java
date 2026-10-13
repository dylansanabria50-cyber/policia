package com.example.policia;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

// Tanque policial (fijo o movil tras la mejora de nivel 2): invocacion,
// un solo disparo con onda expansiva, y en su version movil conduccion
// con WASD y hasta 3 disparos.
public class PoliciaTank {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create((IForgeRegistry<EntityType<?>>) ForgeRegistries.ENTITY_TYPES, "policia");
    public static final RegistryObject<EntityType<TankEntity>> TANK = ENTITIES.register("tanque", () -> EntityType.Builder.of(TankEntity::new, MobCategory.MISC).sized(4.8f, 6.0f).noSummon().fireImmune().clientTrackingRange(16).updateInterval(1).build("policia:tanque"));
    public static final float SCALE = 0.45f;
    public static final float SX = 0.96f;
    public static final float SY = 1.24f;
    public static final int COOLDOWN = 600;
    public static final int SHOTS = 3;
    static final int LOAD_TICKS = 12;
    static final int END_TICKS = 62;
    static final int RESET_TICKS = 30;
    static final double MIN_RANGE = 16.0;
    static final float[] PT = new float[]{0.0f, 2.6875f, -0.4375f};
    static final float[] PB = new float[]{0.0f, 2.3125f, -1.4375f};
    static final float WHEEL_R = 0.45119998f;

    public static void summon(ServerPlayer p) {
        PoliciaTank.summon(p, false);
    }

    public static void summon(ServerPlayer p, boolean mobile) {
        TankEntity t = TANK.get().create(p.level());
        if (t == null) {
            return;
        }
        t.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
        t.owner = p.getUUID();
        if (mobile) {
            t.getEntityData().set(TankEntity.MOBILE, true);
            t.getEntityData().set(TankEntity.SHOTS_LEFT, 3);
            double g = t.footGround(p.getX(), p.getZ(), p.getYRot(), p.getY());
            if (g > p.getY() - 6.0) {
                t.setPos(p.getX(), Math.max(g, p.getY() - 0.5), p.getZ());
            }
        }
        p.level().addFreshEntity(t);
        p.startRiding(t, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.6f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
        p.serverLevel().sendParticles((ParticleOptions) ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 14, 1.0, 0.1, 1.0, 0.02);
        PoliciaMod.msg(p, mobile ? "Tanque listo: WASD para moverte, clic derecho para disparar (3 disparos)" : "Tanque listo: apunta y haz clic derecho para disparar");
    }

    public static void useMobile(ServerPlayer p) {
        Entity entity = p.getVehicle();
        if (entity instanceof TankEntity) {
            TankEntity t = (TankEntity) entity;
            if (t.isMobile()) {
                p.stopRiding();
                PoliciaMod.msg(p, "Bajaste del tanque (J para volver a subir)");
            } else {
                PoliciaMod.msg(p, "El tanque ya esta invocado");
            }
            return;
        }
        if (p.isPassenger()) {
            PoliciaMod.msg(p, "Ya vas montado en algo");
            return;
        }
        List<TankEntity> mine = p.level().getEntitiesOfClass(TankEntity.class, p.getBoundingBox().inflate(48.0), e -> e.isMobile() && p.getUUID().equals(e.owner));
        if (!mine.isEmpty()) {
            TankEntity t = mine.get(0);
            if (t.isRemoved() || t.getFirstPassenger() != null) {
                return;
            }
            PoliciaMod.msg(p, "Haz clic derecho sobre el tanque para montarte");
            return;
        }
        CompoundTag d = p.getPersistentData();
        if (PoliciaMod.shield(p) > 0) {
            PoliciaMod.msg(p, "Espera a que termine el escudo");
            return;
        }
        if (d.getInt("pol_tcd") > 0) {
            PoliciaMod.msg(p, "Tanque en enfriamiento: " + (d.getInt("pol_tcd") + 19) / 20 + " s");
            return;
        }
        PoliciaTank.summon(p, true);
        PoliciaMod.sync(p);
    }

    public static void fire(ServerPlayer p) {
        Entity entity = p.getVehicle();
        if (!(entity instanceof TankEntity)) {
            return;
        }
        TankEntity t = (TankEntity) entity;
        if (t.fireState() >= 0) {
            return;
        }
        if (t.isMobile() && t.shotsLeft() <= 0) {
            return;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        HitResult hr = p.pick(96.0, 1.0f, false);
        Vec3 loc = hr.getLocation();
        if (eye.distanceTo(loc) < 16.0) {
            loc = eye.add(look.scale(16.0));
        }
        t.target = loc;
        t.boomAt = 12 + Mth.clamp((int) (eye.distanceTo(loc) / 4.0), 2, 14);
        if (t.isMobile()) {
            t.getEntityData().set(TankEntity.SHOTS_LEFT, t.shotsLeft() - 1);
        }
        t.setFireState(0);
        p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 0.5f);
        PoliciaMod.msg(p, t.isMobile() ? "Cargando... (quedan " + t.shotsLeft() + ")" : "Cargando...");
    }

    public static class TankEntity extends Entity {
        static final EntityDataAccessor<Integer> FIRE = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Integer> SHOTS_LEFT = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> MOBILE = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.BOOLEAN);
        static final EntityDataAccessor<Float> WL = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
        static final EntityDataAccessor<Float> WR = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
        public float turretYaw;
        public float prevTurretYaw;
        public float pitch;
        public float prevPitch;
        public float wl;
        public float wr;
        public float prevWL;
        public float prevWR;
        UUID owner;
        Vec3 target;
        int boomAt = -1;
        double speed;
        int idle;
        double lx;
        double ly;
        double lz;
        float lyr;
        int lsteps;
        int clankT;
        float lastYaw;

        public TankEntity(EntityType<? extends TankEntity> type, Level level) {
            super(type, level);
            this.noPhysics = true;
            this.noCulling = true;
            this.setNoGravity(true);
        }

        @Override
        protected void defineSynchedData() {
            this.entityData.define(FIRE, -1);
            this.entityData.define(SHOTS_LEFT, 0);
            this.entityData.define(MOBILE, false);
            this.entityData.define(WL, 0.0f);
            this.entityData.define(WR, 0.0f);
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
            return 3.97;
        }

        public boolean shouldRiderSit() {
            return false;
        }

        @Override
        protected boolean canAddPassenger(Entity e) {
            return this.getPassengers().isEmpty();
        }

        @Override
        public boolean isPickable() {
            return this.isMobile() && this.getFirstPassenger() == null && !this.isRemoved();
        }

        @Override
        public InteractionResult interact(Player pl, InteractionHand hand) {
            if (!this.isMobile() || this.getFirstPassenger() != null || pl.isPassenger()) {
                return InteractionResult.PASS;
            }
            if (this.owner != null && !this.owner.equals(pl.getUUID())) {
                return InteractionResult.PASS;
            }
            if (!this.level().isClientSide && pl instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) pl;
                sp.startRiding(this, true);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.6f);
                PoliciaMod.msg(sp, "Subiste al tanque (" + this.shotsLeft() + " disparos)");
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        @Override
        public void positionRider(Entity e, Entity.MoveFunction cb) {
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
        public boolean isAffectedByFluids() {
            return false;
        }

        @Override
        public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
            this.lx = x;
            this.ly = y;
            this.lz = z;
            this.lyr = yr;
            this.lsteps = 3;
        }

        public boolean isMobile() {
            return this.entityData.get(MOBILE);
        }

        public int shotsLeft() {
            return this.entityData.get(SHOTS_LEFT);
        }

        public int fireState() {
            return this.entityData.get(FIRE);
        }

        void setFireState(int v) {
            this.entityData.set(FIRE, v);
        }

        double ground(double x, double z, double refY) {
            int bx = Mth.floor(x);
            int bz = Mth.floor(z);
            int top = Mth.floor(refY) + 4;
            int bot = Math.max(Mth.floor(refY) - 14, this.level().getMinBuildHeight());
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int y = top; y >= bot; --y) {
                mp.set(bx, y, bz);
                BlockState bs = this.level().getBlockState(mp);
                if (bs.is(BlockTags.LEAVES) || bs.is(BlockTags.LOGS)) continue;
                FluidState fs = bs.getFluidState();
                if (!fs.isEmpty()) {
                    return (float) y + fs.getHeight((BlockGetter) this.level(), mp);
                }
                VoxelShape sh = bs.getCollisionShape((BlockGetter) this.level(), mp);
                if (sh.isEmpty()) continue;
                return (double) y + sh.max(Direction.Axis.Y);
            }
            return refY - 14.0;
        }

        double footGround(double cx, double cz, float yaw, double ref) {
            double rad = Math.toRadians(yaw);
            double fx = -Math.sin(rad);
            double fz = Math.cos(rad);
            double px = Math.cos(rad);
            double pz = Math.sin(rad);
            double best = -1.0E9;
            for (int a = -1; a <= 1; ++a) {
                for (int b = -1; b <= 1; ++b) {
                    best = Math.max(best, this.ground(cx + px * (double) a * 1.7 + fx * (double) b * 3.1, cz + pz * (double) a * 1.7 + fz * (double) b * 3.1, ref));
                }
            }
            return best;
        }

        Vec3 dismountSpot() {
            double rad = Math.toRadians(this.getYRot());
            double x = this.getX() - Math.cos(rad) * 3.2;
            double z = this.getZ() - Math.sin(rad) * 3.2;
            double g = this.ground(x, z, this.getY() + 2.0);
            return new Vec3(x, g + 0.05, z);
        }

        @Override
        public Vec3 getDismountLocationForPassenger(LivingEntity p) {
            if (this.isMobile()) {
                return this.dismountSpot();
            }
            return new Vec3(this.getX(), this.getY() + 0.1, this.getZ());
        }

        @Override
        protected void addPassenger(Entity e) {
            super.addPassenger(e);
            e.fallDistance = 0.0f;
            if (e instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) e;
                sp.getPersistentData().putInt("pol_nofall", 100);
                if (this.isMobile() && !this.level().isClientSide) {
                    Vec3 d = this.dismountSpot();
                    sp.connection.teleport(d.x, d.y, d.z, sp.getYRot(), sp.getXRot());
                }
            }
        }

        Vec3 muzzle() {
            double hy = Math.toRadians(this.getYRot());
            double yaw = Math.toRadians(this.turretYaw);
            double pit = Math.toRadians(this.pitch);
            double fwdT = -PT[2] * 0.96f;
            double fwdB = -(PB[2] - PT[2]) * 0.96f;
            double px = this.getX() - Math.sin(hy) * fwdT - Math.sin(yaw) * fwdB;
            double pz = this.getZ() + Math.cos(hy) * fwdT + Math.cos(yaw) * fwdB;
            double py = this.getY() + (double) (PB[1] * 1.24f);
            Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pit), -Math.sin(pit), Math.cos(yaw) * Math.cos(pit));
            return new Vec3(px, py, pz).add(dir.scale(5.183999884128571));
        }

        void clank(Entity r) {
            boolean turning = Math.abs(Mth.wrapDegrees(this.getYRot() - this.lastYaw)) > 0.3f;
            this.lastYaw = this.getYRot();
            if (r == null || Math.abs(this.speed) < 0.03 && !turning) {
                this.clankT = 0;
                return;
            }
            ++this.clankT;
            if (this.clankT % 4 == 0) {
                float pit = 0.45f + this.level().random.nextFloat() * 0.2f;
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (this.clankT & 4) == 0 ? SoundEvents.IRON_GOLEM_STEP : SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.0f, pit);
            }
            if (this.clankT % 12 == 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 0.35f, 0.5f);
            }
        }

        void pushOut(Entity r) {
            double rad = Math.toRadians(this.getYRot());
            double c = Math.cos(rad);
            double s = Math.sin(rad);
            for (Player pl : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(6.0, 0.0, 6.0), e -> e != r && !e.isSpectator())) {
                double of;
                double dx = pl.getX() - this.getX();
                double dz = pl.getZ() - this.getZ();
                double lat = dx * c + dz * s;
                double fw = -dx * s + dz * c;
                if (Math.abs(lat) >= 3.4 || Math.abs(fw) >= 6.3 || pl.getY() > this.getY() + 4.4) continue;
                double nl = lat;
                double nf = fw;
                double ol = 3.5 - Math.abs(lat);
                if (ol <= (of = 6.4 - Math.abs(fw))) {
                    nl = (double) (lat < 0.0 ? -1 : 1) * 3.5;
                } else {
                    nf = (double) (fw < 0.0 ? -1 : 1) * 6.4;
                }
                double nx = this.getX() + nl * c - nf * s;
                double nz = this.getZ() + nl * s + nf * c;
                if (pl instanceof ServerPlayer) {
                    ServerPlayer sp = (ServerPlayer) pl;
                    sp.connection.teleport(nx, pl.getY(), nz, pl.getYRot(), pl.getXRot());
                    continue;
                }
                pl.setPos(nx, pl.getY(), nz);
            }
        }

        void drive(Entity r) {
            double nz;
            float fw = 0.0f;
            float st = 0.0f;
            if (r instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) r;
                fw = le.zza;
                st = le.xxa;
            }
            if (Math.abs(fw) < 0.05f) {
                fw = 0.0f;
            }
            if (Math.abs(st) < 0.05f) {
                st = 0.0f;
            }
            this.speed = fw != 0.0f ? (this.speed += (double) fw * 0.05) : (this.speed *= 0.85);
            this.speed = Mth.clamp(this.speed, -0.2, 0.42);
            if (Math.abs(this.speed) < 0.004) {
                this.speed = 0.0;
            }
            float dyaw = -st * 2.4f;
            float ny = this.getYRot() + dyaw;
            double rad = Math.toRadians(ny);
            double nx = this.getX() - Math.sin(rad) * this.speed;
            double g = this.footGround(nx, nz = this.getZ() + Math.cos(rad) * this.speed, ny, this.getY());
            if (g - this.getY() > 2.3) {
                this.speed = 0.0;
                nx = this.getX();
                g = this.footGround(nx, nz = this.getZ(), ny, this.getY());
                if (g - this.getY() > 2.3) {
                    ny = this.getYRot();
                    dyaw = 0.0f;
                    g = this.footGround(nx, nz, ny, this.getY());
                }
            }
            double ny2 = this.getY() + Mth.clamp(g - this.getY(), -0.7, 0.5);
            this.setPos(nx, ny2, nz);
            this.setYRot(ny);
            double turn = Math.toRadians(dyaw) * 2.8;
            this.wl += (float) ((this.speed + turn) / 0.4511999785900116);
            this.wr += (float) ((this.speed - turn) / 0.4511999785900116);
            this.entityData.set(WL, this.wl);
            this.entityData.set(WR, this.wr);
        }

        @Override
        public void tick() {
            ServerPlayer o;
            super.tick();
            if (this.tickCount == 1) {
                this.turretYaw = this.prevTurretYaw = this.getYRot();
            }
            this.prevTurretYaw = this.turretYaw;
            this.prevPitch = this.pitch;
            this.prevWL = this.wl;
            this.prevWR = this.wr;
            Entity r = this.getFirstPassenger();
            int f = this.entityData.get(FIRE);
            if (r != null && f < 0) {
                float dy = Mth.wrapDegrees(r.getYRot() - this.turretYaw);
                this.turretYaw += Mth.clamp(dy, -6.0f, 6.0f);
                float tp = Mth.clamp(r.getXRot(), -25.0f, 8.0f);
                this.pitch += Mth.clamp(tp - this.pitch, -3.0f, 3.0f);
            }
            if (this.level().isClientSide) {
                if (this.lsteps > 0) {
                    double k = 1.0 / (double) this.lsteps;
                    this.setPos(this.getX() + (this.lx - this.getX()) * k, this.getY() + (this.ly - this.getY()) * k, this.getZ() + (this.lz - this.getZ()) * k);
                    this.setYRot(this.getYRot() + Mth.wrapDegrees(this.lyr - this.getYRot()) * (float) k);
                    --this.lsteps;
                }
                this.wl = this.entityData.get(WL);
                this.wr = this.entityData.get(WR);
                return;
            }
            this.setDeltaMovement(Vec3.ZERO);
            ServerLevel sl = (ServerLevel) this.level();
            boolean mobile = this.isMobile();
            if (mobile && r != null) {
                this.drive(r);
            } else if (mobile) {
                this.speed = 0.0;
            }
            if (mobile) {
                this.clank(r);
                this.pushOut(r);
            }
            if (!(this.tickCount % 20 != 0 || this.owner == null || (o = sl.getServer().getPlayerList().getPlayer(this.owner)) != null && o.isAlive())) {
                this.ejectPassengers();
                this.discard();
                return;
            }
            if (f >= 0) {
                this.entityData.set(FIRE, ++f);
                if (f == 12 && this.target != null) {
                    Vec3 m = this.muzzle();
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.5f);
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 0.5f);
                    sl.sendParticles((ParticleOptions) ParticleTypes.EXPLOSION, m.x, m.y, m.z, 14, 0.15, 0.15, 0.15, 0.08);
                    sl.sendParticles((ParticleOptions) ParticleTypes.FLASH, m.x, m.y, m.z, 10, 0.25, 0.25, 0.25, 0.04);
                    Vec3 d = this.target.subtract(m);
                    int n = 16;
                    for (int i = 1; i <= n; ++i) {
                        Vec3 q = m.add(d.scale((double) i / (double) n));
                        sl.sendParticles((ParticleOptions) ParticleTypes.SMOKE, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
                    }
                }
                if (f == this.boomAt && this.target != null) {
                    this.level().explode(this, this.target.x, this.target.y, this.target.z, 3.2f, Level.ExplosionInteraction.NONE);
                }
                if (mobile && this.shotsLeft() > 0) {
                    if (f >= 30) {
                        this.setFireState(-1);
                        this.target = null;
                        this.boomAt = -1;
                    }
                } else if (f >= 62) {
                    this.finish(sl);
                }
            } else if (mobile) {
                int n = this.idle = r == null ? this.idle + 1 : 0;
                if (this.idle > 1200 || this.tickCount > 24000) {
                    this.finish(sl);
                }
            } else if (r == null && this.tickCount > 20 || this.tickCount > 1200) {
                this.finish(sl);
            }
        }

        void finish(ServerLevel sl) {
            ServerPlayer o;
            sl.sendParticles((ParticleOptions) ParticleTypes.POOF, this.getX(), this.getY() + 0.6, this.getZ(), 18, 1.0, 0.4, 1.0, 0.03);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHULKER_CLOSE, SoundSource.PLAYERS, 1.0f, 0.5f);
            if (this.owner != null && (o = sl.getServer().getPlayerList().getPlayer(this.owner)) != null) {
                o.getPersistentData().putInt("pol_tcd", 600);
                PoliciaMod.msg(o, "Tanque retirado");
                PoliciaMod.sync(o);
            }
            this.ejectPassengers();
            this.discard();
        }
    }
}
