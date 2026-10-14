package com.example.policia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

// Habilidad final del Herrero Legendario: el Analizador del Forjador.
// Es pasiva: una vez desbloqueada (ultimo nodo del oficio), funciona sola.
// Sostener un item en la mano secundaria 2 s lo "analiza" y aprendes a
// craftearlo desde la ventana del Analizador (tecla O). Si no lo tenes en
// mano, podes pedir una mision para analizarlo igual.
public class PoliciaAnalizador {
    // 8 tipos de mision genericos, elegidos segun el item a analizar.
    static final String[] QUEST_NAMES = new String[]{
            "Minar menas", "Cazar monstruos", "Caminar", "Sobrevivir sin heridas",
            "Reunir materiales", "Pescar", "Construir", "Nadar",
            "Talar árboles", "Cosechar cultivos", "Criar animales", "Comerciar con aldeanos",
            "Fundir en el horno", "Craftear objetos", "Domesticar animales", "Escalar las alturas",
            "Bajar a las profundidades", "Comer", "Dormir una noche", "Puntería con arco", "Saltar"
    };
    static final int[] QUEST_NEED = new int[]{12, 8, 500, 1200, 10, 5, 20, 600,
            16, 12, 3, 3, 16, 10, 2, 1, 1, 10, 1, 5, 100};
    static final Block[] QUEST_ORES = new Block[]{
            net.minecraft.world.level.block.Blocks.IRON_ORE, net.minecraft.world.level.block.Blocks.GOLD_ORE,
            net.minecraft.world.level.block.Blocks.DIAMOND_ORE, net.minecraft.world.level.block.Blocks.COPPER_ORE,
            net.minecraft.world.level.block.Blocks.REDSTONE_ORE, net.minecraft.world.level.block.Blocks.LAPIS_ORE
    };
    @SuppressWarnings("unchecked")
    static final EntityType<? extends Mob>[] QUEST_MOBS = new EntityType[]{
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.ENDERMAN
    };
    static final Item[] POOL = new Item[]{
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.STICK, Items.STRING,
            Items.LEATHER, Items.COAL, Items.REDSTONE, Items.QUARTZ, Items.FLINT, Items.CLAY_BALL,
            Items.GLASS, Items.WHITE_WOOL, Items.FEATHER, Items.BONE, Items.GUNPOWDER, Items.SLIME_BALL,
            Items.DIAMOND, Items.EMERALD, Items.LAPIS_LAZULI, Items.NETHERITE_SCRAP
    };

    static void init() {
        PoliciaMod.NET.registerMessage(6, State.class, State::enc, State::dec, State::handle);
        PoliciaMod.NET.registerMessage(7, Act.class, Act::enc, Act::dec, Act::handle);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register((Object) new PoliciaAnalizador());
    }

    static boolean active(ServerPlayer p) {
        return PoliciaHerrero.isHerrero(p) && PoliciaMod.xp(p) >= PoliciaHerrero.ANALIZADOR_XP;
    }

    static class Recipe {
        List<String> items = new ArrayList<String>();
        List<Integer> counts = new ArrayList<Integer>();
        boolean confirmed;
    }

    public static class Recipes extends SavedData {
        final Map<String, Recipe> map = new HashMap<String, Recipe>();

        static Recipes get(ServerLevel sl) {
            return (Recipes) sl.getDataStorage().computeIfAbsent(Recipes::load, Recipes::new, "policia_analizador");
        }

        static Recipes load(CompoundTag t) {
            Recipes r = new Recipes();
            for (Tag tg : t.getList("items", 10)) {
                CompoundTag e = (CompoundTag) tg;
                Recipe rc = new Recipe();
                rc.confirmed = e.getBoolean("c");
                for (Tag it : e.getList("i", 10)) {
                    CompoundTag ie = (CompoundTag) it;
                    rc.items.add(ie.getString("id"));
                    rc.counts.add(ie.getInt("n"));
                }
                r.map.put(e.getString("item"), rc);
            }
            return r;
        }

