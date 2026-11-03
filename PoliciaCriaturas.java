package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegistryObject;

// Criaturas que aparecen por el mundo (las de la vista previa "Criaturas de
// Oficios"): Ciervo espectral, Escorpion gigante, Yeti, Sapo del pantano y
// el Golem de raices (jefe errante). Cada una tiene sus ataques con el
// dano en el instante del impacto de su animacion; el cliente reproduce las
// poses horneadas de la vista previa (PoliciaCriaturasCliente).
// Tambien: el evento Luna roja y los objetos que sueltan.
public class PoliciaCriaturas {
    public static final String[] ID = {"ciervo_espectral", "escorpion_gigante", "yeti", "sapo_pantano", "golem_raices"};
    public static final String[] ARCHIVO = {"ciervo", "escorpion", "yeti", "sapo", "golem"};
    public static final String[] NOMBRE = {"Ciervo espectral", "Escorpión gigante", "Yeti", "Sapo del pantano", "Gólem de raíces"};
    // vida, dano, velocidad, armadura, empuje, xp
    static final double[][] ATR = {{40, 7, 0.32, 2, 0.3, 12}, {55, 7, 0.27, 8, 0.5, 15}, {80, 10, 0.26, 4, 0.6, 20}, {36, 5, 0.25, 2, 0.2, 10}, {450, 14, 0.22, 12, 1.0, 200}};
    static final float[][] TAM = {{0.9f, 1.9f}, {1.6f, 1.0f}, {1.3f, 3.0f}, {1.2f, 1.0f}, {2.2f, 4.6f}};
    // duracion en ticks de cada animacion (indices 0..6, como en la vista previa)
    public static final int[][] DUR = {
            {140, 23, 11, 64, 68, 48, 60},
            {209, 18, 8, 52, 40, 80, 56},
            {251, 30, 16, 60, 64, 56, 64},
            {171, 22, 44, 52, 60, 52, 60},
            {359, 48, 44, 64, 60, 68, 72}};
    // instantes de impacto de cada ataque (ticks desde que empieza)
    public static final int[][][] IMPACTO = {
            {{}, {}, {}, {34}, {16}, {12}, {20}},
            {{}, {}, {}, {17}, {10, 16, 22}, {39}, {21, 29}},
            {{}, {}, {}, {53}, {10}, {25}, {26, 38}},
            {{}, {}, {}, {15}, {10}, {34}, {26}},
            {{}, {}, {21}, {27}, {12, 20, 28, 36, 44}, {19}, {54}}};
    // ataques: {animacion, distancia minima, maxima, enfriamiento en ticks}
    static final double[][][] ATAQUES = {
            {{3, 3, 12, 100}, {4, 0, 8, 240}, {5, 0, 3, 60}, {6, 0, 7, 300}},
            {{3, 0, 4, 60}, {4, 0, 3, 50}, {5, 6, 18, 400}, {6, 3, 9, 160}},
            {{3, 6, 20, 120}, {4, 0, 16, 600}, {5, 0, 4, 80}, {6, 0, 7, 200}},
            {{3, 2.5, 7, 80}, {4, 0, 10, 400}, {5, 4, 12, 120}, {6, 3, 10, 160}},
            {{2, 0, 4, 60}, {3, 0, 6, 160}, {4, 0, 4.5, 180}, {5, 4, 14, 200}, {6, 7, 22, 220}}};
    // animacion para correr (-1 = no tiene)
    public static final int[] CORRER = {2, 2, 2, 2, -1};

    public static final List<RegistryObject<EntityType<Criatura>>> TIPOS = new ArrayList<RegistryObject<EntityType<Criatura>>>();

    static {
        for (int i = 0; i < ID.length; i++) {
            final int k = i;
            TIPOS.add(PoliciaRefuerzo.ENTITIES.register(ID[i], () -> EntityType.Builder.<Criatura>of((t, l) -> new Criatura(t, l, k), MobCategory.MONSTER).sized(TAM[k][0], TAM[k][1]).clientTrackingRange(10).build("policia:" + ID[k])));
        }
    }

