package com.example.policia;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Oficio Herrero Legendario: forja y templa equipo, resiste el fuego de
// la fragua y golpea con un martillo sismico. Se consigue con el Martillo
// del Herrero Legendario (vease PoliciaPlaca).
public class PoliciaHerrero {
    static final String JOB = "herrero";
    public static final String[] SKILLS = new String[]{
            "Mano Firme", "Ojo del Metal", "Resistencia de Fragua", "Martillazo Sismico",
            "Cadena al Rojo Vivo", "Armadura de Brasas", "Reparacion Maestra", "Templar Equipo",
            "Forja Legendaria"
    };
    static final int[] THRESH = new int[]{0, 15, 25, 35, 45, 55, 70, 85, 100};
    static final Set<Block> ORES = Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.ANCIENT_DEBRIS);
    static final UUID TEMPER_DMG = UUID.fromString("9a2f7b10-4c3e-4d55-8f21-6b0a1c2d3e4f");
    static final UUID TEMPER_ARM = UUID.fromString("b4e8c231-7a5d-4f66-9e32-7c1b2d3e4f50");

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaHerrero());
    }

    static boolean isHerrero(Player p) {
        return JOB.equals(PoliciaMod.job(p));
    }

    static int unlocked(ServerPlayer p) {
        int xp = PoliciaMod.xp((Player) p);
        int n = 0;
        for (int t : THRESH) {
            if (xp < t) break;
            ++n;
        }
        return Math.max(1, n);
    }

    static int sel(ServerPlayer p) {
        return Math.max(0, Math.min(SKILLS.length - 1, p.getPersistentData().getInt("her_sel")));
    }

    static int cd(ServerPlayer p, int i) {
        return p.getPersistentData().getInt("her_cd" + i);
    }

    static void act(ServerPlayer p, int id) {
        CompoundTag d = p.getPersistentData();
        if (id >= 10 && id < 20) {
            int s = id - 10;
            if (s >= 0 && s < unlocked(p)) {
                d.putInt("her_sel", s);
                PoliciaMod.msg(p, "Habilidad: " + SKILLS[s]);
            }
            return;
        }
        if (id == 2) {
            int n = unlocked(p);
            int s = (PoliciaHerrero.sel(p) + 1) % n;
            d.putInt("her_sel", s);
            PoliciaMod.msg(p, "Habilidad: " + SKILLS[s]);
            return;
        }
        if (id != 1) {
            return;
        }
        if (!PoliciaMod.on(p)) {
            PoliciaMod.msg(p, "Activa el modo herrero (B) primero");
            return;
        }
        int s = PoliciaHerrero.sel(p);
        if (s >= unlocked(p)) {
            s = 0;
        }
        if (PoliciaHerrero.cd(p, s) > 0) {
            PoliciaMod.msg(p, SKILLS[s] + " en enfriamiento: " + (PoliciaHerrero.cd(p, s) + 19) / 20 + " s");
            return;
        }
        switch (s) {
            case 0: {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0, false, false, true));
                PoliciaMod.msg(p, "Mano Firme: resistencia durante 15 s");
                d.putInt("her_cd0", 400);
                break;
            }
            case 1: {
                PoliciaHerrero.scanOres(p);
                d.putInt("her_cd1", 400);
                break;
            }
            case 2: {
                p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0, false, false, true));
                PoliciaMod.msg(p, "Resistencia de Fragua: inmune al fuego 60 s");
                d.putInt("her_cd2", 1800);
                break;
            }
            case 3: {
                PoliciaHerrero.hammerSlam(p);
                d.putInt("her_cd3", 240);
                break;
            }
            case 4: {
                if (!PoliciaHerrero.hotChain(p)) {
                    PoliciaMod.msg(p, "Cadena al Rojo Vivo: sin objetivo a la vista");
                    return;
                }
                d.putInt("her_cd4", 200);
                break;
            }
            case 5: {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0, false, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, false, true));
                PoliciaMod.msg(p, "Armadura de Brasas: resistencia e inmunidad al fuego 10 s");
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.8f);
                d.putInt("her_cd5", 600);
                break;
            }
            case 6: {
                ItemStack it = p.getMainHandItem();
                if (it.isEmpty() || !it.isDamageableItem() || it.getDamageValue() <= 0) {
                    PoliciaMod.msg(p, "Reparacion Maestra: necesitas una herramienta o arma dañada en la mano");
                    return;
                }
                if (PoliciaMod.xp((Player) p) < 15) {
                    PoliciaMod.msg(p, "Necesitas 15 XP para Reparacion Maestra");
                    return;
                }
                d.putInt("pol_xp", PoliciaMod.xp((Player) p) - 15);
                it.setDamageValue(0);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
                PoliciaMod.msg(p, "Reparacion Maestra: equipo como nuevo");
                d.putInt("her_cd6", 1200);
                break;
            }
            case 7: {
                PoliciaHerrero.temper(p);
                d.putInt("her_temper", 600);
                PoliciaMod.msg(p, "Templar Equipo: +danio y +armadura durante 30 s");
                d.putInt("her_cd7", 900);
                break;
            }
            case 8: {
                if (unlocked(p) < SKILLS.length) {
                    PoliciaMod.msg(p, "Forja Legendaria aun no esta disponible");
                    return;
                }
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1, false, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0, false, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0, false, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0, false, false, true));
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.7f, 1.3f);
                if (p.level() instanceof ServerLevel sl) {
                    sl.sendParticles((ParticleOptions) ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.6, 0.8, 0.6, 0.1);
                }
                PoliciaMod.msg(p, "Forja Legendaria: avatar de la fragua durante 20 s");
                d.putInt("her_cd8", 2400);
                break;
            }
        }
    }

    static void scanOres(ServerPlayer p) {
        int found = 0;
        BlockPos c = p.blockPosition();
        ServerLevel sl = p.serverLevel();
        for (BlockPos bp : BlockPos.betweenClosed(c.offset(-16, -16, -16), c.offset(16, 16, 16))) {
            if (!ORES.contains(sl.getBlockState(bp).getBlock())) continue;
            ++found;
            sl.sendParticles((ParticleOptions) ParticleTypes.END_ROD, (double) bp.getX() + 0.5, (double) bp.getY() + 1.2, (double) bp.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
        PoliciaMod.msg(p, "Ojo del Metal: " + found + " menas detectadas en 16 m");
    }

    static void hammerSlam(ServerPlayer p) {
        boolean hit = false;
        for (LivingEntity m : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4.0), x -> x != p && x.isAlive())) {
            m.hurt(p.damageSources().playerAttack(p), 6.0f);
            m.knockback(1.2f, p.getX() - m.getX(), p.getZ() - m.getZ());
            m.hurtMarked = true;
            hit = true;
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.8f);
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles((ParticleOptions) ParticleTypes.CRIT, p.getX(), p.getY() + 0.1, p.getZ(), 20, 1.5, 0.1, 1.5, 0.2);
        }
        PoliciaMod.msg(p, hit ? "Martillazo Sismico!" : "Martillazo Sismico (sin objetivos cerca)");
    }

    static boolean hotChain(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(20.0));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) p));
        double maxD = bh.getType() == HitResult.Type.MISS ? 20.0 : eye.distanceTo(bh.getLocation());
        LivingEntity target = null;
        double bestD = maxD * maxD;
        for (LivingEntity m : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(20.0), x -> x != p && x.isAlive())) {
            Vec3 toM = m.position().subtract(eye);
            double proj = toM.dot(p.getLookAngle());
            if (proj <= 0.0 || proj > maxD) continue;
            double perp = toM.lengthSqr() - proj * proj;
            if (perp > 2.5 || proj * proj >= bestD) continue;
            bestD = proj * proj;
            target = m;
        }
        if (target == null) {
            return false;
        }
        Vec3 pull = p.position().subtract(target.position()).normalize().scale(1.3);
        target.setDeltaMovement(pull.x, 0.35, pull.z);
        target.hurtMarked = true;
        target.setSecondsOnFire(4);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    static void temper(ServerPlayer p) {
        AttributeInstance dmg = p.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance arm = p.getAttribute(Attributes.ARMOR);
        if (dmg != null) {
            dmg.removeModifier(TEMPER_DMG);
            dmg.addTransientModifier(new AttributeModifier(TEMPER_DMG, "her_temper_dmg", 2.0, AttributeModifier.Operation.ADDITION));
        }
        if (arm != null) {
            arm.removeModifier(TEMPER_ARM);
            arm.addTransientModifier(new AttributeModifier(TEMPER_ARM, "her_temper_arm", 3.0, AttributeModifier.Operation.ADDITION));
        }
    }

    static void endTemper(ServerPlayer p) {
        AttributeInstance dmg = p.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance arm = p.getAttribute(Attributes.ARMOR);
        if (dmg != null) {
            dmg.removeModifier(TEMPER_DMG);
        }
        if (arm != null) {
            arm.removeModifier(TEMPER_ARM);
        }
    }

    static void reset(ServerPlayer p) {
        PoliciaHerrero.endTemper(p);
        p.getPersistentData().putInt("her_temper", 0);
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        Player player;
        if (e.phase != TickEvent.Phase.END || !((player = e.player) instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer p = (ServerPlayer) player;
        if (!PoliciaHerrero.isHerrero(p)) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        for (int i = 0; i < SKILLS.length; ++i) {
            int c = d.getInt("her_cd" + i);
            if (c <= 0) continue;
            d.putInt("her_cd" + i, --c);
            if (c == 0) {
                PoliciaMod.msg(p, SKILLS[i] + " lista");
            }
        }
        int tp = d.getInt("her_temper");
        if (tp > 0) {
            d.putInt("her_temper", --tp);
            if (tp == 0) {
                PoliciaHerrero.endTemper(p);
                PoliciaMod.msg(p, "Templar Equipo termino");
            }
        }
    }

    @SubscribeEvent
    public void death(LivingDeathEvent e) {
        LivingEntity living = e.getEntity();
        if (living instanceof ServerPlayer p && PoliciaHerrero.isHerrero(p)) {
            PoliciaHerrero.reset(p);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Player player = e.getEntity();
        if (player instanceof ServerPlayer p && PoliciaHerrero.isHerrero(p)) {
            PoliciaHerrero.reset(p);
        }
    }
}
