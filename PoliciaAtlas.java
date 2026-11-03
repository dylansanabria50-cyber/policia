package com.example.policia;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.ModList;

// Integracion con Antique Atlas (via Surveyor): las paradas de carruaje,
// comisarias, herrerias, casinos, Celestina y tu casa aparecen como
// marcadores dibujados en el atlas, con iconos propios del mod
// (assets/policia/textures/atlas/marker/*.png).
// Todo va por reflexion: si Antique Atlas/Surveyor no estan instalados,
// el mod funciona igual y esto no hace nada.
public class PoliciaAtlas {
    static Boolean hay;
    static Method mGlobal;
    static Method mCreate;
    static Method mAdd;
    static Method mOf;
    static Method mPut;
    static Object POS;
    static Object NAME;
    static final Set<String> HECHOS = new HashSet<String>();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PoliciaAtlas::entrar);
        MinecraftForge.EVENT_BUS.addListener(PoliciaAtlas::arranque);
    }

    static Class<?> clase(String n) throws ClassNotFoundException {
        ClassLoader[] cls = {PoliciaAtlas.class.getClassLoader(), Thread.currentThread().getContextClassLoader()};
        ClassNotFoundException ultima = null;
        for (ClassLoader cl : cls) {
            if (cl == null) {
                continue;
            }
            try {
                return Class.forName(n, true, cl);
            } catch (ClassNotFoundException e) {
                ultima = e;
            }
        }
        throw ultima != null ? ultima : new ClassNotFoundException(n);
    }

    static Method metodo(Class<?> c, String nombre, int params) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(nombre) && m.getParameterCount() == params) {
                return m;
            }
        }
        return null;
    }

    static boolean listo() {
        if (hay != null) {
            return hay;
        }
        hay = false;
        try {
            if (!ModList.get().isLoaded("surveyor")) {
                return false;
            }
            Class<?> landmark = clase("folk.sisby.surveyor.landmark.Landmark");
            Class<?> mundo = clase("folk.sisby.surveyor.landmark.WorldLandmarks");
            Class<?> builder = clase("folk.sisby.surveyor.landmark.component.LandmarkComponentMap$Builder");
            Class<?> tipos = clase("folk.sisby.surveyor.landmark.component.LandmarkComponentTypes");
            mGlobal = metodo(landmark, "global", 2);
            mCreate = metodo(landmark, "create", 3);
            mAdd = metodo(builder, "add", 2);
            mOf = metodo(mundo, "of", 1);
            mPut = metodo(mundo, "put", 1);
            Field fPos = tipos.getField("POS");
            Field fName = tipos.getField("NAME");
            POS = fPos.get(null);
            NAME = fName.get(null);
            hay = mGlobal != null && mCreate != null && mAdd != null && mOf != null && mPut != null && POS != null && NAME != null;
            if (hay) {
                PoliciaMod.LOGGER.info("[Policia] Antique Atlas/Surveyor detectado: los lugares del mod se marcan en el atlas");
            }
        } catch (Throwable t) {
            PoliciaMod.LOGGER.warn("[Policia] No se pudo conectar con Surveyor: " + t);
            hay = false;
        }
        return hay;
    }

    static String limpio(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            b.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return b.toString();
    }

    // tipo = nombre del icono (parada, comisaria, herreria, casino, celestina, casa).
    // dueno = null para que lo vean todos; si no, solo ese jugador.
    static void marcar(ServerLevel sl, String tipo, String clave, BlockPos pos, String nombre, UUID dueno) {
        if (sl == null || !listo()) {
            return;
        }
        String id = tipo + "/" + limpio(clave);
        String recuerdo = sl.dimension().location() + "|" + (dueno == null ? "" : dueno) + "|" + id + "|" + pos.asLong() + "|" + nombre;
        if (!HECHOS.add(recuerdo)) {
            return;
        }
        try {
            ResourceLocation rl = new ResourceLocation(PoliciaMod.ID, id);
            Component texto = Component.literal(nombre);
            UnaryOperator<Object> op = b -> {
                try {
                    mAdd.invoke(b, POS, pos.immutable());
                    mAdd.invoke(b, NAME, texto);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return b;
            };
            Object lm = dueno == null ? mGlobal.invoke(null, rl, op) : mCreate.invoke(null, dueno, rl, op);
            Object mundo = mOf.invoke(null, sl);
            mPut.invoke(mundo, lm);
        } catch (Throwable t) {
            HECHOS.remove(recuerdo);
            PoliciaMod.LOGGER.warn("[Policia] No se pudo marcar " + id + " en el atlas: " + t);
        }
    }

    static void arranque(ServerStartingEvent e) {
        HECHOS.clear();
    }

    // Al entrar: se marcan todos los lugares que ya existian (mundos viejos).
    static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !listo()) {
            return;
        }
        try {
            ServerLevel ow = p.server.overworld();
            CompoundTag reg = PoliciaParadas.registro(p.server);
            for (String k : reg.getAllKeys()) {
                CompoundTag t = reg.getCompound(k);
                marcar(ow, "parada", k, new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")), "Parada: " + t.getString("n"), null);
            }
            PoliciaStruct.Sites st = PoliciaStruct.Sites.get(ow);
            String[] tipo = {"comisaria", null, "casino", null, null, null, null, "herreria"};
            String[] nom = {"Comisaría", null, "Casino", null, null, null, null, "Herrería"};
            for (int k = 0; k < tipo.length; k++) {
                if (tipo[k] == null) {
                    continue;
                }
                for (long v : st.l.get(k)) {
                    BlockPos b = BlockPos.of(v);
                    marcar(ow, tipo[k], b.getX() + "_" + b.getZ(), b, nom[k], null);
                }
            }
            CompoundTag h = PoliciaMundo.get(p.server).getSeccion("historia");
            if (h.getBoolean("pueblo")) {
                marcar(ow, "celestina", "cronista", new BlockPos(h.getInt("x"), h.getInt("y"), h.getInt("z")), "Celestina, la cronista", null);
            }
            CompoundTag casa = PoliciaCasa.casaDe(p.server, p.getUUID());
            if (casa != null) {
                marcar(ow, "casa", p.getUUID().toString(), new BlockPos(casa.getInt("x"), casa.getInt("y"), casa.getInt("z")), "Tu casa", p.getUUID());
            }
        } catch (Throwable t) {
            PoliciaMod.LOGGER.warn("[Policia] Error marcando lugares en el atlas: " + t);
        }
    }

    // ---- cliente: la tecla M abre el Antique Atlas ----
    static Boolean hayAtlas;

    static boolean abrirAtlas() {
        if (hayAtlas == null) {
            hayAtlas = ModList.get().isLoaded("antique_atlas");
        }
        if (!hayAtlas) {
            return false;
        }
        try {
            Method m = metodo(clase("folk.sisby.antique_atlas.AntiqueAtlas"), "openAtlasScreen", 0);
            if (m == null) {
                hayAtlas = false;
                return false;
            }
            m.invoke(null);
            return true;
        } catch (Throwable t) {
            PoliciaMod.LOGGER.warn("[Policia] No se pudo abrir el Antique Atlas: " + t);
            hayAtlas = false;
            return false;
        }
    }
}
