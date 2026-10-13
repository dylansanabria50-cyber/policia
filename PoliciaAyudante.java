package com.example.policia;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// Ayudantes mineros: oficial Albanil puede invocar peones que pican bloques
// por el, siguiendo una zona marcada o buscando minerales cercanos.
public class PoliciaAyudante {
    public static final RegistryObject<EntityType<Helper>> AYU = PoliciaRefuerzo.ENTITIES.register("ayudante", () -> EntityType.Builder.of(Helper::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).fireImmune().build("policia:ayudante"));
    static final int[] COUNT = new int[]{0, 1, 3, 4};
    static final String[] PICKS = new String[]{"", "piedra", "hierro", "diamante"};
    static final String[] ORDERS = new String[]{"Seguir y defender", "Picar delante de mi", "Buscar minerales"};
    static final int LIFE = 1200;
    static final int CD = 600;
    static final Map<UUID, SimpleContainer> BANKS = new HashMap<UUID, SimpleContainer>();

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaAyudante::attrs);
    }

    static void attrs(EntityAttributeCreationEvent e) {
        e.put(AYU.get(), Helper.attrs().build());
    }

    static Item pickItem(int t) {
        switch (t) {
            case 1: {
                return Items.STONE_PICKAXE;
            }
            case 2: {
                return Items.IRON_PICKAXE;
            }
        }
        return Items.WOODEN_PICKAXE;
    }

    static boolean isOre(BlockState st) {
        return st.is(Tags.Blocks.ORES);
    }

    static String esName(String p) {
        if (p.contains("coal")) {
            return "carbon";
        }
        if (p.contains("iron")) {
            return "hierro";
        }
        if (p.contains("copper")) {
            return "cobre";
        }
        if (p.contains("gold")) {
            return "oro";
        }
        if (p.contains("redstone")) {
            return "redstone";
        }
        if (p.contains("lapis")) {
            return "lapislazuli";
        }
        if (p.contains("emerald")) {
            return "esmeralda";
        }
        if (p.contains("diamond")) {
            return "diamante";
        }
        if (p.contains("quartz")) {
            return "cuarzo";
        }
        if (p.contains("debris")) {
            return "restos antiguos";
        }
        return p.replace('_', ' ');
    }

    static List<Helper> mine(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Helper.class, p.getBoundingBox().inflate(256.0), a -> p.getUUID().equals(a.owner));
    }

    static void clear(ServerPlayer p) {
        for (Helper h : PoliciaAyudante.mine(p)) {
            h.poof();
        }
    }

    static SimpleContainer bank(ServerPlayer p) {
        UUID id = p.getUUID();
        SimpleContainer c = BANKS.get(id);
        if (c != null) {
            return c;
        }
        c = new SimpleContainer(36);
        ListTag l = p.getPersistentData().getList("alb_bank", 10);
        for (Tag t : l) {
            CompoundTag e = (CompoundTag) t;
            int s = e.getInt("Slot");
            if (s < 0 || s >= 36) continue;
            c.setItem(s, ItemStack.of(e));
        }
        MinecraftServer srv = p.server;
        SimpleContainer fc = c;
        c.addListener(cc -> {
            ServerPlayer q = srv.getPlayerList().getPlayer(id);
            if (q != null) {
                PoliciaAyudante.saveBank(q, fc);
            }
        });
        BANKS.put(id, c);
        return c;
    }

    static void saveBank(ServerPlayer p, SimpleContainer c) {
        ListTag l = new ListTag();
        for (int i = 0; i < c.getContainerSize(); ++i) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("Slot", i);
            s.save(e);
            l.add(e);
        }
        p.getPersistentData().put("alb_bank", (Tag) l);
    }

    static boolean hasItems(ServerPlayer p) {
        return !PoliciaAyudante.bank(p).isEmpty();
    }

    static void openBank(ServerPlayer p) {
        if (p.getPersistentData().getInt("alb_hl") < 1) {
            PoliciaMod.msg(p, "Aun no tienes ayudantes: desbloquea AYUDANTE");
            return;
        }
        SimpleContainer b = PoliciaAyudante.bank(p);
        p.openMenu((MenuProvider) new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x4, id, inv, (Container) b, 4), Component.literal("Inventario de los ayudantes (una fila por ayudante)")));
    }

    static void answer(ServerPlayer p, boolean yes) {
        boolean any = false;
        for (Helper h : PoliciaAyudante.mine(p)) {
            if (h.offer == null) continue;
            any = true;
            if (yes) {
                h.ore = h.offer;
                PoliciaMod.msg(p, "Ayudante " + (h.idx + 1) + ": voy a cavar hacia alli");
            } else {
                h.bad.add(h.offer.asLong());
            }
            h.offer = null;
        }
        if (!any) {
            PoliciaMod.msg(p, "Ningun ayudante te esta preguntando");
        }
    }

    static void use(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        int lv = Math.max(0, Math.min(3, d.getInt("alb_hl")));
        if (lv <= 0) {
            PoliciaMod.msg(p, "Desbloquea primero: AYUDANTE");
            return;
        }
        if (d.getInt("alb_ht") > 0) {
            PoliciaMod.msg(p, "Tus ayudantes ya estan en el campo");
            return;
        }
        if (d.getInt("alb_hcd") > 0) {
            PoliciaMod.msg(p, "Ayudantes en enfriamiento: " + (d.getInt("alb_hcd") + 19) / 20 + " s");
            return;
        }
        ServerLevel sl = p.serverLevel();
        int n = COUNT[lv];
        for (int i = 0; i < n; ++i) {
            Helper a = AYU.get().create(sl);
            if (a == null) continue;
            a.owner = p.getUUID();
            a.tier = lv;
            a.idx = i;
            a.equip(lv);
            double ang = Math.PI * 2 * (double) i / (double) n;
            double x = p.getX() + Math.cos(ang) * 2.2;
            double z = p.getZ() + Math.sin(ang) * 2.2;
            for (int dy = 0; dy <= 2; ++dy) {
                a.moveTo(x, p.getY() + (double) dy, z, p.getYRot(), 0.0f);
                if (sl.noCollision(a)) break;
                if (dy != 2) continue;
                a.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
            }
            sl.addFreshEntity(a);
            sl.sendParticles((ParticleOptions) ParticleTypes.HAPPY_VILLAGER, a.getX(), a.getY() + 0.5, a.getZ(), 8, 0.3, 0.5, 0.3, 0.03);
        }
        d.putInt("alb_ht", 1200);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.8f, 1.1f);
        PoliciaMod.msg(p, "AYUDANTE nivel " + lv + ": " + n + (n == 1 ? " ayudante" : " ayudantes") + " con pico de " + PICKS[lv] + ". H: orden (" + ORDERS[Math.max(0, Math.min(2, d.getInt("alb_mode")))] + "), U: inventario");
        PoliciaMod.sync(p);
    }

    public static class Helper extends PathfinderMob {
        UUID owner;
        int tier = 1;
        int idx;
        int life;
        int mt;
        int stuck;
        int warn;
        int offerT;
        int wt;
        int wmax;
        BlockPos ore;
        BlockPos offer;
        BlockPos lastPos;
        BlockPos work;
        final Set<Long> bad = new HashSet<Long>();

        public Helper(EntityType<? extends Helper> type, Level level) {
            super(type, level);
            this.xpReward = 0;
            this.setPersistenceRequired();
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.ARMOR, 0.2);
        }

        void equip(int t) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike) PoliciaAyudante.pickItem(t)));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        }

        ServerPlayer ownerPlayer() {
            Level level;
            if (this.owner == null || !((level = this.level()) instanceof ServerLevel)) {
                return null;
            }
            ServerLevel sl = (ServerLevel) level;
            return sl.getServer().getPlayerList().getPlayer(this.owner);
        }

        boolean near(LivingEntity x) {
            ServerPlayer o = this.ownerPlayer();
            return o != null && o.distanceToSqr((Entity) x) < 400.0;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(1, (Goal) new FloatGoal((Mob) this));
            this.goalSelector.addGoal(2, (Goal) new MeleeAttackGoal((PathfinderMob) this, 1.2, true));
            this.goalSelector.addGoal(3, (Goal) new FollowGoal(this));
            this.goalSelector.addGoal(5, (Goal) new LookAtPlayerGoal((Mob) this, Player.class, 8.0f));
            this.goalSelector.addGoal(6, (Goal) new RandomLookAroundGoal((Mob) this));
            this.targetSelector.addGoal(1, (Goal) new HurtByTargetGoal((PathfinderMob) this, new Class[0]));
            this.targetSelector.addGoal(2, (Goal) new NearestAttackableTargetGoal((Mob) this, Monster.class, 10, true, false, x -> this.near((LivingEntity) x)));
        }

        @Override
        public boolean canAttack(LivingEntity e) {
            if (e instanceof Helper || e instanceof PoliciaRefuerzo.AgentEntity || this.owner != null && e.getUUID().equals(this.owner)) {
                return false;
            }
            return super.canAttack(e);
        }

        @Override
        public boolean hurt(DamageSource s, float amount) {
            Entity src = s.getEntity();
            if (src instanceof Helper || src != null && this.owner != null && src.getUUID().equals(this.owner)) {
                return false;
            }
            return super.hurt(s, amount);
        }

        @Override
        public boolean shouldRenderAtSqrDistance(double d) {
            return false;
        }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() {
            return NetworkHooks.getEntitySpawningPacket((Entity) this);
        }

        void poof() {
            this.stopWork();
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) level;
                sl.sendParticles((ParticleOptions) ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            }
            this.discard();
        }

        boolean canBreak(BlockPos bp) {
            BlockState st = this.level().getBlockState(bp);
            if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) {
                return false;
            }
            float h = st.getDestroySpeed((BlockGetter) this.level(), bp);
            if (h < 0.0f || h > 20.0f) {
                return false;
            }
            if (PoliciaAlbanil.LIVE.contains(bp.asLong())) {
                return false;
            }
            if (PoliciaPlaced.has(this.level(), bp)) {
                return false;
            }
            if (!st.is(BlockTags.MINEABLE_WITH_PICKAXE) && !st.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                return false;
            }
            if (this.tier < 3 && st.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
                return false;
            }
            return this.tier >= 2 || !st.is(BlockTags.NEEDS_IRON_TOOL);
        }

        void startWork(BlockPos bp) {
            this.work = bp;
            this.wt = this.wmax = this.tier == 1 ? 30 : (this.tier == 2 ? 18 : 10);
            this.stuck = 0;
            this.getNavigation().stop();
        }

        void stopWork() {
            Level level;
            if (this.work != null && (level = this.level()) instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) level;
                sl.destroyBlockProgress(this.getId(), this.work, -1);
            }
            this.work = null;
        }

        void workTick() {
            ServerLevel sl = (ServerLevel) this.level();
            if (!this.canBreak(this.work) || this.work.distToCenterSqr(this.getX(), this.getEyeY(), this.getZ()) > 49.0) {
                this.stopWork();
                return;
            }
            this.getLookControl().setLookAt((double) this.work.getX() + 0.5, (double) this.work.getY() + 0.5, (double) this.work.getZ() + 0.5);
            if (this.wt % 6 == 0) {
                this.swing(InteractionHand.MAIN_HAND);
                SoundType t = sl.getBlockState(this.work).getSoundType();
                sl.playSound(null, this.work, t.getBreakSound(), SoundSource.BLOCKS, 0.6f, 0.9f);
            }
            sl.destroyBlockProgress(this.getId(), this.work, Math.min(9, (int) (10.0f * (float) (this.wmax - this.wt) / (float) this.wmax)));
            if (--this.wt <= 0) {
                this.finishWork();
            }
        }

        void finishWork() {
            ServerLevel sl = (ServerLevel) this.level();
            BlockPos bp = this.work;
            BlockState st = sl.getBlockState(bp);
            this.stopWork();
            if (!this.canBreak(bp)) {
                return;
            }
            List<ItemStack> drops = Block.getDrops(st, sl, bp, null, this, this.getMainHandItem());
            ServerPlayer o = this.ownerPlayer();
            for (ItemStack s : drops) {
                ItemStack rest = o == null ? s : this.store(o, s.copy());
                if (rest.isEmpty()) continue;
                Block.popResource((Level) sl, bp, rest);
            }
            sl.destroyBlock(bp, false, (Entity) this);
            this.swing(InteractionHand.MAIN_HAND);
        }

        ItemStack store(ServerPlayer o, ItemStack s) {
            int i;
            SimpleContainer b = PoliciaAyudante.bank(o);
            int base = Math.min(this.idx, 3) * 9;
            for (i = 0; i < 9 && !s.isEmpty(); ++i) {
                ItemStack c = b.getItem(base + i);
                if (c.isEmpty() || !ItemStack.isSameItemSameTags(c, s) || c.getCount() >= c.getMaxStackSize()) continue;
                int m = Math.min(s.getCount(), c.getMaxStackSize() - c.getCount());
                c.grow(m);
                s.shrink(m);
                b.setChanged();
            }
            for (i = 0; i < 9 && !s.isEmpty(); ++i) {
                if (!b.getItem(base + i).isEmpty()) continue;
                b.setItem(base + i, s.copy());
                s.setCount(0);
            }
            return s;
        }

        boolean zoneStep(ServerPlayer o) {
            CompoundTag d = o.getPersistentData();
            if (d.getInt("alb_zon") != 1) {
                if (this.warn-- <= 0) {
                    this.warn = 6;
                    if (this.idx == 0) {
                        PoliciaMod.msg(o, "Ayudantes: manten X para elegir el area donde picar");
                    }
                }
                return false;
            }
            BlockPos org = BlockPos.of(d.getLong("alb_zo"));
            Direction f = Direction.from3DDataValue(d.getInt("alb_zd"));
            Direction r = f.getClockWise();
            int len = d.getInt("alb_zl");
            int wid = d.getInt("alb_zw");
            int lo = -((wid - 1) / 2);
            int hi = wid / 2;
            ServerLevel sl = (ServerLevel) this.level();
            HashSet<Long> taken = new HashSet<Long>();
            for (Helper h : sl.getEntitiesOfClass(Helper.class, o.getBoundingBox().inflate(64.0), x -> x != this && o.getUUID().equals(x.owner))) {
                if (h.work == null) continue;
                taken.add(h.work.asLong());
            }
            BlockPos me = this.blockPosition();
            BlockPos best = null;
            double bd = 1.0E12;
            int bu = 0;
            int bf = 1;
            for (int fw = 1; fw <= len && best == null; ++fw) {
                for (int u = lo; u <= hi; ++u) {
                    for (int h = 0; h <= 2; ++h) {
                        double dd;
                        BlockPos bp = PoliciaAlbanil.at(org, f, r, fw, u, h);
                        if (this.bad.contains(bp.asLong()) || taken.contains(bp.asLong()) || (dd = bp.distSqr((Vec3i) me)) >= bd || !this.canBreak(bp)) continue;
                        bd = dd;
                        best = bp;
                        bu = u;
                        bf = fw;
                    }
                }
            }
            if (best == null) {
                return false;
            }
            if (best.distToCenterSqr(this.getX(), this.getEyeY(), this.getZ()) <= 20.0) {
                this.startWork(best);
                return true;
            }
            BlockPos ap = PoliciaAlbanil.at(org, f, r, bf - 1, bu, 0);
            this.stuck = me.equals((Object) this.lastPos) ? ++this.stuck : 0;
            this.lastPos = me;
            if (this.stuck > 12) {
                this.bad.add(best.asLong());
                this.stuck = 0;
                return false;
            }
            this.getNavigation().moveTo((double) ap.getX() + 0.5, (double) ap.getY(), (double) ap.getZ() + 0.5, 1.1);
            return true;
        }

        void announce(ServerPlayer o, BlockPos bp) {
            BlockState st = this.level().getBlockState(bp);
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(st.getBlock());
            String nm = PoliciaAyudante.esName(key == null ? "mineral" : key.getPath());
            int dist = (int) Math.sqrt(bp.distToCenterSqr(o.getX(), o.getY(), o.getZ()));
            PoliciaMod.msg(o, "Ayudante " + (this.idx + 1) + ": hay " + nm + " en " + bp.getX() + " " + bp.getY() + " " + bp.getZ() + " (a " + dist + " bloques). Y: cavar hacia alli, N: ignorar");
            o.level().playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
        }

        boolean oreStep(ServerPlayer o) {
            BlockPos[] cells;
            ServerLevel sl = (ServerLevel) this.level();
            if (this.ore != null && !PoliciaAyudante.isOre(sl.getBlockState(this.ore))) {
                this.ore = null;
            }
            if (this.ore == null) {
                this.stuck = 0;
                if (this.offer != null) {
                    if (--this.offerT <= 0 || !PoliciaAyudante.isOre(sl.getBlockState(this.offer))) {
                        this.bad.add(this.offer.asLong());
                        this.offer = null;
                    }
                    return false;
                }
                HashSet<Long> taken = new HashSet<Long>();
                for (Helper h : sl.getEntitiesOfClass(Helper.class, o.getBoundingBox().inflate(64.0), x -> x != this && o.getUUID().equals(x.owner))) {
                    if (h.offer != null) {
                        return false;
                    }
                    if (h.ore == null) continue;
                    taken.add(h.ore.asLong());
                }
                BlockPos me = this.blockPosition();
                BlockPos best = null;
                double bd = 1.0E12;
                for (int dx = -12; dx <= 12; ++dx) {
                    for (int dz = -12; dz <= 12; ++dz) {
                        for (int dy = -6; dy <= 6; ++dy) {
                            BlockPos bp = me.offset(dx, dy, dz);
                            double dd = bp.distSqr((Vec3i) me);
                            if (dd >= bd || this.bad.contains(bp.asLong()) || taken.contains(bp.asLong()) || !PoliciaAyudante.isOre(sl.getBlockState(bp)) || !this.canBreak(bp)) continue;
                            bd = dd;
                            best = bp;
                        }
                    }
                }
                if (best == null) {
                    return false;
                }
                this.offer = best;
                this.offerT = 400;
                this.announce(o, best);
                return false;
            }
            if (this.ore.distToCenterSqr(this.getX(), this.getEyeY(), this.getZ()) <= 20.0) {
                this.startWork(this.ore);
                return true;
            }
            BlockPos me = this.blockPosition();
            int dx = this.ore.getX() - me.getX();
            int dz = this.ore.getZ() - me.getZ();
            if (dx == 0 && dz == 0) {
                this.bad.add(this.ore.asLong());
                this.ore = null;
                return false;
            }
            Direction dir = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            BlockPos n = me.relative(dir);
            this.stuck = me.equals((Object) this.lastPos) ? ++this.stuck : 0;
            this.lastPos = me;
            if (this.stuck > 12) {
                this.bad.add(this.ore.asLong());
                this.ore = null;
                this.stuck = 0;
                return false;
            }
            for (BlockPos q : cells = new BlockPos[]{n, n.above()}) {
                if (sl.getBlockState(q).getCollisionShape((BlockGetter) sl, q).isEmpty()) continue;
                if (this.canBreak(q)) {
                    this.startWork(q);
                    return true;
                }
                this.bad.add(this.ore.asLong());
                this.ore = null;
                return false;
            }
            this.getNavigation().moveTo((double) n.getX() + 0.5, (double) n.getY(), (double) n.getZ() + 0.5, 1.1);
            return true;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.level().isClientSide) {
                return;
            }
            ++this.life;
            if (this.life > 1240) {
                this.poof();
                return;
            }
            ServerPlayer o = this.ownerPlayer();
            if (!(this.tickCount % 20 != 0 || o != null && o.isAlive() && o.level() == this.level())) {
                this.poof();
                return;
            }
            if (o == null) {
                return;
            }
            if (this.work != null) {
                if (this.getTarget() != null) {
                    this.stopWork();
                } else {
                    this.workTick();
                    return;
                }
            }
            if (this.getTarget() != null) {
                return;
            }
            int ord = o.getPersistentData().getInt("alb_mode");
            if (ord <= 0) {
                this.ore = null;
                this.offer = null;
                return;
            }
            if (this.mt > 0) {
                --this.mt;
                return;
            }
            boolean did = ord == 1 ? this.zoneStep(o) : this.oreStep(o);
            this.mt = did ? 3 : 30;
        }
    }

    static class FollowGoal extends Goal {
        final Helper a;

        FollowGoal(Helper a) {
            this.a = a;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ServerPlayer o = this.a.ownerPlayer();
            if (o == null || this.a.getTarget() != null || this.a.work != null) {
                return false;
            }
            int ord = o.getPersistentData().getInt("alb_mode");
            double lim = ord > 0 && (this.a.ore != null || ord == 1) && this.a.distanceToSqr((Entity) o) < 900.0 ? 900.0 : 36.0;
            return this.a.distanceToSqr((Entity) o) > lim;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer o = this.a.ownerPlayer();
            return o != null && this.a.getTarget() == null && this.a.work == null && this.a.distanceToSqr((Entity) o) > 9.0;
        }

        @Override
        public void tick() {
            ServerPlayer o = this.a.ownerPlayer();
            if (o == null) {
                return;
            }
            if (this.a.distanceToSqr((Entity) o) > 1600.0) {
                this.a.moveTo(o.getX() + (this.a.getRandom().nextFloat() - 0.5) * 3.0, o.getY(), o.getZ() + (this.a.getRandom().nextFloat() - 0.5) * 3.0, this.a.getYRot(), 0.0f);
                this.a.getNavigation().stop();
                return;
            }
            if (this.a.tickCount % 10 == 0) {
                this.a.getNavigation().moveTo((Entity) o, 1.3);
            }
        }

        @Override
        public void stop() {
            this.a.getNavigation().stop();
        }
    }
}
