package com.example.policia;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Paradas de carruaje con campana. Hay una en cada aldea, otras sueltas por
// los caminos del mundo y la "del Comienzo" donde arranca el juego.
// Tocás la campana -> viene el carruaje (cinematica) -> el cochero pregunta
// a donde vas (ciudades que ya conociste y tu casa) -> subis y viajas.
// Registro en PoliciaMundo, seccion "campanas": clave = posicion de la
// campana, {x,y,z (donde se espera), d (direccion del camino), n (nombre)}.
public class PoliciaParadas {
    static final Map<UUID, ListTag> LLAMANDO = new HashMap<UUID, ListTag>();
    static final Map<UUID, CompoundTag> DESTINO = new HashMap<UUID, CompoundTag>();
    static final Map<UUID, Long> CD = new HashMap<UUID, Long>();
    static final String[] CAMPO = {"Cruce del Roble", "Posta del Viento", "Parada del Arroyo", "Cruce de los Pinos", "Posta Vieja", "Parada del Cerro",
            "Cruce del Molino", "Posta del Sauce", "Parada del Llano", "Cruce de Piedra", "Posta del Zorro", "Parada del Valle"};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosParadas());
    }

    static CompoundTag registro(MinecraftServer s) {
        return PoliciaMundo.get(s).getSeccion("campanas");
    }

    static String clave(BlockPos b) {
        return b.getX() + "_" + b.getY() + "_" + b.getZ();
    }

    // ---------------- Construccion ----------------
    // P = donde se espera (a nivel del piso), D = hacia donde va el carruaje,
    // el camino pasa 2 bloques a la izquierda (-R). Devuelve la campana.
    static BlockPos construir(ServerLevel sl, BlockPos P, Direction D, String nombre, int atras, int adelante) {
        Direction R = D.getClockWise();
        BlockState tabla = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState poste = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState losa = Blocks.SPRUCE_SLAB.defaultBlockState();
        for (int a = -3; a <= 3; a++) {
            for (int b = -1; b <= 3; b++) {
                BlockPos q = P.relative(D, a).relative(R, b);
                boolean adentro = Math.abs(a) <= 2 && b >= 0 && b <= 2;
                if (adentro) {
                    sl.setBlock(q.below(), tabla, 3);
                    // cimiento para que no quede flotando
                    for (int h = 2; h <= 4; h++) {
                        if (sl.getBlockState(q.below(h)).canBeReplaced()) {
                            sl.setBlock(q.below(h), Blocks.SPRUCE_LOG.defaultBlockState(), 3);
                        } else {
                            break;
                        }
                    }
                    for (int h = 0; h < 3; h++) {
                        sl.setBlock(q.above(h), Blocks.AIR.defaultBlockState(), 3);
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
        for (int a = -1; a <= 1; a++) {
            sl.setBlock(P.relative(D, a).relative(R, 2), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, R), 3);
        }
        sl.setBlock(P.relative(R, 1).above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        sl.setBlock(P.relative(D, -2).relative(R, 1), Blocks.BARREL.defaultBlockState(), 3);
        // la campana, colgada del techo entre los postes de adelante
        BlockPos campana = P.relative(D, 2).relative(R, 1).above(2);
        sl.setBlock(campana, Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, R).setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING), 3);
        // cartel
        BlockPos cartel = P.relative(D, -4);
        int yc = PoliciaCine.altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cartel.getX(), cartel.getZ());
        cartel = new BlockPos(cartel.getX(), yc, cartel.getZ());
        int rot = Math.floorMod(Math.round(R.getOpposite().toYRot() / 22.5f), 16);
        sl.setBlock(cartel, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rot), 3);
        if (sl.getBlockEntity(cartel) instanceof SignBlockEntity sb) {
            String n1 = nombre;
            String n2 = "";
            if (n1.length() > 15 && n1.contains(" ")) {
                int corte = n1.lastIndexOf(' ', 15);
                if (corte > 0) {
                    n2 = n1.substring(corte + 1);
                    n1 = n1.substring(0, corte);
                }
            }
            SignText tx = new SignText()
                    .setMessage(0, Component.literal("~ Parada ~"))
                    .setMessage(1, Component.literal(n1))
                    .setMessage(2, Component.literal(n2))
                    .setMessage(3, Component.literal("Tocá la campana"));
            sb.setText(tx, true);
            sb.setChanged();
        }
        // camino de tierra
        for (int i = -atras; i <= adelante; i++) {
            for (int w = -1; w <= 1; w++) {
                BlockPos q = P.relative(R, -2 + w).relative(D, i);
                int y = PoliciaCine.altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ());
                BlockPos suelo = new BlockPos(q.getX(), y - 1, q.getZ());
                BlockState bs = sl.getBlockState(suelo);
                if (bs.is(Blocks.GRASS_BLOCK) || bs.is(Blocks.DIRT) || bs.is(Blocks.PODZOL) || bs.is(Blocks.COARSE_DIRT)) {
                    sl.setBlock(suelo, Blocks.DIRT_PATH.defaultBlockState(), 3);
                }
                for (int h = 0; h < 3; h++) {
                    BlockPos arriba = suelo.above(1 + h);
                    BlockState ba = sl.getBlockState(arriba);
                    if (!ba.isAir() && ba.canBeReplaced() && ba.getFluidState().isEmpty()) {
                        sl.setBlock(arriba, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        return campana;
    }

    static void registrar(ServerLevel sl, BlockPos campana, BlockPos P, Direction D, String nombre) {
        CompoundTag t = new CompoundTag();
        t.putInt("x", P.getX());
        t.putInt("y", P.getY());
        t.putInt("z", P.getZ());
        t.putInt("d", D.get2DDataValue());
        t.putString("n", nombre);
        registro(sl.getServer()).put(clave(campana), t);
        PoliciaMundo.get(sl.getServer()).setDirty();
        PoliciaAtlas.marcar(sl.getServer().overworld(), "parada", clave(campana), P, "Parada: " + nombre, null);
    }

    // Alturas suavizadas del camino (de -40 a +12 respecto de la parada).
    static float[] camino(ServerLevel sl, BlockPos P, Direction D) {
        Direction R = D.getClockWise();
        int[] ys = new int[53];
        for (int i = -40; i <= 12; i++) {
            BlockPos q = P.relative(R, -2).relative(D, i);
            ys[i + 40] = PoliciaCine.altura(sl, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ());
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
        return suaves;
    }

    // Datos para la escena de la parada (cliente, modo 3).
    static CompoundTag datosCine(ServerLevel sl, BlockPos P, Direction D, String nombre) {
        Direction R = D.getClockWise();
        float[] suaves = camino(sl, P, D);
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
        d.putDouble("jy", P.getY());
        d.putDouble("jz", P.getZ() + 0.5);
        d.putString("nombre", nombre);
        return d;
    }

    // ---------------- Buscar lugar ----------------
    static boolean natural(BlockState bs) {
        return bs.is(BlockTags.DIRT) || bs.is(Blocks.SAND) || bs.is(Blocks.RED_SAND) || bs.is(Blocks.GRAVEL) || bs.is(Blocks.DIRT_PATH)
                || bs.is(Blocks.STONE) || bs.is(Blocks.SNOW_BLOCK) || bs.is(Blocks.TERRACOTTA);
    }

    static boolean apto(ServerLevel sl, BlockPos P, Direction D) {
        Direction R = D.getClockWise();
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                BlockPos q = P.relative(D, a).relative(R, b);
                if (!sl.hasChunkAt(q)) {
                    return false;
                }
                int h = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ());
                if (Math.abs(h - P.getY()) > 1) {
                    return false;
                }
                BlockState suelo = sl.getBlockState(new BlockPos(q.getX(), h - 1, q.getZ()));
                if (!suelo.getFluidState().isEmpty() || !natural(suelo)) {
                    return false;
                }
            }
        }
        return true;
    }

    static float pena(ServerLevel sl, BlockPos P, Direction D) {
        Direction R = D.getClockWise();
        float pena = 0;
        int prev = Integer.MIN_VALUE;
        for (int i = -40; i <= 12; i += 2) {
            BlockPos q = P.relative(R, -2).relative(D, i);
            if (!sl.hasChunkAt(q)) {
                pena += 4;
                continue;
            }
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ());
            if (prev != Integer.MIN_VALUE) {
                pena += Math.abs(y - prev) * 3;
            }
            if (!sl.getBlockState(new BlockPos(q.getX(), y - 1, q.getZ())).getFluidState().isEmpty()) {
                pena += 6;
            }
            prev = y;
        }
        return pena;
    }

    // Devuelve {P, D} o null.
    static Object[] buscarSitio(ServerLevel sl, BlockPos centro, int rmin, int rmax) {
        Object[] mejor = null;
        float mejorPena = Float.MAX_VALUE;
        int validos = 0;
        for (int r = rmin; r <= rmax && validos < 8; r += 3) {
            int vueltas = r == 0 ? 1 : 12;
            for (int k = 0; k < vueltas && validos < 8; k++) {
                double ang = k * Math.PI * 2 / vueltas + r * 0.37;
                int x = centro.getX() + (int) Math.round(Math.cos(ang) * r);
                int z = centro.getZ() + (int) Math.round(Math.sin(ang) * r);
                BlockPos col = new BlockPos(x, 0, z);
                if (!sl.hasChunkAt(col)) {
                    continue;
                }
                int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos P = new BlockPos(x, y, z);
                for (Direction D : Direction.Plane.HORIZONTAL) {
                    if (!apto(sl, P, D)) {
                        continue;
                    }
                    validos++;
                    float pn = pena(sl, P, D) + r * 0.05f;
                    if (pn < mejorPena) {
                        mejorPena = pn;
                        mejor = new Object[]{P, D};
                    }
                }
            }
        }
        return mejor;
    }

    static double distanciaParada(MinecraftServer s, double x, double z) {
        double mejor = Double.MAX_VALUE;
        CompoundTag reg = registro(s);
        for (String k : reg.getAllKeys()) {
            CompoundTag t = reg.getCompound(k);
            double dx = t.getInt("x") - x;
            double dz = t.getInt("z") - z;
            mejor = Math.min(mejor, Math.sqrt(dx * dx + dz * dz));
        }
        return mejor;
    }

    // Parada de una aldea (una sola por aldea; la llama PoliciaViaje).
    static void enAldea(ServerLevel sl, StructureStart s, CompoundTag glob) {
        BlockPos c = s.getBoundingBox().getCenter();
        Object[] sitio = buscarSitio(sl, new BlockPos(c.getX(), 0, c.getZ()), 12, 45);
        if (sitio == null) {
            sitio = buscarSitio(sl, new BlockPos(c.getX(), 0, c.getZ()), 45, 70);
        }
        if (sitio == null) {
            glob.putBoolean("est", false);
            return;
        }
        BlockPos P = (BlockPos) sitio[0];
        Direction D = (Direction) sitio[1];
        String n = glob.getString("n");
        BlockPos campana = construir(sl, P, D, n, 10, 8);
        registrar(sl, campana, P, D, n);
        glob.putInt("px", P.getX());
        glob.putInt("py", P.getY());
        glob.putInt("pz", P.getZ());
        PoliciaMundo.get(sl.getServer()).setDirty();
    }

    // Parada suelta en el camino, atras del jugador (para que no aparezca a la vista).
    static void enCampo(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        double ang = Math.toRadians(p.getYRot() + 90 + 180 + (p.getRandom().nextDouble() - 0.5) * 120);
        double dist = 40 + p.getRandom().nextInt(20);
        BlockPos c = new BlockPos((int) (p.getX() + Math.cos(ang) * dist), 0, (int) (p.getZ() + Math.sin(ang) * dist));
        StructureStart aldea = sl.structureManager().getStructureWithPieceAt(new BlockPos(c.getX(), (int) p.getY(), c.getZ()), StructureTags.VILLAGE);
        if (aldea != null && aldea.isValid()) {
            return;
        }
        Object[] sitio = buscarSitio(sl, c, 0, 15);
        if (sitio == null) {
            return;
        }
        BlockPos P = (BlockPos) sitio[0];
        Direction D = (Direction) sitio[1];
        String n = CAMPO[Math.floorMod((P.getX() / 16) * 31 + P.getZ() / 16, CAMPO.length)];
        BlockPos campana = construir(sl, P, D, n, 24, 12);
        registrar(sl, campana, P, D, n);
    }

    // ---------------- La campana ----------------
    static void tocar(ServerPlayer p, BlockPos campana, CompoundTag t) {
        long ahora = p.serverLevel().getGameTime();
        if (PoliciaCine.ocupado(p) || LLAMANDO.containsKey(p.getUUID()) || PoliciaViaje.VIAJANDO.contains(p.getUUID()) || ahora < CD.getOrDefault(p.getUUID(), 0L)) {
            return;
        }
        CD.put(p.getUUID(), ahora + 60);
        if (p.serverLevel().dimension() != Level.OVERWORLD) {
            return;
        }
        BlockPos P = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
        Direction D = Direction.from2DDataValue(t.getInt("d"));
        // destinos: ciudades que ya conociste (menos esta) y tu casa
        ListTag lista = new ListTag();
        CompoundTag mias = PoliciaViaje.paradas(p);
        for (String k : mias.getAllKeys()) {
            CompoundTag v = mias.getCompound(k);
            double dx = v.getInt("x") - P.getX();
            double dz = v.getInt("z") - P.getZ();
            if (dx * dx + dz * dz < 80 * 80) {
                continue;
            }
            CompoundTag e = new CompoundTag();
            e.putString("k", k);
            e.putString("n", v.getString("n"));
            e.putInt("c", PoliciaViaje.costo(p, v.getInt("x"), v.getInt("z")));
            e.putInt("m", (int) Math.sqrt(dx * dx + dz * dz));
            lista.add(e);
        }
        CompoundTag casa = PoliciaCasa.casaDe(p.server, p.getUUID());
        if (casa != null) {
            double dx = casa.getInt("x") - P.getX();
            double dz = casa.getInt("z") - P.getZ();
            if (dx * dx + dz * dz >= 40 * 40) {
                CompoundTag e = new CompoundTag();
                e.putString("k", "casa");
                e.putString("n", "Tu casa");
                e.putInt("c", 0);
                e.putInt("m", (int) Math.sqrt(dx * dx + dz * dz));
                lista.add(0, e);
            }
        }
        p.serverLevel().playSound(null, campana, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 1.0f);
        if (lista.isEmpty()) {
            PoliciaMod.msg(p, "El carruaje no tiene a dónde llevarte todavía: descubrí otras aldeas (o conseguí una casa) y volvé a tocar la campana.");
            return;
        }
        // a esperar bajo el techo, mirando hacia donde viene el carruaje
        ServerLevel sl = p.serverLevel();
        p.teleportTo(sl, P.getX() + 0.5, P.getY(), P.getZ() + 0.5, D.getOpposite().toYRot(), 0);
        p.setDeltaMovement(Vec3.ZERO);
        PoliciaCine.proteger(p);
        CompoundTag d = datosCine(sl, P, D, t.getString("n"));
        d.put("dest", lista);
        d.putBoolean("llamada", true);
        LLAMANDO.put(p.getUUID(), lista);
        DESTINO.remove(p.getUUID());
        PoliciaCine.EN_CURSO.put(p.getUUID(), sl.getGameTime());
        PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_parada", d));
    }

    // El jugador eligio a donde ir en el menu del cochero.
    static void elegir(ServerPlayer p, String k) {
        ListTag lista = LLAMANDO.get(p.getUUID());
        if (lista == null) {
            return;
        }
        int x;
        int y;
        int z;
        String n;
        if ("casa".equals(k)) {
            CompoundTag c = PoliciaCasa.casaDe(p.server, p.getUUID());
            if (c == null) {
                PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_reabrir", new CompoundTag()));
                return;
            }
            x = c.getInt("x");
            y = c.getInt("y");
            z = c.getInt("z");
            n = "Tu casa";
        } else {
            CompoundTag v = PoliciaViaje.paradas(p).getCompound(k);
            if (v.isEmpty()) {
                PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_reabrir", new CompoundTag()));
                return;
            }
            x = v.getInt("x");
            y = v.getInt("y");
            z = v.getInt("z");
            n = v.getString("n");
            int cuesta = PoliciaViaje.costo(p, x, z);
            if (cuesta > 0 && !PoliciaHabOficios.pagar(p, cuesta)) {
                PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_reabrir", new CompoundTag()));
                return;
            }
        }
        CompoundTag dd = new CompoundTag();
        dd.putInt("x", x);
        dd.putInt("y", y);
        dd.putInt("z", z);
        dd.putString("n", n);
        DESTINO.put(p.getUUID(), dd);
        PoliciaCine.EN_CURSO.put(p.getUUID(), p.serverLevel().getGameTime());
        PoliciaRed.toPlayer(p, new PoliciaRed.Efecto("cine_seguir", new CompoundTag()));
    }

    static void cancelar(ServerPlayer p) {
        LLAMANDO.remove(p.getUUID());
        DESTINO.remove(p.getUUID());
    }

    // Llamado por PoliciaCine.subio: si habia destino elegido, viaja.
    static boolean subir(ServerPlayer p) {
        CompoundTag dd = DESTINO.remove(p.getUUID());
        LLAMANDO.remove(p.getUUID());
        if (dd == null) {
            return false;
        }
        PoliciaCine.EN_CURSO.remove(p.getUUID());
        int x = dd.getInt("x");
        int z = dd.getInt("z");
        int yy = Math.max(dd.getInt("y"), PoliciaCine.altura(p.serverLevel(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        String n = dd.getString("n");
        PoliciaCine.empezarEn(p, new Vec3(x + 0.5, yy, z + 0.5), Character.toUpperCase(n.charAt(0)) + n.substring(1), true);
        return true;
    }

    public static class EventosParadas {
        @SubscribeEvent
        public void campana(PlayerInteractEvent.RightClickBlock e) {
            if (e.getLevel().isClientSide || !(e.getEntity() instanceof ServerPlayer p) || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
                return;
            }
            BlockPos b = e.getPos();
            if (!e.getLevel().getBlockState(b).is(Blocks.BELL)) {
                return;
            }
            CompoundTag t = registro(p.server).getCompound(clave(b));
            if (t.isEmpty()) {
                return;
            }
            tocar(p, b, t);
        }

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 200 != 77) {
                return;
            }
            ServerLevel sl = p.serverLevel();
            if (sl.dimension() != Level.OVERWORLD || !PoliciaRPG.datos(p).getBoolean("intro") || PoliciaCine.ocupado(p) || p.isSpectator()) {
                return;
            }
            if (p.getY() < sl.getSeaLevel() - 8 || p.getRandom().nextInt(3) != 0) {
                return;
            }
            if (distanciaParada(p.server, p.getX(), p.getZ()) < 380) {
                return;
            }
            StructureStart s = sl.structureManager().getStructureWithPieceAt(p.blockPosition(), StructureTags.VILLAGE);
            if (s != null && s.isValid()) {
                return;
            }
            enCampo(p);
        }

        @SubscribeEvent
        public void salir(PlayerEvent.PlayerLoggedOutEvent e) {
            LLAMANDO.remove(e.getEntity().getUUID());
            DESTINO.remove(e.getEntity().getUUID());
            CD.remove(e.getEntity().getUUID());
        }
    }
}
