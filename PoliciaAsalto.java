package com.example.policia;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

// Etapa 11: asalto al banco en grupo y huellas del ladron.
// /asalto: un ladron (con el modo activo) junto al mostrador de Dona Rosa y
// su grupo cercano fuerzan la caja 30 s (no se alejen). A los 10 s suena la
// alarma: suben las estrellas y se avisa a todos los policias. Con la caja
// abierta hay 3 minutos para alejarse 120 bloques: ahi se cobra el botin
// (mas gente, mas botin) y sale el SE BUSCA en el diario. Uno por dia.
// Huellas: los ladrones buscados dejan pisadas que solo ven los policias.
public class PoliciaAsalto {
    static final DustParticleOptions HUELLA = new DustParticleOptions(new Vector3f(0.35f, 0.22f, 0.1f), 0.8f);

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosAsalto());
    }

    static WanderingTrader rosa(ServerPlayer p) {
        List<WanderingTrader> l = p.serverLevel().getEntitiesOfClass(WanderingTrader.class, p.getBoundingBox().inflate(10.0), w -> w.getPersistentData().getBoolean("pol_rosa"));
        return l.isEmpty() ? null : l.get(0);
    }

    static void policias(MinecraftServer s, String txt) {
        for (ServerPlayer o : s.getPlayerList().getPlayers()) {
            if ("policia".equals(PoliciaMod.job(o))) {
                o.displayClientMessage(Component.literal(txt), false);
                o.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0f, 2.0f);
            }
        }
    }

    static void titulo(ServerPlayer p, String t, String s) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(s)));
    }

    static int empezar(ServerPlayer p) {
        if (!"ladron".equals(PoliciaMod.job(p)) || !p.getPersistentData().getBoolean("pol_on")) {
            PoliciaMod.msg(p, "Solo un ladrón con el modo activo puede organizar el asalto");
            return 0;
        }
        WanderingTrader r = rosa(p);
        if (r == null) {
            PoliciaMod.msg(p, "Acercate al mostrador de Doña Rosa de una aldea (el banco del pueblo)");
            return 0;
        }
        MinecraftServer s = p.server;
        long hoy = PoliciaMundo.get(s).dia(s);
        if (PoliciaMundo.get(s).getSeccion("asalto").getLong("dia") == hoy + 1) {
            PoliciaMod.msg(p, "Hoy ya hubo un asalto: la guardia está alerta. Probá mañana");
            return 0;
        }
        PoliciaMundo.get(s).getSeccion("asalto").putLong("dia", hoy + 1);
        PoliciaMundo.get(s).setDirty();
        // banda: vos + tu grupo a 16 bloques
        List<ServerPlayer> banda = new ArrayList<ServerPlayer>();
        banda.add(p);
        Set<UUID> g = PoliciaGrupo.members(p.getUUID());
        if (g != null) {
            for (UUID u : g) {
                ServerPlayer o = s.getPlayerList().getPlayer(u);
                if (o != null && o != p && o.distanceTo(p) < 16) {
                    banda.add(o);
                }
            }
        }
        ServerLevel sl = p.serverLevel();
        Vec3 banco = r.position();
        String pueblo = "la aldea";
        for (String k : PoliciaViaje.paradas(p).getAllKeys()) {
            var t = PoliciaViaje.paradas(p).getCompound(k);
            if (Math.abs(t.getInt("x") - banco.x) < 64 && Math.abs(t.getInt("z") - banco.z) < 64) {
                pueblo = t.getString("n");
            }
        }
        final String lugar = pueblo;
        for (ServerPlayer o : banda) {
            titulo(o, "§c¡ASALTO!", "§7Forzá la caja: quedate cerca del mostrador 30 s");
        }
        Set<UUID> caidos = new HashSet<UUID>();
        PoliciaHab.tarea(t -> {
            // fase 1: forzar la caja (0-600 ticks)
            if (t < 600) {
                if (t % 20 == 0) {
                    int cerca = 0;
                    for (ServerPlayer o : banda) {
                        if (o.isAlive() && !o.hasDisconnected() && o.position().distanceTo(banco) < 10) {
                            cerca++;
                            o.displayClientMessage(Component.literal("§eForzando la caja... §f" + (t * 100 / 600) + " %"), true);
                        }
                    }
                    if (cerca == 0) {
                        for (ServerPlayer o : banda) {
                            o.displayClientMessage(Component.literal("§cSe alejaron todos del mostrador: el asalto falló"), false);
                        }
                        return true;
                    }
                    sl.playSound(null, net.minecraft.core.BlockPos.containing(banco), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8f, 0.6f + t * 0.001f);
                }
                if (t == 200) {
                    sl.playSound(null, net.minecraft.core.BlockPos.containing(banco), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 1.2f);
                    for (ServerPlayer o : banda) {
                        PoliciaLadron.crime(o, 3, "asalto al banco");
                    }
                    policias(s, "§c§l¡ALARMA! §fAsalto al banco en " + lugar + " §7(" + (int) banco.x + ", " + (int) banco.z + ")");
                }
                if (t > 200 && t % 30 == 0) {
                    sl.playSound(null, net.minecraft.core.BlockPos.containing(banco), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 1.2f);
                }
                return false;
            }
            if (t == 600) {
                for (ServerPlayer o : banda) {
                    titulo(o, "§6¡Caja abierta!", "§7Alejate 120 bloques en 3 minutos para cobrar");
                }
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, banco.x, banco.y + 1.5, banco.z, 30, 0.6, 0.6, 0.6, 0);
            }
            // fase 2: escapar (600-4200 ticks)
            boolean quedan = false;
            for (ServerPlayer o : banda) {
                if (caidos.contains(o.getUUID())) {
                    continue;
                }
                if (!o.isAlive() || o.hasDisconnected()) {
                    caidos.add(o.getUUID());
                    continue;
                }
                quedan = true;
                if (o.position().distanceTo(banco) >= 120) {
                    caidos.add(o.getUUID());
                    int botin = 8 + 6 * banda.size() + sl.random.nextInt(8);
                    PoliciaHabOficios.dar(o, new ItemStack(Items.EMERALD, botin));
                    PoliciaHabOficios.dar(o, new ItemStack(Items.GOLD_INGOT, 2 + sl.random.nextInt(4)));
                    PoliciaNivel.dar(o, 120);
                    PoliciaNivel.logro(o, "asalto");
                    PoliciaFacciones.sumar(o, "sombras", 8);
                    PoliciaFacciones.sumar(o, "guardia", -8);
                    titulo(o, "§a¡Escapaste!", "§f+" + botin + " esmeraldas · el seguro le devolvió el 80 % al banco");
                    PoliciaDiario.add(sl, "SE BUSCA: " + o.getName().getString() + ", por el asalto al banco de " + lugar);
                } else if (t % 100 == 0) {
                    long falta = (4200 - t) / 20;
                    o.displayClientMessage(Component.literal("§eAlejate del banco: §f" + (int) o.position().distanceTo(banco) + "/120 §7· " + falta + " s"), true);
                }
            }
            if (!quedan) {
                return true;
            }
            if (t >= 4200) {
                for (ServerPlayer o : banda) {
                    if (!caidos.contains(o.getUUID())) {
                        o.displayClientMessage(Component.literal("§cSe acabó el tiempo: la policía recuperó el botín"), false);
                    }
                }
                return true;
            }
            return false;
        });
        return 1;
    }

    public static class EventosAsalto {
        @SubscribeEvent
        public void comandos(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("asalto").executes(c -> empezar(c.getSource().getPlayerOrException())));
        }

        // Huellas que solo ven los policias (cada medio segundo).
        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) {
                return;
            }
            if (!"ladron".equals(PoliciaMod.job(p)) || PoliciaLadron.wanted(p) <= 0 || !p.onGround() || p.getDeltaMovement().horizontalDistanceSqr() < 0.001) {
                return;
            }
            ServerLevel sl = p.serverLevel();
            for (ServerPlayer o : sl.players()) {
                if (o != p && "policia".equals(PoliciaMod.job(o)) && o.getPersistentData().getBoolean("pol_on") && o.distanceTo(p) < 48) {
                    sl.sendParticles(o, HUELLA, true, p.getX(), p.getY() + 0.05, p.getZ(), 2, 0.12, 0, 0.12, 0);
                }
            }
        }
    }
}
