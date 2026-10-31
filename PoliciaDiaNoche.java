package com.example.policia;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 8: franjas del dia (amanecer, dia, atardecer, noche) con aviso al
// cambiar y efectos por oficio: de noche el ladron con el modo activo corre
// mas (sigilo nocturno) y el policia ve en la oscuridad (patrulla nocturna);
// de dia el mercader tiene un poco mas de suerte.
public class PoliciaDiaNoche {
    static final String[] FRANJA = {"Amanecer", "Día", "Atardecer", "Noche"};
    static int ultima = -1;

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Eventos());
    }

    public static int franja(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        if (t >= 23000 || t < 1000) {
            return 0;
        }
        if (t < 11500) {
            return 1;
        }
        if (t < 13000) {
            return 2;
        }
        return 3;
    }

    static String aviso(int f) {
        return switch (f) {
            case 0 -> "§e☀ Amanece en la comarca §7· abren los puestos";
            case 1 -> "§f☀ Pleno día §7· buen momento para comerciar";
            case 2 -> "§6☾ Atardece §7· los ladrones se preparan";
            default -> "§9☾ Cae la noche §7· más monstruos, sigilo para los ladrones y patrulla para la policía";
        };
    }

    public static class Eventos {
        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) {
                return;
            }
            ServerLevel ow = e.getServer().overworld();
            int f = franja(ow.getDayTime());
            if (ultima >= 0 && f != ultima) {
                for (ServerPlayer p : ow.players()) {
                    p.displayClientMessage(Component.literal(aviso(f)), true);
                }
            }
            ultima = f;
            if (e.getServer().getTickCount() % 100 != 0) {
                return;
            }
            for (ServerPlayer p : ow.players()) {
                String job = PoliciaMod.job(p);
                boolean modo = p.getPersistentData().getBoolean("pol_on");
                if (f == 3 && modo && "ladron".equals(job)) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, true, false, true));
                } else if (f == 3 && modo && "policia".equals(job)) {
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false, true));
                } else if (f == 1 && "mercader".equals(job)) {
                    p.addEffect(new MobEffectInstance(MobEffects.LUCK, 120, 0, true, false, true));
                }
            }
        }
    }
}
