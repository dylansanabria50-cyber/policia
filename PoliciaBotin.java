package com.example.policia;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

// Botin RPG:
// - Afijos: estadisticas al azar en armas y armaduras (pol_afijos):
//   vida, critico, robo, velocidad, xp, fuego, armadura, energia.
// - Accesorios: amuleto, capa y cinturon (casilleros en la ficha, tecla I),
//   ademas de los dos anillos. Cada uno trae sus estadisticas fijas.
// - Gemas: se engarzan en las ranuras de un objeto (1 a 3 segun la
//   rareza): con la gema en una mano y el objeto en la otra, clic derecho.
// - Conjuntos: piezas de armadura "del Cazador" y "del Coloso"; con 2 y
//   con 4 piezas puestas dan bonos extra.
// - Botin por zona: los monstruos de zonas de mas nivel pueden soltar
//   equipo con rareza y afijos (mejor cuanto mas alta la zona), gemas y
//   piezas de conjunto. Las menas sueltan gemas de vez en cuando.
public class PoliciaBotin {
    static final String[] AFIJOS = {"vida", "critico", "robo", "velocidad", "xp", "fuego", "armadura", "energia"};

    // ---------------- Objetos nuevos ----------------
    public static final RegistryObject<Item> AMULETO_VIDA = PoliciaShield.ITEMS.register("amuleto_vida", () -> new Accesorio("amuleto", "vida:4"));
    public static final RegistryObject<Item> AMULETO_SABIO = PoliciaShield.ITEMS.register("amuleto_sabio", () -> new Accesorio("amuleto", "xp:15,energia:25"));
    public static final RegistryObject<Item> AMULETO_GUERRA = PoliciaShield.ITEMS.register("amuleto_guerra", () -> new Accesorio("amuleto", "critico:6,robo:2"));
    public static final RegistryObject<Item> CAPA_VIAJERO = PoliciaShield.ITEMS.register("capa_viajero", () -> new Accesorio("capa", "velocidad:10"));
    public static final RegistryObject<Item> CAPA_GUARDIAN = PoliciaShield.ITEMS.register("capa_guardian", () -> new Accesorio("capa", "armadura:3,vida:2"));
    public static final RegistryObject<Item> CAPA_SOMBRAS = PoliciaShield.ITEMS.register("capa_sombras", () -> new Accesorio("capa", "energia:20,velocidad:5"));
    public static final RegistryObject<Item> CINTURON_CUERO = PoliciaShield.ITEMS.register("cinturon_cuero", () -> new Accesorio("cinturon", "armadura:2,vida:2"));
    public static final RegistryObject<Item> CINTURON_ALQUIMISTA = PoliciaShield.ITEMS.register("cinturon_alquimista", () -> new Accesorio("cinturon", "energia:30,xp:5"));

    static final String[] GEMAS = {"rubi", "zafiro", "ambar", "amatista", "onix", "topacio"};
    static final String[] GEMA_NOMBRE = {"Rubí", "Zafiro", "Ámbar", "Amatista", "Ónix", "Topacio"};
    static final String[] GEMA_AFIJO = {"vida:2", "critico:3", "velocidad:3", "xp:5", "robo:2", "fuego:1"};
    public static final RegistryObject<Item> GEMA_RUBI = PoliciaShield.ITEMS.register("gema_rubi", () -> new Gema(0));
    public static final RegistryObject<Item> GEMA_ZAFIRO = PoliciaShield.ITEMS.register("gema_zafiro", () -> new Gema(1));
    public static final RegistryObject<Item> GEMA_AMBAR = PoliciaShield.ITEMS.register("gema_ambar", () -> new Gema(2));
    public static final RegistryObject<Item> GEMA_AMATISTA = PoliciaShield.ITEMS.register("gema_amatista", () -> new Gema(3));
    public static final RegistryObject<Item> GEMA_ONIX = PoliciaShield.ITEMS.register("gema_onix", () -> new Gema(4));
    public static final RegistryObject<Item> GEMA_TOPACIO = PoliciaShield.ITEMS.register("gema_topacio", () -> new Gema(5));

