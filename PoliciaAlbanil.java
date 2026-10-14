package com.example.policia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Position;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

// Oficio Albanil: pico magico, muro/refugio construibles y desmontables,
// ayudantes mineros, tractor y linterna que sigue la mirada.
public class PoliciaAlbanil {
    static final String JOB = "albanil";
    static final String[] NAMES = new String[]{"Pico de albanil", "MURO", "REFUGIO"};
    static final int PICK_T = 140;
    static final int PICK_CD = 160;
    static final int WALL_T = 300;
    static final int WALL_CD = 100;
    static final int HOUSE_T = 700;
    static final int HOUSE_CD = 800;
    static final int HOUSE2_T = 3600;
    static final int COST = 10;
    static final int COST2 = 40;
    static final int COST3 = 25;
    static final int PICK2_T = 280;
    static AlbSync CL = new AlbSync();
    static final int LAMP_T = 1200;
    static final int LAMP_CD = 400;
    static final Set<Long> LIVE = new HashSet<Long>();
    static final Set<Block> MINE = Set.of(Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.GLASS, Blocks.GLOWSTONE, Blocks.RED_BED, Blocks.CHEST, Blocks.OAK_DOOR, Blocks.TORCH);

    static int pickT(ServerPlayer p) {
        return p.getPersistentData().getInt("alb_pl") >= 1 ? 280 : 140;
    }

    static void init() {
        PoliciaMod.NET.registerMessage(2, AlbSync.class, AlbSync::enc, AlbSync::dec, AlbSync::handle);
        PoliciaAyudante.init();
        PoliciaTractor.init();
        PoliciaMision.init();
    }

    static boolean isAlb(Player p) {
        return JOB.equals(PoliciaMod.job(p));
    }

