package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegistryObject;

// Habilidades de inspiracion anime hechas "de verdad" en el juego (diseno,
// nombres y sonidos propios):
//  - Babosa curandera: una babosa de bloques real que salta, se divide en
//    babositas que se pegan a los aliados y los curan.
//  - Pildora monstruo: el jugador se transforma en un monstruo con cuernos
//    de alce (lo dibuja PoliciaAnimeCliente con articulaciones y fisica).
//  - Cartas de mercancia: tres cartas que salen volando y hacen su efecto.
//  - Puerta del tesoro: un portal dorado que dispara tesoros.
//  - Tienda del otro mundo: el comerciante llega por un portal violeta.
// Lo visual se manda a los clientes cercanos con el mensaje Efecto.
public class PoliciaAnime {
    public static final RegistryObject<EntityType<Babosa>> BABOSA = PoliciaRefuerzo.ENTITIES.register("babosa", () -> EntityType.Builder.<Babosa>of(Babosa::new, MobCategory.MISC).sized(0.52f, 0.52f).clientTrackingRange(10).fireImmune().build("policia:babosa"));

    static final int VIDA_BABOSA = 600;
    static final int FORMA_TICKS = 400;

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaAnime::attrs);
        MinecraftForge.EVENT_BUS.register(new EventosAnime());
    }

    static void attrs(EntityAttributeCreationEvent e) {
        e.put(BABOSA.get(), Babosa.attrs().build());
    }

    // Manda un efecto visual a los que ven a p (y a p).
    static void efecto(Entity p, String tipo, CompoundTag d) {
        PoliciaRed.CH.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new PoliciaRed.Efecto(tipo, d));
    }

    static List<LivingEntity> aliados(ServerPlayer p, double r) {
        List<LivingEntity> l = new ArrayList<LivingEntity>();
        l.add(p);
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e != p && e.isAlive() && (e instanceof Player || e instanceof AbstractVillager || PoliciaHab.aliado(p, e)))) {
            if (!(e instanceof Babosa)) {
                l.add(e);
            }
        }
        return l;
    }

    // ================= Babosa curandera =================
    static boolean babosa(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Babosa b = BABOSA.get().create(sl);
        if (b == null) {
            return false;
        }
        Vec3 c = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
        b.moveTo(c.x, p.getY() + 0.5, c.z, p.getYRot(), 0);
        b.dueno = p.getUUID();
        b.madre = true;
        b.setTam(2);
        b.setDeltaMovement(0, 0.35, 0);
        sl.addFreshEntity(b);
        sl.playSound(null, b.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.5f, 0.8f);
        sl.sendParticles(ParticleTypes.ITEM_SLIME, c.x, p.getY() + 0.6, c.z, 20, 0.4, 0.3, 0.4, 0.1);
        return true;
    }

    public static class Babosa extends PathfinderMob {
        static final EntityDataAccessor<Integer> TAM = SynchedEntityData.defineId(Babosa.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> PEGADA = SynchedEntityData.defineId(Babosa.class, EntityDataSerializers.BOOLEAN);
        UUID dueno;
        UUID objetivo;
        boolean madre;
        int vida;
        int salto = 10;
        public float squish;
        public float oSquish;
        public float targetSquish;
        boolean enSuelo;

        public Babosa(EntityType<? extends Babosa> t, Level l) {
            super(t, l);
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.3);
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(TAM, 1);
            this.entityData.define(PEGADA, false);
        }

        public int tam() {
            return this.entityData.get(TAM);
        }

        public boolean pegada() {
            return this.entityData.get(PEGADA);
        }

        void setTam(int n) {
            this.entityData.set(TAM, n);
            this.refreshDimensions();
        }

        @Override
        public void onSyncedDataUpdated(EntityDataAccessor<?> k) {
            if (TAM.equals(k)) {
                this.refreshDimensions();
            }
            super.onSyncedDataUpdated(k);
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return super.getDimensions(pose).scale(this.tam());
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        protected void doPush(Entity e) {
        }

        @Override
        public boolean hurt(DamageSource s, float a) {
            return false;
        }

        @Override
        public boolean causeFallDamage(float d, float m, DamageSource s) {
            return false;
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }

        @Override
        public boolean removeWhenFarAway(double d) {
            return false;
        }

        @Override
        public void tick() {
            this.squish += (this.targetSquish - this.squish) * 0.5f;
            this.oSquish = this.squish;
            super.tick();
            if (this.pegada()) {
                this.targetSquish = (float) Math.sin(this.tickCount * 0.35) * 0.35f;
            } else if (this.onGround() && !this.enSuelo) {
                this.targetSquish = -0.5f;
                if (this.level().isClientSide) {
                    for (int i = 0; i < 4 * this.tam(); i++) {
                        float a = this.random.nextFloat() * 6.2832f;
                        float r = this.tam() * 0.5f * (0.5f + this.random.nextFloat() * 0.5f);
                        this.level().addParticle(ParticleTypes.ITEM_SLIME, this.getX() + Mth.sin(a) * r, this.getY(), this.getZ() + Mth.cos(a) * r, 0, 0, 0);
                    }
                }
            } else if (!this.onGround() && this.enSuelo) {
                this.targetSquish = 1.0f;
            }
            this.enSuelo = this.onGround();
            this.targetSquish *= 0.6f;
            if (!this.level().isClientSide) {
                this.servidor();
            }
        }

        void servidor() {
            ServerLevel sl = (ServerLevel) this.level();
            this.vida++;
            Player du = this.dueno == null ? null : sl.getPlayerByUUID(this.dueno);
            if (du == null || !du.isAlive() || this.vida > VIDA_BABOSA) {
                this.puf();
                return;
            }
            if (this.madre && this.vida == 14) {
                this.dividir((ServerPlayer) du);
            }
            LivingEntity obj = du;
            if (this.objetivo != null) {
                Entity e = sl.getEntity(this.objetivo);
                if (e instanceof LivingEntity le && le.isAlive()) {
                    obj = le;
                } else {
                    this.objetivo = null;
                    this.entityData.set(PEGADA, false);
                }
            }
            if (this.pegada()) {
                this.noPhysics = true;
                this.setNoGravity(true);
                double a = Math.toRadians(obj.yBodyRot);
                this.setPos(obj.getX() + Math.sin(a) * 0.35, obj.getY() + obj.getBbHeight() * 0.55, obj.getZ() - Math.cos(a) * 0.35);
                this.setDeltaMovement(Vec3.ZERO);
                this.setYRot(obj.yBodyRot);
                this.yBodyRot = obj.yBodyRot;
                if (this.vida % 20 == 0) {
                    if (obj.getHealth() < obj.getMaxHealth()) {
                        obj.heal(1.0f);
                    }
                    sl.sendParticles(ParticleTypes.HEART, obj.getX(), obj.getY() + obj.getBbHeight() + 0.2, obj.getZ(), 1, 0.2, 0.1, 0.2, 0);
                    sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.35f, 1.4f);
                }
                return;
            }
            this.noPhysics = false;
            this.setNoGravity(false);
            double dist = this.distanceTo(obj);
            if (dist > 24) {
                this.teleportTo(obj.getX(), obj.getY() + 0.5, obj.getZ());
                return;
            }
            if (this.objetivo != null && dist < 1.6) {
                this.entityData.set(PEGADA, true);
                sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 0.6f, 1.6f);
                sl.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY() + 0.3, this.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                return;
            }
            if (this.onGround() && --this.salto <= 0) {
                Vec3 d = obj.position().subtract(this.position());
                float yaw = (float) (Mth.atan2(d.z, d.x) * 180.0 / Math.PI) - 90.0f;
                this.setYRot(yaw);
                this.yBodyRot = yaw;
                this.yHeadRot = yaw;
                if (dist > 2.5 || this.objetivo != null) {
                    Vec3 h = new Vec3(d.x, 0, d.z).normalize().scale(Math.min(0.5, 0.18 + dist * 0.05));
                    this.setDeltaMovement(h.x, this.tam() > 1 ? 0.48 : 0.42, h.z);
                } else {
                    this.setDeltaMovement(0, 0.3, 0);
                }
                this.hasImpulse = true;
                sl.playSound(null, this.blockPosition(), this.tam() > 1 ? SoundEvents.SLIME_JUMP : SoundEvents.SLIME_JUMP_SMALL, SoundSource.PLAYERS, 0.5f, 1.2f + this.random.nextFloat() * 0.3f);
                this.salto = this.tam() > 1 ? 14 : 8 + this.random.nextInt(4);
            }
            if (this.madre && this.vida % 20 == 0) {
                for (LivingEntity e : aliados((ServerPlayer) du, 6.0)) {
                    if (e.distanceTo(this) < 5.0 && e.getHealth() < e.getMaxHealth()) {
                        e.heal(0.5f);
                        sl.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 1, 0.2, 0.1, 0.2, 0);
                    }
                }
            }
        }

        // La madre se divide: una babosita por aliado (hasta 4).
        void dividir(ServerPlayer du) {
            ServerLevel sl = (ServerLevel) this.level();
            List<LivingEntity> l = aliados(du, 8.0);
            int n = 0;
            for (LivingEntity e : l) {
                if (n >= 4) {
                    break;
                }
                Babosa h = BABOSA.get().create(sl);
                if (h == null) {
                    continue;
                }
                h.moveTo(this.getX(), this.getY() + 0.4, this.getZ(), this.getYRot(), 0);
                h.dueno = this.dueno;
                h.objetivo = e.getUUID();
                h.setTam(1);
                h.vida = this.vida;
                double a = n * 1.57 + this.random.nextDouble();
                h.setDeltaMovement(Math.cos(a) * 0.25, 0.45, Math.sin(a) * 0.25);
                sl.addFreshEntity(h);
                n++;
            }
            this.targetSquish = 1.4f;
            sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2f, 1.0f);
            sl.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY() + 0.5, this.getZ(), 30, 0.5, 0.4, 0.5, 0.15);
        }

        void puf() {
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY() + 0.3, this.getZ(), 12 * this.tam(), 0.3, 0.3, 0.3, 0.1);
                sl.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.6, this.getZ(), 2, 0.2, 0.2, 0.2, 0);
                sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_DEATH_SMALL, SoundSource.PLAYERS, 0.6f, 1.3f);
            }
            this.discard();
        }
    }

    // ================= Pildora monstruo =================
    static int formaRestante(Player p) {
        long h = p.getPersistentData().getLong("anime_forma");
        return (int) Math.max(0L, h - p.level().getGameTime());
    }

    static void enviarForma(ServerPlayer p, int ticks, ServerPlayer solo) {
        CompoundTag t = new CompoundTag();
        t.putInt("ent", p.getId());
        t.putInt("ticks", ticks);
        if (solo != null) {
            PoliciaRed.toPlayer(solo, new PoliciaRed.Efecto("forma", t));
        } else {
            efecto(p, "forma", t);
        }
    }

    static boolean pildora(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, FORMA_TICKS, 1));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, FORMA_TICKS, 1));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, FORMA_TICKS, 1));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, FORMA_TICKS, 0));
        p.getPersistentData().putLong("anime_forma", sl.getGameTime() + FORMA_TICKS);
        enviarForma(p, FORMA_TICKS, null);
        sl.playSound(null, p.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 0.8f);
        final UUID id = p.getUUID();
        PoliciaHab.tarea(t -> {
            ServerPlayer q = sl.getServer().getPlayerList().getPlayer(id);
            if (q == null || !q.isAlive()) {
                return true;
            }
            if (t == 6) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, q.getX(), q.getY() + 1, q.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
                sl.playSound(null, q.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 1.25f);
            }
            if (t == 14) {
                sl.playSound(null, q.blockPosition(), SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 1.0f, 0.7f);
            }
            if (t > 20 && t % 9 == 0 && q.onGround() && q.getDeltaMovement().horizontalDistanceSqr() > 0.003) {
                sl.playSound(null, q.blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 0.35f, 1.3f);
            }
            if (t >= FORMA_TICKS || formaRestante(q) <= 0) {
                q.getPersistentData().remove("anime_forma");
                enviarForma(q, 0, null);
                sl.sendParticles(ParticleTypes.POOF, q.getX(), q.getY() + 1, q.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
                q.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
                q.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
                q.displayClientMessage(Component.literal("§7La píldora se te pasó... bajón"), true);
                return true;
            }
            return false;
        });
        return true;
    }

    // ================= Cartas de mercancia =================
    static boolean cartas(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        LivingEntity obj = PoliciaHab.mirando(p, 16.0);
        boolean hayEnemigo = obj != null && !PoliciaHab.aliado(p, obj);
        int[] tipos = new int[3];
        for (int i = 0; i < 3; i++) {
            int r = p.getRandom().nextInt(4);
            if (r == 1 && !hayEnemigo) {
                r = p.getRandom().nextBoolean() ? 2 : 0;
            }
            tipos[i] = r;
        }
        CompoundTag d = new CompoundTag();
        d.putInt("ent", p.getId());
        d.putIntArray("tipos", tipos);
        d.putInt("obj", hayEnemigo ? obj.getId() : -1);
        efecto(p, "cartas", d);
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.4f);
        final LivingEntity fobj = hayEnemigo ? obj : null;
        PoliciaHab.tarea(t -> {
            if (!p.isAlive() || p.hasDisconnected()) {
                return true;
            }
            if (t == 8) {
                sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.8f);
            }
            if (t == 22) {
                sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.6f);
            }
            if (t < 30) {
                return false;
            }
            StringBuilder sb = new StringBuilder("§6Cartas: ");
            for (int r : tipos) {
                switch (r) {
                    case 0 -> {
                        PoliciaHabOficios.curar(sl, p, 4.0f);
                        sb.append("§a♥ cura ");
                    }
                    case 1 -> {
                        if (fobj != null && fobj.isAlive()) {
                            fobj.invulnerableTime = 0;
                            fobj.hurt(p.damageSources().playerAttack(p), 5.0f);
                            fobj.setSecondsOnFire(3);
                            sl.sendParticles(ParticleTypes.FLAME, fobj.getX(), fobj.getY() + 1, fobj.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
                        }
                        sb.append("§c🔥 fuego ");
                    }
                    case 2 -> {
                        ItemStack[] m = {new ItemStack(Items.COOKED_BEEF, 3), new ItemStack(Items.ARROW, 12), new ItemStack(Items.TORCH, 8), new ItemStack(Items.GOLDEN_CARROT, 2), new ItemStack(Items.EMERALD, 1)};
                        ItemStack st = m[p.getRandom().nextInt(m.length)].copy();
                        PoliciaHabOficios.dar(p, st);
                        sb.append("§e✦ ").append(st.getHoverName().getString()).append(" ");
                    }
                    default -> {
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1));
                        sb.append("§b» velocidad ");
                    }
                }
            }
            sl.playSound(null, p.blockPosition(), PoliciaSonido.anim("chime").get(), SoundSource.PLAYERS, 0.8f, 1.5f);
            p.displayClientMessage(Component.literal(sb.toString()), true);
            return true;
        });
        return true;
    }

    // ================= Puerta del tesoro =================
    static boolean tesoro(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        CompoundTag d = new CompoundTag();
        d.putInt("ent", p.getId());
        d.putInt("dur", 80);
        efecto(p, "tesoro", d);
        sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
        final List<LivingEntity> pendientes = new ArrayList<LivingEntity>();
        PoliciaHab.tarea(t -> {
            if (!p.isAlive() || p.hasDisconnected()) {
                return true;
            }
            if (t == 20 || t == 38 || t == 56) {
                Vec3 puerta = p.position().add(0, 3.2, 0).subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8));
                pendientes.clear();
                ListTag2 l = new ListTag2();
                int k = 0;
                for (LivingEntity e : PoliciaHabExtra.enemigos(p, p.position(), 14.0)) {
                    if (k++ >= 8) {
                        break;
                    }
                    pendientes.add(e);
                    l.add(e.getId(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ());
                }
                if (!pendientes.isEmpty()) {
                    CompoundTag o = new CompoundTag();
                    o.putInt("ent", p.getId());
                    o.putDouble("px", puerta.x);
                    o.putDouble("py", puerta.y);
                    o.putDouble("pz", puerta.z);
                    o.put("obj", l.tag);
                    efecto(p, "tesoro_ola", o);
                }
                sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 0.8f + t * 0.005f);
            }
            if (t == 26 || t == 44 || t == 62) {
                for (LivingEntity e : pendientes) {
                    if (e.isAlive()) {
                        e.invulnerableTime = 0;
                        e.hurt(p.damageSources().playerAttack(p), 4.0f);
                        sl.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 8, 0.2, 0.3, 0.2, 0.2);
                        sl.playSound(null, e.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 1.8f);
                    }
                }
                pendientes.clear();
            }
            return t >= 80;
        });
        return true;
    }

    // Lista simple de objetivos: id + posicion (para el cliente).
    static class ListTag2 {
        final net.minecraft.nbt.ListTag tag = new net.minecraft.nbt.ListTag();

        void add(int id, double x, double y, double z) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", id);
            c.putDouble("x", x);
            c.putDouble("y", y);
            c.putDouble("z", z);
            this.tag.add(c);
        }
    }

    // ================= Tienda del otro mundo =================
    static boolean tienda(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
        CompoundTag d = new CompoundTag();
        d.putDouble("x", c.x);
        d.putDouble("y", p.getY() + 1.1);
        d.putDouble("z", c.z);
        d.putFloat("yaw", p.getYRot() + 180);
        d.putInt("dur", 60);
        efecto(p, "portal", d);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5f, 1.6f);
        final WanderingTrader[] w = new WanderingTrader[1];
        PoliciaHab.tarea(t -> {
            if (t == 18) {
                WanderingTrader x = EntityType.WANDERING_TRADER.create(sl);
                if (x == null) {
                    return true;
                }
                x.moveTo(c.x, p.getY(), c.z, p.getYRot() + 180, 0);
                x.setCustomName(Component.literal("§5Comerciante de otro mundo"));
                x.setDespawnDelay(1300);
                MerchantOffers o = x.getOffers();
                o.clear();
                o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 6), new ItemStack(Items.DIAMOND), 4, 5, 0.05f));
                o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 4), new ItemStack(Items.NAME_TAG), 2, 5, 0.05f));
                o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 8), new ItemStack(Items.EXPERIENCE_BOTTLE, 16), 3, 5, 0.05f));
                o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 16), new ItemStack(Items.NETHERITE_SCRAP), 1, 10, 0.05f));
                o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 10), new ItemStack(Items.SADDLE), 1, 5, 0.05f));
                o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 3), new ItemStack(Items.GLOW_BERRIES, 12), 6, 2, 0.05f));
                o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 12), new ItemStack(Items.ENDER_PEARL, 8), 2, 5, 0.05f));
                o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 20), PoliciaBotin.equipoDeZona(p.getRandom(), Math.max(10, PoliciaRPG.nivel(p))), 1, 10, 0.05f));
                sl.addFreshEntity(x);
                sl.sendParticles(ParticleTypes.PORTAL, c.x, p.getY() + 1, c.z, 80, 0.5, 1.0, 0.5, 0.5);
                sl.playSound(null, x.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0f, 0.6f);
                w[0] = x;
                return false;
            }
            if (t > 18) {
                WanderingTrader x = w[0];
                if (x == null || !x.isAlive()) {
                    return true;
                }
                if (t >= 1200) {
                    CompoundTag e = new CompoundTag();
                    e.putDouble("x", x.getX());
                    e.putDouble("y", x.getY() + 1.1);
                    e.putDouble("z", x.getZ());
                    e.putFloat("yaw", x.getYRot());
                    e.putInt("dur", 40);
                    efecto(x, "portal", e);
                    sl.playSound(null, x.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0f, 0.6f);
                }
                if (t >= 1215) {
                    sl.sendParticles(ParticleTypes.PORTAL, x.getX(), x.getY() + 1, x.getZ(), 60, 0.4, 1.0, 0.4, 0.5);
                    x.discard();
                    return true;
                }
            }
            return false;
        });
        return true;
    }

    public static class EventosAnime {
        // Al empezar a ver a un jugador transformado, el cliente se entera.
        @SubscribeEvent
        public void verAnime(PlayerEvent.StartTracking e) {
            if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer v) {
                int r = formaRestante(t);
                if (r > 0) {
                    enviarForma(t, r, v);
                }
            }
        }

        @SubscribeEvent
        public void entrarAnime(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                p.getPersistentData().remove("anime_forma");
            }
        }

        @SubscribeEvent
        public void morirAnime(LivingDeathEvent e) {
            if (e.getEntity() instanceof ServerPlayer p && formaRestante(p) > 0) {
                p.getPersistentData().remove("anime_forma");
                enviarForma(p, 0, null);
            }
        }

        // En forma de monstruo pega mas fuerte (y empuja) y recibe menos.
        @SubscribeEvent
        public void golpeAnime(LivingHurtEvent e) {
            if (e.getSource().getEntity() instanceof ServerPlayer p && formaRestante(p) > 0 && e.getSource().getDirectEntity() == p) {
                e.setAmount(e.getAmount() * 1.35f);
                LivingEntity v = e.getEntity();
                v.knockback(0.8, p.getX() - v.getX(), p.getZ() - v.getZ());
                p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), 1, 0, 0, 0, 0);
            }
            if (e.getEntity() instanceof ServerPlayer p && formaRestante(p) > 0) {
                e.setAmount(e.getAmount() * 0.8f);
            }
        }
    }
}
