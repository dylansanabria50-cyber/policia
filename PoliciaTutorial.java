package com.example.policia;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Tutorial inicial: ocho pasos cortos que explican oficio, ficha,
// habilidades, combate, consumible rapido, mapa y brujula. El paso actual
// se guarda en los datos RPG ("tut"); el cliente avisa cuando se cumple
// (PoliciaTutorialCliente) y aca se da el premio. Se puede saltar desde
// Ficha -> Opciones. Los jugadores de nivel 10+ lo tienen hecho.
public class PoliciaTutorial {
    public static final String[] PASOS = {
            "Elegí un oficio: hablá con un jefe de oficio o escribí /oficio",
            "Abrí tu ficha de personaje con la tecla I",
            "Desde la ficha, abrí Habilidades y mirá tu árbol",
            "Usá una habilidad con la tecla Z (Mayús + Z la cambia)",
            "Derrotá 3 monstruos (de noche o en cuevas)",
            "Curate al toque con el consumible rápido (tecla V)",
            "Abrí el atlas con la tecla M (Mayús + M: mapa de viaje)",
            "En Ficha → Objetivo, elegí a dónde ir con la brújula"};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosTutorial());
    }

    static int paso(ServerPlayer p) {
        return PoliciaRPG.datos(p).getInt("tut");
    }

    static void ok(ServerPlayer p, int n) {
        CompoundTag d = PoliciaRPG.datos(p);
        int actual = d.getInt("tut");
        if (n != actual || actual >= PASOS.length) {
            return;
        }
        d.putInt("tut", actual + 1);
        PoliciaRPG.sumarXp(p, 10);
        if (actual + 1 >= PASOS.length) {
            PoliciaRPG.sumarXp(p, 100);
            PoliciaHabOficios.dar(p, new ItemStack(Items.EMERALD, 10));
            PoliciaHabOficios.dar(p, PoliciaCocina.comida(1, 3));
            PoliciaHabOficios.dar(p, PoliciaCocina.elixir(0, 1));
            PoliciaHistoria.titulo(p, "§6¡Tutorial completo!", "§7+100 XP, 10 esmeraldas y provisiones");
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
        } else {
            p.displayClientMessage(Component.literal("§a✔ §7Tutorial " + (actual + 1) + "/" + PASOS.length + " §8(+10 XP)"), true);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
        PoliciaRPG.sync(p);
    }

    static void saltar(ServerPlayer p) {
        PoliciaRPG.datos(p).putInt("tut", PASOS.length);
        PoliciaRPG.sync(p);
        p.displayClientMessage(Component.literal("§7Tutorial oculto."), true);
    }

    public static class EventosTutorial {
        @SubscribeEvent
        public void entrarTutorial(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                CompoundTag d = PoliciaRPG.datos(p);
                if (!d.contains("tut") && PoliciaRPG.nivel(p) >= 10) {
                    d.putInt("tut", PASOS.length);
                }
            }
        }
    }
}
