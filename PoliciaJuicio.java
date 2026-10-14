package com.example.policia;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Juicio: cuando un policía entrega a un ladrón (jugador) en la comisaría,
// los demás jugadores votan 60 s si es culpable o inocente. Culpable: va
// preso 3 minutos a la comisaría y paga una multa al policía. Inocente:
// queda libre sin estrellas. Si nadie vota, decide el Jefe de policía
// según cuántas estrellas tenía.
public class PoliciaJuicio {
    static final int VOTE_T = 1200;
    static final int JAIL_T = 3600;
    static Trial cur;

    static class Trial {
        UUID accused;
        UUID cop;
        String an;
        String cn;
        int stars;
        int t = VOTE_T;
        final Map<UUID, Boolean> votes = new HashMap<UUID, Boolean>();
    }

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaJuicio());
    }

    static boolean busy() {
        return cur != null;
    }

    static void start(ServerPlayer cop, ServerPlayer accused) {
        if (cur != null) {
            PoliciaMod.msg(cop, "Ya hay un juicio en curso: esperá a que termine");
            return;
        }
        Trial t = new Trial();
        t.accused = accused.getUUID();
        t.cop = cop.getUUID();
        t.an = accused.getName().getString();
        t.cn = cop.getName().getString();
        t.stars = Math.max(1, PoliciaLadron.wanted(accused));
        cur = t;
        MinecraftServer srv = cop.server;
        MutableComponent msg = Component.literal("§6§l⚖ JUICIO §r§f" + t.cn + " acusa a §e" + t.an + "§f (" + "★".repeat(t.stars) + "). Votá: ");
        MutableComponent g = Component.literal("[CULPABLE]").withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/juicio culpable")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Va preso 3 minutos"))));
        MutableComponent i = Component.literal(" [INOCENTE]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/juicio inocente")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Queda libre"))));
        Component full = msg.append(g).append(i).append(Component.literal(" §7(60 s)"));
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.sendSystemMessage(full);
            p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 0.8f, 0.8f);
        }
    }

    static void vote(ServerPlayer p, boolean guilty) {
        if (cur == null) {
            PoliciaMod.msg(p, "No hay ningún juicio ahora");
            return;
        }
        if (p.getUUID().equals(cur.accused) || p.getUUID().equals(cur.cop)) {
            PoliciaMod.msg(p, "No podés votar en tu propio juicio");
            return;
        }
        cur.votes.put(p.getUUID(), guilty);
        PoliciaMod.msg(p, "Votaste: " + (guilty ? "§cculpable" : "§ainocente"));
    }

    static void end(MinecraftServer srv) {
        Trial t = cur;
        cur = null;
        int g = 0;
        int n = 0;
        for (boolean v : t.votes.values()) {
            if (v) ++g;
            else ++n;
        }
        boolean guilty;
        String how;
        if (g + n == 0) {
            // decide el Jefe de policía: más estrellas, más probable
            guilty = srv.overworld().random.nextInt(5) < t.stars + 1;
            how = "Nadie votó: decidió el Jefe de policía";
        } else {
            guilty = g >= n;
            how = g + " votos culpable, " + n + " inocente";
        }
        ServerPlayer a = srv.getPlayerList().getPlayer(t.accused);
        ServerPlayer c = srv.getPlayerList().getPlayer(t.cop);
        String verdict = guilty ? "§c§lCULPABLE" : "§a§lINOCENTE";
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal("§6⚖ Veredicto para " + t.an + ": " + verdict + " §7(" + how + ")"));
        }
        ServerLevel ow = srv.overworld();
        if (a != null) {
            a.getPersistentData().putInt("pol_cuff", 1);
            a.getPersistentData().putInt("pol_wnt", 0);
            if (guilty) {
                BlockPos jail = PoliciaStruct.Sites.get(ow).nearest(0, a.blockPosition());
                if (jail != null && a.level() == ow) {
                    a.connection.teleport(jail.getX() + 0.5, jail.getY(), jail.getZ() + 0.5, a.getYRot(), a.getXRot());
                    a.getPersistentData().putLong("jail_at", jail.asLong());
                }
                a.getPersistentData().putInt("jail_t", JAIL_T);
                int fine = Math.min(PoliciaBoss.emeralds(a), Math.max(2, PoliciaBoss.emeralds(a) / 5));
                if (fine > 0) {
                    PoliciaBoss.takeEmeralds(a, fine);
                    if (c != null) PoliciaBoss.give(c, new ItemStack(Items.EMERALD, fine));
                }
                a.sendSystemMessage(Component.literal("§cVas preso 3 minutos a la comisaría. Multa: " + fine + " esmeraldas."));
                if (c != null) {
                    c.getPersistentData().putInt("pol_xp", PoliciaMod.xp(c) + 8);
                    c.sendSystemMessage(Component.literal("§aJusticia: +8 XP" + (fine > 0 ? " y " + fine + " esmeraldas de multa" : "")));
                    PoliciaMod.sync(c);
                }
                PoliciaDiario.add(ow, t.an + " fue declarado culpable y está preso en la comisaría");
            } else {
                a.sendSystemMessage(Component.literal("§aQuedaste libre y sin estrellas."));
                if (c != null) {
                    c.getPersistentData().putInt("pol_xp", Math.max(0, PoliciaMod.xp(c) - 2));
                    c.sendSystemMessage(Component.literal("§7El acusado era inocente: -2 XP"));
                    PoliciaMod.sync(c);
                }
                PoliciaDiario.add(ow, t.an + " fue declarado inocente en el juicio");
            }
            PoliciaMod.sync(a);
        }
    }

    @SubscribeEvent
    public void cmd(RegisterCommandsEvent e) {
        e.getDispatcher().register((LiteralArgumentBuilder<CommandSourceStack>) Commands.literal("juicio")
                .then(Commands.literal("culpable").executes(c -> {
                    PoliciaJuicio.vote(c.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(Commands.literal("inocente").executes(c -> {
                    PoliciaJuicio.vote(c.getSource().getPlayerOrException(), false);
                    return 1;
                })));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer srv = e.getServer();
        if (cur != null) {
            ServerPlayer a = srv.getPlayerList().getPlayer(cur.accused);
            if (a != null) {
                // sigue esposado mientras dura el juicio
                a.getPersistentData().putInt("pol_cuff", 40);
                a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6, false, false));
            }
            if (--cur.t <= 0 || a == null) {
                PoliciaJuicio.end(srv);
            } else if (cur.t % 400 == 0) {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    PoliciaMod.msg(p, "⚖ Juicio a " + cur.an + ": quedan " + cur.t / 20 + " s para votar");
                }
            }
        }
        if (srv.getTickCount() % 20 != 0) {
            return;
        }
        // a pie: llevar al esposado hasta la comisaría también inicia el juicio
        if (cur == null) {
            for (ServerPlayer cop : srv.getPlayerList().getPlayers()) {
                if (!"policia".equals(PoliciaMod.job(cop)) || cop.level().dimension() != Level.OVERWORLD || cop.isPassenger()) continue;
                if (!PoliciaStruct.Sites.get(cop.serverLevel()).near(0, cop.blockPosition(), 16)) continue;
                for (ServerPlayer a : srv.getPlayerList().getPlayers()) {
                    CompoundTag ad = a.getPersistentData();
                    if (a != cop && ad.getInt("pol_cuff") > 0 && ad.hasUUID("pol_cuffo") && ad.getUUID("pol_cuffo").equals(cop.getUUID()) && a.distanceToSqr(cop) < 64.0 && a.getPersistentData().getInt("jail_t") <= 0) {
                        PoliciaJuicio.start(cop, a);
                        break;
                    }
                }
                if (cur != null) break;
            }
        }
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            CompoundTag d = p.getPersistentData();
            int jt = d.getInt("jail_t");
            if (jt <= 0) continue;
            jt -= 20;
            d.putInt("jail_t", jt);
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 4, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));
            if (d.contains("jail_at") && p.level().dimension() == Level.OVERWORLD) {
                BlockPos j = BlockPos.of(d.getLong("jail_at"));
                if (p.blockPosition().distSqr(j) > 36.0) {
                    p.connection.teleport(j.getX() + 0.5, j.getY(), j.getZ() + 0.5, p.getYRot(), p.getXRot());
                    PoliciaMod.msg(p, "§cEstás preso: no podés irte de la comisaría");
                }
            }
            if (jt <= 0) {
                d.remove("jail_at");
                p.sendSystemMessage(Component.literal("§aCumpliste tu condena: estás libre."));
            } else if (jt % 600 == 0) {
                PoliciaMod.msg(p, "§cPreso: " + jt / 20 + " s");
            }
        }
    }
}
