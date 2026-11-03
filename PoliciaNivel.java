package com.example.policia;

import net.minecraft.advancements.Advancement;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 4: de donde sale la XP del modo RPG, que pasa al subir de nivel,
// los atributos Destreza (critico) e Ingenio (XP extra) y los logros
// (advancements propios en data/policia/advancements/oficios/).
public class PoliciaNivel {
    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosNivel());
    }

    // ---------------- Contadores (para la ficha y los logros) ----------------
    static CompoundTag cont(Player p) {
        CompoundTag d = PoliciaRPG.datos(p);
        if (!d.contains("cont")) {
            d.put("cont", new CompoundTag());
        }
        return d.getCompound("cont");
    }

    static int sumar(Player p, String k, int n) {
        CompoundTag c = cont(p);
        int v = c.getInt(k) + n;
        c.putInt(k, v);
        return v;
    }

    // ---------------- XP ----------------
    // Ingenio: +2 % de XP por punto sobre la base.
    static int conIngenio(Player p, int xp) {
        int ing = PoliciaRPG.atributo(p, "ingenio") - PoliciaRPG.BASE_ATRIBUTO;
        float anillo = PoliciaRareza.anillos(p, 3) * 0.10f;
        return Math.max(1, Math.round(xp * (1.0f + Math.max(0, ing) * 0.02f + anillo + PoliciaBotin.bonoXp(p) + PoliciaEspecial.bonoXp(p) + (p instanceof net.minecraft.server.level.ServerPlayer sp ? PoliciaAlcalde.bonoXp(sp) : 0.0f))));
    }

    static void dar(ServerPlayer p, int xp) {
        PoliciaRPG.sumarXp(p, conIngenio(p, xp));
    }

    // La llama PoliciaAnim.play cada vez que se usa una habilidad de verdad.
    static void habilidad(ServerPlayer p, String id) {
        if ("modo".equals(id)) {
            logro(p, "transformacion");
            return;
        }
        PoliciaDiarias.habilidad(p, id);
        int total = sumar(p, "habilidades", 1);
        int deEsta = sumar(p, "h_" + id, 1);
        dar(p, 4);
        if (total >= 10) {
            logro(p, "habilidades_10");
        }
        if (total >= 100) {
            logro(p, "habilidades_100");
        }
        if ("pol_esposas".equals(id) && deEsta >= 10) {
            logro(p, "mano_de_hierro");
        }
        if (id.startsWith("alb_muro") && deEsta >= 20) {
            logro(p, "constructor");
        }
        if ("lad_escape".equals(id) && deEsta >= 10) {
            logro(p, "fantasma");
        }
        if (id.startsWith("her_") && cont(p).getInt("h_her_total") + 1 >= 25) {
            logro(p, "forjador");
        }
        if (id.startsWith("her_")) {
            sumar(p, "h_her_total", 1);
        }
    }

    // La llama PoliciaRPG.sumarXp cuando se sube de nivel.
    static void subio(ServerPlayer p, int nivel, int subidos) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6§l¡NIVEL " + nivel + "!")));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§e+" + subidos * 3 + " puntos de atributo §7(tecla I: ficha)")));
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.2, p.getZ(), 70, 0.6, 0.9, 0.6, 0.45);
        sl.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 2.0, p.getZ(), 30, 0.4, 0.3, 0.4, 0.15);
        sl.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
        if (nivel >= 5) {
            logro(p, "nivel_5");
        }
        if (nivel >= 10) {
            logro(p, "nivel_10");
        }
        if (nivel >= 25) {
            logro(p, "nivel_25");
        }
        if (nivel >= PoliciaRPG.NIVEL_MAX) {
            logro(p, "nivel_50");
        }
    }

    // La llama PoliciaRPG.subirAtributo.
    static void atributoSubido(ServerPlayer p, String a) {
        if (PoliciaRPG.atributo(p, a) >= PoliciaRPG.MAX_ATRIBUTO) {
            logro(p, "maestria");
        }
    }

    // Etapa 5: copia para el cliente de la mision del jefe (para el diario
    // y los marcadores ! / ? sobre los jefes). Solo se manda si cambio.
    static void estadoMision(ServerPlayer p) {
        CompoundTag d = PoliciaRPG.datos(p);
        PoliciaJefe.Def def = PoliciaJefe.active(p);
        int estado = def == null ? 0 : PoliciaJefe.done(p) ? 2 : 1;
        String desc = def == null ? "" : def.desc;
        int prog = def == null ? 0 : PoliciaJefe.prog(p);
        int need = def == null ? 0 : def.need;
        if (d.getInt("jm_estado") == estado && d.getString("jm_desc").equals(desc) && d.getInt("jm_prog") == prog && d.getInt("jm_need") == need) {
            return;
        }
        d.putInt("jm_estado", estado);
        d.putString("jm_desc", desc);
        d.putInt("jm_prog", prog);
        d.putInt("jm_need", need);
        PoliciaRPG.sync(p);
    }

    // ---------------- Logros ----------------
    static void logro(ServerPlayer p, String id) {
        try {
            Advancement a = p.server.getAdvancements().getAdvancement(new ResourceLocation(PoliciaMod.ID, "oficios/" + id));
            if (a != null && !p.getAdvancements().getOrStartProgress(a).isDone()) {
                for (String c : a.getCriteria().keySet()) {
                    p.getAdvancements().award(a, c);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- Eventos ----------------
    public static class EventosNivel {
        @SubscribeEvent
        public void entrar(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                logro(p, "raiz");
            }
        }

        // Matar: 2 XP + un tercio de la vida maxima del bicho (jefes dan mucho).
        @SubscribeEvent
        public void matar(LivingDeathEvent e) {
            if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) {
                return;
            }
            LivingEntity v = e.getEntity();
            int xp = v instanceof Player ? 12 : Mth.clamp(2 + Math.round(v.getMaxHealth() / 3.0f), 2, 250);
            dar(p, xp);
            int n = sumar(p, "muertes", 1);
            if (n >= 50) {
                logro(p, "cazador");
            }
            if (v.getMaxHealth() >= 150.0f) {
                logro(p, "matagigantes");
            }
        }

        // Minar menas: 4 XP.
        @SubscribeEvent
        public void romper(BlockEvent.BreakEvent e) {
            if (e.getPlayer() instanceof ServerPlayer p && !p.isCreative() && e.getState().is(Tags.Blocks.ORES)) {
                dar(p, 4);
                if (sumar(p, "menas", 1) >= 100) {
                    logro(p, "minero");
                }
            }
        }

        // Cada logro ganado (de Minecraft o del mod) da XP.
        @SubscribeEvent
        public void logroGanado(AdvancementEvent.AdvancementEarnEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            Advancement a = e.getAdvancement();
            if (a.getDisplay() == null || a.getParent() == null) {
                return;
            }
            dar(p, PoliciaMod.ID.equals(a.getId().getNamespace()) ? 30 : 15);
        }

        // Destreza: 1 % de critico por punto (x1,5 de dano cuerpo a cuerpo).
        @SubscribeEvent
        public void golpe(LivingHurtEvent e) {
            if (!(e.getSource().getDirectEntity() instanceof ServerPlayer p) || e.getSource().getEntity() != p) {
                return;
            }
            int des = PoliciaRPG.atributo(p, "destreza");
            if (PoliciaHabObra.DOMINIO.contains(p.getUUID()) || p.getRandom().nextFloat() < des * 0.01f) {
                e.setAmount(e.getAmount() * 1.5f);
                LivingEntity v = e.getEntity();
                PoliciaCombate.marcarCritico(v);
                p.serverLevel().sendParticles(ParticleTypes.CRIT, v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(), 14, 0.3, 0.3, 0.3, 0.3);
                p.serverLevel().playSound(null, v.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }

        // Elegir un oficio da su logro (se revisa cada 2 segundos).
        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 40 != 0) {
                return;
            }
            if (!PoliciaMod.job(p).isEmpty()) {
                logro(p, "oficio");
            }
            PoliciaNivel.estadoMision(p);
        }
    }
}
