package com.example.policia;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

// Combate RPG (lado servidor):
// - Numeros de dano: cada golpe manda su valor a los que lo ven (blanco,
//   amarillo si es critico, naranja si cierra un combo, rojo si te pegan).
// - Esquivar (tecla Alt izquierdo): una rodada hacia donde te movés, con
//   8 ticks de invulnerabilidad. Gasta energia.
// - Parada: si levantás el escudo justo antes del golpe (8 ticks), no
//   recibis nada y el atacante queda aturdido.
// - Combos: golpes cuerpo a cuerpo seguidos (menos de 1,2 s entre si); el
//   tercero pega 30 % mas fuerte y empuja.
// - Energia: 100 (+2 por punto de Ingenio), se recupera sola. La gastan
//   esquivar y las habilidades nuevas (Z).
// - Elites: algunos monstruos aparecen con 1 o 2 rasgos (Veloz, Blindado,
//   Vampiro, Igneo, Explosivo, Furioso). Son mas duros y dejan mejor botin.
public class PoliciaCombate {
    static final String[] RASGOS = {"Veloz", "Blindado", "Vampiro", "Ígneo", "Explosivo", "Furioso"};
    static final java.util.UUID MOD_VIDA = java.util.UUID.fromString("a91c7f30-2d4e-4b8a-9f13-5c6e7d8a9b01");
    static final java.util.UUID MOD_VEL = java.util.UUID.fromString("a91c7f30-2d4e-4b8a-9f13-5c6e7d8a9b02");
    static final java.util.UUID MOD_ARM = java.util.UUID.fromString("a91c7f30-2d4e-4b8a-9f13-5c6e7d8a9b03");
    static final java.util.UUID MOD_DANO = java.util.UUID.fromString("a91c7f30-2d4e-4b8a-9f13-5c6e7d8a9b04");

