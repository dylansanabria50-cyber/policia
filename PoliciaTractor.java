package com.example.policia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegistryObject;

// Tractor del Albanil: vehiculo excavador con pala delantera y trasera,
// faros e inventario propio; cava bloques mientras se mueve.
public class PoliciaTractor {
    public static final RegistryObject<EntityType<TractorEntity>> TRACTOR = PoliciaRefuerzo.ENTITIES.register("tractor", () -> EntityType.Builder.of(TractorEntity::new, MobCategory.MISC).sized(4.8f, 6.0f).noSummon().fireImmune().clientTrackingRange(16).updateInterval(1).build("policia:tractor"));
    static final int COST = 15;
    static final int CD = 700;
    static final int IDLE = 6000;
    static final int LIFE = 900;
    static final double WHEEL_R = 1.15;
    static final double A0 = 2.4;
    static final double A1 = 5.3;
    static final double AL = 2.1;
    static final double B0 = -3.9;
    static final double B1 = 2.4;
    static final double BL = 2.4;
    static final Map<UUID, TractorEntity> ACTIVE = new HashMap<UUID, TractorEntity>();
    static final Map<UUID, SimpleContainer> INVS = new HashMap<UUID, SimpleContainer>();
    static boolean mute = false;

    static void init() {
        MinecraftForge.EVENT_BUS.register(new PoliciaPlaced.Ev());
    }

    static SimpleContainer inv(ServerPlayer p) {
        UUID id = p.getUUID();
        SimpleContainer c = INVS.get(id);
        if (c != null) {
            return c;
        }
        c = new SimpleContainer(54);
        ListTag l = p.getPersistentData().getList("alb_tinv", 10);
        for (Tag t : l) {
            CompoundTag e = (CompoundTag) t;
            int s = e.getInt("Slot");
            if (s < 0 || s >= 54) continue;
            c.setItem(s, ItemStack.of(e));
        }
        MinecraftServer srv = p.server;
        SimpleContainer fc = c;
        c.addListener(cc -> {
            if (mute) {
                return;
            }
            ServerPlayer q = srv.getPlayerList().getPlayer(id);
            if (q != null) {
                PoliciaTractor.saveInv(q, fc);
            }
        });
        INVS.put(id, c);
        return c;
    }