    // ---------------- Objetos que sueltan ----------------
    public static final RegistryObject<Item> ESENCIA = PoliciaShield.ITEMS.register("esencia_espectral", () -> new Botin(Rarity.UNCOMMON, "Brilla un rato en tu mano. Usala: visión nocturna 2 min.", 0));
    public static final RegistryObject<Item> ASTA = PoliciaShield.ITEMS.register("asta_luminosa", () -> new Botin(Rarity.RARE, "Los mercaderes la pagan muy bien.", -1));
    public static final RegistryObject<Item> AGUIJON = PoliciaShield.ITEMS.register("aguijon_escorpion", () -> new Botin(Rarity.UNCOMMON, "Con 4 flechas: 4 flechas de veneno.", -1));
    public static final RegistryObject<Item> QUITINA = PoliciaShield.ITEMS.register("placa_quitina", () -> new Botin(Rarity.UNCOMMON, "Dura como el hierro. Usala: +2 de armadura por 5 min.", 1));
    public static final RegistryObject<Item> PELAJE = PoliciaShield.ITEMS.register("pelaje_yeti", () -> new Botin(Rarity.UNCOMMON, "Abriga. Usalo: resistencia al frío (no te congelás) 5 min.", 2));
    public static final RegistryObject<Item> BABA = PoliciaShield.ITEMS.register("baba_sapo", () -> new Botin(Rarity.COMMON, "Pegajosa. Se hace bolas de slime.", -1));
    public static final RegistryObject<Item> ANCAS = PoliciaShield.ITEMS.register("ancas_sapo", () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationMod(0.7f).meat().build())));
    public static final RegistryObject<Item> CORAZON = PoliciaShield.ITEMS.register("corazon_raiz", () -> new Botin(Rarity.EPIC, "Usalo: +2 de vida máxima para siempre (hasta 3).", 3));

    static final UUID MOD_CORAZON = UUID.fromString("7c1e2d3f-2001-4a5b-8c9d-0e1f2a3b4c21");
    static final UUID MOD_QUITINA = UUID.fromString("7c1e2d3f-2002-4a5b-8c9d-0e1f2a3b4c22");

    public static class Botin extends Item {
        final String desc;
        final int uso;

        Botin(Rarity r, String desc, int uso) {
            super(new Item.Properties().rarity(r).stacksTo(64));
            this.desc = desc;
            this.uso = uso;
        }

        @Override
        public void appendHoverText(ItemStack st, Level level, List<Component> tt, TooltipFlag f) {
            tt.add(Component.literal("§7" + this.desc));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            ItemStack st = p.getItemInHand(hand);
            if (this.uso < 0) {
                return InteractionResultHolder.pass(st);
            }
            if (!level.isClientSide && p instanceof ServerPlayer sp) {
                switch (this.uso) {
                    case 0 -> sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0));
                    case 1 -> {
                        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 0));
                    }
                    case 2 -> {
                        sp.getPersistentData().putLong("pol_abrigo", level.getGameTime() + 6000);
                        sp.displayClientMessage(Component.literal("§bTe abrigaste: no te congelás por 5 minutos"), true);
                    }
                    default -> {
                        CompoundTag d = PoliciaRPG.datos(sp);
                        int n = d.getInt("corazones_raiz");
                        if (n >= 3) {
                            sp.displayClientMessage(Component.literal("§7Ya tenés el máximo de corazones de raíz"), true);
                            return InteractionResultHolder.fail(st);
                        }
                        d.putInt("corazones_raiz", n + 1);
                        aplicarCorazones(sp);
                        sp.heal(2.0f);
                        sp.displayClientMessage(Component.literal("§a+2 de vida máxima §7(" + (n + 1) + "/3)"), true);
                    }
                }
                level.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
                if (!sp.getAbilities().instabuild) {
                    st.shrink(1);
                }
            }
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
    }

    static void aplicarCorazones(ServerPlayer p) {
        PoliciaRPG.poner(p, Attributes.MAX_HEALTH, MOD_CORAZON, "corazon_raiz", PoliciaRPG.datos(p).getInt("corazones_raiz") * 2.0, AttributeModifier.Operation.ADDITION);
    }

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaCriaturas::attrs);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaCriaturas::lugares);
        MinecraftForge.EVENT_BUS.register(new EventosCriaturas());
    }

    static void attrs(EntityAttributeCreationEvent e) {
        for (int i = 0; i < ID.length; i++) {
            double[] a = ATR[i];
            e.put(TIPOS.get(i).get(), Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, a[0]).add(Attributes.ATTACK_DAMAGE, a[1]).add(Attributes.MOVEMENT_SPEED, a[2]).add(Attributes.ARMOR, a[3]).add(Attributes.KNOCKBACK_RESISTANCE, a[4]).add(Attributes.FOLLOW_RANGE, i == 4 ? 48 : 32).build());
        }
    }

    static void lugares(SpawnPlacementRegisterEvent e) {
        for (int i = 0; i < 4; i++) {
            boolean deNoche = i == 0 || i == 2;
            e.register(TIPOS.get(i).get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    deNoche ? (t, l, r, p, rnd) -> Monster.checkMonsterSpawnRules(t, l, r, p, rnd) : (t, l, r, p, rnd) -> l.getDifficulty() != Difficulty.PEACEFUL && Mob.checkMobSpawnRules(t, l, r, p, rnd),
                    SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }

    static void efecto(Entity e, String tipo, CompoundTag d) {
        PoliciaRed.CH.send(PacketDistributor.TRACKING_ENTITY.with(() -> e), new PoliciaRed.Efecto(tipo, d));
    }

    // ================= La criatura =================
    public static class Criatura extends Monster {
        static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(Criatura.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Integer> INICIO = SynchedEntityData.defineId(Criatura.class, EntityDataSerializers.INT);
        public final int tipo;
        final int[] enfria = new int[8];
        int pausa;
        LivingEntity objetivoAtaque;
        Vec3 marca;
        boolean enterrado;
        // estado del lado del cliente (lo usa el renderer)
        public Object cliente;

        public Criatura(EntityType<? extends Criatura> t, Level l, int tipo) {
            super(t, l);
            this.tipo = tipo;
            this.xpReward = (int) ATR[tipo][5];
            if (tipo == 4) {
                this.setPersistenceRequired();
            }
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(ANIM, 0);
            this.entityData.define(INICIO, 0);
        }

        public int ataque() {
            return this.entityData.get(ANIM);
        }

        public int inicio() {
            return this.entityData.get(INICIO);
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(2, new Pelea(this));
            this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
            this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
            this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, true));
        }

        @Override
        public boolean removeWhenFarAway(double d) {
            return this.tipo != 4 && super.removeWhenFarAway(d);
        }

        @Override
        public boolean isInvulnerableTo(DamageSource s) {
            return this.enterrado && !s.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(s);
        }

        @Override
        public boolean hurt(DamageSource s, float a) {
            if (this.tipo == 2 && s.is(DamageTypeTags.IS_FIRE)) {
                a *= 2.0f;
            }
            if (this.tipo == 4 && s.getEntity() instanceof LivingEntity le && le.getMainHandItem().getItem() instanceof AxeItem) {
                a *= 1.5f;
            }
            if (this.tipo == 4 && s.is(DamageTypeTags.IS_FIRE)) {
                this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
            if (this.tipo == 2 && s.is(DamageTypeTags.IS_FREEZING) || this.tipo == 4 && s.is(DamageTypeTags.IS_FALL)) {
                return false;
            }
            return super.hurt(s, a);
        }

        @Override
        public boolean canFreeze() {
            return this.tipo != 2;
        }

        @Override
        public boolean doHurtTarget(Entity e) {
            return super.doHurtTarget(e);
        }

        int dur(int a) {
            return DUR[this.tipo][a];
        }

        void empezar(int a, LivingEntity obj) {
            this.entityData.set(ANIM, a);
            this.entityData.set(INICIO, (int) this.level().getGameTime());
            this.objetivoAtaque = obj;
            this.marca = obj == null ? null : obj.position();
            this.getNavigation().stop();
        }

        void terminar() {
            this.entityData.set(ANIM, 0);
            this.enterrado = false;
            this.setInvisible(false);
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide) {
                return;
            }
            for (int i = 0; i < this.enfria.length; i++) {
                if (this.enfria[i] > 0) {
                    this.enfria[i]--;
                }
            }
            if (this.pausa > 0) {
                this.pausa--;
            }
            int a = this.ataque();
            if (a == 0) {
                return;
            }
            int t = (int) (this.level().getGameTime() - this.inicio());
            LivingEntity obj = this.objetivoAtaque;
            if (obj != null && obj.isAlive()) {
                this.getLookControl().setLookAt(obj, 30, 30);
                if (a != 5 || this.tipo != 1) {
                    double dx = obj.getX() - this.getX();
                    double dz = obj.getZ() - this.getZ();
                    float yaw = (float) (Mth.atan2(dz, dx) * 180 / Math.PI) - 90;
                    this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 12));
                    this.yBodyRot = this.getYRot();
                }
            }
            this.getNavigation().stop();
            for (int k : IMPACTO[this.tipo][a]) {
                if (t == k) {
                    this.impacto(a, k, obj);
                }
            }
            this.durante(a, t, obj);
            if (t >= this.dur(a)) {
                this.terminar();
                this.pausa = 14;
            }
        }

        Vec3 adelante(double d) {
            Vec3 m = Vec3.directionFromRotation(0, this.yBodyRot);
            return this.position().add(m.scale(d));
        }

        List<LivingEntity> victimas(Vec3 c, double r) {
            List<LivingEntity> l = new ArrayList<LivingEntity>();
            for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r, 3, r), e -> e != this && e.isAlive() && !(e instanceof Criatura))) {
                if (e.position().distanceTo(c) <= r + e.getBbWidth() / 2) {
                    l.add(e);
                }
            }
            return l;
        }

        boolean alcanza(LivingEntity e, double r) {
            return e != null && e.isAlive() && this.distanceTo(e) <= r + this.getBbWidth() / 2 + e.getBbWidth() / 2;
        }

        void danar(LivingEntity e, float d, double emp, double arriba) {
            if (e.hurt(this.damageSources().mobAttack(this), d * (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) / ATR[this.tipo][1]))) {
                Vec3 v = e.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(emp);
                e.setDeltaMovement(e.getDeltaMovement().add(v.x, arriba, v.z));
                e.hurtMarked = true;
            }
        }

        void particulas(ParticleOptions p, Vec3 c, int n, double ancho, double vel) {
            ((ServerLevel) this.level()).sendParticles(p, c.x, c.y, c.z, n, ancho, ancho * 0.5, ancho, vel);
        }

        void sonido(SoundEvent s, float vol, float tono) {
            this.level().playSound(null, this.blockPosition(), s, SoundSource.HOSTILE, vol, tono);
        }

        void sacudir(double r, float f) {
            CompoundTag d = new CompoundTag();
            d.putDouble("x", this.getX());
            d.putDouble("y", this.getY());
            d.putDouble("z", this.getZ());
            d.putDouble("r", r);
            d.putFloat("f", f);
            efecto(this, "criatura_temblor", d);
        }

        void proyectil(Vec3 desde, Vec3 hasta, String item, float esc, int ticks) {
            CompoundTag d = new CompoundTag();
            d.putDouble("px", desde.x);
            d.putDouble("py", desde.y);
            d.putDouble("pz", desde.z);
            d.putDouble("x", hasta.x);
            d.putDouble("y", hasta.y);
            d.putDouble("z", hasta.z);
            d.putInt("id", -1);
            d.putString("it", item);
            d.putFloat("esc", esc);
            d.putFloat("arco", 1.5f);
            d.putInt("dur", ticks);
            efecto(this, "proyectil", d);
        }

        // Lo que pasa durante un ataque, tick a tick (movimientos).
        void durante(int a, int t, LivingEntity obj) {
            switch (this.tipo) {
                case 0 -> {
                    if (a == 3 && t >= 28 && t <= 36 && obj != null) {
                        Vec3 v = obj.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(0.55);
                        this.setDeltaMovement(v.x, this.getDeltaMovement().y, v.z);
                        if (t % 2 == 0) {
                            this.particulas(ParticleTypes.CLOUD, this.position().add(0, 0.2, 0), 3, 0.3, 0.02);
                        }
                    }
                }
                case 1 -> {
                    if (a == 5) {
                        if (t == 4) {
                            this.enterrado = true;
                            this.sonido(SoundEvents.SAND_BREAK, 1.5f, 0.6f);
                        }
                        if (t > 4 && t < 38 && t % 3 == 0) {
                            this.particulas(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState()), this.position().add(0, 0.2, 0), 8, 0.6, 0.1);
                        }
                        if (t == 24) {
                            this.setInvisible(true);
                        }
                        if (t == 37 && obj != null && obj.distanceTo(this) < 22) {
                            Vec3 c = obj.position().subtract(obj.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
                            this.teleportTo(c.x, obj.getY(), c.z);
                        }
                        if (t == 38) {
                            this.setInvisible(false);
                        }
                    }
                }
                case 3 -> {
                    if (a == 6 && t == 5 && obj != null) {
                        Vec3 v = obj.position().subtract(this.position()).multiply(1, 0, 1);
                        double d = Math.min(8, v.length());
                        v = v.normalize().scale(0.12 * d + 0.25);
                        this.setDeltaMovement(v.x, 0.85, v.z);
                        this.hasImpulse = true;
                    }
                }
                case 4 -> {
                    if (a == 6 && t == 39 && obj != null) {
                        this.marca = obj.position();
                        this.proyectil(this.position().add(0, 6.5, 0).add(Vec3.directionFromRotation(0, this.yBodyRot).scale(1.0)), this.marca.add(0, 0.5, 0), "minecraft:cobblestone", 2.6f, 15);
                        this.sonido(SoundEvents.STONE_BREAK, 2.0f, 0.5f);
                    }
                }
                case 2 -> {
                    if (a == 3 && t == 36 && obj != null) {
                        this.marca = obj.position();
                        this.proyectil(this.position().add(0, 3.3, 0), this.marca.add(0, 1, 0), "minecraft:snow_block", 1.3f, 17);
                        this.sonido(SoundEvents.SNOW_GOLEM_SHOOT, 2.0f, 0.5f);
                    }
                }
                default -> {
                }
            }
            if (this.tipo == 3 && a == 5 && t == 19 && obj != null) {
                this.marca = obj.position();
                this.proyectil(this.position().add(0, 0.6, 0).add(Vec3.directionFromRotation(0, this.yBodyRot).scale(0.8)), this.marca.add(0, 0.8, 0), "minecraft:slime_block", 0.7f, 15);
                this.sonido(SoundEvents.SLIME_SQUISH, 1.5f, 0.7f);
            }
        }

        // El instante del golpe: dano, efectos y particulas.
        void impacto(int a, int k, LivingEntity obj) {
            ServerLevel sl = (ServerLevel) this.level();
            switch (this.tipo) {
                case 0 -> {
                    if (a == 3) {
                        if (this.alcanza(obj, 2.2)) {
                            this.danar(obj, 8, 1.3, 0.35);
                        }
                        this.particulas(ParticleTypes.CRIT, this.adelante(1.5).add(0, 1, 0), 15, 0.4, 0.3);
                        this.sonido(SoundEvents.GOAT_RAM_IMPACT, 1.5f, 1.0f);
                    } else if (a == 4) {
                        for (LivingEntity e : this.victimas(this.position(), 8)) {
                            if (e instanceof Player) {
                                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                            }
                        }
                        this.particulas(ParticleTypes.SONIC_BOOM, this.position().add(0, 2.2, 0), 1, 0, 0);
                        this.sonido(SoundEvents.GOAT_SCREAMING_AMBIENT, 2.0f, 0.6f);
                    } else if (a == 5) {
                        if (this.alcanza(obj, 2.6)) {
                            this.danar(obj, 7, 1.8, 0.5);
                        }
                        this.particulas(ParticleTypes.POOF, this.adelante(-1.6).add(0, 0.5, 0), 12, 0.4, 0.05);
                        this.sonido(SoundEvents.HORSE_ANGRY, 1.2f, 1.4f);
                    } else if (a == 6) {
                        for (LivingEntity e : this.victimas(this.position(), 7)) {
                            if (e instanceof Player && this.hasLineOfSight(e)) {
                                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                                e.hurt(this.damageSources().indirectMagic(this, this), 3);
                            }
                        }
                        this.particulas(ParticleTypes.END_ROD, this.position().add(0, 2.2, 0), 50, 1.2, 0.25);
                        this.particulas(ParticleTypes.FLASH, this.position().add(0, 2.0, 0), 1, 0, 0);
                        this.sonido(SoundEvents.AMETHYST_CLUSTER_BREAK, 2.0f, 1.6f);
                    }
                }
                case 1 -> {
                    if (a == 3) {
                        if (this.alcanza(obj, 3.0)) {
                            this.danar(obj, 7, 0.6, 0.2);
                            obj.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                        }
                        this.particulas(ParticleTypes.CRIT, this.adelante(2.0).add(0, 1, 0), 12, 0.3, 0.3);
                        this.sonido(SoundEvents.SPIDER_HURT, 1.2f, 0.5f);
                    } else if (a == 4) {
                        if (this.alcanza(obj, 2.6)) {
                            this.danar(obj, 3, 0.1, 0);
                            obj.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
                        }
                        this.sonido(SoundEvents.ARMOR_EQUIP_CHAIN, 1.4f, 1.6f);
                    } else if (a == 5) {
                        this.enterrado = false;
                        for (LivingEntity e : this.victimas(this.position(), 3)) {
                            this.danar(e, 8, 0.8, 0.8);
                        }
                        this.particulas(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState()), this.position().add(0, 0.5, 0), 60, 1.2, 0.3);
                        this.sonido(SoundEvents.GENERIC_EXPLODE, 0.8f, 1.4f);
                        this.sacudir(10, 0.4f);
                    } else if (a == 6) {
                        Vec3 c = this.marca != null ? this.marca : this.adelante(3.2);
                        if (k == 21) {
                            AreaEffectCloud n = new AreaEffectCloud(sl, c.x, c.y, c.z);
                            n.setOwner(this);
                            n.setRadius(2.0f);
                            n.setDuration(100);
                            n.setRadiusPerTick(-0.01f);
                            n.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                            sl.addFreshEntity(n);
                        }
                        if (obj != null && obj.distanceTo(this) < 9 && this.hasLineOfSight(obj)) {
                            this.danar(obj, 3, 0.2, 0);
                            obj.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                        }
                        this.particulas(ParticleTypes.ITEM_SLIME, this.adelante(2.5).add(0, 1.2, 0), 25, 0.6, 0.2);
                        this.sonido(SoundEvents.LLAMA_SPIT, 1.5f, 0.6f);
                    }
                }
                case 2 -> {
                    if (a == 3) {
                        Vec3 c = this.marca;
                        if (c != null && obj != null && obj.position().distanceTo(c) < 2.5) {
                            this.danar(obj, 6, 0.6, 0.2);
                            obj.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                        }
                        if (c != null) {
                            sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 1, c.z, 40, 0.6, 0.6, 0.6, 0.1);
                            sl.playSound(null, BlockPos.containing(c), SoundEvents.SNOW_BREAK, SoundSource.HOSTILE, 2.0f, 0.6f);
                        }
                    } else if (a == 4) {
                        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
                        this.sonido(SoundEvents.RAVAGER_ROAR, 2.0f, 1.2f);
                        this.particulas(ParticleTypes.ANGRY_VILLAGER, this.position().add(0, 3.2, 0), 6, 0.5, 0);
                    } else if (a == 5) {
                        Vec3 c = this.adelante(1.8);
                        for (LivingEntity e : this.victimas(c, 4)) {
                            this.danar(e, 10, 0.7, 0.75);
                        }
                        sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 0.2, c.z, 80, 2.0, 0.3, 2.0, 0.2);
                        this.sonido(SoundEvents.GENERIC_EXPLODE, 1.0f, 1.2f);
                        this.sacudir(14, 0.6f);
                    } else if (a == 6) {
                        Vec3 m = Vec3.directionFromRotation(0, this.yBodyRot);
                        for (LivingEntity e : this.victimas(this.position(), 7)) {
                            Vec3 h = e.position().subtract(this.position()).multiply(1, 0, 1).normalize();
                            if (h.dot(m) > 0.7) {
                                this.danar(e, 3, 0.3, 0);
                                e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 80, e.getTicksFrozen() + 90));
                                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                            }
                        }
                        for (int i = 1; i <= 6; i++) {
                            Vec3 q = this.position().add(0, 2.4 - i * 0.2, 0).add(m.scale(i));
                            sl.sendParticles(ParticleTypes.SNOWFLAKE, q.x, q.y, q.z, 8, 0.2 + i * 0.12, 0.2, 0.2 + i * 0.12, 0.05);
                        }
                        this.sonido(SoundEvents.POWDER_SNOW_BREAK, 2.0f, 0.5f);
                    }
                }
                case 3 -> {
                    if (a == 3) {
                        Vec3 m = Vec3.directionFromRotation(0, this.yBodyRot);
                        if (obj != null && obj.distanceTo(this) < 7.5 && obj.position().subtract(this.position()).normalize().dot(m) > 0.6) {
                            this.danar(obj, 4, 0, 0);
                            Vec3 v = this.position().subtract(obj.position()).normalize().scale(1.1);
                            obj.setDeltaMovement(v.x, 0.35, v.z);
                            obj.hurtMarked = true;
                        }
                        this.sonido(SoundEvents.FROG_TONGUE, 2.0f, 0.6f);
                    } else if (a == 4) {
                        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));
                        this.sonido(SoundEvents.PUFFER_FISH_BLOW_UP, 1.5f, 0.6f);
                    } else if (a == 5) {
                        Vec3 c = this.marca;
                        if (c != null && obj != null && obj.position().distanceTo(c) < 2.2) {
                            this.danar(obj, 4, 0.2, 0);
                            obj.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                        }
                        if (c != null) {
                            sl.sendParticles(ParticleTypes.ITEM_SLIME, c.x, c.y + 0.3, c.z, 30, 0.6, 0.2, 0.6, 0.1);
                        }
                    } else if (a == 6) {
                        for (LivingEntity e : this.victimas(this.position(), 3.5)) {
                            this.danar(e, 8, 1.0, 0.5);
                        }
                        this.particulas(ParticleTypes.SPLASH, this.position().add(0, 0.3, 0), 80, 1.6, 0.4);
                        this.sonido(SoundEvents.GENERIC_SPLASH, 2.0f, 0.7f);
                        this.sacudir(8, 0.3f);
                    }
                }
                default -> {
                    if (a == 2) {
                        for (LivingEntity e : this.victimas(this.position(), 4)) {
                            this.danar(e, 6, 0.6, 0.45);
                        }
                        this.particulas(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.position().add(0, 0.2, 0), 12, 1.5, 0.02);
                        this.sonido(SoundEvents.RAVAGER_STEP, 2.5f, 0.5f);
                        this.sacudir(16, 0.4f);
                    } else if (a == 3) {
                        for (LivingEntity e : this.victimas(this.adelante(1.5), 6)) {
                            this.danar(e, 12, 1.0, 0.9);
                        }
                        this.particulas(ParticleTypes.EXPLOSION, this.adelante(2).add(0, 0.3, 0), 6, 1.5, 0);
                        this.sonido(SoundEvents.GENERIC_EXPLODE, 2.0f, 0.6f);
                        this.sacudir(24, 1.0f);
                    } else if (a == 4) {
                        for (LivingEntity e : this.victimas(this.position(), 4.5)) {
                            this.danar(e, 5, 1.4, 0.3);
                        }
                        this.particulas(ParticleTypes.SWEEP_ATTACK, this.position().add(0, 1.6, 0), 6, 2.0, 0);
                        this.sonido(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
                    } else if (a == 5) {
                        Vec3 c = this.marca != null ? this.marca : this.adelante(4);
                        for (LivingEntity e : this.victimas(c, 2.5)) {
                            this.danar(e, 10, 0.2, 0.9);
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4));
                        }
                        sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.ROOTED_DIRT.defaultBlockState()), c.x, c.y + 0.5, c.z, 60, 1.2, 0.6, 1.2, 0.2);
                        CompoundTag d = new CompoundTag();
                        d.putDouble("x", c.x);
                        d.putDouble("y", c.y);
                        d.putDouble("z", c.z);
                        d.putInt("dur", 40);
                        efecto(this, "criatura_raices", d);
                        sl.playSound(null, BlockPos.containing(c), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 2.0f, 0.6f);
                        this.sacudir(12, 0.5f);
                    } else if (a == 6) {
                        Vec3 c = this.marca != null ? this.marca : this.adelante(6);
                        for (LivingEntity e : this.victimas(c, 2.5)) {
                            this.danar(e, 12, 1.2, 0.6);
                        }
                        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 4, 0.8, 0.4, 0.8, 0);
                        sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState()), c.x, c.y + 0.5, c.z, 50, 1.0, 0.6, 1.0, 0.3);
                        sl.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.8f);
                        this.sacudir(20, 0.8f);
                    }
                }
            }
        }

        @Override
        protected void dropCustomDeathLoot(DamageSource s, int looting, boolean jugador) {
            super.dropCustomDeathLoot(s, looting, jugador);
            java.util.Random r = new java.util.Random();
            switch (this.tipo) {
                case 0 -> {
                    this.spawnAtLocation(new ItemStack(ESENCIA.get(), 1 + r.nextInt(2 + looting)));
                    if (r.nextFloat() < 0.35f + looting * 0.1f) {
                        this.spawnAtLocation(new ItemStack(ASTA.get()));
                    }
                    this.spawnAtLocation(new ItemStack(Items.LEATHER, 1 + r.nextInt(2)));
                }
                case 1 -> {
                    this.spawnAtLocation(new ItemStack(QUITINA.get(), 1 + r.nextInt(2 + looting)));
                    if (r.nextFloat() < 0.5f + looting * 0.1f) {
                        this.spawnAtLocation(new ItemStack(AGUIJON.get()));
                    }
                }
                case 2 -> {
                    this.spawnAtLocation(new ItemStack(PELAJE.get(), 1 + r.nextInt(2 + looting)));
                    this.spawnAtLocation(new ItemStack(Items.SNOWBALL, 2 + r.nextInt(4)));
                    if (r.nextFloat() < 0.4f) {
                        this.spawnAtLocation(new ItemStack(Items.BLUE_ICE, 1));
                    }
                }
                case 3 -> {
                    this.spawnAtLocation(new ItemStack(ANCAS.get(), 1 + r.nextInt(2)));
                    this.spawnAtLocation(new ItemStack(BABA.get(), 1 + r.nextInt(2 + looting)));
                }
                default -> {
                    this.spawnAtLocation(new ItemStack(CORAZON.get()));
                    this.spawnAtLocation(new ItemStack(Items.EMERALD, 12 + r.nextInt(12)));
                    this.spawnAtLocation(new ItemStack(Items.OAK_LOG, 8));
                    this.spawnAtLocation(new ItemStack(Items.MOSS_BLOCK, 4));
                    this.spawnAtLocation(PoliciaBotin.equipoDeZona(this.getRandom(), 30));
                }
            }
        }

        @Override
        public void startSeenByPlayer(ServerPlayer p) {
            super.startSeenByPlayer(p);
            if (this.tipo == 4) {
                Errante.barra().addPlayer(p);
            }
        }

        @Override
        public void stopSeenByPlayer(ServerPlayer p) {
            super.stopSeenByPlayer(p);
            if (this.tipo == 4) {
                Errante.barra().removePlayer(p);
            }
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.level().isClientSide && this.tipo == 4) {
                Errante.barra().setProgress(this.getHealth() / this.getMaxHealth());
            }
        }

        @Override
        public void die(DamageSource s) {
            super.die(s);
            if (!this.level().isClientSide && this.tipo == 4) {
                Errante.murio(this, s);
            }
        }

        @Override
        protected SoundEvent getAmbientSound() {
            return switch (this.tipo) {
                case 0 -> SoundEvents.GOAT_AMBIENT;
                case 1 -> SoundEvents.SPIDER_AMBIENT;
                case 2 -> SoundEvents.POLAR_BEAR_AMBIENT;
                case 3 -> SoundEvents.FROG_AMBIENT;
                default -> SoundEvents.WARDEN_AMBIENT;
            };
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource s) {
            return switch (this.tipo) {
                case 0 -> SoundEvents.GOAT_HURT;
                case 1 -> SoundEvents.SPIDER_HURT;
                case 2 -> SoundEvents.POLAR_BEAR_HURT;
                case 3 -> SoundEvents.FROG_HURT;
                default -> SoundEvents.WOOD_BREAK;
            };
        }

        @Override
        protected SoundEvent getDeathSound() {
            return switch (this.tipo) {
                case 0 -> SoundEvents.GOAT_DEATH;
                case 1 -> SoundEvents.SPIDER_DEATH;
                case 2 -> SoundEvents.POLAR_BEAR_DEATH;
                case 3 -> SoundEvents.FROG_DEATH;
                default -> SoundEvents.WARDEN_DEATH;
            };
        }

        @Override
        protected void playStepSound(BlockPos p, net.minecraft.world.level.block.state.BlockState b) {
            this.playSound(this.tipo == 4 ? SoundEvents.RAVAGER_STEP : this.tipo == 3 ? SoundEvents.FROG_STEP : SoundEvents.HORSE_STEP, this.tipo == 4 ? 0.8f : 0.3f, this.tipo == 4 ? 0.6f : 1.0f);
        }

        @Override
        public void addAdditionalSaveData(CompoundTag t) {
            super.addAdditionalSaveData(t);
            t.putBoolean("errante", this.tipo == 4);
        }
    }

    // Decide que ataque usar y persigue al objetivo.
    static class Pelea extends Goal {
        final Criatura c;
        int ruta;

        Pelea(Criatura c) {
            this.c = c;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = this.c.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() || this.c.ataque() != 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = this.c.getTarget();
            if (this.c.ataque() != 0 || t == null) {
                return;
            }
            double d = this.c.distanceTo(t) - this.c.getBbWidth() / 2 - t.getBbWidth() / 2;
            this.c.getLookControl().setLookAt(t, 30, 30);
            if (this.c.pausa <= 0 && this.c.hasLineOfSight(t)) {
                double[][] lista = ATAQUES[this.c.tipo];
                List<double[]> listos = new ArrayList<double[]>();
                for (double[] a : lista) {
                    if (this.c.enfria[(int) a[0]] <= 0 && d >= a[1] && d <= a[2]) {
                        listos.add(a);
                    }
                }
                if (!listos.isEmpty()) {
                    double[] a = listos.get(this.c.getRandom().nextInt(listos.size()));
                    this.c.enfria[(int) a[0]] = (int) a[3];
                    this.c.empezar((int) a[0], t);
                    return;
                }
            }
            if (d > 1.5) {
                if (--this.ruta <= 0) {
                    this.ruta = 8;
                    this.c.getNavigation().moveTo(t, d > 6 && CORRER[this.c.tipo] >= 0 ? 1.45 : 1.1);
                }
            } else {
                this.c.getNavigation().stop();
            }
        }
    }

    // ================= Jefe errante =================
    static class Errante {
        static ServerBossEvent barra;

        static ServerBossEvent barra() {
            if (barra == null) {
                barra = new ServerBossEvent(Component.literal("Gólem de raíces"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
            }
            return barra;
        }

        static CompoundTag datos(MinecraftServer s) {
            return PoliciaMundo.get(s).getSeccion("errante");
        }

        static void murio(Criatura g, DamageSource s) {
            ServerLevel sl = (ServerLevel) g.level();
            CompoundTag d = datos(sl.getServer());
            d.remove("x");
            d.putLong("ultimo", sl.getGameTime());
            PoliciaMundo.get(sl.getServer()).setDirty();
            barra().removeAllPlayers();
            String quien = s.getEntity() instanceof Player p ? p.getName().getString() : "alguien";
            for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
                p.displayClientMessage(Component.literal("§6⚔ " + quien + " derrotó al Gólem de raíces."), false);
            }
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, g.getBoundingBox().inflate(40))) {
                PoliciaRPG.sumarXp(p, 500);
                PoliciaHistoria.titulo(p, "§6¡Gólem de raíces derrotado!", "§7+500 XP · botín en el suelo");
            }
            try {
                PoliciaDiario.add(sl, "El Gólem de raíces cayó a manos de " + quien);
            } catch (Throwable ignored) {
            }
        }

        // Cada tanto aparece cerca de un jugador al azar del mundo normal.
        static void quizas(MinecraftServer s, boolean lunaRoja) {
            ServerLevel ow = s.overworld();
            CompoundTag d = datos(s);
            if (d.contains("x")) {
                Entity e = d.hasUUID("id") ? ow.getEntity(d.getUUID("id")) : null;
                if (e == null && ow.getGameTime() - d.getLong("desde") > 24000 * 2) {
                    d.remove("x");
                    PoliciaMundo.get(s).setDirty();
                } else if (e instanceof Criatura c) {
                    d.putInt("x", c.getBlockX());
                    d.putInt("y", c.getBlockY());
                    d.putInt("z", c.getBlockZ());
                }
                return;
            }
            long espera = lunaRoja ? 24000 : 24000 * 3;
            if (ow.getGameTime() - d.getLong("ultimo") < espera || ow.random.nextInt(lunaRoja ? 20 : 60) != 0) {
                return;
            }
            List<ServerPlayer> ps = new ArrayList<ServerPlayer>();
            for (ServerPlayer p : ow.players()) {
                if (PoliciaRPG.nivel(p) >= 15 && !p.isSpectator()) {
                    ps.add(p);
                }
            }
            if (ps.isEmpty()) {
                return;
            }
            ServerPlayer p = ps.get(ow.random.nextInt(ps.size()));
            double ang = ow.random.nextDouble() * Math.PI * 2;
            int x = (int) (p.getX() + Math.cos(ang) * 50);
            int z = (int) (p.getZ() + Math.sin(ang) * 50);
            int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Criatura g = TIPOS.get(4).get().create(ow);
            if (g == null) {
                return;
            }
            g.moveTo(x + 0.5, y, z + 0.5, ow.random.nextFloat() * 360, 0);
            g.finalizeSpawn(ow, ow.getCurrentDifficultyAt(g.blockPosition()), MobSpawnType.EVENT, null, null);
            ow.addFreshEntity(g);
            d.putInt("x", x);
            d.putInt("y", y);
            d.putInt("z", z);
            d.putUUID("id", g.getUUID());
            d.putLong("desde", ow.getGameTime());
            PoliciaMundo.get(s).setDirty();
            for (ServerPlayer q : s.getPlayerList().getPlayers()) {
                q.displayClientMessage(Component.literal("§2🌳 Se vio un §lGólem de raíces§2 cerca de §f" + x + ", " + z + "§2. Es nivel 30: andá en grupo. §8(Ficha → Objetivo para seguirlo)"), false);
                q.level().playSound(null, q.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.4f, 1.2f);
            }
        }
    }

    // ================= Luna roja =================
    static class Luna {
        static boolean activa(MinecraftServer s) {
            return PoliciaMundo.get(s).getSeccion("luna").getBoolean("roja");
        }

        static void avisar(ServerPlayer p, boolean roja) {
            CompoundTag d = new CompoundTag();
            d.putBoolean("roja", roja);
            PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("luna_roja", d));
        }

        static void tick(MinecraftServer s) {
            ServerLevel ow = s.overworld();
            long hora = ow.getDayTime() % 24000L;
            CompoundTag d = PoliciaMundo.get(s).getSeccion("luna");
            boolean roja = d.getBoolean("roja");
            if (!roja && hora == 13000L && ow.random.nextInt(8) == 0 && ow.getDifficulty() != Difficulty.PEACEFUL) {
                d.putBoolean("roja", true);
                PoliciaMundo.get(s).setDirty();
                for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                    avisar(p, true);
                    PoliciaHistoria.titulo(p, "§4§lLuna roja", "§cEsta noche los monstruos son más fuertes y sueltan el doble");
                    p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.4f, 0.6f);
                }
            } else if (roja && (hora >= 23000L || hora < 12000L)) {
                d.putBoolean("roja", false);
                PoliciaMundo.get(s).setDirty();
                for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                    avisar(p, false);
                    p.displayClientMessage(Component.literal("§7Amanece. La luna roja se fue."), true);
                }
            }
            if (roja && ow.getGameTime() % 200 == 0) {
                for (ServerPlayer p : ow.players()) {
                    if (p.isSpectator() || p.isCreative()) {
                        continue;
                    }
                    int cerca = ow.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(32)).size();
                    if (cerca > 12) {
                        continue;
                    }
                    double ang = ow.random.nextDouble() * Math.PI * 2;
                    int x = (int) (p.getX() + Math.cos(ang) * 22);
                    int z = (int) (p.getZ() + Math.sin(ang) * 22);
                    int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    int k = criaturaDelBioma(ow, new BlockPos(x, y, z));
                    Mob m = k >= 0 ? TIPOS.get(k).get().create(ow) : EntityType.ZOMBIE.create(ow);
                    if (m == null) {
                        continue;
                    }
                    m.moveTo(x + 0.5, y, z + 0.5, ow.random.nextFloat() * 360, 0);
                    m.finalizeSpawn(ow, ow.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
                    ow.addFreshEntity(m);
                }
            }
        }

        static int criaturaDelBioma(ServerLevel sl, BlockPos p) {
            var b = sl.getBiome(p);
            String n = b.unwrapKey().map(k -> k.location().getPath()).orElse("");
            if (n.contains("desert") || n.contains("badlands")) {
                return 1;
            }
            if (n.contains("snow") || n.contains("frozen") || n.contains("ice") || n.contains("grove") || n.contains("peaks")) {
                return 2;
            }
            if (n.contains("swamp")) {
                return 3;
            }
            if (n.contains("forest") || n.contains("taiga")) {
                return 0;
            }
            return -1;
        }
    }

    static final UUID MOD_LUNA_VIDA = UUID.fromString("7c1e2d3f-3001-4a5b-8c9d-0e1f2a3b4c31");
    static final UUID MOD_LUNA_DANO = UUID.fromString("7c1e2d3f-3002-4a5b-8c9d-0e1f2a3b4c32");

    public static class EventosCriaturas {
        @SubscribeEvent
        public void tickCriaturas(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.getServer() == null) {
                return;
            }
            MinecraftServer s = e.getServer();
            Luna.tick(s);
            if (s.overworld().getGameTime() % 100 == 0) {
                Errante.quizas(s, Luna.activa(s));
            }
        }

        @SubscribeEvent
        public void entrarCriaturas(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                Luna.avisar(p, Luna.activa(p.server));
                aplicarCorazones(p);
            }
        }

        @SubscribeEvent
        public void revivirCriaturas(PlayerEvent.PlayerRespawnEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                aplicarCorazones(p);
            }
        }

        // Luna roja: los monstruos que aparecen son mas fuertes.
        @SubscribeEvent
        public void nacerCriaturas(EntityJoinLevelEvent e) {
            if (e.getLevel().isClientSide || e.loadedFromDisk() || !(e.getEntity() instanceof Monster m) || !(e.getLevel() instanceof ServerLevel sl)) {
                return;
            }
            if (sl.dimension() == Level.OVERWORLD && Luna.activa(sl.getServer())) {
                PoliciaCombate.mod(m, Attributes.MAX_HEALTH, MOD_LUNA_VIDA, 0.5, AttributeModifier.Operation.MULTIPLY_BASE);
                PoliciaCombate.mod(m, Attributes.ATTACK_DAMAGE, MOD_LUNA_DANO, 0.3, AttributeModifier.Operation.MULTIPLY_BASE);
                m.setHealth(m.getMaxHealth());
                m.getPersistentData().putBoolean("luna_roja", true);
            }
        }

        @SubscribeEvent
        public void botinLuna(LivingDropsEvent e) {
            if (e.getEntity().getPersistentData().getBoolean("luna_roja") && e.getSource().getEntity() instanceof Player) {
                List<net.minecraft.world.entity.item.ItemEntity> extra = new ArrayList<net.minecraft.world.entity.item.ItemEntity>();
                for (net.minecraft.world.entity.item.ItemEntity it : e.getDrops()) {
                    extra.add(new net.minecraft.world.entity.item.ItemEntity(it.level(), it.getX(), it.getY(), it.getZ(), it.getItem().copy()));
                }
                e.getDrops().addAll(extra);
            }
        }

        // Pelaje de yeti: mientras dura no te congelas.
        @SubscribeEvent
        public void abrigo(TickEvent.PlayerTickEvent e) {
            if (e.phase == TickEvent.Phase.END && !e.player.level().isClientSide && e.player.getPersistentData().getLong("pol_abrigo") > e.player.level().getGameTime()) {
                e.player.setTicksFrozen(0);
            }
        }
    }
}