    static Item gema(int i) {
        return switch (i) {
            case 0 -> GEMA_RUBI.get();
            case 1 -> GEMA_ZAFIRO.get();
            case 2 -> GEMA_AMBAR.get();
            case 3 -> GEMA_AMATISTA.get();
            case 4 -> GEMA_ONIX.get();
            default -> GEMA_TOPACIO.get();
        };
    }

    static Item[] accesorios() {
        return new Item[]{AMULETO_VIDA.get(), AMULETO_SABIO.get(), AMULETO_GUERRA.get(), CAPA_VIAJERO.get(), CAPA_GUARDIAN.get(), CAPA_SOMBRAS.get(), CINTURON_CUERO.get(), CINTURON_ALQUIMISTA.get()};
    }

    static final String[] CASILLEROS = {"amuleto", "capa", "cinturon"};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosBotin());
    }

    static CompoundTag parsear(String s) {
        CompoundTag t = new CompoundTag();
        for (String par : s.split(",")) {
            String[] kv = par.split(":");
            t.putInt(kv[0], Integer.parseInt(kv[1]));
        }
        return t;
    }

    static void sumar(CompoundTag a, CompoundTag b) {
        for (String k : b.getAllKeys()) {
            a.putInt(k, a.getInt(k) + b.getInt(k));
        }
    }

    // Todo lo que da un objeto: afijos propios + base del accesorio + gemas.
    static CompoundTag afijos(ItemStack st) {
        CompoundTag res = new CompoundTag();
        if (st.isEmpty()) {
            return res;
        }
        if (st.hasTag()) {
            sumar(res, st.getTag().getCompound("pol_afijos"));
            ListTag gl = st.getTag().getList("pol_gemas", Tag.TAG_STRING);
            for (int i = 0; i < gl.size(); i++) {
                int g = java.util.Arrays.asList(GEMAS).indexOf(gl.getString(i));
                if (g >= 0) {
                    sumar(res, parsear(GEMA_AFIJO[g]));
                }
            }
        }
        if (st.getItem() instanceof Accesorio a) {
            sumar(res, a.base);
        }
        return res;
    }

    static int ranuras(ItemStack st) {
        return new int[]{0, 1, 1, 2, 3}[PoliciaRareza.rareza(st)];
    }

    // Tira afijos segun la rareza del objeto.
    static void tirarAfijos(ItemStack st, RandomSource r) {
        if (st.isEmpty() || !PoliciaRareza.equipo(st)) {
            return;
        }
        int rz = PoliciaRareza.rareza(st);
        int n = switch (rz) {
            case 1 -> 1;
            case 2 -> 1 + r.nextInt(2);
            case 3 -> 2;
            case 4 -> 3;
            default -> 0;
        };
        CompoundTag a = new CompoundTag();
        boolean arma = !(st.getItem() instanceof ArmorItem);
        int intentos = 0;
        while (a.size() < n && intentos++ < 30) {
            String k = AFIJOS[r.nextInt(AFIJOS.length)];
            if (a.contains(k) || (!arma && (k.equals("robo") || k.equals("fuego"))) || (arma && k.equals("armadura"))) {
                continue;
            }
            int v = switch (k) {
                case "vida" -> 1 + r.nextInt(2 + rz);
                case "critico" -> 2 + r.nextInt(3 + rz * 2);
                case "robo" -> 2 + r.nextInt(2 + rz);
                case "velocidad" -> 2 + r.nextInt(2 + rz * 2);
                case "xp" -> 3 + r.nextInt(3 + rz * 3);
                case "armadura" -> 1 + r.nextInt(1 + rz);
                case "energia" -> 5 + r.nextInt(5 + rz * 5);
                default -> 2 + r.nextInt(2 + rz);
            };
            a.putInt(k, v);
        }
        if (!a.isEmpty()) {
            st.getOrCreateTag().put("pol_afijos", a);
        }
    }

    // ---------------- Totales del jugador ----------------
    static ItemStack casillero(Player p, String slot) {
        CompoundTag d = PoliciaRPG.datos(p);
        return d.contains(slot) ? ItemStack.of(d.getCompound(slot)) : ItemStack.EMPTY;
    }

    // Suma de un afijo en mano, armadura, accesorios y bonos de conjunto.
    static int total(LivingEntity e, String k) {
        int t = afijos(e.getMainHandItem()).getInt(k);
        for (ItemStack a : e.getArmorSlots()) {
            t += afijos(a).getInt(k);
        }
        if (e instanceof Player p) {
            for (String s : CASILLEROS) {
                t += afijos(casillero(p, s)).getInt(k);
            }
            int caz = piezas(p, "cazador");
            if (k.equals("critico") && caz >= 2) {
                t += 10;
            }
            if (k.equals("xp") && caz >= 4) {
                t += 20;
            }
        }
        return t;
    }

    // ---------------- Conjuntos ----------------
    static int piezas(LivingEntity e, String conjunto) {
        int n = 0;
        for (ItemStack a : e.getArmorSlots()) {
            if (a.hasTag() && conjunto.equals(a.getTag().getString("pol_conjunto"))) {
                n++;
            }
        }
        return n;
    }

    static ItemStack piezaConjunto(RandomSource r, int nivel) {
        String c = r.nextBoolean() ? "cazador" : "coloso";
        boolean diamante = nivel >= 25 || r.nextInt(4) == 0;
        Item[] hierro = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
        Item[] diam = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        String[] parte = {"Casco", "Peto", "Grebas", "Botas"};
        int i = r.nextInt(4);
        ItemStack st = new ItemStack(diamante ? diam[i] : hierro[i]);
        st.getOrCreateTag().putString("pol_conjunto", c);
        st.setHoverName(Component.literal((c.equals("cazador") ? "§a" : "§c") + parte[i] + (c.equals("cazador") ? " del Cazador" : " del Coloso")).withStyle(s -> s.withItalic(false)));
        PoliciaRareza.ponerRareza(st, 2);
        tirarAfijos(st, r);
        return st;
    }

    // ---------------- Atributos de accesorios y conjuntos ----------------
    static final UUID A_VIDA = UUID.fromString("d7e2a1c0-3b4f-4c5d-8e6f-1a2b3c4d5e01");
    static final UUID A_VEL = UUID.fromString("d7e2a1c0-3b4f-4c5d-8e6f-1a2b3c4d5e02");
    static final UUID A_ARM = UUID.fromString("d7e2a1c0-3b4f-4c5d-8e6f-1a2b3c4d5e03");

    static void poner(Player p, Attribute at, UUID id, double v, AttributeModifier.Operation op) {
        AttributeInstance in = p.getAttribute(at);
        if (in == null) {
            return;
        }
        AttributeModifier viejo = in.getModifier(id);
        if (viejo != null && Math.abs(viejo.getAmount() - v) < 1.0E-6) {
            return;
        }
        in.removeModifier(id);
        if (v != 0) {
            in.addPermanentModifier(new AttributeModifier(id, "policia accesorios", v, op));
        }
    }

    static void aplicarExtra(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        for (String s : CASILLEROS) {
            sumar(t, afijos(casillero(p, s)));
        }
        int col = piezas(p, "coloso");
        int caz = piezas(p, "cazador");
        double vida = t.getInt("vida") + (col >= 2 ? 4 : 0);
        double vel = t.getInt("velocidad") / 100.0 + (caz >= 4 ? 0.15 : 0.0);
        double arm = t.getInt("armadura") + (col >= 4 ? 4 : 0);
        poner(p, Attributes.MAX_HEALTH, A_VIDA, vida, AttributeModifier.Operation.ADDITION);
        poner(p, Attributes.MOVEMENT_SPEED, A_VEL, vel, AttributeModifier.Operation.MULTIPLY_BASE);
        poner(p, Attributes.ARMOR, A_ARM, arm, AttributeModifier.Operation.ADDITION);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    // ---------------- Textos ----------------
    static String texto(String k, int v) {
        return switch (k) {
            case "vida" -> "+" + v + " de vida máxima";
            case "critico" -> "+" + v + " % de crítico";
            case "robo" -> v + " % del daño vuelve como vida";
            case "velocidad" -> "+" + v + " % de velocidad";
            case "xp" -> "+" + v + " % de XP";
            case "armadura" -> "+" + v + " de armadura";
            case "energia" -> "+" + v + " de energía máxima";
            default -> "Prende fuego " + v + " s al golpear";
        };
    }

    static String nombreCasillero(String s) {
        return switch (s) {
            case "amuleto" -> "Amuleto";
            case "capa" -> "Capa";
            case "cinturon" -> "Cinturón";
            case "anillo2" -> "Anillo 2";
            default -> "Anillo";
        };
    }

    // ---------------- Accesorio ----------------
    public static class Accesorio extends Item {
        final String casillero;
        final CompoundTag base;

        Accesorio(String casillero, String base) {
            super(new Item.Properties().stacksTo(1));
            this.casillero = casillero;
            this.base = parsear(base);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            ItemStack st = p.getItemInHand(hand);
            if (!level.isClientSide && p instanceof ServerPlayer sp) {
                CompoundTag d = PoliciaRPG.datos(sp);
                ItemStack viejo = d.contains(this.casillero) ? ItemStack.of(d.getCompound(this.casillero)) : ItemStack.EMPTY;
                d.put(this.casillero, st.copyWithCount(1).save(new CompoundTag()));
                p.setItemInHand(hand, viejo);
                aplicarExtra(sp);
                PoliciaRPG.sync(sp);
                level.playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0f, 1.2f);
                PoliciaMod.msg(sp, "Te pusiste: " + st.getHoverName().getString() + " (" + nombreCasillero(this.casillero) + ")");
            }
            return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack st, Level level, java.util.List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
            tip.add(Component.literal("§7" + nombreCasillero(this.casillero) + " · clic derecho para ponértelo (se ve en la ficha, tecla I)"));
        }
    }

    // ---------------- Gema ----------------
    public static class Gema extends Item {
        final int tipo;

        Gema(int tipo) {
            super(new Item.Properties().stacksTo(16));
            this.tipo = tipo;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            ItemStack gema = p.getItemInHand(hand);
            ItemStack obj = p.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!level.isClientSide && p instanceof ServerPlayer sp) {
                if (obj.isEmpty() || !PoliciaRareza.equipo(obj)) {
                    PoliciaMod.msg(sp, "Poné el arma o la armadura en la otra mano para engarzar la gema");
                } else {
                    ListTag gl = obj.getOrCreateTag().getList("pol_gemas", Tag.TAG_STRING);
                    if (gl.size() >= ranuras(obj)) {
                        PoliciaMod.msg(sp, ranuras(obj) == 0 ? "Ese objeto no tiene ranuras (hace falta rareza Poco común o mejor)" : "No le quedan ranuras libres");
                    } else {
                        gl.add(StringTag.valueOf(GEMAS[this.tipo]));
                        obj.getTag().put("pol_gemas", gl);
                        gema.shrink(1);
                        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.3f);
                        level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.4f, 1.8f);
                        PoliciaMod.msg(sp, "Engarzaste " + GEMA_NOMBRE[this.tipo] + " (" + texto(GEMA_AFIJO[this.tipo].split(":")[0], Integer.parseInt(GEMA_AFIJO[this.tipo].split(":")[1])) + ")");
                    }
                }
            }
            return InteractionResultHolder.sidedSuccess(gema, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack st, Level level, java.util.List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
            String[] kv = GEMA_AFIJO[this.tipo].split(":");
            tip.add(Component.literal("§9" + texto(kv[0], Integer.parseInt(kv[1]))));
            tip.add(Component.literal("§8Con el objeto en la otra mano, clic derecho para engarzarla"));
        }

        @Override
        public boolean isFoil(ItemStack st) {
            return true;
        }
    }

    // ---------------- Botin de los monstruos ----------------
    static void soltar(LivingDropsEvent e, ItemStack st) {
        LivingEntity m = e.getEntity();
        e.getDrops().add(new ItemEntity(m.level(), m.getX(), m.getY() + 0.3, m.getZ(), st));
    }

    static ItemStack equipoDeZona(RandomSource r, int nivel) {
        Item[] bajo = {Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.BOW};
        Item[] alto = {Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.CROSSBOW};
        Item[] l = nivel >= 25 && r.nextInt(3) > 0 ? alto : bajo;
        ItemStack st = new ItemStack(l[r.nextInt(l.length)]);
        // rareza: mejor cuanto mas alta la zona
        double x = r.nextDouble() * 100 - nivel * 0.8;
        int rz = x < 1 ? 4 : x < 8 ? 3 : x < 25 ? 2 : 1;
        if (rz == 4 && nivel < 20) {
            rz = 3;
        }
        PoliciaRareza.ponerRareza(st, rz);
        tirarAfijos(st, r);
        return st;
    }

    public static class EventosBotin {
        @SubscribeEvent
        public void atributos(ItemAttributeModifierEvent e) {
            ItemStack st = e.getItemStack();
            if (st.getItem() instanceof Accesorio) {
                return;
            }
            CompoundTag a = afijos(st);
            if (a.isEmpty()) {
                return;
            }
            EquipmentSlot slot = st.getItem() instanceof ArmorItem ai ? ai.getEquipmentSlot() : EquipmentSlot.MAINHAND;
            if (e.getSlotType() != slot) {
                return;
            }
            int i = slot.ordinal();
            UUID base = UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b0" + i);
            UUID vel = UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b1" + i);
            UUID arm = UUID.fromString("c4b1e2d0-7a3f-4e5c-9d21-0f1e2d3c4b2" + i);
            if (a.contains("vida")) {
                e.addModifier(Attributes.MAX_HEALTH, new AttributeModifier(base, "afijo vida", a.getInt("vida"), AttributeModifier.Operation.ADDITION));
            }
            if (a.contains("velocidad")) {
                e.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(vel, "afijo velocidad", a.getInt("velocidad") / 100.0, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            if (a.contains("armadura")) {
                e.addModifier(Attributes.ARMOR, new AttributeModifier(arm, "afijo armadura", a.getInt("armadura"), AttributeModifier.Operation.ADDITION));
            }
        }

        // critico, robo de vida y fuego al pegar; el Coloso recibe menos
        @SubscribeEvent
        public void golpe(LivingHurtEvent e) {
            if (e.getEntity() instanceof ServerPlayer v && piezas(v, "coloso") >= 4) {
                e.setAmount(e.getAmount() * 0.85f);
            }
            if (!(e.getSource().getDirectEntity() instanceof ServerPlayer p) || e.getSource().getEntity() != p) {
                return;
            }
            LivingEntity v = e.getEntity();
            int crit = total(p, "critico");
            if (crit > 0 && p.getRandom().nextFloat() < crit / 100.0f) {
                e.setAmount(e.getAmount() * 1.5f);
                PoliciaCombate.marcarCritico(v);
            }
            int robo = total(p, "robo");
            if (robo > 0) {
                p.heal(e.getAmount() * robo / 100.0f);
            }
            int fuego = afijos(p.getMainHandItem()).getInt("fuego");
            if (fuego > 0) {
                v.setSecondsOnFire(fuego);
            }
        }

        // equipo, gemas y piezas de conjunto segun el nivel de la zona
        @SubscribeEvent
        public void botin(LivingDropsEvent e) {
            LivingEntity m = e.getEntity();
            if (m.level().isClientSide || !(m instanceof Monster) || !(e.getSource().getEntity() instanceof ServerPlayer)) {
                return;
            }
            RandomSource r = m.getRandom();
            int nivel = 1;
            try {
                nivel = PoliciaZonas.zona((ServerLevel) m.level(), m.blockPosition()).nivel;
            } catch (Throwable ignored) {
            }
            boolean elite = PoliciaCombate.esElite(m);
            float k = elite ? 4.0f : 1.0f;
            if (r.nextFloat() < (0.012f + nivel * 0.0006f) * k) {
                soltar(e, equipoDeZona(r, nivel));
            }
            if (r.nextFloat() < (0.01f + nivel * 0.0004f) * k * 2) {
                soltar(e, new ItemStack(gema(r.nextInt(GEMAS.length))));
            }
            if (r.nextFloat() < (elite ? 0.1f : 0.002f + nivel * 0.0002f)) {
                soltar(e, piezaConjunto(r, nivel));
            }
            if (r.nextFloat() < (elite ? 0.06f : 0.002f)) {
                Item[] a = accesorios();
                soltar(e, new ItemStack(a[r.nextInt(a.length)]));
            }
        }

        // las menas a veces sueltan una gema
        @SubscribeEvent
        public void mena(BlockEvent.BreakEvent e) {
            if (!(e.getPlayer() instanceof ServerPlayer p) || p.isCreative() || !e.getState().is(Tags.Blocks.ORES)) {
                return;
            }
            if (p.getRandom().nextFloat() < 0.02f) {
                var pos = e.getPos();
                p.level().addFreshEntity(new ItemEntity(p.level(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(gema(p.getRandom().nextInt(GEMAS.length)))));
                p.displayClientMessage(Component.literal("§d¡Encontraste una gema!"), true);
            }
        }

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p && p.tickCount % 20 == 7) {
                aplicarExtra(p);
            }
        }

        @SubscribeEvent
        public void tooltip(ItemTooltipEvent e) {
            ItemStack st = e.getItemStack();
            if (st.getItem() instanceof Gema) {
                return;
            }
            CompoundTag a = afijos(st);
            if (st.getItem() instanceof Accesorio acc) {
                for (String k : AFIJOS) {
                    if (acc.base.contains(k)) {
                        e.getToolTip().add(Component.literal("§a" + texto(k, acc.base.getInt(k))));
                    }
                }
                return;
            }
            CompoundTag propios = st.hasTag() ? st.getTag().getCompound("pol_afijos") : new CompoundTag();
            for (String k : AFIJOS) {
                if (propios.contains(k)) {
                    e.getToolTip().add(Component.literal("§9◆ " + texto(k, propios.getInt(k))));
                }
            }
            int n = ranuras(st);
            if (n > 0 && PoliciaRareza.equipo(st)) {
                ListTag gl = st.hasTag() ? st.getTag().getList("pol_gemas", Tag.TAG_STRING) : new ListTag();
                for (int i = 0; i < n; i++) {
                    if (i < gl.size()) {
                        int g = java.util.Arrays.asList(GEMAS).indexOf(gl.getString(i));
                        String[] kv = GEMA_AFIJO[Math.max(0, g)].split(":");
                        e.getToolTip().add(Component.literal("§d◆ " + GEMA_NOMBRE[Math.max(0, g)] + " §7(" + texto(kv[0], Integer.parseInt(kv[1])) + ")"));
                    } else {
                        e.getToolTip().add(Component.literal("§8◇ Ranura vacía"));
                    }
                }
            }
            String c = st.hasTag() ? st.getTag().getString("pol_conjunto") : "";
            if (!c.isEmpty()) {
                int puestas = e.getEntity() != null ? piezas(e.getEntity(), c) : 0;
                boolean caz = c.equals("cazador");
                e.getToolTip().add(Component.literal((caz ? "§a" : "§c") + "Conjunto " + (caz ? "del Cazador" : "del Coloso") + " §7(" + puestas + "/4)"));
                e.getToolTip().add(Component.literal((puestas >= 2 ? "§f" : "§8") + " 2: " + (caz ? "+10 % de crítico" : "+4 de vida máxima")));
                e.getToolTip().add(Component.literal((puestas >= 4 ? "§f" : "§8") + " 4: " + (caz ? "+15 % de velocidad y +20 % de XP" : "+4 de armadura y 15 % menos daño")));
            }
        }
    }

    // XP extra por afijos (lo suma PoliciaNivel.conIngenio).
    static float bonoXp(Player p) {
        return total(p, "xp") / 100.0f;
    }

    // Energia extra (la suma PoliciaCombate.energiaMax).
    static float bonoEnergia(Player p) {
        return total(p, "energia");
    }
}
