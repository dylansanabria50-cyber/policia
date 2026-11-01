package com.example.policia;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

// Opciones del cliente (config/policia-cliente.toml). Se pueden cambiar con
// el juego cerrado; sirven para apagar lo visual nuevo si molesta.
public class PoliciaConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue MOVIMIENTOS;
    public static final ForgeConfigSpec.DoubleValue INTENSIDAD;
    public static final ForgeConfigSpec.BooleanValue PRIMERA_PERSONA;
    public static final ForgeConfigSpec.BooleanValue LINEAS_VELOCIDAD;
    public static final ForgeConfigSpec.BooleanValue HUD_NIVEL;
    public static final ForgeConfigSpec.BooleanValue HUD_DIARIAS;
    public static final ForgeConfigSpec.BooleanValue HUD_RELOJ;
    public static final ForgeConfigSpec.BooleanValue ESTACIONES;
    public static final ForgeConfigSpec.BooleanValue ARTICULACIONES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("movimientos");
        MOVIMIENTOS = b.comment("Movimientos mejorados de todos los jugadores (inclinacion al acelerar y en curvas, aterrizaje, escalera, golpes). Solo visual.")
                .define("activados", true);
        ARTICULACIONES = b.comment("Codos y rodillas que se doblan (al caminar, correr, pelear, cubrirse...). Tambien en la armadura.")
                .define("articulaciones", true);
        INTENSIDAD = b.comment("Que tan marcados son los movimientos (0.0 a 2.0).")
                .defineInRange("intensidad", 1.0, 0.0, 2.0);
        b.pop();
        b.push("primeraPersona");
        PRIMERA_PERSONA = b.comment("Brazo con inercia, camara que se inclina en las curvas y se hunde al aterrizar.")
                .define("activada", true);
        LINEAS_VELOCIDAD = b.comment("Lineas de velocidad en los bordes al correr (estilo anime).")
                .define("lineasDeVelocidad", true);
        b.pop();
        b.push("rpg");
        HUD_NIVEL = b.comment("Mostrar el nivel y la barra de XP del modo RPG abajo a la izquierda.")
                .define("hudNivel", true);
        HUD_DIARIAS = b.comment("Mostrar las misiones diarias a la derecha de la pantalla.")
                .define("hudDiarias", true);
        HUD_RELOJ = b.comment("Mostrar el reloj y la franja del dia arriba a la izquierda.")
                .define("hudReloj", true);
        b.pop();
        b.push("mundo");
        ESTACIONES = b.comment("Estaciones visibles: color de pasto y hojas, petalos, copos y neblina de manana. Apagalo si choca con un shader.")
                .define("estacionesVisuales", true);
        b.pop();
        SPEC = b.build();
    }

    static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "policia-cliente.toml");
    }

    static boolean mov() {
        try {
            return MOVIMIENTOS.get();
        } catch (Throwable t) {
            return true;
        }
    }

    static float intensidad() {
        try {
            return INTENSIDAD.get().floatValue();
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    static boolean primera() {
        try {
            return PRIMERA_PERSONA.get();
        } catch (Throwable t) {
            return true;
        }
    }

    static boolean lineas() {
        try {
            return LINEAS_VELOCIDAD.get();
        } catch (Throwable t) {
            return true;
        }
    }

    static boolean hudNivel() {
        try {
            return HUD_NIVEL.get();
        } catch (Throwable t) {
            return true;
        }
    }

    static boolean hudDiarias() {
        try {
            return HUD_DIARIAS.get();
        } catch (Throwable t) {
            return true;
        }
    }

    static boolean hudReloj() {
        try {
            return HUD_RELOJ.get();
        } catch (Throwable t) {
            return true;
        }
    }

    static boolean estaciones() {
        try {
            return ESTACIONES.get();
        } catch (Throwable t) {
            return true;
        }
    }

    static boolean articulaciones() {
        try {
            return ARTICULACIONES.get();
        } catch (Throwable t) {
            return true;
        }
    }
}
