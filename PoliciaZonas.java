package com.example.policia;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 5: zonas con nivel. Cada lugar del mundo tiene un nivel: cerca de
// las estructuras del mod es el de la estructura; en el resto crece con la
// distancia al punto de aparicion. Al cruzar a otra zona sale un titulo con
// su nombre y nivel. Si la zona te supera por 5 niveles o mas recibis 50 %
// mas de dano, y los monstruos que aparecen ahi son mas fuertes.
public class PoliciaZonas {
    // Estructuras de PoliciaStruct (mismo orden que SN): nombre, nivel, radio.
    static final String[] NOMBRE = {"Comisaría", "Obra en construcción", "Casino clandestino", "Campamento de bandidos", "Escondite de ladrones", "Atalaya", "Cueva de contrabandistas", "Herrería"};
    static final int[] NIVEL = {1, 2, 15, 8, 12, 6, 18, 2};
    static final int[] RADIO = {48, 32, 48, 40, 40, 24, 32, 32};
    static final UUID MOD_VIDA = UUID.fromString("7c1d0a3e-55b2-4f1e-8a77-2b9e0d4c6a01");
    static final UUID MOD_DANO = UUID.fromString("7c1d0a3e-55b2-4f1e-8a77-2b9e0d4c6a02");
    static final Map<UUID, String> ULTIMA = new HashMap<UUID, String>();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosZonas());
    }

    public static class Zona {
        final String nombre;
        final int nivel;

        Zona(String nombre, int nivel) {
            this.nombre = nombre;
            this.nivel = nivel;
        }

        String clave() {
            return this.nombre + "#" + this.nivel;
        }
    }

    static Zona zona(ServerLevel sl, BlockPos pos) {
        try {
            if (sl.dimension() == Level.OVERWORLD && PoliciaMazmorra.enCueva(sl.getServer(), pos)) {
                return new Zona("Cueva del Eco", PoliciaMazmorra.NIVEL);
            }
            PoliciaStruct.Sites s = PoliciaStruct.Sites.get(sl);
            if (sl.dimension() == Level.OVERWORLD) {
                for (int k = 0; k < NOMBRE.length && k < s.l.size(); k++) {
                    if (s.near(k, pos, RADIO[k])) {
                        return new Zona(NOMBRE[k], NIVEL[k]);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        BlockPos sp = sl.getSharedSpawnPos();
        double dx = pos.getX() - sp.getX();
        double dz = pos.getZ() - sp.getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        int base = sl.dimension() == Level.NETHER ? 20 : sl.dimension() == Level.END ? 35 : 1;
        int nivel = Math.min(PoliciaRPG.NIVEL_MAX, base + dist / 160);
        if (sl.dimension() == Level.NETHER) {
            return new Zona("Nether", nivel);
        }
        if (sl.dimension() == Level.END) {
            return new Zona("El Fin", nivel);
        }
        String n = nivel <= 5 ? "Campos tranquilos" : nivel <= 15 ? "Tierras salvajes" : nivel <= 30 ? "Fronteras peligrosas" : "Tierras prohibidas";
        // Las zonas salvajes se agrupan de a 5 niveles para no mostrar un titulo a cada rato.
        int banda = ((nivel - 1) / 5) * 5 + 1;
        return new Zona(n, banda);
    }

    static void anunciar(ServerPlayer p, Zona z) {
        int mio = PoliciaRPG.nivel(p);
        boolean peligro = z.nivel - mio >= 5;
        String color = peligro ? "§c" : z.nivel > mio ? "§e" : "§a";
        p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 40, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§f" + z.nombre)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(color + "Nivel " + z.nivel + (peligro ? " · ¡Peligro! Recibís +50 % de daño" : ""))));
    }

    public static class EventosZonas {
        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 7) {
                return;
            }
            Zona z = zona(p.serverLevel(), p.blockPosition());
            String antes = ULTIMA.put(p.getUUID(), z.clave());
            if (antes != null && !antes.equals(z.clave())) {
                anunciar(p, z);
            }
        }

        @SubscribeEvent
        public void salir(PlayerEvent.PlayerLoggedOutEvent e) {
            ULTIMA.remove(e.getEntity().getUUID());
        }

        @SubscribeEvent
        public void dano(LivingHurtEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                Zona z = zona(p.serverLevel(), p.blockPosition());
                if (z.nivel - PoliciaRPG.nivel(p) >= 5) {
                    e.setAmount(e.getAmount() * 1.5f);
                }
            }
        }

        // Los monstruos nuevos escalan con el nivel de la zona: +5 % de vida
        // y +3 % de dano por nivel.
        @SubscribeEvent
        public void aparece(EntityJoinLevelEvent e) {
            if (e.getLevel().isClientSide() || e.loadedFromDisk() || !(e.getEntity() instanceof Monster m) || !(e.getLevel() instanceof ServerLevel sl)) {
                return;
            }
            if (m.getPersistentData().getBoolean("pol_zona")) {
                return;
            }
            m.getPersistentData().putBoolean("pol_zona", true);
            int nivel = zona(sl, m.blockPosition()).nivel;
            if (nivel <= 1) {
                return;
            }
            m.getPersistentData().putInt("pol_zona_nivel", nivel);
            AttributeInstance vida = m.getAttribute(Attributes.MAX_HEALTH);
            if (vida != null && vida.getModifier(MOD_VIDA) == null) {
                vida.addPermanentModifier(new AttributeModifier(MOD_VIDA, "zona vida", (nivel - 1) * 0.05, AttributeModifier.Operation.MULTIPLY_BASE));
                m.setHealth(m.getMaxHealth());
            }
            AttributeInstance dano = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dano != null && dano.getModifier(MOD_DANO) == null) {
                dano.addPermanentModifier(new AttributeModifier(MOD_DANO, "zona dano", (nivel - 1) * 0.03, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
    }
}
