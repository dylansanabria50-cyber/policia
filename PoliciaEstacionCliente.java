package com.example.policia;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Etapa 13 (cliente): las estaciones se ven. Pasto y hojas cambian de color
// (primavera mas claro, otono naranja, invierno palido), petalos en
// primavera, copos en invierno y neblina a la manana en otono e invierno.
// Se apaga con estacionesVisuales en policia-cliente.toml.
public class PoliciaEstacionCliente {
    static int ultima = -1;

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaEstacionCliente::colores);
        MinecraftForge.EVENT_BUS.addListener(PoliciaEstacionCliente::tick);
        MinecraftForge.EVENT_BUS.addListener(PoliciaEstacionCliente::niebla);
    }

    static int estacion() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 1;
        }
        long day = mc.level.getDayTime() / 24000L;
        return (int) (day / PoliciaEstacion.DAYS % 4L);
    }

    static int mezclar(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return r << 16 | g << 8 | bl;
    }

    static int tinte(int base, boolean hojas) {
        if (!PoliciaConfig.estaciones()) {
            return base;
        }
        return switch (estacion()) {
            case 0 -> mezclar(base, 0x8EE065, 0.25f);
            case 2 -> mezclar(base, hojas ? 0xD9722B : 0xC8A040, hojas ? 0.6f : 0.35f);
            case 3 -> mezclar(base, 0xE4ECEF, hojas ? 0.4f : 0.5f);
            default -> base;
        };
    }

    static void colores(RegisterColorHandlersEvent.Block e) {
        e.register((st, lvl, pos, i) -> tinte(lvl != null && pos != null ? BiomeColors.getAverageGrassColor(lvl, pos) : GrassColor.getDefaultColor(), false),
                Blocks.GRASS_BLOCK, Blocks.GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.POTTED_FERN, Blocks.SUGAR_CANE);
        e.register((st, lvl, pos, i) -> tinte(lvl != null && pos != null ? BiomeColors.getAverageFoliageColor(lvl, pos) : FoliageColor.getDefaultColor(), true),
                Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.VINE);
        e.register((st, lvl, pos, i) -> tinte(FoliageColor.getBirchColor(), true), Blocks.BIRCH_LEAVES);
        e.register((st, lvl, pos, i) -> tinte(FoliageColor.getEvergreenColor(), true), Blocks.SPRUCE_LEAVES);
    }

    static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        if (mc.level.dimension() != Level.OVERWORLD) {
            return;
        }
        int s = estacion();
        if (mc.player.tickCount % 100 == 0) {
            if (ultima >= 0 && s != ultima && PoliciaConfig.estaciones()) {
                mc.levelRenderer.allChanged();
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6Llegó el " + PoliciaEstacion.NAME[s] + " a la comarca"), true);
            }
            ultima = s;
        }
        if (!PoliciaConfig.estaciones() || mc.level.isRaining() || mc.player.tickCount % 3 != 0) {
            return;
        }
        BlockPos p = mc.player.blockPosition();
        if (mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) > p.getY() + 2) {
            return;
        }
        var r = mc.level.random;
        double x = mc.player.getX() + (r.nextDouble() - 0.5) * 16;
        double z = mc.player.getZ() + (r.nextDouble() - 0.5) * 16;
        double y = mc.player.getY() + 6 + r.nextDouble() * 4;
        if (s == 0 && r.nextInt(3) == 0) {
            mc.level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y, z, 0, 0, 0);
        } else if (s == 3) {
            mc.level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, (r.nextDouble() - 0.5) * 0.02, -0.05, (r.nextDouble() - 0.5) * 0.02);
        } else if (s == 2 && r.nextInt(4) == 0) {
            mc.level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, y - 3, z, 0, 0, 0);
        }
    }

    // Neblina por la manana (06:00 a 09:00) en otono e invierno.
    static void niebla(ViewportEvent.RenderFog e) {
        Minecraft mc = Minecraft.getInstance();
        if (!PoliciaConfig.estaciones() || mc.level == null || mc.level.dimension() != Level.OVERWORLD || e.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        int s = estacion();
        long t = Math.floorMod(mc.level.getDayTime(), 24000L);
        if ((s == 2 || s == 3) && t < 3000) {
            float k = t < 2000 ? 1.0f : 1.0f - (t - 2000) / 1000.0f;
            float lejos = e.getFarPlaneDistance();
            e.setFarPlaneDistance(lejos * (1.0f - 0.55f * k));
            e.setNearPlaneDistance(e.getNearPlaneDistance() * (1.0f - 0.6f * k));
            e.setFogShape(FogShape.SPHERE);
            e.setCanceled(true);
        }
    }
}
