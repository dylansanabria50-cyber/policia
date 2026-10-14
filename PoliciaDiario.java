package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Diario de la aldea: guarda las noticias de lo que pasa en el mundo
// (golpes, detenciones, construcciones, encargos...). Se lee tocando la
// campana de cualquier aldea, y cada amanecer sale el titular del día.
public class PoliciaDiario {
    static final int MAX = 40;

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaDiario());
    }

    public static class Data extends SavedData {
        final List<String> news = new ArrayList<String>();
        long lastDay = -1L;

        static Data get(ServerLevel sl) {
            return sl.getServer().overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "policia_diario");
        }

        static Data load(CompoundTag t) {
            Data d = new Data();
            for (Tag e : t.getList("n", 8)) {
                d.news.add(e.getAsString());
            }
            d.lastDay = t.getLong("d");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            ListTag l = new ListTag();
            for (String s : this.news) {
                l.add(StringTag.valueOf(s));
            }
            t.put("n", l);
            t.putLong("d", this.lastDay);
            return t;
        }
    }

    static long day(ServerLevel sl) {
        return sl.getServer().overworld().getDayTime() / 24000L + 1L;
    }

    static void add(ServerLevel sl, String s) {
        Data d = Data.get(sl);
        d.news.add("Día " + PoliciaDiario.day(sl) + ": " + s);
        while (d.news.size() > MAX) {
            d.news.remove(0);
        }
        d.setDirty();
    }

    static void read(ServerPlayer p) {
        Data d = Data.get(p.serverLevel());
        p.sendSystemMessage(Component.literal("§6§l══ DIARIO DE LA ALDEA ══ §r§7(día " + PoliciaDiario.day(p.serverLevel()) + ", " + PoliciaEstacion.name(p.serverLevel()) + ")"));
        if (d.news.isEmpty()) {
            p.sendSystemMessage(Component.literal("§7Hoy no pasó nada interesante."));
            return;
        }
        for (int i = Math.max(0, d.news.size() - 8); i < d.news.size(); ++i) {
            p.sendSystemMessage(Component.literal("§e• §f" + d.news.get(i)));
        }
    }

    @SubscribeEvent
    public void bell(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        if (!(e.getLevel().getBlockState(e.getPos()).getBlock() instanceof BellBlock)) {
            return;
        }
        long now = p.serverLevel().getGameTime();
        if (now - p.getPersistentData().getLong("dia_read") < 60L) {
            return;
        }
        p.getPersistentData().putLong("dia_read", now);
        PoliciaDiario.read(p);
    }

    @SubscribeEvent
    public void dawn(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 100 != 0) {
            return;
        }
        MinecraftServer srv = e.getServer();
        ServerLevel ow = srv.overworld();
        long day = PoliciaDiario.day(ow);
        Data d = Data.get(ow);
        if (d.lastDay == day) {
            return;
        }
        boolean first = d.lastDay < 0L;
        d.lastDay = day;
        d.setDirty();
        if (first) {
            return;
        }
        String head = d.news.isEmpty() ? "Un día tranquilo en la aldea." : d.news.get(d.news.size() - 1).replaceFirst("^Día \\d+: ", "");
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal("§6[Diario de la aldea] §fDía " + day + " (" + PoliciaEstacion.name(ow) + "). Titular: §e" + head + " §7(tocá una campana para leer más)"));
        }
    }
}