    // Golpes marcados en este tick (para el color del numero).
    static final Set<Integer> CRITICOS = new HashSet<Integer>();
    static final Set<Integer> COMBOS = new HashSet<Integer>();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosCombate());
    }

    static void marcarCritico(LivingEntity v) {
        CRITICOS.add(v.getId());
    }

    // ---------------- Energia ----------------
    static float energiaMax(ServerPlayer p) {
        return 100.0f + Math.max(0, PoliciaRPG.atributo(p, "ingenio") - PoliciaRPG.BASE_ATRIBUTO) * 2.0f + PoliciaBotin.bonoEnergia(p);
    }

    static float energia(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains("energia")) {
            d.putFloat("energia", energiaMax(p));
        }
        return d.getFloat("energia");
    }

    static boolean gastar(ServerPlayer p, float n) {
        float e = energia(p);
        if (e < n) {
            p.displayClientMessage(Component.literal("§9Te falta energía §7(" + (int) e + "/" + (int) n + ")"), true);
            return false;
        }
        p.getPersistentData().putFloat("energia", e - n);
        sync(p);
        return true;
    }

    static void sync(ServerPlayer p) {
        PoliciaRed.toPlayer(p, new PoliciaRed.EnergiaSync(energia(p), energiaMax(p)));
    }

    // Costo de energia de una habilidad nueva segun su enfriamiento.
    static float costoHab(int cdTicks) {
        return Math.max(10.0f, Math.min(45.0f, cdTicks / 20.0f * 0.5f));
    }

    // ---------------- Esquivar ----------------
    static void esquivar(ServerPlayer p, float dx, float dz) {
        CompoundTag d = p.getPersistentData();
        long ahora = p.level().getGameTime();
        if (!p.onGround() || p.isPassenger() || p.isInWater() || p.isFallFlying() || ahora < d.getLong("esq_cd")) {
            return;
        }
        if (!gastar(p, 20.0f)) {
            return;
        }
        Vec3 dir = new Vec3(dx, 0.0, dz);
        if (dir.lengthSqr() < 1.0E-4) {
            Vec3 l = p.getLookAngle();
            dir = new Vec3(-l.x, 0.0, -l.z);
        }
        dir = dir.normalize().scale(0.95);
        p.setDeltaMovement(dir.x, 0.28, dir.z);
        p.hurtMarked = true;
        d.putLong("esq_hasta", ahora + 8);
        d.putLong("esq_cd", ahora + 14);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.6f);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
        PoliciaAnim.play(p, "rodar");
    }

    // ---------------- Numeros de dano ----------------
    // tipo: 0 normal, 1 critico, 2 combo, 3 parada, 4 esquivado, 5 te pegan
    static void numero(LivingEntity v, float cant, int tipo) {
        PoliciaRed.CH.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> v), new PoliciaRed.DanoNum(v.getId(), cant, tipo));
    }

    // ---------------- Elites ----------------
    static boolean esElite(LivingEntity e) {
        return e.getPersistentData().contains("elite");
    }

    static void volverElite(Monster m, ServerLevel sl) {
        java.util.Random r = new java.util.Random();
        List<Integer> l = new ArrayList<Integer>();
        int n = r.nextInt(3) == 0 ? 2 : 1;
        while (l.size() < n) {
            int k = r.nextInt(RASGOS.length);
            if (!l.contains(k)) {
                l.add(k);
            }
        }
        StringBuilder nombre = new StringBuilder("§6★ ");
        int mask = 0;
        for (int k : l) {
            mask |= 1 << k;
            nombre.append(RASGOS[k]).append(" ");
        }
        m.getPersistentData().putInt("elite", mask);
        mod(m, Attributes.MAX_HEALTH, MOD_VIDA, 1.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if ((mask & 1) != 0) {
            mod(m, Attributes.MOVEMENT_SPEED, MOD_VEL, 0.4, AttributeModifier.Operation.MULTIPLY_BASE);
        }
        if ((mask & 2) != 0) {
            mod(m, Attributes.ARMOR, MOD_ARM, 8.0, AttributeModifier.Operation.ADDITION);
            mod(m, Attributes.KNOCKBACK_RESISTANCE, MOD_ARM, 0.6, AttributeModifier.Operation.ADDITION);
        }
        if ((mask & 32) != 0) {
            mod(m, Attributes.ATTACK_DAMAGE, MOD_DANO, 0.6, AttributeModifier.Operation.MULTIPLY_BASE);
        }
        if ((mask & 8) != 0) {
            m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1000000, 0, false, false));
        }
        m.setHealth(m.getMaxHealth());
        m.setCustomName(Component.literal(nombre.toString().trim() + " §f" + m.getType().getDescription().getString()));
    }

    static void mod(LivingEntity m, net.minecraft.world.entity.ai.attributes.Attribute a, java.util.UUID id, double v, AttributeModifier.Operation op) {
        AttributeInstance in = m.getAttribute(a);
        if (in != null && in.getModifier(id) == null) {
            in.addPermanentModifier(new AttributeModifier(id, "policia elite", v, op));
        }
    }

    public static class EventosCombate {
        // Invulnerable mientras rueda; parada con el escudo recien levantado.
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void ataque(LivingAttackEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            long ahora = p.level().getGameTime();
            if (ahora < p.getPersistentData().getLong("esq_hasta") && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                e.setCanceled(true);
                numero(p, 0, 4);
                return;
            }
            if (p.isUsingItem() && p.getUseItem().getItem() instanceof ShieldItem && p.getTicksUsingItem() <= 8
                    && e.getSource().getEntity() instanceof LivingEntity at && e.getSource().getDirectEntity() == at) {
                e.setCanceled(true);
                at.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3));
                at.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 1));
                at.knockback(1.0, p.getX() - at.getX(), p.getZ() - at.getZ());
                p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.9f);
                p.level().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.4f);
                p.serverLevel().sendParticles(ParticleTypes.CRIT, at.getX(), at.getY() + at.getBbHeight() * 0.6, at.getZ(), 20, 0.3, 0.3, 0.3, 0.4);
                numero(p, 0, 3);
                PoliciaNivel.dar(p, 3);
            }
        }

        // Combos cuerpo a cuerpo y elites (vampiro, igneo).
        @SubscribeEvent(priority = EventPriority.LOW)
        public void golpe(LivingHurtEvent e) {
            LivingEntity v = e.getEntity();
            if (e.getSource().getDirectEntity() instanceof ServerPlayer p && e.getSource().getEntity() == p) {
                CompoundTag d = p.getPersistentData();
                long ahora = p.level().getGameTime();
                int n = ahora - d.getLong("cmb_t") <= 24 ? d.getInt("cmb_n") + 1 : 1;
                d.putInt("cmb_n", n);
                d.putLong("cmb_t", ahora);
                if (n % 3 == 0) {
                    e.setAmount(e.getAmount() * 1.3f);
                    v.knockback(0.8, p.getX() - v.getX(), p.getZ() - v.getZ());
                    COMBOS.add(v.getId());
                    p.level().playSound(null, v.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.8f);
                    p.displayClientMessage(Component.literal("§6¡Combo x" + n + "!"), true);
                }
            }
            if (e.getSource().getEntity() instanceof Monster m && esElite(m)) {
                int mask = m.getPersistentData().getInt("elite");
                if ((mask & 4) != 0) {
                    m.heal(e.getAmount() * 0.5f);
                }
                if ((mask & 8) != 0) {
                    v.setSecondsOnFire(3);
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void dano(LivingDamageEvent e) {
            LivingEntity v = e.getEntity();
            if (v.level().isClientSide || e.getAmount() <= 0.0f) {
                return;
            }
            int tipo = v instanceof ServerPlayer ? 5 : CRITICOS.remove(v.getId()) ? 1 : COMBOS.contains(v.getId()) ? 2 : 0;
            COMBOS.remove(v.getId());
            CRITICOS.remove(v.getId());
            numero(v, e.getAmount(), tipo);
        }

        @SubscribeEvent
        public void aparece(EntityJoinLevelEvent e) {
            if (e.getLevel().isClientSide || e.loadedFromDisk() || !(e.getEntity() instanceof Monster m) || esElite(m) || m.getPersistentData().getBoolean("elite_no")) {
                return;
            }
            m.getPersistentData().putBoolean("elite_no", true);
            ServerLevel sl = (ServerLevel) e.getLevel();
            int nivel = 1;
            try {
                nivel = PoliciaZonas.zona(sl, m.blockPosition()).nivel;
            } catch (Throwable ignored) {
            }
            float prob = Math.min(0.2f, 0.05f + nivel * 0.003f);
            if (m.getRandom().nextFloat() < prob) {
                m.getPersistentData().remove("elite_no");
                volverElite(m, sl);
            }
        }

        // Aura de particulas segun el rasgo.
        @SubscribeEvent
        public void aura(LivingEvent.LivingTickEvent e) {
            LivingEntity m = e.getEntity();
            if (m.level().isClientSide || m.tickCount % 8 != 0 || !esElite(m)) {
                return;
            }
            int mask = m.getPersistentData().getInt("elite");
            ServerLevel sl = (ServerLevel) m.level();
            var tipo = (mask & 8) != 0 ? ParticleTypes.FLAME : (mask & 4) != 0 ? ParticleTypes.DAMAGE_INDICATOR : (mask & 16) != 0 ? ParticleTypes.SMOKE : (mask & 1) != 0 ? ParticleTypes.CLOUD : ParticleTypes.ENCHANTED_HIT;
            sl.sendParticles(tipo, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 2, m.getBbWidth() * 0.4, m.getBbHeight() * 0.3, m.getBbWidth() * 0.4, 0.01);
        }

        @SubscribeEvent
        public void muere(LivingDeathEvent e) {
            LivingEntity m = e.getEntity();
            if (m.level().isClientSide || !esElite(m)) {
                return;
            }
            int mask = m.getPersistentData().getInt("elite");
            if ((mask & 16) != 0) {
                m.level().explode(m, m.getX(), m.getY() + 0.5, m.getZ(), 2.0f, Level.ExplosionInteraction.NONE);
            }
            if (e.getSource().getEntity() instanceof ServerPlayer p) {
                PoliciaNivel.dar(p, 35);
                PoliciaFacciones.sumar(p, "guardia", 1);
                p.displayClientMessage(Component.literal("§6Derrotaste a un élite: §e+35 XP"), true);
            }
        }

        @SubscribeEvent
        public void botin(LivingDropsEvent e) {
            LivingEntity m = e.getEntity();
            if (m.level().isClientSide || !esElite(m)) {
                return;
            }
            var r = m.getRandom();
            e.getDrops().add(new ItemEntity(m.level(), m.getX(), m.getY(), m.getZ(), new ItemStack(Items.EMERALD, 2 + r.nextInt(4))));
            if (r.nextFloat() < 0.35f) {
                ItemStack[] posibles = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_HELMET), new ItemStack(Items.BOW), new ItemStack(Items.DIAMOND_SWORD)};
                ItemStack it = posibles[r.nextInt(posibles.length)].copy();
                PoliciaRareza.ponerRareza(it, 1 + r.nextInt(3));
                PoliciaBotin.tirarAfijos(it, r);
                e.getDrops().add(new ItemEntity(m.level(), m.getX(), m.getY(), m.getZ(), it));
            }
        }

        // Energia: se recupera sola (4 por segundo).
        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) {
                return;
            }
            float max = energiaMax(p);
            float v = energia(p);
            if (v < max) {
                p.getPersistentData().putFloat("energia", Math.min(max, v + 1.0f));
                if (p.tickCount % 10 == 0 || v + 1.0f >= max) {
                    sync(p);
                }
            } else if (v > max) {
                p.getPersistentData().putFloat("energia", max);
                sync(p);
            } else if (p.tickCount % 200 == 0) {
                sync(p);
            }
        }
    }
}
