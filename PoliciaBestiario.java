package com.example.policia;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Bestiario: cuenta cuantos de cada tipo de criatura mataste (datos RPG,
// clave "bestiario"). La primera vez que matas un tipo nuevo da XP; con
// 10 / 50 / 150 muertes de un tipo le pegas 3 / 6 / 10 % mas fuerte.
public class PoliciaBestiario {
    public static final int[] UMBRAL = {10, 50, 150};
    public static final float[] BONO = {0.03f, 0.06f, 0.10f};
    public static final String[] RANGO = {"Visto", "Conocido", "Estudiado", "Maestría"};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosBestiario());
    }

    static CompoundTag datos(Player p) {
        CompoundTag r = PoliciaRPG.datos(p);
        if (!r.contains("bestiario")) {
            r.put("bestiario", new CompoundTag());
        }
        return r.getCompound("bestiario");
    }

    public static int rango(int n) {
        int r = 0;
        for (int u : UMBRAL) {
            if (n >= u) {
                r++;
            }
        }
        return r;
    }

    static String clave(LivingEntity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    public static class EventosBestiario {
        @SubscribeEvent
        public void muerteBestiario(LivingDeathEvent e) {
            if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() instanceof Player) {
                return;
            }
            CompoundTag b = datos(p);
            String k = clave(e.getEntity());
            int antes = b.getInt(k);
            b.putInt(k, antes + 1);
            if (antes == 0) {
                PoliciaRPG.sumarXp(p, 15);
                p.displayClientMessage(Component.literal("§d📖 Nueva entrada en el bestiario: §f" + e.getEntity().getType().getDescription().getString() + " §7(+15 XP)"), true);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.2f);
                PoliciaRPG.sync(p);
            } else if (rango(antes + 1) > rango(antes)) {
                int r = rango(antes + 1);
                p.displayClientMessage(Component.literal("§d📖 " + e.getEntity().getType().getDescription().getString() + ": §e" + RANGO[r] + " §7(+" + (int) (BONO[r - 1] * 100) + " % de daño contra ellos)"), false);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.5f);
                PoliciaRPG.sync(p);
            }
        }

        @SubscribeEvent
        public void danoBestiario(LivingHurtEvent e) {
            if (e.getSource().getEntity() instanceof ServerPlayer p && !(e.getEntity() instanceof Player)) {
                int r = rango(datos(p).getInt(clave(e.getEntity())));
                if (r > 0) {
                    e.setAmount(e.getAmount() * (1.0f + BONO[r - 1]));
                }
            }
        }
    }
}