        public CompoundTag save(CompoundTag t) {
            ListTag l = new ListTag();
            for (Map.Entry<String, Recipe> en : this.map.entrySet()) {
                CompoundTag e = new CompoundTag();
                e.putString("item", en.getKey());
                e.putBoolean("c", en.getValue().confirmed);
                ListTag il = new ListTag();
                for (int i = 0; i < en.getValue().items.size(); ++i) {
                    CompoundTag ie = new CompoundTag();
                    ie.putString("id", en.getValue().items.get(i));
                    ie.putInt("n", en.getValue().counts.get(i));
                    il.add((Tag) ie);
                }
                e.put("i", (Tag) il);
                l.add((Tag) e);
            }
            t.put("items", (Tag) l);
            return t;
        }
    }

    static Recipe roll(String itemId) {
        Random r = new Random(itemId.hashCode());
        Recipe rc = new Recipe();
        int n = 2 + r.nextInt(3);
        for (int i = 0; i < n; ++i) {
            Item it = POOL[r.nextInt(POOL.length)];
            String id = BuiltInRegistries.ITEM.getKey(it).toString();
            if (rc.items.contains(id)) continue;
            rc.items.add(id);
            rc.counts.add(1 + r.nextInt(4));
        }
        return rc;
    }

    static Recipe reroll(String itemId, long seed) {
        Random r = new Random(itemId.hashCode() ^ seed);
        Recipe rc = new Recipe();
        int n = 2 + r.nextInt(3);
        for (int i = 0; i < n; ++i) {
            Item it = POOL[r.nextInt(POOL.length)];
            String id = BuiltInRegistries.ITEM.getKey(it).toString();
            if (rc.items.contains(id)) continue;
            rc.items.add(id);
            rc.counts.add(1 + r.nextInt(4));
        }
        return rc;
    }

