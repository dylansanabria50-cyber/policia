package com.example.policia;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 10b: gremios de jugadores.
// /gremio fundar <nombre> (20 esmeraldas), /gremio invitar <jugador>,
// /gremio unirse <nombre>, /gremio salir, /gremio echar <jugador>,
// /gremio info, /gremio cofre (27 espacios compartidos, de a uno por vez) y
// /g <mensaje> para hablar solo con el gremio.
public class PoliciaGremio {
    static final int PRECIO = 20;
    static final Set<String> COFRE_ABIERTO = new HashSet<String>();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosGremio());
    }

    static CompoundTag gremios(MinecraftServer s) {
        return PoliciaMundo.get(s).getSeccion("gremios");
    }

    static String gremioDe(MinecraftServer s, String uuid) {
        CompoundTag g = gremios(s);
        for (String k : g.getAllKeys()) {
            ListTag m = g.getCompound(k).getList("miembros", Tag.TAG_STRING);
            for (int i = 0; i < m.size(); i++) {
                if (m.getString(i).equals(uuid)) {
                    return k;
                }
            }
        }
        return null;
    }

    static void avisar(MinecraftServer s, String nombre, String txt) {
        CompoundTag g = gremios(s).getCompound(nombre);
        ListTag m = g.getList("miembros", Tag.TAG_STRING);
        for (int i = 0; i < m.size(); i++) {
            ServerPlayer p = s.getPlayerList().getPlayer(java.util.UUID.fromString(m.getString(i)));
            if (p != null) {
                p.displayClientMessage(Component.literal("§2[" + nombre + "] §f" + txt), false);
            }
        }
    }

    static void sucio(MinecraftServer s) {
        PoliciaMundo.get(s).setDirty();
    }

    public static class EventosGremio {
        @SubscribeEvent
        public void comandos(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("g").then(Commands.argument("mensaje", StringArgumentType.greedyString()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                String g = gremioDe(p.server, p.getUUID().toString());
                if (g == null) {
                    PoliciaMod.msg(p, "No estás en ningún gremio");
                    return 0;
                }
                avisar(p.server, g, "§7" + p.getName().getString() + ": §f" + StringArgumentType.getString(c, "mensaje"));
                return 1;
            })));
            e.getDispatcher().register(Commands.literal("gremio")
                    .executes(c -> info(c.getSource().getPlayerOrException()))
                    .then(Commands.literal("info").executes(c -> info(c.getSource().getPlayerOrException())))
                    .then(Commands.literal("fundar").then(Commands.argument("nombre", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String n = StringArgumentType.getString(c, "nombre");
                        if (gremioDe(p.server, p.getUUID().toString()) != null) {
                            PoliciaMod.msg(p, "Ya estás en un gremio (/gremio salir)");
                            return 0;
                        }
                        if (n.length() < 3 || n.length() > 20 || gremios(p.server).contains(n)) {
                            PoliciaMod.msg(p, "Elegí otro nombre (3 a 20 letras, que no exista)");
                            return 0;
                        }
                        if (!PoliciaBanco.cobrar(p, PRECIO)) {
                            PoliciaMod.msg(p, "Fundar un gremio cuesta " + PRECIO + " esmeraldas (de tu cuenta o encima)");
                            return 0;
                        }
                        CompoundTag t = new CompoundTag();
                        t.putString("jefe", p.getUUID().toString());
                        t.putString("jnom", p.getName().getString());
                        ListTag m = new ListTag();
                        m.add(StringTag.valueOf(p.getUUID().toString()));
                        t.put("miembros", m);
                        t.put("inv", new ListTag());
                        t.put("cofre", new ListTag());
                        gremios(p.server).put(n, t);
                        sucio(p.server);
                        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
                            o.displayClientMessage(Component.literal("§2" + p.getName().getString() + " fundó el gremio «" + n + "»"), false);
                        }
                        PoliciaDiario.add(p.serverLevel(), "Se fundó el gremio «" + n + "», con " + p.getName().getString() + " a la cabeza");
                        PoliciaNivel.logro(p, "gremio");
                        return 1;
                    })))
                    .then(Commands.literal("invitar").then(Commands.argument("jugador", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String g = gremioDe(p.server, p.getUUID().toString());
                        ServerPlayer o = p.server.getPlayerList().getPlayerByName(StringArgumentType.getString(c, "jugador"));
                        if (g == null || o == null) {
                            PoliciaMod.msg(p, g == null ? "No estás en un gremio" : "Ese jugador no está conectado");
                            return 0;
                        }
                        CompoundTag t = gremios(p.server).getCompound(g);
                        ListTag inv = t.getList("inv", Tag.TAG_STRING);
                        inv.add(StringTag.valueOf(o.getUUID().toString()));
                        t.put("inv", inv);
                        sucio(p.server);
                        o.displayClientMessage(Component.literal("§2" + p.getName().getString() + " te invitó al gremio «" + g + "». §fEscribí §e/gremio unirse " + g), false);
                        PoliciaMod.msg(p, "Invitaste a " + o.getName().getString());
                        return 1;
                    })))
                    .then(Commands.literal("unirse").then(Commands.argument("nombre", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String n = StringArgumentType.getString(c, "nombre");
                        CompoundTag t = gremios(p.server).getCompound(n);
                        String u = p.getUUID().toString();
                        if (t.isEmpty() || gremioDe(p.server, u) != null) {
                            PoliciaMod.msg(p, t.isEmpty() ? "Ese gremio no existe" : "Ya estás en un gremio");
                            return 0;
                        }
                        ListTag inv = t.getList("inv", Tag.TAG_STRING);
                        if (!inv.removeIf(x -> x.getAsString().equals(u))) {
                            PoliciaMod.msg(p, "Necesitás una invitación de alguien del gremio");
                            return 0;
                        }
                        t.put("inv", inv);
                        ListTag m = t.getList("miembros", Tag.TAG_STRING);
                        m.add(StringTag.valueOf(u));
                        t.put("miembros", m);
                        sucio(p.server);
                        avisar(p.server, n, p.getName().getString() + " se unió al gremio");
                        PoliciaNivel.logro(p, "gremio");
                        return 1;
                    })))
                    .then(Commands.literal("salir").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String u = p.getUUID().toString();
                        String g = gremioDe(p.server, u);
                        if (g == null) {
                            return 0;
                        }
                        CompoundTag t = gremios(p.server).getCompound(g);
                        ListTag m = t.getList("miembros", Tag.TAG_STRING);
                        m.removeIf(x -> x.getAsString().equals(u));
                        t.put("miembros", m);
                        if (m.isEmpty()) {
                            gremios(p.server).remove(g);
                            PoliciaMod.msg(p, "Eras el último: el gremio «" + g + "» se disolvió");
                        } else {
                            if (u.equals(t.getString("jefe"))) {
                                t.putString("jefe", m.getString(0));
                            }
                            avisar(p.server, g, p.getName().getString() + " dejó el gremio");
                        }
                        sucio(p.server);
                        return 1;
                    }))
                    .then(Commands.literal("echar").then(Commands.argument("jugador", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String g = gremioDe(p.server, p.getUUID().toString());
                        ServerPlayer o = p.server.getPlayerList().getPlayerByName(StringArgumentType.getString(c, "jugador"));
                        if (g == null || o == null || !p.getUUID().toString().equals(gremios(p.server).getCompound(g).getString("jefe"))) {
                            PoliciaMod.msg(p, "Solo el jefe del gremio puede echar a alguien (y tiene que estar conectado)");
                            return 0;
                        }
                        ListTag m = gremios(p.server).getCompound(g).getList("miembros", Tag.TAG_STRING);
                        m.removeIf(x -> x.getAsString().equals(o.getUUID().toString()));
                        gremios(p.server).getCompound(g).put("miembros", m);
                        sucio(p.server);
                        avisar(p.server, g, o.getName().getString() + " fue echado del gremio");
                        o.displayClientMessage(Component.literal("§cTe echaron del gremio «" + g + "»"), false);
                        return 1;
                    })))
                    .then(Commands.literal("cofre").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String g = gremioDe(p.server, p.getUUID().toString());
                        if (g == null) {
                            PoliciaMod.msg(p, "No estás en un gremio");
                            return 0;
                        }
                        if (COFRE_ABIERTO.contains(g)) {
                            PoliciaMod.msg(p, "Otro miembro está usando el cofre del gremio");
                            return 0;
                        }
                        COFRE_ABIERTO.add(g);
                        CompoundTag t = gremios(p.server).getCompound(g);
                        MinecraftServer srv = p.server;
                        SimpleContainer cont = new SimpleContainer(27) {
                            @Override
                            public void stopOpen(Player pl) {
                                super.stopOpen(pl);
                                ListTag l = new ListTag();
                                for (int i = 0; i < this.getContainerSize(); i++) {
                                    ItemStack st = this.getItem(i);
                                    if (!st.isEmpty()) {
                                        CompoundTag it = st.save(new CompoundTag());
                                        it.putByte("Slot", (byte) i);
                                        l.add(it);
                                    }
                                }
                                gremios(srv).getCompound(g).put("cofre", l);
                                sucio(srv);
                                COFRE_ABIERTO.remove(g);
                            }
                        };
                        ListTag l = t.getList("cofre", Tag.TAG_COMPOUND);
                        for (int i = 0; i < l.size(); i++) {
                            CompoundTag it = l.getCompound(i);
                            int s = it.getByte("Slot") & 255;
                            if (s < 27) {
                                cont.setItem(s, ItemStack.of(it));
                            }
                        }
                        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, cont), Component.literal("Cofre del gremio «" + g + "»")));
                        return 1;
                    })));
        }

        static int info(ServerPlayer p) {
            String g = gremioDe(p.server, p.getUUID().toString());
            if (g == null) {
                PoliciaMod.msg(p, "No estás en un gremio. /gremio fundar <nombre> (" + PRECIO + " esmeraldas) o pedí una invitación");
                return 0;
            }
            CompoundTag t = gremios(p.server).getCompound(g);
            ListTag m = t.getList("miembros", Tag.TAG_STRING);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < m.size(); i++) {
                java.util.UUID u = java.util.UUID.fromString(m.getString(i));
                ServerPlayer o = p.server.getPlayerList().getPlayer(u);
                var perfil = p.server.getProfileCache() != null ? p.server.getProfileCache().get(u) : java.util.Optional.<com.mojang.authlib.GameProfile>empty();
                String nom = o != null ? o.getName().getString() : perfil.map(com.mojang.authlib.GameProfile::getName).orElse("?");
                sb.append(i > 0 ? ", " : "").append(m.getString(i).equals(t.getString("jefe")) ? "★" : "").append(nom).append(o != null ? " §a●§f" : "");
            }
            p.displayClientMessage(Component.literal("§2Gremio «" + g + "» §7(" + m.size() + " miembros)\n§f" + sb + "\n§7/g <mensaje> · /gremio cofre · /gremio invitar <jugador> · /gremio salir"), false);
            return 1;
        }
    }
}