    static void saveInv(ServerPlayer p, SimpleContainer c) {
        ListTag l = new ListTag();
        for (int i = 0; i < c.getContainerSize(); ++i) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("Slot", i);
            s.save(e);
            l.add(e);
        }
        p.getPersistentData().put("alb_tinv", (Tag) l);
    }

    static void openInv(ServerPlayer p) {
        if (p.getPersistentData().getInt("alb_tu") < 1) {
            PoliciaMod.msg(p, "Aun no tienes el tractor: desbloquea TRACTOR");
            return;
        }
        SimpleContainer c = PoliciaTractor.inv(p);
        p.openMenu((MenuProvider) new SimpleMenuProvider((id, pi, pl) -> new ChestMenu(MenuType.GENERIC_9x6, id, pi, (Container) c, 6), Component.literal("Inventario del tractor")));
    }

    static void unlock(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("alb_tu") >= 1) {
            PoliciaMod.msg(p, "TRACTOR ya esta desbloqueado");
            return;
        }
        if (PoliciaMod.xp(p) < 15) {
            PoliciaMod.msg(p, "Necesitas 15 XP (tienes " + PoliciaMod.xp(p) + ")");
            return;
        }
        d.putInt("pol_xp", PoliciaMod.xp(p) - 15);
        d.putInt("alb_tu", 1);
        PoliciaMod.msg(p, "Habilidad desbloqueada: TRACTOR");
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.9f);
        PoliciaMod.sync(p);
    }

    static TractorEntity mine(ServerPlayer p) {
        TractorEntity t = ACTIVE.get(p.getUUID());
        if (t != null && t.isRemoved()) {
            ACTIVE.remove(p.getUUID());
            return null;
        }
        return t;
    }

    static void use(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("alb_tu") < 1) {
            PoliciaMod.msg(p, "Desbloquea primero: TRACTOR");
            return;
        }
        if (p.getVehicle() instanceof TractorEntity) {
            p.stopRiding();
            PoliciaMod.msg(p, "Bajaste del tractor (J: volver a subir, Mayus+J: retirarlo)");
            return;
        }
        TractorEntity t = PoliciaTractor.mine(p);
        if (t != null) {
            if (p.isShiftKeyDown()) {
                t.finish((ServerLevel) t.level());
                return;
            }
            if (t.level() != p.level() || t.distanceToSqr((Entity) p) > 400.0) {
                PoliciaMod.msg(p, "El tractor esta lejos (Mayus+J lo retira)");
                return;
            }
            if (t.getFirstPassenger() != null) {
                return;
            }
            p.startRiding((Entity) t, true);
            PoliciaTractor.hint(p);
            return;
        }
        if (p.isPassenger()) {
            PoliciaMod.msg(p, "Ya vas montado en algo");
            return;
        }
        if (d.getInt("alb_tcd") > 0) {
            PoliciaMod.msg(p, "Tractor en enfriamiento: " + (d.getInt("alb_tcd") + 19) / 20 + " s");
            return;
        }
        PoliciaTractor.summon(p);
    }

    static void hint(ServerPlayer p) {
        PoliciaMod.msg(p, "TRACTOR: W/S avanzar, A/D girar, ESPACIO sube la pala, CTRL la baja, G luces, U inventario, Shift bajar");
    }

    static void summon(ServerPlayer p) {
        TractorEntity t = TRACTOR.get().create(p.level());
        if (t == null) {
            return;
        }
        t.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
        t.owner = p.getUUID();
        double g = t.footGround(p.getX(), p.getZ(), p.getYRot(), p.getY());
        if (g > p.getY() - 6.0) {
            t.setPos(p.getX(), Math.max(g, p.getY() - 0.5), p.getZ());
        }
        p.level().addFreshEntity((Entity) t);
        ACTIVE.put(p.getUUID(), t);
        p.getPersistentData().putInt("alb_tact", 1);
        p.startRiding((Entity) t, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.6f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.0f, 0.5f);
        p.serverLevel().sendParticles((ParticleOptions) ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 14, 1.0, 0.1, 1.0, 0.02);
        PoliciaTractor.hint(p);
        PoliciaMod.sync(p);
    }

    static void retire(ServerPlayer p) {
        Level level;
        TractorEntity t = ACTIVE.get(p.getUUID());
        if (t != null && !t.isRemoved() && (level = t.level()) instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel) level;
            t.finish(sl);
        }
        ACTIVE.remove(p.getUUID());
        p.getPersistentData().putInt("alb_tact", 0);
    }

    static void lamp(ServerPlayer p) {
        Entity entity = p.getVehicle();
        if (!(entity instanceof TractorEntity)) {
            return;
        }
        TractorEntity t = (TractorEntity) entity;
        boolean on = !t.lampOn();
        t.setLamp(on);
        if (!on) {
            t.clearLight();
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.8f, on ? 1.4f : 1.0f);
        PoliciaMod.msg(p, on ? "Luces del tractor encendidas" : "Luces del tractor apagadas");
    }

    static void arm(ServerPlayer p, int dir) {
        Entity entity = p.getVehicle();
        if (entity instanceof TractorEntity) {
            TractorEntity t = (TractorEntity) entity;
            t.armDir = dir;
            t.armT = 3;
        }
    }

    public static class TractorEntity extends Entity {
        static final EntityDataAccessor<Float> ARM = SynchedEntityData.defineId(TractorEntity.class, EntityDataSerializers.FLOAT);
        static final EntityDataAccessor<Float> WH = SynchedEntityData.defineId(TractorEntity.class, EntityDataSerializers.FLOAT);
        static final EntityDataAccessor<Float> STEER = SynchedEntityData.defineId(TractorEntity.class, EntityDataSerializers.FLOAT);
        static final EntityDataAccessor<Boolean> LAMP = SynchedEntityData.defineId(TractorEntity.class, EntityDataSerializers.BOOLEAN);
        UUID owner;
        double speed;
        int idle;
        int armDir;
        int armT;
        int fullMsg;
        public float arm;
        public float prevArm;
        public float wh;
        public float prevWH;
        public float steer;
        public float prevSteer;
        BlockPos light;
        double lx;
        double ly;
        double lz;
        float lyr;
        int lsteps;

        public TractorEntity(EntityType<? extends TractorEntity> type, Level level) {
            super(type, level);
            this.noPhysics = true;
            this.noCulling = true;
            this.setNoGravity(true);
        }

        @Override
        protected void defineSynchedData() {
            this.entityData.define(ARM, 0.0f);
            this.entityData.define(WH, 0.0f);
            this.entityData.define(STEER, 0.0f);
            this.entityData.define(LAMP, false);
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag t) {
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag t) {
        }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() {
            return NetworkHooks.getEntitySpawningPacket((Entity) this);
        }

        @Override
        public double getPassengersRidingOffset() {
            return 2.65;
        }

        @Override
        protected boolean canAddPassenger(Entity e) {
            return this.getPassengers().isEmpty();
        }

        @Override
        public boolean isPickable() {
            return this.getFirstPassenger() == null && !this.isRemoved();
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
        public boolean isControlledByLocalInstance() {
            return false;
        }

        @Override
        public InteractionResult interact(Player pl, InteractionHand hand) {
            if (pl.isPassenger()) {
                return InteractionResult.PASS;
            }
            if (this.owner != null && !this.owner.equals(pl.getUUID())) {
                return InteractionResult.PASS;
            }
            if (!this.level().isClientSide && pl instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) pl;
                if (sp.isShiftKeyDown()) {
                    PoliciaTractor.openInv(sp);
                } else if (this.getFirstPassenger() == null) {
                    sp.startRiding((Entity) this, true);
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0f, 0.6f);
                    PoliciaTractor.hint(sp);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        @Override
        public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
            this.lx = x;
            this.ly = y;
            this.lz = z;
            this.lyr = yr;
            this.lsteps = 3;
        }

        public boolean lampOn() {
            return this.entityData.get(LAMP);
        }

        void setLamp(boolean on) {
            this.entityData.set(LAMP, on);
        }

        double ground(double x, double z, double refY) {
            int bx = Mth.floor(x);
            int bz = Mth.floor(z);
            int top = Mth.floor(refY) + 4;
            int bot = Math.max(Mth.floor(refY) - 14, this.level().getMinBuildHeight());
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int y = top; y >= bot; --y) {
                mp.set(bx, y, bz);
                BlockState bs = this.level().getBlockState((BlockPos) mp);
                if (bs.is(BlockTags.LEAVES) || bs.is(BlockTags.SAPLINGS)) continue;
                FluidState fs = bs.getFluidState();
                if (!fs.isEmpty()) {
                    return (float) y + fs.getHeight((BlockGetter) this.level(), (BlockPos) mp);
                }
                VoxelShape sh = bs.getCollisionShape((BlockGetter) this.level(), (BlockPos) mp);
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
                    double fo = b == 1 ? 1.5 : (double) b * 1.7;
                    best = Math.max(best, this.ground(cx + px * (double) a * 1.6 + fx * fo, cz + pz * (double) a * 1.6 + fz * fo, ref));
                }
            }
            return best;
        }

        Vec3 dismountSpot() {
            double rad = Math.toRadians(this.getYRot());
            double x = this.getX() - Math.cos(rad) * 3.6;
            double z = this.getZ() - Math.sin(rad) * 3.6;
            double g = this.ground(x, z, this.getY() + 2.0);
            return new Vec3(x, g + 0.05, z);
        }

        @Override
        public Vec3 getDismountLocationForPassenger(LivingEntity p) {
            return this.dismountSpot();
        }

        @Override
        protected void addPassenger(Entity e) {
            super.addPassenger(e);
            e.fallDistance = 0.0f;
            if (e instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) e;
                sp.getPersistentData().putInt("pol_nofall", 100);
                if (!this.level().isClientSide) {
                    Vec3 d = this.dismountSpot();
                    sp.connection.teleport(d.x, d.y, d.z, sp.getYRot(), sp.getXRot());
                }
            }
        }

        @Override
        public void remove(Entity.RemovalReason r) {
            if (!this.level().isClientSide) {
                this.clearLight();
            }
            super.remove(r);
        }

        List<BlockPos> cells(double nx, double ny, double nz, float yaw, double f0, double f1, double lat, double yLo, double yHi) {
            double rad = Math.toRadians(yaw);
            double c = Math.cos(rad);
            double s = Math.sin(rad);
            double ext = Math.max(Math.abs(f0), Math.abs(f1)) + lat;
            int x0 = Mth.floor(nx - ext);
            int x1 = Mth.floor(nx + ext);
            int z0 = Mth.floor(nz - ext);
            int z1 = Mth.floor(nz + ext);
            int y0 = Mth.floor(ny + yLo);
            int y1 = Mth.floor(ny + yHi - 0.001);
            ArrayList<BlockPos> out = new ArrayList<BlockPos>();
            for (int x = x0; x <= x1; ++x) {
                for (int z = z0; z <= z1; ++z) {
                    double dx = (double) x + 0.5 - nx;
                    double dz = (double) z + 0.5 - nz;
                    double la = dx * c + dz * s;
                    double fw = -dx * s + dz * c;
                    if (Math.abs(la) > lat || fw < f0 || fw > f1) continue;
                    for (int y = y0; y <= y1; ++y) {
                        out.add(new BlockPos(x, y, z));
                    }
                }
            }
            return out;
        }

        boolean floorCell(BlockState st, BlockPos bp, double ny) {
            VoxelShape sh = st.getCollisionShape((BlockGetter) this.level(), bp);
            return !sh.isEmpty() && (double) bp.getY() + sh.max(Direction.Axis.Y) <= ny + 0.12;
        }

        boolean breakable(ServerLevel sl, BlockPos bp, BlockState st, Set<Long> placed) {
            if (st.isAir()) {
                return false;
            }
            if (!st.getFluidState().isEmpty() && st.getBlock() instanceof LiquidBlock) {
                return false;
            }
            if (st.hasBlockEntity()) {
                return false;
            }
            if (st.getDestroySpeed((BlockGetter) sl, bp) < 0.0f) {
                return false;
            }
            if (PoliciaAlbanil.LIVE.contains(bp.asLong())) {
                return false;
            }
            if (placed != null && placed.contains(bp.asLong())) {
                return false;
            }
            return PoliciaPlaced.natural(st);
        }

        void dig(ServerLevel sl, ServerPlayer driver, double nx, double ny, double nz, float yaw) {
            PoliciaPlaced pp = PoliciaPlaced.get((Level) sl);
            Set<Long> placed = pp == null ? null : pp.set;
            List<BlockPos> all = this.cells(nx, ny, nz, yaw, 2.4, 5.3, 2.1, 0.05 + (double) this.arm * 1.8, 4.6);
            all.addAll(this.cells(nx, ny, nz, yaw, -3.9, 2.4, 2.4, 0.3, 4.7));
            SimpleContainer inv = driver == null ? null : PoliciaTractor.inv(driver);
            int broken = 0;
            int fx = 0;
            boolean full = false;
            mute = true;
            try {
                for (BlockPos bp : all) {
                    if (broken >= 200) {
                        break;
                    }
                    BlockState st = sl.getBlockState(bp);
                    if (st.isAir() || !this.breakable(sl, bp, st, placed) || this.floorCell(st, bp, ny)) continue;
                    if (inv != null) {
                        for (ItemStack it : Block.getDrops(st, sl, bp, null, this, ItemStack.EMPTY)) {
                            if (inv.addItem(it).isEmpty()) continue;
                            full = true;
                        }
                    }
                    if (fx < 24) {
                        sl.destroyBlock(bp, false, (Entity) this);
                        ++fx;
                    } else {
                        sl.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                    }
                    ++broken;
                }
            } finally {
                mute = false;
            }
            if (inv != null && broken > 0) {
                PoliciaTractor.saveInv(driver, inv);
            }
            if (full && driver != null && this.tickCount - this.fullMsg > 200) {
                this.fullMsg = this.tickCount;
                PoliciaMod.msg(driver, "Inventario del tractor lleno: lo que rompe se pierde (U para vaciarlo)");
            }
        }

        int solid(ServerLevel sl, double nx, double ny, double nz, float yaw) {
            int n = 0;
            List<BlockPos> all = this.cells(nx, ny, nz, yaw, 2.4, 5.3, 2.1, 0.05 + (double) this.arm * 1.8, 4.6);
            all.addAll(this.cells(nx, ny, nz, yaw, -3.9, 2.4, 2.4, 0.3, 4.7));
            for (BlockPos bp : all) {
                VoxelShape sh;
                BlockState st = sl.getBlockState(bp);
                if (st.isAir() || (sh = st.getCollisionShape((BlockGetter) sl, bp)).isEmpty() || (double) bp.getY() + sh.max(Direction.Axis.Y) <= ny + 0.12) continue;
                ++n;
            }
            return n;
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
                double fw = -dx * s + dz * c - 0.6;
                if (Math.abs(lat) >= 2.7 || Math.abs(fw) >= 4.9 || pl.getY() > this.getY() + 4.8) continue;
                double nl = lat;
                double nf = fw;
                double ol = 2.8 - Math.abs(lat);
                if (ol <= (of = 5.0 - Math.abs(fw))) {
                    nl = (double) (lat < 0.0 ? -1 : 1) * 2.8;
                } else {
                    nf = (double) (fw < 0.0 ? -1 : 1) * 5.0;
                }
                double nx = this.getX() + nl * c - (nf += 0.6) * s;
                double nz = this.getZ() + nl * s + nf * c;
                if (pl instanceof ServerPlayer) {
                    ServerPlayer sp = (ServerPlayer) pl;
                    sp.connection.teleport(nx, pl.getY(), nz, pl.getYRot(), pl.getXRot());
                    continue;
                }
                pl.setPos(nx, pl.getY(), nz);
            }
        }

        void drive(ServerLevel sl, Entity r) {
            double g;
            ServerPlayer sp;
            ServerPlayer drv;
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
            this.speed = fw != 0.0f ? (this.speed += (double) fw * 0.04) : (this.speed *= 0.85);
            this.speed = Mth.clamp(this.speed, -0.16, 0.3);
            if (Math.abs(this.speed) < 0.004) {
                this.speed = 0.0;
            }
            float dyaw = -st * (this.speed != 0.0 ? 2.0f : 1.4f);
            float nyaw = this.getYRot() + dyaw;
            double rad = Math.toRadians(nyaw);
            double nx = this.getX() - Math.sin(rad) * this.speed;
            double nz = this.getZ() + Math.cos(rad) * this.speed;
            ServerPlayer serverPlayer = drv = r instanceof ServerPlayer && (sp = (ServerPlayer) r).getUUID().equals(this.owner) ? sp : null;
            if (this.speed != 0.0 || dyaw != 0.0f) {
                this.dig(sl, drv, nx, this.getY(), nz, nyaw);
                if (this.solid(sl, nx, this.getY(), nz, nyaw) > this.solid(sl, this.getX(), this.getY(), this.getZ(), this.getYRot())) {
                    this.speed = 0.0;
                    nx = this.getX();
                    nz = this.getZ();
                    if (dyaw != 0.0f) {
                        this.dig(sl, drv, nx, this.getY(), nz, nyaw);
                        if (this.solid(sl, nx, this.getY(), nz, nyaw) > this.solid(sl, this.getX(), this.getY(), this.getZ(), this.getYRot())) {
                            nyaw = this.getYRot();
                            dyaw = 0.0f;
                        }
                    } else {
                        nyaw = this.getYRot();
                    }
                }
            }
            if ((g = this.footGround(nx, nz, nyaw, this.getY())) - this.getY() > 2.3) {
                this.speed = 0.0;
                nx = this.getX();
                nz = this.getZ();
                nyaw = this.getYRot();
                dyaw = 0.0f;
                g = this.footGround(nx, nz, nyaw, this.getY());
            }
            double ny2 = this.getY() + Mth.clamp(g - this.getY(), -0.7, 0.5);
            this.setPos(nx, ny2, nz);
            this.setYRot(nyaw);
            this.wh += (float) (this.speed / 1.15);
            this.steer += (st * 28.0f - this.steer) * 0.3f;
        }

        @Override
        public void tick() {
            super.tick();
            this.prevWH = this.wh;
            this.prevArm = this.arm;
            this.prevSteer = this.steer;
            if (this.level().isClientSide) {
                if (this.lsteps > 0) {
                    double k = 1.0 / (double) this.lsteps;
                    this.setPos(this.getX() + (this.lx - this.getX()) * k, this.getY() + (this.ly - this.getY()) * k, this.getZ() + (this.lz - this.getZ()) * k);
                    this.setYRot(this.getYRot() + Mth.wrapDegrees(this.lyr - this.getYRot()) * (float) k);
                    --this.lsteps;
                }
                this.wh = this.entityData.get(WH);
                this.arm = this.entityData.get(ARM);
                this.steer = this.entityData.get(STEER);
                return;
            }
            this.setDeltaMovement(Vec3.ZERO);
            ServerLevel sl = (ServerLevel) this.level();
            Entity r = this.getFirstPassenger();
            if (this.armT > 0) {
                --this.armT;
                this.arm = Mth.clamp(this.arm + (float) this.armDir * 0.035f, 0.0f, 1.0f);
            }
            if (r != null) {
                this.drive(sl, r);
                this.pushOut(r);
                this.idle = 0;
            } else {
                this.speed = 0.0;
                this.steer *= 0.7f;
                ++this.idle;
                this.pushOut(null);
            }
            this.entityData.set(WH, this.wh);
            this.entityData.set(ARM, this.arm);
            this.entityData.set(STEER, this.steer);
            if (r != null) {
                if (Math.abs(this.speed) > 0.02 && this.tickCount % 5 == 0) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 0.6f, 0.4f);
                } else if (this.tickCount % 12 == 0) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.PLAYERS, 0.35f, 0.4f);
                }
                if (this.tickCount % 4 == 0) {
                    double rad = Math.toRadians(this.getYRot());
                    sl.sendParticles((ParticleOptions) ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX() + Math.sin(rad) * 3.0 + Math.cos(rad) * 0.8, this.getY() + 4.4, this.getZ() - Math.cos(rad) * 3.0 + Math.sin(rad) * 0.8, 1, 0.05, 0.1, 0.05, 0.01);
                }
            }
            if (this.lampOn() && this.tickCount % 4 == 0) {
                this.lampTick(sl, r);
            }
            if (this.tickCount % 20 == 0 && this.owner != null) {
                ServerPlayer o = sl.getServer().getPlayerList().getPlayer(this.owner);
                if (o == null || !o.isAlive()) {
                    this.finish(sl);
                    return;
                }
                o.getPersistentData().putInt("alb_tact", Math.max(1, 900 - this.tickCount));
                if (this.tickCount == 700) {
                    PoliciaMod.msg(o, "El tractor se retira en 10 s");
                }
            }
            if (this.idle > 6000 || this.tickCount > 900) {
                this.finish(sl);
            }
        }

        void lampTick(ServerLevel sl, Entity r) {
            BlockPos pos;
            Vec3 dir;
            Vec3 eye;
            if (r != null) {
                eye = r.getEyePosition();
                dir = r.getLookAngle();
            } else {
                double rad = Math.toRadians(this.getYRot());
                dir = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
                eye = new Vec3(this.getX(), this.getY() + 2.4, this.getZ()).add(dir.scale(3.0));
            }
            Vec3 end = eye.add(dir.scale(48.0));
            BlockHitResult hit = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) this));
            BlockPos blockPos = pos = hit.getType() == HitResult.Type.MISS ? BlockPos.containing((Position) end) : hit.getBlockPos().relative(hit.getDirection());
            if (this.light != null && this.light.equals((Object) pos)) {
                return;
            }
            if (!sl.getBlockState(pos).isAir()) {
                return;
            }
            this.clearLight();
            sl.setBlock(pos, Blocks.SEA_LANTERN.defaultBlockState(), 3);
            this.light = pos;
        }

        void clearLight() {
            if (this.light == null) {
                return;
            }
            if (this.level().getBlockState(this.light).is(Blocks.SEA_LANTERN)) {
                this.level().setBlock(this.light, Blocks.AIR.defaultBlockState(), 3);
            }
            this.light = null;
        }

        void finish(ServerLevel sl) {
            this.clearLight();
            sl.sendParticles((ParticleOptions) ParticleTypes.POOF, this.getX(), this.getY() + 0.6, this.getZ(), 18, 1.0, 0.4, 1.0, 0.03);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.PLAYERS, 1.0f, 0.5f);
            if (this.owner != null) {
                ACTIVE.remove(this.owner);
                ServerPlayer o = sl.getServer().getPlayerList().getPlayer(this.owner);
                if (o != null) {
                    o.getPersistentData().putInt("alb_tcd", 700);
                    o.getPersistentData().putInt("alb_tact", 0);
                    PoliciaMod.msg(o, "Tractor retirado");
                    PoliciaMod.sync(o);
                }
            }
            this.ejectPassengers();
            this.discard();
        }
    }
}
