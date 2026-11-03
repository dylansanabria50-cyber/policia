package com.example.policia;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Especializaciones: al llegar a nivel 15 cada oficio se divide en dos
// caminos. La eleccion se guarda en los datos RPG ("espec") y da bonos
// pasivos propios. Cambiarla despues cuesta esmeraldas.
public class PoliciaEspecial {
    public static final int NIVEL = 15;
    public static final int COSTO_CAMBIO = 20;

    // {id, oficio, nombre, linea1, linea2, linea3}
    public static final String[][] TABLA = {
            {"comisario", "policia", "Comisario", "Aliados a 10 bloques: +10 % de daño", "+4 de vida máxima", "Tus refuerzos y perros pegan más fuerte"},
            {"tirador", "policia", "Francotirador", "+25 % de daño con arco y ballesta", "15 % de crítico a distancia (x1,5)", "Ves la vida del enemigo desde más lejos"},
            {"ingeniero", "albanil", "Ingeniero", "+30 % de velocidad al picar", "Andamios y obras más rápidas", "+1 de suerte al minar"},
            {"demoledor", "albanil", "Demoledor", "+20 % de daño con pico y hacha", "-50 % de daño por explosiones", "+2 de armadura"},
            {"sombra", "ladron", "Sombra", "+30 % de daño por la espalda", "Agachado y quieto 3 s: te volvés invisible", "+5 % de velocidad"},
            {"estafador", "ladron", "Estafador", "+2 de suerte", "10 % de esmeralda extra al matar", "Los aldeanos te dan más en los trueques"},
            {"armero", "herrero", "Maestro armero", "+10 % de daño cuerpo a cuerpo", "+15 % de velocidad de ataque", "Tus golpes rompen la guardia (empujón)"},
            {"guardian", "herrero", "Guardián de hierro", "+4 de armadura y +2 de dureza", "30 % de resistencia al empuje", "+4 de vida máxima"},
            {"cirujano", "medico", "Cirujano", "+30 % de curación a vos y aliados a 12 bloques", "Las curas sacan un efecto negativo", "+2 de vida máxima"},
            {"alquimista", "medico", "Alquimista de campo", "Pociones y elixires duran +50 %", "Al beber, los aliados a 5 bloques reciben el efecto", "+10 % de XP"},
            {"magnate", "mercader", "Magnate", "20 % de esmeralda extra en cada trueque", "5 % de esmeralda extra al matar", "+1 de suerte"},
            {"aventurero", "mercader", "Aventurero", "+10 % de velocidad", "+4 de vida máxima", "+10 % de XP"}};

