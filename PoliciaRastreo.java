package com.example.policia;

import java.util.Random;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Policía contra ladrón: cuando un jugador ladrón tiene estrellas de
// búsqueda, los policías (con el modo activo) ven hacia dónde y a cuántos
// bloques está, más o menos. Cuantas más estrellas, más precisa la zona; con
// 5 estrellas el ladrón además brilla.
public class PoliciaRastreo {
    static final int[] ERR = new int[]{0, 120, 80, 45, 20, 0};
    static final String[] DIR = new String[]{"norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste"};
    static final int RANGE = 1500;

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaRastreo());
    }

    // Posición aproximada del ladrón: se corre al azar según las estrellas
    // y el error cambia una vez por minuto.
    static double[] approx(ServerPlayer t, int stars) {
        int err = ERR[Math.max(0, Math.min(5, stars))];
        if (err == 0) {
            return new double[]{t.getX(), t.getZ()};
        }
        Random r = new Random(t.getUUID().getLeastSignificantBits() ^ t.level().getGameTime() / 1200L * 31L);
        double a = r.nextDouble() * Math.PI * 2.0;
        double d = r.nextDouble() * err;
        return new double[]{t.getX() + Math.cos(a) * d, t.getZ() + Math.sin(a) * d};
    }

    static String dir(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        int i = (int) Math.floorMod(Math.round(ang / 45.0), 8L);
        return DIR[i];
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer srv = e.getServer();
        if (srv.getTickCount() % 60 != 0) {
            return;
        }
        boolean minute = srv.getTickCount() % 1200 == 0;
        for (ServerPlayer cop : srv.getPlayerList().getPlayers()) {
            if (!"policia".equals(PoliciaMod.job(cop)) || !PoliciaMod.on(cop)) continue;
            ServerPlayer best = null;
            double bd = Double.MAX_VALUE;
            double[] bp = null;
            for (ServerPlayer th : srv.getPlayerList().getPlayers()) {
                if (th == cop || !"ladron".equals(PoliciaMod.job(th)) || th.level() != cop.level() || th.isSpectator()) continue;
                int st = PoliciaLadron.wanted(th);
                if (st <= 0) continue;
                double[] ap = PoliciaRastreo.approx(th, st);
                double d = Math.hypot(ap[0] - cop.getX(), ap[1] - cop.getZ());
                if (d > RANGE || d >= bd) continue;
                bd = d;
                best = th;
                bp = ap;
            }
            if (best == null) continue;
            int st = PoliciaLadron.wanted(best);
            String where = bd < 12.0 ? "¡está muy cerca!" : "~" + (int) (Math.round(bd / 10.0) * 10L) + " bloques al " + PoliciaRastreo.dir(bp[0] - cop.getX(), bp[1] - cop.getZ());
            String prec = st >= 5 ? "ubicación exacta" : "zona de ±" + ERR[st] + " bloques";
            PoliciaMod.msg(cop, "§c" + "★".repeat(st) + " §fLadrón buscado §e" + best.getName().getString() + "§f: " + where + " §7(" + prec + ")");
            if (minute) {
                PoliciaMod.msg(best, "§cLa policía conoce tu zona aproximada (" + "★".repeat(st) + "). Escondete hasta que bajen las estrellas.");
            }
        }
        // Con 5 estrellas el ladrón brilla para todos.
        for (ServerPlayer th : srv.getPlayerList().getPlayers()) {
            if ("ladron".equals(PoliciaMod.job(th)) && PoliciaLadron.wanted(th) >= 5) {
                th.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false));
            }
        }
    }
}
