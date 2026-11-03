package com.example.policia;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;

// Reparte los pedidos que mandan las pantallas nuevas (mensaje Accion) y
// tiene el "consumible rapido" (tecla V): usa al toque lo mejor que tengas
// para curarte, sin animacion, cada 10 segundos.
public class PoliciaAcciones {
    static final int CD_CONSUMO = 200;

    static void recibir(ServerPlayer p, String tipo, CompoundTag d) {
        switch (tipo) {
            case "espec" -> PoliciaEspecial.elegir(p, d.getString("id"));
            case "sync" -> PoliciaRPG.sync(p);
            case "merc_pedir" -> PoliciaMercenario.enviar(p);
            case "merc_contratar" -> PoliciaMercenario.contratar(p, d.getInt("clase"));
            case "merc_orden" -> PoliciaMercenario.orden(p, d.getString("id"), d.getInt("orden"));
            case "obj_pedir" -> PoliciaObjetivo.pedir(p);
            case "obj_seguir" -> PoliciaObjetivo.seguir(p, d.getString("k"));
            case "consumir" -> consumir(p);
            case "tutorial_saltar" -> PoliciaTutorial.saltar(p);
            case "tut_ok" -> PoliciaTutorial.ok(p, d.getInt("paso"));
            default -> {
            }
        }
    }

    // Prioridad de un objeto para el consumible rapido (0 = no sirve).
    // Lo usan el servidor y el HUD del cliente.
    public static int prioridad(ItemStack st, Player p) {
        if (st.isEmpty()) {
            return 0;
        }
        if (st.getItem() instanceof PoliciaCocina.Elixir e && e.tipo == 0) {
            return 6;
        }
        if (st.is(Items.POTION)) {
            for (MobEffectInstance m : PotionUtils.getMobEffects(st)) {
                if (m.getEffect() == MobEffects.HEAL || m.getEffect() == MobEffects.REGENERATION) {
                    return 5;
                }
            }
            return 0;
        }
        if (st.is(Items.ENCHANTED_GOLDEN_APPLE) || st.is(Items.GOLDEN_APPLE)) {
            return 4;
        }
        if (st.getItem() instanceof PoliciaCocina.Comida) {
            return 3;
        }
        if (st.isEdible() && p.getFoodData().needsFood() && !st.is(Items.ROTTEN_FLESH) && !st.is(Items.SPIDER_EYE) && !st.is(Items.POISONOUS_POTATO) && !st.is(Items.PUFFERFISH) && !st.is(Items.CHORUS_FRUIT)) {
            return 1;
        }
        return 0;
    }

    static void consumir(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData();
        long ahora = p.serverLevel().getGameTime();
        if (ahora < pd.getLong("consumo_cd")) {
            p.displayClientMessage(Component.literal("§7Consumible rápido: esperá " + (pd.getLong("consumo_cd") - ahora + 19) / 20 + " s"), true);
            return;
        }
        int mejor = -1;
        int pri = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            int q = prioridad(p.getInventory().getItem(i), p);
            if (q > pri) {
                pri = q;
                mejor = i;
            }
        }
        if (mejor < 0) {
            p.displayClientMessage(Component.literal("§7No tenés nada para curarte (pociones, elixires, manzanas o comida)"), true);
            return;
        }
        ItemStack st = p.getInventory().getItem(mejor);
        String nombre = st.getHoverName().getString();
        ItemStack res = st.getItem().finishUsingItem(st, p.serverLevel(), p);
        if (st.isEmpty()) {
            p.getInventory().setItem(mejor, res);
        } else if (res != st && !res.isEmpty() && !p.getInventory().add(res)) {
            p.drop(res, false);
        }
        pd.putLong("consumo_cd", ahora + CD_CONSUMO);
        p.serverLevel().playSound(null, p.blockPosition(), pri >= 5 ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8f, 1.2f);
        p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2, p.getZ(), 2, 0.3, 0.1, 0.3, 0);
        p.displayClientMessage(Component.literal("§aUsaste: §f" + nombre), true);
    }
}
