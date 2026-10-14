package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Pedidos de construcción de los aldeanos para el albañil: agachado + clic
// derecho a un aldeano. Se construye cerca del aldeano (32 bloques) y se
// cobra volviendo a hablarle cuando está terminado.
public class PoliciaPedido {
    static final int R = 32;
    static final int TIME = 24000;
    static final String[] NAME = new String[]{"", "Casa de 5x5 con puerta", "Corral para mis animales", "Torre de vigilancia", "Huerta", "Muralla de piedra", "Iluminar la aldea"};
    static final String[] ASK = new String[]{"",
            "Necesito una casita: unos 60 bloques y una puerta. ¡Haceme una casa de 5x5 con puerta!",
            "Mis vacas se escapan. Haceme un corral: 20 cercas y una puerta de cerca.",
            "Queremos ver venir a los saqueadores. Levantá una torre de al menos 8 bloques de alto (40 bloques).",
            "Tengo hambre. Plantame una huerta de 12 cultivos.",
            "Los zombis entran de noche. Construí una muralla de 50 bloques de piedra o ladrillo.",
            "Está muy oscuro de noche. Poné 10 antorchas, faroles o lámparas."};
    static final int[] EM = new int[]{0, 10, 8, 10, 6, 10, 5};
    static final int[] XP = new int[]{0, 10, 8, 10, 6, 10, 6};
    static final Item[] ICON = new Item[]{Items.AIR, Items.OAK_DOOR, Items.OAK_FENCE, Items.STONE_BRICKS, Items.WHEAT, Items.COBBLESTONE_WALL, Items.LANTERN};

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaPedido());
    }

    static int need(int k) {
        return switch (k) {
            case 1 -> 60;
            case 2 -> 20;
            case 3 -> 40;
            case 4 -> 12;
            case 5 -> 50;
            default -> 10;
        };
    }

    // Progreso: {actual, necesario} del requisito principal y el extra.
    static String progress(CompoundTag d) {
        int k = d.getInt("ord_k");
        int n = d.getInt("ord_n");
        return switch (k) {
            case 1 -> "Bloques: " + Math.min(n, 60) + "/60 · Puerta: " + Math.min(1, d.getInt("ord_door")) + "/1";
            case 2 -> "Cercas: " + Math.min(20, d.getInt("ord_fence")) + "/20 · Puerta de cerca: " + Math.min(1, d.getInt("ord_gate")) + "/1";
            case 3 -> "Bloques: " + Math.min(n, 40) + "/40 · Altura: " + Math.min(8, PoliciaPedido.height(d)) + "/8";
            case 4 -> "Cultivos: " + Math.min(12, d.getInt("ord_crop")) + "/12";
            case 5 -> "Piedra o ladrillo: " + Math.min(50, d.getInt("ord_stone")) + "/50";
            default -> "Luces: " + Math.min(10, d.getInt("ord_light")) + "/10";
        };
    }

    static int height(CompoundTag d) {
        return d.contains("ord_ymin") ? d.getInt("ord_ymax") - d.getInt("ord_ymin") + 1 : 0;
    }

    static boolean complete(CompoundTag d) {
        int k = d.getInt("ord_k");
        return switch (k) {
            case 1 -> d.getInt("ord_n") >= 60 && d.getInt("ord_door") >= 1;
            case 2 -> d.getInt("ord_fence") >= 20 && d.getInt("ord_gate") >= 1;
            case 3 -> d.getInt("ord_n") >= 40 && PoliciaPedido.height(d) >= 8;
            case 4 -> d.getInt("ord_crop") >= 12;
            case 5 -> d.getInt("ord_stone") >= 50;
            case 6 -> d.getInt("ord_light") >= 10;
            default -> false;
        };
    }

    static List<Integer> offers(ServerPlayer p, Villager v) {
        Random r = new Random(v.getUUID().getMostSignificantBits() ^ PoliciaLadron.day() * 977L);
        List<Integer> all = new ArrayList<Integer>();
        for (int i = 1; i < NAME.length; ++i) all.add(i);
        java.util.Collections.shuffle(all, r);
        return all.subList(0, 3);
    }

    static void clear(CompoundTag d) {
        for (String k : new String[]{"ord_k", "ord_x", "ord_y", "ord_z", "ord_t", "ord_n", "ord_door", "ord_fence", "ord_gate", "ord_crop", "ord_stone", "ord_light", "ord_ymin", "ord_ymax"}) {
            d.remove(k);
        }
    }

    @SubscribeEvent
    public void interact(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof Villager v)) {
            return;
        }
        if (!p.isShiftKeyDown() || !"albanil".equals(PoliciaMod.job(p)) || v.isBaby() || v.getPersistentData().hasUUID("insp_for") || v.getPersistentData().hasUUID("ev_for")) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        PoliciaPedido.open(p, v);
    }

    static void open(ServerPlayer sp, Villager v) {
        CompoundTag d = sp.getPersistentData();
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; ++i) {
            c.setItem(i, PoliciaBoss.pane());
        }
        int k = d.getInt("ord_k");
        if (k > 0) {
            boolean ok = PoliciaPedido.complete(d);
            int t = d.getInt("ord_t");
            c.setItem(13, PoliciaBoss.named(ICON[k], "Pedido: " + NAME[k], ASK[k], PoliciaPedido.progress(d), "Tiempo: " + t / 1200 + ":" + String.format("%02d", t % 1200 / 20), "Construí a menos de " + R + " bloques de " + d.getInt("ord_x") + ", " + d.getInt("ord_z")));
            c.setItem(11, ok ? PoliciaBoss.named(Items.EMERALD, "Cobrar", EM[k] + " esmeraldas y " + XP[k] + " XP de oficio") : PoliciaBoss.named(Items.CLOCK, "Todavía no está terminado", PoliciaPedido.progress(d)));
            c.setItem(15, PoliciaBoss.named(Items.BARRIER, "Abandonar el pedido", "Podés pedir otro después"));
        } else {
            long left = d.getLong("ord_cd") - sp.serverLevel().getGameTime();
            if (left > 0) {
                c.setItem(13, PoliciaBoss.named(Items.CLOCK, "Descansá un poco", "Podés tomar otro pedido en " + (left / 1200L + 1L) + " min"));
            } else {
                List<Integer> o = PoliciaPedido.offers(sp, v);
                int[] slots = new int[]{11, 13, 15};
                for (int i = 0; i < 3; ++i) {
                    int ok = o.get(i);
                    c.setItem(slots[i], PoliciaBoss.named(ICON[ok], "Pedido: " + NAME[ok], ASK[ok], "Pago: " + EM[ok] + " esmeraldas y " + XP[ok] + " XP", "Plazo: 20 minutos", "Clic para aceptar"));
                }
            }
        }
        c.setItem(22, PoliciaPedido.reformCard(sp, v));
        PoliciaBoss.openMenu3(sp, "Pedidos del aldeano", c, (p, slot) -> {
            CompoundTag dd = p.getPersistentData();
            if (slot == 22) {
                PoliciaPedido.reformClick(p, v);
                return;
            }
            int kk = dd.getInt("ord_k");
            if (kk > 0) {
                if (slot == 15) {
                    p.closeContainer();
                    PoliciaPedido.clear(dd);
                    dd.putLong("ord_cd", p.serverLevel().getGameTime() + 2400L);
                    PoliciaMod.msg(p, "Abandonaste el pedido");
                } else if (slot == 11 && PoliciaPedido.complete(dd)) {
                    p.closeContainer();
                    BlockPos at = new BlockPos(dd.getInt("ord_x"), dd.getInt("ord_y"), dd.getInt("ord_z"));
                    if (v.blockPosition().distSqr(at) > (double) (R * R) * 2.0) {
                        PoliciaMod.msg(p, "Cobrale a un aldeano de la aldea donde construiste");
                        return;
                    }
                    PoliciaPedido.clear(dd);
                    dd.putLong("ord_cd", p.serverLevel().getGameTime() + 2400L);
                    dd.putInt("pol_xp", PoliciaMod.xp(p) + XP[kk]);
                    PoliciaBoss.give(p, new ItemStack((ItemLike) Items.EMERALD, PoliciaEstacion.scale(p.serverLevel(), "albanil", EM[kk])));
                    p.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0f, 1.0f);
                    PoliciaDiario.add(p.serverLevel(), "El albañil " + p.getName().getString() + " terminó un pedido: " + NAME[kk].toLowerCase());
                    p.sendSystemMessage(Component.literal("§a<Aldeano> ¡Quedó hermoso! Acá tenés " + EM[kk] + " esmeraldas. (+" + XP[kk] + " XP)"));
                    PoliciaMod.sync(p);
                }
                return;
            }
            if (dd.getLong("ord_cd") > p.serverLevel().getGameTime()) {
                return;
            }
            int idx = slot == 11 ? 0 : (slot == 13 ? 1 : (slot == 15 ? 2 : -1));
            if (idx < 0) {
                return;
            }
            int nk = PoliciaPedido.offers(p, v).get(idx);
            p.closeContainer();
            PoliciaPedido.clear(dd);
            dd.putInt("ord_k", nk);
            dd.putInt("ord_x", v.getBlockX());
            dd.putInt("ord_y", v.getBlockY());
            dd.putInt("ord_z", v.getBlockZ());
            dd.putInt("ord_t", TIME);
            p.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 1.0f);
            p.sendSystemMessage(Component.literal("§6<Aldeano> " + ASK[nk] + " §7(construí cerca de acá, tenés 20 minutos; cuando termines, agachate y hablame)"));
        });
    }

    // ---------------- Reforma de la casa de un aldeano ----------------
    static final int REF_NEED = 40;
    static final int REF_R = 8;

    static BlockPos bed(Villager v) {
        return v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME).map(gp -> gp.pos()).orElse(null);
    }

    static ItemStack reformCard(ServerPlayer p, Villager v) {
        CompoundTag d = p.getPersistentData();
        if (d.hasUUID("ref_v")) {
            boolean here = d.getUUID("ref_v").equals(v.getUUID());
            int n = d.getInt("ref_n");
            if (!here) {
                return PoliciaBoss.named(Items.OAK_STAIRS, "Reforma en curso", "Estás reformando la casa de otro aldeano", "Clic: abandonar esa reforma");
            }
            return n >= REF_NEED ? PoliciaBoss.named(Items.EMERALD, "¡Mostrarle la reforma!", "Sube de nivel en su próximo intercambio", "y te hace descuento para siempre")
                    : PoliciaBoss.named(Items.CLOCK, "Reformando su casa", "Bloques colocados cerca de su cama: " + n + "/" + REF_NEED, "Tiempo: " + d.getInt("ref_t") / 1200 + " min");
        }
        if (PoliciaPedido.bed(v) == null) {
            return PoliciaBoss.named(Items.BARRIER, "Reformar su casa", "Este aldeano no tiene casa (cama)");
        }
        if (v.getPersistentData().getBoolean("ref_done")) {
            return PoliciaBoss.named(Items.LIME_DYE, "Casa ya reformada", "Este aldeano ya tiene su casa nueva");
        }
        return PoliciaBoss.named(Items.OAK_STAIRS, "Reformar su casa", "Colocá " + REF_NEED + " bloques a menos de " + REF_R + " de su cama", "Premio: sube de nivel, descuentos para siempre,", "6 esmeraldas y 6 XP", "Clic para aceptar");
    }

    static void reformClick(ServerPlayer p, Villager v) {
        p.closeContainer();
        CompoundTag d = p.getPersistentData();
        if (d.hasUUID("ref_v")) {
            if (!d.getUUID("ref_v").equals(v.getUUID())) {
                PoliciaPedido.reformClear(d);
                PoliciaMod.msg(p, "Abandonaste la otra reforma");
                return;
            }
            if (d.getInt("ref_n") < REF_NEED) {
                PoliciaMod.msg(p, "Todavía falta: " + d.getInt("ref_n") + "/" + REF_NEED + " bloques cerca de su cama");
                return;
            }
            PoliciaPedido.reformClear(d);
            v.getPersistentData().putBoolean("ref_done", true);
            v.getGossips().add(p.getUUID(), net.minecraft.world.entity.ai.gossip.GossipType.MAJOR_POSITIVE, 20);
            int lv = v.getVillagerData().getLevel();
            if (lv < 5) {
                int[] need = new int[]{0, 10, 70, 150, 250};
                v.setVillagerXp(Math.max(v.getVillagerXp(), need[lv]));
            }
            ServerLevel sl = p.serverLevel();
            int em = PoliciaEstacion.scale(sl, "albanil", 6);
            PoliciaBoss.give(p, new ItemStack((ItemLike) Items.EMERALD, em));
            d.putInt("pol_xp", PoliciaMod.xp(p) + 6);
            sl.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0f, 1.0f);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.5, v.getZ(), 15, 0.4, 0.4, 0.4, 0.0);
            p.sendSystemMessage(Component.literal("§a<Aldeano> ¡Mi casa quedó preciosa! Desde ahora te hago descuento, y en mi próximo intercambio subo de nivel. (+" + em + " esmeraldas, +6 XP)"));
            PoliciaDiario.add(sl, "El albañil " + p.getName().getString() + " reformó la casa de un aldeano");
            PoliciaMod.sync(p);
            return;
        }
        BlockPos b = PoliciaPedido.bed(v);
        if (b == null || v.getPersistentData().getBoolean("ref_done")) {
            return;
        }
        d.putUUID("ref_v", v.getUUID());
        d.putLong("ref_b", b.asLong());
        d.putInt("ref_n", 0);
        d.putInt("ref_t", 12000);
        p.sendSystemMessage(Component.literal("§6<Aldeano> ¡Gracias! Mi cama está en " + b.getX() + ", " + b.getY() + ", " + b.getZ() + ". Agrandá y mejorá la casa (" + REF_NEED + " bloques). Tenés 10 minutos."));
    }

    static void reformClear(CompoundTag d) {
        for (String k : new String[]{"ref_v", "ref_b", "ref_n", "ref_t"}) {
            d.remove(k);
        }
    }

    @SubscribeEvent
    public void place(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        if (d.hasUUID("ref_v") && BlockPos.of(d.getLong("ref_b")).distSqr(e.getPos()) <= REF_R * REF_R) {
            int rn = d.getInt("ref_n") + 1;
            d.putInt("ref_n", rn);
            if (rn == REF_NEED) {
                p.sendSystemMessage(Component.literal("§a¡Reforma terminada! Agachate y hablale al aldeano para mostrársela."));
            } else if (rn % 10 == 0) {
                PoliciaMod.msg(p, "Reforma: " + rn + "/" + REF_NEED);
            }
        }
        int k = d.getInt("ord_k");
        if (k == 0) {
            return;
        }
        BlockPos bp = e.getPos();
        double dx = bp.getX() - d.getInt("ord_x");
        double dz = bp.getZ() - d.getInt("ord_z");
        if (dx * dx + dz * dz > (double) (R * R)) {
            return;
        }
        BlockState s = e.getPlacedBlock();
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        boolean was = PoliciaPedido.complete(d);
        d.putInt("ord_n", d.getInt("ord_n") + 1);
        if (s.getBlock() instanceof DoorBlock) d.putInt("ord_door", d.getInt("ord_door") + 1);
        if (s.getBlock() instanceof FenceBlock) d.putInt("ord_fence", d.getInt("ord_fence") + 1);
        if (s.getBlock() instanceof FenceGateBlock) d.putInt("ord_gate", d.getInt("ord_gate") + 1);
        if (s.getBlock() instanceof CropBlock || s.getBlock() instanceof StemBlock) d.putInt("ord_crop", d.getInt("ord_crop") + 1);
        if (id.contains("stone") || id.contains("cobble") || id.contains("brick") || id.contains("deepslate") || id.contains("andesite") || id.contains("diorite") || id.contains("granite")) d.putInt("ord_stone", d.getInt("ord_stone") + 1);
        if (id.contains("torch") || id.contains("lantern") || id.contains("lamp") || id.contains("glowstone") || id.contains("shroomlight") || id.contains("campfire")) d.putInt("ord_light", d.getInt("ord_light") + 1);
        if (!d.contains("ord_ymin")) {
            d.putInt("ord_ymin", bp.getY());
            d.putInt("ord_ymax", bp.getY());
        } else {
            d.putInt("ord_ymin", Math.min(d.getInt("ord_ymin"), bp.getY()));
            d.putInt("ord_ymax", Math.max(d.getInt("ord_ymax"), bp.getY()));
        }
        if (!was && PoliciaPedido.complete(d)) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.3f);
            p.sendSystemMessage(Component.literal("§a¡Pedido terminado! Agachate y hablale al aldeano para cobrar."));
        } else if (d.getInt("ord_n") % 10 == 0) {
            PoliciaMod.msg(p, NAME[k] + ": " + PoliciaPedido.progress(d));
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        if (d.hasUUID("ref_v")) {
            int rt = d.getInt("ref_t") - 20;
            d.putInt("ref_t", rt);
            if (rt <= 0) {
                PoliciaPedido.reformClear(d);
                PoliciaMod.msg(p, "§cSe venció el plazo de la reforma");
            }
        }
        if (d.getInt("ord_k") == 0) {
            return;
        }
        int t = d.getInt("ord_t") - 20;
        d.putInt("ord_t", t);
        if (t <= 0) {
            PoliciaMod.msg(p, "§cSe venció el plazo del pedido: " + NAME[d.getInt("ord_k")]);
            PoliciaPedido.clear(d);
            d.putLong("ord_cd", p.serverLevel().getGameTime() + 2400L);
        }
    }
}
