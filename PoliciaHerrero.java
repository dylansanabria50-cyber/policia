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
    // Enfriamiento de cada habilidad (ticks). En las que duran un tiempo
    // (yunques, marca, faro, vinculo, reparacion) empieza a correr recien
    // cuando la habilidad TERMINA.
    static final int[] CD = new int[]{500, 900, 400, 160, 1000, 1200, 1200, 900};

    static void startCd(ServerPlayer p, int i) {
        if (p.getPersistentData().getBoolean("her_sg" + i)) {
            p.getPersistentData().remove("her_sg" + i);
            return;
        }
        if (PoliciaHerrero.isHerrero(p)) {
            p.getPersistentData().putInt("her_cd" + i, CD[i]);
        }
    }

    // Lo que Reparacion Maestra puede arreglar: desgaste, el costo
    // acumulado de yunque, y yunques astillados/danados.
    static boolean repairable(ItemStack it) {
        return !it.isEmpty() && (it.isDamageableItem() && it.getDamageValue() > 0 || it.getBaseRepairCost() > 0 || it.is(net.minecraft.world.item.Items.CHIPPED_ANVIL) || it.is(net.minecraft.world.item.Items.DAMAGED_ANVIL));
    }
    static final UUID MARK_ARM = UUID.fromString("b4e8c231-7a5d-4f66-9e32-7c1b2d3e4f50");

    static HerSync CL = new HerSync();

    static void init() {
        PoliciaMod.NET.registerMessage(8, HerSync.class, HerSync::enc, HerSync::dec, HerSync::handle);
        PoliciaMod.NET.registerMessage(9, HerAnim.class, HerAnim::enc, HerAnim::dec, HerAnim::handle);
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaHerrero());
    }

    // Manda al cliente lo que necesita el HUD y la pantalla de habilidades
    // del Herrero: habilidad elegida, enfriamientos y efectos activos.
    static void send(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        HerSync m = new HerSync();
        m.sel = PoliciaHerrero.sel(p);
        for (int i = 0; i < 8; ++i) {
            m.cd[i] = d.getInt("her_cd" + i);
        }
        m.anvilT = d.getInt("her_anvil_t");
        m.anvilType = d.getInt("her_anvil_type");
        m.faroT = d.getInt("her_faro_t");
        m.linkT = d.getInt("her_link_t");
        m.markT = d.getInt("her_mark_on");
        m.repT = d.getInt("her_rep_t") > 0 || d.getInt("her_anvil_wait") > 0 ? 1 : 0;
        PoliciaMod.NET.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), (Object) m);
    }

    // Animacion del personaje (yunque sacado de la espalda / golpes con el
    // martillo). Se manda a todos los que ven al jugador; el cliente la
    // dibuja en PoliciaHerreroAnim. kind 0 = plantar yunque, 1 = reparar.
    static final int ANIM_PLACE = 18;
    static final int ANIM_REPAIR = 44;

    static void anim(ServerPlayer p, int kind) {
        PoliciaMod.NET.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), (Object) new HerAnim(p.getId(), kind));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, kind >= 1 ? ANIM_REPAIR : ANIM_PLACE, 3, false, false, false));
    }

    public static class HerAnim {
        final int entity;
        final int kind;

        HerAnim(int entity, int kind) {
            this.entity = entity;
            this.kind = kind;
        }

        static void enc(HerAnim m, net.minecraft.network.FriendlyByteBuf b) {
            b.writeVarInt(m.entity);
            b.writeVarInt(m.kind);
        }

        static HerAnim dec(net.minecraft.network.FriendlyByteBuf b) {
            return new HerAnim(b.readVarInt(), b.readVarInt());
        }

        static void handle(HerAnim m, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> PoliciaHerreroAnim.start(m.entity, m.kind)));
            c.get().setPacketHandled(true);
        }
    }

    public static class HerSync {
        int sel;
        int[] cd = new int[8];
        int anvilT;
        int anvilType;
        int faroT;
        int linkT;
        int markT;
        int repT;

        static void enc(HerSync m, net.minecraft.network.FriendlyByteBuf b) {
            b.writeVarInt(m.sel);
            for (int i = 0; i < 8; ++i) {
                b.writeVarInt(m.cd[i]);
            }
            b.writeVarInt(m.anvilT);
            b.writeVarInt(m.anvilType);
            b.writeVarInt(m.faroT);
            b.writeVarInt(m.linkT);
            b.writeVarInt(m.markT);
            b.writeVarInt(m.repT);
        }

        static HerSync dec(net.minecraft.network.FriendlyByteBuf b) {
            HerSync m = new HerSync();
            m.sel = b.readVarInt();
            for (int i = 0; i < 8; ++i) {
                m.cd[i] = b.readVarInt();
            }
            m.anvilT = b.readVarInt();
            m.anvilType = b.readVarInt();
            m.faroT = b.readVarInt();
            m.linkT = b.readVarInt();
            m.markT = b.readVarInt();
            m.repT = b.readVarInt();
            return m;
        }

        static void handle(HerSync m, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                CL = m;
            });
            c.get().setPacketHandled(true);
        }
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

    // Minijuego de forja: momentos (en ticks de her_rep_t) en que el
    // martillo pega en el yunque durante Reparacion Maestra.
    static final int[] STRIKES = new int[]{23, 32, 41};

    static void strike(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        int rt = d.getInt("her_rep_t");
        if (rt <= 0) {
            return;
        }
        int best = 0;
        int bd = 99;
        for (int i = 0; i < STRIKES.length; ++i) {
            int dd = Math.abs(rt - STRIKES[i]);
            if (dd < bd) {
                bd = dd;
                best = i;
            }
        }
        int tried = d.getInt("her_rep_tried");
        if ((tried >> best & 1) == 1) {
            return;
        }
        d.putInt("her_rep_tried", tried | 1 << best);
        if (bd <= 2) {
            int hits = d.getInt("her_rep_hits") | 1 << best;
            d.putInt("her_rep_hits", hits);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0f, 1.8f);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.6f);
            PoliciaMod.msg(p, "Golpe perfecto! (" + Integer.bitCount(hits) + "/3)");
        } else {
            PoliciaMod.msg(p, "Golpe fuera de tiempo");
        }
    }

    // Aplica la calidad de forja segun los golpes perfectos: Irrompibilidad
    // hasta el nivel de golpes perfectos (maximo III) y una linea en la
    // descripcion del objeto.
    static String quality(ItemStack it, int perfects) {
        if (perfects <= 0 || it.isEmpty()) {
            return "";
        }
        String extra = "";
        if (it.isDamageableItem() && net.minecraft.world.item.enchantment.Enchantments.UNBREAKING.canEnchant(it)) {
            java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> m = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(it);
            int cur = m.getOrDefault(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 0);
            int lvl = Math.min(3, Math.max(cur, perfects));
            if (lvl > cur) {
                m.put(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, lvl);
                net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(m, it);
                extra = " -> Irrompibilidad " + (lvl == 1 ? "I" : (lvl == 2 ? "II" : "III"));
            }
        }
        int q = Math.max(it.getOrCreateTag().getInt("her_calidad"), perfects);
        it.getOrCreateTag().putInt("her_calidad", q);
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        net.minecraft.nbt.CompoundTag disp = it.getOrCreateTagElement("display");
        for (net.minecraft.nbt.Tag tg : disp.getList("Lore", 8)) {
            if (!tg.getAsString().contains("Calidad de forja")) {
                lore.add(tg);
            }
        }
        String stars = "\u2605".repeat(q) + "\u2606".repeat(3 - q);
        lore.add(net.minecraft.nbt.StringTag.valueOf(net.minecraft.network.chat.Component.Serializer.toJson(net.minecraft.network.chat.Component.literal("Calidad de forja: " + stars).withStyle(st -> st.withColor(0xFFD24A).withItalic(false)))));
        disp.put("Lore", (net.minecraft.nbt.Tag) lore);
        return " | Calidad " + q + "/3" + extra;
    }

    static void act(ServerPlayer p, int id) {
        CompoundTag d = p.getPersistentData();
        if (id == 63) {
            PoliciaHerrero.strike(p);
            return;
        }
        int usable = Math.min(unlocked(p), SKILLS.length - 1);
        if (id >= 10 && id < 20) {
            int s = id - 10;
            if (s >= 0 && s < usable) {
                d.putInt("her_sel", s);
                PoliciaMod.msg(p, "Habilidad: " + SKILLS[s]);
            } else if (s >= 0 && s < SKILLS.length - 1) {
                PoliciaMod.msg(p, SKILLS[s] + ": se desbloquea con " + THRESH[s] + " XP");
            }
            PoliciaHerrero.send(p);
            return;
        }
        if (id == 2) {
            int s = (PoliciaHerrero.sel(p) + 1) % usable;
            d.putInt("her_sel", s);
            PoliciaMod.msg(p, "Habilidad: " + SKILLS[s]);
            PoliciaHerrero.send(p);
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
        PoliciaHerrero.doSkill(p, s);
    }

    // Ejecuta la habilidad s (sin mirar desbloqueo ni enfriamiento).
    // Devuelve true si se pudo usar.
    static boolean doSkill(ServerPlayer p, int s) {
        CompoundTag d = p.getPersistentData();
        switch (s) {
            case 0: {
                PoliciaHerrero.forgerShout(p);
                PoliciaHerrero.startCd(p, 0);
                return true;
            }
            case 1: {
                return PoliciaHerrero.placeAnvil(p, 0);
            }
            case 2: {
                if (d.getInt("her_mark_on") > 0) {
                    PoliciaMod.msg(p, "La Marca sigue activa: " + (d.getInt("her_mark_on") + 19) / 20 + " s");
                    return false;
                }
                if (!PoliciaHerrero.mark(p)) {
                    PoliciaMod.msg(p, "Marca del Herrero: sin objetivo a la vista");
                    return false;
                }
                d.putInt("her_mark_on", 300);
                return true;
            }
            case 3: {
                int slot = -1;
                for (int i = 0; i < p.getInventory().getContainerSize(); ++i) {
                    if (!p.getInventory().getItem(i).is(net.minecraft.world.item.Items.IRON_INGOT)) continue;
                    slot = i;
                    break;
                }
                if (slot < 0) {
                    PoliciaMod.msg(p, "Forja Interior: necesitas un lingote de hierro en el inventario");
                    return false;
                }
                p.getInventory().getItem(slot).shrink(1);
                p.heal(4.0f);
                if (p.level() instanceof ServerLevel sl) {
                    sl.sendParticles((ParticleOptions) ParticleTypes.HEART, p.getX(), p.getY() + 1.4, p.getZ(), 5, 0.3, 0.3, 0.3, 0.02);
                    sl.sendParticles((ParticleOptions) ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.4, 0.3, 0.01);
                }
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5f, 1.6f);
                PoliciaMod.msg(p, "Forja Interior: fundiste un lingote de hierro, +2 corazones");
                PoliciaHerrero.startCd(p, 3);
                return true;
            }
            case 4: {
                if (d.getInt("her_faro_t") > 0) {
                    PoliciaMod.msg(p, "El Faro de Forja sigue encendido: " + (d.getInt("her_faro_t") + 19) / 20 + " s");
                    return false;
                }
                BlockPos fp = p.blockPosition();
                if (!PoliciaHerrero.free(p, fp)) {
                    PoliciaMod.msg(p, "No hay espacio libre para el Faro de Forja");
                    return false;
                }
                p.level().setBlockAndUpdate(fp, Blocks.CAMPFIRE.defaultBlockState());
                d.putLong("her_faro_pos", fp.asLong());
                d.putInt("her_faro_t", 1200);
                PoliciaMod.msg(p, "Faro de Forja encendido: cura y da inmunidad al fuego cerca suyo");
                return true;
            }
            case 5: {
                if (d.getInt("her_link_t") > 0) {
                    PoliciaMod.msg(p, "El Vinculo de Acero sigue activo: " + (d.getInt("her_link_t") + 19) / 20 + " s");
                    return false;
                }
                if (!PoliciaHerrero.link(p)) {
                    PoliciaMod.msg(p, "Vinculo de Acero: necesitas otro jugador cerca");
                    return false;
                }
                return true;
            }
            case 6: {
                boolean off = !PoliciaHerrero.repairable(p.getMainHandItem()) && PoliciaHerrero.repairable(p.getOffhandItem());
                ItemStack it = off ? p.getOffhandItem() : p.getMainHandItem();
                if (!PoliciaHerrero.repairable(it)) {
                    PoliciaMod.msg(p, "Reparacion Maestra: necesitas en la mano algo gastado (o con costo de yunque) para reparar");
                    return false;
                }
                d.putBoolean("her_rep_off", off);
                if (d.getInt("her_rep_t") > 0 || d.getInt("her_anvil_wait") > 0) {
                    PoliciaMod.msg(p, "Termina lo que estas forjando primero");
                    return false;
                }
                d.putInt("her_rep_slot", p.getInventory().selected);
                d.putInt("her_rep_hits", 0);
                d.putInt("her_rep_tried", 0);
                d.putInt("her_rep_t", 1);
                PoliciaHerrero.anim(p, 1);
                PoliciaMod.msg(p, "Reparacion Maestra: sacas el yunque y empezas a forjar...");
                return true;
            }
            case 7: {
                return PoliciaHerrero.placeAnvil(p, 1);
            }
        }
        return false;
    }

    // Objeto de un solo uso (tienda del Maestro herrero): la habilidad dura
    // lo mismo que la normal, pero al terminar NO empieza el enfriamiento.
    static boolean single(ServerPlayer p, int s) {
        if (!PoliciaHerrero.isHerrero(p)) {
            PoliciaMod.msg(p, "Solo un herrero puede usar esto");
            return false;
        }
        CompoundTag d = p.getPersistentData();
        d.putBoolean("her_sg" + s, true);
        if (!PoliciaHerrero.doSkill(p, s)) {
            d.remove("her_sg" + s);
            return false;
        }
        PoliciaHerrero.send(p);
        return true;
    }

    static boolean free(ServerPlayer p, BlockPos bp) {
        return p.level().getBlockState(bp).isAir() || p.level().getBlockState(bp).canBeReplaced();
    }

    static final double SHOUT_R = 6.0;

    // Ondas de sonido del Grito: tres anillos que salen del jugador y se
    // abren hasta el radio donde hace efecto (6 bloques).
    static void shoutWaves(ServerPlayer p, ServerLevel sl, int t) {
        CompoundTag d = p.getPersistentData();
        double cx = d.getDouble("her_grito_x");
        double cy = d.getDouble("her_grito_y");
        double cz = d.getDouble("her_grito_z");
        net.minecraft.core.particles.DustParticleOptions dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.78f, 0.35f), 1.3f);
        net.minecraft.core.particles.DustParticleOptions edge = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.95f, 0.75f), 0.9f);
        for (int w = 0; w < 3; ++w) {
            int start = w * 5;
            if (t < start) continue;
            double r = Math.min(SHOUT_R, (double) (t - start) * 0.75 + 0.5);
            if ((double) (t - start) * 0.75 + 0.5 > SHOUT_R + 0.8) continue;
            int pts = 10 + (int) (r * 9.0);
            double y = cy + 1.0 - (double) w * 0.35;
            for (int i = 0; i < pts; ++i) {
                double a = Math.PI * 2.0 * (double) i / (double) pts;
                sl.sendParticles(r >= SHOUT_R ? edge : dust, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (t == 1) {
            sl.sendParticles((ParticleOptions) ParticleTypes.SONIC_BOOM, cx, cy + 1.0, cz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    static void forgerShout(ServerPlayer p) {
        for (LivingEntity m : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SHOUT_R), x -> x != p && x.isAlive() && x.distanceTo(p) <= SHOUT_R + 0.5)) {
            m.knockback(1.0f, p.getX() - m.getX(), p.getZ() - m.getZ());
            m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, false, true));
            if (m instanceof Mob mob && !mob.getMainHandItem().isEmpty()) {
                ItemStack held = mob.getMainHandItem();
                mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                mob.spawnAtLocation(held);
            }
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.7f, 1.6f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5f, 1.4f);
        CompoundTag d = p.getPersistentData();
        d.putInt("her_grito_t", 1);
        d.putDouble("her_grito_x", p.getX());
        d.putDouble("her_grito_y", p.getY());
        d.putDouble("her_grito_z", p.getZ());
        PoliciaMod.msg(p, "Grito del Forjador!");
    }

    static boolean placeAnvil(ServerPlayer p, int type) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("her_anvil_t") > 0 || d.getInt("her_anvil_wait") > 0) {
            PoliciaMod.msg(p, "Ya hay un yunque plantado");
            return false;
        }
        if (d.getInt("her_rep_t") > 0) {
            PoliciaMod.msg(p, "Termina lo que estas forjando primero");
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
        // El bloque aparece cuando la animacion lo apoya en el piso.
        d.putLong("her_anvil_pos", bp.asLong());
        d.putInt("her_anvil_type", type);
        d.putInt("her_anvil_wait", 15);
        PoliciaHerrero.anim(p, 0);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0f, 0.7f);
        return true;
    }

    static void plantAnvil(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        BlockPos bp = BlockPos.of(d.getLong("her_anvil_pos"));
        int type = d.getInt("her_anvil_type");
        if (!PoliciaHerrero.free(p, bp)) {
            PoliciaMod.msg(p, "No hay espacio libre para el yunque");
            d.putInt("her_cd" + (type == 0 ? 1 : 7), 0);
            d.remove("her_sg" + (type == 0 ? 1 : 7));
            return;
        }
        p.level().setBlockAndUpdate(bp, Blocks.ANVIL.defaultBlockState().setValue(net.minecraft.world.level.block.AnvilBlock.FACING, p.getDirection().getClockWise()));
        d.putInt("her_anvil_t", 800);
        d.putBoolean("her_anvil_on", true);
        d.putString("her_anvil_dim", p.serverLevel().dimension().location().toString());
        ANVILS.add(PoliciaHerrero.anvilKey(d.getString("her_anvil_dim"), bp));
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles((ParticleOptions) ParticleTypes.POOF, bp.getX() + 0.5, bp.getY() + 0.2, bp.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.03);
        }
        p.level().playSound(null, bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.9f);
        PoliciaMod.msg(p, type == 0 ? "Yunque Portatil plantado: mas danio cerca suyo" : "Yunque Guardian plantado: intercepta proyectiles cercanos");
    }

    // Yunques del herrero plantados ahora mismo ("dimension|pos"): no se
    // pueden romper para llevarselos, desaparecen solos al terminar.
    static final java.util.Set<String> ANVILS = new java.util.HashSet<String>();

    static String anvilKey(String dim, BlockPos bp) {
        return dim + "|" + bp.asLong();
    }

    static void removeAnvil(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        d.putInt("her_anvil_t", 0);
        if (!d.getBoolean("her_anvil_on")) {
            return;
        }
        d.putBoolean("her_anvil_on", false);
        PoliciaHerrero.startCd(p, d.getInt("her_anvil_type") == 0 ? 1 : 7);
        BlockPos bp = BlockPos.of(d.getLong("her_anvil_pos"));
        String dim = d.getString("her_anvil_dim");
        ANVILS.remove(PoliciaHerrero.anvilKey(dim, bp));
        ServerLevel lv = null;
        try {
            lv = p.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(dim)));
        } catch (Exception ignored) {
        }
        if (lv == null) {
            lv = p.serverLevel();
        }
        if (lv.getBlockState(bp).is(net.minecraft.tags.BlockTags.ANVIL)) {
            lv.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
            lv.sendParticles((ParticleOptions) ParticleTypes.POOF, bp.getX() + 0.5, bp.getY() + 0.4, bp.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.02);
        }
    }

    @SubscribeEvent
    public void noBreakAnvil(net.minecraftforge.event.level.BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) {
            return;
        }
        if (ANVILS.contains(PoliciaHerrero.anvilKey(sl.dimension().location().toString(), e.getPos()))) {
            e.setCanceled(true);
            if (e.getPlayer() instanceof ServerPlayer sp) {
                PoliciaMod.msg(sp, "Es un yunque de herrero: desaparece solo cuando termina la habilidad");
            }
        }
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
        if (d.getInt("her_link_t") > 0 || d.hasUUID("her_link_id")) {
            PoliciaHerrero.startCd(p, 5);
        }
        d.putInt("her_link_t", 0);
        d.remove("her_link_id");
    }

    static void reset(ServerPlayer p) {
        CompoundTag rd = p.getPersistentData();
        if (rd.getInt("her_faro_t") > 0) {
            PoliciaHerrero.startCd(p, 4);
        }
        if (rd.getInt("her_rep_t") > 0) {
            PoliciaHerrero.startCd(p, 6);
        }
        if (rd.getInt("her_mark_on") > 0) {
            rd.putInt("her_mark_on", 0);
            PoliciaHerrero.startCd(p, 2);
        }
        p.getPersistentData().putInt("her_anvil_wait", 0);
        p.getPersistentData().putInt("her_rep_t", 0);
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
        if (p.tickCount % 10 == 0) {
            PoliciaHerrero.send(p);
        }
        for (int i = 0; i < SKILLS.length - 1; ++i) {
            int c = d.getInt("her_cd" + i);
            if (c <= 0) continue;
            d.putInt("her_cd" + i, --c);
            if (c == 0) {
                PoliciaMod.msg(p, SKILLS[i] + " lista");
            }
        }
        int gt = d.getInt("her_grito_t");
        if (gt > 0) {
            if (p.level() instanceof ServerLevel gsl) {
                PoliciaHerrero.shoutWaves(p, gsl, gt);
            }
            d.putInt("her_grito_t", gt >= 20 ? 0 : gt + 1);
        }
        int aw = d.getInt("her_anvil_wait");
        if (aw > 0) {
            d.putInt("her_anvil_wait", --aw);
            if (aw == 0) {
                PoliciaHerrero.plantAnvil(p);
            }
        }
        if (d.getBoolean("her_anvil_on") && d.getInt("her_anvil_t") <= 0) {
            PoliciaHerrero.removeAnvil(p);
        }
        int at = d.getInt("her_anvil_t");
        if (at > 0) {
            d.putInt("her_anvil_t", --at);
            BlockPos bp = BlockPos.of(d.getLong("her_anvil_pos"));
            int type = d.getInt("her_anvil_type");
            if (type == 0) {
                if (p.blockPosition().distSqr(bp) < 64.0) {
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 30, 0, false, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 30, 0, false, false, true));
                }
                if (p.tickCount % 10 == 0) {
                    for (Mob m : p.level().getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(bp).inflate(8.0), x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.isAlive())) {
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, true, false));
                        // Marca al monstruo como afectado por este yunque (vale
                        // un rato despues de salir del radio).
                        m.getPersistentData().putUUID("her_anvil_by", p.getUUID());
                        m.getPersistentData().putLong("her_anvil_until", p.level().getGameTime() + 60L);
                    }
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
                if (ft == 0) {
                    if (p.level().getBlockState(bp).is(Blocks.CAMPFIRE)) {
                        p.level().setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
                    }
                    PoliciaMod.msg(p, "El Faro de Forja se apago");
                    PoliciaHerrero.startCd(p, 4);
                }
            }
        }
        int mt = d.getInt("her_marked_t");
        if (mt > 0) {
            d.putInt("her_marked_t", --mt);
        }
        int mo = d.getInt("her_mark_on");
        if (mo > 0) {
            d.putInt("her_mark_on", --mo);
            if (mo == 0) {
                PoliciaMod.msg(p, "La Marca del Herrero se borro");
                PoliciaHerrero.startCd(p, 2);
            }
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
            Vec3 f = Vec3.directionFromRotation(0.0f, p.getYRot()).scale(0.9);
            double ax = p.getX() + f.x;
            double ay = p.getY() + 0.6;
            double az = p.getZ() + f.z;
            if (rt == 15 && p.level() instanceof ServerLevel sl) {
                sl.sendParticles((ParticleOptions) ParticleTypes.POOF, ax, p.getY() + 0.1, az, 8, 0.25, 0.05, 0.25, 0.02);
                p.level().playSound(null, ax, ay, az, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            // Golpes del martillo: coinciden con la bajada del brazo en la
            // animacion (ticks 23, 32 y 41).
            if ((rt == 23 || rt == 32 || rt == 41) && p.level() instanceof ServerLevel sl) {
                p.level().playSound(null, ax, ay, az, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.9f, 0.9f + (float) rt * 0.01f);
                sl.sendParticles((ParticleOptions) ParticleTypes.CRIT, ax, ay, az, 10, 0.15, 0.1, 0.15, 0.25);
                sl.sendParticles((ParticleOptions) ParticleTypes.LAVA, ax, ay, az, 2, 0.1, 0.05, 0.1, 0.0);
            }
            if (rt >= ANIM_REPAIR) {
                d.putInt("her_rep_t", 0);
                PoliciaHerrero.startCd(p, 6);
                boolean off = d.getBoolean("her_rep_off");
                ItemStack it = off ? p.getOffhandItem() : p.getInventory().getItem(d.getInt("her_rep_slot"));
                if (PoliciaHerrero.repairable(it)) {
                    if (it.isDamageableItem()) {
                        it.setDamageValue(0);
                    }
                    it.setRepairCost(0);
                    if (it.is(net.minecraft.world.item.Items.CHIPPED_ANVIL) || it.is(net.minecraft.world.item.Items.DAMAGED_ANVIL)) {
                        ItemStack nu = new ItemStack(net.minecraft.world.item.Items.ANVIL, it.getCount());
                        if (off) {
                            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, nu);
                        } else {
                            p.getInventory().setItem(d.getInt("her_rep_slot"), nu);
                        }
                        it = nu;
                    }
                    String ql = PoliciaHerrero.quality(it, Integer.bitCount(d.getInt("her_rep_hits")));
                    PoliciaMod.msg(p, "Reparacion Maestra: " + it.getHoverName().getString() + " como nuevo" + ql);
                    PoliciaJefe.add(p, "repair", 1);
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.8f);
                } else {
                    PoliciaMod.msg(p, "Reparacion Maestra: ya no tenias ese objeto en la mano");
                }
                if (p.level() instanceof ServerLevel sl) {
                    sl.sendParticles((ParticleOptions) ParticleTypes.POOF, ax, ay, az, 10, 0.25, 0.2, 0.25, 0.03);
                }
            } else {
                d.putInt("her_rep_t", rt + 1);
            }
        }
    }

    // Yunque Portatil: los monstruos afectados por el yunque (con su
    // Lentitud) que mata el herrero sueltan el DOBLE de experiencia. La XP
    // del oficio se suma sola con la de Minecraft, asi que tambien se duplica.
    @SubscribeEvent
    public void xpDrop(net.minecraftforge.event.entity.living.LivingExperienceDropEvent e) {
        if (!(e.getAttackingPlayer() instanceof ServerPlayer p) || !PoliciaHerrero.isHerrero(p)) {
            return;
        }
        CompoundTag t = e.getEntity().getPersistentData();
        if (!t.hasUUID("her_anvil_by") || !t.getUUID("her_anvil_by").equals(p.getUUID()) || t.getLong("her_anvil_until") < p.level().getGameTime()) {
            return;
        }
        e.setDroppedExperience(e.getDroppedExperience() * 2);
        if (p.level() instanceof ServerLevel sl) {
            LivingEntity m = e.getEntity();
            sl.sendParticles((ParticleOptions) ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 0.8, m.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
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
