package com.example.policia;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.joml.Vector3f;

// Etapa 6c: los oficios nuevos Medico y Mercader, con 8 habilidades cada
// uno mas las de inspiracion anime aprobadas (Babosa curandera, Pildora
// monstruo, Puerta del tesoro, Cartas de mercancia, Tienda del otro mundo).
// Diseno, nombres y efectos propios. Se usan con Z (no tienen modo B).
public class PoliciaHabOficios {
    static final DustParticleOptions VERDE = new DustParticleOptions(new Vector3f(0.3f, 0.95f, 0.45f), 1.2f);
    static final DustParticleOptions ORO = new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.2f), 1.3f);

    static {
        // Medico
        PoliciaHab.def("vendaje", "medico", "Vendaje rápido", "Vendás al aliado que mirás (o a vos): +6 de vida y regeneración.", 1, 1, 12, "med_vendaje");
        PoliciaHab.def("antidoto", "medico", "Antídoto", "Le sacás al aliado que mirás (o a vos) veneno, debilidad, lentitud, ceguera y náuseas.", 3, 1, 20, "med_antidoto");
        PoliciaHab.def("nube", "medico", "Nube curativa", "Lanzás una poción que deja una nube de regeneración donde mirás durante 10 s.", 5, 1, 30, "med_nube");
        PoliciaHab.def("mortero", "medico", "Mortero de hierbas", "Molés 2 hierbas (flores, hojas, semillas o algas) y sale una poción arrojadiza de curación.", 6, 1, 45, "med_mortero");
        PoliciaHab.def("burbuja", "medico", "Burbuja vital", "Encerrás al aliado que mirás (o a vos) en una burbuja que absorbe 16 de daño durante 15 s.", 10, 1, 45, "med_burbuja");
        PoliciaHab.def("reanimar", "medico", "Reanimar", "Al jugador más débil a 6 bloques (con menos de la mitad de vida) le devolvés toda la vida y le das un escudo.", 12, 2, 180, "med_reanimar");
        PoliciaHab.def("hospital", "medico", "Hospital de campaña", "Armás una carpa: durante 45 s cura a los aliados a 5 bloques.", 15, 2, 120, "med_hospital");
        PoliciaHab.def("lazo", "medico", "Lazo vital", "Unís tu vida a la de un aliado durante 20 s: le pasás vida en pulsos.", 18, 1, 40, "med_lazo");
        PoliciaHab.def("babosa", "medico", "Babosa curandera", "Una babosa verde se divide en babositas que se pegan a los aliados a 8 bloques y los curan durante 30 s.", 20, 2, 90, "med_babosa");
        PoliciaHab.def("pildora", "medico", "Píldora monstruo", "Te tomás la píldora: 20 s de fuerza, velocidad, salto y resistencia... y después 10 s de bajón.", 25, 2, 180, "med_pildora");
        // Mercader
        PoliciaHab.def("puesto", "mercader", "Puesto instantáneo", "Desplegás un puesto con cartel ¡ABIERTO! durante 60 s: los aldeanos cercanos se acercan y te dejan esmeraldas.", 1, 1, 90, "mer_puesto");
        PoliciaHab.def("balanza", "mercader", "Balanza tasadora", "Pesás el objeto de tu mano y te dice su precio justo en esmeraldas.", 2, 1, 5, "mer_balanza");
        PoliciaHab.def("regateo", "mercader", "Regateo", "Regateás con el aldeano que mirás: te queda una buena impresión y te baja los precios.", 3, 1, 60, "mer_regateo");
        PoliciaHab.def("mochila", "mercader", "Mochila sin fondo", "Abrís tu depósito personal de 27 espacios, desde cualquier lugar.", 6, 1, 3, "mer_mochila");
        PoliciaHab.def("caravana", "mercader", "Caravana", "Llega tu caravana y cobrás la ganancia del viaje (esmeraldas según tu nivel y alguna mercadería).", 8, 1, 600, "mer_caravana");
        PoliciaHab.def("monedas", "mercader", "Lluvia de monedas", "Tirás 1 esmeralda al cofre de la suerte: revienta en una lluvia de monedas (puede salir más o menos).", 10, 1, 60, "mer_monedas");
        PoliciaHab.def("contrato", "mercader", "Contrato sellado", "Firmás un trato con el jugador que mirás: los dos tienen suerte y descuentos en la aldea durante 5 min.", 12, 1, 120, "mer_contrato");
        PoliciaHab.def("propina", "mercader", "Propina dorada", "Pagás 1 esmeralda y los que te persiguen a 12 bloques se olvidan de vos un rato.", 14, 1, 60, "mer_propina");
        PoliciaHab.def("cartas", "mercader", "Cartas de mercancía", "Tirás 3 cartas al azar: curan, queman al enemigo que mirás o te dan mercadería.", 16, 1, 45, "mer_cartas");
        PoliciaHab.def("tesoro", "mercader", "Puerta del tesoro", "Se abre una puerta dorada sobre vos y dispara tesoros a los enemigos a 14 bloques en tres oleadas.", 20, 2, 120, "mer_tesoro");
        PoliciaHab.def("tienda", "mercader", "Tienda del otro mundo", "Aparece un comerciante de otro mundo con ofertas raras durante 1 minuto.", 25, 2, 300, "mer_tienda");
    }

    static void cargar() {
    }

    static boolean usar(ServerPlayer p, String id) {
        return switch (id) {
            case "vendaje" -> vendaje(p);
            case "antidoto" -> antidoto(p);
            case "nube" -> nube(p);
            case "mortero" -> mortero(p);
            case "burbuja" -> burbuja(p);
            case "reanimar" -> reanimar(p);
            case "hospital" -> hospital(p);
            case "lazo" -> lazo(p);
            case "babosa" -> PoliciaAnime.babosa(p);
            case "pildora" -> PoliciaAnime.pildora(p);
            case "puesto" -> puesto(p);
            case "balanza" -> balanza(p);
            case "regateo" -> regateo(p);
            case "mochila" -> mochila(p);
            case "caravana" -> caravana(p);
            case "monedas" -> monedas(p);
            case "contrato" -> contrato(p);
            case "propina" -> propina(p);
            case "cartas" -> PoliciaAnime.cartas(p);
            case "tesoro" -> PoliciaAnime.tesoro(p);
            case "tienda" -> PoliciaAnime.tienda(p);
            default -> false;
        };
    }

    // Aliado que se mira (jugador, mascota o aldeano) o uno mismo.
    static LivingEntity aliado(ServerPlayer p) {
        LivingEntity e = PoliciaHab.mirando(p, 10.0);
        if (e != null && (e instanceof Player || e instanceof AbstractVillager || PoliciaHab.aliado(p, e))) {
            return e;
        }
        return p;
    }

    static void curar(ServerLevel sl, LivingEntity e, float hp) {
        e.heal(hp);
        sl.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
    }

    static boolean pagar(ServerPlayer p, int n) {
        if (p.isCreative()) {
            return true;
        }
        int hay = 0;
        for (ItemStack st : p.getInventory().items) {
            if (st.is(Items.EMERALD)) {
                hay += st.getCount();
            }
        }
        if (hay < n) {
            PoliciaMod.msg(p, "Necesitás " + n + (n == 1 ? " esmeralda" : " esmeraldas"));
            return false;
        }
        for (ItemStack st : p.getInventory().items) {
            if (n > 0 && st.is(Items.EMERALD)) {
                int q = Math.min(n, st.getCount());
                st.shrink(q);
                n -= q;
            }
        }
        return true;
    }

    static void dar(ServerPlayer p, ItemStack st) {
        if (!p.getInventory().add(st)) {
            p.drop(st, false);
        }
    }

    // ---------------- Medico ----------------
    static boolean vendaje(ServerPlayer p) {
        LivingEntity e = aliado(p);
        if (e.getHealth() >= e.getMaxHealth()) {
            PoliciaMod.msg(p, (e == p ? "Tenés" : e.getName().getString() + " tiene") + " la vida llena");
            return false;
        }
        ServerLevel sl = p.serverLevel();
        curar(sl, e, 6.0f);
        e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0));
        sl.sendParticles(new DustParticleOptions(new Vector3f(1, 1, 1), 1.0f), e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.3, 0.3, 0);
        sl.playSound(null, e.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    static boolean antidoto(ServerPlayer p) {
        LivingEntity e = aliado(p);
        MobEffect[] malos = {MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.BLINDNESS, MobEffects.CONFUSION, MobEffects.HUNGER, MobEffects.DIG_SLOWDOWN, MobEffects.DARKNESS};
        int n = 0;
        for (MobEffect m : malos) {
            if (e.removeEffect(m)) {
                n++;
            }
        }
        if (n == 0) {
            PoliciaMod.msg(p, "No hay nada que curar con el antídoto");
            return false;
        }
        ServerLevel sl = p.serverLevel();
        curar(sl, e, 2.0f);
        sl.sendParticles(ParticleTypes.BUBBLE_POP, e.getX(), e.getY() + 1, e.getZ(), 30, 0.4, 0.5, 0.4, 0.05);
        sl.playSound(null, e.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    static boolean nube(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = PoliciaHabExtra.punto(p, 16.0);
        net.minecraft.world.entity.AreaEffectCloud nube = new net.minecraft.world.entity.AreaEffectCloud(sl, c.x, c.y, c.z);
        nube.setOwner(p);
        nube.setRadius(3.0f);
        nube.setDuration(200);
        nube.setRadiusPerTick(0.0f);
        nube.setWaitTime(0);
        nube.setFixedColor(0xFF7FB0);
        nube.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        sl.addFreshEntity(nube);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    static boolean mortero(ServerPlayer p) {
        int falta = 2;
        for (ItemStack st : p.getInventory().items) {
            if (falta > 0 && !st.isEmpty() && (st.is(ItemTags.FLOWERS) || st.is(ItemTags.LEAVES) || st.is(ItemTags.SAPLINGS) || st.is(Tags.Items.SEEDS) || st.is(Items.KELP))) {
                falta -= Math.min(falta, st.getCount());
            }
        }
        if (falta > 0 && !p.isCreative()) {
            PoliciaMod.msg(p, "Necesitás 2 hierbas: flores, hojas, brotes, semillas o algas");
            return false;
        }
        falta = 2;
        for (ItemStack st : p.getInventory().items) {
            if (falta > 0 && !st.isEmpty() && (st.is(ItemTags.FLOWERS) || st.is(ItemTags.LEAVES) || st.is(ItemTags.SAPLINGS) || st.is(Tags.Items.SEEDS) || st.is(Items.KELP))) {
                int q = Math.min(falta, st.getCount());
                st.shrink(q);
                falta -= q;
            }
        }
        dar(p, PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.STRONG_HEALING));
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(VERDE, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.3, 0.2, 0.3, 0);
        sl.playSound(null, p.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    static boolean burbuja(ServerPlayer p) {
        LivingEntity e = aliado(p);
        e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 3));
        e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0));
        ServerLevel sl = p.serverLevel();
        PoliciaHab.tarea(t -> {
            if (t % 5 == 0 && e.isAlive()) {
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8 + t * 0.05;
                    double b = (i % 4) * Math.PI / 8;
                    sl.sendParticles(VERDE, e.getX() + Math.cos(a) * Math.cos(b) * 1.1, e.getY() + 1 + Math.sin(b) * 1.1, e.getZ() + Math.sin(a) * Math.cos(b) * 1.1, 1, 0, 0, 0, 0);
                }
            }
            return t >= 300 || !e.isAlive() || e.getAbsorptionAmount() <= 0.01f && t > 5;
        });
        sl.playSound(null, e.blockPosition(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.5f, 1.0f);
        return true;
    }

    static boolean reanimar(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        ServerPlayer mejor = null;
        for (ServerPlayer o : sl.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(6.0), o -> o != p && o.isAlive())) {
            if (o.getHealth() < o.getMaxHealth() / 2 && (mejor == null || o.getHealth() < mejor.getHealth())) {
                mejor = o;
            }
        }
        if (mejor == null) {
            PoliciaMod.msg(p, "No hay ningún jugador muy herido a 6 bloques");
            return false;
        }
        ServerPlayer o = mejor;
        PoliciaHab.tarea(t -> {
            if (t == 0 || t == 8 || t == 16) {
                sl.playSound(null, o.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
            }
            if (t == 24) {
                sl.playSound(null, o.blockPosition(), PoliciaSonido.anim("zap").get(), SoundSource.PLAYERS, 1.2f, 1.0f);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, o.getX(), o.getY() + 1, o.getZ(), 30, 0.3, 0.3, 0.3, 0.2);
            }
            if (t == 30) {
                o.setHealth(o.getMaxHealth());
                o.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
                curar(sl, o, 0.0f);
                sl.playSound(null, o.blockPosition(), PoliciaSonido.anim("beep").get(), SoundSource.PLAYERS, 1.0f, 1.3f);
                o.displayClientMessage(Component.literal("§a" + p.getName().getString() + " te reanimó"), true);
                return true;
            }
            return false;
        });
        return true;
    }

    static boolean hospital(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        ArmorStand cartel = new ArmorStand(sl, c.x, c.y + 0.8, c.z);
        cartel.setInvisible(true);
        cartel.setNoGravity(true);
        cartel.setInvulnerable(true);
        cartel.setCustomName(Component.literal("§c✚ §fHospital de campaña"));
        cartel.setCustomNameVisible(true);
        sl.addFreshEntity(cartel);
        PoliciaHab.tarea(t -> {
            if (t % 10 == 0) {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12 + t * 0.02;
                    sl.sendParticles(VERDE, c.x + Math.cos(a) * 5, c.y + 0.1, c.z + Math.sin(a) * 5, 1, 0, 0, 0, 0);
                }
            }
            if (t % 20 == 0) {
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(5.0), e -> e.isAlive() && (e instanceof Player || e instanceof AbstractVillager || PoliciaHab.aliado(p, e)))) {
                    if (e.getHealth() < e.getMaxHealth()) {
                        curar(sl, e, 2.0f);
                    }
                }
            }
            if (t >= 900) {
                cartel.discard();
                return true;
            }
            return false;
        });
        return true;
    }

    static boolean lazo(ServerPlayer p) {
        LivingEntity e = aliado(p);
        if (e == p) {
            ServerPlayer cerca = null;
            for (ServerPlayer o : p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(10.0), o -> o != p && o.isAlive())) {
                if (cerca == null || o.distanceTo(p) < cerca.distanceTo(p)) {
                    cerca = o;
                }
            }
            if (cerca == null) {
                PoliciaMod.msg(p, "Mirá a un aliado (o tené un jugador a 10 bloques) para el lazo");
                return false;
            }
            e = cerca;
        }
        LivingEntity o = e;
        ServerLevel sl = p.serverLevel();
        PoliciaHab.tarea(t -> {
            if (!o.isAlive() || !p.isAlive() || p.hasDisconnected() || o.distanceTo(p) > 16) {
                return true;
            }
            if (t % 4 == 0) {
                Vec3 a = p.position().add(0, 1.2, 0);
                Vec3 b = o.position().add(0, 1.2, 0);
                for (int i = 0; i <= 8; i++) {
                    Vec3 q = a.lerp(b, i / 8.0);
                    sl.sendParticles(VERDE, q.x, q.y + Math.sin(t * 0.3 + i) * 0.1, q.z, 1, 0, 0, 0, 0);
                }
            }
            if (t % 10 == 0 && o.getHealth() < o.getMaxHealth() && p.getHealth() > 6.0f) {
                p.setHealth(p.getHealth() - 1.0f);
                curar(sl, o, 2.0f);
            }
            return t >= 400;
        });
        return true;
    }

    static boolean babosa(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        sl.playSound(null, p.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.5f, 0.8f);
        PoliciaHab.tarea(t -> {
            if (!p.isAlive() || p.hasDisconnected()) {
                return true;
            }
            if (t % 20 == 0) {
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8.0), e -> e.isAlive() && (e instanceof Player || PoliciaHab.aliado(p, e)))) {
                    sl.sendParticles(ParticleTypes.ITEM_SLIME, e.getX(), e.getY() + 0.8, e.getZ(), 8, 0.3, 0.4, 0.3, 0.05);
                    if (e.getHealth() < e.getMaxHealth()) {
                        e.heal(1.0f);
                    }
                }
                sl.playSound(null, p.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.5f, 1.2f);
            }
            return t >= 600;
        });
        return true;
    }

    static boolean pildora(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, 400, 1));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0));
        ServerLevel sl = p.serverLevel();
        sl.playSound(null, p.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 0.8f);
        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 2, p.getZ(), 5, 0.3, 0.2, 0.3, 0);
        PoliciaHab.tarea(t -> {
            if (t == 400 && p.isAlive()) {
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
                p.displayClientMessage(Component.literal("§7La píldora se te pasó... bajón"), true);
                return true;
            }
            return t > 400;
        });
        return true;
    }

    // ---------------- Mercader ----------------
    static boolean puesto(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
        ArmorStand cartel = new ArmorStand(sl, c.x, c.y + 0.6, c.z);
        cartel.setInvisible(true);
        cartel.setNoGravity(true);
        cartel.setInvulnerable(true);
        cartel.setCustomName(Component.literal("§a§l¡ABIERTO!"));
        cartel.setCustomNameVisible(true);
        sl.addFreshEntity(cartel);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        PoliciaHab.tarea(t -> {
            if (t % 40 == 0) {
                for (Villager v : sl.getEntitiesOfClass(Villager.class, new AABB(c, c).inflate(16.0), v -> v.isAlive() && !v.isBaby())) {
                    v.getNavigation().moveTo(c.x, c.y, c.z, 0.6);
                }
            }
            if (t % 200 == 100 && p.isAlive()) {
                int n = sl.getEntitiesOfClass(Villager.class, new AABB(c, c).inflate(5.0), v -> v.isAlive() && !v.isBaby()).size();
                if (n > 0) {
                    dar(p, new ItemStack(Items.EMERALD, Math.min(4, n)));
                    sl.playSound(null, BlockPos.containing(c), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 1.0f);
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1, c.z, 10, 0.6, 0.4, 0.6, 0);
                    p.displayClientMessage(Component.literal("§aVendiste: +" + Math.min(4, n) + " esmeraldas"), true);
                }
            }
            if (t >= 1200) {
                cartel.discard();
                return true;
            }
            return false;
        });
        return true;
    }

    static int precio(ItemStack st) {
        double base = switch (st.getRarity()) {
            case EPIC -> 40;
            case RARE -> 16;
            case UNCOMMON -> 6;
            default -> 1;
        };
        String n = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        if (n.contains("netherite")) {
            base += 30;
        } else if (n.contains("diamond")) {
            base += 12;
        } else if (n.contains("gold")) {
            base += 3;
        } else if (n.contains("iron")) {
            base += 2;
        }
        base += EnchantmentHelper.getEnchantments(st).values().stream().mapToInt(Integer::intValue).sum() * 3;
        if (st.isDamageableItem()) {
            base *= 1.0 - 0.7 * st.getDamageValue() / Math.max(1.0, st.getMaxDamage());
        }
        return (int) Math.max(1, Math.round(base * Math.max(1, st.getCount()) / (st.getMaxStackSize() > 1 ? 8.0 : 1.0)));
    }

    static boolean balanza(ServerPlayer p) {
        ItemStack st = p.getMainHandItem();
        if (st.isEmpty()) {
            PoliciaMod.msg(p, "Tené en la mano lo que querés tasar");
            return false;
        }
        int pr = precio(st);
        p.displayClientMessage(Component.literal("§6⚖ Precio justo de §f" + st.getCount() + " × " + st.getHoverName().getString() + "§6: §a" + pr + (pr == 1 ? " esmeralda" : " esmeraldas")), false);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.8f, 1.6f);
        return true;
    }

    static boolean regateo(ServerPlayer p) {
        LivingEntity e = PoliciaHab.mirando(p, 6.0);
        if (!(e instanceof Villager v)) {
            PoliciaMod.msg(p, "Mirá a un aldeano a menos de 6 bloques para regatear");
            return false;
        }
        v.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, 40);
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2, v.getZ(), 12, 0.4, 0.3, 0.4, 0);
        sl.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 1.0f);
        PoliciaMod.msg(p, "¡Trato hecho! " + v.getName().getString() + " te va a cobrar menos");
        return true;
    }

    // Deposito personal de 27 espacios guardado en los datos del jugador.
    static boolean mochila(ServerPlayer p) {
        CompoundTag h = PoliciaHab.datos(p);
        SimpleContainer c = new SimpleContainer(27) {
            @Override
            public void stopOpen(Player pl) {
                super.stopOpen(pl);
                ListTag l = new ListTag();
                for (int i = 0; i < this.getContainerSize(); i++) {
                    ItemStack st = this.getItem(i);
                    if (!st.isEmpty()) {
                        CompoundTag t = st.save(new CompoundTag());
                        t.putByte("Slot", (byte) i);
                        l.add(t);
                    }
                }
                PoliciaHab.datos(pl).put("mochila", l);
            }
        };
        ListTag l = h.getList("mochila", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            int s = t.getByte("Slot") & 255;
            if (s < 27) {
                c.setItem(s, ItemStack.of(t));
            }
        }
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, c), Component.literal("Mochila sin fondo")));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    static boolean caravana(ServerPlayer p) {
        int n = 3 + PoliciaRPG.nivel(p) / 4;
        dar(p, new ItemStack(Items.EMERALD, n));
        ItemStack[] merca = {new ItemStack(Items.BREAD, 8), new ItemStack(Items.LEATHER, 6), new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.WHITE_WOOL, 8), new ItemStack(Items.GOLD_INGOT, 2)};
        ItemStack extra = merca[p.getRandom().nextInt(merca.length)].copy();
        dar(p, extra);
        ServerLevel sl = p.serverLevel();
        sl.playSound(null, p.blockPosition(), SoundEvents.LLAMA_CHEST, SoundSource.PLAYERS, 1.0f, 1.0f);
        sl.playSound(null, p.blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.PLAYERS, 1.0f, 0.8f);
        sl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 20, 1.0, 0.1, 1.0, 0.02);
        PoliciaMod.msg(p, "Llegó la caravana: +" + n + " esmeraldas y " + extra.getCount() + " " + extra.getHoverName().getString());
        return true;
    }

    static boolean monedas(ServerPlayer p) {
        if (!pagar(p, 1)) {
            return false;
        }
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
        PoliciaHab.tarea(t -> {
            if (t < 30 && t % 5 == 0) {
                sl.sendParticles(ORO, c.x, c.y + 0.6, c.z, 4 + t / 3, 0.2, 0.2, 0.2, 0.0);
                sl.playSound(null, BlockPos.containing(c), SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.6f, 1.0f + t * 0.02f);
            }
            if (t == 34) {
                int r = p.getRandom().nextInt(100);
                int em = r < 30 ? 0 : r < 65 ? 1 : r < 88 ? 2 : r < 97 ? 4 : 8;
                for (int i = 0; i < em; i++) {
                    net.minecraft.world.entity.item.ItemEntity ie = new net.minecraft.world.entity.item.ItemEntity(sl, c.x, c.y + 0.8, c.z, new ItemStack(Items.EMERALD));
                    ie.setDeltaMovement((p.getRandom().nextDouble() - 0.5) * 0.4, 0.4, (p.getRandom().nextDouble() - 0.5) * 0.4);
                    sl.addFreshEntity(ie);
                }
                net.minecraft.world.entity.item.ItemEntity oro = new net.minecraft.world.entity.item.ItemEntity(sl, c.x, c.y + 0.8, c.z, new ItemStack(Items.GOLD_NUGGET, 1 + p.getRandom().nextInt(5)));
                oro.setDeltaMovement(0, 0.4, 0);
                sl.addFreshEntity(oro);
                sl.sendParticles(ORO, c.x, c.y + 1, c.z, 40, 0.5, 0.5, 0.5, 0.2);
                sl.playSound(null, BlockPos.containing(c), PoliciaSonido.anim("chime").get(), SoundSource.PLAYERS, 1.0f, 1.2f);
                p.displayClientMessage(Component.literal(em == 0 ? "§7El cofre de la suerte se tragó la moneda..." : "§6¡Lluvia de monedas! §a" + em + (em == 1 ? " esmeralda" : " esmeraldas")), true);
                return true;
            }
            return false;
        });
        return true;
    }

    static boolean contrato(ServerPlayer p) {
        LivingEntity e = PoliciaHab.mirando(p, 6.0);
        if (!(e instanceof ServerPlayer o)) {
            PoliciaMod.msg(p, "Mirá a otro jugador a menos de 6 bloques para firmar el trato");
            return false;
        }
        for (ServerPlayer x : new ServerPlayer[]{p, o}) {
            x.addEffect(new MobEffectInstance(MobEffects.LUCK, 6000, 0));
            x.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 6000, 0));
            x.serverLevel().sendParticles(ORO, x.getX(), x.getY() + 1.2, x.getZ(), 20, 0.3, 0.4, 0.3, 0);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
        o.displayClientMessage(Component.literal("§6Firmaste un contrato con " + p.getName().getString() + ": suerte y descuentos por 5 min"), false);
        PoliciaMod.msg(p, "Contrato sellado con " + o.getName().getString());
        return true;
    }

    static boolean propina(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        List<Mob> l = sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12.0), m -> m.getTarget() == p);
        if (l.isEmpty()) {
            PoliciaMod.msg(p, "Nadie te está persiguiendo");
            return false;
        }
        if (!pagar(p, 1)) {
            return false;
        }
        for (Mob m : l) {
            m.setTarget(null);
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2, false, false));
            sl.sendParticles(ORO, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 6, 0.2, 0.2, 0.2, 0);
        }
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false));
        sl.playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }

    static boolean cartas(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        StringBuilder sb = new StringBuilder("§6Cartas: ");
        for (int i = 0; i < 3; i++) {
            int r = p.getRandom().nextInt(4);
            switch (r) {
                case 0 -> {
                    curar(sl, p, 4.0f);
                    sb.append("§a♥ cura ");
                }
                case 1 -> {
                    LivingEntity e = PoliciaHab.mirando(p, 16.0);
                    if (e != null && !PoliciaHab.aliado(p, e)) {
                        e.hurt(p.damageSources().playerAttack(p), 5.0f);
                        e.setSecondsOnFire(3);
                        sl.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
                        sb.append("§c🔥 fuego ");
                    } else {
                        dar(p, new ItemStack(Items.EMERALD));
                        sb.append("§a◆ esmeralda ");
                    }
                }
                case 2 -> {
                    ItemStack[] m = {new ItemStack(Items.COOKED_BEEF, 3), new ItemStack(Items.ARROW, 12), new ItemStack(Items.TORCH, 8), new ItemStack(Items.GOLDEN_CARROT, 2)};
                    ItemStack st = m[p.getRandom().nextInt(m.length)].copy();
                    dar(p, st);
                    sb.append("§e✦ ").append(st.getHoverName().getString()).append(" ");
                }
                default -> {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1));
                    sb.append("§b» velocidad ");
                }
            }
        }
        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY() + 1.4, p.getZ(), 20, 0.5, 0.3, 0.5, 0.2);
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.4f);
        p.displayClientMessage(Component.literal(sb.toString()), true);
        return true;
    }

    static boolean tesoro(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        PoliciaHab.tarea(t -> {
            if (!p.isAlive() || p.hasDisconnected()) {
                return true;
            }
            Vec3 puerta = p.position().add(0, 3.2, 0).subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8));
            if (t % 3 == 0) {
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8;
                    sl.sendParticles(ORO, puerta.x + Math.cos(a) * 1.0, puerta.y + Math.sin(a) * 1.0, puerta.z, 1, 0, 0, 0, 0);
                }
            }
            if (t == 20 || t == 40 || t == 60) {
                int k = 0;
                for (LivingEntity e : PoliciaHabExtra.enemigos(p, p.position(), 14.0)) {
                    if (k++ >= 8) {
                        break;
                    }
                    Vec3 d = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(puerta);
                    for (int s = 0; s <= 8; s++) {
                        Vec3 q = puerta.add(d.scale(s / 8.0));
                        sl.sendParticles(ParticleTypes.WAX_OFF, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    }
                    e.invulnerableTime = 0;
                    e.hurt(p.damageSources().playerAttack(p), 4.0f);
                    sl.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 8, 0.2, 0.3, 0.2, 0.2);
                }
                sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 0.8f + t * 0.005f);
            }
            return t >= 70;
        });
        return true;
    }

    static boolean tienda(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        WanderingTrader w = EntityType.WANDERING_TRADER.create(sl);
        if (w == null) {
            return false;
        }
        Vec3 c = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.0));
        w.moveTo(c.x, p.getY(), c.z, p.getYRot() + 180, 0);
        w.setCustomName(Component.literal("§5Comerciante de otro mundo"));
        w.setDespawnDelay(1200);
        MerchantOffers o = w.getOffers();
        o.clear();
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 6), new ItemStack(Items.DIAMOND), 4, 5, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 4), new ItemStack(Items.NAME_TAG), 2, 5, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 8), new ItemStack(Items.EXPERIENCE_BOTTLE, 16), 3, 5, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 16), new ItemStack(Items.NETHERITE_SCRAP), 1, 10, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 10), new ItemStack(Items.SADDLE), 1, 5, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 3), new ItemStack(Items.GLOW_BERRIES, 12), 6, 2, 0.05f));
        o.add(new MerchantOffer(new ItemStack(Items.EMERALD, 12), new ItemStack(Items.ENDER_PEARL, 8), 2, 5, 0.05f));
        sl.addFreshEntity(w);
        sl.sendParticles(ParticleTypes.PORTAL, c.x, p.getY() + 1, c.z, 80, 0.5, 1.0, 0.5, 0.5);
        sl.playSound(null, w.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0f, 0.6f);
        PoliciaHab.tarea(t -> {
            if (t >= 1200 && w.isAlive()) {
                sl.sendParticles(ParticleTypes.PORTAL, w.getX(), w.getY() + 1, w.getZ(), 60, 0.4, 1.0, 0.4, 0.5);
                w.discard();
                return true;
            }
            return !w.isAlive();
        });
        return true;
    }
}
