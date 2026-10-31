package com.example.policia;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

// Etapa 6b: habilidades nuevas del Ladron (Senuelo, Trampa de alarma, Bolsa
// sin fondo, Tirolesa, Paso sombra) y del Herrero (Lluvia de metal, Golem
// forjado, Horno portatil, Campana de bronce, Mil espadas, Rueda de
// adaptacion, Escudo de mil placas, Forja del tiempo, Armero y Fundicion de
// emergencia). Los nombres y efectos son propios.
public class PoliciaHabExtra {
    static final UUID MOD_PLACAS = UUID.fromString("9a7e3c21-0b4d-4e8f-a1c2-5d6e7f8a9b01");
    // Rueda de adaptacion: hasta cuando dura y cuantas veces vino cada dano.
    static final Map<UUID, Object[]> RUEDA = new HashMap<UUID, Object[]>();
    static final Map<UUID, Long> PLACAS = new HashMap<UUID, Long>();
    // Forja del tiempo: historial de la mano principal (tiempo, item, dano).
    static final Map<UUID, ArrayDeque<Object[]>> HISTORIAL = new HashMap<UUID, ArrayDeque<Object[]>>();
    static final DustParticleOptions HIERRO = new DustParticleOptions(new Vector3f(0.75f, 0.77f, 0.82f), 1.2f);
    static final DustParticleOptions ORO = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.2f), 1.4f);

    static {
        PoliciaHab.def("senuelo", "ladron", "Señuelo", "Tirás un muñeco con tu forma: los monstruos van por él mientras te volvés invisible. A los 8 s revienta.", 5, 1, 40, "lad_senuelo");
        PoliciaHab.def("trampa", "ladron", "Trampa de alarma", "Tendés un hilo donde mirás (dura 2 min): el primero que lo cruza queda marcado, lento, y te avisa.", 8, 1, 30, "lad_trampa");
        PoliciaHab.def("bolsa", "ladron", "Bolsa sin fondo", "Durante 5 min, si morís no perdés lo que tengas en la barra rápida: vuelve con vos al reaparecer.", 10, 1, 300, "lad_bolsa");
        PoliciaHab.def("tirolesa", "ladron", "Tirolesa", "Lanzás una cuerda con gancho hasta 40 bloques y te deslizás hasta ahí sin daño por caída.", 14, 1, 25, "lad_tirolesa");
        PoliciaHab.def("paso", "ladron", "Paso sombra", "Desaparecés entre hojas y aparecés a la espalda del enemigo que mirás, invisible y con más fuerza un instante.", 18, 1, 20, "lad_paso");
        PoliciaHab.def("lluvia", "herrero", "Lluvia de metal", "Durante 3 s caen lingotes al rojo sobre donde mirás: queman y golpean a los enemigos.", 5, 1, 35, "her_lluvia");
        PoliciaHab.def("horno", "herrero", "Horno portátil", "Funde al instante los minerales crudos de tu inventario (hasta 64).", 6, 1, 20, "her_horno");
        PoliciaHab.def("armero", "herrero", "Armero", "Durante 30 s un martillito encantado repara de a poco la armadura que llevás puesta.", 8, 1, 60, "her_armero");
        PoliciaHab.def("golem", "herrero", "Gólem de hierro forjado", "Forjás un gólem de hierro que pelea a tu lado durante 1 minuto.", 10, 2, 120, "her_golem");
        PoliciaHab.def("fundicion", "herrero", "Fundición de emergencia", "Las herramientas, armas y armaduras muy gastadas de tu inventario se funden en lingotes de su material.", 10, 1, 45, "her_fundicion");
        PoliciaHab.def("campana", "herrero", "Campana de bronce", "Tres campanadas: los enemigos a 8 bloques quedan débiles y lentos, y tu equipo y el de tus aliados se repara.", 12, 1, 45, "her_campana");
        PoliciaHab.def("tiempo", "herrero", "Forja del tiempo", "El objeto de tu mano vuelve a la durabilidad que tenía hace 30 s.", 16, 1, 60, "her_tiempo");
        PoliciaHab.def("rueda", "herrero", "Rueda de adaptación", "Una rueda de runas sobre tu cabeza: cada vez que te golpea el mismo tipo de daño, te hace 25 % menos (hasta anularlo). Dura 1 min.", 20, 2, 150, "her_rueda");
        PoliciaHab.def("placas", "herrero", "Escudo de mil placas", "Durante 30 s placas de hierro orbitan a tu alrededor: +8 de armadura y frenan flechas y proyectiles.", 22, 2, 90, "her_placas");
        PoliciaHab.def("espadas", "herrero", "Mil espadas", "Se abren portales dorados detrás tuyo y llueven espadas sobre los enemigos a 16 bloques; al final cae una espada gigante donde mirás.", 25, 2, 120, "her_espadas");
    }

    static void cargar() {
    }

    static boolean usar(ServerPlayer p, String id) {
        return switch (id) {
            case "senuelo" -> senuelo(p);
            case "trampa" -> trampa(p);
            case "bolsa" -> bolsa(p);
            case "tirolesa" -> tirolesa(p);
            case "paso" -> paso(p);
            case "lluvia" -> lluvia(p);
            case "horno" -> horno(p);
            case "armero" -> armero(p);
            case "golem" -> golem(p);
            case "fundicion" -> fundicion(p);
            case "campana" -> campana(p);
            case "tiempo" -> tiempo(p);
            case "rueda" -> rueda(p);
            case "placas" -> placas(p);
            case "espadas" -> espadas(p);
            default -> PoliciaHabOficios.usar(p, id);
        };
    }

    static boolean enemigo(ServerPlayer p, LivingEntity e) {
        return e.isAlive() && !PoliciaHab.aliado(p, e) && (e instanceof Enemy || (e instanceof Mob m && m.getTarget() == p));
    }

    static List<LivingEntity> enemigos(ServerPlayer p, Vec3 c, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> enemigo(p, e) && e.position().distanceTo(c) <= r);
    }

    static Vec3 punto(ServerPlayer p, double rango) {
        BlockHitResult h = PoliciaHab.bloque(p, rango);
        if (h != null) {
            return Vec3.atCenterOf(h.getBlockPos().relative(h.getDirection())).subtract(0, 0.5, 0);
        }
        return p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(8));
    }

    static void reparar(ItemStack st, float frac) {
        if (!st.isEmpty() && st.isDamageableItem() && st.getDamageValue() > 0) {
            st.setDamageValue(Math.max(0, st.getDamageValue() - Math.max(1, Math.round(st.getMaxDamage() * frac))));
        }
    }

    // ---------------- Ladron ----------------
    static boolean senuelo(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        ArmorStand a = new ArmorStand(sl, p.getX(), p.getY(), p.getZ());
        a.setYRot(p.getYRot());
        ItemStack cabeza = new ItemStack(Items.PLAYER_HEAD);
        cabeza.getOrCreateTag().putString("SkullOwner", p.getGameProfile().getName());
        a.setItemSlot(EquipmentSlot.HEAD, cabeza);
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            a.setItemSlot(s, p.getItemBySlot(s).copy());
        }
        a.setCustomName(p.getName());
        a.setCustomNameVisible(true);
        a.getPersistentData().putBoolean("pol_senuelo", true);
        sl.addFreshEntity(a);
        for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0), m -> m.getTarget() == p || m instanceof Enemy)) {
            m.setTarget(a);
        }
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, false, false));
        sl.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
        PoliciaHab.tarea(t -> {
            if (!a.isAlive()) {
                return true;
            }
            if (t % 20 == 0) {
                for (Mob m : sl.getEntitiesOfClass(Mob.class, a.getBoundingBox().inflate(16.0), m -> m.getTarget() == p)) {
                    m.setTarget(a);
                }
            }
            if (t >= 160) {
                sl.sendParticles(ParticleTypes.POOF, a.getX(), a.getY() + 1, a.getZ(), 40, 0.5, 0.8, 0.5, 0.08);
                sl.sendParticles(ParticleTypes.EXPLOSION, a.getX(), a.getY() + 1, a.getZ(), 1, 0, 0, 0, 0);
                sl.playSound(null, a.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.8f);
                for (LivingEntity e : enemigos(p, a.position(), 3.0)) {
                    e.knockback(0.8, a.getX() - e.getX(), a.getZ() - e.getZ());
                }
                a.discard();
                return true;
            }
            return false;
        });
        return true;
    }

    static boolean trampa(ServerPlayer p) {
        BlockHitResult h = PoliciaHab.bloque(p, 6.0);
        if (h == null) {
            PoliciaMod.msg(p, "Mirá el piso cerca tuyo para tender el hilo");
            return false;
        }
        ServerLevel sl = p.serverLevel();
        Vec3 c = Vec3.atCenterOf(h.getBlockPos().relative(h.getDirection())).subtract(0, 0.4, 0);
        UUID duenio = p.getUUID();
        sl.playSound(null, BlockPos.containing(c), SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 1.0f, 1.0f);
        PoliciaHab.tarea(t -> {
            if (t % 30 == 0) {
                for (int i = -2; i <= 2; i++) {
                    sl.sendParticles(new DustParticleOptions(new Vector3f(0.9f, 0.9f, 0.9f), 0.4f), c.x + i * 0.3, c.y + 0.1, c.z, 1, 0, 0, 0, 0);
                }
            }
            if (t % 5 == 0) {
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(1.3), e -> e.isAlive() && !e.getUUID().equals(duenio) && !(e instanceof ArmorStand))) {
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false));
                    sl.playSound(null, BlockPos.containing(c), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5f, 1.4f);
                    sl.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 1.5f), c.x, c.y + 0.3, c.z, 30, 0.8, 0.2, 0.8, 0);
                    ServerPlayer o = sl.getServer().getPlayerList().getPlayer(duenio);
                    if (o != null) {
                        o.displayClientMessage(Component.literal("§c¡Alarma! §f" + e.getName().getString() + " §7cruzó tu trampa en " + (int) c.x + ", " + (int) c.y + ", " + (int) c.z), false);
                        o.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 2.0f);
                    }
                    return true;
                }
            }
            return t >= 2400;
        });
        return true;
    }

    static boolean bolsa(ServerPlayer p) {
        PoliciaHab.datos(p).putLong("bolsa_hasta", p.level().getGameTime() + 6000);
        p.serverLevel().sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.6, 0.4, 0.5);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0f, 0.8f);
        PoliciaMod.msg(p, "Bolsa sin fondo: tu barra rápida está a salvo durante 5 minutos");
        return true;
    }

    static boolean tirolesa(ServerPlayer p) {
        BlockHitResult h = PoliciaHab.bloque(p, 40.0);
        if (h == null) {
            PoliciaMod.msg(p, "Mirá un punto a menos de 40 bloques para enganchar la cuerda");
            return false;
        }
        ServerLevel sl = p.serverLevel();
        Vec3 meta = Vec3.atCenterOf(h.getBlockPos().relative(h.getDirection()));
        Vec3 desde = p.position().add(0, 1.6, 0);
        sl.playSound(null, p.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0f, 0.6f);
        PoliciaHab.tarea(t -> {
            if (!p.isAlive() || p.hasDisconnected()) {
                return true;
            }
            p.resetFallDistance();
            Vec3 d = meta.subtract(p.position());
            if (t % 3 == 0) {
                for (int i = 0; i <= 10; i++) {
                    Vec3 q = desde.lerp(meta, i / 10.0);
                    sl.sendParticles(new DustParticleOptions(new Vector3f(0.55f, 0.4f, 0.25f), 0.7f), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
            if (d.length() < 1.6 || t > 100) {
                p.setDeltaMovement(p.getDeltaMovement().scale(0.2).add(0, 0.3, 0));
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
                return true;
            }
            double vel = Math.min(1.4, 0.5 + t * 0.04);
            Vec3 v = d.normalize().scale(vel);
            p.setDeltaMovement(v.x, v.y + 0.06, v.z);
            p.hurtMarked = true;
            return false;
        });
        return true;
    }

    static boolean paso(ServerPlayer p) {
        LivingEntity v = PoliciaHab.mirando(p, 16.0);
        if (v == null || PoliciaHab.aliado(p, v)) {
            PoliciaMod.msg(p, "Mirá a un enemigo a menos de 16 bloques");
            return false;
        }
        ServerLevel sl = p.serverLevel();
        Vec3 atras = v.getLookAngle().multiply(1, 0, 1);
        if (atras.lengthSqr() < 0.01) {
            atras = new Vec3(0, 0, 1);
        }
        Vec3 dst = v.position().subtract(atras.normalize().scale(1.3));
        BlockPos bp = BlockPos.containing(dst);
        if (sl.getBlockState(bp).isSolid() || sl.getBlockState(bp.above()).isSolid()) {
            PoliciaMod.msg(p, "No hay lugar detrás de " + v.getName().getString());
            return false;
        }
        sl.sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
        p.teleportTo(dst.x, dst.y, dst.z);
        Vec3 mira = v.position().subtract(dst);
        p.setYRot((float) (Math.toDegrees(Math.atan2(-mira.x, mira.z))));
        p.setYHeadRot(p.getYRot());
        p.connection.teleport(dst.x, dst.y, dst.z, p.getYRot(), p.getXRot());
        sl.sendParticles(ParticleTypes.CHERRY_LEAVES, dst.x, dst.y + 1, dst.z, 30, 0.5, 0.8, 0.5, 0.05);
        sl.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.6f);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1, false, false));
        return true;
    }

    // ---------------- Herrero ----------------
    static boolean lluvia(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = punto(p, 24.0);
        PoliciaHab.tarea(t -> {
            if (t % 3 == 0) {
                double a = p.getRandom().nextDouble() * Math.PI * 2;
                double r = Math.sqrt(p.getRandom().nextDouble()) * 4.0;
                Vec3 q = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                for (int k = 0; k < 6; k++) {
                    sl.sendParticles(ParticleTypes.FLAME, q.x, q.y + 6 - k, q.z, 1, 0.02, 0.1, 0.02, 0.0);
                }
                sl.sendParticles(ParticleTypes.LAVA, q.x, q.y + 0.2, q.z, 3, 0.2, 0.05, 0.2, 0.0);
                sl.sendParticles(ORO, q.x, q.y + 0.3, q.z, 4, 0.2, 0.1, 0.2, 0.0);
                sl.playSound(null, BlockPos.containing(q), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 1.6f + p.getRandom().nextFloat() * 0.3f);
                for (LivingEntity e : enemigos(p, q, 1.6)) {
                    e.hurt(p.damageSources().playerAttack(p), 4.0f);
                    e.setSecondsOnFire(2);
                }
            }
            return t >= 60;
        });
        return true;
    }

    static boolean horno(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        int hechos = 0;
        for (int i = 0; i < p.getInventory().items.size() && hechos < 64; i++) {
            ItemStack st = p.getInventory().items.get(i);
            if (st.isEmpty()) {
                continue;
            }
            String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
            if (!(path.startsWith("raw_") || path.endsWith("_ore") || path.equals("ancient_debris"))) {
                continue;
            }
            Optional<SmeltingRecipe> r = sl.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(st.copyWithCount(1)), sl);
            if (r.isEmpty()) {
                continue;
            }
            ItemStack res = r.get().getResultItem(sl.registryAccess());
            if (res.isEmpty()) {
                continue;
            }
            int n = Math.min(st.getCount(), 64 - hechos);
            st.shrink(n);
            hechos += n;
            ItemStack sale = res.copyWithCount(res.getCount() * n);
            if (!p.getInventory().add(sale)) {
                p.drop(sale, false);
            }
        }
        if (hechos == 0) {
            PoliciaMod.msg(p, "No tenés minerales crudos para fundir");
            return false;
        }
        sl.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.8, p.getZ(), 30, 0.5, 0.4, 0.5, 0.02);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.4, p.getZ(), 10, 0.3, 0.3, 0.3, 0.01);
        sl.playSound(null, p.blockPosition(), SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 1.5f, 1.0f);
        PoliciaMod.msg(p, "Horno portátil: fundiste " + hechos + (hechos == 1 ? " mineral" : " minerales"));
        return true;
    }

    static boolean armero(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        PoliciaHab.tarea(t -> {
            if (!p.isAlive() || p.hasDisconnected()) {
                return true;
            }
            if (t % 20 == 0) {
                for (ItemStack a : p.getArmorSlots()) {
                    reparar(a, 0.03f);
                }
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
            }
            if (t % 40 == 0) {
                sl.playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.25f, 2.0f);
            }
            return t >= 600;
        });
        return true;
    }

    static boolean golem(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        IronGolem g = EntityType.IRON_GOLEM.create(sl);
        if (g == null) {
            return false;
        }
        Vec3 c = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.0));
        g.moveTo(c.x, p.getY(), c.z, p.getYRot(), 0);
        g.setPlayerCreated(true);
        g.getPersistentData().putBoolean("pol_golem", true);
        sl.addFreshEntity(g);
        sl.sendParticles(ParticleTypes.LAVA, c.x, p.getY() + 1, c.z, 20, 0.6, 1.0, 0.6, 0.0);
        sl.playSound(null, g.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.2f, 0.8f);
        PoliciaHab.tarea(t -> {
            if (!g.isAlive()) {
                return true;
            }
            if (t % 20 == 0) {
                List<LivingEntity> l = enemigos(p, g.position(), 12.0);
                if (!l.isEmpty() && (g.getTarget() == null || !g.getTarget().isAlive())) {
                    g.setTarget(l.get(0));
                }
            }
            if (t >= 1200) {
                sl.sendParticles(ParticleTypes.POOF, g.getX(), g.getY() + 1.2, g.getZ(), 30, 0.6, 1.0, 0.6, 0.05);
                g.discard();
                return true;
            }
            return false;
        });
        return true;
    }

    static Item lingote(ItemStack st) {
        Item it = st.getItem();
        if (it instanceof TieredItem ti) {
            if (ti.getTier() == Tiers.IRON) {
                return Items.IRON_INGOT;
            }
            if (ti.getTier() == Tiers.GOLD) {
                return Items.GOLD_INGOT;
            }
            if (ti.getTier() == Tiers.DIAMOND) {
                return Items.DIAMOND;
            }
            if (ti.getTier() == Tiers.NETHERITE) {
                return Items.NETHERITE_SCRAP;
            }
        }
        if (it instanceof ArmorItem ai) {
            if (ai.getMaterial() == ArmorMaterials.IRON) {
                return Items.IRON_INGOT;
            }
            if (ai.getMaterial() == ArmorMaterials.CHAIN) {
                return Items.IRON_NUGGET;
            }
            if (ai.getMaterial() == ArmorMaterials.GOLD) {
                return Items.GOLD_INGOT;
            }
            if (ai.getMaterial() == ArmorMaterials.DIAMOND) {
                return Items.DIAMOND;
            }
            if (ai.getMaterial() == ArmorMaterials.NETHERITE) {
                return Items.NETHERITE_SCRAP;
            }
        }
        return null;
    }

    static boolean fundicion(ServerPlayer p) {
        int fundidos = 0;
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            if (i == p.getInventory().selected) {
                continue;
            }
            ItemStack st = p.getInventory().items.get(i);
            if (st.isEmpty() || !st.isDamageableItem() || st.getDamageValue() < st.getMaxDamage() / 2) {
                continue;
            }
            Item sale = lingote(st);
            if (sale == null) {
                continue;
            }
            float queda = 1.0f - st.getDamageValue() / (float) st.getMaxDamage();
            int n = sale == Items.DIAMOND || sale == Items.NETHERITE_SCRAP ? 1 : Math.max(1, Math.round(1 + queda * 4));
            if (sale == Items.IRON_NUGGET) {
                n *= 4;
            }
            p.getInventory().items.set(i, ItemStack.EMPTY);
            ItemStack r = new ItemStack(sale, n);
            if (!p.getInventory().add(r)) {
                p.drop(r, false);
            }
            fundidos++;
        }
        if (fundidos == 0) {
            PoliciaMod.msg(p, "No tenés equipo gastado (a más de la mitad) para fundir");
            return false;
        }
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 0.6, p.getZ(), 12, 0.4, 0.2, 0.4, 0.0);
        sl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.2, p.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
        sl.playSound(null, p.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.9f);
        PoliciaMod.msg(p, "Fundición de emergencia: " + fundidos + (fundidos == 1 ? " objeto fundido" : " objetos fundidos"));
        return true;
    }

    static boolean campana(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        PoliciaHab.tarea(t -> {
            if (t == 0 || t == 15 || t == 30) {
                sl.playSound(null, BlockPos.containing(c), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0f, 0.6f + t * 0.01f);
                for (int i = 0; i < 40; i++) {
                    double a = i * Math.PI * 2 / 40;
                    sl.sendParticles(ORO, c.x + Math.cos(a) * (2 + t / 5.0), c.y + 0.5, c.z + Math.sin(a) * (2 + t / 5.0), 1, 0, 0, 0, 0);
                }
                for (LivingEntity e : enemigos(p, c, 8.0)) {
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1, false, true));
                    e.knockback(0.4, c.x - e.getX(), c.z - e.getZ());
                }
                for (ServerPlayer o : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(8.0))) {
                    for (ItemStack a : o.getArmorSlots()) {
                        reparar(a, 0.05f);
                    }
                    reparar(o.getMainHandItem(), 0.05f);
                }
            }
            return t >= 30;
        });
        return true;
    }

    static boolean tiempo(ServerPlayer p) {
        ItemStack st = p.getMainHandItem();
        if (st.isEmpty() || !st.isDamageableItem()) {
            PoliciaMod.msg(p, "Tené en la mano un objeto con durabilidad");
            return false;
        }
        ArrayDeque<Object[]> h = HISTORIAL.get(p.getUUID());
        long ahora = p.level().getGameTime();
        Integer mejor = null;
        if (h != null) {
            for (Object[] s : h) {
                if (s[1] == st.getItem() && (long) s[0] <= ahora - 580) {
                    mejor = (Integer) s[2];
                }
            }
            if (mejor == null) {
                for (Object[] s : h) {
                    if (s[1] == st.getItem()) {
                        mejor = (Integer) s[2];
                        break;
                    }
                }
            }
        }
        if (mejor == null || mejor >= st.getDamageValue()) {
            PoliciaMod.msg(p, "No hay desgaste para rebobinar en los últimos 30 s");
            return false;
        }
        st.setDamageValue(mejor);
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.2, p.getZ(), 60, 0.4, 0.5, 0.4, 0.05);
        sl.playSound(null, p.blockPosition(), PoliciaSonido.anim("warp").get(), SoundSource.PLAYERS, 1.0f, 0.6f);
        PoliciaMod.msg(p, "Forja del tiempo: " + st.getHoverName().getString() + " volvió a como estaba hace 30 s");
        return true;
    }

    static boolean rueda(ServerPlayer p) {
        RUEDA.put(p.getUUID(), new Object[]{p.level().getGameTime() + 1200, new HashMap<String, Integer>()});
        ServerLevel sl = p.serverLevel();
        PoliciaHab.tarea(t -> {
            Object[] r = RUEDA.get(p.getUUID());
            if (r == null || !p.isAlive() || p.hasDisconnected()) {
                return true;
            }
            if (t % 6 == 0) {
                double giro = t * 0.05;
                for (int i = 0; i < 8; i++) {
                    double a = giro + i * Math.PI / 4;
                    sl.sendParticles(ORO, p.getX() + Math.cos(a) * 0.6, p.getY() + 2.5 + Math.sin(a) * 0.6, p.getZ(), 1, 0, 0, 0, 0);
                }
            }
            if (p.level().getGameTime() >= (long) r[0]) {
                RUEDA.remove(p.getUUID());
                return true;
            }
            return false;
        });
        return true;
    }

    static boolean placas(ServerPlayer p) {
        PLACAS.put(p.getUUID(), p.level().getGameTime() + 600);
        AttributeInstance arm = p.getAttribute(Attributes.ARMOR);
        if (arm != null) {
            arm.removeModifier(MOD_PLACAS);
            arm.addTransientModifier(new AttributeModifier(MOD_PLACAS, "mil placas", 8.0, AttributeModifier.Operation.ADDITION));
        }
        ServerLevel sl = p.serverLevel();
        PoliciaHab.tarea(t -> {
            if (t % 4 == 0 && p.isAlive()) {
                for (int i = 0; i < 10; i++) {
                    double a = t * 0.08 + i * Math.PI / 5;
                    double y = 0.3 + (i % 3) * 0.7;
                    sl.sendParticles(HIERRO, p.getX() + Math.cos(a) * 1.3, p.getY() + y, p.getZ() + Math.sin(a) * 1.3, 1, 0, 0, 0, 0);
                }
            }
            if (t >= 600 || !p.isAlive() || p.hasDisconnected()) {
                PLACAS.remove(p.getUUID());
                AttributeInstance a2 = p.getAttribute(Attributes.ARMOR);
                if (a2 != null) {
                    a2.removeModifier(MOD_PLACAS);
                }
                sl.sendParticles(HIERRO, p.getX(), p.getY() + 1, p.getZ(), 40, 0.8, 0.8, 0.8, 0.2);
                return true;
            }
            return false;
        });
        return true;
    }

    static boolean espadas(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 fin = punto(p, 24.0);
        PoliciaHab.tarea(t -> {
            if (!p.isAlive() || p.hasDisconnected()) {
                return true;
            }
            Vec3 atras = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
            if (t < 20 && t % 2 == 0) {
                // muralla de portales dorados detras
                for (int i = -4; i <= 4; i++) {
                    Vec3 lado = p.getLookAngle().multiply(1, 0, 1).normalize().yRot((float) Math.PI / 2);
                    Vec3 q = atras.add(lado.scale(i * 0.8)).add(0, 1.6 + Math.abs(i) * 0.25 + Math.sin(t * 0.4 + i) * 0.2, 0);
                    sl.sendParticles(ORO, q.x, q.y, q.z, 2, 0.15, 0.15, 0.15, 0);
                    sl.sendParticles(ParticleTypes.ENCHANT, q.x, q.y, q.z, 2, 0.2, 0.2, 0.2, 0.3);
                }
            }
            if (t >= 20 && t < 68 && t % 2 == 0) {
                List<LivingEntity> l = enemigos(p, p.position(), 16.0);
                if (!l.isEmpty()) {
                    LivingEntity e = l.get(p.getRandom().nextInt(l.size()));
                    Vec3 o = atras.add(0, 2.2, 0);
                    Vec3 d = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(o);
                    for (int k = 0; k <= 6; k++) {
                        Vec3 q = o.add(d.scale(k / 6.0));
                        sl.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    }
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 1, 0, 0, 0, 0);
                    e.invulnerableTime = 0;
                    e.hurt(p.damageSources().playerAttack(p), 4.0f);
                    sl.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.4f);
                }
            }
            if (t == 72) {
                for (int k = 0; k < 14; k++) {
                    sl.sendParticles(ORO, fin.x, fin.y + 14 - k, fin.z, 6, 0.15, 0.3, 0.15, 0);
                }
            }
            if (t == 80) {
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, fin.x, fin.y + 0.5, fin.z, 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.END_ROD, fin.x, fin.y + 1, fin.z, 80, 1.5, 1.5, 1.5, 0.3);
                sl.playSound(null, BlockPos.containing(fin), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.2f);
                sl.playSound(null, BlockPos.containing(fin), PoliciaSonido.anim("chime").get(), SoundSource.PLAYERS, 1.5f, 0.8f);
                for (LivingEntity e : enemigos(p, fin, 4.5)) {
                    e.invulnerableTime = 0;
                    e.hurt(p.damageSources().playerAttack(p), 14.0f);
                    e.knockback(1.2, fin.x - e.getX(), fin.z - e.getZ());
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------------- Eventos ----------------
    public static class EventosHabExtra {
        // Rueda de adaptacion: cada golpe del mismo tipo de dano pega menos.
        @SubscribeEvent(priority = EventPriority.LOW)
        public void dano(LivingHurtEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            Object[] r = RUEDA.get(p.getUUID());
            if (r == null || p.level().getGameTime() >= (long) r[0]) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Integer> m = (Map<String, Integer>) r[1];
            String tipo = e.getSource().getMsgId();
            int n = m.getOrDefault(tipo, 0);
            if (n > 0) {
                e.setAmount(e.getAmount() * Math.max(0.0f, 1.0f - 0.25f * n));
            }
            m.put(tipo, Math.min(4, n + 1));
            ServerLevel sl = p.serverLevel();
            sl.playSound(null, p.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 1.0f, 0.7f + n * 0.15f);
            sl.sendParticles(ORO, p.getX(), p.getY() + 2.5, p.getZ(), 16, 0.5, 0.5, 0.1, 0);
            p.displayClientMessage(Component.literal("§6Rueda: adaptado a «" + tipo + "» §e-" + Math.min(100, (n + 1) * 25) + " %"), true);
        }

        // Escudo de mil placas: frena los proyectiles que te iban a pegar.
        @SubscribeEvent
        public void proyectil(ProjectileImpactEvent e) {
            if (!(e.getRayTraceResult() instanceof EntityHitResult eh) || !(eh.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            Long hasta = PLACAS.get(p.getUUID());
            if (hasta == null || p.level().getGameTime() >= hasta) {
                return;
            }
            Projectile pr = e.getProjectile();
            e.setCanceled(true);
            p.serverLevel().sendParticles(HIERRO, pr.getX(), pr.getY(), pr.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
            p.serverLevel().playSound(null, pr.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.3f);
            pr.discard();
        }

        // Bolsa sin fondo: guarda la barra rapida al morir.
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void morir(LivingDeathEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            RUEDA.remove(p.getUUID());
            CompoundTag h = PoliciaHab.datos(p);
            if (h.getLong("bolsa_hasta") <= p.level().getGameTime() || p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                return;
            }
            ListTag l = new ListTag();
            for (int i = 0; i < 9; i++) {
                ItemStack st = p.getInventory().items.get(i);
                if (!st.isEmpty()) {
                    CompoundTag t = st.save(new CompoundTag());
                    t.putByte("Slot", (byte) i);
                    l.add(t);
                    p.getInventory().items.set(i, ItemStack.EMPTY);
                }
            }
            h.put("bolsa_items", l);
            h.putLong("bolsa_hasta", 0);
        }

        @SubscribeEvent
        public void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            CompoundTag h = PoliciaHab.datos(p);
            if (!h.contains("bolsa_items")) {
                return;
            }
            ListTag l = h.getList("bolsa_items", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag t = l.getCompound(i);
                ItemStack st = ItemStack.of(t);
                int slot = t.getByte("Slot");
                if (slot >= 0 && slot < 9 && p.getInventory().items.get(slot).isEmpty()) {
                    p.getInventory().items.set(slot, st);
                } else if (!p.getInventory().add(st)) {
                    p.drop(st, false);
                }
            }
            h.remove("bolsa_items");
            PoliciaMod.msg(p, "La bolsa sin fondo te devolvió tu barra rápida");
        }

        // Forja del tiempo: guarda cada segundo el desgaste de la mano principal.
        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 3) {
                return;
            }
            if (!"herrero".equals(PoliciaMod.job(p))) {
                return;
            }
            ItemStack st = p.getMainHandItem();
            if (st.isEmpty() || !st.isDamageableItem()) {
                return;
            }
            ArrayDeque<Object[]> h = HISTORIAL.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<Object[]>());
            h.addLast(new Object[]{p.level().getGameTime(), st.getItem(), st.getDamageValue()});
            while (h.size() > 40) {
                h.removeFirst();
            }
        }

        @SubscribeEvent
        public void salir(PlayerEvent.PlayerLoggedOutEvent e) {
            HISTORIAL.remove(e.getEntity().getUUID());
            RUEDA.remove(e.getEntity().getUUID());
        }
    }
}
