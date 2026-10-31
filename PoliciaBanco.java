package com.example.policia;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 10a: banco, almacen de Dona Rosa y subasta.
// - Banco (/banco): cuenta en esmeraldas guardada en el mundo (se usa desde
//   cualquier aldea), interes del 2 % por dia (hasta 10 por dia), prestamo
//   de hasta 10 + 2 x nivel que vence a los 5 dias con 20 % de recargo; si
//   vence, se embarga del saldo.
// - Dona Rosa: almacen general en cada aldea (aparece al descubrirla).
// - Subasta (/subasta): una a la vez para todo el server; 2 minutos, +10 s
//   si alguien puja al final. Las pujas salen de la cuenta del banco.
public class PoliciaBanco {
    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosBanco());
    }

    static CompoundTag cuentas(MinecraftServer s) {
        return PoliciaMundo.get(s).getSeccion("banco");
    }

    static CompoundTag cuenta(MinecraftServer s, UUID u) {
        CompoundTag c = cuentas(s);
        if (!c.contains(u.toString())) {
            CompoundTag t = new CompoundTag();
            t.putLong("dia", PoliciaMundo.get(s).dia(s));
            c.put(u.toString(), t);
        }
        return c.getCompound(u.toString());
    }

    static void sucio(MinecraftServer s) {
        PoliciaMundo.get(s).setDirty();
    }

    // Interes, vencimiento del prestamo y entregas pendientes.
    static void actualizar(ServerPlayer p) {
        MinecraftServer s = p.server;
        CompoundTag c = cuenta(s, p.getUUID());
        long hoy = PoliciaMundo.get(s).dia(s);
        long dias = Math.min(30, hoy - c.getLong("dia"));
        if (dias > 0) {
            int saldo = c.getInt("saldo");
            int ganado = 0;
            // Ley "comercio" del alcalde: el banco paga el doble.
            int por = "comercio".equals(PoliciaAlcalde.ley(s)) ? 2 : 1;
            for (int i = 0; i < dias; i++) {
                int inc = Math.min(10 * por, saldo / 50 * por);
                saldo += inc;
                ganado += inc;
            }
            c.putInt("saldo", saldo);
            c.putLong("dia", hoy);
            if (ganado > 0) {
                p.displayClientMessage(Component.literal("§6Banco: §fganaste §a" + ganado + "§f de interés"), false);
            }
        }
        if (c.getInt("deuda") > 0 && hoy > c.getLong("vence")) {
            int deuda = c.getInt("deuda");
            int saca = Math.min(deuda, c.getInt("saldo"));
            c.putInt("saldo", c.getInt("saldo") - saca);
            c.putInt("deuda", deuda - saca);
            p.displayClientMessage(Component.literal("§cBanco: venció tu préstamo. Te embargaron " + saca + " esmeraldas" + (deuda - saca > 0 ? " y todavía debés " + (deuda - saca) : "")), false);
            if (deuda - saca == 0) {
                c.remove("vence");
            } else {
                c.putLong("vence", hoy + 2);
            }
        }
        ListTag pend = c.getList("pendientes", Tag.TAG_COMPOUND);
        if (!pend.isEmpty()) {
            for (int i = 0; i < pend.size(); i++) {
                PoliciaHabOficios.dar(p, ItemStack.of(pend.getCompound(i)));
            }
            c.remove("pendientes");
            p.displayClientMessage(Component.literal("§6Te llegaron objetos de la subasta al inventario"), false);
        }
        sucio(s);
    }

    static boolean enAldea(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        var st = sl.structureManager().getStructureWithPieceAt(p.blockPosition(), StructureTags.VILLAGE);
        return (st != null && st.isValid()) || PoliciaCasa.enSuCasa(p) || p.isCreative();
    }

    static int esmeraldas(ServerPlayer p) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) {
            if (st.is(Items.EMERALD)) {
                n += st.getCount();
            }
        }
        return n;
    }

    // Saca n esmeraldas: primero de la cuenta, despues del inventario.
    static boolean cobrar(ServerPlayer p, int n) {
        CompoundTag c = cuenta(p.server, p.getUUID());
        int deCuenta = Math.min(n, c.getInt("saldo"));
        if (deCuenta + esmeraldas(p) < n) {
            return false;
        }
        c.putInt("saldo", c.getInt("saldo") - deCuenta);
        if (n - deCuenta > 0) {
            PoliciaHabOficios.pagar(p, n - deCuenta);
        }
        sucio(p.server);
        return true;
    }

    static void acreditar(MinecraftServer s, UUID u, int n) {
        CompoundTag c = cuenta(s, u);
        c.putInt("saldo", c.getInt("saldo") + n);
        sucio(s);
    }

    static void entregar(MinecraftServer s, UUID u, ItemStack st) {
        ServerPlayer p = s.getPlayerList().getPlayer(u);
        if (p != null) {
            PoliciaHabOficios.dar(p, st);
            return;
        }
        CompoundTag c = cuenta(s, u);
        ListTag l = c.getList("pendientes", Tag.TAG_COMPOUND);
        l.add(st.save(new CompoundTag()));
        c.put("pendientes", l);
        sucio(s);
    }

    // ---------------- Dona Rosa ----------------
    static void donaRosa(ServerLevel sl, double x, double y, double z) {
        WanderingTrader w = EntityType.WANDERING_TRADER.create(sl);
        if (w == null) {
            return;
        }
        int yy = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
        w.moveTo(x + 0.5, yy, z + 0.5, sl.random.nextFloat() * 360, 0);
        w.setCustomName(Component.literal("§6Doña Rosa §7(almacén)"));
        w.setCustomNameVisible(true);
        w.setNoAi(true);
        w.setInvulnerable(true);
        w.setPersistenceRequired();
        w.setDespawnDelay(0);
        w.getPersistentData().putBoolean("pol_rosa", true);
        MerchantOffers o = w.getOffers();
        o.clear();
        Object[][] vende = {{1, Items.BREAD, 6}, {1, Items.TORCH, 16}, {1, Items.APPLE, 4}, {2, Items.COOKED_BEEF, 6}, {3, Items.WHITE_BED, 1}, {2, Items.OAK_LOG, 16},
                {4, Items.IRON_PICKAXE, 1}, {4, Items.IRON_AXE, 1}, {3, Items.SHEARS, 1}, {1, Items.WHEAT_SEEDS, 16}, {2, Items.BUCKET, 1}, {5, Items.SHIELD, 1}, {2, Items.LANTERN, 2}, {1, Items.PAPER, 12}};
        for (Object[] v : vende) {
            o.add(new MerchantOffer(new ItemStack(Items.EMERALD, (int) v[0]), new ItemStack((net.minecraft.world.item.Item) v[1], (int) v[2]), 9999, 1, 0.0f));
        }
        Object[][] compra = {{Items.WHEAT, 20}, {Items.COBBLESTONE, 32}, {Items.ROTTEN_FLESH, 24}, {Items.STRING, 14}, {Items.COAL, 12}, {Items.LEATHER, 6}};
        for (Object[] c : compra) {
            o.add(new MerchantOffer(new ItemStack((net.minecraft.world.item.Item) c[0], (int) c[1]), new ItemStack(Items.EMERALD), 9999, 1, 0.0f));
        }
        sl.addFreshEntity(w);
    }

    // ---------------- Subasta ----------------
    static CompoundTag subasta(MinecraftServer s) {
        return PoliciaMundo.get(s).getSeccion("subasta");
    }

    static void anunciar(MinecraftServer s, String txt) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.displayClientMessage(Component.literal("§6[Subasta] §f" + txt), false);
        }
    }

    static void cerrar(MinecraftServer s) {
        CompoundTag a = subasta(s);
        if (!a.contains("item")) {
            return;
        }
        ItemStack st = ItemStack.of(a.getCompound("item"));
        UUID vendedor = a.getUUID("vendedor");
        if (a.contains("ganador")) {
            UUID g = a.getUUID("ganador");
            int monto = a.getInt("puja");
            entregar(s, g, st);
            acreditar(s, vendedor, monto);
            anunciar(s, a.getString("gnom") + " se llevó " + st.getCount() + " × " + st.getHoverName().getString() + " por " + monto + " esmeraldas");
            PoliciaDiario.add(s.overworld(), "Subasta: " + st.getHoverName().getString() + " se vendió a " + monto + " esmeraldas");
        } else {
            entregar(s, vendedor, st);
            anunciar(s, "Nadie pujó por " + st.getHoverName().getString() + ": vuelve a su dueño");
        }
        PoliciaMundo.get(s).raiz.put("subasta", new CompoundTag());
        sucio(s);
    }

    public static class EventosBanco {
        @SubscribeEvent
        public void entrar(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                actualizar(p);
            }
        }

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 5) {
                return;
            }
            CompoundTag a = subasta(e.getServer());
            if (!a.contains("item")) {
                return;
            }
            long fin = a.getLong("fin");
            long ahora = e.getServer().overworld().getGameTime();
            long falta = (fin - ahora) / 20;
            if (falta == 30 || falta == 10) {
                anunciar(e.getServer(), "Quedan " + falta + " s para " + ItemStack.of(a.getCompound("item")).getHoverName().getString() + " (puja actual: " + Math.max(a.getInt("puja"), a.getInt("base")) + ")");
            }
            if (ahora >= fin) {
                cerrar(e.getServer());
            }
        }

        @SubscribeEvent
        public void comandos(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("banco")
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        actualizar(p);
                        CompoundTag t = cuenta(p.server, p.getUUID());
                        int max = 10 + PoliciaRPG.nivel(p) * 2;
                        p.displayClientMessage(Component.literal("§6Banco · §fSaldo: §a" + t.getInt("saldo") + " esmeraldas"
                                + (t.getInt("deuda") > 0 ? " §7· §cDeuda: " + t.getInt("deuda") + " (vence el día " + t.getLong("vence") + ")" : " §7· préstamo disponible: hasta " + max)
                                + "\n§7/banco depositar <n|todo> · /banco retirar <n> · /banco prestamo <n> · /banco pagar"), false);
                        return 1;
                    })
                    .then(Commands.literal("depositar").then(Commands.argument("cantidad", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        if (!enAldea(p)) {
                            PoliciaMod.msg(p, "El banco atiende en las aldeas (o en tu casa)");
                            return 0;
                        }
                        actualizar(p);
                        String q = StringArgumentType.getString(c, "cantidad");
                        int n = "todo".equalsIgnoreCase(q) ? esmeraldas(p) : parse(q);
                        if (n <= 0 || esmeraldas(p) < n) {
                            PoliciaMod.msg(p, "No tenés esa cantidad de esmeraldas");
                            return 0;
                        }
                        PoliciaHabOficios.pagar(p, n);
                        acreditar(p.server, p.getUUID(), n);
                        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1.6f);
                        PoliciaMod.msg(p, "Depositaste " + n + ". Saldo: " + cuenta(p.server, p.getUUID()).getInt("saldo"));
                        return 1;
                    })))
                    .then(Commands.literal("retirar").then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 100000)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        if (!enAldea(p)) {
                            PoliciaMod.msg(p, "El banco atiende en las aldeas (o en tu casa)");
                            return 0;
                        }
                        actualizar(p);
                        int n = IntegerArgumentType.getInteger(c, "cantidad");
                        CompoundTag t = cuenta(p.server, p.getUUID());
                        if (t.getInt("saldo") < n) {
                            PoliciaMod.msg(p, "Tu saldo es " + t.getInt("saldo"));
                            return 0;
                        }
                        t.putInt("saldo", t.getInt("saldo") - n);
                        sucio(p.server);
                        while (n > 0) {
                            int q = Math.min(64, n);
                            PoliciaHabOficios.dar(p, new ItemStack(Items.EMERALD, q));
                            n -= q;
                        }
                        PoliciaMod.msg(p, "Retiraste. Saldo: " + t.getInt("saldo"));
                        return 1;
                    })))
                    .then(Commands.literal("prestamo").then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 1000)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        if (!enAldea(p)) {
                            PoliciaMod.msg(p, "El banco atiende en las aldeas (o en tu casa)");
                            return 0;
                        }
                        actualizar(p);
                        CompoundTag t = cuenta(p.server, p.getUUID());
                        int n = IntegerArgumentType.getInteger(c, "cantidad");
                        int max = 10 + PoliciaRPG.nivel(p) * 2;
                        if (t.getInt("deuda") > 0) {
                            PoliciaMod.msg(p, "Primero pagá el préstamo que tenés (" + t.getInt("deuda") + ")");
                            return 0;
                        }
                        if (n > max) {
                            PoliciaMod.msg(p, "Con tu nivel te prestan hasta " + max);
                            return 0;
                        }
                        long hoy = PoliciaMundo.get(p.server).dia(p.server);
                        t.putInt("deuda", (int) Math.ceil(n * 1.2));
                        t.putLong("vence", hoy + 5);
                        t.putInt("saldo", t.getInt("saldo") + n);
                        sucio(p.server);
                        PoliciaMod.msg(p, "Te prestaron " + n + " (a tu cuenta). Devolvés " + t.getInt("deuda") + " antes del día " + (hoy + 5) + " con /banco pagar");
                        return 1;
                    })))
                    .then(Commands.literal("pagar").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        actualizar(p);
                        CompoundTag t = cuenta(p.server, p.getUUID());
                        int d = t.getInt("deuda");
                        if (d <= 0) {
                            PoliciaMod.msg(p, "No debés nada");
                            return 0;
                        }
                        if (!cobrar(p, d)) {
                            PoliciaMod.msg(p, "Te faltan esmeraldas: debés " + d);
                            return 0;
                        }
                        t.putInt("deuda", 0);
                        t.remove("vence");
                        sucio(p.server);
                        PoliciaMod.msg(p, "¡Préstamo pagado!");
                        return 1;
                    })));
            e.getDispatcher().register(Commands.literal("subasta")
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CompoundTag a = subasta(p.server);
                        if (!a.contains("item")) {
                            PoliciaMod.msg(p, "No hay subasta. Para vender: objeto en la mano y /subasta vender <precio base>");
                            return 0;
                        }
                        ItemStack st = ItemStack.of(a.getCompound("item"));
                        long falta = (a.getLong("fin") - p.level().getGameTime()) / 20;
                        p.displayClientMessage(Component.literal("§6[Subasta] §f" + st.getCount() + " × " + st.getHoverName().getString() + " §7· base " + a.getInt("base") + " · puja " + a.getInt("puja") + (a.contains("gnom") ? " (" + a.getString("gnom") + ")" : "") + " · quedan " + falta + " s · /subasta pujar <monto>"), false);
                        return 1;
                    })
                    .then(Commands.literal("vender").then(Commands.argument("base", IntegerArgumentType.integer(1, 10000)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CompoundTag a = subasta(p.server);
                        if (a.contains("item")) {
                            PoliciaMod.msg(p, "Ya hay una subasta en curso (/subasta)");
                            return 0;
                        }
                        ItemStack st = p.getMainHandItem();
                        if (st.isEmpty()) {
                            PoliciaMod.msg(p, "Tené en la mano lo que querés subastar");
                            return 0;
                        }
                        a.put("item", st.copy().save(new CompoundTag()));
                        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        a.putUUID("vendedor", p.getUUID());
                        a.putInt("base", IntegerArgumentType.getInteger(c, "base"));
                        a.putInt("puja", 0);
                        a.putLong("fin", p.level().getGameTime() + 2400);
                        sucio(p.server);
                        anunciar(p.server, p.getName().getString() + " subasta " + st.getCount() + " × " + st.getHoverName().getString() + " · base " + a.getInt("base") + " · 2 minutos · /subasta pujar <monto>");
                        return 1;
                    })))
                    .then(Commands.literal("pujar").then(Commands.argument("monto", IntegerArgumentType.integer(1, 100000)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CompoundTag a = subasta(p.server);
                        if (!a.contains("item")) {
                            PoliciaMod.msg(p, "No hay subasta en curso");
                            return 0;
                        }
                        int m = IntegerArgumentType.getInteger(c, "monto");
                        if (p.getUUID().equals(a.getUUID("vendedor"))) {
                            PoliciaMod.msg(p, "No podés pujar por lo tuyo");
                            return 0;
                        }
                        if (m < a.getInt("base") || m <= a.getInt("puja")) {
                            PoliciaMod.msg(p, "Tenés que pujar más de " + Math.max(a.getInt("puja"), a.getInt("base") - 1));
                            return 0;
                        }
                        if (!cobrar(p, m)) {
                            PoliciaMod.msg(p, "No te alcanza (cuenta del banco + esmeraldas encima)");
                            return 0;
                        }
                        if (a.contains("ganador")) {
                            acreditar(p.server, a.getUUID("ganador"), a.getInt("puja"));
                            ServerPlayer antes = p.server.getPlayerList().getPlayer(a.getUUID("ganador"));
                            if (antes != null) {
                                antes.displayClientMessage(Component.literal("§6[Subasta] §fTe superaron la puja: te devolvieron " + a.getInt("puja") + " a tu cuenta"), false);
                            }
                        }
                        a.putUUID("ganador", p.getUUID());
                        a.putString("gnom", p.getName().getString());
                        a.putInt("puja", m);
                        long ahora = p.level().getGameTime();
                        if (a.getLong("fin") - ahora < 200) {
                            a.putLong("fin", ahora + 200);
                        }
                        sucio(p.server);
                        anunciar(p.server, p.getName().getString() + " puja " + m);
                        return 1;
                    }))));
        }
    }

    static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
