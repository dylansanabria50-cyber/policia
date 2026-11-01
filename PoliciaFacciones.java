package com.example.policia;

import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Facciones y reputacion (de -100 a 100):
//   guardia     La Guardia (policias y la ley)
//   sombras     Gremio de las sombras (ladrones)
//   artesanos   Liga de artesanos (albaniles y herreros)
//   mercaderes  Casa de mercaderes
//   cronistas   Orden de los cronistas (sabios y medicos)
// Se gana con las decisiones de la historia, las diarias de tu oficio,
// los elites (Guardia) y los asaltos (Sombras, y la Guardia baja).
// Rangos: Hostil, Desconfiado, Neutral, Amistoso (20), Honrado (50) y
// Exaltado (80). En cada rango alto hay una recompensa para reclamar en la
// ficha (boton Facciones). Los Mercaderes ademas bajan los precios de
// Dona Rosa.
public class PoliciaFacciones {
    public static final String[] IDS = {"guardia", "sombras", "artesanos", "mercaderes", "cronistas"};
    public static final String[] NOMBRES = {"La Guardia", "Gremio de las sombras", "Liga de artesanos", "Casa de mercaderes", "Orden de los cronistas"};
    public static final int[] UMBRAL = {20, 50, 80};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosFacciones());
    }

    static CompoundTag datos(ServerPlayer p) {
        CompoundTag r = PoliciaRPG.datos(p);
        if (!r.contains("fac")) {
            r.put("fac", new CompoundTag());
        }
        return r.getCompound("fac");
    }

    public static int rep(CompoundTag fac, String id) {
        return fac.getInt(id);
    }

    public static String rango(int r) {
        return r <= -50 ? "§4Hostil" : r < -10 ? "§cDesconfiado" : r < 20 ? "§7Neutral" : r < 50 ? "§aAmistoso" : r < 80 ? "§bHonrado" : "§6Exaltado";
    }

    static int indice(String id) {
        return java.util.Arrays.asList(IDS).indexOf(id);
    }

    public static void sumar(ServerPlayer p, String id, int n) {
        int i = indice(id);
        if (i < 0 || n == 0) {
            return;
        }
        CompoundTag f = datos(p);
        int antes = f.getInt(id);
        int ahora = Math.max(-100, Math.min(100, antes + n));
        f.putInt(id, ahora);
        p.displayClientMessage(Component.literal((n > 0 ? "§a▲ " : "§c▼ ") + NOMBRES[i] + " §7" + (n > 0 ? "+" : "") + n + " (" + ahora + ")"), false);
        if (!rango(antes).equals(rango(ahora))) {
            p.displayClientMessage(Component.literal("§6" + NOMBRES[i] + ": ahora sos " + rango(ahora)), false);
            p.level().playSound(null, p.blockPosition(), n > 0 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.3f);
        }
        PoliciaRPG.sync(p);
    }

    // Faccion del oficio del jugador (para las diarias).
    static String deOficio(String oficio) {
        return switch (oficio) {
            case "policia" -> "guardia";
            case "ladron" -> "sombras";
            case "albanil", "herrero" -> "artesanos";
            case "mercader" -> "mercaderes";
            case "medico" -> "cronistas";
            default -> "";
        };
    }

    // ---------------- Recompensas ----------------
    static ItemStack recompensa(String id, int nivel, ServerPlayer p) {
        ItemStack st = switch (id + nivel) {
            case "guardia0" -> new ItemStack(Items.EMERALD, 12);
            case "guardia1" -> new ItemStack(PoliciaBotin.CAPA_GUARDIAN.get());
            case "guardia2" -> new ItemStack(PoliciaBotin.AMULETO_GUERRA.get());
            case "sombras0" -> new ItemStack(Items.ENDER_PEARL, 4);
            case "sombras1" -> new ItemStack(PoliciaBotin.CAPA_SOMBRAS.get());
            case "sombras2" -> new ItemStack(PoliciaBotin.GEMA_ONIX.get(), 3);
            case "artesanos0" -> new ItemStack(PoliciaBotin.CINTURON_CUERO.get());
            case "artesanos1" -> new ItemStack(PoliciaBotin.GEMA_RUBI.get(), 2);
            case "artesanos2" -> PoliciaBotin.piezaConjunto(p.getRandom(), 30);
            case "mercaderes0" -> new ItemStack(Items.EMERALD, 20);
            case "mercaderes1" -> new ItemStack(PoliciaBotin.CINTURON_ALQUIMISTA.get());
            case "mercaderes2" -> new ItemStack(PoliciaBotin.AMULETO_SABIO.get());
            case "cronistas0" -> new ItemStack(Items.EXPERIENCE_BOTTLE, 8);
            case "cronistas1" -> new ItemStack(PoliciaBotin.AMULETO_VIDA.get());
            default -> new ItemStack(PoliciaBotin.CAPA_VIAJERO.get());
        };
        if (nivel == 2 && !(st.getItem() instanceof PoliciaBotin.Gema) && st.getCount() == 1 && !st.hasTag()) {
            PoliciaRareza.ponerRareza(st, 3);
        }
        return st;
    }

    static String textoRecompensa(String id, int nivel) {
        return switch (id + nivel) {
            case "guardia0" -> "12 esmeraldas";
            case "guardia1" -> "Capa del guardián";
            case "guardia2" -> "Amuleto de guerra (épico)";
            case "sombras0" -> "4 perlas de ender";
            case "sombras1" -> "Capa de sombras";
            case "sombras2" -> "3 ónix";
            case "artesanos0" -> "Cinturón de cuero reforzado";
            case "artesanos1" -> "2 rubíes";
            case "artesanos2" -> "Pieza de conjunto";
            case "mercaderes0" -> "20 esmeraldas";
            case "mercaderes1" -> "Cinturón del alquimista";
            case "mercaderes2" -> "Amuleto del sabio (épico)";
            case "cronistas0" -> "8 frascos de experiencia";
            case "cronistas1" -> "Amuleto de vida";
            default -> "Capa de viajero (épica)";
        };
    }

    static void reclamar(ServerPlayer p, String id, int nivel) {
        int i = indice(id);
        if (i < 0 || nivel < 0 || nivel > 2) {
            return;
        }
        CompoundTag f = datos(p);
        String clave = "r_" + id + nivel;
        if (f.getBoolean(clave)) {
            PoliciaMod.msg(p, "Ya reclamaste esa recompensa");
            return;
        }
        if (f.getInt(id) < UMBRAL[nivel]) {
            PoliciaMod.msg(p, "Te falta reputación con " + NOMBRES[i] + " (" + f.getInt(id) + "/" + UMBRAL[nivel] + ")");
            return;
        }
        f.putBoolean(clave, true);
        ItemStack st = recompensa(id, nivel, p);
        if (!p.getInventory().add(st)) {
            p.drop(st, false);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.2f);
        PoliciaMod.msg(p, "§6" + NOMBRES[i] + " te entrega: §f" + textoRecompensa(id, nivel));
        PoliciaRPG.sync(p);
    }

    public static class EventosFacciones {
        // Dona Rosa ajusta los precios segun tu reputacion con los Mercaderes.
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void rosa(PlayerInteractEvent.EntityInteract e) {
            if (e.getLevel().isClientSide || !(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof WanderingTrader w) || !w.getPersistentData().getBoolean("pol_rosa")) {
                return;
            }
            int r = datos(p).getInt("mercaderes");
            for (MerchantOffer o : w.getOffers()) {
                int base = o.getBaseCostA().getCount();
                if (!o.getBaseCostA().is(Items.EMERALD)) {
                    continue;
                }
                int d = 0;
                if (r >= 80) {
                    d = -(base / 3);
                } else if (r >= 50) {
                    d = -(base / 4);
                } else if (r >= 20 && base >= 3) {
                    d = -1;
                } else if (r <= -50) {
                    d = Math.max(1, base / 2);
                }
                o.setSpecialPriceDiff(d);
            }
        }

        // /faccion: tu reputacion con todas.
        @SubscribeEvent
        public void comandos(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("faccion").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                CompoundTag f = datos(p);
                StringBuilder sb = new StringBuilder("§6Reputación");
                for (int i = 0; i < IDS.length; i++) {
                    sb.append("\n§f").append(NOMBRES[i]).append(": ").append(rango(f.getInt(IDS[i]))).append(" §7(").append(f.getInt(IDS[i])).append(")");
                }
                p.displayClientMessage(Component.literal(sb.toString()), false);
                return 1;
            }));
        }
    }
}
