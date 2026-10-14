package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Eventos aleatorios según el oficio (cada 10-20 minutos con el modo activo):
// policía: persecución de un ladrón; albañil: derrumbe que hay que despejar;
// herrero: pedido urgente de armas para la guardia; ladrón: un mercader
// adinerado al que robarle los bolsillos.
public class PoliciaEventos {
    static final String[] NAME = new String[]{"", "Persecución", "Derrumbe", "Pedido urgente", "Oportunidad de robo"};
    static final int[] TIME = new int[]{0, 3600, 3600, 6000, 3600};
    static final int[] XP = new int[]{0, 10, 10, 12, 10};
    static final int[] EM = new int[]{0, 6, 6, 10, 4};
    static final Item[] ORDER = new Item[]{Items.IRON_SWORD, Items.IRON_CHESTPLATE, Items.IRON_HELMET, Items.IRON_AXE, Items.SHIELD, Items.IRON_BOOTS};
    static final int[] ORDER_N = new int[]{3, 1, 2, 2, 2, 2};

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaEventos());
    }

    static int jobKind(ServerPlayer p) {
        return switch (PoliciaMod.job(p)) {
            case "policia" -> 1;
            case "albanil" -> 2;
            case "herrero" -> 3;
            case "ladron" -> 4;
            default -> 0;
        };
    }

    static BlockPos ground(ServerLevel sl, ServerPlayer p, int min, int max) {
        for (int tries = 0; tries < 12; ++tries) {
            double a = p.getRandom().nextDouble() * Math.PI * 2.0;
            int r = min + p.getRandom().nextInt(max - min + 1);
            int x = (int) Math.floor(p.getX() + Math.cos(a) * r);
            int z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
            BlockPos top = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (Math.abs(top.getY() - p.getY()) > 12 || !sl.getFluidState(top.below()).isEmpty() || !sl.getFluidState(top).isEmpty()) continue;
            return top;
        }
        return null;
    }

    static void announce(ServerPlayer p, int k, String sub) {
        PoliciaGolpe.title(p, "§6" + NAME[k], "§f" + sub);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
        p.sendSystemMessage(Component.literal("§6[Evento] " + NAME[k] + ": §f" + sub));
    }

    static boolean start(ServerPlayer p, int k) {
        ServerLevel sl = p.serverLevel();
        CompoundTag d = p.getPersistentData();
        String sub;
        if (k == 1) {
            BlockPos at = PoliciaEventos.ground(sl, p, 14, 22);
            if (at == null) return false;
            PoliciaMision.Pj e = PoliciaMision.spawn(sl, null, 0, at);
            e.fleeT = TIME[1] + 200;
            e.setCustomName(Component.literal("Ladrón fugitivo"));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, TIME[1], 0, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, TIME[1], 0, false, false));
            e.getPersistentData().putUUID("ev_for", p.getUUID());
            d.putUUID("ev_e", e.getUUID());
            d.putLong("ev_last", at.asLong());
            sub = "Un ladrón asaltó una tienda cerca tuyo: atrapalo o eliminalo";
        } else if (k == 2) {
            BlockPos at = PoliciaEventos.ground(sl, p, 8, 14);
            if (at == null) return false;
            java.util.ArrayList<Long> list = new java.util.ArrayList<Long>();
            for (int x = -1; x <= 1; ++x) {
                for (int z = -1; z <= 1; ++z) {
                    int h = (x == 0 && z == 0) ? 3 : ((x == 0 || z == 0) ? 2 : 1);
                    for (int y = 0; y < h; ++y) {
                        BlockPos bp = at.offset(x, y, z);
                        if (!sl.getBlockState(bp).canBeReplaced()) continue;
                        Block b = p.getRandom().nextInt(3) == 0 ? Blocks.GRAVEL : (p.getRandom().nextBoolean() ? Blocks.COBBLESTONE : Blocks.CRACKED_STONE_BRICKS);
                        sl.setBlockAndUpdate(bp, b.defaultBlockState());
                        list.add(bp.asLong());
                    }
                }
            }
            if (list.isEmpty()) return false;
            d.putLongArray("ev_b", list);
            d.putInt("ev_n", list.size());
            sl.playSound(null, at, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0f, 0.7f);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 20, 1.0, 1.0, 1.0, 0.02);
            sub = "Se derrumbó una obra en (" + at.getX() + ", " + at.getY() + ", " + at.getZ() + "): despejá los " + list.size() + " escombros";
        } else if (k == 3) {
            int i = p.getRandom().nextInt(ORDER.length);
            d.putInt("ev_i", i);
            sub = "La guardia necesita " + ORDER_N[i] + " x " + new ItemStack(ORDER[i]).getHoverName().getString() + " en 5 minutos (tenelos en el inventario)";
        } else {
            BlockPos at = PoliciaEventos.ground(sl, p, 14, 22);
            if (at == null) return false;
            WanderingTrader t = EntityType.WANDERING_TRADER.create(sl);
            if (t == null) return false;
            t.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getRandom().nextFloat() * 360.0f, 0.0f);
            t.setCustomName(Component.literal("Mercader adinerado"));
            t.setCustomNameVisible(true);
            t.setDespawnDelay(TIME[4] + 200);
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, TIME[4], 0, false, false));
            t.getPersistentData().putUUID("ev_for", p.getUUID());
            sl.addFreshEntity(t);
            d.putUUID("ev_e", t.getUUID());
            sub = "Un mercader adinerado pasa cerca: robale los bolsillos (agachado, por detrás)";
        }
        d.putInt("ev_k", k);
        d.putInt("ev_t", TIME[k]);
        PoliciaEventos.announce(p, k, sub);
        return true;
    }

    static void end(ServerPlayer p, boolean ok) {
        CompoundTag d = p.getPersistentData();
        int k = d.getInt("ev_k");
        if (k == 0) return;
        ServerLevel sl = p.serverLevel();
        if (d.hasUUID("ev_e")) {
            Entity e = sl.getEntity(d.getUUID("ev_e"));
            if (e != null && e.isAlive() && (k == 4 || !ok)) {
                e.discard();
            }
        }
        if (k == 2 && !ok) {
            for (long v : d.getLongArray("ev_b")) {
                BlockPos bp = BlockPos.of(v);
                BlockState s = sl.getBlockState(bp);
                if (s.is(Blocks.GRAVEL) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.CRACKED_STONE_BRICKS)) {
                    sl.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (String key : new String[]{"ev_k", "ev_t", "ev_e", "ev_b", "ev_n", "ev_i", "ev_last"}) {
            d.remove(key);
        }
        d.putInt("ev_next", 12000 + p.getRandom().nextInt(12000));
        if (ok) {
            d.putInt("pol_xp", PoliciaMod.xp(p) + XP[k]);
            PoliciaBoss.give(p, new ItemStack((ItemLike) Items.EMERALD, EM[k]));
            PoliciaGolpe.title(p, "§a¡Evento superado!", "§f" + NAME[k] + ": +" + XP[k] + " XP y " + EM[k] + " esmeraldas");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            PoliciaMod.sync(p);
        } else {
            PoliciaMod.msg(p, "§7Evento terminado: " + NAME[k] + " (se acabó el tiempo)");
        }
    }

    // Carterismo exitoso sobre el mercader del evento.
    static void picked(ServerPlayer p, LivingEntity t) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("ev_k") == 4 && d.hasUUID("ev_e") && t.getUUID().equals(d.getUUID("ev_e"))) {
            PoliciaBoss.give(p, new ItemStack((ItemLike) Items.DIAMOND, 2));
            PoliciaBoss.give(p, new ItemStack((ItemLike) Items.EMERALD, 8));
            p.sendSystemMessage(Component.literal("§aEl mercader llevaba 2 diamantes y 8 esmeraldas en el bolsillo"));
            PoliciaEventos.end(p, true);
        }
    }

    static int count(ServerPlayer p, Item it) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(it)) n += s.getCount();
        }
        return n;
    }

    @SubscribeEvent
    public void brk(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p)) return;
        CompoundTag d = p.getPersistentData();
        if (d.getInt("ev_k") != 2) return;
        long v = e.getPos().asLong();
        for (long x : d.getLongArray("ev_b")) {
            if (x != v) continue;
            int n = d.getInt("ev_n") - 1;
            d.putInt("ev_n", n);
            if (n <= 0) {
                PoliciaEventos.end(p, true);
            } else {
                PoliciaMod.msg(p, "Escombros: quedan " + n);
            }
            return;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        int k = d.getInt("ev_k");
        ServerLevel sl = p.serverLevel();
        if (k == 0) {
            int job = PoliciaEventos.jobKind(p);
            if (job == 0 || !PoliciaMod.on(p) || sl.dimension() != Level.OVERWORLD || p.isCreative() || p.isSpectator()) {
                return;
            }
            if (!d.contains("ev_next")) {
                d.putInt("ev_next", 12000 + p.getRandom().nextInt(12000));
            }
            int n = d.getInt("ev_next") - 20;
            d.putInt("ev_next", n);
            if (n <= 0 && !PoliciaEventos.start(p, job)) {
                d.putInt("ev_next", 1200);
            }
            return;
        }
        int t = d.getInt("ev_t") - 20;
        d.putInt("ev_t", t);
        if (k == 1 && d.hasUUID("ev_e")) {
            Entity en = sl.getEntity(d.getUUID("ev_e"));
            BlockPos last = BlockPos.of(d.getLong("ev_last"));
            if (en != null && en.isAlive()) {
                d.putLong("ev_last", en.blockPosition().asLong());
            } else if (en != null || p.blockPosition().distSqr(last) < 48 * 48) {
                // muerto o arrestado (desaparecido con el jugador cerca)
                PoliciaEventos.end(p, true);
                return;
            }
        }
        if (k == 3) {
            int i = d.getInt("ev_i");
            if (PoliciaEventos.count(p, ORDER[i]) >= ORDER_N[i]) {
                int left = ORDER_N[i];
                for (ItemStack s : p.getInventory().items) {
                    if (left <= 0) break;
                    if (!s.is(ORDER[i])) continue;
                    int c = Math.min(left, s.getCount());
                    s.shrink(c);
                    left -= c;
                }
                p.sendSystemMessage(Component.literal("§aEntregaste el pedido a la guardia"));
                PoliciaEventos.end(p, true);
                return;
            }
        }
        if (k == 4 && d.hasUUID("ev_e")) {
            Entity en = sl.getEntity(d.getUUID("ev_e"));
            if (en != null && !en.isAlive()) {
                PoliciaEventos.end(p, false);
                return;
            }
        }
        if (t <= 0) {
            PoliciaEventos.end(p, false);
            return;
        }
        if (t % 200 == 0) {
            String extra = k == 2 ? " · quedan " + d.getInt("ev_n") + " escombros" : (k == 3 ? " · " + PoliciaEventos.count(p, ORDER[d.getInt("ev_i")]) + "/" + ORDER_N[d.getInt("ev_i")] : "");
            PoliciaMod.msg(p, "§6" + NAME[k] + "§f: " + t / 1200 + ":" + String.format("%02d", t % 1200 / 20) + extra);
        }
    }
}
