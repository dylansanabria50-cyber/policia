package com.example.policia;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 1 del modo RPG: los datos de cada jugador (nivel 1-50, XP, puntos
// libres y los cinco atributos) en un solo lugar. Se guardan en los datos
// persistentes del jugador bajo la clave "rpg", sobreviven a la muerte
// (lista de onClone en PoliciaMod) y se mandan al cliente con PoliciaRed.
public class PoliciaRPG {
    public static final String CLAVE = "rpg";
    public static final int NIVEL_MAX = 50;
    public static final int BASE_ATRIBUTO = 5;
    public static final int MAX_ATRIBUTO = 30;
    public static final String[] ATRIBUTOS = new String[]{"fuerza", "vitalidad", "agilidad", "destreza", "ingenio"};
    public static final String[] NOMBRES = new String[]{"Fuerza", "Vitalidad", "Agilidad", "Destreza", "Ingenio"};

    static final UUID MOD_VIDA = UUID.fromString("5b3c9f0e-1a2b-4c6d-9e01-7a3b5c9d2e11");
    static final UUID MOD_DANO = UUID.fromString("5b3c9f0e-1a2b-4c6d-9e01-7a3b5c9d2e12");
    static final UUID MOD_VEL = UUID.fromString("5b3c9f0e-1a2b-4c6d-9e01-7a3b5c9d2e13");

    // Copia que tiene el cliente de sus propios datos (la llena PoliciaRed).
    public static volatile CompoundTag CLIENTE = new CompoundTag();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosRPG());
    }

    // ---------------- Datos ----------------
    static CompoundTag datos(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(CLAVE)) {
            pd.put(CLAVE, new CompoundTag());
        }
        CompoundTag d = pd.getCompound(CLAVE);
        if (!d.contains("nivel")) {
            d.putInt("nivel", 1);
            d.putInt("xp", 0);
            d.putInt("puntos", 0);
            for (String a : ATRIBUTOS) {
                d.putInt(a, BASE_ATRIBUTO);
            }
        }
        return d;
    }

    public static int nivel(Player p) {
        return datos(p).getInt("nivel");
    }

    public static int atributo(Player p, String a) {
        return datos(p).getInt(a);
    }

    // XP que hace falta para pasar del nivel n al n+1.
    public static int xpParaSubir(int n) {
        return 100 + n * 60;
    }

    // Suma XP del modo RPG (separada de la XP de encantar). Devuelve
    // cuantos niveles subio.
    public static int sumarXp(ServerPlayer p, int cantidad) {
        if (cantidad <= 0) {
            return 0;
        }
        CompoundTag d = datos(p);
        int nivel = d.getInt("nivel");
        int xp = d.getInt("xp") + cantidad;
        int subidos = 0;
        while (nivel < NIVEL_MAX && xp >= xpParaSubir(nivel)) {
            xp -= xpParaSubir(nivel);
            nivel++;
            subidos++;
            d.putInt("puntos", d.getInt("puntos") + 3);
        }
        if (nivel >= NIVEL_MAX) {
            xp = 0;
        }
        d.putInt("nivel", nivel);
        d.putInt("xp", xp);
        if (subidos > 0) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
            p.displayClientMessage(Component.literal("§6¡Subiste al nivel " + nivel + "! §7(+" + (subidos * 3) + " puntos de atributo)"), false);
            aplicar(p);
            PoliciaNivel.subio(p, nivel, subidos);
        }
        sync(p);
        return subidos;
    }

    // Gasta un punto libre en un atributo.
    public static boolean subirAtributo(ServerPlayer p, String a) {
        CompoundTag d = datos(p);
        if (d.getInt("puntos") <= 0 || !d.contains(a) || d.getInt(a) >= MAX_ATRIBUTO) {
            return false;
        }
        d.putInt(a, d.getInt(a) + 1);
        d.putInt("puntos", d.getInt("puntos") - 1);
        aplicar(p);
        sync(p);
        PoliciaNivel.atributoSubido(p, a);
        return true;
    }

    // ---------------- Efecto en el jugador ----------------
    // Vitalidad: +1 de vida (medio corazon) por punto sobre la base.
    // Fuerza: +2 % de dano cuerpo a cuerpo por punto. Agilidad: +0,5 % de
    // velocidad por punto. Destreza e Ingenio los usan los oficios (calidad
    // de forja, criticos, enfriamientos) en las etapas siguientes.
    public static void aplicar(ServerPlayer p) {
        CompoundTag d = datos(p);
        poner(p, Attributes.MAX_HEALTH, MOD_VIDA, "rpg vitalidad", (d.getInt("vitalidad") - BASE_ATRIBUTO) * 1.0, AttributeModifier.Operation.ADDITION);
        poner(p, Attributes.ATTACK_DAMAGE, MOD_DANO, "rpg fuerza", (d.getInt("fuerza") - BASE_ATRIBUTO) * 0.02, AttributeModifier.Operation.MULTIPLY_BASE);
        poner(p, Attributes.MOVEMENT_SPEED, MOD_VEL, "rpg agilidad", (d.getInt("agilidad") - BASE_ATRIBUTO) * 0.005, AttributeModifier.Operation.MULTIPLY_BASE);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    static void poner(ServerPlayer p, Attribute at, UUID id, String nombre, double valor, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(at);
        if (inst == null) {
            return;
        }
        inst.removeModifier(id);
        if (valor != 0.0) {
            inst.addPermanentModifier(new AttributeModifier(id, nombre, valor, op));
        }
    }

    public static void sync(ServerPlayer p) {
        CompoundTag c = datos(p).copy();
        c.putString("oficio", PoliciaMod.job(p));
        // La casa vive en los datos del mundo: se manda aparte para el mapa.
        try {
            CompoundTag casa = PoliciaCasa.casaDe(p.server, p.getUUID());
            if (casa != null) {
                c.putBoolean("casa", true);
                c.putInt("casa_x", casa.getInt("x"));
                c.putInt("casa_z", casa.getInt("z"));
            }
        } catch (Throwable ignored) {
        }
        PoliciaRed.toPlayer(p, new PoliciaRed.RpgSync(c));
    }

    static String resumen(Player p) {
        CompoundTag d = datos(p);
        int n = d.getInt("nivel");
        StringBuilder sb = new StringBuilder();
        sb.append("§6Nivel ").append(n).append(" §7· XP ").append(d.getInt("xp")).append("/").append(n >= NIVEL_MAX ? 0 : xpParaSubir(n));
        sb.append(" §7· Puntos libres: §e").append(d.getInt("puntos"));
        for (int i = 0; i < ATRIBUTOS.length; i++) {
            sb.append("\n§f").append(NOMBRES[i]).append(": §b").append(d.getInt(ATRIBUTOS[i]));
        }
        return sb.toString();
    }

    // ---------------- Eventos ----------------
    public static class EventosRPG {
        @SubscribeEvent
        public void entrar(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                aplicar(p);
                sync(p);
            }
        }

        @SubscribeEvent
        public void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                aplicar(p);
                p.setHealth(p.getMaxHealth());
                sync(p);
            }
        }

        @SubscribeEvent
        public void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                sync(p);
            }
        }

        // /rpg              muestra nivel y atributos
        // /rpg subir <atr>  gasta un punto libre
        // /rpg xp <n>       (solo operadores) suma XP para probar
        @SubscribeEvent
        public void comandos(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("rpg")
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        p.displayClientMessage(Component.literal(resumen(p)), false);
                        return 1;
                    })
                    .then(Commands.literal("subir").then(Commands.argument("atributo", StringArgumentType.word())
                            .suggests((c, b) -> {
                                for (String a : ATRIBUTOS) {
                                    b.suggest(a);
                                }
                                return b.buildFuture();
                            })
                            .executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                String a = StringArgumentType.getString(c, "atributo").toLowerCase();
                                if (subirAtributo(p, a)) {
                                    p.displayClientMessage(Component.literal("§a+1 en " + a + ". " + resumen(p)), false);
                                    return 1;
                                }
                                p.displayClientMessage(Component.literal("§cNo se pudo: no tenés puntos libres o ese atributo no existe."), false);
                                return 0;
                            })))
                    .then(Commands.literal("xp").requires((CommandSourceStack s) -> s.hasPermission(2))
                            .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 100000)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                int subidos = sumarXp(p, IntegerArgumentType.getInteger(c, "cantidad"));
                                p.displayClientMessage(Component.literal("§7XP sumada. Niveles subidos: " + subidos), false);
                                return 1;
                            }))));
        }
    }
}
