package com.example.policia;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Precios segun la faccion: la reputacion con la Casa de mercaderes baja
// los precios de los aldeanos (1 a 3 esmeraldas por trueque), el oficio
// Mercader tiene 1 de descuento siempre, y un ladron buscado paga 2 mas.
// Se aplica al abrir el trueque y se borra solo al cerrarlo.
public class PoliciaEconomia {
    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosEconomia());
    }

    static int descuento(ServerPlayer p) {
        int rep = PoliciaFacciones.rep(PoliciaFacciones.datos(p), "mercaderes");
        int d = rep >= 75 ? 3 : rep >= 50 ? 2 : rep >= 25 ? 1 : 0;
        if ("mercader".equals(PoliciaMod.job(p))) {
            d++;
        }
        if (PoliciaLadron.wanted(p) > 0) {
            d -= 2;
        }
        return d;
    }

    public static class EventosEconomia {
        @SubscribeEvent
        public void trueque(PlayerInteractEvent.EntityInteract e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof AbstractVillager v)) {
                return;
            }
            final int d = descuento(p);
            if (d == 0) {
                return;
            }
            PoliciaHab.tarea(t -> {
                if (t < 1) {
                    return false;
                }
                if (p.containerMenu instanceof MerchantMenu mm && v.getTradingPlayer() == p) {
                    MerchantOffers o = mm.getOffers();
                    for (MerchantOffer of : o) {
                        int costo = of.getCostA().getCount();
                        int cambio = d > 0 ? -Math.min(d, costo - 1) : -d;
                        of.addToSpecialPriceDiff(cambio);
                    }
                    int nivel = v instanceof Villager vi ? vi.getVillagerData().getLevel() : 1;
                    int xp = v instanceof Villager vi ? vi.getVillagerXp() : 0;
                    p.sendMerchantOffers(mm.containerId, o, nivel, xp, v.showProgressBar(), v.canRestock());
                    p.displayClientMessage(Component.literal(d > 0 ? "§aPrecio de facción: -" + d + " ◆ por trueque" : "§cTe cobran " + (-d) + " ◆ más: te están buscando"), true);
                }
                return true;
            });
        }
    }
}
