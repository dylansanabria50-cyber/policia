package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 5: misiones diarias por oficio. Cada dia de juego se sortean 3 del
// grupo de tu oficio. Cada una da XP y esmeraldas; las 3 juntas dan el
// cofre del dia y suman racha (dias seguidos). Se ven en el HUD y en el
// diario de misiones (ficha, tecla I).
public class PoliciaDiarias {
    // tipo: monstruo, mob:<id>, mena, tronco, piedra, poner, poner:<id>,
    //       habilidad, habilidad:<anim>, fundir:<item>, craftear:<texto>
    public static class Def {
        final String id;
        final String tipo;
        final int meta;
        final String texto;

        Def(String id, String tipo, int meta, String texto) {
            this.id = id;
            this.tipo = tipo;
            this.meta = meta;
            this.texto = texto;
        }
    }

    static final Def[] POLICIA = {
            new Def("p1", "monstruo", 15, "Derrotá 15 monstruos"),
            new Def("p2", "habilidad", 6, "Usá 6 habilidades de policía"),
            new Def("p3", "habilidad:pol_esposas", 2, "Esposá a 2 sospechosos"),
            new Def("p4", "mob:minecraft:skeleton", 6, "Derrotá 6 esqueletos"),
            new Def("p5", "mob:policia:bandido", 3, "Detené a 3 bandidos"),
            new Def("p6", "mob:minecraft:zombie", 10, "Derrotá 10 zombis"),
            new Def("p7", "habilidad:pol_k9", 2, "Salí 2 veces con el K9")};
    static final Def[] ALBANIL = {
            new Def("a1", "poner", 200, "Colocá 200 bloques"),
            new Def("a2", "piedra", 150, "Picá 150 bloques de piedra"),
            new Def("a3", "habilidad", 6, "Usá 6 habilidades de albañil"),
            new Def("a4", "poner:minecraft:torch", 24, "Iluminá con 24 antorchas"),
            new Def("a5", "tronco", 40, "Talá 40 troncos"),
            new Def("a6", "habilidad:alb_muro", 4, "Levantá 4 muros o refugios"),
            new Def("a7", "mena", 15, "Picá 15 menas")};
    static final Def[] LADRON = {
            new Def("l1", "habilidad", 6, "Usá 6 habilidades de ladrón"),
            new Def("l2", "habilidad:lad_escape", 2, "Escapá 2 veces"),
            new Def("l3", "habilidad:lad_humo", 3, "Usá la bomba de humo 3 veces"),
            new Def("l4", "monstruo", 10, "Derrotá 10 monstruos sin ser visto"),
            new Def("l5", "mena", 12, "Robale a la montaña 12 menas"),
            new Def("l6", "mob:minecraft:spider", 5, "Derrotá 5 arañas")};
    static final Def[] HERRERO = {
            new Def("h1", "fundir:minecraft:iron_ingot", 32, "Fundí 32 lingotes de hierro"),
            new Def("h2", "craftear:iron_", 3, "Fabricá 3 objetos de hierro"),
            new Def("h3", "habilidad", 5, "Usá 5 habilidades de herrero"),
            new Def("h4", "mena", 20, "Picá 20 menas"),
            new Def("h5", "fundir:minecraft:gold_ingot", 12, "Fundí 12 lingotes de oro"),
            new Def("h6", "craftear:diamond_", 1, "Fabricá un objeto de diamante")};
    static final Def[] MEDICO = {
            new Def("m1", "habilidad", 6, "Usá 6 habilidades de médico"),
            new Def("m2", "habilidad:med_vendaje", 4, "Vendá a 4 heridos"),
            new Def("m3", "monstruo", 8, "Protegé el pueblo: derrotá 8 monstruos"),
            new Def("m4", "habilidad:med_mortero", 2, "Prepará 2 pociones en el mortero"),
            new Def("m5", "tronco", 16, "Juntá leña para el hospital: 16 troncos")};
    static final Def[] MERCADER = {
            new Def("c1", "habilidad", 6, "Usá 6 habilidades de mercader"),
            new Def("c2", "habilidad:mer_balanza", 5, "Tasá 5 objetos con la balanza"),
            new Def("c3", "habilidad:mer_regateo", 2, "Regateá con 2 aldeanos"),
            new Def("c4", "mena", 10, "Conseguí mercadería: picá 10 menas"),
            new Def("c5", "monstruo", 8, "Cuidá la ruta: derrotá 8 monstruos")};
    static final Def[] GENERAL = {
            new Def("g1", "monstruo", 10, "Derrotá 10 monstruos"),
            new Def("g2", "mena", 10, "Picá 10 menas"),
            new Def("g3", "tronco", 24, "Talá 24 troncos"),
            new Def("g4", "poner", 64, "Colocá 64 bloques"),
            new Def("g5", "piedra", 64, "Picá 64 bloques de piedra")};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Eventos());
    }

    static Def[] grupo(String oficio) {
        return switch (oficio) {
            case "policia" -> POLICIA;
            case "albanil" -> ALBANIL;
            case "ladron" -> LADRON;
            case "herrero" -> HERRERO;
            case "medico" -> MEDICO;
            case "mercader" -> MERCADER;
            default -> GENERAL;
        };
    }

    static Def buscar(String id) {
        for (Def[] g : new Def[][]{POLICIA, ALBANIL, LADRON, HERRERO, MEDICO, MERCADER, GENERAL}) {
            for (Def d : g) {
                if (d.id.equals(id)) {
                    return d;
                }
            }
        }
        return null;
    }

    static long dia(ServerPlayer p) {
        return p.server.overworld().getDayTime() / 24000L;
    }

    static CompoundTag datos(ServerPlayer p) {
        CompoundTag r = PoliciaRPG.datos(p);
        if (!r.contains("diarias")) {
            r.put("diarias", new CompoundTag());
        }
        return r.getCompound("diarias");
    }

    // Sortea las del dia si cambio el dia o el oficio.
    static void revisar(ServerPlayer p) {
        CompoundTag d = datos(p);
        long hoy = dia(p);
        String oficio = PoliciaMod.job(p);
        if (d.contains("dia") && d.getLong("dia") == hoy && oficio.equals(d.getString("oficio"))) {
            return;
        }
        if (d.contains("dia") && d.getLong("dia") != hoy) {
            boolean seguida = d.getBoolean("completas") && d.getLong("dia") == hoy - 1;
            d.putInt("racha", seguida ? d.getInt("racha") : 0);
        }
        Def[] g = grupo(oficio);
        List<Def> lista = new ArrayList<Def>(List.of(g));
        Random r = new Random(hoy * 31L + p.getUUID().getLeastSignificantBits());
        for (int i = 0; i < 3; i++) {
            Def def = lista.remove(r.nextInt(lista.size()));
            CompoundTag t = new CompoundTag();
            t.putString("id", def.id);
            t.putInt("prog", 0);
            t.putBoolean("hecha", false);
            d.put("t" + i, t);
        }
        d.putLong("dia", hoy);
        d.putString("oficio", oficio);
        d.putBoolean("completas", false);
        PoliciaRPG.sync(p);
    }

    // Suma progreso a las tareas que cumplan "test".
    static void avanzar(ServerPlayer p, java.util.function.Predicate<String> test, int n) {
        CompoundTag d = datos(p);
        if (!d.contains("dia")) {
            return;
        }
        boolean cambio = false;
        for (int i = 0; i < 3; i++) {
            CompoundTag t = d.getCompound("t" + i);
            Def def = buscar(t.getString("id"));
            if (def == null || t.getBoolean("hecha") || !test.test(def.tipo)) {
                continue;
            }
            int prog = Math.min(def.meta, t.getInt("prog") + n);
            t.putInt("prog", prog);
            cambio = true;
            if (prog >= def.meta) {
                t.putBoolean("hecha", true);
                p.displayClientMessage(Component.literal("§a✔ Misión diaria cumplida: §f" + def.texto + " §7(+40 XP, +3 esmeraldas)"), false);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
                dar(p, new ItemStack(Items.EMERALD, 3));
                PoliciaNivel.dar(p, 40);
            }
        }
        if (!cambio) {
            return;
        }
        if (!d.getBoolean("completas") && d.getCompound("t0").getBoolean("hecha") && d.getCompound("t1").getBoolean("hecha") && d.getCompound("t2").getBoolean("hecha")) {
            d.putBoolean("completas", true);
            int racha = d.getInt("racha") + 1;
            d.putInt("racha", racha);
            cofre(p, racha);
        }
        PoliciaRPG.sync(p);
    }

    static void dar(ServerPlayer p, ItemStack st) {
        if (!p.getInventory().add(st)) {
            p.drop(st, false);
        }
    }

    // Cofre del dia: esmeraldas, un premio al azar y XP segun la racha.
    static void cofre(ServerPlayer p, int racha) {
        ItemStack[] premios = {new ItemStack(Items.GOLDEN_APPLE, 2), new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.EXPERIENCE_BOTTLE, 8), new ItemStack(Items.IRON_INGOT, 12), new ItemStack(Items.ENDER_PEARL, 4)};
        ItemStack premio = premios[p.getRandom().nextInt(premios.length)].copy();
        // Ley "fiesta" del alcalde: 5 esmeraldas mas.
        int esm = "fiesta".equals(PoliciaAlcalde.ley(p.server)) ? 15 : 10;
        dar(p, new ItemStack(Items.EMERALD, esm));
        dar(p, premio);
        int xp = 60 + 20 * Math.min(7, racha);
        PoliciaNivel.dar(p, xp);
        p.displayClientMessage(Component.literal("§6§l¡Cofre del día! §e+" + esm + " esmeraldas, " + premio.getCount() + " " + premio.getHoverName().getString() + " y " + xp + " XP §7(racha: " + racha + (racha == 1 ? " día)" : " días)")), false);
        p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.6, 0.8, 0.6, 0.1);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.8f, 1.2f);
        PoliciaNivel.logro(p, "diarias_1");
        if (racha >= 7) {
            PoliciaNivel.logro(p, "diarias_7");
        }
    }

    // La llama PoliciaNivel.habilidad.
    static void habilidad(ServerPlayer p, String anim) {
        avanzar(p, t -> t.equals("habilidad") || t.equals("habilidad:" + anim), 1);
    }

    static String id(ResourceLocation rl) {
        return rl == null ? "" : rl.toString();
    }

    public static class Eventos {
        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p && p.tickCount % 100 == 13) {
                revisar(p);
            }
        }

        @SubscribeEvent
        public void matar(LivingDeathEvent e) {
            if (e.getSource().getEntity() instanceof ServerPlayer p) {
                LivingEntity v = e.getEntity();
                String mob = id(BuiltInRegistries.ENTITY_TYPE.getKey(v.getType()));
                boolean mon = v instanceof Enemy;
                avanzar(p, t -> (mon && t.equals("monstruo")) || t.equals("mob:" + mob), 1);
            }
        }

        @SubscribeEvent
        public void romper(BlockEvent.BreakEvent e) {
            if (!(e.getPlayer() instanceof ServerPlayer p) || p.isCreative()) {
                return;
            }
            BlockState st = e.getState();
            boolean mena = st.is(Tags.Blocks.ORES);
            boolean tronco = st.is(BlockTags.LOGS);
            boolean piedra = st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(Tags.Blocks.STONE);
            if (mena || tronco || piedra) {
                avanzar(p, t -> (mena && t.equals("mena")) || (tronco && t.equals("tronco")) || (piedra && t.equals("piedra")), 1);
            }
        }

        @SubscribeEvent
        public void poner(BlockEvent.EntityPlaceEvent e) {
            if (e.getEntity() instanceof ServerPlayer p && !p.isCreative()) {
                String b = id(BuiltInRegistries.BLOCK.getKey(e.getPlacedBlock().getBlock()));
                avanzar(p, t -> t.equals("poner") || t.equals("poner:" + b), 1);
            }
        }

        @SubscribeEvent
        public void fundir(PlayerEvent.ItemSmeltedEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                String it = id(BuiltInRegistries.ITEM.getKey(e.getSmelting().getItem()));
                avanzar(p, t -> t.equals("fundir:" + it), e.getSmelting().getCount());
            }
        }

        @SubscribeEvent
        public void craftear(PlayerEvent.ItemCraftedEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) {
                String it = BuiltInRegistries.ITEM.getKey(e.getCrafting().getItem()).getPath();
                boolean equipo = it.endsWith("_sword") || it.endsWith("_pickaxe") || it.endsWith("_axe") || it.endsWith("_shovel") || it.endsWith("_hoe") || it.endsWith("_helmet") || it.endsWith("_chestplate") || it.endsWith("_leggings") || it.endsWith("_boots");
                if (equipo) {
                    avanzar(p, t -> t.startsWith("craftear:") && it.startsWith(t.substring(9)), 1);
                }
            }
        }
    }
}
