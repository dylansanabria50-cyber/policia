package com.example.policia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

// Oficio Ladron: carterismo, ganzua, mercado negro y habilidades de fuga
// (silbido, humo, salto, instinto, polvo cegador, escape).
public class PoliciaLadron {
    public static final int[] CHANCE = new int[]{30, 40, 45, 50, 55};
    static final int WHISTLE_CD = 300;
    static final int DIS_T = 140;
    static final int ATT_CD = 40;
    static final double WHISTLE_R = 12.0;
    static final Map<Mob, Dis> DIS = new WeakHashMap<Mob, Dis>();
    static final Map<UUID, SimpleContainer> LOOT = new HashMap<UUID, SimpleContainer>();
    static final String[] AN = new String[]{"Silbido", "Humo", "Salto", "Instinto", "Polvo", "Ganzua", "Escape"};
    static final int[] ACD = new int[]{300, 600, 240, 500, 400, 0, 1800};
    static final Item[] OI = new Item[]{Items.ENDER_PEARL, Items.SADDLE, Items.SPYGLASS, Items.TNT, Items.ARROW, Items.STRING};
    static final int[] OC = new int[]{2, 1, 1, 8, 32, 16};
    static final int[] OP = new int[]{8, 6, 12, 10, 4, 3};

    public static int luck(Player p) {
        return Math.max(0, Math.min(4, p.getPersistentData().getInt("pol_lsu")));
    }

    public static boolean whistle(Player p) {
        return p.getPersistentData().getInt("pol_lsi") > 0;
    }

    public static int wcd(Player p) {
        return p.getPersistentData().getInt("pol_lcd");
    }

    public static boolean has(Player p, int a) {
        if (a == 0) {
            return PoliciaLadron.whistle(p);
        }
        return (p.getPersistentData().getInt("pol_lun") >> a - 1 & 1) == 1;
    }

    public static int cd(Player p, int a) {
        return p.getPersistentData().getInt(a == 0 ? "pol_lcd" : "pol_lc" + a);
    }

    public static int sel(Player p) {
        int s = p.getPersistentData().getInt("pol_lse");
        return s < 0 || s > 4 ? 0 : s;
    }

    public static int mask(Player p) {
        return (PoliciaLadron.whistle(p) ? 1 : 0) | p.getPersistentData().getInt("pol_lun") << 1;
    }

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    static boolean active(ServerPlayer p) {
        if (!"ladron".equals(PoliciaMod.job(p))) {
            return false;
        }
        if (!PoliciaMod.on(p)) {
            PoliciaMod.msg(p, "Activa el modo ladron (B) primero");
            return false;
        }
        return true;
    }

