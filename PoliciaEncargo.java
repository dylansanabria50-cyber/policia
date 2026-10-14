package com.example.policia;

import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

// Encargos para el herrero: los jefes de los otros oficios (policía, albañil
// y ladrones) le piden armas, armaduras o herramientas. Uno por jefe y por
// día de Minecraft.
public class PoliciaEncargo {
    // {item, cantidad, esmeraldas}
    static final Object[][][] ORDERS = new Object[][][]{
            {{"iron_chestplate", 2, 10}, {"iron_helmet", 3, 8}, {"shield", 3, 7}, {"iron_sword", 3, 8}, {"iron_boots", 3, 7}, {"crossbow", 2, 7}},
            {{"iron_pickaxe", 3, 8}, {"iron_shovel", 3, 6}, {"iron_axe", 2, 6}, {"anvil", 1, 8}, {"iron_bars", 16, 6}, {"chain", 8, 6}},
            {{"iron_sword", 2, 7}, {"shears", 2, 5}, {"iron_boots", 2, 6}, {"iron_door", 2, 5}, {"tripwire_hook", 8, 4}, {"crossbow", 1, 6}}
    };
    static final String[] WHY = new String[]{
            "Mis agentes necesitan equipo nuevo para patrullar.",
            "En la obra se nos gastaron las herramientas.",
            "Los muchachos necesitan algo filoso para el próximo trabajo."
    };
    static final int XP = 8;

    static Object[] today(int j, ServerPlayer p) {
        long day = PoliciaLadron.day();
        Random r = new Random(day * 104729L + j * 31L);
        Object[][] l = ORDERS[j];
        return l[r.nextInt(l.length)];
    }

    static Item item(Object[] o) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", (String) o[0]));
    }

    static boolean doneToday(ServerPlayer p, int j) {
        return p.getPersistentData().getLong("enc_d" + j) == PoliciaLadron.day() + 1L;
    }

    static int have(ServerPlayer p, Item it) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(it)) n += s.getCount();
        }
        return n;
    }

    static ItemStack card(ServerPlayer p, int j) {
        Object[] o = PoliciaEncargo.today(j, p);
        Item it = PoliciaEncargo.item(o);
        String name = new ItemStack(it).getHoverName().getString();
        if (PoliciaEncargo.doneToday(p, j)) {
            return PoliciaBoss.named(Items.LIME_DYE, "Encargo cumplido", "Ya me entregaste lo de hoy.", "Mañana tengo otro encargo.");
        }
        return PoliciaBoss.named(Items.SMITHING_TABLE, "Encargo para el herrero", WHY[j], "Necesito: " + o[1] + " x " + name, "Tenés: " + Math.min((Integer) o[1], PoliciaEncargo.have(p, it)) + "/" + o[1], "Pago: " + o[2] + " esmeraldas y " + XP + " XP de oficio", "Clic para entregar");
    }

    static void deliver(ServerPlayer p, int j, String boss) {
        p.closeContainer();
        if (PoliciaEncargo.doneToday(p, j)) {
            p.sendSystemMessage(Component.literal("<" + boss + "> Ya cumpliste mi encargo de hoy. Volvé mañana."));
            return;
        }
        Object[] o = PoliciaEncargo.today(j, p);
        Item it = PoliciaEncargo.item(o);
        int need = (Integer) o[1];
        String name = new ItemStack(it).getHoverName().getString();
        if (PoliciaEncargo.have(p, it) < need) {
            p.sendSystemMessage(Component.literal("<" + boss + "> Te pedí " + need + " x " + name + ". Traémelos cuando los tengas forjados."));
            return;
        }
        int left = need;
        for (ItemStack s : p.getInventory().items) {
            if (left <= 0) break;
            if (!s.is(it)) continue;
            int t = Math.min(left, s.getCount());
            s.shrink(t);
            left -= t;
        }
        p.getPersistentData().putLong("enc_d" + j, PoliciaLadron.day() + 1L);
        p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + XP);
        PoliciaBoss.give(p, new ItemStack((ItemLike) Items.EMERALD, (Integer) o[2]));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0f, 1.0f);
        p.sendSystemMessage(Component.literal("<" + boss + "> ¡Excelente trabajo, herrero! Acá tenés: " + o[2] + " esmeraldas y " + XP + " XP."));
        PoliciaMod.sync(p);
    }
}
