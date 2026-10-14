package com.example.policia;

import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Servicio del herrero: otro jugador se agacha y hace clic derecho sobre
// un herrero (con el modo activo) sosteniendo un objeto. Le paga esmeraldas
// para repararlo o mejorarlo (+1 Irrompibilidad, hasta III). Las esmeraldas
// van al herrero, que ademas gana XP de oficio y hace la animacion de forja.
public class PoliciaServicio {
    static final int UPGRADE_PRICE = 8;
    static final int ENCH_PRICE = 10;

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaServicio());
    }

    static int repairPrice(ItemStack it) {
        if (!it.isDamageableItem() || it.getDamageValue() <= 0) {
            return it.getBaseRepairCost() > 0 ? 2 : 0;
        }
        float worn = (float) it.getDamageValue() / (float) Math.max(1, it.getMaxDamage());
        return Math.max(2, (int) Math.ceil(worn * 10.0f));
    }

    static int unbreaking(ItemStack it) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, it);
    }

    static boolean canUpgrade(ItemStack it) {
        return it.isDamageableItem() && Enchantments.UNBREAKING.canEnchant(it) && PoliciaServicio.unbreaking(it) < 3;
    }

    @SubscribeEvent
    public void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer client) || !(e.getTarget() instanceof ServerPlayer smith) || client == smith) {
            return;
        }
        if (e.getHand() != InteractionHand.MAIN_HAND || !client.isShiftKeyDown()) {
            return;
        }
        if (!PoliciaHerrero.isHerrero(smith) || !PoliciaMod.on(smith)) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        ItemStack it = client.getMainHandItem();
        if (it.isEmpty()) {
            PoliciaMod.msg(client, "Sostené en la mano lo que querés que el herrero repare o mejore");
            return;
        }
        PoliciaServicio.open(client, smith);
    }

    static void open(ServerPlayer client, ServerPlayer smith) {
        ItemStack it = client.getMainHandItem();
        int rp = PoliciaServicio.repairPrice(it);
        boolean up = PoliciaServicio.canUpgrade(it);
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; ++i) {
            c.setItem(i, PoliciaBoss.pane());
        }
        ItemStack show = it.copy();
        show.setCount(1);
        c.setItem(13, show);
        c.setItem(11, rp > 0 ? PoliciaBoss.named(Items.ANVIL, "Reparar", "Lo deja como nuevo y le quita el costo de yunque", "Precio: " + rp + " esmeraldas") : PoliciaBoss.named(Items.BARRIER, "Reparar", "No está gastado"));
        c.setItem(15, up ? PoliciaBoss.named(Items.ENCHANTED_BOOK, "Mejorar", "+1 Irrompibilidad (tiene " + PoliciaServicio.unbreaking(it) + ", máximo 3)", "Precio: " + UPGRADE_PRICE + " esmeraldas") : PoliciaBoss.named(Items.BARRIER, "Mejorar", "No se puede mejorar más"));
        boolean fw = PoliciaForja.weapon(it) && EnchantmentHelper.getItemEnchantmentLevel(PoliciaForja.FILO.get(), it) < 2;
        boolean ar = PoliciaForja.armor(it) && EnchantmentHelper.getItemEnchantmentLevel(PoliciaForja.TEMPLE.get(), it) < 3;
        c.setItem(20, fw ? PoliciaBoss.named(Items.BLAZE_POWDER, "Filo de fragua", "Prende fuego al golpear y suma daño", "Precio: " + ENCH_PRICE + " esmeraldas") : PoliciaBoss.named(Items.BARRIER, "Filo de fragua", "Solo armas (máx. II)"));
        c.setItem(24, ar ? PoliciaBoss.named(Items.IRON_CHESTPLATE, "Temple", "Devuelve parte del daño al atacante", "Precio: " + ENCH_PRICE + " esmeraldas") : PoliciaBoss.named(Items.BARRIER, "Temple", "Solo armaduras (máx. III)"));
        c.setItem(22, PoliciaBoss.named(Items.EMERALD, "Tenés " + PoliciaBoss.emeralds(client) + " esmeraldas", "Las esmeraldas van para " + smith.getName().getString()));
        PoliciaBoss.openMenu3(client, "Herrero " + smith.getName().getString(), c, (p, slot) -> {
            if (slot != 11 && slot != 15 && slot != 20 && slot != 24) {
                return;
            }
            p.closeContainer();
            if (!smith.isAlive() || smith.distanceToSqr(p) > 64.0 || !PoliciaMod.on(smith)) {
                PoliciaMod.msg(p, "El herrero ya no está disponible");
                return;
            }
            ItemStack cur = p.getMainHandItem();
            if (cur.isEmpty()) {
                return;
            }
            int price = slot == 11 ? PoliciaServicio.repairPrice(cur) : (slot == 15 ? UPGRADE_PRICE : ENCH_PRICE);
            net.minecraft.world.item.enchantment.Enchantment own = slot == 20 ? PoliciaForja.FILO.get() : PoliciaForja.TEMPLE.get();
            boolean ownOk = slot == 20 ? PoliciaForja.weapon(cur) : PoliciaForja.armor(cur);
            if (slot == 11 && price <= 0 || slot == 15 && !PoliciaServicio.canUpgrade(cur) || (slot == 20 || slot == 24) && (!ownOk || EnchantmentHelper.getItemEnchantmentLevel(own, cur) >= own.getMaxLevel())) {
                PoliciaMod.msg(p, slot == 11 ? "No está gastado" : "No se puede mejorar más");
                return;
            }
            if (PoliciaBoss.emeralds(p) < price) {
                PoliciaMod.msg(p, "Te faltan esmeraldas: cuesta " + price);
                return;
            }
            PoliciaBoss.takeEmeralds(p, price);
            PoliciaBoss.give(smith, new ItemStack((ItemLike) Items.EMERALD, price));
            String what;
            if (slot == 11) {
                if (cur.isDamageableItem()) {
                    cur.setDamageValue(0);
                }
                cur.setRepairCost(0);
                what = "reparó";
            } else if (slot == 15) {
                Map<Enchantment, Integer> m = EnchantmentHelper.getEnchantments(cur);
                m.put(Enchantments.UNBREAKING, Math.min(3, PoliciaServicio.unbreaking(cur) + 1));
                EnchantmentHelper.setEnchantments(m, cur);
                what = "mejoró";
            } else {
                Map<Enchantment, Integer> m = EnchantmentHelper.getEnchantments(cur);
                m.put(own, EnchantmentHelper.getItemEnchantmentLevel(own, cur) + 1);
                EnchantmentHelper.setEnchantments(m, cur);
                what = "encantó";
            }
            PoliciaForja.sign(cur, smith);
            smith.getPersistentData().putInt("pol_xp", PoliciaMod.xp(smith) + 3);
            PoliciaHerrero.anim(smith, 2);
            p.level().playSound(null, smith.getX(), smith.getY(), smith.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            p.sendSystemMessage(Component.literal(smith.getName().getString() + " te " + what + ": " + cur.getHoverName().getString() + " (-" + price + " esmeraldas)"));
            smith.sendSystemMessage(Component.literal("Le " + what + " " + cur.getHoverName().getString() + " a " + p.getName().getString() + ": +" + price + " esmeraldas y +3 XP de oficio"));
            PoliciaMod.sync(smith);
        });
    }
}