    static void act(ServerPlayer p, int id) {
        CompoundTag d = p.getPersistentData();
        if (id == 1) {
            if (!PoliciaLadron.active(p)) {
                return;
            }
            int a = PoliciaLadron.sel(p);
            if (a == 5) {
                a = 0;
            }
            if (!PoliciaLadron.has(p, a)) {
                PoliciaMod.msg(p, "Desbloquea primero " + AN[a] + " en la rama de habilidades (K cambia de habilidad)");
                return;
            }
            if (PoliciaLadron.cd(p, a) > 0) {
                PoliciaMod.msg(p, AN[a] + " en enfriamiento: " + (PoliciaLadron.cd(p, a) + 19) / 20 + " s");
                return;
            }
            PoliciaLadron.use(p, a);
            return;
        }
        if (id == 2) {
            int s0 = PoliciaLadron.sel(p);
            for (int i = 1; i <= 7; ++i) {
                int a = (s0 + i) % 7;
                if (a == 5 || !PoliciaLadron.has(p, a)) continue;
                d.putInt("pol_lse", a);
                PoliciaMod.msg(p, "Habilidad elegida: " + AN[a]);
                PoliciaMod.sync(p);
                return;
            }
            PoliciaMod.msg(p, "Todavia no tienes habilidades que elegir");
            return;
        }
        if (id >= 82 && id <= 87) {
            int a = id - 81;
            if (PoliciaLadron.has(p, a)) {
                return;
            }
            if (!(a > 2 && a != 6 || PoliciaLadron.whistle(p))) {
                PoliciaMod.msg(p, "Primero desbloquea el Silbido");
                return;
            }
            if (a >= 3 && a <= 5 && PoliciaLadron.luck(p) < 1) {
                PoliciaMod.msg(p, "Primero mejora Suerte al nivel 1");
                return;
            }
            int cost = 20;
            if (PoliciaMod.xp(p) < cost) {
                PoliciaMod.msg(p, "Necesitas " + cost + " XP para " + AN[a] + " (tienes " + PoliciaMod.xp(p) + ")");
                return;
            }
            d.putInt("pol_xp", PoliciaMod.xp(p) - cost);
            d.putInt("pol_lun", d.getInt("pol_lun") | 1 << a - 1);
            if (a != 5) {
                d.putInt("pol_lse", a);
            }
            PoliciaMod.msg(p, a == 5 ? "Habilidad desbloqueada: Ganzua (agachate y clic derecho en un cofre con llave o puerta de hierro de una aldea)" : "Habilidad desbloqueada: " + AN[a] + " (elegida; K cambia, J usa)");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.3f);
            PoliciaMod.sync(p);
            return;
        }
        if (id == 80) {
            int lv = PoliciaLadron.luck(p);
            if (lv >= 4) {
                PoliciaMod.msg(p, "Suerte ya esta al nivel maximo (" + CHANCE[4] + "%)");
                return;
            }
            int cost = 10 * (lv + 1);
            if (PoliciaMod.xp(p) < cost) {
                PoliciaMod.msg(p, "Necesitas " + cost + " XP para Suerte " + (lv + 1) + " (tienes " + PoliciaMod.xp(p) + ")");
                return;
            }
            d.putInt("pol_xp", PoliciaMod.xp(p) - cost);
            d.putInt("pol_lsu", lv + 1);
            PoliciaMod.msg(p, "Suerte " + (lv + 1) + ": carterismo al " + CHANCE[lv + 1] + "%");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.4f);
            PoliciaMod.sync(p);
            return;
        }
        if (id == 81) {
            if (PoliciaLadron.whistle(p)) {
                return;
            }
            if (PoliciaMod.xp(p) < 10) {
                PoliciaMod.msg(p, "Necesitas 10 XP para desbloquear el Silbido");
                return;
            }
            d.putInt("pol_xp", PoliciaMod.xp(p) - 10);
            d.putInt("pol_lsi", 1);
            PoliciaMod.msg(p, "Habilidad desbloqueada: Silbido");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            PoliciaMod.sync(p);
        }
    }

    public static int wanted(Player p) {
        return Math.max(0, Math.min(5, p.getPersistentData().getInt("pol_wnt")));
    }

    static void crime(ServerPlayer p, int n, String why) {
        CompoundTag d = p.getPersistentData();
        d.putInt("pol_esc", 0);
        int w = Math.min(5, PoliciaLadron.wanted(p) + n);
        d.putInt("pol_wnt", w);
        d.putInt("pol_wnt_t", 1200);
        PoliciaMod.msg(p, "Buscado " + "*".repeat(w) + " (" + why + ")");
        for (PoliciaBoss.BossNpc b : p.serverLevel().getEntitiesOfClass(PoliciaBoss.BossNpc.class, p.getBoundingBox().inflate(48.0), x -> x.kind() == 5 && x.isAlive())) {
            b.setTarget(p);
        }
        PoliciaMod.sync(p);
    }

    static boolean zone(ServerLevel sl, BlockPos bp) {
        return sl.dimension() == Level.OVERWORLD && PoliciaStruct.Sites.get(sl).near(0, bp, 90);
    }

    static void pickLock(ServerPlayer p, BlockPos bp, BlockState bs, boolean door) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        if (now - p.getPersistentData().getLong("pol_latt") < 40L) {
            return;
        }
        p.getPersistentData().putLong("pol_latt", now);
        int chance = 50 + 10 * PoliciaLadron.luck(p);
        if (p.getRandom().nextInt(100) >= chance) {
            sl.playSound(null, bp, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 3.0f, 0.7f);
            PoliciaMod.msg(p, "Se rompio la ganzua y salto la alarma! (" + chance + "% de exito)");
            PoliciaLadron.crime(p, 2, "alarma");
            return;
        }
        if (door) {
            ((DoorBlock) bs.getBlock()).setOpen(p, sl, bs, bp, bs.getValue(DoorBlock.OPEN) == false);
        } else {
            MenuProvider mp = bs.getMenuProvider(sl, bp);
            if (mp != null) {
                p.openMenu(mp);
            }
        }
        p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + 3);
        sl.playSound(null, bp, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.BLOCKS, 1.0f, 1.4f);
        PoliciaMod.msg(p, "Ganzua: abierto (" + chance + "%)");
        PoliciaLadron.crime(p, 1, door ? "puerta forzada" : "cofre forzado");
    }

    static int price(ItemStack s) {
        int pr;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        int base = 0;
        if (id.contains("netherite")) {
            base = 20;
        } else if (id.contains("diamond")) {
            base = 10;
        } else if (id.contains("gold")) {
            base = 5;
        } else if (id.contains("iron")) {
            base = 2;
        } else if (id.equals("enchanted_book")) {
            base = 8;
        }
        int n = pr = s.getMaxStackSize() == 1 ? Math.max(3, base * 2) : Math.max(1, Math.max(1, base) * s.getCount() / 8);
        if (s.isEnchanted()) {
            pr += 4;
        }
        return pr;
    }

    static void say(ServerPlayer sp, String s) {
        sp.sendSystemMessage(Component.literal("[Mercader negro] " + s));
    }

    static void market(ServerPlayer sp, PoliciaMision.Pj m) {
        ItemStack h = sp.getMainHandItem();
        boolean sh = sp.isShiftKeyDown();
        boolean job = "ladron".equals(PoliciaMod.job(sp));
        if (!h.isEmpty()) {
            if (h.is(Items.EMERALD)) {
                PoliciaLadron.say(sp, "Las esmeraldas no me interesan.");
                return;
            }
            if (sh && !job) {
                PoliciaLadron.say(sp, "El pago en XP es solo para ladrones. Sin agacharte te pago esmeraldas.");
                return;
            }
            int pr = PoliciaLadron.price(h);
            String nm = h.getCount() + " x " + h.getHoverName().getString();
            sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (sh) {
                sp.getPersistentData().putInt("pol_xp", PoliciaMod.xp(sp) + pr * 2);
                PoliciaLadron.say(sp, "Vendido " + nm + " por " + pr * 2 + " XP.");
                PoliciaMod.sync(sp);
            } else {
                PoliciaMision.give(sp, new ItemStack((ItemLike) Items.EMERALD, pr));
                PoliciaLadron.say(sp, "Vendido " + nm + " por " + pr + " esmeraldas.");
            }
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0f, 0.8f);
            return;
        }
        CompoundTag md = m.getPersistentData();
        int idx = md.getInt("mk_i") % OI.length;
        if (!sh) {
            idx = (idx + 1) % OI.length;
            md.putInt("mk_i", idx);
            PoliciaLadron.say(sp, "Oferta: " + OC[idx] + " x " + new ItemStack((ItemLike) OI[idx]).getHoverName().getString() + " por " + OP[idx] + " esmeraldas. Agachate y clic con la mano vacia para comprar. Clic con un objeto para venderlo (agachado, un ladron cobra XP).");
            return;
        }
        int have = 0;
        for (ItemStack s : sp.getInventory().items) {
            if (!s.is(Items.EMERALD)) continue;
            have += s.getCount();
        }
        if (have < OP[idx]) {
            PoliciaLadron.say(sp, "Te faltan esmeraldas: cuesta " + OP[idx] + " y tienes " + have + ".");
            return;
        }
        int left = OP[idx];
        for (ItemStack s : sp.getInventory().items) {
            if (left <= 0) break;
            if (!s.is(Items.EMERALD)) continue;
            int tk = Math.min(left, s.getCount());
            s.shrink(tk);
            left -= tk;
        }
        PoliciaMision.give(sp, new ItemStack((ItemLike) OI[idx], OC[idx]));
        PoliciaLadron.say(sp, "Comprado: " + OC[idx] + " x " + new ItemStack((ItemLike) OI[idx]).getHoverName().getString() + ".");
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0f, 1.2f);
    }

    static void use(ServerPlayer p, int a) {
        ServerLevel sl = p.serverLevel();
        if (a == 0) {
            PoliciaLadron.whistleUse(p);
            return;
        }
        if (a == 1) {
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 1, false, false));
            for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0), x -> x.getTarget() == p)) {
                m.setTarget(null);
            }
            sl.sendParticles((ParticleOptions) ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.5, 0.8, 0.5, 0.02);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
            PoliciaMod.msg(p, "Humo: invisible 8 s");
        } else if (a == 2) {
            Vec3 l = p.getLookAngle();
            Vec3 h = new Vec3(l.x, 0.0, l.z);
            if (h.lengthSqr() < 1.0E-4) {
                h = new Vec3(0.0, 0.0, 1.0);
            }
            h = h.normalize().scale(1.7);
            p.setDeltaMovement(h.x, 0.55, h.z);
            p.hurtMarked = true;
            p.fallDistance = 0.0f;
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
            sl.sendParticles((ParticleOptions) ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 15, 0.3, 0.1, 0.3, 0.05);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 1.0f, 1.2f);
        } else if (a == 3) {
            int n = 0;
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24.0), x -> x != p && x.isAlive())) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                ++n;
            }
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f);
            PoliciaMod.msg(p, "Instinto: " + n + (n == 1 ? " ser cerca" : " seres cerca") + " (brillan 10 s)");
        } else if (a == 6) {
            p.getPersistentData().putInt("pol_esc", 200);
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0, false, false));
            for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48.0), x -> x.getTarget() == p)) {
                m.setTarget(null);
            }
            sl.sendParticles((ParticleOptions) ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.5, 0.8, 0.5, 0.02);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.7f);
            PoliciaMod.msg(p, "Escape: 10 s sin cometer delitos y la policia te pierde");
        } else {
            int n = 0;
            for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(6.0), x -> x.isAlive() && !(x instanceof AbstractVillager))) {
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 0, false, false));
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 2, false, false));
                m.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 140, 1, false, false));
                m.setTarget(null);
                ++n;
            }
            sl.sendParticles((ParticleOptions) ParticleTypes.POOF, p.getX(), p.getY() + 1.0, p.getZ(), 30, 1.5, 0.5, 1.5, 0.02);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2f, 0.8f);
            PoliciaMod.msg(p, "Polvo cegador: " + n + (n == 1 ? " objetivo afectado" : " objetivos afectados"));
        }
        p.getPersistentData().putInt("pol_lc" + a, ACD[a]);
        PoliciaMod.sync(p);
    }

    static void whistleUse(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        int n = 0;
        for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12.0), x -> x.isAlive())) {
            double dx = m.getX() - p.getX();
            double dz = m.getZ() - p.getZ();
            Dis ds = new Dis();
            ds.yaw = (float) (Mth.atan2(-dx, dz) * 57.29577951308232);
            DIS.put(m, ds);
            PoliciaLadron.face(m, ds.yaw);
            m.setTarget(null);
            m.getNavigation().stop();
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 3, false, false));
            ++n;
        }
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), (SoundEvent) SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.2f, 1.6f);
        sl.sendParticles((ParticleOptions) ParticleTypes.NOTE, p.getX(), p.getY() + 2.1, p.getZ(), 5, 0.4, 0.2, 0.4, 1.0);
        p.getPersistentData().putInt("pol_lcd", 300);
        PoliciaMod.msg(p, n > 0 ? "Silbido: " + n + (n == 1 ? " objetivo mira" : " objetivos miran") + " para otro lado" : "Silbido: no habia nadie cerca");
        PoliciaMod.sync(p);
    }

    static void face(Mob m, float yaw) {
        m.setYRot(yaw);
        m.setYHeadRot(yaw);
        m.yBodyRot = yaw;
        m.yBodyRotO = yaw;
    }

    static SimpleContainer pockets(ServerLevel sl, LivingEntity t, ServerPlayer thief) {
        SimpleContainer c = LOOT.get(t.getUUID());
        if (c != null) {
            return c;
        }
        c = new SimpleContainer(27);
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        try {
            LootTable lt = sl.getServer().getLootData().getLootTable(t.getType().getDefaultLootTable());
            LootParams lp = new LootParams.Builder(sl).withParameter(LootContextParams.THIS_ENTITY, t).withParameter(LootContextParams.ORIGIN, t.position()).withParameter(LootContextParams.DAMAGE_SOURCE, sl.damageSources().generic()).withOptionalParameter(LootContextParams.KILLER_ENTITY, thief).create(LootContextParamSets.ENTITY);
            items.addAll(lt.getRandomItems(lp));
        } catch (RuntimeException runtimeException) {
            // empty catch block
        }
        if (t instanceof AbstractVillager) {
            items.add(new ItemStack((ItemLike) Items.EMERALD, 1 + sl.random.nextInt(4)));
            if (sl.random.nextInt(3) == 0) {
                items.add(new ItemStack((ItemLike) Items.EMERALD_BLOCK, 1 + sl.random.nextInt(2)));
            }
        }
        for (ItemStack s : items) {
            if (s.isEmpty()) continue;
            c.addItem(s);
        }
        LOOT.put(t.getUUID(), c);
        return c;
    }

    static void pick(ServerPlayer p, LivingEntity t) {
        Mob dm;
        if (!PoliciaLadron.active(p)) {
            return;
        }
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        if (now - p.getPersistentData().getLong("pol_latt") < 40L) {
            return;
        }
        p.getPersistentData().putLong("pol_latt", now);
        Vec3 look = t.getLookAngle();
        Vec3 to = new Vec3(p.getX() - t.getX(), 0.0, p.getZ() - t.getZ());
        double len = Math.sqrt(to.x * to.x + to.z * to.z);
        double hl = Math.sqrt(look.x * look.x + look.z * look.z);
        if (len < 1.0E-4 || hl < 1.0E-4) {
            return;
        }
        double dot = (look.x * to.x + look.z * to.z) / (hl * len);
        boolean dist = t instanceof Mob && DIS.containsKey(dm = (Mob) t);
        double d = dist ? 0.4 : -0.35;
        if (dot > d) {
            PoliciaMod.msg(p, "Ponte detras de tu objetivo (silbido ayuda)");
            return;
        }
        if (p.distanceToSqr((Entity) t) > 9.0) {
            return;
        }
        int chance = CHANCE[PoliciaLadron.luck(p)];
        if (p.getRandom().nextInt(100) >= chance) {
            PoliciaMod.msg(p, "Te descubrieron! (" + chance + "% de exito)");
            if (t instanceof AbstractVillager) {
                PoliciaLadron.crime(p, 1, "te vieron");
            }
            sl.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
            if (t instanceof ServerPlayer) {
                ServerPlayer victim = (ServerPlayer) t;
                PoliciaMod.msg(victim, "Alguien intento robarte!");
            } else if (t instanceof Mob) {
                Mob fm = (Mob) t;
                if (!(t instanceof AbstractVillager)) {
                    fm.setTarget(p);
                }
            }
            return;
        }
        MutableComponent title = Component.literal("Bolsillos de " + t.getDisplayName().getString());
        if (t instanceof Player) {
            Player vp = (Player) t;
            p.openMenu((MenuProvider) new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x4, id, inv, (Container) vp.getInventory(), 4), (Component) title));
        } else {
            SimpleContainer c = PoliciaLadron.pockets(sl, t, p);
            if (c.isEmpty()) {
                PoliciaMod.msg(p, "No lleva nada encima");
                return;
            }
            p.openMenu((MenuProvider) new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, (Container) c, 3), (Component) title));
        }
        p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + 2);
        PoliciaMod.msg(p, "Carterismo exitoso (" + chance + "%)");
        if (t instanceof AbstractVillager || t instanceof Player) {
            PoliciaLadron.crime(p, 1, "robo");
        }
        PoliciaMod.sync(p);
    }

    public static class Ev {
        @SubscribeEvent
        public void lEntity(PlayerInteractEvent.EntityInteract e) {
            LivingEntity t;
            ServerPlayer p;
            if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            Player player = e.getEntity();
            if (!(player instanceof ServerPlayer) || !(p = (ServerPlayer) player).isShiftKeyDown()) {
                return;
            }
            if (!"ladron".equals(PoliciaMod.job(p)) || !PoliciaMod.on(p)) {
                return;
            }
            Entity entity = e.getTarget();
            if (!(entity instanceof LivingEntity) || (t = (LivingEntity) entity) == p) {
                return;
            }
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            PoliciaLadron.pick(p, t);
        }

        @SubscribeEvent
        public void lBlock(PlayerInteractEvent.RightClickBlock e) {
            boolean can;
            BlockEntity be;
            boolean chest;
            ServerPlayer p;
            if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            Player player = e.getEntity();
            if (!(player instanceof ServerPlayer) || (p = (ServerPlayer) player).isCreative()) {
                return;
            }
            ServerLevel sl = p.serverLevel();
            BlockPos bp = e.getPos();
            BlockState bs = sl.getBlockState(bp);
            boolean door = bs.is(Blocks.IRON_DOOR);
            boolean bl = chest = bs.is(Blocks.CHEST) || bs.is(Blocks.TRAPPED_CHEST);
            if (!door && !chest) {
                return;
            }
            if (!PoliciaLadron.zone(sl, bp)) {
                return;
            }
            if (chest && ((be = sl.getBlockEntity(bp)) == null || !be.saveWithoutMetadata().contains("LootTable"))) {
                return;
            }
            boolean bl2 = can = "ladron".equals(PoliciaMod.job(p)) && PoliciaMod.on(p) && PoliciaLadron.has(p, 5);
            if (can && p.isShiftKeyDown()) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
                PoliciaLadron.pickLock(p, bp, bs, door);
                return;
            }
            if (!chest) {
                return;
            }
            if (p.isShiftKeyDown() && !p.getMainHandItem().isEmpty()) {
                return;
            }
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            PoliciaMod.msg(p, can ? "Cofre con llave: agachate y usa la ganzua (clic derecho)" : "Cofre con llave: la policia vigila esta aldea. Solo un ladron con Ganzua puede abrirlo");
        }

        @SubscribeEvent
        public void lTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) {
                return;
            }
            if (!DIS.isEmpty()) {
                for (Map.Entry<Mob, Dis> entry : new ArrayList<Map.Entry<Mob, Dis>>(DIS.entrySet())) {
                    Mob m = entry.getKey();
                    Dis ds = entry.getValue();
                    if (m == null || !m.isAlive() || --ds.t <= 0) {
                        DIS.remove(m);
                        continue;
                    }
                    if (ds.t % 3 != 0) continue;
                    if (m.getTarget() instanceof Player) {
                        m.setTarget(null);
                    }
                    PoliciaLadron.face(m, ds.yaw);
                }
            }
            for (ServerPlayer serverPlayer : srv.getPlayerList().getPlayers()) {
                boolean sy = false;
                for (int a = 0; a < 7; ++a) {
                    String key = a == 0 ? "pol_lcd" : "pol_lc" + a;
                    int c = serverPlayer.getPersistentData().getInt(key);
                    if (c <= 0) continue;
                    serverPlayer.getPersistentData().putInt(key, c - 1);
                    if (c != 1 && c % 20 != 0) continue;
                    sy = true;
                }
                CompoundTag dd = serverPlayer.getPersistentData();
                int w = dd.getInt("pol_wnt");
                if (w > 0) {
                    int es = dd.getInt("pol_esc");
                    if (es > 0) {
                        dd.putInt("pol_esc", es - 1);
                        if (es % 20 == 0) {
                            for (PoliciaBoss.BossNpc b : serverPlayer.serverLevel().getEntitiesOfClass(PoliciaBoss.BossNpc.class, serverPlayer.getBoundingBox().inflate(48.0), x -> x.getTarget() == serverPlayer)) {
                                b.setTarget(null);
                            }
                        }
                        if (es == 1) {
                            dd.putInt("pol_wnt", 0);
                            PoliciaMod.msg(serverPlayer, "Despistaste a la policia");
                            sy = true;
                        }
                    } else {
                        int wt = dd.getInt("pol_wnt_t") - 1;
                        if (wt <= 0) {
                            dd.putInt("pol_wnt", w - 1);
                            wt = 1200;
                            sy = true;
                            if (w == 1) {
                                PoliciaMod.msg(serverPlayer, "Ya no te busca la policia");
                            }
                        }
                        dd.putInt("pol_wnt_t", wt);
                    }
                } else if (dd.getInt("pol_esc") > 0) {
                    dd.putInt("pol_esc", 0);
                }
                if (!sy) continue;
                PoliciaMod.sync(serverPlayer);
            }
        }

        @SubscribeEvent
        public void lDrops(LivingDropsEvent e) {
            SimpleContainer c = LOOT.remove(e.getEntity().getUUID());
            if (c == null) {
                return;
            }
            e.getDrops().clear();
            for (int i = 0; i < c.getContainerSize(); ++i) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty()) continue;
                e.getDrops().add(new ItemEntity(e.getEntity().level(), e.getEntity().getX(), e.getEntity().getY(), e.getEntity().getZ(), s));
            }
        }
    }

    static class Dis {
        int t = 140;
        float yaw;

        Dis() {
        }
    }
}
