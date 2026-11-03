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

    // llegada ya calculada que espera a que el jugador suba al carruaje
    static final Map<UUID, CompoundTag> LISTA = new HashMap<UUID, CompoundTag>();

    static boolean ocupado(ServerPlayer p) {
        return EN_CURSO.containsKey(p.getUUID());
    }

    // Comienzo: el jugador espera en una parada de carruaje (escena), y
    // mientras tanto se prepara la llegada al pueblo mas cercano.
    static void empezar(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        if (sl.dimension() != Level.OVERWORLD || EN_CURSO.containsKey(p.getUUID())) {
            return;
        }
        EN_CURSO.put(p.getUUID(), sl.getGameTime());
        proteger(p);
        parada(p, !PoliciaRPG.datos(p).getBoolean("intro"));
        final UUID id = p.getUUID();
        PoliciaHab.tarea(t -> {
            if (t < 3) {
                return false;
            }
            ServerPlayer q = p.server.getPlayerList().getPlayer(id);
            if (q != null) {
                CompoundTag d = buscarYPreparar(q);
                LISTA.put(id, d == null ? new CompoundTag() : d);
            }
            return true;
        });
    }

    // El cliente avisa que el jugador subio al carruaje de la parada.
    static void subio(ServerPlayer p) {
        CompoundTag d = LISTA.remove(p.getUUID());
        if (d == null) {
            d = buscarYPreparar(p);
        }
        if (d == null || d.isEmpty()) {
            PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_cancelar", new CompoundTag()));
            terminar(p, true);
            return;
        }
        lanzar(p, d);
    }

    static void proteger(ServerPlayer p) {
        if (!p.isCreative() && !p.isSpectator()) {
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            p.getPersistentData().putBoolean("cine_inv", true);
        }
    }

    static CompoundTag buscarYPreparar(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
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
            centro = p.position().add(40, 0, 0);
            nombre = "";
        }
        return preparar(p, centro, nombre, false);
    }

    // Llegada a un lugar (la del principio o la de un viaje).
    static void empezarEn(ServerPlayer p, Vec3 centro, String nombre, boolean viaje) {
        CompoundTag d = preparar(p, centro, nombre, viaje);
        if (d == null) {
            if (viaje) {
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("§cEl carruaje no encontró camino. Probá de nuevo."), false);
            }
            terminar(p, false);
            PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_cancelar", new CompoundTag()));
            return;
        }
        lanzar(p, d);
    }

    // Calcula el camino y la bajada (no mueve al jugador). null = sin suelo seguro.
    static CompoundTag preparar(ServerPlayer p, Vec3 centro, String nombre, boolean viaje) {
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
            return null;
        }
        CompoundTag d = new CompoundTag();
        d.putDouble("bx", bajada.x);
        d.putInt("by", yb);
        d.putDouble("bz", bajada.z);
        d.putFloat("byaw", yaw);
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
        return d;
    }

    // Deja al jugador en la bajada y arranca la escena de llegada.
    static void lanzar(ServerPlayer p, CompoundTag d) {
        ServerLevel sl = p.serverLevel();
        p.teleportTo(sl, d.getDouble("bx"), d.getInt("by"), d.getDouble("bz"), d.getFloat("byaw"), 0);
        p.setDeltaMovement(Vec3.ZERO);
        proteger(p);
        PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_llegada", d));
        EN_CURSO.put(p.getUUID(), sl.getGameTime());
    }

    // ---------------- La parada del comienzo ----------------
    // Se construye donde aparece el jugador: techito, banco, farol, cartel
    // y un camino de tierra por donde llega el carruaje.
    static void parada(ServerPlayer p, boolean construir) {
        ServerLevel sl = p.serverLevel();
        BlockPos base = p.blockPosition();
        net.minecraft.core.Direction mejorD = net.minecraft.core.Direction.SOUTH;
        double mejor = Double.MAX_VALUE;
        for (net.minecraft.core.Direction dd : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            net.minecraft.core.Direction r = dd.getClockWise();
            double pena = 0;
            int prev = Integer.MIN_VALUE;
            for (int i = -40; i <= 12; i += 2) {
                BlockPos q = base.relative(r, -2).relative(dd, i);
                int y = altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ());
                if (prev != Integer.MIN_VALUE) {
                    pena += Math.abs(y - prev) * 3;
                }
                if (!sl.getBlockState(new BlockPos(q.getX(), y - 1, q.getZ())).getFluidState().isEmpty()) {
                    pena += 6;
                }
                prev = y;
            }
            if (pena < mejor) {
                mejor = pena;
                mejorD = dd;
            }
        }
        net.minecraft.core.Direction D = mejorD;
        net.minecraft.core.Direction R = D.getClockWise();
        int y0 = altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
        BlockPos P = new BlockPos(base.getX(), y0, base.getZ());
        net.minecraft.world.level.block.state.BlockState tabla = net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState();
        net.minecraft.world.level.block.state.BlockState poste = net.minecraft.world.level.block.Blocks.SPRUCE_FENCE.defaultBlockState();
        net.minecraft.world.level.block.state.BlockState losa = net.minecraft.world.level.block.Blocks.SPRUCE_SLAB.defaultBlockState();
        if (construir) {
        // piso, aire adentro y techo
        for (int a = -3; a <= 3; a++) {
            for (int b = -1; b <= 3; b++) {
                BlockPos q = P.relative(D, a).relative(R, b);
                boolean adentro = Math.abs(a) <= 2 && b >= 0 && b <= 2;
                if (adentro) {
                    sl.setBlock(q.below(), tabla, 3);
                    for (int h = 0; h < 3; h++) {
                        sl.setBlock(q.above(h), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                sl.setBlock(q.above(3), losa, 3);
            }
        }
        for (int a : new int[]{-2, 2}) {
            for (int b : new int[]{0, 2}) {
                for (int h = 0; h < 3; h++) {
                    sl.setBlock(P.relative(D, a).relative(R, b).above(h), poste, 3);
                }
            }
        }
        // banco mirando al camino
        for (int a = -1; a <= 1; a++) {
            sl.setBlock(P.relative(D, a).relative(R, 2), net.minecraft.world.level.block.Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, R), 3);
        }
        sl.setBlock(P.relative(R, 1).above(2), net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 3);
        // valija al lado del jugador
        sl.setBlock(P.relative(D, -2).relative(R, 1), net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), 3);
        // cartel
        BlockPos cartel = P.relative(D, -4);
        int yc = altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cartel.getX(), cartel.getZ());
        cartel = new BlockPos(cartel.getX(), yc, cartel.getZ());
        int rot = Math.floorMod(Math.round((R.getOpposite().toYRot()) / 22.5f), 16);
        sl.setBlock(cartel, net.minecraft.world.level.block.Blocks.SPRUCE_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, rot), 3);
        if (sl.getBlockEntity(cartel) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sb) {
            net.minecraft.world.level.block.entity.SignText tx = new net.minecraft.world.level.block.entity.SignText()
                    .setMessage(0, net.minecraft.network.chat.Component.literal("~ Parada ~"))
                    .setMessage(1, net.minecraft.network.chat.Component.literal("del Comienzo"))
                    .setMessage(3, net.minecraft.network.chat.Component.literal("Carruaje diario"));
            sb.setText(tx, true);
            sb.setChanged();
        }
        }
        // camino de tierra
        int[] ys = new int[53];
        for (int i = -40; i <= 12; i++) {
            for (int w = -1; w <= 1; w++) {
                BlockPos q = P.relative(R, -2 + w).relative(D, i);
                int y = altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ());
                BlockPos suelo = new BlockPos(q.getX(), y - 1, q.getZ());
                net.minecraft.world.level.block.state.BlockState bs = sl.getBlockState(suelo);
                if (construir && (bs.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || bs.is(net.minecraft.world.level.block.Blocks.DIRT) || bs.is(net.minecraft.world.level.block.Blocks.PODZOL) || bs.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT))) {
                    sl.setBlock(suelo, net.minecraft.world.level.block.Blocks.DIRT_PATH.defaultBlockState(), 3);
                }
                for (int h = 0; h < 3; h++) {
                    BlockPos arriba = suelo.above(1 + h);
                    if (construir && sl.getBlockState(arriba).canBeReplaced()) {
                        sl.setBlock(arriba, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                if (w == 0) {
                    ys[i + 40] = y;
                }
            }
        }
        float[] suaves = new float[53];
        for (int i = 0; i < 53; i++) {
            float acc = 0;
            int n = 0;
            for (int j = Math.max(0, i - 3); j <= Math.min(52, i + 3); j++) {
                acc += ys[j];
                n++;
            }
            suaves[i] = Math.max(acc / n, ys[i] - 0.4f);
        }
        // el jugador espera parado bajo el techo, mirando hacia donde viene el carruaje
        float yaw = D.getOpposite().toYRot();
        p.teleportTo(sl, P.getX() + 0.5, y0, P.getZ() + 0.5, yaw, 0);
        p.setDeltaMovement(Vec3.ZERO);
        BlockPos S = P.relative(R, -2);
        CompoundTag d = new CompoundTag();
        d.putDouble("x0", S.getX() + 0.5 - D.getStepX() * 40);
        d.putDouble("z0", S.getZ() + 0.5 - D.getStepZ() * 40);
        d.putDouble("x1", S.getX() + 0.5 + D.getStepX() * 12);
        d.putDouble("z1", S.getZ() + 0.5 + D.getStepZ() * 12);
        int[] yi = new int[53];
        for (int i = 0; i < 53; i++) {
            yi[i] = Math.round(suaves[i] * 100);
        }
        d.putIntArray("ys", yi);
        d.putFloat("parada", 40f);
        d.putDouble("jx", P.getX() + 0.5);
        d.putDouble("jy", y0);
        d.putDouble("jz", P.getZ() + 0.5);
        d.putString("nombre", "");
        PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_parada", d));
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
        LISTA.remove(p.getUUID());
        if (vista && estaba) {
            PoliciaRPG.datos(p).putBoolean("intro", true);
            PoliciaViaje.revisar(p);
            PoliciaRPG.sync(p);
            PoliciaOficio.login(p);
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
                PoliciaOficio.MANDADA.remove(p.getUUID());
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
