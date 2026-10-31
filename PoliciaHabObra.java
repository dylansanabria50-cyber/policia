package com.example.policia;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// Etapa 6a: habilidades nuevas del Policia (Camara de seguridad, Dominio de
// la ley) y del Albanil (Grua torre, Ascensor, Taladro de tunel, Puno de
// tierra, Torre instantanea).
public class PoliciaHabObra {
    // Jugadores con el Dominio de la ley activo: sus golpes son criticos.
    static final Set<UUID> DOMINIO = new HashSet<UUID>();

    static boolean usar(ServerPlayer p, String id) {
        return switch (id) {
            case "camara" -> camara(p);
            case "dominio" -> dominio(p);
            case "grua" -> grua(p);
            case "ascensor" -> ascensor(p);
            case "taladro" -> taladro(p);
            case "puno" -> puno(p);
            case "torre" -> torre(p);
            default -> false;
        };
    }

    static boolean vivo(ServerPlayer p) {
        return p.isAlive() && !p.hasDisconnected();
    }

    // ---------------- Policia ----------------
    static boolean camara(ServerPlayer p) {
        BlockHitResult h = PoliciaHab.bloque(p, 6.0);
        if (h == null) {
            PoliciaMod.msg(p, "Mirá una pared o un bloque cercano para atornillar la cámara");
            return false;
        }
        ServerLevel sl = p.serverLevel();
        Vec3 c = Vec3.atCenterOf(h.getBlockPos()).add(Vec3.atLowerCornerOf(h.getDirection().getNormal()).scale(0.6));
        sl.playSound(null, BlockPos.containing(c), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.0f, 1.6f);
        Set<Integer> vistos = new HashSet<Integer>();
        PoliciaHab.tarea(t -> {
            if (t % 10 == 0) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 1, 0.02, 0.02, 0.02, 0.0);
                if ((t / 10) % 2 == 0) {
                    sl.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE, c.x, c.y + 0.15, c.z, 1, 0, 0, 0, 0);
                }
            }
            if (t % 20 == 0) {
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(20.0), x -> x.isAlive() && x != p)) {
                    boolean sospechoso = e instanceof Enemy || (e instanceof ServerPlayer o && "ladron".equals(PoliciaMod.job(o)));
                    if (!sospechoso) {
                        continue;
                    }
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
                    if (vistos.add(e.getId()) && vistos.size() <= 6 && vivo(p)) {
                        p.displayClientMessage(Component.literal("§c● REC §7La cámara vio: §f" + e.getName().getString()), true);
                    }
                }
            }
            return t >= 1200;
        });
        return true;
    }

    static boolean dominio(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        double r = 10.0;
        DOMINIO.add(p.getUUID());
        sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 0.6f);
        PoliciaHab.tarea(t -> {
            if (t % 8 == 0) {
                // cupula de particulas
                for (int i = 0; i < 48; i++) {
                    double a = i * Math.PI * 2 / 48;
                    for (int k = 0; k < 4; k++) {
                        double phi = k * Math.PI / 8;
                        double rr = r * Math.cos(phi);
                        sl.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a + t * 0.01) * rr, c.y + r * Math.sin(phi), c.z + Math.sin(a + t * 0.01) * rr, 1, 0, 0, 0, 0);
                    }
                }
            }
            if (t % 20 == 10) {
                sl.playSound(null, BlockPos.containing(c), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.4f, t % 40 == 10 ? 1.6f : 1.2f);
            }
            for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(r + 4), x -> x instanceof Enemy && x.isAlive())) {
                Vec3 d = m.position().subtract(c);
                double dist = Math.sqrt(d.x * d.x + d.z * d.z);
                if (dist > r - 0.8 && dist < r + 4) {
                    Vec3 empuje = new Vec3(-d.x, 0, -d.z).normalize().scale(0.5);
                    m.setDeltaMovement(empuje.x, 0.15, empuje.z);
                    m.hurtMarked = true;
                }
                if (t % 20 == 0 && dist <= r) {
                    m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false));
                    m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
                }
            }
            if (t >= 400) {
                DOMINIO.remove(p.getUUID());
                sl.playSound(null, BlockPos.containing(c), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5f, 0.7f);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), c.x, c.y + 4, c.z, 200, r * 0.6, 3, r * 0.6, 0.3);
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------------- Albanil ----------------
    static boolean movible(ServerLevel sl, BlockPos pos, BlockState st) {
        return !st.isAir() && st.getFluidState().isEmpty() && st.getDestroySpeed(sl, pos) >= 0.0f
                && !st.is(Blocks.BEDROCK) && !st.is(Blocks.END_PORTAL_FRAME) && !st.is(Blocks.SPAWNER);
    }

    static boolean grua(ServerPlayer p) {
        CompoundTag h = PoliciaHab.datos(p);
        ServerLevel sl = p.serverLevel();
        BlockHitResult hit = PoliciaHab.bloque(p, 24.0);
        if (!h.contains("grua")) {
            if (hit == null || p.position().distanceTo(Vec3.atCenterOf(hit.getBlockPos())) > 8.0) {
                PoliciaMod.msg(p, "Mirá un bloque o cofre a menos de 8 bloques para engancharlo");
                return false;
            }
            BlockPos src = hit.getBlockPos();
            BlockState st = sl.getBlockState(src);
            if (!movible(sl, src, st)) {
                PoliciaMod.msg(p, "La grúa no puede levantar eso");
                return false;
            }
            h.put("grua", NbtUtils.writeBlockPos(src));
            sl.playSound(null, src, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
            sl.sendParticles(ParticleTypes.END_ROD, src.getX() + 0.5, src.getY() + 1.1, src.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.01);
            PoliciaMod.msg(p, "Enganchado: " + st.getBlock().getName().getString() + ". Mirá dónde dejarlo y usá Z otra vez");
            PoliciaAnim.play(p, "alb_grua");
            return false;
        }
        BlockPos src = NbtUtils.readBlockPos(h.getCompound("grua"));
        if (hit == null) {
            PoliciaMod.msg(p, "Mirá dónde querés dejar la carga (o agachate + Z para soltar el gancho)");
            if (p.isShiftKeyDown()) {
                h.remove("grua");
            }
            return false;
        }
        BlockPos dst = sl.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        BlockState st = sl.getBlockState(src);
        if (!movible(sl, src, st) || src.distSqr(dst) > 24 * 24 || !sl.getBlockState(dst).canBeReplaced() || dst.equals(src)) {
            PoliciaMod.msg(p, "No se puede dejar ahí (máximo 24 bloques y en un lugar libre)");
            h.remove("grua");
            return false;
        }
        BlockEntity be = sl.getBlockEntity(src);
        CompoundTag nbt = be != null ? be.saveWithoutMetadata() : null;
        if (be instanceof Clearable cl) {
            cl.clearContent();
        }
        sl.setBlock(src, Blocks.AIR.defaultBlockState(), 3);
        sl.setBlock(dst, st, 3);
        if (nbt != null) {
            BlockEntity nb = sl.getBlockEntity(dst);
            if (nb != null) {
                nb.load(nbt);
                nb.setChanged();
            }
        }
        h.remove("grua");
        sl.playSound(null, dst, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.4f);
        sl.sendParticles(ParticleTypes.CLOUD, dst.getX() + 0.5, dst.getY() + 0.2, dst.getZ() + 0.5, 12, 0.4, 0.1, 0.4, 0.02);
        return true;
    }

    static boolean ascensor(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        BlockPos base = p.blockPosition();
        int top = -999;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int y = 40; y >= 2; y--) {
                    BlockPos q = base.offset(dx, y, dz);
                    if (!sl.getBlockState(q).isAir() && sl.getBlockState(q).isSolid()) {
                        top = Math.max(top, base.getY() + y);
                        break;
                    }
                }
            }
        }
        // no subir a traves de un techo
        for (int y = 1; y <= Math.max(2, top - base.getY() + 2); y++) {
            BlockPos q = base.above(y);
            if (sl.getBlockState(q).isSolid()) {
                top = Math.min(top, base.getY() + y - 3);
                break;
            }
        }
        if (top < base.getY() + 3) {
            PoliciaMod.msg(p, "No hay nada alto al lado tuyo para subir");
            return false;
        }
        final double meta = top + 1.2;
        sl.playSound(null, base, SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 1.0f, 0.7f);
        PoliciaHab.tarea(t -> {
            if (!vivo(p)) {
                return true;
            }
            p.resetFallDistance();
            if (p.getY() < meta && t < 160) {
                p.setDeltaMovement(p.getDeltaMovement().x * 0.5, 0.55, p.getDeltaMovement().z * 0.5);
                p.hurtMarked = true;
                if (t % 4 == 0) {
                    sl.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() - 0.2, p.getZ(), 4, 0.4, 0.1, 0.4, 0.0);
                }
                return false;
            }
            Vec3 l = p.getLookAngle();
            p.setDeltaMovement(l.x * 0.35, 0.25, l.z * 0.35);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
            sl.playSound(null, p.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.8f, 1.2f);
            return true;
        });
        return true;
    }

    static boolean rompible(ServerLevel sl, BlockPos q) {
        BlockState st = sl.getBlockState(q);
        return !st.isAir() && PoliciaPlaced.natural(st) && !PoliciaPlaced.has(sl, q) && sl.getBlockEntity(q) == null
                && st.getDestroySpeed(sl, q) >= 0.0f && st.getFluidState().isEmpty();
    }

    static boolean taladro(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Direction dir = p.getDirection();
        Direction lado = dir.getClockWise();
        BlockPos inicio = p.blockPosition().relative(dir);
        PoliciaHab.tarea(t -> {
            if (t % 2 != 0) {
                return false;
            }
            int i = t / 2;
            if (i >= 10) {
                return true;
            }
            BlockPos c = inicio.relative(dir, i);
            for (int s = -1; s <= 1; s++) {
                for (int y = 0; y <= 2; y++) {
                    BlockPos q = c.relative(lado, s).above(y);
                    if (rompible(sl, q)) {
                        sl.destroyBlock(q, true, p);
                    }
                }
            }
            if (i % 4 == 2) {
                BlockPos tq = c.relative(lado, -1);
                if (sl.getBlockState(tq).isAir() && sl.getBlockState(tq.below()).isFaceSturdy(sl, tq.below(), Direction.UP)) {
                    sl.setBlock(tq, Blocks.TORCH.defaultBlockState(), 3);
                }
            }
            sl.playSound(null, c, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.8f, 0.6f + i * 0.02f);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, 4, 0.6, 0.6, 0.6, 0.01);
            return false;
        });
        return true;
    }

    static boolean puno(ServerPlayer p) {
        LivingEntity v = PoliciaHab.mirando(p, 16.0);
        if (v == null || PoliciaHab.aliado(p, v)) {
            PoliciaMod.msg(p, "Mirá a un enemigo a menos de 16 bloques");
            return false;
        }
        ServerLevel sl = p.serverLevel();
        BlockParticleOption piedra = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        Vec3 desde = p.position();
        PoliciaHab.tarea(t -> {
            if (!v.isAlive()) {
                return true;
            }
            if (t <= 8) {
                // grieta que corre por el piso hasta el enemigo
                Vec3 q = desde.lerp(v.position(), t / 8.0);
                sl.sendParticles(piedra, q.x, q.y + 0.1, q.z, 8, 0.2, 0.0, 0.2, 0.1);
                if (t == 0) {
                    sl.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
                }
            } else if (t == 10) {
                v.setDeltaMovement(0, 1.15, 0);
                v.hurtMarked = true;
                sl.sendParticles(piedra, v.getX(), v.getY(), v.getZ(), 60, 0.6, 0.6, 0.6, 0.2);
                sl.playSound(null, v.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);
            } else if (t == 22) {
                v.setDeltaMovement(0, -2.2, 0);
                v.hurtMarked = true;
            } else if (t == 26) {
                float dano = 8.0f + Math.max(0, PoliciaRPG.atributo(p, "fuerza") - 5) * 0.3f;
                v.hurt(p.damageSources().playerAttack(p), dano);
                sl.sendParticles(piedra, v.getX(), v.getY(), v.getZ(), 80, 1.0, 0.2, 1.0, 0.25);
                sl.sendParticles(ParticleTypes.EXPLOSION, v.getX(), v.getY(), v.getZ(), 1, 0, 0, 0, 0);
                sl.playSound(null, v.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.4f);
                return true;
            }
            return false;
        });
        return true;
    }

    static boolean torre(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        BlockPos base = p.blockPosition();
        for (int y = 2; y <= 10; y++) {
            if (!sl.getBlockState(base.above(y)).isAir()) {
                PoliciaMod.msg(p, "No hay lugar arriba para la torre");
                return false;
            }
        }
        List<BlockPos> puestos = new ArrayList<BlockPos>();
        PoliciaHab.tarea(t -> {
            if (t < 16) {
                if (t % 2 == 0 && vivo(p)) {
                    BlockPos q = base.above(t / 2);
                    if (sl.getBlockState(q).isAir() || sl.getBlockState(q).canBeReplaced()) {
                        p.teleportTo(p.getX(), q.getY() + 1.0, p.getZ());
                        sl.setBlock(q, Blocks.COBBLESTONE.defaultBlockState(), 3);
                        puestos.add(q);
                        sl.playSound(null, q, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f + t * 0.03f);
                        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()), q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 10, 0.6, 0.3, 0.6, 0.1);
                    }
                    p.resetFallDistance();
                }
                return false;
            }
            if (t >= 1200) {
                for (int i = puestos.size() - 1; i >= 0; i--) {
                    BlockPos q = puestos.get(i);
                    if (sl.getBlockState(q).is(Blocks.COBBLESTONE)) {
                        sl.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
                        sl.sendParticles(ParticleTypes.POOF, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.01);
                    }
                }
                return true;
            }
            return false;
        });
        return true;
    }
}
