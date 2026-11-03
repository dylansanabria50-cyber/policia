package com.example.policia;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

// Reparte los pedidos que mandan las pantallas nuevas (mensaje Accion).
public class PoliciaAcciones {
    static void recibir(ServerPlayer p, String tipo, CompoundTag d) {
        switch (tipo) {
            case "espec" -> PoliciaEspecial.elegir(p, d.getString("id"));
            default -> {
            }
        }
    }
}
