package com.example.policia;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 13: eleccion de alcalde cada 7 dias de juego.
// Dias 1 a 6 del ciclo: /alcalde candidato (anotarse) y /alcalde votar
// <nombre> (un voto por jugador). El dia 7 al amanecer se cuenta, hay fuegos
// artificiales y el ganador elige la ley de la semana con /alcalde ley
// <ley>. Si nadie se anota gana Don Ramiro (un vecino) con una ley al azar.
// Leyes: comercio (interes del banco x2), manodura (policias +25 % XP),
// fiesta (cofre del dia +5 esmeraldas) y obra (albaniles y herreros +25 % XP).
public class PoliciaAlcalde {
    static final int CICLO = 7;
    static final String[] LEYES = {"comercio", "manodura", "fiesta", "obra"};
    static final String[] LEY_TXT = {"Comercio libre: el banco paga el doble de interés", "Mano dura: los policías ganan 25 % más de XP", "Fiesta del pueblo: el cofre del día trae 5 esmeraldas más", "Obra pública: albañiles y herreros ganan 25 % más de XP"};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosAlcalde());
    }

    static CompoundTag datos(MinecraftServer s) {
        return PoliciaMundo.get(s).getSeccion("alcalde");
    }

    static String ley(MinecraftServer s) {
        return datos(s).getString("ley");
    }

    // Multiplicador de XP por la ley vigente (lo usa PoliciaNivel).
    static float bonoXp(ServerPlayer p) {
        String l = ley(p.server);
        String j = PoliciaMod.job(p);
        if ("manodura".equals(l) && "policia".equals(j)) {
            return 0.25f;
        }
        if ("obra".equals(l) && ("albanil".equals(j) || "herrero".equals(j))) {
            return 0.25f;
        }
        return 0.0f;
    }

    static void todos(MinecraftServer s, String txt) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.displayClientMessage(Component.literal(txt), false);
        }
    }

    static void contar(MinecraftServer s) {
        CompoundTag d = datos(s);
        CompoundTag cand = d.getCompound("cand");
        CompoundTag votos = d.getCompound("votos");
        String ganador = null;
        int max = -1;
        for (String c : cand.getAllKeys()) {
            int n = 0;
            for (String v : votos.getAllKeys()) {
                if (votos.getString(v).equalsIgnoreCase(c)) {
                    n++;
                }
            }
            if (n > max) {
                max = n;
                ganador = c;
            }
        }
        if (ganador == null) {
            ganador = "Don Ramiro";
            max = 0;
            String l = LEYES[s.overworld().random.nextInt(LEYES.length)];
            d.putString("ley", l);
            todos(s, "§6Nadie se postuló: §fDon Ramiro sigue de alcalde. Ley de la semana: §e" + LEY_TXT[java.util.Arrays.asList(LEYES).indexOf(l)]);
        } else {
            d.putString("ley", "");
            todos(s, "§6¡" + ganador + " es el nuevo alcalde! §7(" + max + (max == 1 ? " voto" : " votos") + "). §fElige la ley con §e/alcalde ley <comercio|manodura|fiesta|obra>");
        }
        d.putString("alcalde", ganador);
        d.put("cand", new CompoundTag());
        d.put("votos", new CompoundTag());
        PoliciaMundo.get(s).setDirty();
        PoliciaDiario.add(s.overworld(), "Elecciones: " + ganador + " es el alcalde de la comarca");
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6" + ganador)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7nuevo alcalde de la comarca")));
            for (int i = 0; i < 3; i++) {
                ItemStack fw = new ItemStack(Items.FIREWORK_ROCKET);
                CompoundTag ex = new CompoundTag();
                CompoundTag f = new CompoundTag();
                net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
                CompoundTag e1 = new CompoundTag();
                e1.putByte("Type", (byte) (i % 3 == 0 ? 1 : 0));
                e1.putIntArray("Colors", new int[]{0xFFD02A, 0x3A6FD8, 0xE04040});
                e1.putBoolean("Flicker", true);
                l.add(e1);
                f.put("Explosions", l);
                f.putByte("Flight", (byte) 1);
                ex.put("Fireworks", f);
                fw.setTag(ex);
                FireworkRocketEntity r = new FireworkRocketEntity(p.level(), p.getX() + (i - 1) * 3, p.getY() + 1, p.getZ() + 2, fw);
                p.level().addFreshEntity(r);
            }
            if (p.getName().getString().equalsIgnoreCase(ganador)) {
                PoliciaNivel.logro(p, "alcalde");
                PoliciaNivel.dar(p, 150);
            }
        }
    }

    public static class EventosAlcalde {
        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 100 != 40) {
                return;
            }
            MinecraftServer s = e.getServer();
            long dia = PoliciaMundo.get(s).dia(s);
            CompoundTag d = datos(s);
            if (!d.contains("ultimo")) {
                d.putLong("ultimo", dia - dia % CICLO);
                PoliciaMundo.get(s).setDirty();
                return;
            }
            if (dia >= d.getLong("ultimo") + CICLO) {
                d.putLong("ultimo", dia);
                contar(s);
            }
        }

        @SubscribeEvent
        public void comandos(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("alcalde")
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CompoundTag d = datos(p.server);
                        long dia = PoliciaMundo.get(p.server).dia(p.server);
                        long faltan = d.getLong("ultimo") + CICLO - dia;
                        String l = d.getString("ley");
                        int li = java.util.Arrays.asList(LEYES).indexOf(l);
                        p.displayClientMessage(Component.literal("§6Alcalde: §f" + (d.getString("alcalde").isEmpty() ? "Don Ramiro" : d.getString("alcalde"))
                                + "\n§6Ley: §f" + (li >= 0 ? LEY_TXT[li] : "sin ley todavía")
                                + "\n§6Próxima elección en §f" + faltan + (faltan == 1 ? " día" : " días") + " §7· candidatos: " + String.join(", ", d.getCompound("cand").getAllKeys())
                                + "\n§7/alcalde candidato · /alcalde votar <nombre>"), false);
                        return 1;
                    })
                    .then(Commands.literal("candidato").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CompoundTag d = datos(p.server);
                        CompoundTag cand = d.getCompound("cand");
                        cand.putBoolean(p.getName().getString(), true);
                        d.put("cand", cand);
                        PoliciaMundo.get(p.server).setDirty();
                        todos(p.server, "§6" + p.getName().getString() + " se postula para alcalde. §7/alcalde votar " + p.getName().getString());
                        return 1;
                    }))
                    .then(Commands.literal("votar").then(Commands.argument("nombre", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CompoundTag d = datos(p.server);
                        String n = StringArgumentType.getString(c, "nombre");
                        String real = null;
                        for (String k : d.getCompound("cand").getAllKeys()) {
                            if (k.equalsIgnoreCase(n)) {
                                real = k;
                            }
                        }
                        if (real == null) {
                            PoliciaMod.msg(p, "Ese candidato no existe. Mirá la lista con /alcalde");
                            return 0;
                        }
                        CompoundTag v = d.getCompound("votos");
                        boolean cambio = v.contains(p.getUUID().toString());
                        v.putString(p.getUUID().toString(), real);
                        d.put("votos", v);
                        PoliciaMundo.get(p.server).setDirty();
                        PoliciaMod.msg(p, (cambio ? "Cambiaste tu voto a " : "Votaste a ") + real);
                        return 1;
                    })))
                    .then(Commands.literal("ley").then(Commands.argument("ley", StringArgumentType.word()).suggests((c, b) -> {
                        for (String l : LEYES) {
                            b.suggest(l);
                        }
                        return b.buildFuture();
                    }).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CompoundTag d = datos(p.server);
                        if (!p.getName().getString().equalsIgnoreCase(d.getString("alcalde"))) {
                            PoliciaMod.msg(p, "Solo el alcalde elige la ley");
                            return 0;
                        }
                        String l = StringArgumentType.getString(c, "ley").toLowerCase();
                        int i = java.util.Arrays.asList(LEYES).indexOf(l);
                        if (i < 0) {
                            PoliciaMod.msg(p, "Leyes: comercio, manodura, fiesta, obra");
                            return 0;
                        }
                        d.putString("ley", l);
                        PoliciaMundo.get(p.server).setDirty();
                        todos(p.server, "§6El alcalde " + p.getName().getString() + " decretó: §e" + LEY_TXT[i]);
                        PoliciaDiario.add(p.serverLevel(), "Nueva ley: " + LEY_TXT[i]);
                        return 1;
                    }))));
        }
    }
}
