package com.example.policia;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

// Mercenarios: soldados que se contratan en una aldea por esmeraldas
// (Ficha -> Mercenarios). Hasta 2 a la vez, contrato de 3 dias de juego.
// Cuatro clases: Espadachin, Arquero, Guardian (atrae a los enemigos) y
// Sanador (cura al dueno). Crecen con el nivel del dueno y obedecen
// "seguir" o "quedarse".
public class PoliciaMercenario {
    public static final RegistryObject<EntityType<Merc>> MERC = PoliciaRefuerzo.ENTITIES.register("mercenario", () -> EntityType.Builder.<Merc>of(Merc::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).build("policia:mercenario"));
    public static final String[] CLASE = {"Espadachín", "Arquero", "Guardián", "Sanador"};
    public static final String[] DESC = {"Espada y escudo, equilibrado", "Dispara desde lejos", "Mucha vida, atrae a los enemigos", "Te cura en combate"};
    public static final int[] PRECIO = {16, 18, 22, 26};
    static final String[] NOMBRES = {"Bruno", "Lucía", "Tadeo", "Mora", "Ramiro", "Zoe", "Ciro", "Alma", "Gael", "Juana", "Teo", "Pilar", "Iñaki", "Rocío", "Facundo", "Vera"};
    static final int MAX = 2;
    static final long CONTRATO = 24000L * 3;

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaMercenario::attrs);
        MinecraftForge.EVENT_BUS.register(new EventosMerc());
    }

    static void attrs(EntityAttributeCreationEvent e) {
        e.put(MERC.get(), Merc.attrs().build());
    }

    static List<Merc> mios(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Merc.class, p.getBoundingBox().inflate(160), m -> p.getUUID().equals(m.dueno) && m.isAlive());
    }

    static boolean enAldea(ServerPlayer p) {
        return !p.serverLevel().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(40), v -> !v.isBaby()).isEmpty();
    }

    static void contratar(ServerPlayer p, int clase) {
        if (clase < 0 || clase >= CLASE.length) {
            return;
        }
        if (!enAldea(p)) {
            PoliciaMod.msg(p, "Los mercenarios se contratan en una aldea (tiene que haber aldeanos cerca)");
            return;
        }
        if (mios(p).size() >= MAX) {
            PoliciaMod.msg(p, "Ya tenés " + MAX + " mercenarios. Despedí uno para contratar otro.");
            return;
        }
        int precio = precio(p, clase);
        if (!PoliciaHabOficios.pagar(p, precio)) {
            return;
        }
        ServerLevel sl = p.serverLevel();
        Merc m = MERC.get().create(sl);
        if (m == null) {
            return;
        }
        Vec3 c = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
        m.moveTo(c.x, p.getY(), c.z, p.getYRot() + 180, 0);
        m.dueno = p.getUUID();
        m.setClase(clase);
        m.hasta = sl.getGameTime() + CONTRATO;
        m.nivel = PoliciaRPG.nivel(p);
        String nombre = NOMBRES[sl.random.nextInt(NOMBRES.length)];
        m.setCustomName(Component.literal("§e" + nombre + " §7(" + CLASE[clase] + ")"));
        m.setCustomNameVisible(true);
        m.ajustar();
        sl.addFreshEntity(m);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 1, m.getZ(), 12, 0.4, 0.6, 0.4, 0);
        sl.playSound(null, m.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, 1.0f, 1.0f);
        p.displayClientMessage(Component.literal("§a" + nombre + " se suma por 3 días. §7(" + precio + " esmeraldas)"), false);
        enviar(p);
    }

    static int precio(ServerPlayer p, int clase) {
        return (int) Math.max(1, Math.round((PRECIO[clase] + PoliciaRPG.nivel(p) / 2) * PoliciaBalance.v(PoliciaBalance.PRECIO_MERC)));
    }

    static Merc buscar(ServerPlayer p, String id) {
        try {
            UUID u = UUID.fromString(id);
            for (Merc m : mios(p)) {
                if (m.getUUID().equals(u)) {
                    return m;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static void orden(ServerPlayer p, String id, int orden) {
        Merc m = buscar(p, id);
        if (m == null) {
            return;
        }
        if (orden == 2) {
            m.despedir("§7" + m.getName().getString() + " §7se despide. ¡Suerte!");
        } else {
            m.quieto = orden == 1;
            if (m.quieto) {
                m.puesto = m.position();
            }
            p.displayClientMessage(Component.literal("§e" + m.getName().getString() + "§7: " + (m.quieto ? "me quedo acá" : "te sigo")), true);
        }
        enviar(p);
    }

    // Manda la lista a la pantalla del cliente.
    static void enviar(ServerPlayer p) {
        CompoundTag d = new CompoundTag();
        ListTag l = new ListTag();
        long ahora = p.serverLevel().getGameTime();
        for (Merc m : mios(p)) {
            CompoundTag c = new CompoundTag();
            c.putString("id", m.getUUID().toString());
            c.putString("n", m.getName().getString());
            c.putInt("clase", m.clase());
            c.putFloat("vida", m.getHealth());
            c.putFloat("max", m.getMaxHealth());
            c.putLong("resta", Math.max(0, m.hasta - ahora));
            c.putBoolean("quieto", m.quieto);
            l.add(c);
        }
        d.put("l", l);
        d.putBoolean("aldea", enAldea(p));
        int[] pr = new int[CLASE.length];
        for (int i = 0; i < pr.length; i++) {
            pr[i] = precio(p, i);
        }
        d.putIntArray("precios", pr);
        PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("ui_merc", d));
    }

    public static class Merc extends PathfinderMob {
        static final EntityDataAccessor<Integer> CLASE_D = SynchedEntityData.defineId(Merc.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> APUNTA = SynchedEntityData.defineId(Merc.class, EntityDataSerializers.BOOLEAN);
        UUID dueno;
        long hasta;
        int nivel = 1;
        boolean quieto;
        Vec3 puesto;
        int recarga;

        public Merc(EntityType<? extends Merc> t, Level l) {
            super(t, l);
            this.setPersistenceRequired();
            this.xpReward = 0;
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.ATTACK_DAMAGE, 5.0).add(Attributes.ARMOR, 4.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(CLASE_D, 0);
            this.entityData.define(APUNTA, false);
        }

        public int clase() {
            return this.entityData.get(CLASE_D);
        }

        public boolean apunta() {
            return this.entityData.get(APUNTA);
        }

        void setClase(int c) {
            this.entityData.set(CLASE_D, c);
        }

        // Vida, dano y equipo segun clase y nivel del dueno.
        void ajustar() {
            int c = this.clase();
            double vida = (c == 2 ? 50 : c == 3 ? 26 : 32) + this.nivel * 2.0;
            double dano = (c == 0 ? 6 : c == 2 ? 5 : 3) + this.nivel * 0.25;
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(vida);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(dano);
            this.getAttribute(Attributes.ARMOR).setBaseValue(c == 2 ? 12 : 5);
            this.setHealth(this.getMaxHealth());
            ItemStack mano = switch (c) {
                case 0 -> new ItemStack(this.nivel >= 25 ? Items.DIAMOND_SWORD : Items.IRON_SWORD);
                case 1 -> new ItemStack(Items.BOW);
                case 2 -> new ItemStack(this.nivel >= 25 ? Items.DIAMOND_AXE : Items.IRON_AXE);
                default -> new ItemStack(Items.GOLDEN_SWORD);
            };
            this.setItemSlot(EquipmentSlot.MAINHAND, mano);
            this.setItemSlot(EquipmentSlot.OFFHAND, c == 0 || c == 2 ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(c == 2 ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
            this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(c == 3 ? Items.LEATHER_HELMET : Items.IRON_HELMET));
            for (EquipmentSlot s : EquipmentSlot.values()) {
                this.setDropChance(s, 0.0f);
            }
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(1, new Disparar(this));
            this.goalSelector.addGoal(2, new Pelear(this));
            this.goalSelector.addGoal(3, new Seguir(this));
            this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
            this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Merc.class));
        }

        ServerPlayer duenoJ() {
            if (this.dueno == null || !(this.level() instanceof ServerLevel sl)) {
                return null;
            }
            return sl.getServer().getPlayerList().getPlayer(this.dueno);
        }

        boolean amenaza(LivingEntity e, ServerPlayer d) {
            if (e == null || !e.isAlive() || e instanceof Merc || e == d || e instanceof Player) {
                return false;
            }
            if (e instanceof Mob m && d != null && m.getTarget() == d) {
                return true;
            }
            if (d != null && d.getLastHurtMob() == e && d.tickCount - d.getLastHurtMobTimestamp() < 200) {
                return true;
            }
            return e instanceof Enemy && !(e instanceof Creeper && this.clase() != 1) && e.distanceToSqr(this) < 144;
        }

        @Override
        public boolean hurt(DamageSource s, float a) {
            Entity at = s.getEntity();
            if (at instanceof Merc || at != null && at.getUUID().equals(this.dueno)) {
                return false;
            }
            return super.hurt(s, a);
        }

        @Override
        public boolean canAttack(LivingEntity e) {
            return !(e instanceof Merc) && !(e instanceof Player) && super.canAttack(e);
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide || this.tickCount % 10 != 0) {
                return;
            }
            ServerLevel sl = (ServerLevel) this.level();
            ServerPlayer d = this.duenoJ();
            if (sl.getGameTime() > this.hasta) {
                this.despedir("§7El contrato de " + this.getName().getString() + " §7terminó. ¡Gracias por el trabajo!");
                return;
            }
            // nivel del dueno
            if (d != null && this.tickCount % 200 == 0 && PoliciaRPG.nivel(d) != this.nivel) {
                float frac = this.getHealth() / this.getMaxHealth();
                this.nivel = PoliciaRPG.nivel(d);
                this.ajustar();
                this.setHealth(this.getMaxHealth() * frac);
            }
            // objetivo
            LivingEntity t = this.getTarget();
            if (t != null && (!t.isAlive() || t instanceof Player || t.distanceToSqr(this) > 900)) {
                this.setTarget(null);
                t = null;
            }
            if (t == null && this.clase() != 3) {
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(14), e -> this.amenaza(e, d))) {
                    this.setTarget(e);
                    break;
                }
            }
            // guardian: atrae a los que atacan al dueno
            if (this.clase() == 2 && this.tickCount % 100 == 0 && d != null) {
                boolean grito = false;
                for (Mob m : sl.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(9), m -> m.getTarget() == d)) {
                    m.setTarget(this);
                    grito = true;
                }
                if (grito) {
                    sl.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 0.5f, 1.6f);
                    sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 2.2, this.getZ(), 3, 0.3, 0.1, 0.3, 0);
                }
            }
            // sanador
            if (this.clase() == 3 && this.tickCount % 60 == 0) {
                LivingEntity cura = null;
                if (d != null && d.distanceToSqr(this) < 144 && d.getHealth() < d.getMaxHealth() - 1) {
                    cura = d;
                } else if (this.getHealth() < this.getMaxHealth()) {
                    cura = this;
                } else {
                    for (Merc o : sl.getEntitiesOfClass(Merc.class, this.getBoundingBox().inflate(10), o -> o != this && this.dueno != null && this.dueno.equals(o.dueno) && o.getHealth() < o.getMaxHealth())) {
                        cura = o;
                        break;
                    }
                }
                if (cura != null) {
                    cura.heal(2.0f + this.nivel * 0.08f);
                    this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    sl.sendParticles(ParticleTypes.HEART, cura.getX(), cura.getY() + cura.getBbHeight() + 0.2, cura.getZ(), 3, 0.3, 0.2, 0.3, 0);
                    sl.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6f, 1.6f);
                }
            }
            if (this.tickCount % 40 == 0 && this.getHealth() < this.getMaxHealth() && this.getTarget() == null) {
                this.heal(1.0f);
            }
        }

        void despedir(String msg) {
            ServerPlayer d = this.duenoJ();
            if (d != null) {
                d.displayClientMessage(Component.literal(msg), false);
            }
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1, this.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
            }
            this.discard();
            if (d != null) {
                enviar(d);
            }
        }

        @Override
        public void die(DamageSource s) {
            ServerPlayer d = this.duenoJ();
            if (d != null) {
                d.displayClientMessage(Component.literal("§c" + this.getName().getString() + " §ccayó en combate."), false);
            }
            super.die(s);
        }

        @Override
        public void addAdditionalSaveData(CompoundTag t) {
            super.addAdditionalSaveData(t);
            if (this.dueno != null) {
                t.putUUID("dueno", this.dueno);
            }
            t.putLong("hasta", this.hasta);
            t.putInt("nivel", this.nivel);
            t.putInt("clase", this.clase());
            t.putBoolean("quieto", this.quieto);
            if (this.puesto != null) {
                t.putDouble("px", this.puesto.x);
                t.putDouble("py", this.puesto.y);
                t.putDouble("pz", this.puesto.z);
            }
        }

        @Override
        public void readAdditionalSaveData(CompoundTag t) {
            super.readAdditionalSaveData(t);
            if (t.hasUUID("dueno")) {
                this.dueno = t.getUUID("dueno");
            }
            this.hasta = t.getLong("hasta");
            this.nivel = t.getInt("nivel");
            this.setClase(t.getInt("clase"));
            this.quieto = t.getBoolean("quieto");
            if (t.contains("px")) {
                this.puesto = new Vec3(t.getDouble("px"), t.getDouble("py"), t.getDouble("pz"));
            }
        }

        @Override
        public boolean removeWhenFarAway(double d) {
            return false;
        }
    }

    // Cuerpo a cuerpo (todos menos el arquero).
    static class Pelear extends MeleeAttackGoal {
        final Merc m;

        Pelear(Merc m) {
            super(m, 1.2, true);
            this.m = m;
        }

        @Override
        public boolean canUse() {
            return this.m.clase() != 1 && this.m.clase() != 3 && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.m.clase() != 1 && this.m.clase() != 3 && super.canContinueToUse();
        }
    }

    // Arquero: se acerca hasta 12 bloques y dispara.
    static class Disparar extends Goal {
        final Merc m;

        Disparar(Merc m) {
            this.m = m;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.m.clase() == 1 && this.m.getTarget() != null && this.m.getTarget().isAlive();
        }

        @Override
        public void stop() {
            this.m.entityData.set(Merc.APUNTA, false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = this.m.getTarget();
            if (t == null) {
                return;
            }
            double d = this.m.distanceToSqr(t);
            boolean ve = this.m.getSensing().hasLineOfSight(t);
            this.m.getLookControl().setLookAt(t, 30, 30);
            if (d > 144 || !ve) {
                this.m.getNavigation().moveTo(t, 1.1);
            } else if (d < 25) {
                Vec3 lejos = this.m.position().add(this.m.position().subtract(t.position()).normalize().scale(4));
                this.m.getNavigation().moveTo(lejos.x, lejos.y, lejos.z, 1.2);
            } else {
                this.m.getNavigation().stop();
            }
            this.m.entityData.set(Merc.APUNTA, ve && d < 400);
            if (--this.m.recarga <= 0 && ve && d < 400) {
                Arrow a = new Arrow(this.m.level(), this.m);
                double dx = t.getX() - this.m.getX();
                double dy = t.getY(0.33) - a.getY();
                double dz = t.getZ() - this.m.getZ();
                a.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.7f, 3.0f);
                a.setBaseDamage(this.m.getAttributeValue(Attributes.ATTACK_DAMAGE));
                a.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
                this.m.level().addFreshEntity(a);
                this.m.playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.2f);
                this.m.recarga = 30;
            }
        }
    }

    // Seguir al dueno (o quedarse en el puesto).
    static class Seguir extends Goal {
        final Merc m;

        Seguir(Merc m) {
            this.m = m;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.m.getTarget() != null && this.m.clase() != 3) {
                return false;
            }
            if (this.m.quieto) {
                return this.m.puesto != null && this.m.position().distanceToSqr(this.m.puesto) > 4;
            }
            ServerPlayer d = this.m.duenoJ();
            return d != null && d.level() == this.m.level() && d.distanceToSqr(this.m) > 25;
        }

        @Override
        public void tick() {
            if (this.m.quieto) {
                if (this.m.puesto != null) {
                    this.m.getNavigation().moveTo(this.m.puesto.x, this.m.puesto.y, this.m.puesto.z, 1.0);
                }
                return;
            }
            ServerPlayer d = this.m.duenoJ();
            if (d == null) {
                return;
            }
            if (d.distanceToSqr(this.m) > 900 && d.onGround()) {
                this.m.teleportTo(d.getX() + (this.m.getRandom().nextDouble() - 0.5) * 3, d.getY(), d.getZ() + (this.m.getRandom().nextDouble() - 0.5) * 3);
                this.m.getNavigation().stop();
                return;
            }
            if (this.m.tickCount % 10 == 0) {
                this.m.getNavigation().moveTo(d, d.isSprinting() ? 1.35 : 1.1);
            }
        }
    }

    public static class EventosMerc {
        // Al morir el dueno, los mercenarios no se van: lo esperan.
        @SubscribeEvent
        public void salirMerc(PlayerEvent.PlayerLoggedOutEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                for (Merc m : mios(p)) {
                    m.quieto = true;
                    m.puesto = m.position();
                }
            }
        }
    }
}
