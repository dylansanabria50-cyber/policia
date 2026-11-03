package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Flecha de objetivo: el jugador elige a donde ir (historia, casa, una
// parada del mapa o una mazmorra) y arriba de la pantalla aparece una
// brujula con la direccion y la distancia. Se guarda en los datos RPG
// ("seguir"); vacio = automatico (la historia, si esta en curso).
public class PoliciaObjetivo {
    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosObjetivo());
    }

    // {clave, nombre, x, y, z} de todos los lugares que se pueden seguir.
    static ListTag lugares(ServerPlayer p) {
        ListTag l = new ListTag();
        CompoundTag h = PoliciaHistoria.datos(p);
        int cap = h.getInt("cap");
        if (cap >= 1 && cap <= 5 && !h.getBoolean("listo")) {
            BlockPos n = PoliciaHistoria.lugarNpc(p.server);
            l.add(lugar("historia", "Historia: " + PoliciaHistoria.TITULO[cap], n.getX(), n.getY(), n.getZ()));
        }
        try {
            CompoundTag casa = PoliciaCasa.casaDe(p.server, p.getUUID());
            if (casa != null) {
                l.add(lugar("casa", "Mi casa", casa.getInt("x"), casa.contains("y") ? casa.getInt("y") : 70, casa.getInt("z")));
            }
        } catch (Throwable ignored) {
        }
        CompoundTag jefe = PoliciaMundo.get(p.server).getSeccion("errante");
        if (jefe.contains("x")) {
            l.add(lugar("jefe", "Gólem de raíces (jefe)", jefe.getInt("x"), jefe.getInt("y"), jefe.getInt("z")));
        }
        CompoundTag par = PoliciaViaje.paradas(p);
        for (String k : par.getAllKeys()) {
            CompoundTag c = par.getCompound(k);
            l.add(lugar("p:" + k, c.contains("n") ? c.getString("n") : PoliciaViaje.nombre(k), c.getInt("x"), c.getInt("y"), c.getInt("z")));
        }
        return l;
    }

    static CompoundTag lugar(String k, String n, int x, int y, int z) {
        CompoundTag c = new CompoundTag();
        c.putString("k", k);
        c.putString("n", n);
        c.putInt("x", x);
        c.putInt("y", y);
        c.putInt("z", z);
        return c;
    }

    static void pedir(ServerPlayer p) {
        CompoundTag d = new CompoundTag();
        d.put("l", lugares(p));
        d.putString("sel", PoliciaRPG.datos(p).getString("seguir"));
        PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("ui_obj", d));
    }

    static void seguir(ServerPlayer p, String k) {
        PoliciaRPG.datos(p).putString("seguir", k);
        actualizar(p);
        pedir(p);
    }

    static void actualizar(ServerPlayer p) {
        String sel = PoliciaRPG.datos(p).getString("seguir");
        CompoundTag d = new CompoundTag();
        if (!"no".equals(sel) && p.level().dimension() == Level.OVERWORLD) {
            ListTag l = lugares(p);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                if (sel.isEmpty() ? c.getString("k").equals("historia") : c.getString("k").equals(sel)) {
                    d = c;
                    break;
                }
            }
        }
        PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("ui_flecha", d));
    }

    public static class EventosObjetivo {
        @SubscribeEvent
        public void tickObjetivo(TickEvent.PlayerTickEvent e) {
            if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p && p.tickCount % 60 == 23) {
                actualizar(p);
            }
        }
    }
}
