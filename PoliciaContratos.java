package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Contratos entre jugadores: agachado + clic derecho en un atril abre el
// tablero. Un jugador publica un pedido y deja las esmeraldas en garantía;
// otro lo acepta, lo hace y cobra. Los pedidos de "conseguir un objeto" se
// cumplen solos al entregar los objetos. Lo que no se puede entregar en el
// momento queda en el buzón.
public class PoliciaContratos {
    static final String[] TYPE = new String[]{"Construir algo", "Reparar o mejorar un objeto", "Protección / escolta", "Conseguir un objeto", "Cualquier trabajo"};
    static final String[] FOR = new String[]{"albanil", "herrero", "policia", "", ""};
    static final Item[] ICON = new Item[]{Items.BRICKS, Items.ANVIL, Items.SHIELD, Items.CHEST, Items.PAPER};
    static final int[] PAY = new int[]{5, 10, 20, 40};

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaContratos());
    }

    public static class Data extends SavedData {
        final List<CompoundTag> list = new ArrayList<CompoundTag>();
        final CompoundTag mail = new CompoundTag();
        int next = 1;

        static Data get(ServerLevel sl) {
            return sl.getServer().overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "policia_contratos");
        }

        static Data load(CompoundTag t) {
            Data d = new Data();
            for (Tag e : t.getList("c", 10)) d.list.add((CompoundTag) e);
            d.mail.merge(t.getCompound("mail"));
            d.next = Math.max(1, t.getInt("next"));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            ListTag l = new ListTag();
            l.addAll(this.list);
            t.put("c", l);
            t.put("mail", this.mail);
            t.putInt("next", this.next);
            return t;
        }

        CompoundTag find(int id) {
            for (CompoundTag c : this.list) if (c.getInt("id") == id) return c;
            return null;
        }
    }

    // Guarda algo en el buzón de un jugador (se entrega al abrir el tablero o al entrar).
    static void mail(ServerLevel sl, UUID who, ItemStack s) {
        if (s.isEmpty()) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(who);
        if (p != null) {
            PoliciaBoss.give(p, s);
            return;
        }
        Data d = Data.get(sl);
        ListTag l = d.mail.getList(who.toString(), 10);
        l.add(s.save(new CompoundTag()));
        d.mail.put(who.toString(), l);
        d.setDirty();
    }

    static void pay(ServerLevel sl, UUID who, int em) {
        while (em > 0) {
            int n = Math.min(64, em);
            PoliciaContratos.mail(sl, who, new ItemStack(Items.EMERALD, n));
            em -= n;
        }
    }

    static void collect(ServerPlayer p) {
        Data d = Data.get(p.serverLevel());
        String k = p.getUUID().toString();
        if (!d.mail.contains(k)) return;
        int n = 0;
        for (Tag e : d.mail.getList(k, 10)) {
            PoliciaBoss.give(p, ItemStack.of((CompoundTag) e));
            ++n;
        }
        d.mail.remove(k);
        d.setDirty();
        if (n > 0) {
            p.sendSystemMessage(Component.literal("§6[Contratos] §fRecibiste lo que había en tu buzón (" + n + ")."));
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            p.getPersistentData().putInt("mus_cur", 0);
            PoliciaContratos.collect(p);
        }
    }

    @SubscribeEvent
    public void lectern(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown()) {
            return;
        }
        if (!(e.getLevel().getBlockState(e.getPos()).getBlock() instanceof LecternBlock)) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        PoliciaContratos.collect(p);
        PoliciaContratos.board(p, 0);
    }

    static ItemStack card(CompoundTag c, ServerPlayer viewer) {
        int t = c.getInt("t");
        List<String> lore = new ArrayList<String>();
        lore.add("Pide: " + c.getString("rn"));
        if (t == 3) {
            lore.add("Quiere: " + c.getInt("n") + " x " + PoliciaContratos.itemName(c.getString("it")));
        }
        lore.add("Paga: " + c.getInt("pay") + " esmeraldas (ya depositadas)");
        if (!FOR[t].isEmpty()) {
            lore.add("Para: " + PoliciaBoss.JOBNAME[java.util.Arrays.asList(PoliciaBoss.JOBS).indexOf(FOR[t])]);
        }
        boolean mine = c.getUUID("r").equals(viewer.getUUID());
        if (c.hasUUID("a")) {
            lore.add("Lo tomó: " + c.getString("an"));
            if (mine) lore.add("Clic: pagar (trabajo hecho) · Mayús+clic: reclamar");
            else if (c.getUUID("a").equals(viewer.getUUID())) lore.add(t == 3 ? "Clic: entregar los objetos" : "Clic: abandonar el contrato");
        } else {
            lore.add(mine ? "Clic: cancelar y recuperar las esmeraldas" : "Clic: aceptar el contrato");
        }
        return PoliciaBoss.named(ICON[t], "#" + c.getInt("id") + " " + TYPE[t], lore.toArray(new String[0]));
    }

    static String itemName(String id) {
        try {
            return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id))).getHoverName().getString();
        } catch (Exception ex) {
            return id;
        }
    }

    static void board(ServerPlayer sp, int page) {
        Data d = Data.get(sp.serverLevel());
        SimpleContainer c = new SimpleContainer(27);
        List<CompoundTag> l = d.list;
        int from = page * 18;
        for (int i = 0; i < 18 && from + i < l.size(); ++i) {
            c.setItem(i, PoliciaContratos.card(l.get(from + i), sp));
        }
        for (int i = 18; i < 27; ++i) c.setItem(i, PoliciaBoss.pane());
        c.setItem(22, PoliciaBoss.named(Items.WRITABLE_BOOK, "Publicar un contrato", "Elegís el tipo y cuánto pagás", "Para \"Conseguir un objeto\": tené en la mano lo que querés"));
        if (page > 0) c.setItem(18, PoliciaBoss.named(Items.ARROW, "Página anterior"));
        if (from + 18 < l.size()) c.setItem(26, PoliciaBoss.named(Items.ARROW, "Página siguiente"));
        PoliciaBoss.openMenu3(sp, "Tablero de contratos (" + l.size() + ")", c, (p, slot) -> {
            if (slot == 22) {
                PoliciaBoss.later(p, () -> PoliciaContratos.publish(p));
                return;
            }
            if (slot == 18 && page > 0) {
                PoliciaBoss.later(p, () -> PoliciaContratos.board(p, page - 1));
                return;
            }
            if (slot == 26) {
                PoliciaBoss.later(p, () -> PoliciaContratos.board(p, page + 1));
                return;
            }
            if (slot < 18 && from + slot < d.list.size()) {
                p.closeContainer();
                PoliciaContratos.act(p, d.list.get(from + slot).getInt("id"));
            }
        });
    }

    static void publish(ServerPlayer sp) {
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; ++i) c.setItem(i, PoliciaBoss.pane());
        for (int t = 0; t < TYPE.length; ++t) {
            c.setItem(2 + t * 2, PoliciaBoss.named(ICON[t], TYPE[t], FOR[t].isEmpty() ? "Para cualquier oficio" : "Para " + FOR[t]));
        }
        for (int i = 0; i < PAY.length; ++i) {
            c.setItem(19 + i * 2, PoliciaBoss.named(Items.EMERALD, "Pagar " + PAY[i] + " esmeraldas", "Elegí primero el tipo (arriba)"));
        }
        int[] chosen = new int[]{-1};
        PoliciaBoss.openMenu3(sp, "Nuevo contrato", c, (p, slot) -> {
            for (int t = 0; t < TYPE.length; ++t) {
                if (slot == 2 + t * 2) {
                    chosen[0] = t;
                    PoliciaMod.msg(p, "Tipo: " + TYPE[t] + ". Ahora elegí cuánto pagás (abajo)");
                    return;
                }
            }
            int pi = slot >= 19 && (slot - 19) % 2 == 0 ? (slot - 19) / 2 : -1;
            if (pi < 0 || pi >= PAY.length) return;
            if (chosen[0] < 0) {
                PoliciaMod.msg(p, "Primero elegí el tipo de trabajo");
                return;
            }
            p.closeContainer();
            int t = chosen[0];
            int pay = PAY[pi];
            if (PoliciaBoss.emeralds(p) < pay) {
                PoliciaMod.msg(p, "No tenés " + pay + " esmeraldas");
                return;
            }
            ItemStack held = p.getMainHandItem();
            if (t == 3 && held.isEmpty()) {
                PoliciaMod.msg(p, "Tené en la mano el objeto que querés conseguir (la cantidad también cuenta)");
                return;
            }
            Data d = Data.get(p.serverLevel());
            if (d.list.size() >= 54) {
                PoliciaMod.msg(p, "El tablero está lleno");
                return;
            }
            int mine = 0;
            for (CompoundTag x : d.list) if (x.getUUID("r").equals(p.getUUID())) ++mine;
            if (mine >= 5) {
                PoliciaMod.msg(p, "Ya tenés 5 contratos publicados");
                return;
            }
            PoliciaBoss.takeEmeralds(p, pay);
            CompoundTag c2 = new CompoundTag();
            c2.putInt("id", d.next++);
            c2.putInt("t", t);
            c2.putInt("pay", pay);
            c2.putUUID("r", p.getUUID());
            c2.putString("rn", p.getName().getString());
            if (t == 3) {
                c2.putString("it", BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
                c2.putInt("n", held.getCount());
            }
            d.list.add(c2);
            d.setDirty();
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
            PoliciaMod.msg(p, "§aPublicaste el contrato #" + c2.getInt("id") + " (" + pay + " esmeraldas en garantía)");
            String job = FOR[t];
            for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
                if (o != p && (job.isEmpty() || job.equals(PoliciaMod.job(o)))) {
                    o.sendSystemMessage(Component.literal("§6[Contratos] §f" + p.getName().getString() + " publicó: " + TYPE[t] + " por " + pay + " esmeraldas (atril, agachado + clic)"));
                }
            }
        });
    }

    static void act(ServerPlayer p, int id) {
        ServerLevel sl = p.serverLevel();
        Data d = Data.get(sl);
        CompoundTag c = d.find(id);
        if (c == null) return;
        int t = c.getInt("t");
        boolean mine = c.getUUID("r").equals(p.getUUID());
        boolean taken = c.hasUUID("a");
        if (mine) {
            if (!taken) {
                d.list.remove(c);
                d.setDirty();
                PoliciaContratos.pay(sl, p.getUUID(), c.getInt("pay"));
                PoliciaMod.msg(p, "Cancelaste el contrato #" + id + " y recuperaste las esmeraldas");
            } else if (p.isShiftKeyDown()) {
                // el trabajador no cumplió: se libera el contrato
                c.remove("a");
                c.remove("an");
                d.setDirty();
                PoliciaMod.msg(p, "Liberaste el contrato #" + id + ": vuelve a estar disponible");
            } else {
                PoliciaContratos.done(sl, d, c, p.getName().getString());
            }
            return;
        }
        if (!taken) {
            String job = FOR[t];
            if (!job.isEmpty() && !job.equals(PoliciaMod.job(p))) {
                PoliciaMod.msg(p, "Este contrato es para el oficio " + job);
                return;
            }
            c.putUUID("a", p.getUUID());
            c.putString("an", p.getName().getString());
            d.setDirty();
            PoliciaMod.msg(p, "§aAceptaste el contrato #" + id + ": " + TYPE[t]);
            ServerPlayer r = sl.getServer().getPlayerList().getPlayer(c.getUUID("r"));
            if (r != null) {
                r.sendSystemMessage(Component.literal("§6[Contratos] §f" + p.getName().getString() + " aceptó tu contrato #" + id + "."));
            }
            return;
        }
        if (c.getUUID("a").equals(p.getUUID())) {
            if (t == 3) {
                Item want = BuiltInRegistries.ITEM.get(new ResourceLocation(c.getString("it")));
                int n = c.getInt("n");
                if (PoliciaEncargo.have(p, want) < n) {
                    PoliciaMod.msg(p, "Te faltan objetos: " + n + " x " + PoliciaContratos.itemName(c.getString("it")));
                    return;
                }
                int left = n;
                for (ItemStack s : p.getInventory().items) {
                    if (left <= 0) break;
                    if (!s.is(want)) continue;
                    int k = Math.min(left, s.getCount());
                    PoliciaContratos.mail(sl, c.getUUID("r"), s.split(k));
                    left -= k;
                }
                PoliciaContratos.done(sl, d, c, "entrega automática");
            } else {
                c.remove("a");
                c.remove("an");
                d.setDirty();
                PoliciaMod.msg(p, "Abandonaste el contrato #" + id);
            }
            return;
        }
        PoliciaMod.msg(p, "Ese contrato ya lo tomó " + c.getString("an"));
    }

    static void done(ServerLevel sl, Data d, CompoundTag c, String by) {
        d.list.remove(c);
        d.setDirty();
        UUID a = c.getUUID("a");
        PoliciaContratos.pay(sl, a, c.getInt("pay"));
        ServerPlayer w = sl.getServer().getPlayerList().getPlayer(a);
        if (w != null) {
            w.getPersistentData().putInt("pol_xp", PoliciaMod.xp(w) + 5);
            w.sendSystemMessage(Component.literal("§a[Contratos] Cobraste el contrato #" + c.getInt("id") + ": " + c.getInt("pay") + " esmeraldas y 5 XP de oficio"));
            PoliciaMod.sync(w);
        }
        ServerPlayer r = sl.getServer().getPlayerList().getPlayer(c.getUUID("r"));
        if (r != null) {
            r.sendSystemMessage(Component.literal("§a[Contratos] Contrato #" + c.getInt("id") + " terminado (" + by + ")."));
        }
        PoliciaDiario.add(sl, c.getString("an") + " cumplió un contrato de " + c.getString("rn") + " (" + TYPE[c.getInt("t")].toLowerCase() + ")");
    }
}
