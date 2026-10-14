package com.example.policia;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegistryObject;

// Cajas fuertes (seguro contra robos): el albañil instala una caja fuerte en
// un cofre o barril (si hay otro jugador al lado, la caja es para él) y el
// herrero la refuerza con placas (más números en la combinación). Solo el
// dueño la abre normal; un ladrón la puede forzar con el minijuego de la
// perilla. Si la abren o fallan, salta la alarma y le avisa al dueño.
public class PoliciaCaja {
    public static final RegistryObject<Item> KIT = PoliciaShield.ITEMS.register("kit_caja_fuerte", () -> new Kit(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> PLACAS = PoliciaShield.ITEMS.register("placas_refuerzo", () -> new Placas(new Item.Properties().stacksTo(16)));
    static final UUID NOBODY = new UUID(0L, 0L);
    static final int MAX_LV = 3;
    static final int TRIES = 3;

    static void init() {
        PoliciaMod.NET.registerMessage(14, Open.class, Open::enc, Open::dec, Open::handle);
        PoliciaMod.NET.registerMessage(15, Try.class, Try::enc, Try::dec, Try::handle);
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaCaja());
    }

    static boolean container(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
    }

    static CompoundTag data(BlockEntity be) {
        return be.getPersistentData();
    }

    static boolean isSafe(BlockEntity be) {
        return be != null && be.getPersistentData().contains("safe_lv");
    }

    // La otra mitad de un cofre doble, si la hay.
    static BlockPos other(Level lv, BlockPos p) {
        BlockState s = lv.getBlockState(p);
        if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            Direction d = ChestBlock.getConnectedDirection(s);
            return p.relative(d);
        }
        return null;
    }

    static int[] newCombo(Level lv, int level) {
        int[] c = new int[level + 1];
        for (int i = 0; i < c.length; ++i) {
            c[i] = lv.random.nextInt(40);
        }
        return c;
    }

    static void make(Level lv, BlockPos p, UUID owner, int level) {
        for (BlockPos q : new BlockPos[]{p, PoliciaCaja.other(lv, p)}) {
            if (q == null) continue;
            BlockEntity be = lv.getBlockEntity(q);
            if (be == null) continue;
            CompoundTag d = PoliciaCaja.data(be);
            d.putUUID("safe_owner", owner);
            d.putInt("safe_lv", level);
            d.putIntArray("safe_combo", PoliciaCaja.newCombo(lv, level));
            be.setChanged();
        }
    }

    // La combinación se toma siempre de la mitad clicada.
    static void reroll(Level lv, BlockPos p) {
        BlockEntity be = lv.getBlockEntity(p);
        if (be == null) return;
        int[] c = PoliciaCaja.newCombo(lv, be.getPersistentData().getInt("safe_lv"));
        for (BlockPos q : new BlockPos[]{p, PoliciaCaja.other(lv, p)}) {
            if (q == null) continue;
            BlockEntity b2 = lv.getBlockEntity(q);
            if (b2 != null && PoliciaCaja.isSafe(b2)) {
                b2.getPersistentData().putIntArray("safe_combo", c);
                b2.setChanged();
            }
        }
    }

    static boolean owner(BlockEntity be, Player p) {
        CompoundTag d = be.getPersistentData();
        return d.hasUUID("safe_owner") && d.getUUID("safe_owner").equals(p.getUUID());
    }

    public static class Kit extends Item {
        public Kit(Item.Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack it, Level lv, List<Component> tip, TooltipFlag f) {
            tip.add(Component.literal("§7Albañil: clic derecho en un cofre o barril para convertirlo en caja fuerte"));
            tip.add(Component.literal("§7Si hay otro jugador a tu lado, la caja queda a su nombre"));
        }

