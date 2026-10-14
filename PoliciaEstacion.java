package com.example.policia;

import net.minecraft.server.level.ServerLevel;

// Estaciones del año: cada una dura 8 días de Minecraft y cambia los
// trabajos: en invierno hay más pedidos de casas y pagan mejor, en verano
// hay más robos y el Perista paga más, en otoño el herrero cobra más por
// los encargos y en primavera abundan las misiones y eventos.
public class PoliciaEstacion {
    static final String[] NAME = new String[]{"primavera", "verano", "otoño", "invierno"};
    static final int DAYS = 8;

    static int of(ServerLevel sl) {
        long day = sl.getServer().overworld().getDayTime() / 24000L;
        return (int) (day / DAYS % 4L);
    }

    static String name(ServerLevel sl) {
        return NAME[PoliciaEstacion.of(sl)];
    }

    // Multiplicador (en %) para el pago de cada oficio según la estación.
    static int pay(ServerLevel sl, String job) {
        int s = PoliciaEstacion.of(sl);
        return switch (job) {
            case "albanil" -> s == 3 ? 150 : 100;
            case "ladron" -> s == 1 ? 150 : 100;
            case "herrero" -> s == 2 ? 150 : 100;
            case "policia" -> s == 1 ? 125 : 100;
            default -> 100;
        };
    }

    static int scale(ServerLevel sl, String job, int amount) {
        return Math.max(1, amount * PoliciaEstacion.pay(sl, job) / 100);
    }

    // Eventos aleatorios más frecuentes en primavera (y robos en verano).
    static int eventDelay(ServerLevel sl, int base) {
        return PoliciaEstacion.of(sl) == 0 ? base * 2 / 3 : base;
    }

    static String desc(ServerLevel sl) {
        return switch (PoliciaEstacion.of(sl)) {
            case 0 -> "Primavera: eventos aleatorios más seguidos.";
            case 1 -> "Verano: más robos. El Perista y las recompensas de policía pagan más.";
            case 2 -> "Otoño: los encargos al herrero pagan 50% más.";
            default -> "Invierno: los pedidos de construcción pagan 50% más.";
        };
    }
}
