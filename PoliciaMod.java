package com.example.policia;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.joml.Vector3f;

// Clase principal del mod. Reconstruida a partir del jar compilado
// (policia-1.0.0.jar) via decompilacion, traduciendo los nombres internos
// (SRG) a los nombres oficiales de Mojang para que el proyecto pueda
// compilarse y seguir editandose como codigo fuente normal.
@Mod(PoliciaMod.ID)
public class PoliciaMod {
    public static final String ID = "policia";
    public static final SimpleChannel NET = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ID, "main"))
            .networkProtocolVersion(() -> "1")
            .clientAcceptedVersions(v -> true)
            .serverAcceptedVersions(v -> true)
            .simpleChannel();

    // Arbol de habilidades del oficio Policia.
    public static final String[] SKILLS = new String[]{
            "Escudo balistico", "REFUERZO", "Tanque", "Helicoptero", "Tanque movil",
            "Esposas", "Perro K9", "Sirena y torreta", "Dron de vigilancia", "Escudo de burbuja"
    };
    public static final int[] PRE = new int[]{-1, 0, 0, 0, 2, 1, 1, 3, 3, 0};
    public static final int[] LEVEL_COST = new int[]{0, 0, 0, 0, 20, 0, 0, 0, 0, 0};
    public static final int XP_PER_NODE = 10;
    public static final int SHIELD_TICKS = 240;
    public static final int SHIELD_COOLDOWN = 80;
    static final double BLOCK_COS = 0.25;
    static final float BLOCK_FACTOR = 0.15f;
    static final UUID SLOW_ID = UUID.fromString("7c1d2e3a-5b6f-4a90-8c11-2d3e4f5a6b7c");
    static final DustParticleOptions NANO = new DustParticleOptions(new Vector3f(0.25f, 0.85f, 1.0f), 0.8f);
    static final DustParticleOptions NANO2 = new DustParticleOptions(new Vector3f(0.85f, 0.92f, 1.0f), 0.6f);

    static String job(Player p) {
        return p.getPersistentData().getString("pol_job");
    }

    static boolean on(Player p) {
        CompoundTag d = p.getPersistentData();
        return d.getBoolean("pol_on");
    }

    static int xp(Player p) {
        return p.getPersistentData().getInt("pol_xp");
    }

    // Migracion de datos antiguos: antes de la version 2 el mascara de
    // habilidades desbloqueadas usaba otro orden de bits.
    static int un(Player p) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("pol_ver") < 2) {
            int o = d.getInt("pol_un");
            int n = (o & 1) | (((o >> 1) & 1) << 2) | (((o >> 2) & 1) << 3) | (((o >> 3) & 1) << 4);
            d.putInt("pol_un", n);
            int so = d.getInt("pol_sel");
            d.putInt("pol_sel", so == 0 ? 0 : (so == 2 ? 3 : 2));
            d.putInt("pol_ver", 2);
        }
        return d.getInt("pol_un") | 1;
    }

    static boolean has(Player p, int i) {
        return ((un(p) >> i) & 1) == 1;
    }

    static int rlevel(Player p) {
        return has(p, 1) ? Math.max(1, Math.min(4, p.getPersistentData().getInt("pol_rl"))) : 0;
    }

    static int sel(Player p) {
        return p.getPersistentData().getInt("pol_sel");
    }

    static int shield(Player p) {
        return p.getPersistentData().getInt("pol_shield");
    }

    static int cooldown(Player p) {
        return p.getPersistentData().getInt("pol_cd");
    }

    static int unlocked(Player p) {
        return Math.min(SKILLS.length, 1 + xp(p) / 10);
    }

    static void msg(ServerPlayer p, String s) {
        p.displayClientMessage(Component.literal(s), true);
    }

    public PoliciaMod() {
        NET.registerMessage(0, Act.class, Act::enc, Act::dec, Act::handle);
        NET.registerMessage(1, Sync.class, Sync::enc, Sync::dec, Sync::handle);
        MinecraftForge.EVENT_BUS.register(this);
        PoliciaShield.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        PoliciaTank.ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        PoliciaRefuerzo.ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        PoliciaExtra.ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        PoliciaHeli.ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        PoliciaPlaca.init();
        PoliciaLadron.init();
        PoliciaBoss.init();
        PoliciaStruct.init();
        PoliciaBoard.init();
        PoliciaGrupo.init();
        PoliciaAlbanil.init();
        MinecraftForge.EVENT_BUS.register(new PoliciaAlbanil());
        PoliciaHerrero.init();
        PoliciaOficio.init();
        PoliciaJefe.init();
        PoliciaPasivas.init();
        PoliciaAnalizador.init();
        PoliciaExtra.EFFECTS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaExtra::attrs);
        MinecraftForge.EVENT_BUS.register(new PoliciaExtra());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaRefuerzo::attrs);
        if (FMLEnvironment.dist.isClient()) {
            PoliciaClient.init();
        }
    }

    static Sync snapshot(Player p) {
        Sync m = new Sync();
        m.id = p.getUUID();
        m.on = on(p);
        m.shield = shield(p);
        m.cooldown = cooldown(p);
        m.xp = xp(p);
        m.sel = sel(p);
        m.un = un(p);
        m.tcd = p.getPersistentData().getInt("pol_tcd");
        m.rl = rlevel(p);
        m.rcd = p.getPersistentData().getInt("pol_rcd");
        m.c5 = PoliciaExtra.cd(p, 5);
        m.c6 = PoliciaExtra.cd(p, 6);
        m.c7 = PoliciaExtra.cd(p, 7);
        m.c8 = PoliciaExtra.cd(p, 8);
        m.c9 = PoliciaExtra.cd(p, 9);
        m.jb = "albanil".equals(job(p)) ? 2 : (ID.equals(job(p)) ? 1 : ("herrero".equals(job(p)) ? 4 : 0));
        m.hideArmor = p.getPersistentData().getBoolean("pol_hidearmor");
        if ("ladron".equals(job(p))) {
            m.jb = 3;
            m.rl = PoliciaLadron.luck(p);
            m.c5 = PoliciaLadron.cd(p, 0);
            m.c6 = PoliciaLadron.cd(p, 1);
            m.c7 = PoliciaLadron.cd(p, 2);
            m.c8 = PoliciaLadron.cd(p, 3);
            m.c9 = PoliciaLadron.cd(p, 4);
            m.un = PoliciaLadron.mask(p);
            m.sel = PoliciaLadron.sel(p);
            m.rcd = PoliciaLadron.cd(p, 6);
            m.tcd = PoliciaLadron.wanted(p);
        }
        return m;
    }

    static void sync(ServerPlayer p) {
        NET.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), snapshot(p));
        if ("albanil".equals(job(p))) {
            PoliciaAlbanil.send(p);
        }
        if ("herrero".equals(job(p))) {
            PoliciaHerrero.send(p);
        }
    }

    static void act(ServerPlayer p, int id) {
        CompoundTag d = p.getPersistentData();
        if (id == 0) {
            if (!on(p) && job(p).isEmpty()) {
                msg(p, "Estas desempleado: no tenes ningun oficio");
                return;
            }
            d.putBoolean("pol_on", !on(p));
            if (!on(p)) {
                d.putInt("pol_shield", 0);
                endShield(p);
                if ("albanil".equals(job(p))) {
                    PoliciaAlbanil.reset(p);
                }
                if ("herrero".equals(job(p))) {
                    PoliciaHerrero.reset(p);
                }
            }
            d.putInt("pol_tf", 40);
            d.putInt("pol_tfd", on(p) ? 1 : -1);
            transformFx(p);
            msg(p, on(p) ? "Modo " + job(p) + " activado" : "Modo " + job(p) + " desactivado");
            sync(p);
            return;
        }
        if (id == 4) {
            if (p.getVehicle() instanceof PoliciaTractor.TractorEntity) {
                PoliciaTractor.lamp(p);
            } else if ("albanil".equals(job(p))) {
                PoliciaAlbanil.toggleLamp(p);
            } else {
                PoliciaHeli.toggleLight(p);
            }
            return;
        }
        if (id == 5 || id == 6) {
            if (p.getVehicle() instanceof PoliciaTractor.TractorEntity) {
                PoliciaTractor.arm(p, id == 5 ? 1 : -1);
            } else {
                PoliciaHeli.vert(p, id == 5 ? 1 : -1);
            }
            return;
        }
        if (id == 61) {
            PoliciaRefuerzo.cycle(p);
            return;
        }
        if (id == 62) {
            boolean hide = !d.getBoolean("pol_hidearmor");
            d.putBoolean("pol_hidearmor", hide);
            msg(p, hide ? "Armadura oculta" : "Armadura visible");
            sync(p);
            return;
        }
        if (id >= 70 && id <= 74) {
            PoliciaOficio.choose(p, id - 70);
            return;
        }
        if ("ladron".equals(job(p))) {
            PoliciaLadron.act(p, id);
            return;
        }
        if ("albanil".equals(job(p))) {
            PoliciaAlbanil.act(p, id);
            return;
        }
        if ("herrero".equals(job(p))) {
            PoliciaHerrero.act(p, id);
            return;
        }
        if (id == 30) {
            int lv = rlevel(p);
            if (lv <= 0) {
                msg(p, "Desbloquea primero REFUERZO");
                return;
            }
            if (lv >= 4) {
                msg(p, "REFUERZO ya esta al nivel maximo");
                return;
            }
            int cost = 10 * lv;
            if (xp(p) < cost) {
                msg(p, "Necesitas " + cost + " XP para REFUERZO nivel " + (lv + 1) + " (tienes " + xp(p) + ")");
                return;
            }
            d.putInt("pol_xp", xp(p) - cost);
            d.putInt("pol_rl", lv + 1);
            msg(p, "REFUERZO nivel " + (lv + 1) + ": " + PoliciaRefuerzo.COUNT[lv + 1] + " policias, espada de " + PoliciaRefuerzo.SWORD[lv + 1]);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
            sync(p);
            return;
        }
        if (id >= 20 && id < 30) {
            int s = id - 20;
            if (s <= 0 || s >= SKILLS.length || has(p, s)) {
                return;
            }
            if (s == 9) {
                msg(p, "Se gana completando la mision especial del comisario");
                return;
            }
            if (!has(p, PRE[s])) {
                msg(p, "Desbloquea primero: " + SKILLS[PRE[s]]);
                return;
            }
            if (LEVEL_COST[s] > 0) {
                if (p.experienceLevel < LEVEL_COST[s]) {
                    msg(p, "Necesitas " + LEVEL_COST[s] + " niveles de experiencia para desbloquear " + SKILLS[s] + " (tienes " + p.experienceLevel + ")");
                    return;
                }
                p.giveExperienceLevels(-LEVEL_COST[s]);
            } else {
                if (xp(p) < 10) {
                    msg(p, "Necesitas 10 XP para desbloquear " + SKILLS[s]);
                    return;
                }
                d.putInt("pol_xp", xp(p) - 10);
            }
            d.putInt("pol_un", un(p) | (1 << s));
            if (s == 1) {
                d.putInt("pol_rl", 1);
            }
            msg(p, "Habilidad desbloqueada: " + SKILLS[s]);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            sync(p);
            return;
        }
        if (id >= 10) {
            int s = id - 10;
            if (s == 4) {
                s = 2;
            }
            if (s >= 0 && s < SKILLS.length && has(p, s)) {
                d.putInt("pol_sel", s);
                msg(p, "Habilidad: " + SKILLS[s]);
                sync(p);
            }
            return;
        }
        if (id == 3) {
            PoliciaTank.fire(p);
            return;
        }
        if (!on(p)) {
            msg(p, "Activa el modo policia (B) primero");
            return;
        }
        if (id == 2) {
            int s = sel(p);
            for (int k = 1; k <= SKILLS.length; ++k) {
                int c = (sel(p) + k) % SKILLS.length;
                if (c == 4 || !has(p, c)) continue;
                s = c;
                break;
            }
            d.putInt("pol_sel", s);
            msg(p, "Habilidad: " + SKILLS[s]);
            sync(p);
            return;
        }
        if (id == 1) {
            int s = has(p, sel(p)) ? sel(p) : 0;
            if (s == 0) {
                if (shield(p) > 0) {
                    msg(p, "El escudo ya esta activo");
                    return;
                }
                if (cooldown(p) > 0) {
                    msg(p, "Escudo en enfriamiento: " + (cooldown(p) + 19) / 20 + " s");
                    return;
                }
                d.putInt("pol_shield", 240);
                startShield(p);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6f, 1.6f);
                msg(p, "Escudo balistico desplegado");
                sync(p);
            } else if (s == 2 && has(p, 4)) {
                PoliciaTank.useMobile(p);
            } else if (s == 2) {
                if (p.getVehicle() instanceof PoliciaTank.TankEntity) {
                    msg(p, "El tanque ya esta invocado");
                    return;
                }
                if (shield(p) > 0) {
                    msg(p, "Espera a que termine el escudo");
                    return;
                }
                if (d.getInt("pol_tcd") > 0) {
                    msg(p, "Tanque en enfriamiento: " + (d.getInt("pol_tcd") + 19) / 20 + " s");
                    return;
                }
                PoliciaTank.summon(p);
                sync(p);
            } else if (s == 3) {
                PoliciaHeli.use(p);
            } else if (s >= 5) {
                PoliciaExtra.use(p, s);
            } else if (s == 1) {
                PoliciaRefuerzo.use(p);
            } else {
                msg(p, SKILLS[s] + " (proximamente)");
            }
        }
    }

    static void pushMobs(ServerPlayer p) {
        for (LivingEntity m : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.0), x -> x != p && x.isAlive())) {
            m.knockback(1.8f, p.getX() - m.getX(), p.getZ() - m.getZ());
            m.hurtMarked = true;
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4f, 1.8f);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 18, 0.5, 0.1, 0.5, 0.15);
    }

    static void transformFx(ServerPlayer p) {
        boolean form = p.getPersistentData().getInt("pol_tfd") > 0;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), form ? SoundEvents.ARMOR_EQUIP_IRON : SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0f, form ? 1.6f : 1.2f);
    }

    static void transformTick(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        int tf = d.getInt("pol_tf");
        if (tf <= 0) {
            return;
        }
        d.putInt("pol_tf", --tf);
        boolean form = d.getInt("pol_tfd") > 0;
        float prog = (float) (40 - tf) / 40.0f;
        if (tf % 8 == 0) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.7f, 0.8f + prog);
        }
        if (tf == 0) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), form ? SoundEvents.ANVIL_USE : SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, form ? 1.8f : 1.2f);
        }
    }

    @SubscribeEvent
    public void noDamage(LivingAttackEvent e) {
    }

    @SubscribeEvent
    public void noAttack(AttackEntityEvent e) {
        Player player = e.getEntity();
        if (player instanceof ServerPlayer p && (p.getVehicle() instanceof PoliciaTank.TankEntity
                || p.getVehicle() instanceof PoliciaHeli.HeliEntity
                || p.getVehicle() instanceof PoliciaTractor.TractorEntity)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void noInteract(PlayerInteractEvent e) {
        Player player = e.getEntity();
        if (player instanceof ServerPlayer p && (p.getVehicle() instanceof PoliciaTank.TankEntity
                || p.getVehicle() instanceof PoliciaHeli.HeliEntity
                || p.getVehicle() instanceof PoliciaTractor.TractorEntity) && e.isCancelable()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void noBreak(BlockEvent.BreakEvent e) {
        Player player = e.getPlayer();
        if (player instanceof ServerPlayer p && (p.getVehicle() instanceof PoliciaTank.TankEntity
                || p.getVehicle() instanceof PoliciaHeli.HeliEntity
                || p.getVehicle() instanceof PoliciaTractor.TractorEntity)) {
            e.setCanceled(true);
        }
    }

    static boolean isMine(ItemStack s) {
        return s.getItem() instanceof PoliciaShield;
    }

    static void purge(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); ++i) {
            if (!isMine(inv.getItem(i))) continue;
            inv.setItem(i, ItemStack.EMPTY);
        }
    }

    static void startShield(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        ItemStack off = p.getOffhandItem();
        if (!isMine(off)) {
            if (!d.getBoolean("pol_saved")) {
                if (!off.isEmpty()) {
                    d.put("pol_off", off.save(new CompoundTag()));
                } else {
                    d.remove("pol_off");
                }
                d.putBoolean("pol_saved", true);
            } else if (!off.isEmpty()) {
                p.getInventory().placeItemBackInInventory(off.copy());
            }
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack((ItemLike) PoliciaShield.SHIELD.get()));
        }
    }

    static void keepShield(ServerPlayer p) {
        if (!isMine(p.getOffhandItem())) {
            purge(p);
        }
        startShield(p);
    }

    static void endShield(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (p.isUsingItem() && isMine(p.getUseItem())) {
            p.stopUsingItem();
        }
        AttributeInstance mv = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (mv != null) {
            mv.removeModifier(SLOW_ID);
        }
        purge(p);
        if (d.getBoolean("pol_saved")) {
            ItemStack orig = d.contains("pol_off") ? ItemStack.of(d.getCompound("pol_off")) : ItemStack.EMPTY;
            d.remove("pol_off");
            d.putBoolean("pol_saved", false);
            if (!orig.isEmpty()) {
                if (p.getOffhandItem().isEmpty()) {
                    p.setItemInHand(InteractionHand.OFF_HAND, orig);
                } else {
                    p.getInventory().placeItemBackInInventory(orig);
                }
            }
        }
    }

    @SubscribeEvent
    public void death(LivingDeathEvent e) {
        LivingEntity livingEntity = e.getEntity();
        if (livingEntity instanceof ServerPlayer p && (shield(p) > 0 || p.getPersistentData().getBoolean("pol_saved"))) {
            p.getPersistentData().putInt("pol_shield", 0);
            endShield(p);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Player player = e.getEntity();
        if (player instanceof ServerPlayer p) {
            p.getPersistentData().putInt("pol_shield", 0);
            endShield(p);
        }
    }

    @SubscribeEvent
    public void toss(ItemTossEvent e) {
        if (isMine(e.getEntity().getItem())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        boolean change = false;
        transformTick(p);
        int nf = d.getInt("pol_nofall");
        if (nf > 0) {
            d.putInt("pol_nofall", nf - 1);
            p.fallDistance = 0.0f;
        }
        int tc = d.getInt("pol_tcd");
        if (tc > 0) {
            d.putInt("pol_tcd", tc - 1);
            if (tc - 1 == 0) {
                msg(p, "Tanque listo");
                change = true;
            }
        }
        change |= PoliciaExtra.tickCd(p);
        int rc = d.getInt("pol_rcd");
        if (rc > 0) {
            d.putInt("pol_rcd", rc - 1);
            if (rc - 1 == 0) {
                msg(p, "Refuerzo listo");
                change = true;
            }
        }
        int sh = d.getInt("pol_shield");
        if (sh > 0) {
            d.putInt("pol_shield", --sh);
            if (sh > 0) {
                keepShield(p);
            }
            if (sh == 0) {
                endShield(p);
                pushMobs(p);
                d.putInt("pol_cd", 80);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.5f, 1.4f);
                msg(p, "El escudo se retiro");
                change = true;
            }
        } else {
            int cd = d.getInt("pol_cd");
            if (cd > 0) {
                d.putInt("pol_cd", cd - 1);
                if (cd - 1 == 0) {
                    msg(p, "Escudo listo");
                    change = true;
                }
            }
        }
        if (change || p.tickCount % 40 == 0) {
            sync(p);
            PoliciaOficio.login(p);
        }
    }

    @SubscribeEvent
    public void arrowBounce(ProjectileImpactEvent e) {
        HitResult hitResult = e.getRayTraceResult();
        if (!(hitResult instanceof EntityHitResult eh)) {
            return;
        }
        Entity tg = eh.getEntity();
        boolean veh = tg instanceof PoliciaTank.TankEntity || tg instanceof PoliciaHeli.HeliEntity || tg instanceof PoliciaTractor.TractorEntity
                || tg.getVehicle() instanceof PoliciaTank.TankEntity || tg.getVehicle() instanceof PoliciaHeli.HeliEntity || tg.getVehicle() instanceof PoliciaTractor.TractorEntity;
        Projectile projectile = e.getProjectile();
        if (!veh || !(projectile instanceof AbstractArrow a)) {
            return;
        }
        e.setCanceled(true);
        CompoundTag ad = a.getPersistentData();
        if (a.tickCount - ad.getInt("pol_bt") < 8 && ad.contains("pol_bt")) {
            return;
        }
        ad.putInt("pol_bt", Math.max(1, a.tickCount));
        Vec3 mv = a.getDeltaMovement();
        Vec3 back = mv.scale(-0.3);
        a.setDeltaMovement(back);
        a.setPos(a.getX() + back.x * 2.0, a.getY() + back.y * 2.0, a.getZ() + back.z * 2.0);
        a.setYRot(a.getYRot() + 180.0f);
        a.hurtMarked = true;
        a.level().playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    @SubscribeEvent
    public void noKnock(LivingKnockBackEvent e) {
        LivingEntity livingEntity = e.getEntity();
        if (livingEntity instanceof ServerPlayer p && (p.getVehicle() instanceof PoliciaTank.TankEntity
                || p.getVehicle() instanceof PoliciaHeli.HeliEntity || p.getVehicle() instanceof PoliciaTractor.TractorEntity)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void noFall(LivingFallEvent e) {
        LivingEntity livingEntity = e.getEntity();
        if (livingEntity instanceof ServerPlayer p && p.getPersistentData().getInt("pol_nofall") > 0) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onXp(PlayerXpEvent.XpChange e) {
        Player player = e.getEntity();
        if (!(player instanceof ServerPlayer p) || e.getAmount() <= 0) {
            return;
        }
        p.getPersistentData().putInt("pol_xp", xp(p) + e.getAmount());
        sync(p);
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        CompoundTag o = e.getOriginal().getPersistentData();
        CompoundTag n = e.getEntity().getPersistentData();
        for (String k : new String[]{
                "pol_on", "pol_xp", "pol_sel", "pol_un", "pol_ver", "pol_rl", "pol_job", "pol_hcd_t",
                "alb_lv", "alb_pl", "alb_hl", "alb_ench", "alb_mode", "alb_bank", "alb_lu", "alb_tu",
                "alb_tinv", "alb_sel", "alb_chest", "alb_inner", "alb_c0", "alb_c1",
                "pol_mdone", "pol_bdone", "pol_cerebro", "pol_lsu", "pol_lsi", "pol_lun", "pol_lse",
                "pol_cin0", "pol_cin2", "pol_comp", "pol_party", "pol_hidearmor",
                "her_sel", "her_cd0", "her_cd1", "her_cd2", "her_cd3", "her_cd4", "her_cd5", "her_cd6", "her_cd7", "her_cd8",
                "her_an_known", "her_an_qitem", "her_an_qtype", "her_an_qprog", "her_an_qneed", "her_an_qtarget", "pol_chosen", "jm_id", "jm_prog", "jm_acc"}) {
            if (!o.contains(k)) continue;
            n.put(k, o.get(k).copy());
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        Player player = e.getEntity();
        if (player instanceof ServerPlayer p) {
            if (on(p) && job(p).isEmpty()) {
                p.getPersistentData().putBoolean("pol_on", false);
            }
            sync(p);
        }
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent e) {
        Player player = e.getEntity();
        if (player instanceof ServerPlayer p) {
            sync(p);
        }
    }

    @SubscribeEvent
    public void track(PlayerEvent.StartTracking e) {
        Entity entity = e.getTarget();
        if (entity instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer viewer) {
            NET.send(PacketDistributor.PLAYER.with(() -> viewer), snapshot(t));
        }
    }

    public static class Act {
        final int id;

        Act(int id) {
            this.id = id;
        }

        static void enc(Act m, FriendlyByteBuf b) {
            b.writeVarInt(m.id);
        }

        static Act dec(FriendlyByteBuf b) {
            return new Act(b.readVarInt());
        }

        static void handle(Act m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p != null) {
                    PoliciaMod.act(p, m.id);
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    public static class Sync {
        UUID id = new UUID(0L, 0L);
        boolean on;
        int shield;
        int cooldown;
        int xp;
        int sel;
        int un;
        int tcd;
        int rl;
        int rcd;
        int c5;
        int c6;
        int c7;
        int c8;
        int c9;
        int jb;
        boolean hideArmor;

        Sync() {
        }

        static void enc(Sync m, FriendlyByteBuf b) {
            b.writeUUID(m.id);
            b.writeBoolean(m.on);
            b.writeVarInt(m.shield);
            b.writeVarInt(m.cooldown);
            b.writeVarInt(m.xp);
            b.writeVarInt(m.sel);
            b.writeVarInt(m.un);
            b.writeVarInt(m.tcd);
            b.writeVarInt(m.rl);
            b.writeVarInt(m.rcd);
            b.writeVarInt(m.c5);
            b.writeVarInt(m.c6);
            b.writeVarInt(m.c7);
            b.writeVarInt(m.c8);
            b.writeVarInt(m.c9);
            b.writeVarInt(m.jb);
            b.writeBoolean(m.hideArmor);
        }

        static Sync dec(FriendlyByteBuf b) {
            Sync m = new Sync();
            m.id = b.readUUID();
            m.on = b.readBoolean();
            m.shield = b.readVarInt();
            m.cooldown = b.readVarInt();
            m.xp = b.readVarInt();
            m.sel = b.readVarInt();
            m.un = b.readVarInt();
            m.tcd = b.readVarInt();
            m.rl = b.readVarInt();
            m.rcd = b.readVarInt();
            m.c5 = b.readVarInt();
            m.c6 = b.readVarInt();
            m.c7 = b.readVarInt();
            m.c8 = b.readVarInt();
            m.c9 = b.readVarInt();
            m.jb = b.readVarInt();
            m.hideArmor = b.readBoolean();
            return m;
        }

        static void handle(Sync m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PoliciaClient.onSync(m)));
            c.get().setPacketHandled(true);
        }
    }
}
