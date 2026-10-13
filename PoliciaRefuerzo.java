package com.example.policia;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

// Policias de refuerzo (habilidad REFUERZO): invocacion, ordenes
// (proteger/atacar/dividirse) y la IA del agente (defensa, reparto de
// amenazas, seguimiento del dueno).
public class PoliciaRefuerzo {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create((IForgeRegistry<EntityType<?>>) ForgeRegistries.ENTITY_TYPES, "policia");
    public static final RegistryObject<EntityType<AgentEntity>> AGENT = ENTITIES.register("agente", () -> EntityType.Builder.of(AgentEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).fireImmune().build("policia:agente"));
    public static final int[] COUNT = new int[]{0, 1, 3, 5, 6};
    public static final String[] SWORD = new String[]{"", "piedra", "hierro", "diamante", "netherita"};
    static final int LIFE = 700;
    static final int COOLDOWN = 300;
    static final String[] ORDERS = new String[]{"PROTEGER (te cuidan de cerca)", "ATACAR al que te ataco", "DIVIDIRSE y atacar a los enemigos"};

    public static void attrs(EntityAttributeCreationEvent e) {
        e.put(AGENT.get(), AgentEntity.attrs().build());
    }

    static Item sword(int lv) {
        switch (lv) {
            case 1: {
                return Items.STONE_SWORD;
            }
            case 2: {
                return Items.IRON_SWORD;
            }
            case 3: {
                return Items.DIAMOND_SWORD;
            }
        }
        return Items.NETHERITE_SWORD;
    }

