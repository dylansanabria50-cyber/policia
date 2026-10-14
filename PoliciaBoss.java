package com.example.policia;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;

// Jefes de oficio (NPC): hablan, venden, compran lo robado y despiden al
// jugador de su oficio. Incluye el chaleco antibalas y los items de un solo uso.
public class PoliciaBoss {
    public static final RegistryObject<EntityType<BossNpc>> BOSS = PoliciaRefuerzo.ENTITIES.register("jefe_oficio", () -> EntityType.Builder.of(BossNpc::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).build("policia:jefe_oficio"));
    public static final RegistryObject<Item> SKILL_ITEM = PoliciaShield.ITEMS.register("habilidad_unica", () -> new SingleUse(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> VEST = PoliciaShield.ITEMS.register("chaleco_antibalas", () -> new ArmorItem((ArmorMaterial) VestMat.INSTANCE, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create((ResourceKey) Registries.CREATIVE_MODE_TAB, "policia");
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("oficios", () -> CreativeModeTab.builder().title(Component.literal("Oficios")).icon(() -> new ItemStack((ItemLike) PoliciaPlaca.PLACA.get())).displayItems((params, out) -> {
        String[][] sk;
        out.accept((ItemLike) PoliciaPlaca.PLACA.get());
        out.accept((ItemLike) PoliciaPlaca.BALDE.get());
        out.accept((ItemLike) PoliciaPlaca.GUANTE.get());
        out.accept((ItemLike) PoliciaPlaca.MARTILLO.get());
        out.accept((ItemLike) PoliciaShield.SHIELD.get());
        out.accept((ItemLike) VEST.get());
        for (String[] x : sk = new String[][]{{"refuerzo", "Refuerzo"}, {"k9", "Perro K9"}, {"esposas", "Esposas"}, {"sirena", "Sirena y torreta"}, {"dron", "Dron de vigilancia"}, {"burbuja", "Escudo de burbuja"}, {"pico", "Pico de albanil"}, {"muro", "Muro"}, {"refugio", "Refugio"}, {"silbido", "Silbido"}}) {
            out.accept(PoliciaBoss.single(x[0], x[1]));
        }
    }).build());
    static final String[] NAMES = new String[]{"Jefe de policia", "Capataz", "Jefe de ladrones", "Perista", "Ladron", "Agente", "Obrero", "Maestro herrero"};
    static final String[] JOBS = new String[]{"policia", "albanil", "ladron", "herrero"};
    static final String[] JOBNAME = new String[]{"policia", "albanil", "ladron", "herrero legendario"};
    // Tipo de NPC del jefe de cada oficio (indice en NAMES).
    static final int HERRERO_BOSS = 7;

    // Jefes de oficio: 0 policia, 1 capataz (albanil), 2 ladrones, 7 herrero.
    static boolean isJobBoss(int kind) {
        return kind == 0 || kind == 1 || kind == 2 || kind == HERRERO_BOSS;
    }

    // Indice de oficio (en JOBS/JOBNAME/LINES) del jefe de tipo "kind".
    static int jobIdx(int kind) {
        return kind == HERRERO_BOSS ? 3 : Math.max(0, Math.min(2, kind));
    }
    static final Map<UUID, Cin> CIN = new HashMap<UUID, Cin>();
    static final String[][] LINES = new String[][]{{"Mantener el orden no es un juego, novato.", "Los bandidos acampan lejos de las aldeas. Hablen con el comisario para las misiones.", "Un buen oficial cuida a la gente y cumple la ley.", "El chaleco antibalas te salva de las flechas... pero se gasta rapido."}, {"Una obra bien hecha dura toda la vida.", "Sin ayudantes no hay muro que se termine.", "Cuida el pico: lo que se rompe en la obra lo pagas tu.", "Muros, refugios y tractores: todo se construye."}, {"En este negocio se habla poco y se roba mucho.", "El perista compra todo lo que traigas, sin preguntas.", "Silba, acercate por la espalda y no mires atras.", "La suerte favorece a los que practican."}, {"El hierro se doblega con fuego y paciencia.", "Un buen yunque vale mas que cien espadas.", "Lo que no sabes forjar, analizalo primero.", "Golpe firme, mano tranquila. Asi se templa el acero."}};

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaBoss::attrs);
        TABS.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    static void attrs(EntityAttributeCreationEvent e) {
        e.put(BOSS.get(), BossNpc.attrs().build());
    }

    static ItemStack single(String id, String name) {
        ItemStack s = new ItemStack((ItemLike) SKILL_ITEM.get());
        s.getOrCreateTag().putString("pol_skill", id);
        s.setHoverName(Component.literal(name + " (un solo uso)"));
        return s;
    }

    static boolean run(ServerPlayer p, String id) {
        CompoundTag d = p.getPersistentData();
        try {
            switch (id) {
                case "refuerzo": {
                    boolean ok;
                    int old = d.getInt("pol_rl");
                    int oc = d.getInt("pol_rcd");
                    if (old <= 0) {
                        d.putInt("pol_rl", 1);
                    }
                    d.putInt("pol_rcd", 0);
                    PoliciaRefuerzo.use(p);
                    boolean bl = ok = d.getInt("pol_rcd") > 0;
                    if (old <= 0) {
                        d.putInt("pol_rl", old);
                    }
                    if (!ok) {
                        d.putInt("pol_rcd", oc);
                    }
                    return ok;
                }
                case "k9": {
                    return PoliciaExtra.k9(p);
                }
                case "esposas": {
                    return PoliciaExtra.cuff(p);
                }
                case "sirena": {
                    return PoliciaExtra.siren(p);
                }
                case "dron": {
                    return PoliciaExtra.drone(p);
                }
                case "burbuja": {
                    return PoliciaBubble.use(p);
                }
                case "silbido": {
                    PoliciaLadron.whistleUse(p);
                    return true;
                }
                case "muro": {
                    return PoliciaAlbanil.useSingle(p, 1);
                }
                case "refugio": {
                    return PoliciaAlbanil.useSingle(p, 2);
                }
                case "pico": {
                    return PoliciaAlbanil.useSingle(p, 0);
                }
            }
            PoliciaMod.msg(p, "Objeto sin efecto");
            return false;
        } catch (RuntimeException ex) {
            PoliciaMod.msg(p, "No se pudo usar ahora");
            return false;
        }
    }

    static void startCin(ServerPlayer p, BossNpc b) {
        Cin c = new Cin();
        c.boss = b.getUUID();
        c.x = p.getX();
        c.y = p.getY();
        c.z = p.getZ();
        c.y0 = p.getYRot();
        c.p0 = p.getXRot();
        c.kind = b.kind();
        CIN.put(p.getUUID(), c);
    }

    static void stepCin(MinecraftServer srv) {
        for (Map.Entry<UUID, Cin> en : new ArrayList<Map.Entry<UUID, Cin>>(CIN.entrySet())) {
            Entity e;
            Cin c = en.getValue();
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            Entity entity = e = p == null ? null : p.serverLevel().getEntity(c.boss);
            if (p == null || e == null || !e.isAlive() || ++c.t > 75) {
                CIN.remove(en.getKey());
                continue;
            }
            double dx = e.getX() - c.x;
            double dz = e.getZ() - c.z;
            double dy = e.getY() + 1.4 - (c.y + (double) p.getEyeHeight());
            float ty = (float) (Mth.atan2(-dx, dz) * 57.29577951308232);
            float tp = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 57.29577951308232));
            float k = Math.min(1.0f, (float) c.t / 20.0f);
            float sm = k * k * (3.0f - 2.0f * k);
            float yaw = c.y0 + Mth.wrapDegrees(ty - c.y0) * sm;
            float pit = c.p0 + (tp - c.p0) * sm;
            p.setDeltaMovement(0.0, 0.0, 0.0);
            p.connection.teleport(c.x, c.y, c.z, yaw, pit);
            if (c.t != 22) continue;
            p.connection.send((Packet) new ClientboundSetTitlesAnimationPacket(8, 40, 12));
            p.connection.send((Packet) new ClientboundSetSubtitleTextPacket(Component.literal(c.kind == 0 ? "Novato, que haces aqui?" : "Que haces aqui?")));
            p.connection.send((Packet) new ClientboundSetTitleTextPacket(Component.literal(c.kind == 0 ? "Jefe de policia" : "Jefe de ladrones")));
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), c.kind == 0 ? SoundEvents.VILLAGER_NO : SoundEvents.PIGLIN_AMBIENT, SoundSource.NEUTRAL, 0.6f, 0.7f);
        }
    }

    static ItemStack named(Item it, String name, String... lore) {
        ItemStack s = new ItemStack((ItemLike) it);
        s.setHoverName(Component.literal(name));
        if (lore.length > 0) {
            ListTag l = new ListTag();
            for (String x : lore) {
                l.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(x))));
            }
            s.getOrCreateTagElement("display").put("Lore", (Tag) l);
        }
        return s;
    }

    static ItemStack pane() {
        return PoliciaBoss.named(Items.GRAY_STAINED_GLASS_PANE, " ", new String[0]);
    }

    static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) {
            p.drop(s, false);
        }
    }

    static void later(ServerPlayer p, Runnable r) {
        p.getServer().execute(r);
    }

    static void openMenu3(ServerPlayer sp, String title, SimpleContainer c, BiConsumer<ServerPlayer, Integer> on) {
        sp.openMenu((MenuProvider) new SimpleMenuProvider((id, inv, pl) -> new Menu3(id, inv, c, sp, on), Component.literal(title)));
    }

    static void openMain(ServerPlayer sp, BossNpc b) {
        int k = b.kind();
        int j = PoliciaBoss.jobIdx(k);
        String cur = PoliciaMod.job(sp);
        boolean mine = JOBS[j].equals(cur);
        boolean jobless = cur.isEmpty();
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; ++i) {
            c.setItem(i, PoliciaBoss.pane());
        }
        c.setItem(11, PoliciaBoss.named(Items.PAPER, "Hablar", "Escuchar lo que tiene para decir"));
        c.setItem(13, mine ? PoliciaBoss.named(Items.EMERALD, "Comprar", "Tienda de " + JOBNAME[j] + " (esmeraldas)") : PoliciaBoss.named(Items.BARRIER, "Comprar", "Solo le vendo a mi gente (oficio: " + JOBNAME[j] + ")"));
        if (mine) {
            c.setItem(15, PoliciaBoss.named(Items.LEAD, "Dejar el oficio", "Pedir que te despidan"));
        } else if (jobless) {
            c.setItem(15, PoliciaBoss.named(Items.WRITABLE_BOOK, "Unirme al oficio", "Convertirte en " + JOBNAME[j], "Solo si no tenes ningun oficio"));
        } else {
            c.setItem(15, PoliciaBoss.named(Items.BARRIER, "Dejar el oficio", "No eres de mi oficio (sos " + cur + ")", "Renuncia con tu jefe para poder unirte"));
        }
        PoliciaBoss.openMenu3(sp, NAMES[k], c, (p, slot) -> {
            if (slot == 11) {
                p.closeContainer();
                PoliciaBoss.talk(p, b);
            } else if (slot == 13) {
                if (!mine) {
                    PoliciaMod.msg(p, "Solo le vendo a quienes son " + JOBNAME[j]);
                    return;
                }
                PoliciaBoss.later(p, () -> PoliciaBoss.openShop(p, k));
            } else if (slot == 15) {
                if (mine) {
                    PoliciaBoss.later(p, () -> PoliciaBoss.openFire(p, k));
                } else if (PoliciaMod.job(p).isEmpty()) {
                    PoliciaBoss.later(p, () -> PoliciaBoss.openJoin(p, k));
                } else {
                    PoliciaMod.msg(p, "Ya tenes un oficio: renuncia con tu jefe primero");
                }
            }
        });
    }

    static void openJoin(ServerPlayer sp, int k) {
        int j = PoliciaBoss.jobIdx(k);
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; ++i) {
            c.setItem(i, PoliciaBoss.pane());
        }
        c.setItem(13, PoliciaBoss.named(Items.WRITABLE_BOOK, "Unirte al oficio", "Queres convertirte en " + JOBNAME[j] + "?", "Recibis el libro con sus habilidades y teclas."));
        c.setItem(11, PoliciaBoss.named(Items.LIME_DYE, "SI, quiero unirme", new String[0]));
        c.setItem(15, PoliciaBoss.named(Items.RED_DYE, "NO, todavia no", new String[0]));
        PoliciaBoss.openMenu3(sp, NAMES[k], c, (p, slot) -> {
            if (slot == 11) {
                p.closeContainer();
                if (!PoliciaMod.job(p).isEmpty()) {
                    PoliciaMod.msg(p, "Ya tenes un oficio");
                    return;
                }
                PoliciaOficio.assign(p, PoliciaOficio.JOBS[j], PoliciaOficio.LABEL[j], PoliciaOficio.HINT[j]);
                p.sendSystemMessage(Component.literal("<" + NAMES[k] + "> Bienvenido al oficio. No me hagas quedar mal."));
            } else if (slot == 15) {
                p.closeContainer();
            }
        });
    }

    static void talk(ServerPlayer p, BossNpc b) {
        int k = b.kind();
        String[] ls = LINES[PoliciaBoss.jobIdx(k)];
        p.sendSystemMessage(Component.literal("<" + NAMES[k] + "> " + ls[p.getRandom().nextInt(ls.length)]));
    }

    static void openFire(ServerPlayer sp, int k) {
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; ++i) {
            c.setItem(i, PoliciaBoss.pane());
        }
        c.setItem(13, PoliciaBoss.named(Items.BARRIER, "Esta despedido!", "Quiere dejar el oficio de " + JOBNAME[PoliciaBoss.jobIdx(k)] + "?", "Perdera el modo y el libro; el progreso se conserva."));
        c.setItem(11, PoliciaBoss.named(Items.LIME_DYE, "SI, quiero ser despedido", new String[0]));
        c.setItem(15, PoliciaBoss.named(Items.RED_DYE, "NO, quiero seguir", new String[0]));
        PoliciaBoss.openMenu3(sp, NAMES[k], c, (p, slot) -> {
            if (slot == 11) {
                p.closeContainer();
                PoliciaBoss.fire(p, k);
            } else if (slot == 15) {
                p.closeContainer();
                PoliciaMod.msg(p, "Sigues en tu oficio");
            }
        });
    }

    static void fire(ServerPlayer p, int k) {
        if (!JOBS[PoliciaBoss.jobIdx(k)].equals(PoliciaMod.job(p))) {
            return;
        }
        if (PoliciaMod.on(p)) {
            PoliciaMod.act(p, 0);
        }
        p.getPersistentData().putString("pol_job", "");
        PoliciaMod.msg(p, "Estas despedido del oficio. Para tener otro, habla con el jefe de ese oficio");
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
        PoliciaMod.sync(p);
    }

    static ItemStack ench(ItemStack s, Enchantment e, int lv) {
        s.enchant(e, lv);
        return s;
    }

    static List<Entry> catalog(int k) {
        ArrayList<Entry> l = new ArrayList<Entry>();
        if (k == HERRERO_BOSS) {
            l.add(new Entry(new ItemStack((ItemLike) Items.IRON_INGOT, 8), 4));
            l.add(new Entry(new ItemStack((ItemLike) Items.GOLD_INGOT, 4), 5));
            l.add(new Entry(new ItemStack((ItemLike) Items.COAL_BLOCK, 4), 4));
            l.add(new Entry(new ItemStack((ItemLike) Items.LAVA_BUCKET), 4));
            l.add(new Entry(new ItemStack((ItemLike) Items.ANVIL), 6));
            l.add(new Entry(new ItemStack((ItemLike) Items.SMITHING_TABLE), 3));
            l.add(new Entry(new ItemStack((ItemLike) Items.GRINDSTONE), 3));
            l.add(new Entry(new ItemStack((ItemLike) Items.BLAST_FURNACE), 5));
            l.add(new Entry(new ItemStack((ItemLike) Items.EXPERIENCE_BOTTLE, 8), 6));
            l.add(new Entry(new ItemStack((ItemLike) Items.NAME_TAG), 5));
            l.add(new Entry(new ItemStack((ItemLike) Items.DIAMOND), 8));
            l.add(new Entry(new ItemStack((ItemLike) Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), 24));
            l.add(new Entry(PoliciaBoss.ench(new ItemStack((ItemLike) Items.IRON_PICKAXE), Enchantments.UNBREAKING, 3), 10));
            l.add(new Entry(PoliciaBoss.ench(new ItemStack((ItemLike) Items.IRON_SWORD), Enchantments.SHARPNESS, 2), 10));
            return l;
        }
        if (k == 0) {
            l.add(new Entry(new ItemStack((ItemLike) Items.IRON_HELMET), 3));
            l.add(new Entry(new ItemStack((ItemLike) Items.IRON_CHESTPLATE), 5));
            l.add(new Entry(new ItemStack((ItemLike) Items.IRON_LEGGINGS), 4));
            l.add(new Entry(new ItemStack((ItemLike) Items.IRON_BOOTS), 3));
            l.add(new Entry(new ItemStack((ItemLike) Items.BOW), 14));
            l.add(new Entry(new ItemStack((ItemLike) Items.CROSSBOW), 24));
            l.add(new Entry(new ItemStack((ItemLike) Items.SHIELD), 20));
            l.add(new Entry(new ItemStack((ItemLike) Items.SPYGLASS), 12));
            ItemStack v = new ItemStack((ItemLike) VEST.get());
            v.setHoverName(Component.literal("Chaleco antibalas"));
            l.add(new Entry(v, 18));
            l.add(new Entry(PoliciaBoss.single("refuerzo", "Refuerzo"), 12));
            l.add(new Entry(PoliciaBoss.single("k9", "Perro K9"), 10));
            l.add(new Entry(PoliciaBoss.single("esposas", "Esposas"), 6));
            l.add(new Entry(PoliciaBoss.single("sirena", "Sirena y torreta"), 10));
            l.add(new Entry(PoliciaBoss.single("dron", "Dron de vigilancia"), 12));
            l.add(new Entry(PoliciaBoss.single("burbuja", "Escudo de burbuja"), 20));
        } else if (k == 1) {
            l.add(new Entry(PoliciaBoss.named(Items.LEATHER_HELMET, "Casco de obra", new String[0]), 5));
            l.add(new Entry(new ItemStack((ItemLike) Items.IRON_PICKAXE), 5));
            l.add(new Entry(new ItemStack((ItemLike) Items.WOODEN_PICKAXE), 18));
            l.add(new Entry(new ItemStack((ItemLike) Items.TORCH, 32), 6));
            l.add(new Entry(new ItemStack((ItemLike) Items.OAK_PLANKS, 16), 4));
            l.add(new Entry(new ItemStack((ItemLike) Items.SAND, 64), 4));
            l.add(new Entry(new ItemStack((ItemLike) Items.SCAFFOLDING, 8), 5));
            l.add(new Entry(new ItemStack((ItemLike) Items.STONE_PICKAXE), 3));
            l.add(new Entry(PoliciaBoss.single("pico", "Pico de albanil"), 8));
            l.add(new Entry(PoliciaBoss.single("muro", "Muro"), 6));
            l.add(new Entry(PoliciaBoss.single("refugio", "Refugio"), 14));
        } else {
            l.add(new Entry(PoliciaBoss.ench(PoliciaBoss.named(Items.IRON_BOOTS, "Botas de sigilo", new String[0]), Enchantments.FALL_PROTECTION, 2), 8));
            l.add(new Entry(new ItemStack((ItemLike) Items.ENDER_PEARL, 2), 6));
            l.add(new Entry(PotionUtils.setPotion(new ItemStack((ItemLike) Items.POTION), Potions.INVISIBILITY), 10));
            l.add(new Entry(new ItemStack((ItemLike) Items.FIREWORK_ROCKET, 2), 2));
            l.add(new Entry(new ItemStack((ItemLike) Items.BUNDLE), 6));
            l.add(new Entry(new ItemStack((ItemLike) Items.GUNPOWDER, 8), 3));
            l.add(new Entry(PoliciaBoss.single("silbido", "Silbido"), 8));
        }
        return l;
    }

    static int emeralds(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (!s.is(Items.EMERALD)) continue;
            n += s.getCount();
        }
        return n;
    }

    static void takeEmeralds(Player p, int n) {
        for (ItemStack s : p.getInventory().items) {
            if (n <= 0) {
                return;
            }
            if (!s.is(Items.EMERALD)) continue;
            int t = Math.min(n, s.getCount());
            s.shrink(t);
            n -= t;
        }
    }

    static void openShop(ServerPlayer sp, int k) {
        List<Entry> l = PoliciaBoss.catalog(k);
        SimpleContainer c = new SimpleContainer(54);
        for (int i = 0; i < l.size() && i < 54; ++i) {
            Entry e = l.get(i);
            ItemStack d = e.give.copy();
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Precio: " + e.price + " esmeraldas"))));
            d.getOrCreateTagElement("display").put("Lore", (Tag) lore);
            c.setItem(i, d);
        }
        sp.openMenu((MenuProvider) new SimpleMenuProvider((id, inv, pl) -> new Shop(id, inv, c, sp, l), Component.literal("Tienda - " + NAMES[k])));
    }

    static double value(ItemStack s) {
        Item it = s.getItem();
        if (it == Items.DIAMOND) {
            return 30.0;
        }
        if (it == Items.SPYGLASS) {
            return 6.0;
        }
        if (it == Items.EMERALD) {
            return 1.0;
        }
        if (it == Items.GOLD_INGOT) {
            return 2.0;
        }
        if (it == Items.IRON_INGOT) {
            return 1.0;
        }
        if (it == Items.STRING) {
            return 0.25;
        }
        if (it == Items.STICK) {
            return 0.1;
        }
        if (it == Items.ENDER_PEARL) {
            return 3.0;
        }
        if (it == Items.EXPERIENCE_BOTTLE) {
            return 2.0;
        }
        if (it == Items.NETHERITE_INGOT) {
            return 60.0;
        }
        switch (s.getRarity()) {
            case UNCOMMON: {
                return 2.0;
            }
            case RARE: {
                return 5.0;
            }
            case EPIC: {
                return 12.0;
            }
        }
        return 0.3;
    }

    static void openSell(ServerPlayer sp, BossNpc b) {
        if (!"ladron".equals(PoliciaMod.job(sp))) {
            sp.sendSystemMessage(Component.literal("<Perista> Yo solo trato con ladrones."));
            return;
        }
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 18; i < 27; ++i) {
            c.setItem(i, PoliciaBoss.pane());
        }
        c.setItem(22, PoliciaBoss.named(Items.EMERALD, "VENDER", "Pon lo robado arriba y pulsa aqui", "Te pago en esmeraldas"));
        sp.openMenu((MenuProvider) new SimpleMenuProvider((id, inv, pl) -> new Sell(id, inv, c, sp), Component.literal("Perista")));
    }

    public static class Ev {
        @SubscribeEvent
        public void bsTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || CIN.isEmpty()) {
                return;
            }
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv != null) {
                PoliciaBoss.stepCin(srv);
            }
        }

        @SubscribeEvent
        public void bsHurt(LivingHurtEvent e) {
            LivingEntity livingEntity = e.getEntity();
            if (!(livingEntity instanceof ServerPlayer)) {
                return;
            }
            ServerPlayer p = (ServerPlayer) livingEntity;
            ItemStack ch = p.getItemBySlot(EquipmentSlot.CHEST);
            if (ch.isEmpty() || ch.getItem() != VEST.get()) {
                return;
            }
            if (e.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                e.setAmount(e.getAmount() * 0.3f);
                ch.hurtAndBreak(6, p, x -> x.broadcastBreakEvent(EquipmentSlot.CHEST));
            }
        }
    }

    public static class BossNpc extends PathfinderMob {
        static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(BossNpc.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> SIT = SynchedEntityData.defineId(BossNpc.class, EntityDataSerializers.BOOLEAN);
        BlockPos home;
        int homeR;

        public BossNpc(EntityType<? extends BossNpc> t, Level l) {
            super(t, l);
            this.setPersistenceRequired();
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.KNOCKBACK_RESISTANCE, 5.0).add(Attributes.FOLLOW_RANGE, 24.0);
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(KIND, 0);
            this.entityData.define(SIT, false);
        }

        public int kind() {
            return this.entityData.get(KIND);
        }

        public boolean sitting() {
            return this.entityData.get(SIT);
        }

        public void setSitting(boolean s) {
            this.entityData.set(SIT, s);
        }

        public void setHome(BlockPos p, int r) {
            this.home = p;
            this.homeR = r;
            this.restrictTo(p, r);
        }

        public void setKind(int k) {
            this.entityData.set(KIND, k);
            if (k < 0 || k >= NAMES.length) {
                return;
            }
            this.setCustomName(Component.literal(NAMES[k]));
            this.setCustomNameVisible(k <= 3 || k == HERRERO_BOSS);
            if (k <= 3 || k == HERRERO_BOSS) {
                this.setNoAi(true);
            }
            if (k == HERRERO_BOSS) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike) PoliciaPlaca.MARTILLO.get()));
                this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }
            if (k == 4) {
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(34.0);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0);
                this.setHealth(34.0f);
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike) Items.IRON_SWORD));
                this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            } else if (k == 5) {
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0);
                this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32.0);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
                this.setHealth(40.0f);
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike) Items.IRON_SWORD));
                this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            } else if (k == 6) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike) Items.IRON_PICKAXE));
                this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }
            if (k == 0 || k == 2) {
                this.setSitting(true);
            }
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, (Goal) new FloatGoal((Mob) this));
            this.goalSelector.addGoal(1, (Goal) new MeleeAttackGoal(this, 1.2, true) {

                @Override
                public boolean canUse() {
                    return (BossNpc.this.kind() == 4 || BossNpc.this.kind() == 5) && super.canUse();
                }
            });
            this.goalSelector.addGoal(5, (Goal) new WaterAvoidingRandomStrollGoal(this, 0.8) {

                @Override
                public boolean canUse() {
                    return BossNpc.this.kind() >= 4 && BossNpc.this.kind() != HERRERO_BOSS && super.canUse();
                }
            });
            this.goalSelector.addGoal(4, (Goal) new MoveTowardsRestrictionGoal(this, 1.0) {

                @Override
                public boolean canUse() {
                    return BossNpc.this.kind() == 5 && super.canUse();
                }
            });
            this.goalSelector.addGoal(6, (Goal) new LookAtPlayerGoal((Mob) this, Player.class, 8.0f));
            this.goalSelector.addGoal(7, (Goal) new RandomLookAroundGoal((Mob) this));
            this.targetSelector.addGoal(1, (Goal) new HurtByTargetGoal(this, new Class[0]) {

                @Override
                public boolean canUse() {
                    return BossNpc.this.kind() == 4 && super.canUse();
                }
            });
            this.targetSelector.addGoal(2, (Goal) new NearestAttackableTargetGoal((Mob) this, Player.class, 10, true, false, pl -> this.kind() == 4 && !"ladron".equals(PoliciaMod.job((Player) pl))));
            this.targetSelector.addGoal(2, (Goal) new NearestAttackableTargetGoal((Mob) this, Player.class, 5, true, false, pl -> this.kind() == 5 && !((Player) pl).isCreative() && PoliciaLadron.wanted((Player) pl) > 0 && ((Player) pl).getPersistentData().getInt("pol_esc") <= 0));
            this.goalSelector.addGoal(3, new Goal() {
                Villager tv;
                int t = 0;
                int wait = 0;

                {
                    this.setFlags(EnumSet.of(Goal.Flag.MOVE));
                }

                @Override
                public boolean canUse() {
                    if (BossNpc.this.kind() != 5 || BossNpc.this.getTarget() != null || BossNpc.this.getRandom().nextInt(400) != 0) {
                        return false;
                    }
                    List<Villager> vs = BossNpc.this.level().getEntitiesOfClass(Villager.class, BossNpc.this.getBoundingBox().inflate(80.0, 30.0, 80.0));
                    if (vs.isEmpty()) {
                        return false;
                    }
                    this.tv = vs.get(BossNpc.this.getRandom().nextInt(vs.size()));
                    return true;
                }

                @Override
                public boolean canContinueToUse() {
                    return this.tv != null && this.tv.isAlive() && BossNpc.this.getTarget() == null && this.t < 1200 && this.wait < 160;
                }

                @Override
                public void start() {
                    this.t = 0;
                    this.wait = 0;
                }

                @Override
                public void stop() {
                    this.tv = null;
                    BossNpc.this.getNavigation().stop();
                }

                @Override
                public void tick() {
                    ++this.t;
                    if (BossNpc.this.distanceToSqr((Entity) this.tv) > 16.0) {
                        if (this.t % 10 == 0) {
                            BossNpc.this.getNavigation().moveTo((Entity) this.tv, 0.9);
                        }
                    } else {
                        BossNpc.this.getNavigation().stop();
                        BossNpc.this.getLookControl().setLookAt((Entity) this.tv, 30.0f, 30.0f);
                        ++this.wait;
                    }
                }
            });
            this.targetSelector.addGoal(3, (Goal) new NearestAttackableTargetGoal((Mob) this, Mob.class, 5, true, false, e -> {
                PoliciaMision.Pj pj;
                return this.kind() == 5 && (e instanceof Enemy || e instanceof PoliciaMision.Pj && (pj = (PoliciaMision.Pj) ((Object) e)).hostile());
            }));
        }

        @Override
        public boolean isInvulnerableTo(DamageSource s) {
            if (!(this.kind() > 3 && this.kind() != 6 && this.kind() != HERRERO_BOSS || s.is(DamageTypeTags.BYPASSES_INVULNERABILITY))) {
                return true;
            }
            return super.isInvulnerableTo(s);
        }

        @Override
        public boolean isPushable() {
            return this.kind() >= 4 && this.kind() != HERRERO_BOSS && super.isPushable();
        }

        @Override
        public boolean shouldRenderAtSqrDistance(double d) {
            return false;
        }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() {
            return NetworkHooks.getEntitySpawningPacket((Entity) this);
        }

        @Override
        public void addAdditionalSaveData(CompoundTag t) {
            super.addAdditionalSaveData(t);
            t.putInt("pk", this.kind());
            t.putBoolean("psit", this.sitting());
            if (this.home != null) {
                t.putLong("phome", this.home.asLong());
                t.putInt("phr", this.homeR);
            }
        }

        @Override
        public void readAdditionalSaveData(CompoundTag t) {
            super.readAdditionalSaveData(t);
            if (t.contains("pk")) {
                int k = t.getInt("pk");
                this.setKind(k);
                if (t.contains("psit")) {
                    this.setSitting(t.getBoolean("psit"));
                }
                if (t.contains("phome")) {
                    this.setHome(BlockPos.of(t.getLong("phome")), t.getInt("phr"));
                }
            }
        }

        @Override
        public InteractionResult mobInteract(Player p, InteractionHand h) {
            if (h != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            int k = this.kind();
            if (k > 3 && k != HERRERO_BOSS) {
                return InteractionResult.PASS;
            }
            if (p instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) p;
                if (k == 3) {
                    PoliciaBoss.openSell(sp, this);
                } else {
                    PoliciaBoss.openMain(sp, this);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        void ensureChair() {
            BlockPos seat = this.blockPosition();
            BlockState st = this.level().getBlockState(seat);
            if (st.getBlock() instanceof StairBlock) {
                return;
            }
            Direction d = Direction.fromYRot(this.getYRot());
            float yy = d.toYRot();
            this.setYRot(yy);
            this.setYHeadRot(yy);
            this.setYRot(yy);
            this.setPos(this.getX() + (double) d.getStepX() * 0.15, this.getY(), this.getZ() + (double) d.getStepZ() * 0.15);
            this.level().setBlock(seat, (BlockState) Blocks.OAK_STAIRS.defaultBlockState().setValue((Property) StairBlock.FACING, (Comparable) d.getOpposite()), 3);
            for (Direction s : new Direction[]{d.getClockWise(), d.getCounterClockWise()}) {
                BlockPos ap = seat.relative(s);
                if (!this.level().getBlockState(ap).isAir()) continue;
                this.level().setBlock(ap, (BlockState) Blocks.OAK_SLAB.defaultBlockState().setValue((Property) SlabBlock.TYPE, (Comparable) SlabType.TOP), 3);
            }
        }

        @Override
        public void tick() {
            super.tick();
            if (this.level().isClientSide) {
                return;
            }
            int k = this.kind();
            if ((k == 0 || k == 2) && this.tickCount % 20 == 5) {
                this.ensureChair();
            }
            if ((k == 0 || k == 2) && this.tickCount % 10 == 0) {
                String key = k == 0 ? "pol_cin0" : "pol_cin2";
                for (ServerPlayer sp : this.level().getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(7.0, 3.0, 7.0))) {
                    if (sp.getPersistentData().getBoolean(key) || CIN.containsKey(sp.getUUID())) continue;
                    sp.getPersistentData().putBoolean(key, true);
                    PoliciaBoss.startCin(sp, this);
                }
            }
        }
    }

    static class Cin {
        UUID boss;
        int t;
        double x;
        double y;
        double z;
        float y0;
        float p0;
        int kind;

        Cin() {
        }
    }

    static class Entry {
        final ItemStack give;
        final int price;

        Entry(ItemStack give, int price) {
            this.give = give;
            this.price = price;
        }
    }

    static class Sell extends ChestMenu {
        final ServerPlayer sp;
        final SimpleContainer box;

        Sell(int id, Inventory inv, SimpleContainer c, ServerPlayer sp) {
            super(MenuType.GENERIC_9x3, id, inv, (Container) c, 3);
            this.sp = sp;
            this.box = c;
        }

        @Override
        public void clicked(int slot, int btn, ClickType t, Player p) {
            if (slot >= 18 && slot < 27) {
                if (slot == 22 && t == ClickType.PICKUP) {
                    this.sell();
                }
                return;
            }
            if (t == ClickType.PICKUP_ALL || t == ClickType.QUICK_CRAFT) {
                return;
            }
            super.clicked(slot, btn, t, p);
        }

        void sell() {
            int n;
            double total = 0.0;
            for (int i = 0; i < 18; ++i) {
                ItemStack s = this.box.getItem(i);
                if (s.isEmpty()) continue;
                total += PoliciaBoss.value(s) * (double) s.getCount();
            }
            int em = (int) Math.floor(total);
            if (em < 1) {
                PoliciaMod.msg(this.sp, "Eso no vale nada");
                return;
            }
            for (int i = 0; i < 18; ++i) {
                this.box.setItem(i, ItemStack.EMPTY);
            }
            for (int left = em; left > 0; left -= n) {
                n = Math.min(64, left);
                PoliciaBoss.give(this.sp, new ItemStack((ItemLike) Items.EMERALD, n));
            }
            this.sp.level().playSound(null, this.sp.getX(), this.sp.getY(), this.sp.getZ(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0f, 1.0f);
            PoliciaMod.msg(this.sp, "Vendido por " + em + " esmeraldas");
            this.sp.getPersistentData().putInt("pol_xp", PoliciaMod.xp(this.sp) + Math.min(5, 1 + em / 10));
            PoliciaMod.sync(this.sp);
        }

        @Override
        public ItemStack quickMoveStack(Player p, int idx) {
            Slot s = this.slots.get(idx);
            if (s == null || !s.hasItem()) {
                return ItemStack.EMPTY;
            }
            ItemStack st = s.getItem();
            ItemStack copy = st.copy();
            if (idx < 27 ? !this.moveItemStackTo(st, 27, this.slots.size(), true) : !this.moveItemStackTo(st, 0, 18, false)) {
                return ItemStack.EMPTY;
            }
            if (st.isEmpty()) {
                s.set(ItemStack.EMPTY);
            } else {
                s.setChanged();
            }
            return copy;
        }

        @Override
        public void removed(Player p) {
            for (int i = 0; i < 18; ++i) {
                ItemStack s = this.box.getItem(i);
                if (!s.isEmpty() && p instanceof ServerPlayer) {
                    ServerPlayer sp2 = (ServerPlayer) p;
                    PoliciaBoss.give(sp2, s);
                }
                this.box.setItem(i, ItemStack.EMPTY);
            }
            super.removed(p);
        }
    }

    static class Shop extends ChestMenu {
        final ServerPlayer sp;
        final List<Entry> list;

        Shop(int id, Inventory inv, SimpleContainer c, ServerPlayer sp, List<Entry> list) {
            super(MenuType.GENERIC_9x6, id, inv, (Container) c, 6);
            this.sp = sp;
            this.list = list;
        }

        @Override
        public void clicked(int slot, int btn, ClickType t, Player p) {
            if (slot >= 0 && slot < 54) {
                if (t == ClickType.PICKUP && slot < this.list.size()) {
                    this.buy(this.list.get(slot));
                }
                return;
            }
            if (t == ClickType.QUICK_MOVE || t == ClickType.PICKUP_ALL || t == ClickType.QUICK_CRAFT) {
                return;
            }
            super.clicked(slot, btn, t, p);
        }

        void buy(Entry e) {
            if (PoliciaBoss.emeralds(this.sp) < e.price) {
                PoliciaMod.msg(this.sp, "Te faltan esmeraldas: cuesta " + e.price + " (tienes " + PoliciaBoss.emeralds(this.sp) + ")");
                return;
            }
            PoliciaBoss.takeEmeralds(this.sp, e.price);
            PoliciaBoss.give(this.sp, e.give.copy());
            this.sp.level().playSound(null, this.sp.getX(), this.sp.getY(), this.sp.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 1.0f, 1.0f);
            PoliciaMod.msg(this.sp, "Comprado por " + e.price + " esmeraldas");
        }

        @Override
        public ItemStack quickMoveStack(Player p, int i) {
            return ItemStack.EMPTY;
        }
    }

    static class Menu3 extends ChestMenu {
        final ServerPlayer sp;
        final BiConsumer<ServerPlayer, Integer> on;

        Menu3(int id, Inventory inv, SimpleContainer c, ServerPlayer sp, BiConsumer<ServerPlayer, Integer> on) {
            super(MenuType.GENERIC_9x3, id, inv, (Container) c, 3);
            this.sp = sp;
            this.on = on;
        }

        @Override
        public void clicked(int slot, int btn, ClickType t, Player p) {
            if (slot >= 0 && slot < 27) {
                if (t == ClickType.PICKUP) {
                    this.on.accept(this.sp, slot);
                }
                return;
            }
            if (t == ClickType.QUICK_MOVE || t == ClickType.PICKUP_ALL || t == ClickType.QUICK_CRAFT) {
                return;
            }
            super.clicked(slot, btn, t, p);
        }

        @Override
        public ItemStack quickMoveStack(Player p, int i) {
            return ItemStack.EMPTY;
        }
    }

    static class VestMat implements ArmorMaterial {
        static final VestMat INSTANCE = new VestMat();

        VestMat() {
        }

        @Override
        public int getDurabilityForType(ArmorItem.Type t) {
            return 70;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type t) {
            return t == ArmorItem.Type.CHESTPLATE ? 6 : 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 9;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(new ItemLike[]{Items.IRON_INGOT});
        }

        @Override
        public String getName() {
            return "policia:chaleco";
        }

        @Override
        public float getToughness() {
            return 1.0f;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0f;
        }
    }

    public static class SingleUse extends Item {
        public SingleUse(Item.Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level lv, Player p, InteractionHand h) {
            ItemStack st = p.getItemInHand(h);
            if (!lv.isClientSide && p instanceof ServerPlayer) {
                String id;
                ServerPlayer sp = (ServerPlayer) p;
                String string = id = st.hasTag() ? st.getTag().getString("pol_skill") : "";
                if (PoliciaBoss.run(sp, id) && !sp.getAbilities().instabuild) {
                    st.shrink(1);
                }
            }
            return InteractionResultHolder.sidedSuccess(st, lv.isClientSide);
        }
    }
}
