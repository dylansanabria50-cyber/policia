package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

// Misiones de los jefes de oficio (Capataz, Jefe de ladrones y Maestro
// herrero; las de policia las sigue dando el Comisario) y las PRUEBAS DE
// INGRESO: quien no tiene oficio tiene que superar la prueba del oficio que
// quiere antes de que el jefe lo acepte. Una sola mision a la vez por
// jugador; se guarda en sus datos y sobrevive a la muerte.
public class PoliciaJefe {
    // Tipos: kill_enemy, kill (mob), place, mine (bloque o #tag), logs, dig,
    // smelt (item o ""), craft_iron, sneak, deliver (item), pick, lock,
    // repair, analyze.
    static class Def {
        final String id;
        final int job;
        final String type;
        final String target;
        final int need;
        final String desc;
        final int xp;
        final int em;

        Def(String id, int job, String type, String target, int need, String desc, int xp, int em) {
            this.id = id;
            this.job = job;
            this.type = type;
            this.target = target;
            this.need = need;
            this.desc = desc;
            this.xp = xp;
            this.em = em;
        }
    }

    // Pruebas de ingreso, una por oficio (0 policia, 1 albanil, 2 ladron, 3 herrero).
    static final Def[] TRIALS = new Def[]{
            new Def("t_policia", 0, "kill_enemy", "", 8, "Prueba de reclutamiento: elimina 8 monstruos para demostrar que podés proteger a la gente", 0, 0),
            new Def("t_albanil", 1, "place", "", 64, "Prueba de obra: coloca 64 bloques", 0, 0),
            new Def("t_ladron", 2, "sneak", "", 150, "Prueba de sigilo: recorre 150 bloques agachado", 0, 0),
            new Def("t_herrero", 3, "smelt", "minecraft:iron_ingot", 16, "Prueba de forja: funde 16 lingotes de hierro en un horno", 0, 0)
    };

    static final Def[] JOBS = new Def[]{
            new Def("a_piedra", 1, "mine", "#minecraft:base_stone_overworld", 64, "Pica 64 bloques de piedra", 6, 4),
            new Def("a_obra", 1, "place", "", 100, "Levanta una obra: coloca 100 bloques", 6, 5),
            new Def("a_troncos", 1, "logs", "", 32, "Tala 32 troncos para los andamios", 5, 4),
            new Def("a_excavar", 1, "dig", "", 48, "Excava 48 bloques de tierra, arena o grava", 5, 3),
            new Def("a_ladrillos", 1, "deliver", "minecraft:bricks", 32, "Entrega 32 bloques de ladrillos", 8, 6),
            new Def("a_vidrio", 1, "deliver", "minecraft:glass", 32, "Entrega 32 bloques de vidrio", 7, 5),
            new Def("l_sigilo", 2, "sneak", "", 250, "Recorre 250 bloques agachado sin que te vean", 6, 4),
            new Def("l_bolsillos", 2, "pick", "", 3, "Haz 3 carterismos exitosos", 8, 6),
            new Def("l_ganzua", 2, "lock", "", 2, "Abre 2 cerraduras con la ganzúa", 7, 5),
            new Def("l_oro", 2, "deliver", "minecraft:gold_ingot", 12, "Entrega 12 lingotes de oro", 7, 6),
            new Def("l_rivales", 2, "kill", "minecraft:pillager", 4, "Elimina 4 saqueadores: nos arruinan el negocio", 7, 5),
            new Def("l_diamantes", 2, "deliver", "minecraft:diamond", 3, "Consigue 3 diamantes para el jefe", 9, 8),
            new Def("h_fundir", 3, "smelt", "minecraft:iron_ingot", 32, "Funde 32 lingotes de hierro", 6, 4),
            new Def("h_forjar", 3, "craft_iron", "", 4, "Forja 4 herramientas, armas o armaduras de hierro", 7, 5),
            new Def("h_yunque", 3, "deliver", "minecraft:anvil", 1, "Entrega un yunque nuevo para el taller", 6, 5),
            new Def("h_reparar", 3, "repair", "", 2, "Usa Reparación Maestra 2 veces", 7, 5),
            new Def("h_analizar", 3, "analyze", "", 3, "Analiza 3 objetos nuevos", 8, 6),
            new Def("h_menas", 3, "mine", "#minecraft:iron_ores", 16, "Mina 16 menas de hierro", 6, 5),
            new Def("h_carbon", 3, "deliver", "minecraft:coal_block", 6, "Entrega 6 bloques de carbón para la forja", 5, 4)
    };

