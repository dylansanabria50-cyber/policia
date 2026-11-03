package com.example.policia;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Llegada en carruaje: la primera vez que entras al mundo un carruaje te
// trae por un camino hasta el pueblo mas cercano, con tomas de camara de
// pelicula (PoliciaCineCliente). Aca se elige el pueblo y el camino, se
// deja al jugador en la parada y se lo protege mientras dura la escena.
// Se puede volver a ver desde Ficha -> Opciones.
public class PoliciaCine {
    static final int LARGO = 64;
    static final Map<UUID, Long> EN_CURSO = new HashMap<UUID, Long>();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosCine());
    }

    // Altura del suelo cargando el chunk antes: si no esta cargado,
    // Minecraft devuelve el fondo del mundo (y = -64).
    static int altura(ServerLevel sl, Heightmap.Types tipo, int x, int z) {
        sl.getChunk(x >> 4, z >> 4);
        return sl.getHeight(tipo, x, z);
    }

    static void empezar(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        if (sl.dimension() != Level.OVERWORLD || EN_CURSO.containsKey(p.getUUID())) {
            return;
        }
        BlockPos base = p.blockPosition();
        BlockPos v = null;
        try {
            v = sl.findNearestMapStructure(StructureTags.VILLAGE, base, 24, false);
        } catch (Throwable ignored) {
        }
        Vec3 centro;
        String nombre;
        if (v != null) {
            int y = altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, v.getX(), v.getZ());
            StructureStart st = sl.structureManager().getStructureWithPieceAt(new BlockPos(v.getX(), y - 1, v.getZ()), StructureTags.VILLAGE);
            String clave;
            if (st != null && st.isValid()) {
                BlockPos c = st.getBoundingBox().getCenter();
                clave = "v_" + st.getChunkPos().x + "_" + st.getChunkPos().z;
                centro = new Vec3(c.getX() + 0.5, altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()), c.getZ() + 0.5);
            } else {
                clave = "v_" + (v.getX() >> 4) + "_" + (v.getZ() >> 4);
                centro = new Vec3(v.getX() + 0.5, y, v.getZ() + 0.5);
            }
            CompoundTag glob = PoliciaMundo.get(sl.getServer()).getSeccion("paradas").getCompound(clave);
            nombre = glob.contains("n") ? glob.getString("n") : PoliciaViaje.nombre(clave);
        } else {
            centro = p.position();
            nombre = "";
        }
        empezarEn(p, centro, nombre, false);
    }

    // Llegada a un lugar (la del principio o la de un viaje).
    static void empezarEn(ServerPlayer p, Vec3 centro, String nombre, boolean viaje) {
        ServerLevel sl = p.serverLevel();
        final int LARGO = viaje ? 40 : PoliciaCine.LARGO;
        final int INICIO = LARGO + 8;
        // el camino mas prolijo de 8 direcciones: poca pendiente, sin agua ni arboles
        double mejor = Double.MAX_VALUE;
        int[] mejorY = null;
        Vec3 mejorDir = null;
        double giro = sl.random.nextDouble() * Math.PI / 2;
        for (int k = 0; k < 4; k++) {
            double a = giro + k * Math.PI / 2;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            int[] ys = new int[LARGO + 1];
            double pena = 0;
            for (int i = 0; i <= LARGO; i++) {
                Vec3 q = centro.add(dir.scale(INICIO - i));
                int x = (int) Math.floor(q.x);
                int z = (int) Math.floor(q.z);
                int y = altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                ys[i] = y;
                if (i > 0) {
                    pena += Math.abs(y - ys[i - 1]) * 3;
                }
                if (!sl.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty()) {
                    pena += 8;
                }
                if (altura(sl, Heightmap.Types.MOTION_BLOCKING, x, z) > y + 1) {
                    pena += 2;
                }
            }
            if (pena < mejor) {
                mejor = pena;
                mejorY = ys;
                mejorDir = dir;
            }
        }
        // alturas suavizadas para que el carruaje no salte
        float[] ys = new float[LARGO + 1];
        for (int i = 0; i <= LARGO; i++) {
            float s = 0;
            int n = 0;
            for (int j = Math.max(0, i - 3); j <= Math.min(LARGO, i + 3); j++) {
                s += mejorY[j];
                n++;
            }
            ys[i] = Math.max(s / n, mejorY[i] - 0.4f);
        }
        Vec3 ini = centro.add(mejorDir.scale(INICIO));
        Vec3 fin = centro.add(mejorDir.scale(INICIO - LARGO));
        Vec3 adelante = fin.subtract(ini).normalize();
        Vec3 derecha = new Vec3(-adelante.z, 0, adelante.x);
        // el jugador baja del carruaje: queda al lado de la puerta
        Vec3 bajada = fin.add(derecha.scale(1.9));
        int yb = altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(bajada.x), (int) Math.floor(bajada.z));
        float yaw = (float) (Math.atan2(-(centro.x - bajada.x), centro.z - bajada.z) * 180 / Math.PI);
        if (yb <= sl.getMinBuildHeight() + 2 || mejorY[LARGO] <= sl.getMinBuildHeight() + 2) {
            // sin suelo seguro: no hay escena y el jugador no se mueve
            if (viaje) {
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("§cEl carruaje no encontró camino. Probá de nuevo."), false);
            }
            terminar(p, false);
            PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_cancelar", new CompoundTag()));
            return;
        }
        p.teleportTo(sl, bajada.x, yb, bajada.z, yaw, 0);
        p.setDeltaMovement(Vec3.ZERO);
        if (!p.isCreative() && !p.isSpectator()) {
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            p.getPersistentData().putBoolean("cine_inv", true);
        }
        CompoundTag d = new CompoundTag();
        d.putDouble("x0", ini.x);
        d.putDouble("z0", ini.z);
        d.putDouble("x1", fin.x);
        d.putDouble("z1", fin.z);
        int[] yi = new int[ys.length];
        for (int i = 0; i < ys.length; i++) {
            yi[i] = Math.round(ys[i] * 100);
        }
        d.putIntArray("ys", yi);
        d.putString("nombre", nombre);
        d.putBoolean("viaje", viaje);
        d.putDouble("cx", centro.x);
        d.putDouble("cy", centro.y);
        d.putDouble("cz", centro.z);
        PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_llegada", d));
        EN_CURSO.put(p.getUUID(), sl.getGameTime());
    }

    // Salida de un viaje: el carruaje te levanta en la parada y se va por el
    // camino; a los 4,7 s (con la pantalla en negro) llegas al destino.
    static void salida(ServerPlayer p, int x, int y, int z, String nombre) {
        ServerLevel sl = p.serverLevel();
        EN_CURSO.put(p.getUUID(), sl.getGameTime());
        if (!p.isCreative() && !p.isSpectator()) {
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            p.getPersistentData().putBoolean("cine_inv", true);
        }
        Vec3 adelante = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 derecha = new Vec3(-adelante.z, 0, adelante.x);
        Vec3 ini = p.position().subtract(derecha.scale(1.9));
        Vec3 fin = ini.add(adelante.scale(30));
        int n = 30;
        int[] ys = new int[n + 1];
        float prev = (float) p.getY();
        for (int i = 0; i <= n; i++) {
            Vec3 q = ini.lerp(fin, i / (double) n);
            float h = altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(q.x), (int) Math.floor(q.z));
            if (i == 0) {
                h = (float) p.getY();
            }
            h = Mth.clamp(h, prev - 1.2f, prev + 1.2f);
            prev = h;
            ys[i] = Math.round(h * 100);
        }
        CompoundTag d = new CompoundTag();
        d.putDouble("x0", ini.x);
        d.putDouble("z0", ini.z);
        d.putDouble("x1", fin.x);
        d.putDouble("z1", fin.z);
        d.putIntArray("ys", ys);
        d.putString("nombre", nombre);
        PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_salida", d));
        final java.util.UUID id = p.getUUID();
        final String fn = nombre;
        PoliciaHab.tarea(t -> {
            if (t < 95) {
                return false;
            }
            ServerPlayer q = p.server.getPlayerList().getPlayer(id);
            if (q != null && q.isAlive()) {
                EN_CURSO.remove(id);
                int yy = Math.max(y, altura(q.serverLevel(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
                empezarEn(q, new Vec3(x + 0.5, yy, z + 0.5), Character.toUpperCase(fn.charAt(0)) + fn.substring(1), true);
            }
            return true;
        });
    }

    static void terminar(ServerPlayer p, boolean vista) {
        boolean estaba = EN_CURSO.remove(p.getUUID()) != null;
        if (p.getPersistentData().getBoolean("cine_inv")) {
            p.getPersistentData().remove("cine_inv");
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().invulnerable = false;
                p.onUpdateAbilities();
            }
        }
        if (vista && estaba) {
            PoliciaRPG.datos(p).putBoolean("intro", true);
            PoliciaViaje.revisar(p);
            PoliciaRPG.sync(p);
        }
    }

    public static class EventosCine {
        @SubscribeEvent
        public void entrarCine(PlayerEvent.PlayerLoggedInEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            terminar(p, false);
            if (PoliciaRPG.datos(p).getBoolean("intro")) {
                return;
            }
            final UUID id = p.getUUID();
            PoliciaHab.tarea(t -> {
                if (t < 60) {
                    return false;
                }
                ServerPlayer q = p.server.getPlayerList().getPlayer(id);
                if (q != null && q.isAlive()) {
                    empezar(q);
                }
                return true;
            });
        }

        @SubscribeEvent
        public void salirCine(PlayerEvent.PlayerLoggedOutEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                terminar(p, false);
            }
        }

        // si el cliente no avisa (se cerro, etc.), se termina sola
        @SubscribeEvent
        public void tickCine(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.getServer() == null || EN_CURSO.isEmpty()) {
                return;
            }
            long ahora = e.getServer().overworld().getGameTime();
            for (UUID u : new java.util.ArrayList<UUID>(EN_CURSO.keySet())) {
                ServerPlayer p = e.getServer().getPlayerList().getPlayer(u);
                if (p == null) {
                    EN_CURSO.remove(u);
                } else if (ahora - EN_CURSO.get(u) > 900) {
                    terminar(p, true);
                }
            }
        }
    }
}