    public static void cycle(ServerPlayer p) {
        Object object;
        CompoundTag d = p.getPersistentData();
        int m = (d.getInt("pol_rord") + 1) % ORDERS.length;
        d.putInt("pol_rord", m);
        PoliciaMod.msg(p, "Orden de refuerzos: " + ORDERS[m]);
        // Aproximacion de sonido de confirmacion de orden
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.0f + 0.25f * (float) m);
        if (m == 0 && (object = p.level()) instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel) object;
            for (AgentEntity a : sl.getEntitiesOfClass(AgentEntity.class, p.getBoundingBox().inflate(128.0), x -> p.getUUID().equals(x.owner))) {
                a.setTarget(null);
            }
        }
    }

    public static void use(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        int lv = PoliciaMod.rlevel(p);
        if (lv <= 0) {
            return;
        }
        ServerLevel sl = p.serverLevel();
        List<AgentEntity> mine = sl.getEntitiesOfClass(AgentEntity.class, p.getBoundingBox().inflate(128.0), a -> p.getUUID().equals(a.owner));
        if (!mine.isEmpty()) {
            PoliciaMod.msg(p, "Tus refuerzos ya estan en el campo");
            return;
        }
        if (d.getInt("pol_rcd") > 0) {
            PoliciaMod.msg(p, "Refuerzo en enfriamiento: " + (d.getInt("pol_rcd") + 19) / 20 + " s");
            return;
        }
        int n = COUNT[lv];
        for (int i = 0; i < n; ++i) {
            AgentEntity a2 = AGENT.get().create(sl);
            if (a2 == null) continue;
            a2.owner = p.getUUID();
            a2.tier = lv;
            a2.equip(lv);
            double ang = Math.PI * 2 * (double) i / (double) n;
            double x = p.getX() + Math.cos(ang) * 2.6;
            double z = p.getZ() + Math.sin(ang) * 2.6;
            for (int dy = 0; dy <= 2; ++dy) {
                a2.moveTo(x, p.getY() + (double) dy, z, p.getYRot(), 0.0f);
                if (sl.noCollision(a2)) break;
                if (dy != 2) continue;
                a2.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
            }
            sl.addFreshEntity(a2);
            sl.sendParticles((ParticleOptions) ParticleTypes.HAPPY_VILLAGER, a2.getX(), a2.getY() + 0.5, a2.getZ(), 8, 0.3, 0.5, 0.3, 0.03);
        }
        d.putInt("pol_rcd", 300);
        // Aproximacion de sonidos de invocacion de refuerzos
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.4f);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0f, 0.8f);
        PoliciaMod.msg(p, "Refuerzo nivel " + lv + ": " + n + (n == 1 ? " policia" : " policias") + " con espada de " + SWORD[lv]);
        PoliciaMod.sync(p);
    }

    public static class AgentEntity extends PathfinderMob {
        UUID owner;
        int tier = 1;
        int life;
        int blockT;

        public AgentEntity(EntityType<? extends AgentEntity> type, Level level) {
            super(type, level);
            this.xpReward = 0;
            this.setPersistenceRequired();
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.ARMOR, 0.3);
        }

        void equip(int lv) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike) PoliciaRefuerzo.sword(lv)));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack((ItemLike) PoliciaShield.SHIELD.get()));
            this.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
        }

        ServerPlayer ownerPlayer() {
            Level level;
            if (this.owner == null || !((level = this.level()) instanceof ServerLevel)) {
                return null;
            }
            ServerLevel sl = (ServerLevel) level;
            return sl.getServer().getPlayerList().getPlayer(this.owner);
        }

        boolean threat(LivingEntity e) {
            Mob m;
            if (e instanceof AgentEntity || this.owner != null && e.getUUID().equals(this.owner)) {
                return false;
            }
            if (e instanceof PoliciaMision.Pj) {
                PoliciaMision.Pj bj = (PoliciaMision.Pj) e;
                return bj.hostile();
            }
            if (e instanceof Enemy) {
                return true;
            }
            return this.owner != null && e instanceof Mob && (m = (Mob) e).getTarget() != null && this.owner.equals(m.getTarget().getUUID());
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(1, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
            this.goalSelector.addGoal(3, new FollowGoal(this));
            this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0f));
            this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new SpreadGoal(this));
            this.targetSelector.addGoal(2, new HurtByTargetGoal(this, new Class[0]));
        }

        @Override
        public boolean canAttack(LivingEntity e) {
            if (e instanceof AgentEntity || this.owner != null && e.getUUID().equals(this.owner)) {
                return false;
            }
            return super.canAttack(e);
        }

        @Override
        public boolean hurt(DamageSource s, float amount) {
            Entity src = s.getEntity();
            if (src instanceof AgentEntity || src != null && this.owner != null && src.getUUID().equals(this.owner)) {
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
            return NetworkHooks.getEntitySpawningPacket(this);
        }

        void poof() {
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) level;
                sl.sendParticles((ParticleOptions) ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            }
            this.discard();
        }

        @Override
        public void tick() {
            ServerPlayer o;
            LivingEntity t2;
            ServerPlayer ow;
            super.tick();
            if (this.level().isClientSide) {
                return;
            }
            LivingEntity tg = this.getTarget();
            if (tg != null && tg.isAlive() && this.distanceToSqr(tg) < 36.0 || this.hurtTime > 0) {
                this.blockT = 25;
            }
            if (this.blockT > 0) {
                --this.blockT;
                if (!this.isUsingItem()) {
                    this.startUsingItem(InteractionHand.OFF_HAND);
                }
            } else if (this.isUsingItem()) {
                this.stopUsingItem();
            }
            if (this.tickCount % 10 == 0 && (ow = this.ownerPlayer()) != null && ow.getPersistentData().getInt("pol_rord") == 0 && (t2 = this.getTarget()) != null && t2.distanceToSqr(ow) > 256.0) {
                this.setTarget(null);
            }
            ++this.life;
            if (this.life > 700) {
                this.poof();
                return;
            }
            if (!(this.tickCount % 20 != 0 || (o = this.ownerPlayer()) != null && o.isAlive() && o.level() == this.level())) {
                this.poof();
            }
        }
    }

    static class DefendGoal extends TargetGoal {
        final AgentEntity a;
        final boolean byOwner;
        LivingEntity cand;
        int stamp;
        int last;

        DefendGoal(AgentEntity a, boolean byOwner) {
            super(a, false);
            this.a = a;
            this.byOwner = byOwner;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            int st;
            ServerPlayer o = this.a.ownerPlayer();
            if (o == null) {
                return false;
            }
            LivingEntity t = this.byOwner ? o.getLastHurtByMob() : o.getLastHurtMob();
            int n = st = this.byOwner ? o.getLastHurtByMobTimestamp() : o.getLastHurtMobTimestamp();
            if (t == null || st == this.last || !t.isAlive() || !this.a.canAttack(t)) {
                return false;
            }
            if (!this.canAttack(t, TargetingConditions.DEFAULT)) {
                return false;
            }
            this.cand = t;
            this.stamp = st;
            return true;
        }

        @Override
        public void start() {
            this.mob.setTarget(this.cand);
            this.last = this.stamp;
            super.start();
        }
    }

    static class SpreadGoal extends TargetGoal {
        final AgentEntity a;
        LivingEntity cand;

        SpreadGoal(AgentEntity a) {
            super(a, false);
            this.a = a;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            double dd;
            boolean shared;
            LivingEntity hm;
            int mode;
            if ((this.a.tickCount + this.a.getId()) % 8 != 0) {
                return false;
            }
            ServerPlayer o = this.a.ownerPlayer();
            if (o == null) {
                return false;
            }
            LivingEntity my = this.a.getTarget();
            if (my != null && !my.isAlive()) {
                this.a.setTarget(null);
                my = null;
            }
            if ((mode = o.getPersistentData().getInt("pol_rord")) == 1) {
                LivingEntity hb1 = o.getLastHurtByMob();
                if (hb1 != null && hb1.isAlive() && o.tickCount - o.getLastHurtByMobTimestamp() < 400 && !(hb1 instanceof AgentEntity) && this.a.canAttack(hb1) && this.a.distanceToSqr(hb1) < 1600.0 && hb1 != my) {
                    this.cand = hb1;
                    return true;
                }
                return false;
            }
            double rad = mode == 0 ? 9.0 : 30.0;
            ArrayList<LivingEntity> threats = new ArrayList<>();
            for (LivingEntity e : this.a.level().getEntitiesOfClass(LivingEntity.class, o.getBoundingBox().inflate(rad), x -> x.isAlive() && this.a.threat(x) && this.a.canAttack(x))) {
                threats.add(e);
            }
            LivingEntity hb = o.getLastHurtByMob();
            if (hb != null && hb.isAlive() && !threats.contains(hb) && this.a.canAttack(hb) && this.a.distanceToSqr(hb) < 900.0) {
                threats.add(hb);
            }
            if ((hm = o.getLastHurtMob()) != null && hm.isAlive() && !threats.contains(hm) && this.a.canAttack(hm) && this.a.distanceToSqr(hm) < 900.0) {
                threats.add(hm);
            }
            if (threats.isEmpty()) {
                return false;
            }
            HashSet<LivingEntity> claimed = new HashSet<>();
            for (AgentEntity b : this.a.level().getEntitiesOfClass(AgentEntity.class, o.getBoundingBox().inflate(48.0), x -> x != this.a && o.getUUID().equals(x.owner))) {
                if (b.getTarget() == null) continue;
                claimed.add(b.getTarget());
            }
            boolean bl = shared = my != null && claimed.contains(my);
            if (my != null && !shared) {
                return false;
            }
            LivingEntity best = null;
            double bd = 1.0E18;
            for (LivingEntity e : threats) {
                if (claimed.contains(e) || !((dd = this.a.distanceToSqr(e)) < bd)) continue;
                bd = dd;
                best = e;
            }
            if (best == null) {
                if (my != null) {
                    return false;
                }
                for (LivingEntity e : threats) {
                    dd = this.a.distanceToSqr(e);
                    if (!(dd < bd)) continue;
                    bd = dd;
                    best = e;
                }
            }
            if (best == null || best == my) {
                return false;
            }
            this.cand = best;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            this.a.setTarget(this.cand);
        }

        @Override
        public void stop() {
        }
    }

    static class FollowGoal extends Goal {
        final AgentEntity a;

        FollowGoal(AgentEntity a) {
            this.a = a;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ServerPlayer o = this.a.ownerPlayer();
            return o != null && this.a.getTarget() == null && this.a.distanceToSqr(o) > 36.0;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer o = this.a.ownerPlayer();
            return o != null && this.a.getTarget() == null && this.a.distanceToSqr(o) > 9.0;
        }

        @Override
        public void tick() {
            ServerPlayer o = this.a.ownerPlayer();
            if (o == null) {
                return;
            }
            if (this.a.distanceToSqr(o) > 576.0) {
                this.a.moveTo(o.getX() + (this.a.getRandom().nextDouble() - 0.5) * 3.0, o.getY(), o.getZ() + (this.a.getRandom().nextDouble() - 0.5) * 3.0, this.a.getYRot(), 0.0f);
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
