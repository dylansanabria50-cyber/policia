package com.example.policia;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;

// Estaciones del año: cada una dura 8 días de Minecraft y cambia los
// trabajos: en invierno hay más pedidos de casas y pagan mejor, en verano
// hay más robos y el Perista paga más, en otoño el herrero cobra más por
// los encargos y en primavera abundan las misiones y eventos.
public class PoliciaEstacion {
    static final String[] NAME = new String[]{"primavera", "verano", "otoño", "invierno"};
    static final int DAYS = 8;

    // Etapa 13: efectos de las estaciones en el mundo.
    // - Clima: al amanecer se tira el clima del dia. Otono lluvioso, invierno
    //   con lluvia (nieve en biomas frios), verano con alguna tormenta.
    // - Cultivos: en invierno crecen a la mitad de velocidad.
    static long ultimoDia = -1;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PoliciaEstacion::tick);
        MinecraftForge.EVENT_BUS.addListener(PoliciaEstacion::cultivo);
    }

    static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl) || sl != sl.getServer().overworld()) {
            return;
        }
        long dia = sl.getDayTime() / 24000L;
        long hora = sl.getDayTime() % 24000L;
        if (dia == ultimoDia || hora < 0 || hora > 200) {
            return;
        }
        ultimoDia = dia;
        if (!sl.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE)) {
            return;
        }
        int s = PoliciaEstacion.of(sl);
        var r = sl.random;
        int lluvia = switch (s) {
            case 2 -> 45;
            case 3 -> 35;
            case 0 -> 20;
            default -> 10;
        };
        if (r.nextInt(100) < lluvia) {
            boolean tormenta = s == 1 ? r.nextInt(2) == 0 : r.nextInt(6) == 0;
            sl.setWeatherParameters(0, 6000 + r.nextInt(6000), true, tormenta);
        } else if (s == 1) {
            sl.setWeatherParameters(12000, 0, false, false);
        }
    }

    static void cultivo(BlockEvent.CropGrowEvent.Pre e) {
        if (e.getLevel() instanceof ServerLevel sl && PoliciaEstacion.of(sl) == 3 && sl.random.nextBoolean()) {
            e.setResult(Event.Result.DENY);
        }
    }

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
            case 2 -> "Otoño: los encargos al herrero pagan 50% más. Llueve seguido.";
            default -> "Invierno: los pedidos de construcción pagan 50% más y los cultivos crecen a la mitad.";
        };
    }
}
