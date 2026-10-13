package com.example.policia;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Oficio Herrero Legendario: forja y templa equipo, planta yunques que
// protegen o potencian, y se vincula con un aliado. Se consigue con el
// Martillo del Herrero Legendario (vease PoliciaPlaca). La ultima
// habilidad (Analizador del Forjador) es pasiva y vive en PoliciaAnalizador.
public class PoliciaHerrero {
    static final String JOB = "herrero";
    public static final String[] SKILLS = new String[]{
            "Grito del Forjador", "Yunque Portatil", "Marca del Herrero", "Forja Interior",
            "Faro de Forja", "Vinculo de Acero", "Reparacion Maestra", "Yunque Guardian",
            "Analizador del Forjador"
    };
    static final int[] THRESH = new int[]{0, 15, 25, 35, 45, 55, 70, 85};
    static final int ANALIZADOR_XP = 35;
    static final UUID MARK_ARM = UUID.fromString("b4e8c231-7a5d-4f66-9e32-7c1b2d3e4f50");

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
        return Math.max(0, Math.min(SKILLS.length - 2, p.getPersistentData().getInt("her_sel")));
    }

    static int cd(ServerPlayer p, int i) {
        return p.getPersistentData().getInt("her_cd" + i);
    }

    static void act(ServerPlayer p, int id) {
        CompoundTag d = p.getPersistentData();
        int usable = Math.min(unlocked(p), SKILLS.length - 1);
        if (id >= 10 && id < 20) {
            int s = id - 10;
            if (s >= 0 && s < usable) {
                d.putInt("her_sel", s);
                PoliciaMod.msg(p, "Habilidad: " + SKILLS[s]);
            }
            return;
        }
        if (id == 2) {
            int s = (PoliciaHerrero.sel(p) + 1) % usable;
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
        if (s >= usable) {
            s = 0;
        }
        if (PoliciaHerrero.cd(p, s) > 0) {
            PoliciaMod.msg(p, SKILLS[s] + " en enfriamiento: " + (PoliciaHerrero.cd(p, s) + 19) / 20 + " s");
            return;
        }
        switch (s) {
            case 0: {
                PoliciaHerrero.forgerShout(p);
                d.putInt("her_cd0", 500);
                break;
            }
            case 1: {
                if (!PoliciaHerrero.placeAnvil(p, 0)) {
                    return;
                }
                d.putInt("her_cd1", 900);
                break;
            }
            case 2: {
                if (!PoliciaHerrero.mark(p)) {
                    PoliciaMod.msg(p, "Marca del Herrero: sin objetivo a la vista");
                    return;
                }
                d.putInt("her_cd2", 400);
                break;
            }
            case 3: {
                p.heal(6.0f);
                if (p.level() instanceof ServerLevel sl) {
                    sl.sendParticles((ParticleOptions) ParticleTypes.HEART, p.getX(), p.getY() + 1.4, p.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
                }
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
                PoliciaMod.msg(p, "Forja Interior: +3 corazones");
                d.putInt("her_cd3", 700);
                break;
            }
            case 4: {
                BlockPos fp = p.blockPosition();
                if (!PoliciaHerrero.free(p, fp)) {
                    PoliciaMod.msg(p, "No hay espacio libre para el Faro de Forja");
                    return;
                }
                p.level().setBlockAndUpdate(fp, Blocks.CAMPFIRE.defaultBlockState());
                d.putLong("her_faro_pos", fp.asLong());
                d.putInt("her_faro_t", 1200);
                PoliciaMod.msg(p, "Faro de Forja encendido: cura y da inmunidad al fuego cerca suyo");
                d.putInt("her_cd4", 1000);
                break;
            }
            case 5: {
                if (!PoliciaHerrero.link(p)) {
                    PoliciaMod.msg(p, "Vinculo de Acero: necesitas otro jugador cerca");
                    return;
                }
                d.putInt("her_cd5", 1200);
                break;
            }
            case 6: {
                ItemStack it = p.getMainHandItem();
                if (it.isEmpty() || !it.isDamageableItem() || it.getDamageValue() <= 0) {
                    PoliciaMod.msg(p, "Reparacion Maestra: necesitas una herramienta o arma danada en la mano");
                    return;
                }
                if (PoliciaMod.xp((Player) p) < 15) {
                    PoliciaMod.msg(p, "Necesitas 15 XP para Reparacion Maestra");
                    return;
                }
                d.putInt("pol_xp", PoliciaMod.xp((Player) p) - 15);
                it.setDamageValue(0);
                d.putInt("her_rep_t", 1);
                if (p.level() instanceof ServerLevel sl) {
                    PoliciaForja.spawn(sl, p.blockPosition());
                }
                PoliciaMod.msg(p, "Reparacion Maestra: equipo como nuevo");
                d.putInt("her_cd6", 1200);
                break;
            }
            case 7: {
                if (!PoliciaHerrero.placeAnvil(p, 1)) {
                    return;
                }
                d.putInt("her_cd7", 900);
                break;
            }
        }
    }

    static boolean free(ServerPlayer p, BlockPos bp) {
        return p.level().getBlockState(bp).isAir() || p.level().getBlockState(bp).canBeReplaced();
    }

    static void forgerShout(ServerPlayer p) {
        for (LivingEntity m : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6.0), x -> x != p && x.isAlive())) {
            m.knockback(1.0f, p.getX() - m.getX(), p.getZ() - m.getZ());
            m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, false, true));
            if (m instanceof Mob mob && !mob.getMainHandItem().isEmpty()) {
                ItemStack held = mob.getMainHandItem();
                mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                mob.spawnAtLocation(held);
            }
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.7f, 1.6f);
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles((ParticleOptions) ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
        PoliciaMod.msg(p, "Grito del Forjador!");
    }

    static boolean placeAnvil(ServerPlayer p, int type) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("her_anvil_t") > 0) {
            PoliciaMod.msg(p, "Ya hay un yunque plantado");
            return false;
        }
        BlockPos bp = p.blockPosition().relative(p.getDirection());
        if (!PoliciaHerrero.free(p, bp)) {
            bp = p.blockPosition();
        }
        if (!PoliciaHerrero.free(p, bp)) {
            PoliciaMod.msg(p, "No hay espacio libre para el yunque");
            return false;
        }
        p.level().setBlockAndUpdate(bp, Blocks.ANVIL.defaultBlockState());
        d.putLong("her_anvil_pos", bp.asLong());
        d.putInt("her_anvil_t", 800);
        d.putInt("her_anvil_type", type);
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles((ParticleOptions) ParticleTypes.POOF, p.getX(), p.getY() + 1.6, p.getZ(), 10, 0.2, 0.3, 0.2, 0.05);
            PoliciaForja.spawn(sl, bp);
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.9f);
        PoliciaMod.msg(p, type == 0 ? "Yunque Portatil plantado: mas danio cerca suyo" : "Yunque Guardian plantado: intercepta proyectiles cercanos");
        return true;
    }

    static void removeAnvil(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("her_anvil_t") <= 0) {
            return;
        }
        BlockPos bp = BlockPos.of(d.getLong("her_anvil_pos"));
        if (p.level().getBlockState(bp).is(Blocks.ANVIL)) {
            p.level().setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
        }
        d.putInt("her_anvil_t", 0);
    }

    static boolean mark(ServerPlayer p) {
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
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, true, true));
        AttributeInstance arm = target.getAttribute(Attributes.ARMOR);
        if (arm != null) {
            arm.removeModifier(MARK_ARM);
            arm.addTransientModifier(new AttributeModifier(MARK_ARM, "her_mark", -4.0, AttributeModifier.Operation.ADDITION));
        }
        target.getPersistentData().putInt("her_marked_t", 300);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 1.5f);
        PoliciaMod.msg(p, "Marca del Herrero: " + target.getDisplayName().getString() + " recibira mas danio durante 15 s");
        return true;
    }

    static boolean link(ServerPlayer p) {
        Player best = null;
        double bd = 100.0;
        for (Player o : p.level().players()) {
            double dist;
            if (o == p || !((dist = o.distanceToSqr(p)) < bd)) continue;
            bd = dist;
            best = o;
        }
        if (best == null || !(best instanceof ServerPlayer)) {
            return false;
        }
        ServerPlayer other = (ServerPlayer) best;
        p.getPersistentData().putUUID("her_link_id", other.getUUID());
        p.getPersistentData().putInt("her_link_t", 600);
        other.getPersistentData().putUUID("her_link_id", p.getUUID());
        other.getPersistentData().putInt("her_link_t", 600);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8f, 1.1f);
        PoliciaMod.msg(p, "Vinculo de Acero con " + other.getName().getString());
        PoliciaMod.msg(other, "Vinculo de Acero con " + p.getName().getString());
        return true;
    }

    static void endLink(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("her_link_t") <= 0 && !d.hasUUID("her_link_id")) {
            return;
        }
        d.putInt("her_link_t", 0);
        d.remove("her_link_id");
    }

    static void reset(ServerPlayer p) {
        PoliciaHerrero.removeAnvil(p);
        PoliciaHerrero.endLink(p);
        CompoundTag d = p.getPersistentData();
        if (d.getInt("her_faro_t") > 0 && d.contains("her_faro_pos")) {
            BlockPos bp = BlockPos.of(d.getLong("her_faro_pos"));
            if (p.level().getBlockState(bp).is(Blocks.CAMPFIRE)) {
                p.level().setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
            }
        }
        d.putInt("her_faro_t", 0);
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
        for (int i = 0; i < SKILLS.length - 1; ++i) {
            int c = d.getInt("her_cd" + i);
            if (c <= 0) continue;
            d.putInt("her_cd" + i, --c);
            if (c == 0) {
                PoliciaMod.msg(p, SKILLS[i] + " lista");
            }
        }
        int at = d.getInt("her_anvil_t");
        if (at > 0) {
            d.putInt("her_anvil_t", --at);
            BlockPos bp = BlockPos.of(d.getLong("her_anvil_pos"));
            int type = d.getInt("her_anvil_type");
            if (type == 0) {
                if (p.blockPosition().distSqr(bp) < 64.0) {
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 30, 0, false, false, false));
                }
            } else if (p.level() instanceof ServerLevel sl) {
                for (Projectile proj : sl.getEntitiesOfClass(Projectile.class, new net.minecraft.world.phys.AABB(bp).inflate(4.0))) {
                    sl.sendParticles((ParticleOptions) ParticleTypes.CRIT, proj.getX(), proj.getY(), proj.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
                    sl.playSound(null, proj.getX(), proj.getY(), proj.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6f, 1.4f);
                    proj.discard();
                }
            }
            if (at == 0) {
                PoliciaHerrero.removeAnvil(p);
                PoliciaMod.msg(p, "El yunque se desmorono");
            }
        }
        int ft = d.getInt("her_faro_t");
        if (ft > 0) {
            d.putInt("her_faro_t", --ft);
            if (d.contains("her_faro_pos")) {
                BlockPos bp = BlockPos.of(d.getLong("her_faro_pos"));
                if (p.blockPosition().distSqr(bp) < 64.0) {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 0, false, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 30, 0, false, false, false));
                }
                if (ft == 0 && p.level().getBlockState(bp).is(Blocks.CAMPFIRE)) {
                    p.level().setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
                    PoliciaMod.msg(p, "El Faro de Forja se apago");
                }
            }
        }
        int mt = d.getInt("her_marked_t");
        if (mt > 0) {
            d.putInt("her_marked_t", --mt);
        }
        int lt = d.getInt("her_link_t");
        if (lt > 0) {
            d.putInt("her_link_t", --lt);
            if (d.hasUUID("her_link_id") && p.level() instanceof ServerLevel sl) {
                Entity other = sl.getEntity(d.getUUID("her_link_id"));
                if (other != null && p.tickCount % 3 == 0) {
                    Vec3 a = p.position().add(0.0, 1.0, 0.0);
                    Vec3 b = other.position().add(0.0, 1.0, 0.0);
                    Vec3 step = b.subtract(a).scale(1.0 / 6.0);
                    for (int k = 0; k <= 6; ++k) {
                        Vec3 pt = a.add(step.scale(k));
                        sl.sendParticles((ParticleOptions) ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
            if (lt == 0) {
                PoliciaHerrero.endLink(p);
                PoliciaMod.msg(p, "Vinculo de Acero termino");
            }
        }
        int rt = d.getInt("her_rep_t");
        if (rt > 0) {
            if (rt == 1 && p.level() instanceof ServerLevel sl) {
                sl.sendParticles((ParticleOptions) ParticleTypes.POOF, p.getX(), p.getY() + 1.5, p.getZ(), 6, 0.2, 0.3, 0.2, 0.05);
            }
            if ((rt == 1 || rt == 7 || rt == 13) && p.level() instanceof ServerLevel) {
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.9f, 1.0f + (float) rt * 0.02f);
                if (p.level() instanceof ServerLevel sl2) {
                    sl2.sendParticles((ParticleOptions) ParticleTypes.CRIT, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
                }
            }
            d.putInt("her_rep_t", rt + 1 > 14 ? 0 : rt + 1);
        }
    }

    @SubscribeEvent
    public void damage(LivingDamageEvent e) {
        LivingEntity hit = e.getEntity();
        if (!(hit instanceof ServerPlayer p) || !p.getPersistentData().hasUUID("her_link_id") || p.getPersistentData().getInt("her_link_t") <= 0) {
            return;
        }
        if (p.level() instanceof ServerLevel sl) {
            Entity otherE = sl.getEntity(p.getPersistentData().getUUID("her_link_id"));
            if (otherE instanceof ServerPlayer other && other.getPersistentData().getInt("her_link_t") > 0) {
                other.heal(e.getAmount() * 0.3f);
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