    static void send(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        AlbSync m = new AlbSync();
        m.lv = Math.max(0, Math.min(3, d.getInt("alb_lv")));
        m.sel = d.getInt("alb_sel");
        m.pickT = d.getInt("alb_pick");
        m.structT = d.getInt("alb_st");
        m.c0 = d.getInt("alb_c0");
        m.c1 = d.getInt("alb_c1");
        m.walls = d.getInt("alb_walls");
        m.hl = Math.max(0, Math.min(3, d.getInt("alb_hl")));
        m.ht = d.getInt("alb_ht");
        m.hc = d.getInt("alb_hcd");
        m.mode = d.getInt("alb_mode");
        m.lu = d.getInt("alb_lu");
        m.lt = d.getInt("alb_lamp");
        m.lc = d.getInt("alb_lcd");
        m.zl = d.getInt("alb_zon") == 1 ? d.getInt("alb_zl") : 0;
        m.zw = d.getInt("alb_zw");
        m.tu = d.getInt("alb_tu");
        m.tc = d.getInt("alb_tcd");
        m.ta = d.getInt("alb_tact");
        m.pl = d.getInt("alb_pl");
        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), (Object) m);
    }

    static String nm(ServerPlayer p, int s) {
        if (s == 2) {
            return "AYUDANTE";
        }
        if (s == 3) {
            return "LINTERNA";
        }
        if (s == 4) {
            return "TRACTOR";
        }
        return s == 0 ? NAMES[0] : (PoliciaAlbanil.lv(p) >= 3 ? "REFUGIO II" : (PoliciaAlbanil.lv(p) >= 2 ? NAMES[2] : NAMES[1]));
    }

    static int lv(ServerPlayer p) {
        return Math.max(0, Math.min(3, p.getPersistentData().getInt("alb_lv")));
    }

    static void act(ServerPlayer p, int id) {
        CompoundTag d = p.getPersistentData();
        if (id >= 1000) {
            PoliciaAlbanil.setZone(p, (id - 1000) / 10, (id - 1000) % 10);
            return;
        }
        if (id == 50) {
            if (p.getVehicle() instanceof PoliciaTractor.TractorEntity) {
                PoliciaTractor.openInv(p);
            } else {
                PoliciaAyudante.openBank(p);
            }
            return;
        }
        if (id == 42) {
            PoliciaTractor.unlock(p);
            return;
        }
        if (id == 15) {
            if (d.getInt("alb_tu") >= 1) {
                d.putInt("alb_sel", 4);
                PoliciaMod.msg(p, "Habilidad: TRACTOR");
                PoliciaMod.sync(p);
            }
            return;
        }
        if (id == 44) {
            if (d.getInt("alb_pl") >= 1) {
                PoliciaMod.msg(p, "PICO II ya esta desbloqueado");
                return;
            }
            if (PoliciaMod.xp(p) < 25) {
                PoliciaMod.msg(p, "Necesitas 25 XP (tienes " + PoliciaMod.xp(p) + ")");
                return;
            }
            d.putInt("pol_xp", PoliciaMod.xp(p) - 25);
            d.putInt("alb_pl", 1);
            PoliciaMod.msg(p, "Habilidad mejorada: PICO II (14 s, Fuerza II y Prisa minera II)");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.1f);
            PoliciaMod.sync(p);
            return;
        }
        if (id == 43) {
            int lv = PoliciaAlbanil.lv(p);
            if (lv >= 3) {
                PoliciaMod.msg(p, "REFUGIO II ya esta desbloqueado");
                return;
            }
            if (lv < 2) {
                PoliciaMod.msg(p, "Desbloquea primero: REFUGIO");
                return;
            }
            if (PoliciaMod.xp(p) < 40) {
                PoliciaMod.msg(p, "Necesitas 40 XP (tienes " + PoliciaMod.xp(p) + ")");
                return;
            }
            d.putInt("pol_xp", PoliciaMod.xp(p) - 40);
            d.putInt("alb_lv", 3);
            PoliciaMod.msg(p, "Habilidad mejorada: REFUGIO II");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.0f);
            PoliciaMod.sync(p);
            return;
        }
        if (id == 41) {
            if (d.getInt("alb_lu") >= 1) {
                PoliciaMod.msg(p, "LINTERNA ya esta desbloqueada");
                return;
            }
            if (PoliciaMod.xp(p) < 10) {
                PoliciaMod.msg(p, "Necesitas 10 XP (tienes " + PoliciaMod.xp(p) + ")");
                return;
            }
            d.putInt("pol_xp", PoliciaMod.xp(p) - 10);
            d.putInt("alb_lu", 1);
            PoliciaMod.msg(p, "Habilidad desbloqueada: LINTERNA");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.5f);
            PoliciaMod.sync(p);
            return;
        }
        if (id == 40) {
            int hl = d.getInt("alb_hl");
            if (hl >= 3) {
                PoliciaMod.msg(p, "AYUDANTE ya esta al nivel maximo");
                return;
            }
            int cost = 10 + 5 * hl;
            if (PoliciaMod.xp(p) < cost) {
                PoliciaMod.msg(p, "Necesitas " + cost + " XP (tienes " + PoliciaMod.xp(p) + ")");
                return;
            }
            d.putInt("pol_xp", PoliciaMod.xp(p) - cost);
            d.putInt("alb_hl", hl + 1);
            PoliciaMod.msg(p, "AYUDANTE nivel " + (hl + 1) + ": " + PoliciaAyudante.COUNT[hl + 1] + (hl == 0 ? " ayudante" : " ayudantes") + " con pico de " + PoliciaAyudante.PICKS[hl + 1]);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.3f);
            PoliciaMod.sync(p);
            return;
        }
        if (id >= 20 && id < 30) {
            int s = id - 20;
            if (s != 1 && s != 2) {
                return;
            }
            int lv = PoliciaAlbanil.lv(p);
            if (lv >= 2) {
                PoliciaMod.msg(p, "MURO ya esta mejorado a REFUGIO");
                return;
            }
            if (PoliciaMod.xp(p) < 10) {
                PoliciaMod.msg(p, "Necesitas 10 XP (tienes " + PoliciaMod.xp(p) + ")");
                return;
            }
            if (lv == 0 && s == 2) {
                PoliciaMod.msg(p, "Desbloquea primero: MURO");
                return;
            }
            d.putInt("pol_xp", PoliciaMod.xp(p) - 10);
            d.putInt("alb_lv", lv + 1);
            PoliciaMod.msg(p, lv == 0 ? "Habilidad desbloqueada: MURO" : "Habilidad mejorada: REFUGIO");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            PoliciaMod.sync(p);
            return;
        }
        if (id == 14) {
            if (d.getInt("alb_lu") >= 1) {
                d.putInt("alb_sel", 3);
                PoliciaMod.msg(p, "Habilidad: LINTERNA");
                PoliciaMod.sync(p);
            }
            return;
        }
        if (id == 13) {
            if (d.getInt("alb_hl") >= 1) {
                d.putInt("alb_sel", 2);
                PoliciaMod.msg(p, "Habilidad: AYUDANTE");
                PoliciaMod.sync(p);
            }
            return;
        }
        if (id >= 10 && id < 20) {
            int s = id - 10;
            if (s == 2) {
                s = 1;
            }
            if (s == 0 || s == 1 && PoliciaAlbanil.lv(p) >= 1) {
                d.putInt("alb_sel", s);
                PoliciaMod.msg(p, "Habilidad: " + PoliciaAlbanil.nm(p, s));
                PoliciaMod.sync(p);
            }
            return;
        }
        if (!PoliciaMod.on(p)) {
            PoliciaMod.msg(p, "Activa el modo albanil (B) primero");
            return;
        }
        if (id == 2) {
            int cur;
            int s = cur = d.getInt("alb_sel");
            for (int k = 1; k <= 5; ++k) {
                int c = (cur + k) % 5;
                if (!(c == 0 || c == 1 && PoliciaAlbanil.lv(p) >= 1 || c == 2 && d.getInt("alb_hl") >= 1 || c == 3 && d.getInt("alb_lu") >= 1) && (c != 4 || d.getInt("alb_tu") < 1)) continue;
                s = c;
                break;
            }
            d.putInt("alb_sel", s);
            PoliciaMod.msg(p, "Habilidad: " + PoliciaAlbanil.nm(p, s));
            PoliciaMod.sync(p);
            return;
        }
        if (id == 8 || id == 9) {
            PoliciaAyudante.answer(p, id == 8);
            return;
        }
        if (id == 7) {
            if (d.getInt("alb_hl") < 1) {
                PoliciaMod.msg(p, "Aun no tienes ayudantes: desbloquea AYUDANTE");
                return;
            }
            int m = (d.getInt("alb_mode") + 1) % 3;
            d.putInt("alb_mode", m);
            PoliciaMod.msg(p, "Orden a los ayudantes: " + PoliciaAyudante.ORDERS[m]);
            PoliciaMod.sync(p);
            return;
        }
        if (id != 1) {
            return;
        }
        int s = d.getInt("alb_sel");
        if (s == 1 && PoliciaAlbanil.lv(p) < 1) {
            s = 0;
        }
        if (s == 2) {
            PoliciaAyudante.use(p);
            return;
        }
        if (s == 3) {
            PoliciaAlbanil.toggleLamp(p);
            return;
        }
        if (s == 4) {
            PoliciaTractor.use(p);
            return;
        }
        if (s == 0) {
            if (d.getInt("alb_pick") > 0) {
                PoliciaMod.msg(p, "El pico ya esta activo");
                return;
            }
            if (d.getInt("alb_c0") > 0) {
                PoliciaMod.msg(p, "Pico en enfriamiento: " + (d.getInt("alb_c0") + 19) / 20 + " s");
                return;
            }
            d.putInt("alb_pick", PoliciaAlbanil.pickT(p));
            PoliciaAlbanil.startPick(p);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.6f, 1.4f);
            PoliciaMod.msg(p, "Pico de albanil: fuerza y prisa minera");
        } else {
            boolean house;
            boolean bl = house = PoliciaAlbanil.lv(p) >= 2;
            if (d.getInt("alb_st") > 0) {
                if (house || d.getInt("alb_house") == 1) {
                    PoliciaMod.msg(p, "El refugio sigue en pie");
                    return;
                }
                if (d.getInt("alb_walls") >= 3) {
                    PoliciaMod.msg(p, "Ya levantaste los 3 muros");
                    return;
                }
            } else {
                if (d.getInt("alb_c1") > 0) {
                    PoliciaMod.msg(p, "Construccion en enfriamiento: " + (d.getInt("alb_c1") + 19) / 20 + " s");
                    return;
                }
                d.putInt("alb_walls", 0);
                d.putInt("alb_st", house ? (PoliciaAlbanil.lv(p) >= 3 ? 3600 : 700) : 300);
                d.putInt("alb_house", house ? 1 : 0);
            }
            if (house && PoliciaAlbanil.lv(p) >= 3) {
                PoliciaAlbanil.buildHouse2(p);
            } else if (house) {
                PoliciaAlbanil.buildHouse(p);
            } else {
                PoliciaAlbanil.buildWall(p);
                d.putInt("alb_walls", d.getInt("alb_walls") + 1);
            }
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 1.2f);
            PoliciaMod.msg(p, house ? (PoliciaAlbanil.lv(p) >= 3 ? "REFUGIO II levantado" : "REFUGIO levantado") : "MURO " + d.getInt("alb_walls") + "/3");
        }
        PoliciaMod.sync(p);
    }

    static void setZone(ServerPlayer p, int len, int wid) {
        CompoundTag d = p.getPersistentData();
        if (!PoliciaAlbanil.isAlb(p) || d.getInt("alb_hl") < 1) {
            return;
        }
        len = Math.max(1, Math.min(24, len));
        wid = Math.max(1, Math.min(9, wid));
        d.putLong("alb_zo", p.blockPosition().asLong());
        d.putInt("alb_zd", p.getDirection().get3DDataValue());
        d.putInt("alb_zl", len);
        d.putInt("alb_zw", wid);
        d.putInt("alb_zon", 1);
        d.putInt("alb_mode", 1);
        PoliciaMod.msg(p, "Area fijada: " + len + " de largo x " + wid + " de ancho x 3 de alto. Orden: " + PoliciaAyudante.ORDERS[1]);
        PoliciaMod.sync(p);
    }

    static void toggleLamp(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (!PoliciaAlbanil.isAlb(p)) {
            return;
        }
        if (d.getInt("alb_lu") < 1) {
            PoliciaMod.msg(p, "Desbloquea primero: LINTERNA");
            return;
        }
        if (!PoliciaMod.on(p)) {
            PoliciaMod.msg(p, "Activa el modo albanil (B) primero");
            return;
        }
        if (d.getInt("alb_lamp") > 0) {
            d.putInt("alb_lamp", 0);
            PoliciaAlbanil.lampClear(p);
            d.putInt("alb_lcd", 400);
            PoliciaMod.msg(p, "Linterna apagada");
        } else {
            if (d.getInt("alb_lcd") > 0) {
                PoliciaMod.msg(p, "Linterna en enfriamiento: " + (d.getInt("alb_lcd") + 19) / 20 + " s");
                return;
            }
            d.putInt("alb_lamp", 1200);
            PoliciaAlbanil.lampTick(p);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.8f, 1.4f);
            PoliciaMod.msg(p, "Linterna: la luz sigue tu mirada");
        }
        PoliciaMod.sync(p);
    }

    static void lampTick(ServerPlayer p) {
        BlockPos old;
        Vec3 end;
        Vec3 eye;
        CompoundTag d = p.getPersistentData();
        ServerLevel lv = p.serverLevel();
        BlockHitResult hit = lv.clip(new ClipContext(eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(48.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) p));
        BlockPos pos = hit.getType() == HitResult.Type.MISS ? BlockPos.containing((Position) end) : hit.getBlockPos().relative(hit.getDirection());
        BlockPos blockPos = old = d.contains("alb_lp") ? BlockPos.of(d.getLong("alb_lp")) : null;
        if (old != null && old.equals((Object) pos)) {
            return;
        }
        if (!lv.getBlockState(pos).isAir()) {
            return;
        }
        if (old != null && lv.getBlockState(old).is(Blocks.SEA_LANTERN)) {
            lv.setBlock(old, Blocks.AIR.defaultBlockState(), 3);
        }
        lv.setBlock(pos, Blocks.SEA_LANTERN.defaultBlockState(), 3);
        d.putLong("alb_lp", pos.asLong());
    }

    static void lampClear(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains("alb_lp")) {
            return;
        }
        BlockPos old = BlockPos.of(d.getLong("alb_lp"));
        d.remove("alb_lp");
        ServerLevel lv = p.serverLevel();
        if (lv.getBlockState(old).is(Blocks.SEA_LANTERN)) {
            lv.setBlock(old, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    static boolean isPick(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean("alb_pick");
    }

    static ItemStack makePick(ServerPlayer p) {
        Map<Enchantment, Integer> em;
        ItemStack pk = new ItemStack((ItemLike) Items.WOODEN_PICKAXE);
        pk.getOrCreateTag().putBoolean("alb_pick", true);
        pk.getOrCreateTag().putBoolean("Unbreakable", true);
        pk.setHoverName(Component.literal("Pico de albanil"));
        ListTag en = p.getPersistentData().getList("alb_ench", 10);
        if (!en.isEmpty()) {
            pk.getOrCreateTag().put("Enchantments", (Tag) en.copy());
        }
        if (!(em = EnchantmentHelper.getEnchantments(pk)).containsKey(Enchantments.SILK_TOUCH)) {
            em.merge(Enchantments.BLOCK_EFFICIENCY, 2, Math::max);
            EnchantmentHelper.setEnchantments((Map) em, pk);
        }
        return pk;
    }

    static boolean hasPick(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); ++i) {
            if (!PoliciaAlbanil.isPick(inv.getItem(i))) continue;
            return true;
        }
        return PoliciaAlbanil.isPick(p.containerMenu.getCarried());
    }

    static void effects(ServerPlayer p) {
        int am = p.getPersistentData().getInt("alb_pl") >= 1 ? 1 : 0;
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 25, am, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 25, am, false, false, true));
    }

    static void startPick(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        Inventory inv = p.getInventory();
        int slot = inv.selected;
        ItemStack cur = inv.getItem(slot);
        if (!d.getBoolean("alb_saved")) {
            if (!cur.isEmpty()) {
                d.put("alb_item", (Tag) cur.save(new CompoundTag()));
            } else {
                d.remove("alb_item");
            }
            d.putInt("alb_slot", slot);
            d.putBoolean("alb_saved", true);
            inv.setItem(slot, PoliciaAlbanil.makePick(p));
        }
        PoliciaAlbanil.effects(p);
    }

    static void guardMenu(ServerPlayer p) {
        AbstractContainerMenu m = p.containerMenu;
        if (!(m instanceof ChestMenu || m instanceof ShulkerBoxMenu || m instanceof HopperMenu || m instanceof DispenserMenu)) {
            return;
        }
        for (Slot sl : m.slots) {
            if (sl.container instanceof Inventory || !PoliciaAlbanil.isPick(sl.getItem())) continue;
            sl.set(ItemStack.EMPTY);
            m.broadcastChanges();
            if (PoliciaAlbanil.hasPick(p)) continue;
            p.getInventory().add(PoliciaAlbanil.makePick(p));
        }
    }

    static void keepPick(ServerPlayer p) {
        if (!PoliciaAlbanil.hasPick(p)) {
            p.getInventory().add(PoliciaAlbanil.makePick(p));
        }
        PoliciaAlbanil.effects(p);
    }

    static void endPick(ServerPlayer p) {
        int i;
        CompoundTag d = p.getPersistentData();
        Inventory inv = p.getInventory();
        for (i = 0; i < inv.getContainerSize(); ++i) {
            if (!PoliciaAlbanil.isPick(inv.getItem(i))) continue;
            d.put("alb_ench", (Tag) inv.getItem(i).getEnchantmentTags().copy());
            break;
        }
        for (i = 0; i < inv.getContainerSize(); ++i) {
            if (!PoliciaAlbanil.isPick(inv.getItem(i))) continue;
            inv.setItem(i, ItemStack.EMPTY);
        }
        if (PoliciaAlbanil.isPick(p.containerMenu.getCarried())) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
        p.removeEffect(MobEffects.DIG_SPEED);
        p.removeEffect(MobEffects.DAMAGE_BOOST);
        if (d.getBoolean("alb_saved")) {
            ItemStack orig = d.contains("alb_item") ? ItemStack.of(d.getCompound("alb_item")) : ItemStack.EMPTY;
            int slot = Math.max(0, Math.min(35, d.getInt("alb_slot")));
            d.remove("alb_item");
            d.putBoolean("alb_saved", false);
            if (!orig.isEmpty()) {
                if (inv.getItem(slot).isEmpty()) {
                    inv.setItem(slot, orig);
                } else {
                    inv.placeItemBackInInventory(orig);
                }
            }
        }
    }

    static boolean free(ServerLevel lv, BlockPos bp) {
        BlockState st = lv.getBlockState(bp);
        return st.isAir() || st.canBeReplaced() && st.getFluidState().isEmpty();
    }

    static void put(ServerLevel lv, List<Long> out, BlockPos bp, BlockState st, int flags) {
        if (!PoliciaAlbanil.free(lv, bp)) {
            return;
        }
        lv.setBlock(bp, st, flags);
        out.add(bp.asLong());
        LIVE.add(bp.asLong());
    }

    static BlockPos at(BlockPos o, Direction f, Direction r, int fw, int rt, int up) {
        return o.relative(f, fw).relative(r, rt).above(up);
    }

    static void store(ServerPlayer p, List<Long> out) {
        long[] arr = new long[out.size()];
        for (int i = 0; i < arr.length; ++i) {
            arr[i] = out.get(i);
        }
        CompoundTag d = p.getPersistentData();
        d.putLongArray("alb_w", arr);
        d.putString("alb_wd", p.level().dimension().location().toString());
        d.remove("alb_wb");
        d.remove("alb_cp");
        d.remove("alb_wc");
    }

    static boolean useSingle(ServerPlayer p, int kind) {
        CompoundTag d = p.getPersistentData();
        if (kind == 0) {
            if (d.getInt("alb_pick") > 0 || d.getBoolean("alb_saved")) {
                PoliciaMod.msg(p, "El pico ya esta activo");
                return false;
            }
            d.putInt("alb_pick", PoliciaAlbanil.pickT(p));
            PoliciaAlbanil.startPick(p);
            PoliciaAlbanil.send(p);
            return true;
        }
        if (d.getInt("alb_st") > 0 || d.getLongArray("alb_w").length > 0) {
            PoliciaMod.msg(p, "Ya hay una construccion en pie");
            return false;
        }
        d.putInt("alb_walls", kind == 1 ? 1 : 0);
        d.putInt("alb_house", kind == 2 ? 1 : 0);
        d.putInt("alb_st", kind == 2 ? 700 : 300);
        if (kind == 2) {
            PoliciaAlbanil.buildHouse(p);
        } else {
            PoliciaAlbanil.buildWall(p);
        }
        PoliciaAlbanil.send(p);
        return true;
    }

    static BlockPos rotAt(BlockPos a, int dx, int dz, int steps) {
        for (int k = 0; k < steps; ++k) {
            int nx = -dz;
            int nz = dx;
            dx = nx;
            dz = nz;
        }
        return new BlockPos(a.getX() + dx, a.getY(), a.getZ() + dz);
    }

    static void buildHouse2(ServerPlayer p) {
        BlockEntity z2;
        ServerLevel lv = p.serverLevel();
        CompoundTag d = p.getPersistentData();
        if (d.getLongArray("alb_w").length > 0) {
            PoliciaAlbanil.removeStruct(p);
        }
        Direction f = p.getDirection();
        int steps = (f.getOpposite().get3DDataValue() - Direction.SOUTH.get3DDataValue() + 4) % 4;
        Rotation rot = Rotation.values()[steps];
        BlockPos pp = p.blockPosition();
        BlockPos a = pp.relative(f, 2);
        int top = pp.getY();
        for (int sx = 3; sx <= 24; sx += 3) {
            for (int sz = 3; sz <= 19; sz += 4) {
                BlockPos w = PoliciaAlbanil.rotAt(a, sx - 16, sz - 19, steps);
                top = Math.max(top, lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, w.getX(), w.getZ()));
            }
        }
        if (top - pp.getY() > 8) {
            top = pp.getY();
        }
        int[] cells = PoliciaRef.cells();
        ArrayList<Long> out = new ArrayList<Long>();
        ArrayList<Integer> ids = new ArrayList<Integer>();
        ArrayList<Integer> cix = new ArrayList<Integer>();
        HashMap<Integer, BlockPos> conts = new HashMap<Integer, BlockPos>();
        BlockPos chest = null;
        for (int y = 0; y < 19; ++y) {
            for (int zz = 0; zz < 23; ++zz) {
                for (int x = 0; x < 27; ++x) {
                    BlockPos u;
                    int i = cells[(y * 23 + zz) * 27 + x];
                    if (i < 0) continue;
                    BlockState s = PoliciaRef.state(i).rotate(rot);
                    BlockPos q = PoliciaAlbanil.rotAt(a, x - 16, zz - 19, steps);
                    BlockPos bp = new BlockPos(q.getX(), top + y, q.getZ());
                    if (!PoliciaAlbanil.free(lv, bp)) continue;
                    lv.setBlock(bp, s, 18);
                    out.add(bp.asLong());
                    ids.add(BuiltInRegistries.BLOCK.getId(s.getBlock()));
                    LIVE.add(bp.asLong());
                    int ci = (y * 23 + zz) * 27 + x;
                    cix.add(ci);
                    if (lv.getBlockEntity(bp) instanceof Container) {
                        conts.put(ci, bp);
                    }
                    if (x == 12 && y == 1 && zz == 10) {
                        chest = bp;
                    }
                    if (y != 0) continue;
                    for (int k = 1; k <= 9 && PoliciaAlbanil.free(lv, u = bp.below(k)); ++k) {
                        lv.setBlock(u, s, 18);
                        out.add(u.asLong());
                        ids.add(BuiltInRegistries.BLOCK.getId(s.getBlock()));
                        cix.add(ci);
                        LIVE.add(u.asLong());
                    }
                }
            }
        }
        if (chest != null && d.contains("alb_chest") && (z2 = lv.getBlockEntity(chest)) instanceof ChestBlockEntity) {
            ChestBlockEntity cbe = (ChestBlockEntity) z2;
            cbe.load(d.getCompound("alb_chest").copy());
            cbe.setChanged();
        }
        for (Tag tg : d.getList("alb_cont", 10)) {
            BlockEntity tb;
            CompoundTag e = (CompoundTag) tg;
            BlockPos tp = conts.get(e.getInt("c"));
            if (tp == null || tp.equals(chest) || (tb = lv.getBlockEntity(tp)) == null) continue;
            tb.load(e.getCompound("d").copy());
            tb.setChanged();
        }
        d.remove("alb_cont");
        PoliciaAlbanil.store(p, out);
        int[] arr = new int[ids.size()];
        for (int k = 0; k < arr.length; ++k) {
            arr[k] = ids.get(k);
        }
        d.putIntArray("alb_wb", arr);
        int[] cw = new int[cix.size()];
        for (int k = 0; k < cw.length; ++k) {
            cw[k] = cix.get(k);
        }
        d.putIntArray("alb_wc", cw);
        if (chest != null) {
            d.putLong("alb_cp", chest.asLong());
        }
        d.remove("alb_o");
    }

    static void buildWall(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Direction f = p.getDirection();
        Direction r = f.getClockWise();
        BlockPos o = p.blockPosition();
        ArrayList<Long> out = new ArrayList<Long>();
        for (long l : p.getPersistentData().getLongArray("alb_w")) {
            out.add(l);
        }
        for (int u = -1; u <= 1; ++u) {
            for (int h = 0; h < 3; ++h) {
                PoliciaAlbanil.put(lv, out, PoliciaAlbanil.at(o, f, r, 2, u, h), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }
        PoliciaAlbanil.store(p, out);
    }

    static void buildHouse(ServerPlayer p) {
        BlockPos chest;
        ServerLevel lv = p.serverLevel();
        CompoundTag d = p.getPersistentData();
        Direction f = p.getDirection();
        Direction r = f.getClockWise();
        BlockPos pp = p.blockPosition();
        int top = pp.getY();
        for (int fw = 1; fw <= 6; ++fw) {
            for (int u = -2; u <= 2; ++u) {
                BlockPos c = pp.relative(f, fw).relative(r, u);
                top = Math.max(top, lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()));
            }
        }
        if (top - pp.getY() > 6) {
            top = pp.getY();
        }
        BlockPos o = new BlockPos(pp.getX(), top + 1, pp.getZ());
        ArrayList<Long> out = new ArrayList<Long>();
        BlockState plank = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int fw = 2; fw <= 6; ++fw) {
            for (int u = -2; u <= 2; ++u) {
                boolean corner;
                boolean fl = PoliciaAlbanil.free(lv, PoliciaAlbanil.at(o, f, r, fw, u, -1));
                PoliciaAlbanil.put(lv, out, PoliciaAlbanil.at(o, f, r, fw, u, -1), floor, 3);
                if (fl) {
                    BlockPos s;
                    for (int k = 2; k <= 9 && PoliciaAlbanil.free(lv, s = PoliciaAlbanil.at(o, f, r, fw, u, -k)); ++k) {
                        PoliciaAlbanil.put(lv, out, s, floor, 3);
                    }
                }
                boolean edge = fw == 2 || fw == 6 || u == -2 || u == 2;
                boolean bl = corner = !(fw != 2 && fw != 6 || u != -2 && u != 2);
                if (edge) {
                    for (int h = 0; h < 3; ++h) {
                        boolean win;
                        if (fw == 2 && u == 0 && h < 2) continue;
                        boolean bl2 = win = h == 1 && fw == 4 && (u == -2 || u == 2);
                        BlockState st = corner ? log : (win ? Blocks.GLASS.defaultBlockState() : plank);
                        PoliciaAlbanil.put(lv, out, PoliciaAlbanil.at(o, f, r, fw, u, h), st, 3);
                    }
                }
                PoliciaAlbanil.put(lv, out, PoliciaAlbanil.at(o, f, r, fw, u, 3), fw == 4 && u == 0 ? Blocks.GLOWSTONE.defaultBlockState() : plank, 3);
            }
        }
        BlockState door = (BlockState) Blocks.OAK_DOOR.defaultBlockState().setValue((Property) DoorBlock.FACING, (Comparable) f);
        PoliciaAlbanil.put(lv, out, PoliciaAlbanil.at(o, f, r, 2, 0, 0), (BlockState) door.setValue((Property) DoorBlock.HALF, (Comparable) DoubleBlockHalf.LOWER), 18);
        PoliciaAlbanil.put(lv, out, PoliciaAlbanil.at(o, f, r, 2, 0, 1), (BlockState) door.setValue((Property) DoorBlock.HALF, (Comparable) DoubleBlockHalf.UPPER), 18);
        PoliciaAlbanil.put(lv, out, PoliciaAlbanil.at(o, f, r, 1, 0, -1), Blocks.TORCH.defaultBlockState(), 3);
        BlockPos foot = PoliciaAlbanil.at(o, f, r, 4, -1, 0);
        BlockPos head = foot.relative(f);
        if (PoliciaAlbanil.free(lv, foot) && PoliciaAlbanil.free(lv, head)) {
            BlockState bed = (BlockState) Blocks.RED_BED.defaultBlockState().setValue((Property) BedBlock.FACING, (Comparable) f);
            PoliciaAlbanil.put(lv, out, head, (BlockState) bed.setValue((Property) BedBlock.PART, (Comparable) BedPart.HEAD), 18);
            PoliciaAlbanil.put(lv, out, foot, (BlockState) bed.setValue((Property) BedBlock.PART, (Comparable) BedPart.FOOT), 18);
        }
        if (PoliciaAlbanil.free(lv, chest = PoliciaAlbanil.at(o, f, r, 5, 1, 0))) {
            BlockEntity blockEntity;
            PoliciaAlbanil.put(lv, out, chest, (BlockState) Blocks.CHEST.defaultBlockState().setValue((Property) ChestBlock.FACING, (Comparable) f.getOpposite()), 3);
            if (d.contains("alb_chest") && (blockEntity = lv.getBlockEntity(chest)) instanceof ChestBlockEntity) {
                ChestBlockEntity cbe = (ChestBlockEntity) blockEntity;
                cbe.load(d.getCompound("alb_chest").copy());
                cbe.setChanged();
            }
        }
        d.putLong("alb_o", o.asLong());
        d.putInt("alb_f", f.get3DDataValue());
        PoliciaAlbanil.store(p, out);
        PoliciaAlbanil.restoreInner(p, lv, o, f, r);
    }

    static AABB inner(BlockPos o, Direction f, Direction r) {
        BlockPos a = PoliciaAlbanil.at(o, f, r, 3, -1, 0);
        BlockPos b = PoliciaAlbanil.at(o, f, r, 5, 1, 2);
        return new AABB((double) Math.min(a.getX(), b.getX()), (double) Math.min(a.getY(), b.getY()), (double) Math.min(a.getZ(), b.getZ()), (double) (Math.max(a.getX(), b.getX()) + 1), (double) (Math.max(a.getY(), b.getY()) + 1), (double) (Math.max(a.getZ(), b.getZ()) + 1));
    }

    static void captureInner(ServerPlayer p, ServerLevel lv, Set<Long> ours) {
        CompoundTag d = p.getPersistentData();
        BlockPos o = BlockPos.of(d.getLong("alb_o"));
        Direction f = Direction.from3DDataValue(d.getInt("alb_f"));
        Direction r = f.getClockWise();
        ListTag blocks = new ListTag();
        ListTag items = new ListTag();
        ArrayList<BlockPos> clear = new ArrayList<BlockPos>();
        for (int fw = 3; fw <= 5; ++fw) {
            for (int u = -1; u <= 1; ++u) {
                for (int h = 0; h <= 2; ++h) {
                    BlockState st;
                    BlockPos bp = PoliciaAlbanil.at(o, f, r, fw, u, h);
                    if (ours.contains(bp.asLong()) || (st = lv.getBlockState(bp)).isAir() || !st.getFluidState().isEmpty() || st.getDestroySpeed((BlockGetter) lv, bp) < 0.0f) continue;
                    CompoundTag e = new CompoundTag();
                    e.putInt("fw", fw);
                    e.putInt("u", u);
                    e.putInt("h", h);
                    e.put("st", (Tag) NbtUtils.writeBlockState(st));
                    BlockEntity be = lv.getBlockEntity(bp);
                    if (be != null) {
                        e.put("be", (Tag) be.saveWithoutMetadata());
                        if (be instanceof Container) {
                            Container c = (Container) be;
                            c.clearContent();
                        }
                    }
                    blocks.add(e);
                    clear.add(bp);
                }
            }
        }
        for (BlockPos bp : clear) {
            lv.setBlock(bp, Blocks.AIR.defaultBlockState(), 18);
        }
        for (ItemEntity ie : lv.getEntitiesOfClass(ItemEntity.class, PoliciaAlbanil.inner(o, f, r))) {
            CompoundTag e = new CompoundTag();
            e.put("item", (Tag) ie.getItem().save(new CompoundTag()));
            e.putDouble("dx", ie.getX() - ((double) o.getX() + 0.5));
            e.putDouble("dy", ie.getY() - (double) o.getY());
            e.putDouble("dz", ie.getZ() - ((double) o.getZ() + 0.5));
            items.add(e);
            ie.discard();
        }
        CompoundTag in = new CompoundTag();
        in.put("blocks", (Tag) blocks);
        in.put("items", (Tag) items);
        in.putInt("dir", f.get3DDataValue());
        d.put("alb_inner", (Tag) in);
        d.remove("alb_o");
    }

    static void restoreInner(ServerPlayer p, ServerLevel lv, BlockPos o, Direction f, Direction r) {
        CompoundTag e;
        CompoundTag in = p.getPersistentData().getCompound("alb_inner");
        if (in.isEmpty()) {
            return;
        }
        int steps = ((f.get3DDataValue() - in.getInt("dir")) % 4 + 4) % 4;
        Rotation rot = Rotation.values()[steps];
        for (Tag t : in.getList("blocks", 10)) {
            BlockEntity be;
            e = (CompoundTag) t;
            BlockPos bp = PoliciaAlbanil.at(o, f, r, e.getInt("fw"), e.getInt("u"), e.getInt("h"));
            if (!PoliciaAlbanil.free(lv, bp)) continue;
            BlockState st = NbtUtils.readBlockState((HolderGetter) BuiltInRegistries.BLOCK.asLookup(), e.getCompound("st")).rotate(rot);
            lv.setBlock(bp, st, 18);
            if (!e.contains("be") || (be = lv.getBlockEntity(bp)) == null) continue;
            be.load(e.getCompound("be"));
            be.setChanged();
        }
        for (Tag t : in.getList("items", 10)) {
            e = (CompoundTag) t;
            ItemStack st = ItemStack.of(e.getCompound("item"));
            if (st.isEmpty()) continue;
            double dx = e.getDouble("dx");
            double dz = e.getDouble("dz");
            for (int k = 0; k < steps; ++k) {
                double nx = -dz;
                double nz = dx;
                dx = nx;
                dz = nz;
            }
            ItemEntity ie = new ItemEntity((Level) lv, (double) o.getX() + 0.5 + dx, (double) o.getY() + e.getDouble("dy"), (double) o.getZ() + 0.5 + dz, st);
            ie.setDeltaMovement(0.0, 0.0, 0.0);
            lv.addFreshEntity((Entity) ie);
        }
        p.getPersistentData().remove("alb_inner");
    }

    static void removeStruct(ServerPlayer p) {
        int[] wb;
        CompoundTag d = p.getPersistentData();
        long[] arr = d.getLongArray("alb_w");
        d.putInt("alb_walls", 0);
        if (arr.length == 0) {
            d.remove("alb_o");
            return;
        }
        ServerLevel lv = p.server.getLevel(ResourceKey.create((ResourceKey) Registries.DIMENSION, new ResourceLocation(d.getString("alb_wd"))));
        if (lv == null) {
            lv = p.serverLevel();
        }
        if (d.contains("alb_o")) {
            HashSet<Long> ours = new HashSet<Long>();
            for (long l : arr) {
                ours.add(l);
            }
            PoliciaAlbanil.captureInner(p, lv, ours);
        }
        boolean big = (wb = d.getIntArray("alb_wb")).length == arr.length && arr.length > 0;
        long cp = d.contains("alb_cp") ? d.getLong("alb_cp") : Long.MIN_VALUE;
        int[] wc = d.getIntArray("alb_wc");
        ListTag sv = new ListTag();
        for (int n = 0; n < arr.length; ++n) {
            Container ct;
            long l = arr[n];
            BlockPos bp = BlockPos.of(l);
            LIVE.remove(l);
            BlockState st = lv.getBlockState(bp);
            if (big) {
                if (BuiltInRegistries.BLOCK.getId(st.getBlock()) != wb[n]) continue;
                BlockEntity be = lv.getBlockEntity(bp);
                if (be instanceof Container) {
                    ct = (Container) be;
                    if (l == cp && be instanceof ChestBlockEntity) {
                        ChestBlockEntity cb = (ChestBlockEntity) be;
                        CompoundTag keep = new CompoundTag();
                        keep.put("Items", (Tag) cb.saveWithoutMetadata().getList("Items", 10).copy());
                        d.put("alb_chest", (Tag) keep);
                        cb.clearContent();
                    } else {
                        ListTag its = be.saveWithoutMetadata().getList("Items", 10);
                        if (!its.isEmpty()) {
                            if (wc.length == arr.length) {
                                CompoundTag cd = new CompoundTag();
                                cd.put("Items", (Tag) its.copy());
                                CompoundTag en = new CompoundTag();
                                en.putInt("c", wc[n]);
                                en.put("d", (Tag) cd);
                                sv.add(en);
                            } else {
                                Containers.dropContents((Level) lv, bp, (Container) ct);
                            }
                        }
                        ct.clearContent();
                    }
                }
                lv.setBlock(bp, Blocks.AIR.defaultBlockState(), 18);
                continue;
            }
            if (!MINE.contains(st.getBlock())) continue;
            BlockEntity cbCheck;
            if (st.is(Blocks.CHEST) && (cbCheck = lv.getBlockEntity(bp)) instanceof ChestBlockEntity) {
                ChestBlockEntity cbe = (ChestBlockEntity) cbCheck;
                CompoundTag full = cbe.saveWithoutMetadata();
                CompoundTag keep = new CompoundTag();
                keep.put("Items", (Tag) full.getList("Items", 10).copy());
                d.put("alb_chest", (Tag) keep);
                cbe.clearContent();
            }
            lv.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
        }
        if (big) {
            d.put("alb_cont", (Tag) sv);
        }
        d.remove("alb_w");
        d.remove("alb_wb");
        d.remove("alb_wc");
        d.remove("alb_cp");
        lv.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    static void reset(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("alb_pick") > 0 || d.getBoolean("alb_saved")) {
            d.putInt("alb_pick", 0);
            PoliciaAlbanil.endPick(p);
        }
        if (d.getInt("alb_st") > 0 || d.getLongArray("alb_w").length > 0) {
            d.putInt("alb_st", 0);
            PoliciaAlbanil.removeStruct(p);
        }
        if (d.getInt("alb_ht") > 0) {
            d.putInt("alb_ht", 0);
            PoliciaAyudante.clear(p);
        }
        if (d.getInt("alb_lamp") > 0) {
            d.putInt("alb_lamp", 0);
        }
        PoliciaAlbanil.lampClear(p);
        PoliciaTractor.retire(p);
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        int ht;
        int c;
        int lt;
        int pt;
        Player player;
        if (e.phase != TickEvent.Phase.END || !((player = e.player) instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer p = (ServerPlayer) player;
        boolean alb = PoliciaAlbanil.isAlb(p);
        CompoundTag d = p.getPersistentData();
        if (!alb && d.getInt("alb_st") <= 0 && d.getInt("alb_pick") <= 0) {
            return;
        }
        boolean ch = false;
        if (d.getInt("alb_pick") > 0 && p.containerMenu != p.inventoryMenu) {
            PoliciaAlbanil.guardMenu(p);
        }
        if ((pt = d.getInt("alb_pick")) > 0) {
            d.putInt("alb_pick", --pt);
            if (pt > 0) {
                PoliciaAlbanil.keepPick(p);
            } else {
                PoliciaAlbanil.endPick(p);
                if (alb) {
                    d.putInt("alb_c0", 160);
                }
                PoliciaMod.msg(p, "El pico se retiro");
                ch = true;
            }
        } else if (d.getInt("alb_c0") > 0) {
            int c2 = d.getInt("alb_c0") - 1;
            d.putInt("alb_c0", c2);
            if (c2 == 0) {
                PoliciaMod.msg(p, "Pico listo");
                ch = true;
            }
        }
        int st = d.getInt("alb_st");
        if (st > 0) {
            d.putInt("alb_st", --st);
            if (st == 0) {
                PoliciaAlbanil.removeStruct(p);
                if (alb) {
                    d.putInt("alb_c1", d.getInt("alb_house") == 1 ? 800 : 100);
                }
                PoliciaMod.msg(p, "La construccion desaparecio");
                ch = true;
            }
        } else if (d.getInt("alb_c1") > 0) {
            int c3 = d.getInt("alb_c1") - 1;
            d.putInt("alb_c1", c3);
            if (c3 == 0) {
                PoliciaMod.msg(p, "Construccion lista");
                ch = true;
            }
        }
        if ((lt = d.getInt("alb_lamp")) > 0) {
            d.putInt("alb_lamp", --lt);
            if (lt % 4 == 0) {
                PoliciaAlbanil.lampTick(p);
            }
            if (lt == 0) {
                PoliciaAlbanil.lampClear(p);
                d.putInt("alb_lcd", 400);
                PoliciaMod.msg(p, "La linterna se apago");
                ch = true;
            }
        } else if (d.getInt("alb_lcd") > 0) {
            c = d.getInt("alb_lcd") - 1;
            d.putInt("alb_lcd", c);
            if (c == 0) {
                PoliciaMod.msg(p, "Linterna lista");
                ch = true;
            }
        }
        if (d.getInt("alb_tact") == 0 && d.getInt("alb_tcd") > 0) {
            c = d.getInt("alb_tcd") - 1;
            d.putInt("alb_tcd", c);
            if (c == 0) {
                PoliciaMod.msg(p, "Tractor listo");
                ch = true;
            }
        }
        if ((ht = d.getInt("alb_ht")) > 0) {
            d.putInt("alb_ht", --ht);
            if (ht == 200 && PoliciaAyudante.hasItems(p)) {
                PoliciaMod.msg(p, "Tus ayudantes se retiran en 10 s. Pulsa U para recoger lo que llevan");
            }
            if (ht == 0 || p.tickCount % 20 == 0 && PoliciaAyudante.mine(p).isEmpty()) {
                d.putInt("alb_ht", 0);
                PoliciaAyudante.clear(p);
                d.putInt("alb_hcd", 600);
                PoliciaMod.msg(p, PoliciaAyudante.hasItems(p) ? "Los ayudantes se retiraron y guardan lo que picaron. Pulsa U para recogerlo" : "Los ayudantes se retiraron");
                ch = true;
            }
        } else if (d.getInt("alb_hcd") > 0) {
            int c4 = d.getInt("alb_hcd") - 1;
            d.putInt("alb_hcd", c4);
            if (c4 == 0) {
                PoliciaMod.msg(p, "Ayudantes listos");
                ch = true;
            }
        }
        if (alb && (ch || p.tickCount % 20 == 0)) {
            PoliciaAlbanil.send(p);
        }
    }

    @SubscribeEvent
    public void toss(ItemTossEvent e) {
        if (PoliciaAlbanil.isPick(e.getEntity().getItem())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent e) {
        if (LIVE.contains(e.getPos().asLong())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void frame(PlayerInteractEvent.EntityInteract e) {
        if (!PoliciaAlbanil.isPick(e.getItemStack())) {
            return;
        }
        if (e.getTarget() instanceof ItemFrame || e.getTarget() instanceof ArmorStand) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void death(LivingDeathEvent e) {
        ServerPlayer p;
        LivingEntity livingEntity = e.getEntity();
        if (livingEntity instanceof ServerPlayer && PoliciaAlbanil.isAlb(p = (ServerPlayer) livingEntity)) {
            PoliciaAlbanil.reset(p);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Player player = e.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer p = (ServerPlayer) player;
            PoliciaAlbanil.reset(p);
            PoliciaAyudante.BANKS.remove(p.getUUID());
            PoliciaTractor.INVS.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        Player player = e.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer p = (ServerPlayer) player;
            PoliciaAlbanil.reset(p);
            if (PoliciaAlbanil.isAlb(p)) {
                PoliciaAlbanil.send(p);
            }
        }
    }

    public static class AlbSync {
        int lv;
        int sel;
        int pickT;
        int structT;
        int c0;
        int c1;
        int walls;
        int hl;
        int ht;
        int hc;
        int mode;
        int lu;
        int lt;
        int lc;
        int zl;
        int zw;
        int tu;
        int tc;
        int ta;
        int pl;

        static void enc(AlbSync m, FriendlyByteBuf b) {
            b.writeVarInt(m.lv);
            b.writeVarInt(m.sel);
            b.writeVarInt(m.pickT);
            b.writeVarInt(m.structT);
            b.writeVarInt(m.c0);
            b.writeVarInt(m.c1);
            b.writeVarInt(m.walls);
            b.writeVarInt(m.hl);
            b.writeVarInt(m.ht);
            b.writeVarInt(m.hc);
            b.writeVarInt(m.mode);
            b.writeVarInt(m.lu);
            b.writeVarInt(m.lt);
            b.writeVarInt(m.lc);
            b.writeVarInt(m.zl);
            b.writeVarInt(m.zw);
            b.writeVarInt(m.tu);
            b.writeVarInt(m.tc);
            b.writeVarInt(m.ta);
            b.writeVarInt(m.pl);
        }

        static AlbSync dec(FriendlyByteBuf b) {
            AlbSync m = new AlbSync();
            m.lv = b.readVarInt();
            m.sel = b.readVarInt();
            m.pickT = b.readVarInt();
            m.structT = b.readVarInt();
            m.c0 = b.readVarInt();
            m.c1 = b.readVarInt();
            m.walls = b.readVarInt();
            m.hl = b.readVarInt();
            m.ht = b.readVarInt();
            m.hc = b.readVarInt();
            m.mode = b.readVarInt();
            m.lu = b.readVarInt();
            m.lt = b.readVarInt();
            m.lc = b.readVarInt();
            m.zl = b.readVarInt();
            m.zw = b.readVarInt();
            m.tu = b.readVarInt();
            m.tc = b.readVarInt();
            m.ta = b.readVarInt();
            m.pl = b.readVarInt();
            return m;
        }

        static void handle(AlbSync m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                CL = m;
            });
            c.get().setPacketHandled(true);
        }
    }
}
