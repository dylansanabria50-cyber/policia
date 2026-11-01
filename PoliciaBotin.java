package com.example.policia;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Botin: estadisticas al azar ("afijos") en armas y armaduras.
// Se guardan en la etiqueta pol_afijos del objeto. Los raros traen mas:
// Comun 0, Poco comun 1, Raro 1-2, Epico 2, Legendario 3.
//  vida      +corazones de vida maxima (puesto o en la mano)
//  critico   +% de golpe critico
//  robo      +% del dano que hacés vuelve como vida
//  velocidad +% de velocidad de movimiento
//  xp        +% de XP del modo RPG
//  fuego     prende fuego al que golpeás (segundos)
public class PoliciaBotin {
    static final String[] AFIJOS = {"vida", "critico", "robo", "velocidad", "xp", "fuego"};
    static final String[] NOMBRE = {"de vida", "de crítico", "de robo de vida", "de velocidad", "de experiencia", "ígneo"};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosBotin());
    }

    static CompoundTag afijos(ItemStack st) {
        return st.hasTag() ? st.getTag().getCompound("pol_afijos") : new CompoundTag();
    }

    // Tira afijos segun la rareza del objeto.
    static void tirarAfijos(ItemStack st, RandomSource r) {
        if (st.isEmpty() || !PoliciaRareza.equipo(st)) {
            return;
        }
        int rz = PoliciaRareza.rareza(st);
        int n = switch (rz) {
            case 1 -> 1;
            case 2 -> 1 + r.nextInt(2);
            case 3 -> 2;
            case 4 -> 3;
            default -> 0;
        };
        CompoundTag a = new CompoundTag();
        boolean arma = !(st.getItem() instanceof ArmorItem);
        int intentos = 0;
        while (a.size() < n && intentos++ < 20) {
            String k = AFIJOS[r.nextInt(AFIJOS.length)];
            if (a.contains(k) || (!arma && (k.equals("robo") || k.equals("fuego")))) {
                continue;
            }
            int v = switch (k) {
                case "vida" -> 1 + r.nextInt(2 + rz);
                case "critico" -> 2 + r.nextInt(3 + rz * 2);
                case "robo" -> 2 + r.nextInt(2 + rz);
                case "velocidad" -> 2 + r.nextInt(2 + rz * 2);
                case "xp" -> 3 + r.nextInt(3 + rz * 3);
                default -> 2 + r.nextInt(2 + rz);
            };
            a.putInt(k, v);
        }
        if (!a.isEmpty()) {
            st.getOrCreateTag().put("pol_afijos", a);
        }
    }

    // Suma de un afijo en lo que el jugador tiene puesto y en la mano.
    static int total(LivingEntity p, String k) {
        int t = afijos(p.getMainHandItem()).getInt(k);
        for (ItemStack a : p.getArmorSlots()) {
            t += afijos(a).getInt(k);
        }
        return t;
    }

    static final UUID[] UID_VIDA = {
            UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b01"), UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b02"),
            UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b03"), UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b04"),
            UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b05"), UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b06")};
    static final UUID[] UID_VEL = {
            UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b11"), UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b12"),
            UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b13"), UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b14"),
            UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b15"), UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b16")};

    static String texto(String k, int v) {
        return switch (k) {
            case "vida" -> "+" + v + " de vida máxima";
            case "critico" -> "+" + v + " % de crítico";
            case "robo" -> v + " % del daño vuelve como vida";
            case "velocidad" -> "+" + v + " % de velocidad";
            case "xp" -> "+" + v + " % de XP";
            default -> "Prende fuego " + v + " s al golpear";
        };
    }

    public static class EventosBotin {
        // vida y velocidad como modificadores del objeto en su casillero
        @SubscribeEvent
        public void atributos(ItemAttributeModifierEvent e) {
            ItemStack st = e.getItemStack();
            CompoundTag a = afijos(st);
            if (a.isEmpty()) {
                return;
            }
            EquipmentSlot slot = st.getItem() instanceof ArmorItem ai ? ai.getEquipmentSlot() : EquipmentSlot.MAINHAND;
            if (e.getSlotType() != slot) {
                return;
            }
            int i = slot.ordinal();
            if (a.contains("vida")) {
                e.addModifier(Attributes.MAX_HEALTH, new AttributeModifier(UID_VIDA[i], "afijo vida", a.getInt("vida"), AttributeModifier.Operation.ADDITION));
            }
            if (a.contains("velocidad")) {
                e.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(UID_VEL[i], "afijo velocidad", a.getInt("velocidad") / 100.0, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }

        // critico, robo de vida y fuego al pegar
        @SubscribeEvent
        public void golpe(LivingHurtEvent e) {
            if (!(e.getSource().getDirectEntity() instanceof ServerPlayer p) || e.getSource().getEntity() != p) {
                return;
            }
            LivingEntity v = e.getEntity();
            int crit = total(p, "critico");
            if (crit > 0 && p.getRandom().nextFloat() < crit / 100.0f) {
                e.setAmount(e.getAmount() * 1.5f);
                PoliciaCombate.marcarCritico(v);
            }
            int robo = total(p, "robo");
            if (robo > 0) {
                p.heal(e.getAmount() * robo / 100.0f);
            }
            int fuego = afijos(p.getMainHandItem()).getInt("fuego");
            if (fuego > 0) {
                v.setSecondsOnFire(fuego);
            }
        }

        @SubscribeEvent
        public void tooltip(ItemTooltipEvent e) {
            CompoundTag a = afijos(e.getItemStack());
            for (String k : AFIJOS) {
                if (a.contains(k)) {
                    e.getToolTip().add(Component.literal("§9◆ " + texto(k, a.getInt(k))));
                }
            }
        }
    }

    // XP extra por afijos (lo suma PoliciaNivel.conIngenio).
    static float bonoXp(Player p) {
        return total(p, "xp") / 100.0f;
    }
}