    static void init() {
        PoliciaMod.NET.registerMessage(11, Dialog.class, Dialog::enc, Dialog::dec, Dialog::handle);
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaJefe());
    }

    static Def find(String id) {
        for (Def d : TRIALS) {
            if (d.id.equals(id)) return d;
        }
        for (Def d : JOBS) {
            if (d.id.equals(id)) return d;
        }
        return null;
    }

    static Def active(Player p) {
        return PoliciaJefe.find(p.getPersistentData().getString("jm_id"));
    }

    static boolean isTrial(Def d) {
        return d != null && d.id.startsWith("t_");
    }

    static int prog(Player p) {
        Def d = PoliciaJefe.active(p);
        if (d != null && "deliver".equals(d.type)) {
            return Math.min(d.need, PoliciaJefe.count(p, d.target));
        }
        return p.getPersistentData().getInt("jm_prog");
    }

    static boolean done(Player p) {
        Def d = PoliciaJefe.active(p);
        return d != null && PoliciaJefe.prog(p) >= d.need;
    }

    static int count(Player p, String id) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id)) continue;
            n += s.getCount();
        }
        return n;
    }

    static void take(Player p, String id, int amount) {
        for (ItemStack s : p.getInventory().items) {
            if (amount <= 0) return;
            if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id)) continue;
            int t = Math.min(amount, s.getCount());
            s.shrink(t);
            amount -= t;
        }
    }

    static String itemName(String id) {
        try {
            return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id))).getHoverName().getString();
        } catch (Exception ex) {
            return id;
        }
    }

    static void start(ServerPlayer p, Def d) {
        CompoundTag t = p.getPersistentData();
        t.putString("jm_id", d.id);
        t.putInt("jm_prog", 0);
        t.putDouble("jm_lx", p.getX());
        t.putDouble("jm_lz", p.getZ());
        t.putDouble("jm_acc", 0.0);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
        p.sendSystemMessage(Component.literal("[Misión] " + d.desc).withStyle(s -> s.withColor(0x7CFC7C)));
    }

    static void clear(ServerPlayer p) {
        CompoundTag t = p.getPersistentData();
        t.remove("jm_id");
        t.putInt("jm_prog", 0);
    }

    static void add(ServerPlayer p, String type, int n) {
        Def d = PoliciaJefe.active(p);
        if (d == null || !d.type.equals(type) || n <= 0) {
            return;
        }
        CompoundTag t = p.getPersistentData();
        int before = t.getInt("jm_prog");
        if (before >= d.need) {
            return;
        }
        int now = Math.min(d.need, before + n);
        t.putInt("jm_prog", now);
        if (now >= d.need) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            p.sendSystemMessage(Component.literal("[Misión] Completa! Volvé con " + PoliciaBoss.NAMES[PoliciaJefe.bossKind(d.job)] + (PoliciaJefe.isTrial(d) ? " para que te acepte en el oficio" : " para cobrar")).withStyle(s -> s.withColor(0xFFD700)));
        } else if (d.need <= 20 || now / Math.max(1, d.need / 10) != before / Math.max(1, d.need / 10)) {
            PoliciaMod.msg(p, "Misión: " + d.desc + " (" + now + "/" + d.need + ")");
        }
    }

    static int bossKind(int job) {
        return job == 3 ? PoliciaBoss.HERRERO_BOSS : job;
    }

    static boolean matchBlock(BlockState st, String target) {
        if (target.startsWith("#")) {
            return st.is(TagKey.create(Registries.BLOCK, new ResourceLocation(target.substring(1))));
        }
        return BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString().equals(target);
    }

    // ---------------- Menus del jefe ----------------

    static ItemStack card(Def d, ServerPlayer p, boolean active) {
        List<String> lore = new ArrayList<String>();
        lore.add(d.desc);
        if ("deliver".equals(d.type)) {
            lore.add("Tenés " + Math.min(d.need, PoliciaJefe.count(p, d.target)) + "/" + d.need + " " + PoliciaJefe.itemName(d.target));
        } else if (active) {
            lore.add("Progreso: " + PoliciaJefe.prog(p) + "/" + d.need);
        }
        if (!PoliciaJefe.isTrial(d)) {
            lore.add("Recompensa: " + d.xp + " XP de oficio y " + d.em + " esmeraldas");
        }
        Item icon = switch (d.type) {
            case "kill", "kill_enemy" -> Items.IRON_SWORD;
            case "place" -> Items.BRICKS;
            case "mine" -> Items.IRON_PICKAXE;
            case "logs" -> Items.IRON_AXE;
            case "dig" -> Items.IRON_SHOVEL;
            case "smelt" -> Items.FURNACE;
            case "craft_iron" -> Items.SMITHING_TABLE;
            case "sneak" -> Items.LEATHER_BOOTS;
            case "pick" -> Items.LEATHER;
            case "lock" -> Items.TRIPWIRE_HOOK;
            case "repair" -> Items.ANVIL;
            case "analyze" -> Items.SPYGLASS;
            default -> BuiltInRegistries.ITEM.get(new ResourceLocation(d.target));
        };
        return PoliciaBoss.named(icon, (active ? "Misión actual: " : "Misión: ") + d.desc, lore.toArray(new String[0]));
    }

    static List<Def> offers(ServerPlayer p, int job) {
        List<Def> pool = new ArrayList<Def>();
        for (Def d : JOBS) {
            if (d.job == job) pool.add(d);
        }
        long day = p.serverLevel().getDayTime() / 24000L;
        Random r = new Random(p.getUUID().getMostSignificantBits() ^ day * 31L + job);
        List<Def> out = new ArrayList<Def>();
        while (out.size() < 3 && !pool.isEmpty()) {
            out.add(pool.remove(r.nextInt(pool.size())));
        }
        return out;
    }

    // Menu de misiones de un jefe (solo para los de su oficio).
    static void openMissions(ServerPlayer sp, int bossKind) {
        int job = PoliciaBoss.jobIdx(bossKind);
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; ++i) {
            c.setItem(i, PoliciaBoss.pane());
        }
        Def cur = PoliciaJefe.active(sp);
        if (cur != null) {
            boolean here = cur.job == job && !PoliciaJefe.isTrial(cur);
            c.setItem(13, PoliciaJefe.card(cur, sp, true));
            if (here) {
                c.setItem(11, PoliciaJefe.done(sp) ? PoliciaBoss.named(Items.EMERALD, "Cobrar recompensa", cur.xp + " XP de oficio y " + cur.em + " esmeraldas") : PoliciaBoss.named(Items.CLOCK, "Todavía no terminaste", "Progreso: " + PoliciaJefe.prog(sp) + "/" + cur.need));
                c.setItem(15, PoliciaBoss.named(Items.BARRIER, "Abandonar misión", "Podés pedir otra después"));
            } else {
                c.setItem(15, PoliciaBoss.named(Items.BARRIER, "Abandonar misión", "Es de otro jefe; abandonala para pedir una acá"));
            }
        } else {
            List<Def> offers = PoliciaJefe.offers(sp, job);
            int[] slots = new int[]{11, 13, 15};
            for (int i = 0; i < offers.size(); ++i) {
                c.setItem(slots[i], PoliciaJefe.card(offers.get(i), sp, false));
            }
            c.setItem(22, PoliciaBoss.named(Items.PAPER, "Elegí una misión", "Las ofertas cambian cada día"));
        }
        if (job == 2) {
            c.setItem(20, PoliciaGolpe.card(sp, 1));
            c.setItem(24, PoliciaGolpe.card(sp, 2));
        }
        PoliciaBoss.openMenu3(sp, "Misiones - " + PoliciaBoss.NAMES[bossKind], c, (p, slot) -> {
            if (job == 2 && (slot == 20 || slot == 24)) {
                PoliciaGolpe.click(p, slot == 20 ? 1 : 2);
                return;
            }
            Def a = PoliciaJefe.active(p);
            if (a != null) {
                boolean here = a.job == job && !PoliciaJefe.isTrial(a);
                if (slot == 15) {
                    PoliciaJefe.clear(p);
                    PoliciaMod.msg(p, "Misión abandonada");
                    p.closeContainer();
                } else if (slot == 11 && here && PoliciaJefe.done(p)) {
                    p.closeContainer();
                    PoliciaJefe.reward(p, a);
                }
                return;
            }
            List<Def> o = PoliciaJefe.offers(p, job);
            int idx = slot == 11 ? 0 : (slot == 13 ? 1 : (slot == 15 ? 2 : -1));
            if (idx >= 0 && idx < o.size()) {
                p.closeContainer();
                PoliciaJefe.start(p, o.get(idx));
                p.sendSystemMessage(Component.literal("<" + PoliciaBoss.NAMES[bossKind] + "> Cuento con vos. Volvé cuando termines."));
            }
        });
    }

    static void reward(ServerPlayer p, Def d) {
        if ("deliver".equals(d.type)) {
            if (PoliciaJefe.count(p, d.target) < d.need) {
                PoliciaMod.msg(p, "Te faltan " + PoliciaJefe.itemName(d.target));
                return;
            }
            PoliciaJefe.take(p, d.target, d.need);
        }
        PoliciaJefe.clear(p);
        p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + d.xp);
        PoliciaBoss.give(p, new ItemStack((ItemLike) Items.EMERALD, d.em));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0f, 1.0f);
        p.sendSystemMessage(Component.literal("<" + PoliciaBoss.NAMES[PoliciaJefe.bossKind(d.job)] + "> Buen trabajo. Acá tenés lo tuyo: +" + d.xp + " XP y " + d.em + " esmeraldas."));
        PoliciaMod.sync(p);
    }

    // Boton "Unirme al oficio" de un jefe, para quien no tiene oficio.
    static void joinFlow(ServerPlayer p, int bossKind) {
        int job = PoliciaBoss.jobIdx(bossKind);
        Def trial = TRIALS[job];
        Def cur = PoliciaJefe.active(p);
        String boss = PoliciaBoss.NAMES[bossKind];
        if (cur != null && cur.id.equals(trial.id)) {
            if (PoliciaJefe.done(p)) {
                PoliciaJefe.clear(p);
                PoliciaOficio.assign(p, PoliciaOficio.JOBS[job], PoliciaOficio.LABEL[job], PoliciaOficio.HINT[job]);
                p.sendSystemMessage(Component.literal("<" + boss + "> Superaste la prueba. Bienvenido al oficio: no me hagas quedar mal."));
            } else {
                p.sendSystemMessage(Component.literal("<" + boss + "> Todavía no terminaste la prueba: " + trial.desc + " (" + PoliciaJefe.prog(p) + "/" + trial.need + ")"));
            }
            return;
        }
        if (cur != null) {
            p.sendSystemMessage(Component.literal("<" + boss + "> Ya estas haciendo otra cosa (" + cur.desc + "). Abandonala en el menú de misiones si querés esta."));
            return;
        }
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; ++i) {
            c.setItem(i, PoliciaBoss.pane());
        }
        c.setItem(13, PoliciaBoss.named(Items.WRITABLE_BOOK, "Prueba de ingreso", trial.desc, "Superala y volvé a hablarme para entrar al oficio."));
        c.setItem(11, PoliciaBoss.named(Items.LIME_DYE, "Aceptar la prueba", new String[0]));
        c.setItem(15, PoliciaBoss.named(Items.RED_DYE, "Ahora no", new String[0]));
        PoliciaBoss.openMenu3(p, boss, c, (pl, slot) -> {
            if (slot == 11) {
                pl.closeContainer();
                if (!PoliciaMod.job(pl).isEmpty() || PoliciaJefe.active(pl) != null) {
                    return;
                }
                PoliciaJefe.start(pl, trial);
                pl.sendSystemMessage(Component.literal("<" + boss + "> Demostrame lo que sabes hacer y volvé a verme."));
            } else if (slot == 15) {
                pl.closeContainer();
            }
        });
    }

    // ---------------- Progreso ----------------

    @SubscribeEvent
    public void kill(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) {
            return;
        }
        Def d = PoliciaJefe.active(p);
        if (d == null) return;
        if ("kill_enemy".equals(d.type) && dead instanceof Enemy) {
            PoliciaJefe.add(p, "kill_enemy", 1);
        } else if ("kill".equals(d.type) && BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString().equals(d.target)) {
            PoliciaJefe.add(p, "kill", 1);
        }
    }

    @SubscribeEvent
    public void place(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            PoliciaJefe.add(p, "place", 1);
        }
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || e.isCanceled()) {
            return;
        }
        Def d = PoliciaJefe.active(p);
        if (d == null) return;
        BlockState st = e.getState();
        if ("mine".equals(d.type) && PoliciaJefe.matchBlock(st, d.target)) {
            PoliciaJefe.add(p, "mine", 1);
        } else if ("logs".equals(d.type) && st.is(BlockTags.LOGS)) {
            PoliciaJefe.add(p, "logs", 1);
        } else if ("dig".equals(d.type) && st.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            PoliciaJefe.add(p, "dig", 1);
        }
    }

    @SubscribeEvent
    public void smelted(PlayerEvent.ItemSmeltedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Def d = PoliciaJefe.active(p);
        if (d == null || !"smelt".equals(d.type)) return;
        ItemStack s = e.getSmelting();
        if (d.target.isEmpty() || BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(d.target)) {
            PoliciaJefe.add(p, "smelt", Math.max(1, s.getCount()));
        }
    }

    @SubscribeEvent
    public void crafted(PlayerEvent.ItemCraftedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Item it = e.getCrafting().getItem();
        boolean iron = it instanceof TieredItem ti && ti.getTier() == Tiers.IRON || it instanceof ArmorItem ai && ai.getMaterial() == ArmorMaterials.IRON;
        if (iron) {
            PoliciaJefe.add(p, "craft_iron", 1);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) {
            return;
        }
        // Habilidades temporales compradas (un solo uso): al terminar el
        // tiempo, desaparecen.
        PoliciaJefe.tmp(p, "pol_tmp_luck", "Suerte temporal");
        PoliciaJefe.tmp(p, "pol_tmp_pick", "Carterismo maestro");
        PoliciaJefe.tmp(p, "pol_tmp_lock", "Ganzúa temporal");
        Def d = PoliciaJefe.active(p);
        if (d == null || !"sneak".equals(d.type)) {
            return;
        }
        CompoundTag t = p.getPersistentData();
        double dx = p.getX() - t.getDouble("jm_lx");
        double dz = p.getZ() - t.getDouble("jm_lz");
        double dist = Math.sqrt(dx * dx + dz * dz);
        t.putDouble("jm_lx", p.getX());
        t.putDouble("jm_lz", p.getZ());
        if (!p.isCrouching() || !p.onGround() || dist > 1.0) {
            return;
        }
        double acc = t.getDouble("jm_acc") + dist;
        if (acc >= 1.0) {
            PoliciaJefe.add(p, "sneak", (int) acc);
            acc -= (int) acc;
        }
        t.putDouble("jm_acc", acc);
    }

    static void tmp(ServerPlayer p, String key, String name) {
        CompoundTag t = p.getPersistentData();
        int v = t.getInt(key);
        if (v <= 0) {
            return;
        }
        t.putInt(key, --v);
        if (v == 0) {
            PoliciaMod.msg(p, name + ": se terminó");
        } else if (v % 200 == 0) {
            PoliciaMod.msg(p, name + ": quedan " + v / 20 + " s");
        }
    }

    // ---------------- Dialogo (abre la ventana de charla en el cliente) ----------------

    // relacion: 0 = es de su oficio, 1 = no tiene oficio, 2 = tiene otro oficio
    static void talk(ServerPlayer p, PoliciaBoss.BossNpc b) {
        int job = PoliciaBoss.jobIdx(b.kind());
        String cur = PoliciaMod.job(p);
        int rel = PoliciaBoss.JOBS[job].equals(cur) ? 0 : (cur.isEmpty() ? 1 : 2);
        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), (Object) new Dialog(b.getId(), job, rel));
    }

    public static class Dialog {
        final int entity;
        final int job;
        final int rel;

        Dialog(int entity, int job, int rel) {
            this.entity = entity;
            this.job = job;
            this.rel = rel;
        }

        static void enc(Dialog m, FriendlyByteBuf b) {
            b.writeVarInt(m.entity);
            b.writeVarInt(m.job);
            b.writeVarInt(m.rel);
        }

        static Dialog dec(FriendlyByteBuf b) {
            return new Dialog(b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        static void handle(Dialog m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> PoliciaDialogo.open(m.entity, m.job, m.rel)));
            c.get().setPacketHandled(true);
        }
    }
}