        @Override
        public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c) {
            if (c.getLevel().isClientSide || !(c.getPlayer() instanceof ServerPlayer p)) {
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            BlockEntity be = c.getLevel().getBlockEntity(c.getClickedPos());
            if (!PoliciaCaja.container(be)) {
                PoliciaMod.msg(p, "Usalo sobre un cofre o un barril");
                return net.minecraft.world.InteractionResult.FAIL;
            }
            if (!"albanil".equals(PoliciaMod.job(p))) {
                PoliciaMod.msg(p, "Solo un albañil sabe instalar cajas fuertes");
                return net.minecraft.world.InteractionResult.FAIL;
            }
            if (PoliciaCaja.isSafe(be)) {
                PoliciaMod.msg(p, "Ya es una caja fuerte");
                return net.minecraft.world.InteractionResult.FAIL;
            }
            ServerPlayer client = null;
            double best = 25.0;
            for (ServerPlayer o : p.serverLevel().players()) {
                if (o == p) continue;
                double d = o.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    client = o;
                }
            }
            ServerPlayer own = client != null ? client : p;
            PoliciaCaja.make(c.getLevel(), c.getClickedPos(), own.getUUID(), 1);
            c.getItemInHand().shrink(1);
            c.getLevel().playSound(null, c.getClickedPos(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.7f);
            p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + 2);
            if (client != null) {
                client.sendSystemMessage(Component.literal("§a" + p.getName().getString() + " te instaló una caja fuerte. Solo vos la podés abrir."));
                PoliciaMod.msg(p, "Caja fuerte instalada para " + client.getName().getString() + " (+2 XP)");
            } else {
                PoliciaMod.msg(p, "Caja fuerte instalada (nivel 1). Un herrero la puede reforzar");
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
    }

    public static class Placas extends Item {
        public Placas(Item.Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack it, Level lv, List<Component> tip, TooltipFlag f) {
            tip.add(Component.literal("§7Herrero: clic derecho en una caja fuerte para reforzarla"));
            tip.add(Component.literal("§7Cada nivel agrega un número a la combinación (máx. nivel " + MAX_LV + ")"));
        }

        @Override
        public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c) {
            if (c.getLevel().isClientSide || !(c.getPlayer() instanceof ServerPlayer p)) {
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            BlockEntity be = c.getLevel().getBlockEntity(c.getClickedPos());
            if (!PoliciaCaja.isSafe(be)) {
                PoliciaMod.msg(p, "Primero tiene que ser una caja fuerte (la instala un albañil)");
                return net.minecraft.world.InteractionResult.FAIL;
            }
            if (!PoliciaHerrero.isHerrero(p)) {
                PoliciaMod.msg(p, "Solo un herrero puede reforzar cajas fuertes");
                return net.minecraft.world.InteractionResult.FAIL;
            }
            int lv = be.getPersistentData().getInt("safe_lv");
            if (lv >= MAX_LV) {
                PoliciaMod.msg(p, "Ya está reforzada al máximo");
                return net.minecraft.world.InteractionResult.FAIL;
            }
            UUID own = be.getPersistentData().getUUID("safe_owner");
            PoliciaCaja.make(c.getLevel(), c.getClickedPos(), own, lv + 1);
            c.getItemInHand().shrink(1);
            c.getLevel().playSound(null, c.getClickedPos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
            p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + 2);
            PoliciaHerrero.anim(p, 2);
            PoliciaMod.msg(p, "Caja fuerte reforzada a nivel " + (lv + 1) + " (+2 XP)");
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
    }

    static void alarm(ServerPlayer thief, BlockPos pos, BlockEntity be, boolean opened) {
        ServerLevel sl = thief.serverLevel();
        sl.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.5f, 1.2f);
        sl.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 2.0f, 2.0f);
        int lv = be.getPersistentData().getInt("safe_lv");
        PoliciaLadron.crime(thief, opened ? lv : 1, opened ? "caja fuerte forzada" : "intento de robo");
        UUID own = be.getPersistentData().hasUUID("safe_owner") ? be.getPersistentData().getUUID("safe_owner") : NOBODY;
        ServerPlayer o = sl.getServer().getPlayerList().getPlayer(own);
        if (o != null && o != thief) {
            o.sendSystemMessage(Component.literal("§c§l¡ALARMA! §r§c" + (opened ? "Forzaron" : "Intentaron forzar") + " tu caja fuerte en " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + thief.getName().getString() + ")"));
            o.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1.0f, 1.4f);
        }
        if (opened && !own.equals(NOBODY)) {
            PoliciaDiario.add(sl, "Forzaron una caja fuerte de nivel " + lv + (o != null ? " de " + o.getName().getString() : ""));
        }
    }

    // Prioridad alta: decide antes que otras reglas de cofres (ganzúa, etc.).
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void click(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        BlockEntity be = e.getLevel().getBlockEntity(e.getPos());
        if (!PoliciaCaja.isSafe(be)) {
            return;
        }
        ItemStack held = p.getMainHandItem();
        if (held.is(KIT.get()) || held.is(PLACAS.get())) {
            return;
        }
        CompoundTag pd = p.getPersistentData();
        if (PoliciaCaja.owner(be, p) || p.isCreative()) {
            return;
        }
        if (pd.contains("safe_ok") && pd.getLong("safe_ok") == e.getPos().asLong() && p.serverLevel().getGameTime() < pd.getLong("safe_okt")) {
            return;
        }
        e.setCanceled(true);
        long now = p.serverLevel().getGameTime();
        CompoundTag d = be.getPersistentData();
        if (!"ladron".equals(PoliciaMod.job(p)) || !PoliciaMod.on(p) || !p.isShiftKeyDown()) {
            PoliciaMod.msg(p, "Caja fuerte de nivel " + d.getInt("safe_lv") + ": está cerrada con combinación");
            return;
        }
        if (now < d.getLong("safe_lock")) {
            PoliciaMod.msg(p, "La cerradura está trabada por la alarma: esperá " + (d.getLong("safe_lock") - now) / 20L + " s");
            return;
        }
        pd.putLong("safe_s", e.getPos().asLong());
        pd.putLong("safe_st", now);
        int[] combo = d.getIntArray("safe_combo");
        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), new Open(e.getPos(), d.getInt("safe_lv"), combo));
    }

    static void result(ServerPlayer p, BlockPos pos, int[] guess) {
        CompoundTag pd = p.getPersistentData();
        ServerLevel sl = p.serverLevel();
        if (!pd.contains("safe_s") || pd.getLong("safe_s") != pos.asLong() || sl.getGameTime() - pd.getLong("safe_st") > 2400L || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 49.0) {
            return;
        }
        pd.remove("safe_s");
        if (guess.length == 0) {
            PoliciaMod.msg(p, "Te alejaste de la caja fuerte sin hacer ruido");
            return;
        }
        BlockEntity be = sl.getBlockEntity(pos);
        if (!PoliciaCaja.isSafe(be)) {
            return;
        }
        int[] combo = be.getPersistentData().getIntArray("safe_combo");
        boolean ok = guess.length == combo.length;
        for (int i = 0; ok && i < combo.length; ++i) {
            ok = guess[i] == combo[i];
        }
        if (!ok) {
            be.getPersistentData().putLong("safe_lock", sl.getGameTime() + 1200L);
            be.setChanged();
            PoliciaCaja.alarm(p, pos, be, false);
            PoliciaMod.msg(p, "§c¡Fallaste la combinación! Saltó la alarma");
            return;
        }
        PoliciaCaja.alarm(p, pos, be, true);
        PoliciaCaja.reroll(sl, pos);
        pd.putLong("safe_ok", pos.asLong());
        pd.putLong("safe_okt", sl.getGameTime() + 600L);
        int lv = be.getPersistentData().getInt("safe_lv");
        pd.putInt("pol_xp", PoliciaMod.xp(p) + 2 + lv * 2);
        PoliciaMod.msg(p, "§a¡Caja abierta! (+" + (2 + lv * 2) + " XP) Tenés 30 s");
        MenuProvider mp = sl.getBlockState(pos).getMenuProvider(sl, pos);
        if (mp != null) {
            p.openMenu(mp);
        }
        PoliciaGolpe.vaultOpened(p, pos);
        PoliciaMod.sync(p);
    }

    @SubscribeEvent
    public void brk(BlockEvent.BreakEvent e) {
        BlockEntity be = e.getLevel().getBlockEntity(e.getPos());
        if (PoliciaCaja.isSafe(be) && !PoliciaCaja.owner(be, e.getPlayer()) && !e.getPlayer().isCreative()) {
            e.setCanceled(true);
            if (e.getPlayer() instanceof ServerPlayer sp) {
                PoliciaMod.msg(sp, "Es una caja fuerte: no se puede romper");
            }
        }
    }

    @SubscribeEvent
    public void boom(ExplosionEvent.Detonate e) {
        e.getAffectedBlocks().removeIf(bp -> PoliciaCaja.isSafe(e.getLevel().getBlockEntity(bp)));
    }

    public static class Open {
        final BlockPos pos;
        final int lv;
        final int[] combo;

        Open(BlockPos pos, int lv, int[] combo) {
            this.pos = pos;
            this.lv = lv;
            this.combo = combo;
        }

        static void enc(Open m, FriendlyByteBuf b) {
            b.writeBlockPos(m.pos);
            b.writeVarInt(m.lv);
            b.writeVarIntArray(m.combo);
        }

        static Open dec(FriendlyByteBuf b) {
            return new Open(b.readBlockPos(), b.readVarInt(), b.readVarIntArray());
        }

        static void handle(Open m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PoliciaCajaScreen.open(m.pos, m.lv, m.combo)));
            c.get().setPacketHandled(true);
        }
    }

    public static class Try {
        final BlockPos pos;
        final int[] guess;

        Try(BlockPos pos, int[] guess) {
            this.pos = pos;
            this.guess = guess;
        }

        static void enc(Try m, FriendlyByteBuf b) {
            b.writeBlockPos(m.pos);
            b.writeVarIntArray(m.guess);
        }

        static Try dec(FriendlyByteBuf b) {
            return new Try(b.readBlockPos(), b.readVarIntArray());
        }

        static void handle(Try m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p != null) {
                    PoliciaCaja.result(p, m.pos, m.guess);
                }
            });
            c.get().setPacketHandled(true);
        }
    }
}
