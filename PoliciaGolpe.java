package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Golpes grandes del ladrón: misiones especiales que da el Jefe de ladrones.
// 1) Bóveda del casino: aparece una bóveda llena en el casino; al abrirla
//    suena la alarma, llegan agentes y hay que escapar lejos a tiempo.
// 2) Golpe al Perista: robarle los bolsillos al Perista; él da la alarma.
// Un golpe por día de Minecraft.
public class PoliciaGolpe {
    static final String[] NAME = new String[]{"", "Asalto a la bóveda", "Golpe al Perista"};
    static final int[] ESCAPE = new int[]{0, 120, 100};
    static final int[] TIME = new int[]{0, 3600, 3000};
    static final int[] XP = new int[]{0, 30, 25};
    static final int[] EM = new int[]{0, 16, 12};

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaGolpe());
    }

    static int kind(ServerPlayer p) {
        return p.getPersistentData().getInt("gh_k");
    }

    static ItemStack card(ServerPlayer p, int k) {
        CompoundTag d = p.getPersistentData();
        if (PoliciaGolpe.kind(p) == k) {
            return PoliciaBoss.named(k == 1 ? Items.GOLD_BLOCK : Items.DIAMOND, "GOLPE EN CURSO: " + NAME[k], d.getInt("gh_ph") == 2 ? "¡Escapá!" : (k == 1 ? "Abrí la bóveda del casino" : "Robale los bolsillos al Perista (por detrás)"), "Clic: abandonar el golpe");
        }
        long left = d.getLong("gh_cd") - p.serverLevel().getGameTime();
        String[] lore = k == 1
                ? new String[]{"Aparece una bóveda llena en el casino (2.º sótano).", "Es una caja fuerte: forzala (agachado + clic).", "Al abrirla suena la alarma y llegan agentes:", "escapá a " + ESCAPE[1] + " bloques en 3 minutos.", "Botín: diamantes, oro y esmeraldas", "Recompensa extra: " + XP[1] + " XP y " + EM[1] + " esmeraldas", left > 0 ? "Disponible en " + (left / 1200L + 1L) + " min" : "Clic: aceptar el golpe"}
                : new String[]{"Robale los bolsillos al Perista del casino.", "Da la alarma: escapá a " + ESCAPE[2] + " bloques en 2:30.", "Botín: diamantes, esmeraldas y chatarra de netherita", "Recompensa extra: " + XP[2] + " XP y " + EM[2] + " esmeraldas", left > 0 ? "Disponible en " + (left / 1200L + 1L) + " min" : "Clic: aceptar el golpe"};
        return PoliciaBoss.named(k == 1 ? Items.GOLD_BLOCK : Items.DIAMOND, "GOLPE GRANDE: " + NAME[k], lore);
    }

    static void click(ServerPlayer p, int k) {
        p.closeContainer();
        CompoundTag d = p.getPersistentData();
        int cur = PoliciaGolpe.kind(p);
        if (cur == k) {
            PoliciaGolpe.end(p, false, "Abandonaste el golpe");
            return;
        }
        if (cur != 0) {
            PoliciaMod.msg(p, "Ya tenés un golpe en curso: " + NAME[cur]);
            return;
        }
        long left = d.getLong("gh_cd") - p.serverLevel().getGameTime();
        if (left > 0) {
            PoliciaMod.msg(p, "Todavía está caliente: esperá " + (left / 1200L + 1L) + " minutos para otro golpe");
            return;
        }
        ServerLevel sl = p.serverLevel();
        BlockPos site = PoliciaStruct.Sites.get(sl).nearest(2, p.blockPosition());
        if (site == null || sl.dimension() != Level.OVERWORLD) {
            PoliciaMod.msg(p, "No conozco ningún casino por acá");
            return;
        }
        d.putInt("gh_k", k);
        d.putInt("gh_ph", 1);
        d.putLong("gh_site", site.asLong());
        if (k == 1) {
            // site = (x0 + 6, g + 1, z0 + 13): la bóveda va en el 2.º sótano.
            int x0 = site.getX() - 6;
            int z0 = site.getZ() - 13;
            int g = site.getY() - 1;
            BlockPos v = new BlockPos(x0 + 11, g - 12 + 1, z0 + 11);
            sl.setBlockAndUpdate(v, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.NORTH));
            BlockEntity be = sl.getBlockEntity(v);
            if (be instanceof ChestBlockEntity ch) {
                ch.setCustomName(Component.literal("Bóveda del casino"));
                ItemStack[] loot = new ItemStack[]{new ItemStack((ItemLike) Items.DIAMOND, 6), new ItemStack((ItemLike) Items.GOLD_INGOT, 16), new ItemStack((ItemLike) Items.EMERALD, 24), new ItemStack((ItemLike) Items.GOLDEN_APPLE, 2), new ItemStack((ItemLike) Items.GOLD_BLOCK, 2), new ItemStack((ItemLike) Items.DIAMOND, 2 + p.getRandom().nextInt(4))};
                for (int i = 0; i < loot.length; ++i) {
                    ch.setItem(i * 4 + p.getRandom().nextInt(4), loot[i]);
                }
            }
            d.putLong("gh_v", v.asLong());
            PoliciaCaja.make(sl, v, PoliciaCaja.NOBODY, 2);
            p.sendSystemMessage(Component.literal("§6<Jefe de ladrones> La bóveda del casino está en el segundo sótano (" + v.getX() + ", " + v.getY() + ", " + v.getZ() + "). Es una caja fuerte: agachate y hacé clic derecho para forzar la combinación. Cuando la abras va a sonar la alarma: agarrá todo y corré."));
        } else {
            p.sendSystemMessage(Component.literal("§6<Jefe de ladrones> El Perista guarda lo mejor en los bolsillos. Está en el último sótano del casino (" + site.getX() + ", " + site.getZ() + "). Acercate por detrás, agachado, y clic derecho."));
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    // Llamado desde el carterismo exitoso. true = lo manejó el golpe.
    static boolean pickedPerista(ServerPlayer p, LivingEntity t) {
        if (PoliciaGolpe.kind(p) != 2 || p.getPersistentData().getInt("gh_ph") != 1) {
            return false;
        }
        if (!(t instanceof PoliciaBoss.BossNpc b) || b.kind() != 3) {
            return false;
        }
        PoliciaBoss.give(p, new ItemStack((ItemLike) Items.DIAMOND, 5));
        PoliciaBoss.give(p, new ItemStack((ItemLike) Items.EMERALD, 20));
        PoliciaBoss.give(p, new ItemStack((ItemLike) Items.NETHERITE_SCRAP, 2));
        p.sendSystemMessage(Component.literal("§a¡Le vaciaste los bolsillos al Perista! 5 diamantes, 20 esmeraldas y 2 chatarras de netherita."));
        PoliciaGolpe.alarm(p, t.blockPosition());
        return true;
    }

    static void alarm(ServerPlayer p, BlockPos at) {
        CompoundTag d = p.getPersistentData();
        int k = PoliciaGolpe.kind(p);
        d.putInt("gh_ph", 2);
        d.putInt("gh_t", TIME[k]);
        d.putLong("gh_from", at.asLong());
        ServerLevel sl = p.serverLevel();
        PoliciaLadron.crime(p, k == 1 ? 3 : 2, NAME[k].toLowerCase());
        BlockPos site = BlockPos.of(d.getLong("gh_site"));
        for (int i = 0; i < (k == 1 ? 3 : 2); ++i) {
            PoliciaBoss.BossNpc a = PoliciaStruct.npc(sl, 5, site.getX() + 0.5 + i - 1, site.getY(), site.getZ() + 1.5, 180.0f, null, 0);
            a.setTarget(p);
        }
        for (PoliciaBoss.BossNpc b : sl.getEntitiesOfClass(PoliciaBoss.BossNpc.class, p.getBoundingBox().inflate(24.0), x -> x.kind() == 4 && x.isAlive())) {
            b.setTarget(p);
        }
        sl.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 1.0f);
        sl.playSound(null, at, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 1.4f);
        PoliciaGolpe.title(p, "§c¡ALARMA!", "§eEscapá a " + ESCAPE[k] + " bloques antes de " + TIME[k] / 1200 + ":" + String.format("%02d", TIME[k] % 1200 / 20));
    }

    static void title(ServerPlayer p, String t, String s) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(s)));
    }

    static void end(ServerPlayer p, boolean ok, String why) {
        CompoundTag d = p.getPersistentData();
        int k = PoliciaGolpe.kind(p);
        if (k == 0) {
            return;
        }
        d.remove("gh_k");
        d.remove("gh_ph");
        d.remove("gh_t");
        d.putLong("gh_cd", p.serverLevel().getGameTime() + 24000L);
        if (ok) {
            d.putInt("pol_xp", PoliciaMod.xp(p) + XP[k]);
            PoliciaBoss.give(p, new ItemStack((ItemLike) Items.EMERALD, EM[k]));
            PoliciaGolpe.title(p, "§a¡GOLPE PERFECTO!", "§f" + NAME[k] + ": +" + XP[k] + " XP y " + EM[k] + " esmeraldas");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
            PoliciaMod.sync(p);
        } else {
            PoliciaGolpe.title(p, "§cGolpe fallido", "§7" + why);
        }
    }

    // La bóveda es una caja fuerte: la alarma suena al forzarla.
    static void vaultOpened(ServerPlayer p, BlockPos pos) {
        CompoundTag d = p.getPersistentData();
        if (PoliciaGolpe.kind(p) == 1 && d.getInt("gh_ph") == 1 && pos.asLong() == d.getLong("gh_v")) {
            PoliciaGolpe.alarm(p, pos);
        }
    }

    @SubscribeEvent
    public void open(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        if (PoliciaGolpe.kind(p) == 1 && d.getInt("gh_ph") == 1 && e.getPos().asLong() == d.getLong("gh_v")) {
            PoliciaGolpe.alarm(p, e.getPos());
        }
    }

    @SubscribeEvent
    public void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && PoliciaGolpe.kind(p) != 0) {
            PoliciaGolpe.end(p, false, "Te atraparon");
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        int k = PoliciaGolpe.kind(p);
        if (k == 0 || d.getInt("gh_ph") != 2) {
            return;
        }
        int t = d.getInt("gh_t") - 20;
        d.putInt("gh_t", t);
        BlockPos from = BlockPos.of(d.getLong("gh_from"));
        double dist = Math.sqrt(p.distanceToSqr(from.getX() + 0.5, p.getY(), from.getZ() + 0.5));
        if (dist >= ESCAPE[k]) {
            PoliciaGolpe.end(p, true, "");
            return;
        }
        if (t <= 0) {
            PoliciaLadron.crime(p, 1, "te identificaron");
            PoliciaGolpe.end(p, false, "No escapaste a tiempo: te identificaron");
            return;
        }
        PoliciaMod.msg(p, "§c¡ESCAPÁ! §f" + (int) (ESCAPE[k] - dist) + " bloques más · " + t / 1200 + ":" + String.format("%02d", t % 1200 / 20));
    }
}