    static void title(ServerPlayer p, String text) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(text).withStyle(s -> s.withColor(0xFFD700))));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    static boolean known(ServerPlayer p, String item) {
        for (Tag t : p.getPersistentData().getList("her_an_known", 8)) {
            if (((StringTag) t).getAsString().equals(item)) return true;
        }
        return false;
    }

    static void addKnown(ServerPlayer p, String item) {
        ListTag l = p.getPersistentData().getList("her_an_known", 8);
        l.add((Tag) StringTag.valueOf(item));
        p.getPersistentData().put("her_an_known", (Tag) l);
    }

    static void learn(ServerPlayer p, String itemId) {
        if (PoliciaAnalizador.known(p, itemId)) {
            return;
        }
        Recipes recipes = Recipes.get(p.serverLevel());
        Recipe rc = recipes.map.get(itemId);
        if (rc == null) {
            rc = PoliciaAnalizador.roll(itemId);
            recipes.map.put(itemId, rc);
            recipes.setDirty();
        }
        PoliciaAnalizador.addKnown(p, itemId);
        PoliciaJefe.add(p, "analyze", 1);
        Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
        PoliciaAnalizador.title(p, "Analizaste: " + new ItemStack(it).getHoverName().getString());
        PoliciaAnalizador.send(p);
    }

    // Mision que corresponde a cada item: siempre la misma para el mismo item
    // (el cliente la usa para mostrarla antes de pedirla). Devuelve
    // {tipo, objetivo} donde objetivo es una mena, un mob o un material.
    static String[] questFor(String itemId) {
        int type = Math.abs(itemId.hashCode()) % QUEST_NAMES.length;
        Random r = new Random(itemId.hashCode());
        String target = "";
        if (type == 0) {
            target = BuiltInRegistries.BLOCK.getKey(QUEST_ORES[r.nextInt(QUEST_ORES.length)]).toString();
        } else if (type == 1) {
            target = BuiltInRegistries.ENTITY_TYPE.getKey(QUEST_MOBS[r.nextInt(QUEST_MOBS.length)]).toString();
        } else if (type == 4) {
            target = BuiltInRegistries.ITEM.getKey(POOL[r.nextInt(POOL.length)]).toString();
        }
        return new String[]{String.valueOf(type), target};
    }

    static void startQuest(ServerPlayer p, String itemId) {
        CompoundTag d = p.getPersistentData();
        if (PoliciaAnalizador.known(p, itemId)) {
            PoliciaMod.msg(p, "Ese item ya está analizado");
            return;
        }
        String cur = d.getString("her_an_qitem");
        if (!cur.isEmpty()) {
            PoliciaMod.msg(p, "Ya tenés una misión de análisis en curso: cancelala en el Analizador (O) para pedir otra");
            return;
        }
        String[] q = PoliciaAnalizador.questFor(itemId);
        int type = Integer.parseInt(q[0]);
        d.putString("her_an_qitem", itemId);
        d.putInt("her_an_qtype", type);
        d.putInt("her_an_qprog", 0);
        d.putInt("her_an_qneed", QUEST_NEED[type]);
        d.putString("her_an_qtarget", q[1]);
        d.putInt("her_an_qhp", (int) p.getHealth());
        d.putDouble("her_an_qx", p.getX());
        d.putDouble("her_an_qz", p.getZ());
        PoliciaMod.msg(p, "Misión de análisis: " + QUEST_NAMES[type] + " (" + QUEST_NEED[type] + ")");
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
        PoliciaAnalizador.send(p);
    }

    static void cancelQuest(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (d.getString("her_an_qitem").isEmpty()) {
            return;
        }
        d.putString("her_an_qitem", "");
        d.putInt("her_an_qprog", 0);
        PoliciaMod.msg(p, "Misión de análisis cancelada");
        PoliciaAnalizador.send(p);
    }

    static void questProgress(ServerPlayer p, int amount) {
        CompoundTag d = p.getPersistentData();
        String item = d.getString("her_an_qitem");
        if (item.isEmpty()) {
            return;
        }
        int prog = Math.min(d.getInt("her_an_qneed"), d.getInt("her_an_qprog") + amount);
        d.putInt("her_an_qprog", prog);
        if (prog >= d.getInt("her_an_qneed")) {
            if (d.getInt("her_an_qtype") == 4) {
                PoliciaAnalizador.remove(p.getInventory(), d.getString("her_an_qtarget"), d.getInt("her_an_qneed"));
            }
            d.putString("her_an_qitem", "");
            PoliciaAnalizador.learn(p, item);
            PoliciaMod.msg(p, "Misión de análisis completa!");
        }
        PoliciaAnalizador.send(p);
    }

    // Igual que questProgress pero solo avisa al cliente cada 20 pasos, para
    // las misiones que avanzan cada tick.
    static void questStep(ServerPlayer p, int amount) {
        CompoundTag d = p.getPersistentData();
        int before = d.getInt("her_an_qprog");
        int need = d.getInt("her_an_qneed");
        int prog = Math.min(need, before + amount);
        if (prog >= need || prog / 20 != before / 20) {
            d.putInt("her_an_qprog", before);
            PoliciaAnalizador.questProgress(p, amount);
        } else {
            d.putInt("her_an_qprog", prog);
        }
    }

    static void act(ServerPlayer p, int kind, String item) {
        if (!PoliciaAnalizador.active(p)) {
            if (kind != 4) {
                PoliciaMod.msg(p, "El Analizador del Forjador se desbloquea con " + PoliciaHerrero.ANALIZADOR_XP + " XP siendo herrero");
            }
            PoliciaAnalizador.send(p);
            return;
        }
        CompoundTag d = p.getPersistentData();
        Recipes recipes = Recipes.get(p.serverLevel());
        if (kind == 4) {
            PoliciaAnalizador.send(p);
            return;
        }
        if (kind == 0) {
            PoliciaAnalizador.startQuest(p, item);
            return;
        }
        if (kind == 5) {
            PoliciaAnalizador.cancelQuest(p);
            return;
        }
        if (kind == 1) {
            Recipe rc = recipes.map.get(item);
            if (rc != null && !rc.confirmed && PoliciaAnalizador.known(p, item)) {
                recipes.map.put(item, PoliciaAnalizador.reroll(item, p.getRandom().nextLong()));
                recipes.setDirty();
                PoliciaAnalizador.send(p);
            }
            return;
        }
        if (kind == 2) {
            Recipe rc = recipes.map.get(item);
            if (rc != null && PoliciaAnalizador.known(p, item)) {
                rc.confirmed = true;
                recipes.setDirty();
                PoliciaMod.msg(p, "Receta confirmada");
                PoliciaAnalizador.send(p);
            }
            return;
        }
        if (kind == 3) {
            Recipe rc = recipes.map.get(item);
            if (rc == null || !PoliciaAnalizador.known(p, item)) {
                return;
            }
            Inventory inv = p.getInventory();
            for (int i = 0; i < rc.items.size(); ++i) {
                if (PoliciaAnalizador.count(inv, rc.items.get(i)) >= rc.counts.get(i)) continue;
                PoliciaMod.msg(p, "Te faltan materiales para craftearlo");
                return;
            }
            for (int i = 0; i < rc.items.size(); ++i) {
                PoliciaAnalizador.remove(inv, rc.items.get(i), rc.counts.get(i));
            }
            Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(item));
            ItemStack out = new ItemStack(it);
            if (!inv.add(out)) {
                p.drop(out, false);
            }
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.1f);
            PoliciaMod.msg(p, "Crafteado: " + out.getHoverName().getString());
            PoliciaAnalizador.send(p);
        }
    }

    static int count(Inventory inv, String id) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); ++i) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id)) continue;
            n += s.getCount();
        }
        return n;
    }

    static void remove(Inventory inv, String id, int amount) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; ++i) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id)) continue;
            int take = Math.min(amount, s.getCount());
            s.shrink(take);
            amount -= take;
        }
    }

    static void send(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        Recipes recipes = Recipes.get(p.serverLevel());
        State m = new State();
        m.known = new ArrayList<String>();
        for (Tag t : d.getList("her_an_known", 8)) {
            m.known.add(((StringTag) t).getAsString());
        }
        m.confirmed = new ArrayList<String>();
        m.recipeBlob = new StringBuilder();
        for (String it : m.known) {
            Recipe rc = recipes.map.get(it);
            if (rc == null) continue;
            if (rc.confirmed) {
                m.confirmed.add(it);
            }
            if (m.recipeBlob.length() > 0) {
                m.recipeBlob.append(';');
            }
            m.recipeBlob.append(it).append(':').append(rc.confirmed ? 1 : 0);
            for (int i = 0; i < rc.items.size(); ++i) {
                m.recipeBlob.append(',').append(rc.items.get(i)).append('x').append(rc.counts.get(i));
            }
        }
        m.questItem = d.getString("her_an_qitem");
        m.questType = d.getInt("her_an_qtype");
        m.questProg = d.getInt("her_an_qprog");
        m.questNeed = d.getInt("her_an_qneed");
        m.questTarget = d.getString("her_an_qtarget");
        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), (Object) m);
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        Player player;
        if (e.phase != TickEvent.Phase.END || !((player = e.player) instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer p = (ServerPlayer) player;
        if (!PoliciaAnalizador.active(p)) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        ItemStack off = p.getOffhandItem();
        if (off.isEmpty()) {
            d.putInt("her_an_off", 0);
        } else {
            String id = BuiltInRegistries.ITEM.getKey(off.getItem()).toString();
            if (id.equals(d.getString("her_an_offi")) ) {
                int t = d.getInt("her_an_off") + 1;
                d.putInt("her_an_off", t);
                if (t == 40) {
                    PoliciaAnalizador.learn(p, id);
                }
            } else {
                d.putString("her_an_offi", id);
                d.putInt("her_an_off", 1);
            }
        }
        String qi = d.getString("her_an_qitem");
        if (!qi.isEmpty()) {
            int type = d.getInt("her_an_qtype");
            if (type == 2) {
                double dx = p.getX() - d.getDouble("her_an_qx");
                double dz = p.getZ() - d.getDouble("her_an_qz");
                double dist = Math.sqrt(dx * dx + dz * dz);
                // Cuenta bloques enteros recorridos; el resto queda para
                // el siguiente tick (asi no se pierde distancia ni se
                // completa de golpe si el punto de partida era 0,0).
                if (dist >= 1.0 && dist < 50.0) {
                    PoliciaAnalizador.questStep(p, (int) dist);
                    d.putDouble("her_an_qx", p.getX());
                    d.putDouble("her_an_qz", p.getZ());
                } else if (dist >= 50.0) {
                    d.putDouble("her_an_qx", p.getX());
                    d.putDouble("her_an_qz", p.getZ());
                }
            } else if (type == 3) {
                if (p.getHealth() < d.getInt("her_an_qhp")) {
                    d.putInt("her_an_qprog", 0);
                }
                d.putInt("her_an_qhp", (int) p.getHealth());
                PoliciaAnalizador.questStep(p, 1);
            } else if (type == 7) {
                if (p.isUnderWater()) {
                    PoliciaAnalizador.questStep(p, 1);
                }
            } else if (type == 15 && p.getY() >= 120.0 || type == 16 && p.getY() <= -30.0) {
                PoliciaAnalizador.questProgress(p, 1);
            } else if (type == 4 && p.tickCount % 10 == 0) {
                int have = PoliciaAnalizador.count(p.getInventory(), d.getString("her_an_qtarget"));
                int need = d.getInt("her_an_qneed");
                if (Math.min(need, have) != d.getInt("her_an_qprog")) {
                    d.putInt("her_an_qprog", Math.min(need, have));
                    PoliciaAnalizador.questProgress(p, 0);
                }
            }
        }
    }

    // Jugador con mision de analisis en curso del tipo dado (o null).
    static ServerPlayer quester(Object ent, int type) {
        if (!(ent instanceof ServerPlayer p) || !PoliciaAnalizador.active(p)) {
            return null;
        }
        CompoundTag d = p.getPersistentData();
        if (d.getString("her_an_qitem").isEmpty() || d.getInt("her_an_qtype") != type) {
            return null;
        }
        return p;
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent e) {
        ServerPlayer p = PoliciaAnalizador.quester(e.getPlayer(), 0);
        if (p != null && BuiltInRegistries.BLOCK.getKey(e.getState().getBlock()).toString().equals(p.getPersistentData().getString("her_an_qtarget"))) {
            PoliciaAnalizador.questProgress(p, 1);
        }
        p = PoliciaAnalizador.quester(e.getPlayer(), 8);
        if (p != null && e.getState().is(net.minecraft.tags.BlockTags.LOGS)) {
            PoliciaAnalizador.questProgress(p, 1);
        }
        p = PoliciaAnalizador.quester(e.getPlayer(), 9);
        if (p != null && e.getState().getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(e.getState())) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    @SubscribeEvent
    public void breed(net.minecraftforge.event.entity.living.BabyEntitySpawnEvent e) {
        ServerPlayer p = PoliciaAnalizador.quester(e.getCausedByPlayer(), 10);
        if (p != null) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    @SubscribeEvent
    public void trade(net.minecraftforge.event.entity.player.TradeWithVillagerEvent e) {
        ServerPlayer p = PoliciaAnalizador.quester(e.getEntity(), 11);
        if (p != null) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    @SubscribeEvent
    public void smelted(net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent e) {
        ServerPlayer p = PoliciaAnalizador.quester(e.getEntity(), 12);
        if (p != null) {
            PoliciaAnalizador.questProgress(p, Math.max(1, e.getSmelting().getCount()));
        }
    }

    @SubscribeEvent
    public void crafted(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent e) {
        ServerPlayer p = PoliciaAnalizador.quester(e.getEntity(), 13);
        if (p != null) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    @SubscribeEvent
    public void tamed(net.minecraftforge.event.entity.living.AnimalTameEvent e) {
        ServerPlayer p = PoliciaAnalizador.quester(e.getTamer(), 14);
        if (p != null) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    @SubscribeEvent
    public void ate(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish e) {
        ServerPlayer p = PoliciaAnalizador.quester(e.getEntity(), 17);
        if (p != null && e.getItem().isEdible()) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    @SubscribeEvent
    public void slept(net.minecraftforge.event.entity.player.PlayerWakeUpEvent e) {
        ServerPlayer p = PoliciaAnalizador.quester(e.getEntity(), 18);
        if (p != null && !e.wakeImmediately()) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    @SubscribeEvent
    public void jumped(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent e) {
        ServerPlayer p = PoliciaAnalizador.quester(e.getEntity(), 20);
        if (p != null) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    @SubscribeEvent
    public void place(BlockEvent.EntityPlaceEvent e) {
        ServerPlayer p;
        if (!(e.getEntity() instanceof ServerPlayer) || !PoliciaAnalizador.active(p = (ServerPlayer) e.getEntity())) {
            return;
        }
        if (p.getPersistentData().getInt("her_an_qtype") == 6 && !p.getPersistentData().getString("her_an_qitem").isEmpty()) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    @SubscribeEvent
    public void fished(ItemFishedEvent e) {
        ServerPlayer p;
        if (!(e.getEntity() instanceof ServerPlayer) || !PoliciaAnalizador.active(p = (ServerPlayer) e.getEntity())) {
            return;
        }
        if (p.getPersistentData().getInt("her_an_qtype") == 5 && !p.getPersistentData().getString("her_an_qitem").isEmpty()) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    @SubscribeEvent
    public void kill(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Object killer = e.getSource().getEntity() != null ? e.getSource().getEntity() : dead.getLastHurtByMob();
        ServerPlayer p = PoliciaAnalizador.quester(killer, 1);
        if (p != null && BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString().equals(p.getPersistentData().getString("her_an_qtarget"))) {
            PoliciaAnalizador.questProgress(p, 1);
        }
        p = PoliciaAnalizador.quester(killer, 19);
        if (p != null && dead instanceof Mob && e.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow) {
            PoliciaAnalizador.questProgress(p, 1);
        }
    }

    public static class State {
        List<String> known = new ArrayList<String>();
        List<String> confirmed = new ArrayList<String>();
        StringBuilder recipeBlob = new StringBuilder();
        String questItem = "";
        int questType;
        int questProg;
        int questNeed;
        String questTarget = "";

        static void enc(State m, FriendlyByteBuf b) {
            b.writeInt(m.known.size());
            for (String s : m.known) {
                b.writeUtf(s);
            }
            b.writeUtf(m.recipeBlob.toString());
            b.writeUtf(m.questItem);
            b.writeVarInt(m.questType);
            b.writeVarInt(m.questProg);
            b.writeVarInt(m.questNeed);
            b.writeUtf(m.questTarget);
        }

        static State dec(FriendlyByteBuf b) {
            State m = new State();
            int n = b.readInt();
            for (int i = 0; i < n; ++i) {
                m.known.add(b.readUtf());
            }
            m.recipeBlob = new StringBuilder(b.readUtf());
            for (String entry : m.recipeBlob.toString().split(";")) {
                String head = entry.split(",")[0];
                int c = head.lastIndexOf(':');
                if (c > 0 && head.substring(c + 1).equals("1")) {
                    m.confirmed.add(head.substring(0, c));
                }
            }
            m.questItem = b.readUtf();
            m.questType = b.readVarInt();
            m.questProg = b.readVarInt();
            m.questNeed = b.readVarInt();
            m.questTarget = b.readUtf();
            return m;
        }

        static void handle(State m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> PoliciaAnalizadorScreen.onState(m)));
            c.get().setPacketHandled(true);
        }
    }

    public static class Act {
        final int kind;
        final String item;

        Act(int kind, String item) {
            this.kind = kind;
            this.item = item;
        }

        static void enc(Act m, FriendlyByteBuf b) {
            b.writeVarInt(m.kind);
            b.writeUtf(m.item);
        }

        static Act dec(FriendlyByteBuf b) {
            return new Act(b.readVarInt(), b.readUtf());
        }

        static void handle(Act m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p != null) {
                    PoliciaAnalizador.act(p, m.kind, m.item);
                }
            });
            c.get().setPacketHandled(true);
        }
    }
}
