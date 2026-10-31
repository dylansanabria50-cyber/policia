package com.example.policia;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 10b: comercio entre jugadores. /comercio <jugador> manda el pedido y
// el otro lo acepta con /comercio aceptar (a 8 bloques). Se abre una ventana
// compartida: arriba lo que ofrece el que pidio, abajo lo del otro. Cada uno
// marca "Listo" con la lana de su lado. ANTIESTAFA: cualquier cambio pone a
// los dos en "no listo". Con los dos listos hay una cuenta de 3 s y el
// intercambio se hace en el servidor. Si alguien cierra, cada uno recupera
// lo suyo.
public class PoliciaComercio {
    static final Map<UUID, UUID> PEDIDOS = new HashMap<UUID, UUID>();
    static final Map<UUID, Long> PEDIDO_T = new HashMap<UUID, Long>();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Eventos());
    }

    static class Sesion {
        final ServerPlayer a;
        final ServerPlayer b;
        final SimpleContainer c = new SimpleContainer(27);
        boolean listoA;
        boolean listoB;
        boolean terminada;
        int cuenta = -1;

        Sesion(ServerPlayer a, ServerPlayer b) {
            this.a = a;
            this.b = b;
            for (int i = 9; i < 18; i++) {
                this.c.setItem(i, new ItemStack(Items.GRAY_STAINED_GLASS_PANE).setHoverName(Component.literal("§7Arriba: " + a.getName().getString() + " · Abajo: " + b.getName().getString())));
            }
            this.indicadores();
        }

        void indicadores() {
            this.c.setItem(8, lana(this.listoA, this.a));
            this.c.setItem(26, lana(this.listoB, this.b));
        }

        static ItemStack lana(boolean listo, ServerPlayer p) {
            ItemStack st = new ItemStack(listo ? Items.LIME_WOOL : Items.RED_WOOL);
            st.setHoverName(Component.literal((listo ? "§a✔ Listo: " : "§c✘ No listo: ") + p.getName().getString() + " §7(clic para cambiar)"));
            return st;
        }

        void cambio() {
            this.listoA = false;
            this.listoB = false;
            this.cuenta = -1;
            this.indicadores();
        }

        void devolver() {
            for (int i = 0; i < 8; i++) {
                PoliciaHabOficios.dar(this.a, this.c.getItem(i).copy());
                this.c.setItem(i, ItemStack.EMPTY);
            }
            for (int i = 18; i < 26; i++) {
                PoliciaHabOficios.dar(this.b, this.c.getItem(i).copy());
                this.c.setItem(i, ItemStack.EMPTY);
            }
        }

        void cerrar(boolean hecho) {
            if (this.terminada) {
                return;
            }
            this.terminada = true;
            if (hecho) {
                for (int i = 0; i < 8; i++) {
                    PoliciaHabOficios.dar(this.b, this.c.getItem(i).copy());
                    this.c.setItem(i, ItemStack.EMPTY);
                }
                for (int i = 18; i < 26; i++) {
                    PoliciaHabOficios.dar(this.a, this.c.getItem(i).copy());
                    this.c.setItem(i, ItemStack.EMPTY);
                }
                for (ServerPlayer p : new ServerPlayer[]{this.a, this.b}) {
                    p.displayClientMessage(Component.literal("§a¡Intercambio hecho!"), false);
                    p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0f, 1.2f);
                }
            } else {
                this.devolver();
                for (ServerPlayer p : new ServerPlayer[]{this.a, this.b}) {
                    p.displayClientMessage(Component.literal("§7El comercio se canceló: cada uno recuperó lo suyo"), false);
                }
            }
            for (ServerPlayer p : new ServerPlayer[]{this.a, this.b}) {
                if (p.containerMenu instanceof Menu m && m.s == this) {
                    p.closeContainer();
                }
            }
        }
    }

    public static class Menu extends ChestMenu {
        final Sesion s;
        final boolean ladoA;

        Menu(int id, Inventory inv, Sesion s, boolean ladoA) {
            super(MenuType.GENERIC_9x3, id, inv, s.c, 3);
            this.s = s;
            this.ladoA = ladoA;
        }

        boolean mio(int slot) {
            return this.ladoA ? slot >= 0 && slot < 8 : slot >= 18 && slot < 26;
        }

        @Override
        public void clicked(int slot, int boton, ClickType tipo, Player p) {
            if (this.s.terminada) {
                return;
            }
            if (slot >= 0 && slot < 27) {
                if (slot == (this.ladoA ? 8 : 26) && tipo == ClickType.PICKUP) {
                    if (this.ladoA) {
                        this.s.listoA = !this.s.listoA;
                    } else {
                        this.s.listoB = !this.s.listoB;
                    }
                    this.s.cuenta = this.s.listoA && this.s.listoB ? 60 : -1;
                    this.s.indicadores();
                    if (this.s.cuenta > 0) {
                        Sesion se = this.s;
                        for (ServerPlayer o : new ServerPlayer[]{se.a, se.b}) {
                            o.displayClientMessage(Component.literal("§eLos dos listos: intercambio en 3 s..."), true);
                        }
                        PoliciaHab.tarea(t -> {
                            if (se.terminada || se.cuenta < 0) {
                                return true;
                            }
                            se.cuenta--;
                            if (se.cuenta <= 0) {
                                se.cerrar(true);
                                return true;
                            }
                            return false;
                        });
                    }
                    return;
                }
                if (!this.mio(slot)) {
                    return;
                }
                super.clicked(slot, boton, tipo, p);
                this.s.cambio();
                return;
            }
            if (tipo == ClickType.PICKUP_ALL) {
                return;
            }
            super.clicked(slot, boton, tipo, p);
        }

        @Override
        public ItemStack quickMoveStack(Player p, int index) {
            Slot sl = this.slots.get(index);
            if (sl == null || !sl.hasItem() || this.s.terminada) {
                return ItemStack.EMPTY;
            }
            ItemStack st = sl.getItem();
            ItemStack copia = st.copy();
            if (index < 27) {
                if (!this.mio(index) || !this.moveItemStackTo(st, 27, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                int ini = this.ladoA ? 0 : 18;
                if (!this.moveItemStackTo(st, ini, ini + 8, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (st.isEmpty()) {
                sl.set(ItemStack.EMPTY);
            } else {
                sl.setChanged();
            }
            this.s.cambio();
            return copia;
        }

        @Override
        public boolean canTakeItemForPickAll(ItemStack st, Slot slot) {
            return slot.container != this.s.c;
        }

        @Override
        public boolean canDragTo(Slot slot) {
            return slot.container != this.s.c || this.mio(slot.getContainerSlot());
        }

        @Override
        public void removed(Player p) {
            super.removed(p);
            if (!this.s.terminada) {
                this.s.cerrar(false);
            }
        }
    }

    static void abrir(ServerPlayer a, ServerPlayer b) {
        Sesion s = new Sesion(a, b);
        a.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Menu(id, inv, s, true), Component.literal("Comercio con " + b.getName().getString())));
        b.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Menu(id, inv, s, false), Component.literal("Comercio con " + a.getName().getString())));
    }

    public static class Eventos {
        @SubscribeEvent
        public void comandos(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("comercio")
                    .then(Commands.literal("aceptar").executes(c -> {
                        ServerPlayer b = c.getSource().getPlayerOrException();
                        UUID desde = PEDIDOS.remove(b.getUUID());
                        Long t = PEDIDO_T.remove(b.getUUID());
                        ServerPlayer a = desde == null ? null : b.server.getPlayerList().getPlayer(desde);
                        if (a == null || t == null || b.level().getGameTime() - t > 600) {
                            PoliciaMod.msg(b, "No tenés pedidos de comercio vigentes");
                            return 0;
                        }
                        if (a.level() != b.level() || a.distanceTo(b) > 8) {
                            PoliciaMod.msg(b, "Tienen que estar a menos de 8 bloques");
                            return 0;
                        }
                        abrir(a, b);
                        return 1;
                    }))
                    .then(Commands.argument("jugador", StringArgumentType.word()).executes(c -> {
                        ServerPlayer a = c.getSource().getPlayerOrException();
                        ServerPlayer b = a.server.getPlayerList().getPlayerByName(StringArgumentType.getString(c, "jugador"));
                        if (b == null || b == a) {
                            PoliciaMod.msg(a, "Ese jugador no está conectado");
                            return 0;
                        }
                        if (a.level() != b.level() || a.distanceTo(b) > 8) {
                            PoliciaMod.msg(a, "Acercate a menos de 8 bloques de " + b.getName().getString());
                            return 0;
                        }
                        PEDIDOS.put(b.getUUID(), a.getUUID());
                        PEDIDO_T.put(b.getUUID(), a.level().getGameTime());
                        PoliciaMod.msg(a, "Le pediste comerciar a " + b.getName().getString());
                        b.displayClientMessage(Component.literal("§6" + a.getName().getString() + " quiere comerciar con vos. §fEscribí §e/comercio aceptar §f(30 s)"), false);
                        return 1;
                    })));
        }
    }
}
