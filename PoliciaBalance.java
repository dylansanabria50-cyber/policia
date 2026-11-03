package com.example.policia;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

// Balance del mundo (config/policia-balance.toml): XP, vida de los jefes de
// mazmorra y precio de los mercenarios. Lo decide el servidor (o el que
// abre el mundo).
public class PoliciaBalance {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue XP;
    public static final ForgeConfigSpec.DoubleValue VIDA_JEFES;
    public static final ForgeConfigSpec.DoubleValue PRECIO_MERC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("balance");
        XP = b.comment("Multiplicador de la XP de nivel que se gana (1.0 = normal).").defineInRange("xp", 1.0, 0.25, 4.0);
        VIDA_JEFES = b.comment("Multiplicador de la vida de los jefes de las mazmorras nuevas.").defineInRange("vidaJefes", 1.0, 0.5, 3.0);
        PRECIO_MERC = b.comment("Multiplicador del precio de los mercenarios.").defineInRange("precioMercenarios", 1.0, 0.25, 3.0);
        b.pop();
        SPEC = b.build();
    }

    static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "policia-balance.toml");
    }

    static double v(ForgeConfigSpec.DoubleValue d) {
        try {
            return d.get();
        } catch (Throwable t) {
            return 1.0;
        }
    }
}
