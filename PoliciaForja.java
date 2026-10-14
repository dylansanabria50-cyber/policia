package com.example.policia;

import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// Herrero: encantamientos propios (Filo de fragua y Temple, solo los pone un
// herrero en un yunque), fundición de chatarra en el alto horno y la firma
// del herrero en todo lo que forja.
public class PoliciaForja {
    public static final DeferredRegister<Enchantment> ENCH = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "policia");
    public static final RegistryObject<Enchantment> FILO = ENCH.register("filo_de_fragua", () -> new Own(EnchantmentCategory.WEAPON, 2, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final RegistryObject<Enchantment> TEMPLE = ENCH.register("temple", () -> new Own(EnchantmentCategory.ARMOR, 3, new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));

    static void init(IEventBus modBus) {
        ENCH.register(modBus);
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaForja());
    }

    // Encantamientos de tesoro: no salen en la mesa, ni en libros, ni en
    // tradeos. Solo los aplica el herrero.
    public static class Own extends Enchantment {
        final int max;

        Own(EnchantmentCategory cat, int max, EquipmentSlot[] slots) {
            super(Enchantment.Rarity.VERY_RARE, cat, slots);
            this.max = max;
        }

        @Override
        public int getMaxLevel() {
            return this.max;
        }

        @Override
        public boolean isTreasureOnly() {
            return true;
        }

        @Override
        public boolean isTradeable() {
            return false;
        }

        @Override
        public boolean isDiscoverable() {
            return false;
        }

        @Override
        public boolean isAllowedOnBooks() {
            return false;
        }

        @Override
        public boolean canApplyAtEnchantingTable(ItemStack stack) {
            return false;
        }
    }

    // ---------------- Efectos ----------------
    @SubscribeEvent
    public void hurt(LivingHurtEvent e) {
        LivingEntity t = e.getEntity();
        if (e.getSource().getDirectEntity() instanceof LivingEntity at && e.getSource().getEntity() == at) {
            int filo = EnchantmentHelper.getItemEnchantmentLevel(FILO.get(), at.getMainHandItem());
            if (filo > 0) {
                t.setSecondsOnFire(3 * filo);
                e.setAmount(e.getAmount() + filo);
            }
            int temple = 0;
            for (ItemStack a : t.getArmorSlots()) {
                temple += EnchantmentHelper.getItemEnchantmentLevel(TEMPLE.get(), a);
            }
            if (temple > 0 && at != t && t.level() instanceof ServerLevel sl) {
                float back = e.getAmount() * Math.min(0.5f, 0.06f * temple);
                if (back > 0.1f) {
                    at.hurt(sl.damageSources().thorns(t), back);
                }
            }
            PoliciaForja.signUse(at.getMainHandItem(), at, 20);
        }
        if (e.getEntity() instanceof Player) {
            for (ItemStack a : t.getArmorSlots()) {
                PoliciaForja.signUse(a, t, 25);
            }
        }
    }

    @SubscribeEvent
    public void mined(BlockEvent.BreakEvent e) {
        PoliciaForja.signUse(e.getPlayer().getMainHandItem(), e.getPlayer(), 60);
    }

    // ---------------- Firma ----------------
    static boolean signable(ItemStack s) {
        return s.isDamageableItem() && s.getMaxStackSize() == 1;
    }

    static void sign(ItemStack s, ServerPlayer smith) {
        if (!PoliciaForja.signable(s) || s.hasTag() && s.getTag().hasUUID("her_sign")) {
            return;
        }
        CompoundTag t = s.getOrCreateTag();
        t.putUUID("her_sign", smith.getUUID());
        t.putString("her_signn", smith.getName().getString());
        CompoundTag disp = s.getOrCreateTagElement("display");
        ListTag lore = disp.getList("Lore", 8);
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Forjado por " + smith.getName().getString()).withStyle(st -> st.withColor(0xD4A84B).withItalic(true)))));
        disp.put("Lore", lore);
    }

    // Cuando otro jugador usa algo firmado, el herrero gana XP de a poco.
    static void signUse(ItemStack s, LivingEntity user, int every) {
        if (!(user instanceof ServerPlayer u) || s.isEmpty() || !s.hasTag() || !s.getTag().hasUUID("her_sign")) {
            return;
        }
        UUID smith = s.getTag().getUUID("her_sign");
        if (smith.equals(u.getUUID())) {
            return;
        }
        ServerPlayer sm = u.server.getPlayerList().getPlayer(smith);
        if (sm == null) {
            return;
        }
        CompoundTag d = sm.getPersistentData();
        int c = d.getInt("sign_cnt") + 1;
        if (c >= every) {
            c = 0;
            d.putInt("pol_xp", PoliciaMod.xp(sm) + 1);
            PoliciaMod.msg(sm, "§6" + u.getName().getString() + " está usando algo que forjaste: +1 XP");
            PoliciaMod.sync(sm);
        }
        d.putInt("sign_cnt", c);
    }

    @SubscribeEvent
    public void crafted(PlayerEvent.ItemCraftedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && PoliciaHerrero.isHerrero(p)) {
            PoliciaForja.sign(e.getCrafting(), p);
        }
    }

    // ---------------- Yunque de encantamientos y fundición ----------------
    @SubscribeEvent
    public void click(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown() || !PoliciaHerrero.isHerrero(p)) {
            return;
        }
        var b = e.getLevel().getBlockState(e.getPos()).getBlock();
        if (b instanceof AnvilBlock) {
            ItemStack it = p.getMainHandItem();
            if (it.isEmpty()) {
                return;
            }
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            PoliciaForja.enchantMenu(p);
        } else if (b instanceof BlastFurnaceBlock) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            SimpleContainer c = new SimpleContainer(27);
            for (int i = 18; i < 27; ++i) {
                c.setItem(i, PoliciaBoss.pane());
            }
            c.setItem(22, PoliciaBoss.named(Items.LAVA_BUCKET, "FUNDIR", "Poné arriba herramientas y armaduras viejas", "Devuelve lingotes según cuánto les quede"));
            p.openMenu((MenuProvider) new SimpleMenuProvider((id, inv, pl) -> new Scrap(id, inv, c, p), Component.literal("Fundición de chatarra")));
        }
    }

    static boolean weapon(ItemStack s) {
        return FILO.get().canEnchant(s);
    }

    static boolean armor(ItemStack s) {
        return s.getItem() instanceof ArmorItem;
    }

    static void enchantMenu(ServerPlayer sp) {
        ItemStack it = sp.getMainHandItem();
        int filo = EnchantmentHelper.getItemEnchantmentLevel(FILO.get(), it);
        int temple = EnchantmentHelper.getItemEnchantmentLevel(TEMPLE.get(), it);
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; ++i) {
            c.setItem(i, PoliciaBoss.pane());
        }
        ItemStack show = it.copy();
        show.setCount(1);
        c.setItem(13, show);
        c.setItem(11, PoliciaForja.weapon(it) && filo < 2 ? PoliciaBoss.named(Items.BLAZE_POWDER, "Filo de fragua " + (filo == 0 ? "I" : "II"), "Prende fuego al golpear y suma daño", "Cuesta: 3 lingotes de oro + 1 polvo de blaze + 5 niveles") : PoliciaBoss.named(Items.BARRIER, "Filo de fragua", PoliciaForja.weapon(it) ? "Ya está al máximo" : "Solo para armas"));
        c.setItem(15, PoliciaForja.armor(it) && temple < 3 ? PoliciaBoss.named(Items.IRON_CHESTPLATE, "Temple " + (temple == 0 ? "I" : (temple == 1 ? "II" : "III")), "Devuelve parte del daño a quien te golpea", "Cuesta: 4 lingotes de hierro + 5 niveles") : PoliciaBoss.named(Items.BARRIER, "Temple", PoliciaForja.armor(it) ? "Ya está al máximo" : "Solo para armaduras"));
        PoliciaBoss.openMenu3(sp, "Yunque del herrero", c, (p, slot) -> {
            if (slot != 11 && slot != 15) return;
            p.closeContainer();
            ItemStack cur = p.getMainHandItem();
            boolean f = slot == 11;
            Enchantment en = f ? FILO.get() : TEMPLE.get();
            int lv = EnchantmentHelper.getItemEnchantmentLevel(en, cur);
            if (f ? !PoliciaForja.weapon(cur) : !PoliciaForja.armor(cur)) {
                PoliciaMod.msg(p, f ? "Sostené un arma" : "Sostené una pieza de armadura");
                return;
            }
            if (lv >= en.getMaxLevel()) {
                PoliciaMod.msg(p, "Ya está al máximo");
                return;
            }
            if (!p.isCreative()) {
                if (p.experienceLevel < 5) {
                    PoliciaMod.msg(p, "Necesitás 5 niveles de experiencia");
                    return;
                }
                Item mat = f ? Items.GOLD_INGOT : Items.IRON_INGOT;
                int need = f ? 3 : 4;
                if (PoliciaEncargo.have(p, mat) < need || f && PoliciaEncargo.have(p, Items.BLAZE_POWDER) < 1) {
                    PoliciaMod.msg(p, f ? "Faltan materiales: 3 lingotes de oro y 1 polvo de blaze" : "Faltan materiales: 4 lingotes de hierro");
                    return;
                }
                PoliciaForja.take(p, mat, need);
                if (f) PoliciaForja.take(p, Items.BLAZE_POWDER, 1);
                p.giveExperienceLevels(-5);
            }
            Map<Enchantment, Integer> m = EnchantmentHelper.getEnchantments(cur);
            m.put(en, lv + 1);
            EnchantmentHelper.setEnchantments(m, cur);
            PoliciaForja.sign(cur, p);
            p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + 3);
            PoliciaHerrero.anim(p, 2);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0f, 0.9f);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
            PoliciaMod.msg(p, "§6" + en.getFullname(lv + 1).getString() + " aplicado (+3 XP)");
            PoliciaMod.sync(p);
        });
    }

    static void take(ServerPlayer p, Item it, int n) {
        for (ItemStack s : p.getInventory().items) {
            if (n <= 0) return;
            if (!s.is(it)) continue;
            int t = Math.min(n, s.getCount());
            s.shrink(t);
            n -= t;
        }
    }

    // Cuánto material lleva cada pieza (como en la mesa de crafteo).
    static int units(String id) {
        if (id.endsWith("_chestplate")) return 8;
        if (id.endsWith("_leggings")) return 7;
        if (id.endsWith("_helmet")) return 5;
        if (id.endsWith("_boots")) return 4;
        if (id.endsWith("_pickaxe") || id.endsWith("_axe")) return 3;
        if (id.endsWith("_sword") || id.endsWith("_hoe")) return 2;
        if (id.endsWith("_shovel")) return 1;
        if (id.endsWith("_horse_armor")) return 6;
        if (id.equals("shears") || id.equals("flint_and_steel")) return 1;
        return 0;
    }

    static ItemStack[] melt(ItemStack s) {
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        int u = PoliciaForja.units(id);
        if (u == 0) return null;
        double left = s.isDamageableItem() ? 1.0 - (double) s.getDamageValue() / Math.max(1, s.getMaxDamage()) : 1.0;
        int n = Math.max(1, (int) Math.floor(u * left * 0.75));
        if (id.startsWith("netherite_")) {
            return new ItemStack[]{new ItemStack(Items.NETHERITE_SCRAP, Math.max(1, (int) Math.round(2 * left))), new ItemStack(Items.DIAMOND, n)};
        }
        if (id.startsWith("diamond_")) return new ItemStack[]{new ItemStack(Items.DIAMOND, n)};
        if (id.startsWith("golden_")) return new ItemStack[]{new ItemStack(Items.GOLD_INGOT, n)};
        if (id.startsWith("iron_") || id.equals("shears") || id.equals("flint_and_steel")) return new ItemStack[]{new ItemStack(Items.IRON_INGOT, n)};
        if (id.startsWith("chainmail_")) return new ItemStack[]{new ItemStack(Items.IRON_NUGGET, n * 3)};
        return null;
    }

    static class Scrap extends ChestMenu {
        final ServerPlayer sp;
        final SimpleContainer box;

        Scrap(int id, Inventory inv, SimpleContainer c, ServerPlayer sp) {
            super(MenuType.GENERIC_9x3, id, inv, (Container) c, 3);
            this.sp = sp;
            this.box = c;
        }

        @Override
        public void clicked(int slot, int btn, ClickType t, Player p) {
            if (slot >= 18 && slot < 27) {
                if (slot == 22 && t == ClickType.PICKUP) {
                    this.melt();
                }
                return;
            }
            if (t == ClickType.PICKUP_ALL || t == ClickType.QUICK_CRAFT) {
                return;
            }
            super.clicked(slot, btn, t, p);
        }

        void melt() {
            int done = 0;
            for (int i = 0; i < 18; ++i) {
                ItemStack s = this.box.getItem(i);
                if (s.isEmpty()) continue;
                ItemStack[] out = PoliciaForja.melt(s);
                if (out == null) continue;
                for (ItemStack o : out) {
                    PoliciaBoss.give(this.sp, o);
                }
                this.box.setItem(i, ItemStack.EMPTY);
                ++done;
            }
            if (done == 0) {
                PoliciaMod.msg(this.sp, "Nada para fundir: poné herramientas o armaduras de metal");
                return;
            }
            this.sp.level().playSound(null, this.sp.getX(), this.sp.getY(), this.sp.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.8f);
            this.sp.getPersistentData().putInt("pol_xp", PoliciaMod.xp(this.sp) + done);
            PoliciaMod.msg(this.sp, "§6Fundiste " + done + " piezas de chatarra (+" + done + " XP)");
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
                if (!s.isEmpty()) {
                    PoliciaBoss.give(this.sp, s);
                }
                this.box.setItem(i, ItemStack.EMPTY);
            }
            super.removed(p);
        }
    }
}
