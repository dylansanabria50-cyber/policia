package com.example.policia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;

// Sistema de misiones del oficio Policia: bandidos, comisarios, furgonetas
// y el tablero de misiones del comisario.
public class PoliciaMision {
    public static final RegistryObject<EntityType<Pj>> PJ = PoliciaRefuerzo.ENTITIES.register("bandido", () -> EntityType.Builder.of(Pj::new, MobCategory.MISC).sized(0.6f, 1.95f).fireImmune().clientTrackingRange(10).build("policia:bandido"));
    public static final RegistryObject<EntityType<Pj>> COM = PoliciaRefuerzo.ENTITIES.register("comisario", () -> EntityType.Builder.of(Pj::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).build("policia:comisario"));
    public static final RegistryObject<EntityType<Pj>> VAN = PoliciaRefuerzo.ENTITIES.register("furgoneta", () -> EntityType.Builder.of(Pj::new, MobCategory.MISC).sized(2.0f, 1.8f).fireImmune().clientTrackingRange(10).build("policia:furgoneta"));
    static final String[] NAMES = new String[]{"Ladron", "Pandillero", "Pandillero arquero", "Lider de pandilla", "Jefe de banda", "Francotirador", "Contrabandista", "Saboteador", "Evadido", "Aldeano", "Comisario", "El Cerebro", "Mercader negro"};
    static final double[] HP = new double[]{16.0, 22.0, 18.0, 40.0, 120.0, 16.0, 30.0, 14.0, 20.0, 20.0, 40.0, 220.0, 40.0};
    static final double[] SPD = new double[]{0.23, 0.26, 0.24, 0.27, 0.27, 0.0, 0.25, 0.25, 0.25, 0.22, 0.0, 0.3, 0.0};
    static final double[] DMG = new double[]{2.0, 4.0, 2.0, 6.0, 9.0, 2.0, 3.0, 1.0, 3.0, 1.0, 1.0, 11.0, 1.0};
    static final int[] XP = new int[]{0, 8, 6, 10, 12, 10, 10, 10, 40, 70, 60, 30};
    static final int[] TIME = new int[]{0, 6000, 4800, 9600, 9600, 12000, 12000, 3000, 14400, 24000, 36000, 24000};
    static final String[] TITLE = new String[]{"", "Atrapar al fugitivo", "Patrulla de 3 puntos", "Rescate de aldeano", "Limpiar el campamento", "Recuperar evidencia", "Escolta de aldeano", "Desactivar bombas", "Cazar al jefe de banda", "El cerebro de la organizacion", "Rescate en el casino", "Asalto al campamento"};
    static final String[] DESC = new String[]{"", "Un delincuente huye con lo robado. Esposelo con la habilidad Esposas.", "Recorra los 3 puntos marcados antes de que se acabe el tiempo.", "Un aldeano fue secuestrado. Liberelo (clic derecho) y llevelo al comisario.", "Una pandilla acampa cerca. Eliminela entera, hay un francotirador.", "Traiga el papel de evidencia del cofre vigilado hasta el comisario.", "Lleve al aldeano a salvo hasta el punto marcado. Habra una emboscada.", "Un saboteador dejo 3 bombas. Desactivelas (romperlas) antes de que expire el tiempo.", "El jefe de la banda se esconde con su guardia. Cacelo.", "Siga las pistas hasta la guarida del cerebro de la organizacion y derrotelo. Se teletransporta e invoca refuerzos.", "Los ladrones del casino secuestraron a unos aldeanos. Baje al casino subterraneo (tres plantas) y libere a un rehen.", "Hay un campamento de bandidos o ladrones en la zona. Asaltelo y elimine a todos sus habitantes."};
    static final Item[] ICON = new Item[]{Items.PAPER, Items.CHAIN, Items.MAP, Items.EMERALD, Items.IRON_SWORD, Items.WRITABLE_BOOK, Items.SHIELD, Items.FLINT_AND_STEEL, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.DIAMOND, Items.CROSSBOW};
    static final String[] DIRS = new String[]{"este", "sureste", "sur", "suroeste", "oeste", "noroeste", "norte", "noreste"};
    static final Map<UUID, M> ACTIVE = new HashMap<UUID, M>();

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaMision::attrs);
        PoliciaBubble.init();
        MinecraftForge.EVENT_BUS.register((Object) new Ev());
    }

    static void attrs(EntityAttributeCreationEvent e) {
        AttributeSupplier s = Pj.attrs().build();
        e.put((EntityType) PJ.get(), s);
        e.put((EntityType) COM.get(), s);
        e.put((EntityType) VAN.get(), s);
    }

    static void dead(Pj p) {
        if (p.mission == null) {
            return;
        }
        M m = ACTIVE.get(p.mission);
        if (m == null) {
            return;
        }
        if (p.getUUID().equals(m.target)) {
            m.killed = true;
        }
        if (p.getUUID().equals(m.hostage)) {
            m.hostDead = true;
        }
    }

    static String dir(Vec3 from, BlockPos to) {
        double dx = (double) to.getX() - from.x;
        double dz = (double) to.getZ() - from.z;
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        int i = (int) Math.round((deg + 360.0) % 360.0 / 45.0) % 8;
        return DIRS[i] + ", " + (int) Math.sqrt(dx * dx + dz * dz) + " m";
    }

    static BlockPos at(ServerLevel sl, int x, int z) {
        if (!sl.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos g = new BlockPos(x, y, z);
        BlockState b = sl.getBlockState(g.below());
        if (b.isAir() || !b.getFluidState().isEmpty()) {
            return null;
        }
        return g;
    }

    static BlockPos site(ServerLevel sl, BlockPos c, int min, int max) {
        for (int i = 0; i < 24; ++i) {
            double ang = sl.random.nextDouble() * Math.PI * 2.0;
            double d = (double) min + sl.random.nextDouble() * (double) (max - min);
            BlockPos p = PoliciaMision.at(sl, c.getX() + (int) (Math.cos(ang) * d), c.getZ() + (int) (Math.sin(ang) * d));
            if (p == null) continue;
            return p;
        }
        return null;
    }

    static BlockPos around(ServerLevel sl, BlockPos c, int r) {
        for (int i = 0; i < 8; ++i) {
            BlockPos p = PoliciaMision.at(sl, c.getX() + sl.random.nextInt(r * 2 + 1) - r, c.getZ() + sl.random.nextInt(r * 2 + 1) - r);
            if (p == null || Math.abs(p.getY() - c.getY()) > 4) continue;
            return p;
        }
        return c;
    }

    static Pj spawn(ServerLevel sl, M m, int kind, BlockPos p) {
        Pj e = new Pj((EntityType<? extends Pj>) (kind == 6 ? (EntityType) VAN.get() : (EntityType) PJ.get()), (Level) sl);
        e.setKind(kind);
        e.moveTo((double) p.getX() + 0.5, p.getY(), (double) p.getZ() + 0.5, sl.random.nextFloat() * 360.0f, 0.0f);
        e.life = 24000;
        if (kind == 0 && m != null) {
            e.fleeT = 1200;
        }
        if (m != null) {
            e.mission = m.pl;
            m.ents.add(e.getUUID());
        }
        sl.addFreshEntity((Entity) e);
        return e;
    }

    static void put(ServerLevel sl, M m, BlockPos p, Block b) {
        sl.setBlock(p, b.defaultBlockState(), 3);
        m.blocks.put(p.immutable(), b);
    }

    static BlockPos tower(ServerLevel sl, M m, BlockPos g) {
        for (int i = 0; i < 4; ++i) {
            PoliciaMision.put(sl, m, g.above(i), Blocks.COBBLESTONE_WALL);
        }
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                PoliciaMision.put(sl, m, g.offset(dx, 4, dz), Blocks.OAK_PLANKS);
            }
        }
        return g.above(5);
    }

    static void camp(ServerLevel sl, M m, BlockPos s, int nPand, boolean leader, boolean boss) {
        BlockPos tg;
        for (int i = 0; i < nPand; ++i) {
            PoliciaMision.spawn(sl, m, 1, PoliciaMision.around(sl, s, 4)).restrictTo(s, 9);
        }
        PoliciaMision.spawn(sl, m, 2, PoliciaMision.around(sl, s, 4)).restrictTo(s, 9);
        if (leader) {
            PoliciaMision.spawn(sl, m, 3, PoliciaMision.around(sl, s, 3)).restrictTo(s, 9);
        }
        if (boss) {
            Pj b = PoliciaMision.spawn(sl, m, 4, s);
            b.restrictTo(s, 9);
            m.target = b.getUUID();
        }
        if ((tg = PoliciaMision.at(sl, s.getX() + 8, s.getZ() + 3)) != null) {
            BlockPos top = PoliciaMision.tower(sl, m, tg);
            PoliciaMision.spawn(sl, m, 5, top);
        }
    }

    static boolean setup(ServerLevel sl, ServerPlayer sp, M m) {
        BlockPos pp = sp.blockPosition();
        switch (m.type) {
            case 1: {
                BlockPos s = PoliciaMision.site(sl, pp, 30, 45);
                if (s == null) {
                    return false;
                }
                int[] ks = new int[]{0, 0, 6, 8};
                Pj e = PoliciaMision.spawn(sl, m, ks[sl.random.nextInt(ks.length)], s);
                m.target = e.getUUID();
                PoliciaMod.msg(sp, "Un " + NAMES[e.kind()].toLowerCase() + " huye con lo robado. Busquelo y esposelo.");
                return true;
            }
            case 2: {
                double a0 = sl.random.nextDouble() * Math.PI * 2.0;
                for (int i = 0; i < 3; ++i) {
                    double ang = a0 + (double) i * 2.0944;
                    double d = 28 + sl.random.nextInt(14);
                    BlockPos p = PoliciaMision.at(sl, pp.getX() + (int) (Math.cos(ang) * d), pp.getZ() + (int) (Math.sin(ang) * d));
                    if (p == null) {
                        p = PoliciaMision.site(sl, pp, 25, 40);
                    }
                    if (p == null) {
                        return false;
                    }
                    m.pts.add(p);
                }
                return true;
            }
            case 3: {
                BlockPos s = PoliciaMision.site(sl, pp, 38, 55);
                if (s == null) {
                    return false;
                }
                for (int i = 0; i < 2; ++i) {
                    PoliciaMision.spawn(sl, m, 1, PoliciaMision.around(sl, s, 4)).restrictTo(s, 8);
                }
                PoliciaMision.spawn(sl, m, 2, PoliciaMision.around(sl, s, 4)).restrictTo(s, 8);
                Pj h = PoliciaMision.spawn(sl, m, 9, s);
                h.setCaptive(true);
                h.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20000, 0, false, false));
                m.hostage = h.getUUID();
                m.dest = s;
                return true;
            }
            case 4: {
                BlockPos s = PoliciaMision.site(sl, pp, 40, 60);
                if (s == null) {
                    return false;
                }
                PoliciaMision.put(sl, m, s, Blocks.CAMPFIRE);
                PoliciaMision.camp(sl, m, s, 3, true, false);
                m.dest = s;
                return true;
            }
            case 5: {
                BlockPos s = PoliciaMision.site(sl, pp, 45, 65);
                if (s == null) {
                    return false;
                }
                PoliciaMision.put(sl, m, s, Blocks.CHEST);
                m.chest = s;
                ItemStack ev = new ItemStack((ItemLike) Items.PAPER);
                ev.setHoverName((Component) Component.literal("Evidencia"));
                ev.getOrCreateTag().putBoolean("pol_evid", true);
                BlockEntity blockEntity = sl.getBlockEntity(s);
                if (blockEntity instanceof ChestBlockEntity) {
                    ChestBlockEntity ce = (ChestBlockEntity) blockEntity;
                    ce.setItem(13, ev);
                }
                for (int i = 0; i < 2; ++i) {
                    PoliciaMision.spawn(sl, m, 1, PoliciaMision.around(sl, s, 4)).restrictTo(s, 8);
                }
                PoliciaMision.spawn(sl, m, 2, PoliciaMision.around(sl, s, 4)).restrictTo(s, 8);
                m.dest = s;
                return true;
            }
            case 6: {
                BlockPos s = PoliciaMision.site(sl, pp, 55, 70);
                if (s == null) {
                    return false;
                }
                Pj h = PoliciaMision.spawn(sl, m, 9, PoliciaMision.around(sl, pp, 2));
                h.owner = sp.getUUID();
                m.hostage = h.getUUID();
                m.dest = s;
                return true;
            }
            case 7: {
                int[][] off;
                BlockPos s = PoliciaMision.site(sl, pp, 35, 50);
                if (s == null) {
                    return false;
                }
                for (int[] o : off = new int[][]{{0, 0}, {9, 4}, {-6, 8}}) {
                    BlockPos b = PoliciaMision.at(sl, s.getX() + o[0], s.getZ() + o[1]);
                    if (b == null) {
                        b = s.offset(o[0] / 3, 0, o[1] / 3);
                    }
                    PoliciaMision.put(sl, m, b, Blocks.TNT);
                    m.bombs.add(b.immutable());
                }
                PoliciaMision.spawn(sl, m, 7, PoliciaMision.around(sl, s, 5));
                m.dest = s;
                return true;
            }
            case 8: {
                BlockPos s = PoliciaMision.site(sl, pp, 55, 70);
                if (s == null) {
                    return false;
                }
                PoliciaMision.camp(sl, m, s, 2, true, true);
                m.dest = s;
                return true;
            }
            case 11: {
                BlockPos s = PoliciaMision.campOf(sl, pp);
                if (s == null) {
                    return false;
                }
                m.dest = s;
                return true;
            }
        }
        return false;
    }

    static BlockPos campOf(ServerLevel sl, BlockPos from) {
        PoliciaStruct.Sites st = PoliciaStruct.Sites.get(sl);
        BlockPos best = null;
        double bd = 1440000.0;
        for (int k = 3; k <= 6; ++k) {
            BlockPos p = st.nearest(k, from);
            if (p == null || !(p.distSqr((Vec3i) from) < bd)) continue;
            bd = p.distSqr((Vec3i) from);
            best = p;
        }
        return best;
    }

    static boolean setup9(ServerLevel sl, ServerPlayer sp, M m) {
        BlockPos pp = sp.blockPosition();
        BlockPos c1 = PoliciaMision.site(sl, pp, 25, 38);
        BlockPos c2 = PoliciaMision.site(sl, pp, 40, 55);
        BlockPos s = PoliciaMision.site(sl, pp, 55, 70);
        if (c1 == null || c2 == null || s == null) {
            return false;
        }
        m.pts.add(c1);
        m.pts.add(c2);
        PoliciaMision.camp(sl, m, s, 3, true, false);
        PoliciaMision.spawn(sl, m, 3, PoliciaMision.around(sl, s, 4)).restrictTo(s, 9);
        PoliciaMision.spawn(sl, m, 2, PoliciaMision.around(sl, s, 4)).restrictTo(s, 9);
        BlockPos tg = PoliciaMision.at(sl, s.getX() - 8, s.getZ() - 3);
        if (tg != null) {
            PoliciaMision.spawn(sl, m, 5, PoliciaMision.tower(sl, m, tg));
        }
        Pj b = PoliciaMision.spawn(sl, m, 11, s);
        b.restrictTo(s, 10);
        m.target = b.getUUID();
        m.dest = s;
        return true;
    }

    static void start(ServerPlayer sp, int type, BlockPos origin) {
        if (ACTIVE.containsKey(sp.getUUID())) {
            PoliciaMod.msg(sp, "Ya tiene una mision en curso");
            return;
        }
        ServerLevel sl = sp.serverLevel();
        M m = new M();
        m.pl = sp.getUUID();
        m.type = type;
        m.dim = sl.dimension();
        m.origin = origin;
        m.start = sl.getGameTime();
        m.end = m.start + (long) TIME[type];
        ACTIVE.put(m.pl, m);
        if (!(type != 9 ? (type != 10 ? PoliciaMision.setup(sl, sp, m) : PoliciaBoard.setup10(sl, sp, m)) : PoliciaMision.setup9(sl, sp, m))) {
            PoliciaMision.cleanup(sl, m);
            PoliciaMod.msg(sp, "No encuentro un lugar para la mision, intente en otra zona");
            return;
        }
        PoliciaBoard.narrate(sp, type, m);
    }

    static void cleanup(ServerLevel sl, M m) {
        ACTIVE.remove(m.pl);
        ServerPlayer cp = sl.getServer().getPlayerList().getPlayer(m.pl);
        if (cp != null) {
            PoliciaBoard.removeCompass(cp);
        }
        for (UUID uUID : m.ents) {
            Entity e = sl.getEntity(uUID);
            if (e == null) continue;
            sl.sendParticles((ParticleOptions) ParticleTypes.POOF, e.getX(), e.getY() + 1.0, e.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
            e.discard();
        }
        for (Map.Entry entry : m.blocks.entrySet()) {
            if (!sl.getBlockState((BlockPos) entry.getKey()).is((Block) entry.getValue())) continue;
            sl.setBlock((BlockPos) entry.getKey(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    static void fail(ServerPlayer sp, ServerLevel sl, M m, String why) {
        if (m.type == 7) {
            for (BlockPos b : m.bombs) {
                sl.explode(null, (double) b.getX() + 0.5, (double) b.getY() + 0.5, (double) b.getZ() + 0.5, 2.5f, Level.ExplosionInteraction.NONE);
            }
        }
        PoliciaMision.cleanup(sl, m);
        if (sp != null) {
            sp.sendSystemMessage((Component) Component.literal("[Comisario] Mision fallida: " + why));
            PoliciaMod.msg(sp, "Mision fallida: " + why);
        }
    }

    static void give(ServerPlayer sp, ItemStack s) {
        if (!sp.getInventory().add(s)) {
            sp.drop(s, false);
        }
    }

    static void finish(ServerPlayer sp, ServerLevel sl, M m, double scale) {
        int xp = (int) Math.round((double) XP[m.type] * scale);
        int em = Math.max(1, xp / 2);
        sp.getPersistentData().putInt("pol_xp", PoliciaMod.xp((Player) sp) + xp);
        sp.getPersistentData().putInt("pol_mdone", sp.getPersistentData().getInt("pol_mdone") + 1);
        PoliciaMision.give(sp, new ItemStack((ItemLike) Items.EMERALD, em));
        PoliciaGrupo.share(sp, xp, em);
        String extra = "";
        if (m.type == 4 || m.type == 3) {
            PoliciaMision.give(sp, new ItemStack((ItemLike) Items.GOLDEN_APPLE));
            extra = ", 1 manzana dorada";
        }
        if (m.type == 9) {
            sp.getPersistentData().putBoolean("pol_cerebro", true);
            sp.getPersistentData().putInt("pol_un", PoliciaMod.un((Player) sp) | 0x200);
            PoliciaMision.give(sp, new ItemStack((ItemLike) Items.DIAMOND, 5));
            PoliciaMision.give(sp, new ItemStack((ItemLike) Items.NETHERITE_INGOT));
            extra = ", 5 diamantes, un lingote de netherita y la habilidad ESCUDO DE BURBUJA";
        }
        if (m.type == 11) {
            PoliciaMision.give(sp, new ItemStack((ItemLike) Items.DIAMOND, 2));
            extra = ", 2 diamantes";
        }
        if (m.type == 8) {
            sp.getPersistentData().putInt("pol_bdone", sp.getPersistentData().getInt("pol_bdone") + 1);
            PoliciaMision.give(sp, new ItemStack((ItemLike) Items.DIAMOND, 3));
            PoliciaMision.give(sp, new ItemStack((ItemLike) Items.TOTEM_OF_UNDYING));
            extra = ", 3 diamantes y un totem";
        }
        PoliciaMod.sync(sp);
        sp.sendSystemMessage((Component) Component.literal("[Comisario] Mision cumplida: " + TITLE[m.type] + ". Recompensa: " + xp + " XP, " + em + " esmeraldas" + extra + "."));
        PoliciaMod.msg(sp, "Mision cumplida +" + xp + " XP");
        sl.playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        PoliciaMision.cleanup(sl, m);
    }

    static void free(ServerPlayer sp, Pj h) {
        M m = ACTIVE.get(sp.getUUID());
        if (h.getPersistentData().getBoolean("pol_cas")) {
            h.setCaptive(false);
            h.removeEffect(MobEffects.WEAKNESS);
            h.owner = sp.getUUID();
            h.getPersistentData().putBoolean("pol_cas", false);
            sp.sendSystemMessage((Component) Component.literal("[Comisario] Rehen liberado."));
            if (m != null && m.type == 10) {
                PoliciaMision.finish(sp, sp.serverLevel(), m, 1.0);
            }
            return;
        }
        if (m == null || m.type != 3 || !h.getUUID().equals(m.hostage)) {
            PoliciaMod.msg(sp, "No es el aldeano de su mision");
            return;
        }
        h.setCaptive(false);
        h.removeEffect(MobEffects.WEAKNESS);
        h.owner = sp.getUUID();
        m.phase = 1;
        sp.sendSystemMessage((Component) Component.literal("[Comisario] Aldeano liberado. Llevelo de vuelta al comisario."));
    }

    static boolean hasEv(Player p) {
        for (ItemStack s : p.getInventory().items) {
            if (s.isEmpty() || !s.hasTag() || !s.getTag().getBoolean("pol_evid")) continue;
            return true;
        }
        return false;
    }

    static void takeEv(Player p) {
        for (ItemStack s : p.getInventory().items) {
            if (s.isEmpty() || !s.hasTag() || !s.getTag().getBoolean("pol_evid")) continue;
            s.setCount(0);
        }
    }

    static int alive(ServerLevel sl, M m) {
        int n = 0;
        for (UUID u : m.ents) {
            Pj p;
            Entity e = sl.getEntity(u);
            if (!(e instanceof Pj) || !(p = (Pj) e).isAlive() || !p.hostile()) continue;
            ++n;
        }
        return n;
    }

    static void col(ServerLevel sl, BlockPos p) {
        for (int y = 0; y < 14; y += 2) {
            sl.sendParticles((ParticleOptions) ParticleTypes.END_ROD, (double) p.getX() + 0.5, (double) (p.getY() + y), (double) p.getZ() + 0.5, 1, 0.1, 0.0, 0.1, 0.0);
        }
    }

    static void tick9(ServerPlayer sp, ServerLevel sl, M m, Vec3 pos, long t, long now) {
        if (m.killed) {
            PoliciaMision.finish(sp, sl, m, 1.0);
            return;
        }
        if (m.phase < 2) {
            double dz;
            double dx;
            BlockPos p = m.pts.get(m.phase);
            if (t % 5L == 0L) {
                PoliciaMision.col(sl, p);
            }
            if ((dx = (double) p.getX() + 0.5 - pos.x) * dx + (dz = (double) p.getZ() + 0.5 - pos.z) * dz < 25.0 && Math.abs((double) p.getY() - pos.y) < 10.0) {
                ++m.phase;
                sl.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
                PoliciaMision.spawn(sl, m, 1, PoliciaMision.around(sl, p, 6));
                PoliciaMision.spawn(sl, m, 1, PoliciaMision.around(sl, p, 6));
                PoliciaMision.spawn(sl, m, 3, PoliciaMision.around(sl, p, 6));
                if (m.phase == 1) {
                    sp.sendSystemMessage((Component) Component.literal("[Comisario] Documento falso hallado. Hay otra pista mas adentro."));
                } else {
                    sp.sendSystemMessage((Component) Component.literal("[Comisario] Ya sabemos donde se esconde el cerebro. Vaya a la guarida, con cuidado."));
                }
            } else if (t % 40L == 0L) {
                PoliciaMod.msg(sp, "Pista " + (m.phase + 1) + "/2: siga la brujula");
            }
        } else if (t % 40L == 0L) {
            PoliciaMod.msg(sp, "Guarida del cerebro: siga la brujula");
        }
    }

    static void tickM(MinecraftServer srv, M m) {
        ServerLevel sl = srv.getLevel(m.dim);
        if (sl == null) {
            ACTIVE.remove(m.pl);
            return;
        }
        ServerPlayer sp = srv.getPlayerList().getPlayer(m.pl);
        if (sp == null || !sp.isAlive() || sp.level() != sl) {
            PoliciaMision.cleanup(sl, m);
            if (sp != null) {
                PoliciaMod.msg(sp, "Mision cancelada");
            }
            return;
        }
        long now = sl.getGameTime();
        long t = now - m.start;
        if (now > m.end) {
            PoliciaMision.fail(sp, sl, m, "se acabo el tiempo");
            return;
        }
        Vec3 pos = sp.position();
        boolean hint = false;
        if (m.type == 9) {
            PoliciaMision.tick9(sp, sl, m, pos, t, now);
            return;
        }
        switch (m.type) {
            case 1: {
                Entity e = sl.getEntity(m.target);
                if (m.killed) {
                    PoliciaMision.finish(sp, sl, m, 0.5);
                    return;
                }
                if (e instanceof Pj) {
                    Pj pj = (Pj) e;
                    m.n = 0;
                    if (pj.hasEffect((MobEffect) PoliciaExtra.CUFF.get())) {
                        PoliciaMision.finish(sp, sl, m, 1.0);
                        return;
                    }
                    if (t % 10L == 0L) {
                        sl.sendParticles((ParticleOptions) ParticleTypes.CRIT, pj.getX(), pj.getY() + 0.2, pj.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
                    }
                    if (t % 160L == 0L) {
                        pj.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0, false, false));
                    }
                    if (!hint) break;
                    PoliciaMod.msg(sp, "Sospechoso: " + PoliciaMision.dir(pos, pj.blockPosition()) + " - " + (m.end - now) / 20L + " s");
                    break;
                }
                if (++m.n <= 600) break;
                PoliciaMision.fail(sp, sl, m, "el sospechoso escapo");
                return;
            }
            case 2: {
                BlockPos p = m.pts.get(m.phase);
                double dx = (double) p.getX() + 0.5 - pos.x;
                double dz = (double) p.getZ() + 0.5 - pos.z;
                if (t % 5L == 0L) {
                    PoliciaMision.col(sl, p);
                }
                if (dx * dx + dz * dz < 25.0 && Math.abs((double) p.getY() - pos.y) < 10.0) {
                    ++m.phase;
                    sl.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
                    if (m.phase >= 3) {
                        PoliciaMision.finish(sp, sl, m, 1.0);
                        return;
                    }
                    PoliciaMod.msg(sp, "Punto " + m.phase + "/3 revisado");
                    if (sl.random.nextInt(100) >= 45) break;
                    PoliciaMision.spawn(sl, m, 1, PoliciaMision.around(sl, p, 6));
                    PoliciaMision.spawn(sl, m, 2, PoliciaMision.around(sl, p, 6));
                    break;
                }
                if (!hint) break;
                PoliciaMod.msg(sp, "Punto " + (m.phase + 1) + "/3: " + PoliciaMision.dir(pos, p) + " - " + (m.end - now) / 20L + " s");
                break;
            }
            case 3: {
                double dz;
                double dx;
                Entity h = sl.getEntity(m.hostage);
                if (m.hostDead || h == null && t > 100L) {
                    PoliciaMision.fail(sp, sl, m, "el aldeano murio");
                    return;
                }
                if (m.phase == 0) {
                    if (!hint) break;
                    PoliciaMod.msg(sp, "Rehen: " + PoliciaMision.dir(pos, m.dest) + " - " + (m.end - now) / 20L + " s");
                    break;
                }
                if (h == null) break;
                if (t % 10L == 0L) {
                    PoliciaMision.col(sl, m.origin);
                }
                if ((dx = h.getX() - (double) m.origin.getX()) * dx + (dz = h.getZ() - (double) m.origin.getZ()) * dz < 36.0 && sp.distanceToSqr(h) < 200.0) {
                    PoliciaMision.finish(sp, sl, m, 1.0);
                    return;
                }
                if (!hint) break;
                PoliciaMod.msg(sp, "Lleve al aldeano al comisario: " + PoliciaMision.dir(pos, m.origin));
                break;
            }
            case 4:
            case 8: {
                if (t % 20L == 0L && t > 60L) {
                    boolean done;
                    boolean bl = m.type == 8 ? m.killed : (done = PoliciaMision.alive(sl, m) == 0);
                    if (done) {
                        PoliciaMision.finish(sp, sl, m, 1.0);
                        return;
                    }
                }
                if (!hint) break;
                PoliciaMod.msg(sp, (m.type == 8 ? "Jefe de banda: " : "Campamento: ") + PoliciaMision.dir(pos, m.dest) + " - " + (m.end - now) / 20L + " s");
                break;
            }
            case 5: {
                double dz;
                double dx;
                if (m.phase == 0) {
                    if (PoliciaMision.hasEv((Player) sp)) {
                        m.phase = 1;
                        sp.sendSystemMessage((Component) Component.literal("[Comisario] Tiene la evidencia. Llevela al comisario."));
                        break;
                    }
                    if (!hint) break;
                    PoliciaMod.msg(sp, "Cofre con evidencia: " + PoliciaMision.dir(pos, m.dest) + " - " + (m.end - now) / 20L + " s");
                    break;
                }
                if (!PoliciaMision.hasEv((Player) sp)) {
                    m.phase = 0;
                }
                if ((dx = pos.x - (double) m.origin.getX()) * dx + (dz = pos.z - (double) m.origin.getZ()) * dz < 49.0) {
                    PoliciaMision.takeEv((Player) sp);
                    PoliciaMision.finish(sp, sl, m, 1.0);
                    return;
                }
                if (!hint) break;
                PoliciaMod.msg(sp, "Lleve la evidencia al comisario: " + PoliciaMision.dir(pos, m.origin));
                break;
            }
            case 6: {
                double dz;
                double dx;
                Entity h = sl.getEntity(m.hostage);
                if (m.hostDead || h == null && t > 100L) {
                    PoliciaMision.fail(sp, sl, m, "el aldeano murio");
                    return;
                }
                if (h == null) break;
                if (t % 10L == 0L) {
                    PoliciaMision.col(sl, m.dest);
                }
                double ox = h.getX() - (double) m.origin.getX();
                double oz = h.getZ() - (double) m.origin.getZ();
                if (m.phase == 0 && ox * ox + oz * oz > 900.0) {
                    m.phase = 1;
                    BlockPos a = PoliciaMision.around(sl, h.blockPosition().offset(12, 0, 12), 6);
                    PoliciaMision.spawn(sl, m, 1, a);
                    PoliciaMision.spawn(sl, m, 1, PoliciaMision.around(sl, a, 4));
                    PoliciaMision.spawn(sl, m, 2, PoliciaMision.around(sl, a, 4));
                    PoliciaMod.msg(sp, "Emboscada!");
                }
                if ((dx = h.getX() - (double) m.dest.getX()) * dx + (dz = h.getZ() - (double) m.dest.getZ()) * dz < 36.0 && sp.distanceToSqr(h) < 200.0) {
                    PoliciaMision.finish(sp, sl, m, 1.0);
                    return;
                }
                if (!hint) break;
                PoliciaMod.msg(sp, "Destino: " + PoliciaMision.dir(pos, m.dest) + " - " + (m.end - now) / 20L + " s");
                break;
            }
            case 11: {
                if (t % 20L != 0L || t <= 40L || !(pos.distanceToSqr(Vec3.atCenterOf((Vec3i) m.dest)) < 2304.0)) break;
                boolean any = false;
                for (Pj e : sl.getEntitiesOfClass(Pj.class, new AABB(m.dest).inflate(24.0, 12.0, 24.0))) {
                    if (!e.isAlive() || !e.getPersistentData().getBoolean("pol_camp")) continue;
                    any = true;
                    break;
                }
                if (any) break;
                PoliciaMision.finish(sp, sl, m, 1.0);
                return;
            }
            case 7: {
                if (t % 10L == 0L) {
                    for (BlockPos b2 : m.bombs) {
                        sl.sendParticles((ParticleOptions) ParticleTypes.SMOKE, (double) b2.getX() + 0.5, (double) b2.getY() + 1.2, (double) b2.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.01);
                    }
                }
                if (t % 20L == 0L) {
                    m.bombs.removeIf(b -> !sl.getBlockState(b).is(Blocks.TNT));
                    if (m.bombs.isEmpty()) {
                        PoliciaMision.finish(sp, sl, m, 1.0);
                        return;
                    }
                }
                if (!hint) break;
                PoliciaMod.msg(sp, "Bombas: " + m.bombs.size() + " - " + (m.end - now) / 20L + " s - " + PoliciaMision.dir(pos, m.bombs.get(0)));
                break;
            }
        }
    }

    static int[] offered(Pj com, ServerLevel sl) {
        Random r = new Random(com.getUUID().getLeastSignificantBits() ^ sl.getGameTime() / 24000L * 7919L);
        ArrayList<Integer> pool = new ArrayList<Integer>();
        for (int i = 1; i <= 7; ++i) {
            pool.add(i);
        }
        if (PoliciaMision.campOf(sl, com.blockPosition()) != null) {
            pool.add(11);
        }
        Collections.shuffle(pool, r);
        return new int[]{(Integer) pool.get(0), (Integer) pool.get(1), (Integer) pool.get(2)};
    }

    static ItemStack named(Item it, String name, String... lore) {
        ItemStack s = new ItemStack((ItemLike) it);
        s.setHoverName((Component) Component.literal(name));
        ListTag l = new ListTag();
        for (String x : lore) {
            l.add(StringTag.valueOf(Component.Serializer.toJson((Component) Component.literal(x))));
        }
        s.getOrCreateTagElement("display").put("Lore", (Tag) l);
        return s;
    }

    static ItemStack missionItem(int type) {
        int xp = XP[type];
        return PoliciaMision.named(ICON[type], TITLE[type], DESC[type], "Tiempo: " + TIME[type] / 1200 + " min", "Recompensa: " + xp + " XP y " + Math.max(1, xp / 2) + " esmeraldas" + (type == 8 ? ", diamantes y un totem" : (type == 4 || type == 3 ? " y una manzana dorada" : "")));
    }

    static void openBoard(ServerPlayer sp, Pj com) {
        if (!"policia".equals(PoliciaMod.job((Player) sp))) {
            sp.sendSystemMessage((Component) Component.literal("[Comisario] Solo atiendo a policias. Vuelva con su placa."));
            return;
        }
        PoliciaBoard.open(sp, com);
    }

    static void comisarios(ServerPlayer sp, ServerLevel sl) {
        Bells bells = Bells.get(sl);
        List<BlockPos> found = sl.getPoiManager().findAll(h -> h.is(PoiTypes.MEETING), bp -> true, sp.blockPosition(), 48, PoiManager.Occupancy.ANY).collect(Collectors.toList());
        for (BlockPos bell : found) {
            if (bells.set.contains(bell.asLong())) continue;
            bells.set.add(bell.asLong());
            bells.setDirty();
            AABB box = new AABB(bell).inflate(64.0);
            boolean has = false;
            for (Pj p : sl.getEntitiesOfClass(Pj.class, box)) {
                if (p.kind() != 10) continue;
                has = true;
            }
            if (has) continue;
            BlockPos spot = null;
            for (int i = 0; i < 20 && spot == null; ++i) {
                BlockPos p = PoliciaMision.at(sl, bell.getX() + sl.random.nextInt(9) - 4, bell.getZ() + sl.random.nextInt(9) - 4);
                if (p == null || Math.abs(p.getY() - bell.getY()) > 3) continue;
                spot = p;
            }
            if (spot == null) {
                spot = bell.above();
            }
            Pj c = new Pj((EntityType<? extends Pj>) ((EntityType) COM.get()), (Level) sl);
            c.setKind(10);
            c.moveTo((double) spot.getX() + 0.5, spot.getY(), (double) spot.getZ() + 0.5, sl.random.nextFloat() * 360.0f, 0.0f);
            sl.addFreshEntity((Entity) c);
        }
    }

    public static class Ev {
        @SubscribeEvent
        public void mTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
                return;
            }
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) {
                return;
            }
            for (M m : new ArrayList<M>(ACTIVE.values())) {
                try {
                    PoliciaMision.tickM(srv, m);
                } catch (Exception ex) {
                    ACTIVE.remove(m.pl);
                }
            }
        }

        @SubscribeEvent
        public void mPtick(TickEvent.PlayerTickEvent e) {
            Player player;
            if (e.phase != TickEvent.Phase.END || !((player = e.player) instanceof ServerPlayer)) {
                return;
            }
            ServerPlayer sp = (ServerPlayer) player;
            ServerLevel sl = sp.serverLevel();
            if (sp.tickCount % 200 == 7) {
                try {
                    PoliciaMision.comisarios(sp, sl);
                } catch (Exception exception) {
                    // empty catch block
                }
            }
            if (sp.tickCount % 1200 == 600 && "policia".equals(PoliciaMod.job((Player) sp)) && sl.dimension() == Level.OVERWORLD && sl.isDay() && !ACTIVE.containsKey(sp.getUUID()) && sl.random.nextInt(100) < 30) {
                for (Pj p : sl.getEntitiesOfClass(Pj.class, sp.getBoundingBox().inflate(80.0))) {
                    if (!p.hostile()) continue;
                    return;
                }
                BlockPos s = PoliciaMision.site(sl, sp.blockPosition(), 35, 50);
                if (s == null) {
                    return;
                }
                for (int i = 0; i < 3; ++i) {
                    Pj p = PoliciaMision.spawn(sl, null, 1, PoliciaMision.around(sl, s, 4));
                    p.life = 3600;
                }
                Pj a = PoliciaMision.spawn(sl, null, 2, PoliciaMision.around(sl, s, 4));
                a.life = 3600;
                PoliciaMod.msg(sp, "Una pandilla merodea cerca...");
            }
        }

        @SubscribeEvent
        public void mBrk(BlockEvent.BreakEvent e) {
            Player player = e.getPlayer();
            if (!(player instanceof ServerPlayer)) {
                return;
            }
            ServerPlayer sp = (ServerPlayer) player;
            if (!(e.getLevel() instanceof ServerLevel)) {
                return;
            }
            ServerLevel sl = (ServerLevel) e.getLevel();
            M m = ACTIVE.get(sp.getUUID());
            if (m == null || m.type != 7) {
                return;
            }
            BlockPos p = e.getPos();
            if (!m.bombs.contains(p)) {
                return;
            }
            e.setCanceled(true);
            sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            m.bombs.remove(p);
            sl.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
            PoliciaMod.msg(sp, "Bomba desactivada (" + (3 - m.bombs.size()) + "/3)");
            if (m.bombs.isEmpty()) {
                PoliciaMision.finish(sp, sl, m, 1.0);
            }
        }

        @SubscribeEvent
        public void mStop(ServerStoppingEvent e) {
            for (M m : new ArrayList<M>(ACTIVE.values())) {
                ServerLevel sl = e.getServer().getLevel(m.dim);
                if (sl == null) continue;
                PoliciaMision.cleanup(sl, m);
            }
            ACTIVE.clear();
        }
    }

    public static class Pj extends PathfinderMob implements RangedAttackMob {
        static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Pj.class, (EntityDataSerializer) EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> CAP = SynchedEntityData.defineId(Pj.class, (EntityDataSerializer) EntityDataSerializers.BOOLEAN);
        public UUID owner;
        public UUID mission;
        public int life = 6000;
        final ServerBossEvent bar = new ServerBossEvent((Component) Component.literal("Jefe de banda"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        ItemStack stolen = ItemStack.EMPTY;
        int fleeT = 0;
        int stealCd = 100;
        int hits = 0;
        boolean sneaking = false;
        int tpc = 0;
        int stage = 0;

        public Pj(EntityType<? extends Pj> t, Level l) {
            super(t, l);
            this.setPersistenceRequired();
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.FOLLOW_RANGE, 40.0);
        }

        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(KIND, 1);
            this.entityData.define(CAP, false);
        }

        public int kind() {
            return (Integer) this.entityData.get(KIND);
        }

        public boolean captive() {
            return (Boolean) this.entityData.get(CAP);
        }

        public void setCaptive(boolean c) {
            this.entityData.set(CAP, c);
            this.setInvisible(c);
        }

        public void setKind(int k) {
            this.entityData.set(KIND, k);
            if (k < 0 || k > 12) {
                return;
            }
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HP[k]);
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPD[k]);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DMG[k]);
            if (k == 4) {
                this.getAttribute(Attributes.ARMOR).setBaseValue(8.0);
                this.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.6);
            } else if (k == 3) {
                this.getAttribute(Attributes.ARMOR).setBaseValue(4.0);
            }
            this.setHealth((float) HP[k]);
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike) (k == 2 ? Items.BOW : (k == 5 ? Items.CROSSBOW : (k == 10 || k == 12 ? Items.WOODEN_HOE : (k == 4 || k == 11 ? Items.NETHERITE_SWORD : (k == 9 ? Items.WOODEN_HOE : Items.STONE_SWORD)))))));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            this.setCustomName((Component) Component.literal(NAMES[k]));
            this.setCustomNameVisible(k == 3 || k == 4 || k == 10 || k == 11 || k == 12);
            if (k == 11) {
                this.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
                this.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.8);
                this.bar.setName((Component) Component.literal("El Cerebro"));
                this.bar.setColor(BossEvent.BossBarColor.PURPLE);
            }
            if (k == 4) {
                this.bar.setName((Component) Component.literal("Jefe de banda"));
            }
        }

        boolean hostile() {
            int k = this.kind();
            return k >= 1 && k <= 5 || k == 11;
        }

        boolean thief() {
            return this.kind() == 0 && !this.captive();
        }

        boolean flees() {
            int k = this.kind();
            return k == 0 && (!this.stolen.isEmpty() || this.fleeT > 0) || k == 6 || k == 7 || k == 8;
        }

        void steal(Player p) {
            ArrayList<Integer> slots = new ArrayList<Integer>();
            for (int i = 0; i < 36; ++i) {
                if (p.getInventory().getItem(i).isEmpty()) continue;
                slots.add(i);
            }
            if (slots.isEmpty()) {
                this.stealCd = 600;
                return;
            }
            ItemStack it = p.getInventory().getItem(((Integer) slots.get(this.random.nextInt(slots.size()))).intValue());
            this.stolen = it.split(Math.min(it.getCount(), 16));
            p.getInventory().setChanged();
            this.getPersistentData().put("pol_stolen", (Tag) this.stolen.save(new CompoundTag()));
            this.fleeT = 1200;
            this.hits = 0;
            this.playSound(SoundEvents.VILLAGER_NO, 1.0f, 0.7f);
            if (p instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) p;
                sp.sendSystemMessage((Component) Component.literal("Un ladron te robo " + this.stolen.getCount() + " x " + this.stolen.getHoverName().getString() + "! Pegale 2 veces para que lo suelte"));
            }
        }

        void deposit() {
            BlockPos c = this.getRestrictCenter();
            for (BlockPos bp : BlockPos.betweenClosed(c.offset(-9, -3, -9), c.offset(9, 4, 9))) {
                ItemStack left;
                BlockEntity blockEntity = this.level().getBlockEntity(bp);
                if (!(blockEntity instanceof ChestBlockEntity)) continue;
                ChestBlockEntity ch = (ChestBlockEntity) blockEntity;
                this.stolen = left = HopperBlockEntity.addItem(null, (Container) ch, (ItemStack) this.stolen, null);
                if (!left.isEmpty()) continue;
                this.getPersistentData().remove("pol_stolen");
                this.fleeT = 0;
                this.stealCd = 1200;
                return;
            }
            if (!this.stolen.isEmpty()) {
                this.getPersistentData().put("pol_stolen", (Tag) this.stolen.save(new CompoundTag()));
            }
        }

        void dropStolen() {
            if (this.stolen.isEmpty()) {
                return;
            }
            this.spawnAtLocation(this.stolen);
            this.stolen = ItemStack.EMPTY;
            this.getPersistentData().remove("pol_stolen");
            this.hits = 0;
            this.fleeT = 300;
            this.stealCd = 1200;
            this.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.3f);
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) level;
                sl.sendParticles((ParticleOptions) ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            }
        }

        public boolean hurt(DamageSource s, float a) {
            boolean r = super.hurt(s, a);
            if (r && !this.level().isClientSide && this.thief()) {
                this.fleeT = Math.max(this.fleeT, 100);
                if (!this.stolen.isEmpty() && s.getEntity() instanceof Player && ++this.hits >= 2) {
                    this.dropStolen();
                }
            }
            return r;
        }

        boolean melee() {
            int k = this.kind();
            return k == 1 || k == 3 || k == 4 || k == 11;
        }

        void cerebro() {
            Level level = this.level();
            if (!(level instanceof ServerLevel)) {
                return;
            }
            ServerLevel sl = (ServerLevel) level;
            LivingEntity t = this.getTarget();
            if (t == null) {
                return;
            }
            if (++this.tpc >= 100) {
                this.tpc = 0;
                if (this.distanceToSqr((Entity) t) > 25.0 && this.random.nextInt(2) == 0) {
                    sl.sendParticles((ParticleOptions) ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.3, 0.6, 0.3, 0.3);
                    double ang = this.random.nextDouble() * Math.PI * 2.0;
                    if (this.randomTeleport(t.getX() + Math.cos(ang) * 3.0, t.getY(), t.getZ() + Math.sin(ang) * 3.0, false)) {
                        sl.sendParticles((ParticleOptions) ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.3, 0.6, 0.3, 0.3);
                        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                }
            }
            float f = this.getHealth() / this.getMaxHealth();
            if (this.stage == 0 && f < 0.66f || this.stage == 1 && f < 0.33f) {
                ++this.stage;
                M mm = this.mission == null ? null : ACTIVE.get(this.mission);
                for (int i = 0; i < 2; ++i) {
                    Pj g1 = PoliciaMision.spawn(sl, mm, 1, PoliciaMision.around(sl, this.blockPosition(), 4));
                    g1.life = 3000;
                }
                Pj g2 = PoliciaMision.spawn(sl, mm, 2, PoliciaMision.around(sl, this.blockPosition(), 4));
                g2.life = 3000;
                this.playSound(SoundEvents.WITHER_SPAWN, 1.0f, 1.0f);
            }
        }

        Player ownerPlayer() {
            return this.owner == null ? null : this.level().getPlayerByUUID(this.owner);
        }

        static boolean police(LivingEntity le) {
            Player p;
            return le instanceof Player && "policia".equals(PoliciaMod.job(p = (Player) le));
        }

        protected void registerGoals() {
            this.goalSelector.addGoal(0, (Goal) new FloatGoal((Mob) this));
            this.goalSelector.addGoal(1, (Goal) new AvoidEntityGoal<Player>((PathfinderMob) this, Player.class, 18.0f, 1.0, 1.35) {

                public boolean canUse() {
                    return Pj.this.flees() && !Pj.this.captive() && super.canUse();
                }
            });
            this.goalSelector.addGoal(3, new Goal() {
                {
                    this.setFlags(EnumSet.of(Goal.Flag.MOVE));
                }

                boolean far() {
                    return Pj.this.hasRestriction() && Pj.this.distanceToSqr(Vec3.atCenterOf((Vec3i) Pj.this.getRestrictCenter())) > 144.0;
                }

                boolean safe() {
                    Player np = Pj.this.level().getNearestPlayer((Entity) Pj.this, 14.0);
                    return np == null;
                }

                public boolean canUse() {
                    return Pj.this.thief() && !Pj.this.stolen.isEmpty() && Pj.this.hasRestriction() && this.far() && this.safe();
                }

                public boolean canContinueToUse() {
                    return Pj.this.thief() && !Pj.this.stolen.isEmpty() && Pj.this.hasRestriction() && this.safe() && Pj.this.distanceToSqr(Vec3.atCenterOf((Vec3i) Pj.this.getRestrictCenter())) > 9.0;
                }

                public void tick() {
                    if (Pj.this.tickCount % 10 == 0) {
                        BlockPos c = Pj.this.getRestrictCenter();
                        Pj.this.getNavigation().moveTo((double) c.getX() + 0.5, (double) c.getY(), (double) c.getZ() + 0.5, 1.0);
                    }
                }

                public void stop() {
                    if (Pj.this.hasRestriction() && !Pj.this.stolen.isEmpty() && Pj.this.distanceToSqr(Vec3.atCenterOf((Vec3i) Pj.this.getRestrictCenter())) <= 9.0) {
                        Pj.this.deposit();
                    }
                }
            });
            this.goalSelector.addGoal(2, new Goal() {
                Player tgt;
                int t = 0;

                {
                    this.setFlags(EnumSet.of(Goal.Flag.MOVE));
                }

                public boolean canUse() {
                    if (!Pj.this.thief() || Pj.this.captive() || !Pj.this.stolen.isEmpty() || Pj.this.fleeT > 0 || Pj.this.stealCd > 0) {
                        return false;
                    }
                    if (Pj.this.getRandom().nextInt(10) != 0) {
                        return false;
                    }
                    Player best = null;
                    double bd = 324.0;
                    for (Player p : Pj.this.level().players()) {
                        double d;
                        if (p.isCreative() || p.isSpectator() || !p.isAlive() || "ladron".equals(PoliciaMod.job(p)) || !((d = p.distanceToSqr((Entity) Pj.this)) < bd)) continue;
                        bd = d;
                        best = p;
                    }
                    if (best == null) {
                        return false;
                    }
                    if (!Pj.this.level().getEntitiesOfClass(Pj.class, Pj.this.getBoundingBox().inflate(24.0), x -> x != Pj.this && x.sneaking).isEmpty()) {
                        return false;
                    }
                    this.tgt = best;
                    return true;
                }

                public boolean canContinueToUse() {
                    return this.tgt != null && this.tgt.isAlive() && Pj.this.stolen.isEmpty() && Pj.this.fleeT <= 0 && this.t < 700 && Pj.this.distanceToSqr((Entity) this.tgt) < 900.0;
                }

                public void start() {
                    Pj.this.sneaking = true;
                    this.t = 0;
                }

                public void stop() {
                    Pj.this.sneaking = false;
                    this.tgt = null;
                    Pj.this.stealCd = 200;
                    Pj.this.getNavigation().stop();
                }

                public void tick() {
                    ++this.t;
                    Vec3 look = this.tgt.getLookAngle();
                    double dx = Pj.this.getX() - this.tgt.getX();
                    double dz = Pj.this.getZ() - this.tgt.getZ();
                    double len = Math.sqrt(dx * dx + dz * dz);
                    double hl = Math.max(1.0E-4, Math.sqrt(look.x * look.x + look.z * look.z));
                    boolean seen = len > 1.0E-4 && (look.x * dx + look.z * dz) / (len * hl) > 0.2;
                    double d2 = Pj.this.distanceToSqr((Entity) this.tgt);
                    if (!seen && d2 < 5.76) {
                        Pj.this.steal(this.tgt);
                        return;
                    }
                    if (seen && d2 < 64.0) {
                        Pj.this.getNavigation().stop();
                        Pj.this.getLookControl().setLookAt((Entity) this.tgt, 30.0f, 30.0f);
                    } else if (this.t % 5 == 0) {
                        Pj.this.getNavigation().moveTo((Entity) this.tgt, seen ? 0.8 : 1.1);
                    }
                }
            });
            this.goalSelector.addGoal(2, (Goal) new RangedAttackGoal(this, 1.0, 30, 16.0f) {

                public boolean canUse() {
                    return Pj.this.kind() == 2 && super.canUse();
                }
            });
            this.goalSelector.addGoal(2, (Goal) new RangedAttackGoal(this, 1.0, 50, 40.0f) {

                public boolean canUse() {
                    return Pj.this.kind() == 5 && super.canUse();
                }
            });
            this.goalSelector.addGoal(3, (Goal) new MeleeAttackGoal(this, 1.15, false) {

                public boolean canUse() {
                    return Pj.this.melee() && super.canUse();
                }
            });
            this.goalSelector.addGoal(4, new Goal() {
                {
                    this.setFlags(EnumSet.of(Goal.Flag.MOVE));
                }

                public boolean canUse() {
                    return Pj.this.kind() == 9 && !Pj.this.captive() && Pj.this.ownerPlayer() != null;
                }

                public void tick() {
                    Player o = Pj.this.ownerPlayer();
                    if (o == null) {
                        return;
                    }
                    if (Pj.this.distanceToSqr((Entity) o) > 9.0) {
                        Pj.this.getNavigation().moveTo((Entity) o, 1.2);
                    }
                }
            });
            this.goalSelector.addGoal(5, (Goal) new MoveTowardsRestrictionGoal((PathfinderMob) this, 1.0));
            this.goalSelector.addGoal(6, (Goal) new WaterAvoidingRandomStrollGoal(this, 0.8) {

                public boolean canUse() {
                    int k = Pj.this.kind();
                    return k != 5 && k != 9 && k != 10 && k != 12 && super.canUse();
                }
            });
            this.goalSelector.addGoal(7, (Goal) new LookAtPlayerGoal((Mob) this, Player.class, 8.0f));
            this.goalSelector.addGoal(8, (Goal) new RandomLookAroundGoal((Mob) this));
            this.targetSelector.addGoal(1, (Goal) new HurtByTargetGoal(this, new Class[0]) {

                public boolean canUse() {
                    return Pj.this.hostile() && super.canUse();
                }
            });
            this.targetSelector.addGoal(2, (Goal) new NearestAttackableTargetGoal<Player>(this, Player.class, 10, true, false, le -> this.hostile() && Pj.police(le)));
            this.targetSelector.addGoal(3, (Goal) new NearestAttackableTargetGoal<PoliciaBoss.BossNpc>(this, PoliciaBoss.BossNpc.class, 10, true, false, le -> {
                PoliciaBoss.BossNpc bn;
                return this.hostile() && le instanceof PoliciaBoss.BossNpc && ((bn = (PoliciaBoss.BossNpc) ((Object) le)).kind() == 4 || bn.kind() == 5);
            }));
        }

        public void setTarget(LivingEntity t) {
            if (t != null && !this.hostile()) {
                return;
            }
            super.setTarget(t);
        }

        public void performRangedAttack(LivingEntity t, float f) {
            Arrow a = new Arrow(this.level(), (LivingEntity) this);
            double dx = t.getX() - this.getX();
            double dz = t.getZ() - this.getZ();
            double dy = t.getY(0.3333) - a.getY();
            double h = Math.sqrt(dx * dx + dz * dz);
            boolean sn = this.kind() == 5;
            a.shoot(dx, dy + h * 0.2, dz, sn ? 2.4f : 1.6f, sn ? 0.8f : 10.0f);
            a.setBaseDamage(sn ? 5.0 : 2.0);
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            this.level().addFreshEntity((Entity) a);
            this.playSound(SoundEvents.ARROW_SHOOT, 1.0f, 1.0f / (this.random.nextFloat() * 0.4f + 0.8f));
        }

        protected InteractionResult mobInteract(Player p, InteractionHand h) {
            if (h != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (p instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) p;
                if (this.kind() == 10) {
                    PoliciaMision.openBoard(sp, this);
                } else if (this.kind() == 12) {
                    PoliciaLadron.market(sp, this);
                } else if (this.kind() == 9 && this.captive()) {
                    PoliciaMision.free(sp, this);
                }
            }
            return this.kind() == 10 || this.kind() == 12 || this.kind() == 9 && this.captive() ? InteractionResult.sidedSuccess(this.level().isClientSide) : InteractionResult.PASS;
        }

        public boolean isInvulnerableTo(DamageSource s) {
            if (!(this.kind() != 10 && this.kind() != 12 || s.is(DamageTypeTags.BYPASSES_INVULNERABILITY))) {
                return true;
            }
            return super.isInvulnerableTo(s);
        }

        public boolean canBeLeashed(Player player) {
            return this.kind() != 10 && this.kind() != 12 && !this.captive() && super.canBeLeashed(player);
        }

        public boolean removeWhenFarAway(double d) {
            return false;
        }

        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide) {
                return;
            }
            if (this.kind() != 10 && this.kind() != 12 && !this.getPersistentData().getBoolean("pol_cas") && --this.life <= 0) {
                ((ServerLevel) this.level()).sendParticles((ParticleOptions) ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                this.discard();
                return;
            }
            if (this.stealCd > 0) {
                --this.stealCd;
            }
            if (this.fleeT > 0) {
                --this.fleeT;
            }
            if (this.stolen.isEmpty() && this.getPersistentData().contains("pol_stolen")) {
                this.stolen = ItemStack.of((CompoundTag) this.getPersistentData().getCompound("pol_stolen"));
            }
            if (this.kind() == 4 || this.kind() == 11) {
                this.bar.setProgress(Mth.clamp((float) (this.getHealth() / this.getMaxHealth()), 0.0f, 1.0f));
            }
            if (this.kind() == 11) {
                this.cerebro();
            }
        }

        public void startSeenByPlayer(ServerPlayer p) {
            super.startSeenByPlayer(p);
            if (this.kind() == 4 || this.kind() == 11) {
                this.bar.addPlayer(p);
            }
        }

        public void stopSeenByPlayer(ServerPlayer p) {
            super.stopSeenByPlayer(p);
            this.bar.removePlayer(p);
        }

        public void remove(Entity.RemovalReason r) {
            this.bar.removeAllPlayers();
            super.remove(r);
        }

        public void die(DamageSource s) {
            super.die(s);
            if (!this.level().isClientSide) {
                PoliciaMision.dead(this);
            }
        }

        protected void dropCustomDeathLoot(DamageSource s, int looting, boolean hit) {
            super.dropCustomDeathLoot(s, looting, hit);
            int k = this.kind();
            if (k == 0 || k == 6 || k == 8) {
                this.spawnAtLocation(new ItemStack((ItemLike) Items.EMERALD, 2 + this.random.nextInt(3)));
            } else if (k >= 1 && k <= 5 && this.random.nextInt(3) == 0) {
                this.spawnAtLocation(new ItemStack((ItemLike) Items.EMERALD));
            }
            if (k == 4) {
                this.spawnAtLocation(new ItemStack((ItemLike) Items.GOLD_INGOT, 4));
                this.spawnAtLocation(new ItemStack((ItemLike) Items.EMERALD, 8));
                ItemStack sw = new ItemStack((ItemLike) Items.DIAMOND_SWORD);
                sw.setHoverName((Component) Component.literal("Espada del Jefe de Banda"));
                sw.enchant(Enchantments.SHARPNESS, 4);
                sw.enchant(Enchantments.UNBREAKING, 3);
                sw.enchant(Enchantments.FIRE_ASPECT, 2);
                this.spawnAtLocation(sw);
            }
            this.dropStolen();
        }

        public void addAdditionalSaveData(CompoundTag t) {
            super.addAdditionalSaveData(t);
            t.putInt("pk", this.kind());
            t.putBoolean("pcap", this.captive());
        }

        public void readAdditionalSaveData(CompoundTag t) {
            super.readAdditionalSaveData(t);
            if (t.contains("pk")) {
                this.setKind(t.getInt("pk"));
            }
            if (t.contains("pcap")) {
                this.setCaptive(t.getBoolean("pcap"));
            }
        }
    }

    static class M {
        UUID pl;
        int type;
        int phase;
        int n;
        long start;
        long end;
        ResourceKey<Level> dim;
        BlockPos origin;
        BlockPos dest;
        BlockPos chest;
        List<UUID> ents = new ArrayList<UUID>();
        List<BlockPos> pts = new ArrayList<BlockPos>();
        List<BlockPos> bombs = new ArrayList<BlockPos>();
        Map<BlockPos, Block> blocks = new LinkedHashMap<BlockPos, Block>();
        UUID target;
        UUID hostage;
        boolean killed;
        boolean hostDead;

        M() {
        }
    }

    public static class Bells extends SavedData {
        final Set<Long> set = new HashSet<Long>();

        static Bells get(ServerLevel sl) {
            return (Bells) sl.getDataStorage().computeIfAbsent(Bells::load, Bells::new, "policia_bells");
        }

        static Bells load(CompoundTag t) {
            Bells b = new Bells();
            for (long l : t.getLongArray("b")) {
                b.set.add(l);
            }
            return b;
        }

        public CompoundTag save(CompoundTag t) {
            long[] a = new long[this.set.size()];
            int i = 0;
            for (long l : this.set) {
                a[i++] = l;
            }
            t.putLongArray("b", a);
            return t;
        }
    }

    static class Board extends ChestMenu {
        final int[] ts;
        final BlockPos origin;
        final boolean bossOk;
        final boolean cerOk;

        Board(int id, Inventory inv, SimpleContainer c, int[] ts, BlockPos origin, boolean bossOk, boolean cerOk) {
            super(MenuType.GENERIC_9x1, id, inv, (Container) c, 1);
            this.ts = ts;
            this.origin = origin;
            this.bossOk = bossOk;
            this.cerOk = cerOk;
        }

        public void clicked(int slot, int btn, ClickType ct, Player pl) {
            if (slot < 0 || slot >= 9 || !(pl instanceof ServerPlayer)) {
                return;
            }
            ServerPlayer sp = (ServerPlayer) pl;
            if (slot == 1 || slot == 3 || slot == 5) {
                int i = slot == 1 ? 0 : (slot == 3 ? 1 : 2);
                sp.closeContainer();
                PoliciaMision.start(sp, this.ts[i], this.origin);
            } else if (slot == 4 && this.cerOk) {
                sp.closeContainer();
                PoliciaMision.start(sp, 9, this.origin);
            } else if (slot == 7 && this.bossOk) {
                sp.closeContainer();
                PoliciaMision.start(sp, 8, this.origin);
            } else if (slot == 8) {
                M m = ACTIVE.get(sp.getUUID());
                sp.closeContainer();
                if (m != null) {
                    PoliciaMision.cleanup(sp.serverLevel(), m);
                    PoliciaMod.msg(sp, "Mision cancelada");
                }
            }
        }

        public ItemStack quickMoveStack(Player p, int i) {
            return ItemStack.EMPTY;
        }
    }
}
