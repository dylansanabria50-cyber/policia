package com.example.policia;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 8: viaje en carruaje. Cada aldea es una parada: al entrar por
// primera vez se descubre (titulo con su nombre). Desde una parada (o tu
// casa) se abre el mapa (tecla M o la ficha) y se viaja pagando esmeraldas
// segun la distancia. El carruaje sale a los 3 s si no te pegan ni te movés.
public class PoliciaViaje {
    static final String[] PUEBLOS = {"Villa Robles", "Los Álamos", "San Telmo del Río", "Piedra Blanca", "El Molino", "Arroyo Seco", "Las Lomas", "Puerto Viejo", "Valle Verde", "La Herradura",
            "Cerro Alto", "Campo Dorado", "Tres Pinos", "La Cañada", "Río Manso", "Santa Brisa", "El Mirador", "Los Sauces", "Villa Trigal", "Monte Claro", "Agua Clara", "La Posta", "Los Nogales", "Cruz del Sur"};
    static final java.util.Set<UUID> VIAJANDO = new java.util.HashSet<UUID>();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosViaje());
    }

    static CompoundTag paradas(ServerPlayer p) {
        CompoundTag r = PoliciaRPG.datos(p);
        if (!r.contains("paradas")) {
            r.put("paradas", new CompoundTag());
        }
        return r.getCompound("paradas");
    }

    static String nombre(String clave) {
        return PUEBLOS[Math.floorMod(clave.hashCode(), PUEBLOS.length)];
    }

    // Revisa si el jugador esta en una aldea y la descubre.
    static void revisar(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        if (sl.dimension() != Level.OVERWORLD) {
            return;
        }
        StructureStart s = sl.structureManager().getStructureWithPieceAt(p.blockPosition(), StructureTags.VILLAGE);
        if (s == null || !s.isValid()) {
            return;
        }
        String clave = "v_" + s.getChunkPos().x + "_" + s.getChunkPos().z;
        CompoundTag mias = paradas(p);
        PoliciaMundo m = PoliciaMundo.get(sl.getServer());
        // Etapa 10: cada aldea tiene el almacen de Dona Rosa.
        CompoundTag glob = m.getSeccion("paradas").getCompound(clave);
        if (!glob.isEmpty() && !glob.getBoolean("rosa")) {
            glob.putBoolean("rosa", true);
            m.setDirty();
            PoliciaBanco.donaRosa(sl, glob.getInt("x") + 2, glob.getInt("y"), glob.getInt("z") + 2);
        }
        if (mias.contains(clave)) {
            return;
        }
        CompoundTag todas = m.getSeccion("paradas");
        CompoundTag par;
        if (todas.contains(clave)) {
            par = todas.getCompound(clave);
        } else {
            BlockPos c = s.getBoundingBox().getCenter();
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
            par = new CompoundTag();
            par.putString("n", nombre(clave));
            par.putInt("x", c.getX());
            par.putInt("y", y);
            par.putInt("z", c.getZ());
            todas.put(clave, par);
            m.setDirty();
        }
        mias.put(clave, par.copy());
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§f" + par.getString("n"))));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7Parada de carruaje descubierta · tecla M para viajar")));
        sl.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        PoliciaNivel.dar(p, 20);
        if (mias.size() >= 5) {
            PoliciaNivel.logro(p, "viajero");
        }
        PoliciaRPG.sync(p);
    }

    static boolean enParada(ServerPlayer p) {
        CompoundTag mias = paradas(p);
        for (String k : mias.getAllKeys()) {
            CompoundTag t = mias.getCompound(k);
            double dx = t.getInt("x") - p.getX();
            double dz = t.getInt("z") - p.getZ();
            if (dx * dx + dz * dz < 48 * 48) {
                return true;
            }
        }
        return PoliciaCasa.enSuCasa(p);
    }

    static int costo(ServerPlayer p, int x, int z) {
        double d = Math.sqrt((x - p.getX()) * (x - p.getX()) + (z - p.getZ()) * (z - p.getZ()));
        return Math.max(1, (int) (d / 250.0));
    }

    // Llega desde el mapa (PoliciaRed mensaje 7). "casa" = tu casa.
    static void viajar(ServerPlayer p, String clave) {
        if (VIAJANDO.contains(p.getUUID())) {
            return;
        }
        if (p.serverLevel().dimension() != Level.OVERWORLD) {
            PoliciaMod.msg(p, "El carruaje solo anda en el mundo normal");
            return;
        }
        int x;
        int y;
        int z;
        String n;
        boolean gratis;
        if ("casa".equals(clave)) {
            CompoundTag c = PoliciaCasa.casaDe(p.server, p.getUUID());
            if (c == null) {
                PoliciaMod.msg(p, "No tenés casa todavía");
                return;
            }
            x = c.getInt("x");
            y = c.getInt("y");
            z = c.getInt("z");
            n = "tu casa";
            gratis = true;
        } else {
            CompoundTag t = paradas(p).getCompound(clave);
            if (t.isEmpty()) {
                return;
            }
            x = t.getInt("x");
            y = t.getInt("y");
            z = t.getInt("z");
            n = t.getString("n");
            gratis = false;
        }
        if (!enParada(p)) {
            PoliciaMod.msg(p, "Tenés que estar en una parada (una aldea que conozcas) o en tu casa para tomar el carruaje");
            return;
        }
        int cuesta = gratis ? 0 : costo(p, x, z);
        if (cuesta > 0 && !PoliciaHabOficios.pagar(p, cuesta)) {
            return;
        }
        Vec3 desde = p.position();
        float vida = p.getHealth();
        ServerLevel sl = p.serverLevel();
        VIAJANDO.add(p.getUUID());
        final int fx = x;
        final int fz = z;
        final int fy = y;
        PoliciaHab.tarea(t -> {
            if (!p.isAlive() || p.hasDisconnected()) {
                VIAJANDO.remove(p.getUUID());
                return true;
            }
            if (p.position().distanceTo(desde) > 2.5 || p.getHealth() < vida - 0.01f) {
                VIAJANDO.remove(p.getUUID());
                PoliciaMod.msg(p, "El viaje se canceló" + (cuesta > 0 ? " (te devolvieron " + cuesta + " esmeraldas)" : ""));
                if (cuesta > 0) {
                    PoliciaHabOficios.dar(p, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, cuesta));
                }
                return true;
            }
            if (t % 20 == 0 && t < 60) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 5));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6" + (3 - t / 20))));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7El carruaje sale hacia " + n)));
                sl.playSound(null, p.blockPosition(), SoundEvents.HORSE_STEP, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            if (t >= 60) {
                VIAJANDO.remove(p.getUUID());
                PoliciaCine.salida(p, fx, fy, fz, n);
                return true;
            }
            if (t >= 99999) {
                sl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 30, 0.6, 0.4, 0.6, 0.05);
                int yy = Math.max(fy, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fx, fz));
                p.teleportTo(fx + 0.5, yy + 0.1, fz + 0.5);
                sl.playSound(null, p.blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.PLAYERS, 1.0f, 1.0f);
                sl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 30, 0.6, 0.4, 0.6, 0.05);
                p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§f" + Character.toUpperCase(n.charAt(0)) + n.substring(1))));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7Llegaste en carruaje")));
                return true;
            }
            return false;
        });
    }

    public static class EventosViaje {
        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p && p.tickCount % 40 == 21) {
                revisar(p);
            }
        }
    }
}