    static final UUID M_VIDA = UUID.fromString("7c1e2d3f-0001-4a5b-8c9d-0e1f2a3b4c01");
    static final UUID M_ARM = UUID.fromString("7c1e2d3f-0002-4a5b-8c9d-0e1f2a3b4c02");
    static final UUID M_DUR = UUID.fromString("7c1e2d3f-0003-4a5b-8c9d-0e1f2a3b4c03");
    static final UUID M_EMP = UUID.fromString("7c1e2d3f-0004-4a5b-8c9d-0e1f2a3b4c04");
    static final UUID M_VEL = UUID.fromString("7c1e2d3f-0005-4a5b-8c9d-0e1f2a3b4c05");
    static final UUID M_SUE = UUID.fromString("7c1e2d3f-0006-4a5b-8c9d-0e1f2a3b4c06");
    static final UUID M_ATV = UUID.fromString("7c1e2d3f-0007-4a5b-8c9d-0e1f2a3b4c07");

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosEspecial());
    }

    public static String[] fila(String id) {
        for (String[] f : TABLA) {
            if (f[0].equals(id)) {
                return f;
            }
        }
        return null;
    }

    public static String de(Player p) {
        String e = PoliciaRPG.datos(p).getString("espec");
        String[] f = fila(e);
        // si cambio de oficio, la especializacion vieja no cuenta
        return f != null && f[1].equals(PoliciaMod.job(p)) ? e : "";
    }

    static boolean es(Player p, String id) {
        return id.equals(de(p));
    }

    static void elegir(ServerPlayer p, String id) {
        String[] f = fila(id);
        if (f == null || !f[1].equals(PoliciaMod.job(p))) {
            PoliciaMod.msg(p, "Esa especialización no es de tu oficio");
            return;
        }
        if (PoliciaRPG.nivel(p) < NIVEL) {
            PoliciaMod.msg(p, "Necesitás nivel " + NIVEL + " para especializarte");
            return;
        }
        String antes = de(p);
        if (antes.equals(id)) {
            return;
        }
        if (!antes.isEmpty() && !PoliciaHabOficios.pagar(p, COSTO_CAMBIO)) {
            return;
        }
        PoliciaRPG.datos(p).putString("espec", id);
        aplicar(p);
        PoliciaRPG.sync(p);
        ServerLevel sl = p.serverLevel();
        sl.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
        PoliciaHistoria.titulo(p, "§6" + f[2], "§7Nueva especialización");
        p.displayClientMessage(Component.literal("§6Ahora sos §e" + f[2] + "§6. §7" + f[3] + " · " + f[4] + " · " + f[5]), false);
    }

    // Pone o saca los modificadores de atributos segun la especializacion.
    static void aplicar(ServerPlayer p) {
        String e = de(p);
        double vida = switch (e) {
            case "comisario", "guardian", "aventurero" -> 4.0;
            case "cirujano" -> 2.0;
            default -> 0.0;
        };
        PoliciaRPG.poner(p, Attributes.MAX_HEALTH, M_VIDA, "espec_vida", vida, AttributeModifier.Operation.ADDITION);
        PoliciaRPG.poner(p, Attributes.ARMOR, M_ARM, "espec_armadura", e.equals("guardian") ? 4.0 : e.equals("demoledor") ? 2.0 : 0.0, AttributeModifier.Operation.ADDITION);
        PoliciaRPG.poner(p, Attributes.ARMOR_TOUGHNESS, M_DUR, "espec_dureza", e.equals("guardian") ? 2.0 : 0.0, AttributeModifier.Operation.ADDITION);
        PoliciaRPG.poner(p, Attributes.KNOCKBACK_RESISTANCE, M_EMP, "espec_empuje", e.equals("guardian") ? 0.3 : 0.0, AttributeModifier.Operation.ADDITION);
        PoliciaRPG.poner(p, Attributes.MOVEMENT_SPEED, M_VEL, "espec_vel", e.equals("aventurero") ? 0.1 : e.equals("sombra") ? 0.05 : 0.0, AttributeModifier.Operation.MULTIPLY_BASE);
        PoliciaRPG.poner(p, Attributes.LUCK, M_SUE, "espec_suerte", e.equals("estafador") ? 2.0 : e.equals("ingeniero") || e.equals("magnate") ? 1.0 : 0.0, AttributeModifier.Operation.ADDITION);
        PoliciaRPG.poner(p, Attributes.ATTACK_SPEED, M_ATV, "espec_atv", e.equals("armero") ? 0.15 : 0.0, AttributeModifier.Operation.MULTIPLY_BASE);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    // XP extra (lo suma PoliciaNivel).
    static float bonoXp(Player p) {
        String e = de(p);
        return e.equals("alquimista") || e.equals("aventurero") ? 0.1f : 0.0f;
    }

    static boolean cercaDe(Player quien, String espec, double r) {
        for (Player o : quien.level().players()) {
            if (o.distanceToSqr(quien) <= r * r && es(o, espec)) {
                return true;
            }
        }
        return false;
    }

    static boolean porLaEspalda(LivingEntity atacante, LivingEntity v) {
        Vec3 mira = Vec3.directionFromRotation(0, v.yBodyRot);
        Vec3 hacia = atacante.position().subtract(v.position()).multiply(1, 0, 1).normalize();
        return mira.dot(hacia) < -0.35;
    }

    public static class EventosEspecial {
        @SubscribeEvent
        public void dano(LivingHurtEvent e) {
            LivingEntity v = e.getEntity();
            if (e.getSource().getEntity() instanceof ServerPlayer p && v != p) {
                float m = 1.0f;
                boolean distancia = e.getSource().getDirectEntity() instanceof AbstractArrow;
                ItemStack mano = p.getMainHandItem();
                switch (de(p)) {
                    case "tirador" -> {
                        if (distancia) {
                            m *= 1.25f;
                            if (p.getRandom().nextFloat() < 0.15f) {
                                m *= 1.5f;
                                PoliciaCombate.marcarCritico(v);
                            }
                        }
                    }
                    case "demoledor" -> {
                        if (!distancia && (mano.getItem() instanceof PickaxeItem || mano.getItem() instanceof AxeItem)) {
                            m *= 1.2f;
                        }
                    }
                    case "sombra" -> {
                        if (!distancia && porLaEspalda(p, v)) {
                            m *= 1.3f;
                            p.serverLevel().sendParticles(ParticleTypes.SMOKE, v.getX(), v.getY() + 1, v.getZ(), 8, 0.2, 0.3, 0.2, 0.02);
                        }
                    }
                    case "armero" -> {
                        if (!distancia) {
                            m *= 1.1f;
                            v.knockback(0.3, p.getX() - v.getX(), p.getZ() - v.getZ());
                        }
                    }
                    default -> {
                    }
                }
                if (!es(p, "comisario") && cercaDe(p, "comisario", 10.0)) {
                    m *= 1.1f;
                } else if (es(p, "comisario")) {
                    m *= 1.1f;
                }
                e.setAmount(e.getAmount() * m);
            }
            // aliados del comisario (perros y agentes)
            if (e.getSource().getEntity() instanceof net.minecraft.world.entity.TamableAnimal ta && ta.getOwner() instanceof Player o && es(o, "comisario")) {
                e.setAmount(e.getAmount() * 1.25f);
            }
            if (v instanceof ServerPlayer p && es(p, "demoledor") && e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
                e.setAmount(e.getAmount() * 0.5f);
            }
        }

        @SubscribeEvent
        public void curar(LivingHealEvent e) {
            LivingEntity v = e.getEntity();
            if (v.level().isClientSide || !(v instanceof Player || v instanceof net.minecraft.world.entity.npc.AbstractVillager)) {
                return;
            }
            if (v instanceof Player pv && cercaDe(pv, "cirujano", 12.0) || !(v instanceof Player) && hayCirujano(v)) {
                e.setAmount(e.getAmount() * 1.3f);
                if (v.getRandom().nextFloat() < 0.15f) {
                    for (MobEffectInstance ef : v.getActiveEffects()) {
                        if (!ef.getEffect().isBeneficial()) {
                            final MobEffect quitar = ef.getEffect();
                            PoliciaHab.tarea(t -> {
                                v.removeEffect(quitar);
                                return true;
                            });
                            break;
                        }
                    }
                }
            }
        }

        static boolean hayCirujano(LivingEntity v) {
            for (Player o : v.level().players()) {
                if (o.distanceToSqr(v) <= 144 && es(o, "cirujano")) {
                    return true;
                }
            }
            return false;
        }

        @SubscribeEvent
        public void beber(LivingEntityUseItemEvent.Finish e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || !es(p, "alquimista")) {
                return;
            }
            ItemStack st = e.getItem();
            java.util.List<MobEffectInstance> efs = new java.util.ArrayList<MobEffectInstance>(PotionUtils.getMobEffects(st));
            efs.addAll(PoliciaCocina.efectosDe(st));
            if (efs.isEmpty()) {
                return;
            }
            PoliciaHab.tarea(t -> {
                for (MobEffectInstance m : efs) {
                    if (m.getEffect().isInstantenous()) {
                        continue;
                    }
                    p.addEffect(new MobEffectInstance(m.getEffect(), (int) (m.getDuration() * 1.5), m.getAmplifier()));
                }
                for (LivingEntity o : PoliciaAnime.aliados(p, 5.0)) {
                    if (o == p) {
                        continue;
                    }
                    for (MobEffectInstance m : efs) {
                        if (m.getEffect().isInstantenous()) {
                            m.getEffect().applyInstantenousEffect(p, p, o, m.getAmplifier(), 0.5);
                        } else {
                            o.addEffect(new MobEffectInstance(m.getEffect(), m.getDuration() / 2, m.getAmplifier()));
                        }
                    }
                    p.serverLevel().sendParticles(ParticleTypes.EFFECT, o.getX(), o.getY() + 1, o.getZ(), 10, 0.3, 0.5, 0.3, 0.1);
                }
                return true;
            });
        }

        @SubscribeEvent
        public void trueque(TradeWithVillagerEvent e) {
            if (e.getEntity() instanceof ServerPlayer p && (es(p, "magnate") && p.getRandom().nextFloat() < 0.2f || es(p, "estafador") && p.getRandom().nextFloat() < 0.12f)) {
                PoliciaHabOficios.dar(p, new ItemStack(Items.EMERALD));
                p.displayClientMessage(Component.literal("§a+1 esmeralda §7(buen trato)"), true);
            }
        }

        @SubscribeEvent
        public void botin(LivingDropsEvent e) {
            if (e.getSource().getEntity() instanceof ServerPlayer p && e.getEntity() instanceof Enemy) {
                float ch = es(p, "estafador") ? 0.10f : es(p, "magnate") ? 0.05f : 0.0f;
                if (ch > 0 && p.getRandom().nextFloat() < ch) {
                    LivingEntity v = e.getEntity();
                    e.getDrops().add(new ItemEntity(v.level(), v.getX(), v.getY() + 0.5, v.getZ(), new ItemStack(Items.EMERALD)));
                }
            }
        }

        @SubscribeEvent
        public void picar(PlayerEvent.BreakSpeed e) {
            if (es(e.getEntity(), "ingeniero")) {
                e.setNewSpeed(e.getNewSpeed() * 1.3f);
            }
        }

        @SubscribeEvent
        public void tickEspecial(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) {
                return;
            }
            if (p.tickCount % 100 == 0) {
                aplicar(p);
            }
            if (es(p, "sombra")) {
                CompoundTag h = p.getPersistentData();
                boolean quieto = p.isCrouching() && p.getDeltaMovement().horizontalDistanceSqr() < 0.0005;
                int n = quieto ? h.getInt("espec_quieto") + 10 : 0;
                h.putInt("espec_quieto", n);
                if (n >= 60) {
                    p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, true));
                }
            }
        }

        @SubscribeEvent
        public void entrarEspecial(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                aplicar(p);
            }
        }

        @SubscribeEvent
        public void revivirEspecial(PlayerEvent.PlayerRespawnEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                aplicar(p);
            }
        }
    }
}
