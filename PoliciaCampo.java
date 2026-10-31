package com.example.policia;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// Etapa 12: granja, establo y pesca.
// - Flor de luna: cultivo que solo crece de noche y brilla al madurar. Las
//   semillas salen a veces al cortar pasto de noche. La flor se come: vision
//   nocturna y regeneracion.
// - Regadera: moja la tierra 3 x 3 y a veces hace crecer los cultivos; se
//   llena en el agua.
// - Establo: darle de comer a tu caballo y cepillarlo sube el vinculo; con
//   mas vinculo corre y salta mas.
// - Pesca: a veces sale un pez de la comarca (9 especies por rareza, con
//   tamano en cm); se guarda tu coleccion y tus records.
public class PoliciaCampo {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PoliciaMod.ID);
    public static final RegistryObject<Block> FLOR_LUNA_BLOQUE = BLOCKS.register("flor_de_luna", () -> new FlorLuna(BlockBehaviour.Properties.copy(Blocks.WHEAT).lightLevel(s -> s.getValue(FlorLuna.EDAD) >= 3 ? 10 : s.getValue(FlorLuna.EDAD) == 2 ? 4 : 0)));
    public static final RegistryObject<Item> SEMILLAS_LUNA = PoliciaShield.ITEMS.register("semillas_de_luna", () -> new ItemNameBlockItem(FLOR_LUNA_BLOQUE.get(), new Item.Properties()));
    public static final RegistryObject<Item> FLOR_LUNA = PoliciaShield.ITEMS.register("flor_de_luna_cosechada", () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationMod(0.4f).alwaysEat()
            .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0), 1.0f).effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 200, 0), 1.0f).build())));
    public static final RegistryObject<Item> REGADERA = PoliciaShield.ITEMS.register("regadera", () -> new Regadera(new Item.Properties().durability(48)));
    public static final RegistryObject<Item> CEPILLO = PoliciaShield.ITEMS.register("cepillo", () -> new Cepillo(new Item.Properties().durability(128)));
    static final UUID MOD_VEL = UUID.fromString("b1d2e3f4-0a1b-4c2d-8e3f-4a5b6c7d8e01");
    static final UUID MOD_SALTO = UUID.fromString("b1d2e3f4-0a1b-4c2d-8e3f-4a5b6c7d8e02");

    static void init() {
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.register(new EventosCampo());
    }

    // ---------------- Flor de luna ----------------
    public static class FlorLuna extends CropBlock {
        public static final IntegerProperty EDAD = BlockStateProperties.AGE_3;

        public FlorLuna(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        public IntegerProperty getAgeProperty() {
            return EDAD;
        }

        @Override
        public int getMaxAge() {
            return 3;
        }

        @Override
        protected ItemLike getBaseSeedId() {
            return SEMILLAS_LUNA.get();
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(EDAD);
        }

        // Solo crece de noche.
        @Override
        public void randomTick(BlockState st, ServerLevel sl, BlockPos pos, RandomSource r) {
            if (sl.isDay()) {
                return;
            }
            super.randomTick(st, sl, pos, r);
            if (st.getValue(EDAD) >= 2 && r.nextInt(3) == 0) {
                sl.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.0);
            }
        }
    }

    // ---------------- Regadera ----------------
    public static class Regadera extends Item {
        Regadera(Item.Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
            ItemStack st = p.getItemInHand(h);
            BlockHitResult hit = getPlayerPOVHitResult(l, p, ClipContext.Fluid.SOURCE_ONLY);
            if (hit.getType() == HitResult.Type.BLOCK && l.getFluidState(hit.getBlockPos()).is(Fluids.WATER)) {
                if (!l.isClientSide) {
                    st.setDamageValue(0);
                    l.playSound(null, p.blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0f, 1.2f);
                }
                return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
            }
            return InteractionResultHolder.pass(st);
        }

        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            Level l = ctx.getLevel();
            ItemStack st = ctx.getItemInHand();
            if (st.getDamageValue() >= st.getMaxDamage() - 1) {
                if (ctx.getPlayer() != null && !l.isClientSide) {
                    ctx.getPlayer().displayClientMessage(Component.literal("§7La regadera está vacía: llenala en el agua"), true);
                }
                return InteractionResult.FAIL;
            }
            if (!(l instanceof ServerLevel sl)) {
                return InteractionResult.SUCCESS;
            }
            BlockPos c = ctx.getClickedPos();
            int mojados = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 0; dy++) {
                        BlockPos q = c.offset(dx, dy, dz);
                        BlockState s = sl.getBlockState(q);
                        if (s.getBlock() instanceof FarmBlock) {
                            sl.setBlock(q, s.setValue(FarmBlock.MOISTURE, 7), 2);
                            mojados++;
                            BlockPos up = q.above();
                            BlockState cs = sl.getBlockState(up);
                            IntegerProperty edad = edad(cs);
                            if (cs.getBlock() instanceof CropBlock && edad != null && sl.random.nextInt(4) == 0 && cs.getValue(edad) < java.util.Collections.max(edad.getPossibleValues())) {
                                sl.setBlock(up, cs.setValue(edad, cs.getValue(edad) + 1), 2);
                                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, up.getX() + 0.5, up.getY() + 0.5, up.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
                            }
                            sl.sendParticles(ParticleTypes.SPLASH, q.getX() + 0.5, q.getY() + 1.1, q.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0.1);
                        }
                    }
                }
            }
            if (mojados == 0) {
                return InteractionResult.PASS;
            }
            sl.playSound(null, c, SoundEvents.WEATHER_RAIN, SoundSource.PLAYERS, 0.4f, 1.6f);
            st.setDamageValue(Math.min(st.getMaxDamage() - 1, st.getDamageValue() + 1));
            return InteractionResult.CONSUME;
        }

        @Override
        public void appendHoverText(ItemStack st, Level level, java.util.List<Component> tip, net.minecraft.world.item.TooltipFlag f) {
            tip.add(Component.literal("§7Clic en la tierra arada: moja 3 × 3 y ayuda a crecer"));
            tip.add(Component.literal("§7Clic en el agua: se llena"));
        }
    }

    static IntegerProperty edad(BlockState s) {
        for (net.minecraft.world.level.block.state.properties.Property<?> pr : s.getProperties()) {
            if (pr instanceof IntegerProperty ip && pr.getName().equals("age")) {
                return ip;
            }
        }
        return null;
    }

    // ---------------- Establo ----------------
    public static class Cepillo extends Item {
        Cepillo(Item.Properties p) {
            super(p);
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity e, InteractionHand h) {
            if (!(e instanceof AbstractHorse caballo) || !caballo.isTamed()) {
                return InteractionResult.PASS;
            }
            if (p.level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            CompoundTag d = caballo.getPersistentData();
            long ahora = p.level().getGameTime();
            if (ahora - d.getLong("pol_cepillado") < 6000) {
                p.displayClientMessage(Component.literal("§7" + caballo.getName().getString() + " ya está brillante"), true);
                return InteractionResult.CONSUME;
            }
            d.putLong("pol_cepillado", ahora);
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, caballo.getX(), caballo.getY() + 1.2, caballo.getZ(), 12, 0.6, 0.4, 0.6, 0);
            p.level().playSound(null, caballo.blockPosition(), SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 1.0f, 1.2f);
            st.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(h));
            vinculo((ServerPlayer) p, caballo, 4);
            return InteractionResult.CONSUME;
        }
    }

    static void vinculo(ServerPlayer p, AbstractHorse c, int mas) {
        CompoundTag d = c.getPersistentData();
        int v = Math.min(100, d.getInt("pol_vinculo") + mas);
        d.putInt("pol_vinculo", v);
        int nivel = v / 25;
        AttributeInstance vel = c.getAttribute(Attributes.MOVEMENT_SPEED);
        if (vel != null) {
            vel.removeModifier(MOD_VEL);
            if (nivel > 0) {
                vel.addPermanentModifier(new AttributeModifier(MOD_VEL, "vinculo", nivel * 0.04, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
        AttributeInstance sal = c.getAttribute(Attributes.JUMP_STRENGTH);
        if (sal != null) {
            sal.removeModifier(MOD_SALTO);
            if (nivel > 0) {
                sal.addPermanentModifier(new AttributeModifier(MOD_SALTO, "vinculo", nivel * 0.03, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
        String corazones = "§c" + "♥".repeat(nivel) + "§8" + "♡".repeat(4 - nivel);
        p.displayClientMessage(Component.literal("§7Vínculo con " + c.getName().getString() + ": " + corazones + " §7(" + v + "/100)"), true);
        if (v >= 100) {
            PoliciaNivel.logro(p, "jinete");
        }
    }

    // ---------------- Pesca ----------------
    // nombre, item base, rareza (0-4), cm minimo, cm maximo, solo de noche
    static final Object[][] PECES = {
            {"Mojarra", Items.COD, 0, 12, 25, false}, {"Bagre", Items.COD, 0, 30, 60, false}, {"Pejerrey", Items.SALMON, 0, 20, 40, false},
            {"Trucha arcoíris", Items.SALMON, 1, 30, 70, false}, {"Dorado", Items.TROPICAL_FISH, 1, 40, 90, false},
            {"Surubí", Items.COD, 2, 60, 130, false}, {"Pez linterna", Items.TROPICAL_FISH, 2, 15, 30, true},
            {"Pez luna", Items.PUFFERFISH, 3, 40, 80, true}, {"Dorado del Eco", Items.TROPICAL_FISH, 4, 90, 160, true}};

    static void pescar(ServerPlayer p, double hx, double hy, double hz) {
        ServerLevel sl = p.serverLevel();
        boolean noche = !sl.isDay();
        float suerte = p.getLuck();
        double r = sl.random.nextDouble() * 100 - suerte * 3;
        int rareza = r < 1.5 && noche ? 4 : r < 6 ? 3 : r < 18 ? 2 : r < 42 ? 1 : 0;
        java.util.List<Object[]> l = new java.util.ArrayList<Object[]>();
        for (Object[] f : PECES) {
            if ((int) f[2] == rareza && (noche || !(boolean) f[5])) {
                l.add(f);
            }
        }
        if (l.isEmpty()) {
            for (Object[] f : PECES) {
                if ((int) f[2] <= 1) {
                    l.add(f);
                }
            }
        }
        Object[] f = l.get(sl.random.nextInt(l.size()));
        String nombre = (String) f[0];
        int cm = (int) f[3] + sl.random.nextInt((int) f[4] - (int) f[3] + 1);
        int ra = (int) f[2];
        ItemStack pez = new ItemStack((Item) f[1]);
        pez.setHoverName(Component.literal(nombre + " (" + cm + " cm)").withStyle(PoliciaRareza.COLOR[ra]));
        CompoundTag t = pez.getOrCreateTag();
        t.putString("pol_pez", nombre);
        t.putInt("pol_cm", cm);
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(PoliciaRareza.etiqueta(ra))));
        t.getCompound("display").put("Lore", lore);
        ItemEntity ie = new ItemEntity(sl, hx, hy, hz, pez);
        double dx = p.getX() - hx;
        double dy = p.getY() - hy;
        double dz = p.getZ() - hz;
        ie.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
        sl.addFreshEntity(ie);
        // coleccion y records
        CompoundTag col = PoliciaRPG.datos(p).getCompound("pesca");
        int antes = col.getInt(nombre);
        String extra = "";
        if (antes == 0) {
            extra = " §a¡Especie nueva! (" + (col.getAllKeys().size() + 1) + "/" + PECES.length + ")";
        } else if (cm > antes) {
            extra = " §6¡Récord! (antes " + antes + " cm)";
        }
        if (cm > antes) {
            col.putInt(nombre, cm);
        }
        PoliciaRPG.datos(p).put("pesca", col);
        p.displayClientMessage(Component.literal("§bPescaste: ").append(Component.literal(nombre + " de " + cm + " cm").withStyle(PoliciaRareza.COLOR[ra])).append(Component.literal(extra)), false);
        PoliciaNivel.dar(p, 6 + ra * 10);
        if (col.getAllKeys().size() >= 5) {
            PoliciaNivel.logro(p, "pescador");
        }
        if (ra == 4) {
            sl.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
        PoliciaRPG.sync(p);
    }

    public static class EventosCampo {
        @SubscribeEvent
        public void pesca(ItemFishedEvent e) {
            if (e.getEntity() instanceof ServerPlayer p && p.getRandom().nextFloat() < 0.35f) {
                pescar(p, e.getHookEntity().getX(), e.getHookEntity().getY(), e.getHookEntity().getZ());
            }
        }

        // Semillas de luna: a veces al cortar pasto o helechos de noche.
        @SubscribeEvent
        public void pasto(BlockEvent.BreakEvent e) {
            if (!(e.getLevel() instanceof ServerLevel sl) || e.getPlayer().isCreative() || sl.isDay()) {
                return;
            }
            BlockState s = e.getState();
            if ((s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN)) && sl.random.nextInt(12) == 0) {
                BlockPos q = e.getPos();
                sl.addFreshEntity(new ItemEntity(sl, q.getX() + 0.5, q.getY() + 0.3, q.getZ() + 0.5, new ItemStack(SEMILLAS_LUNA.get())));
            }
        }

        // Darle de comer a tu caballo sube el vinculo.
        @SubscribeEvent
        public void comer(PlayerInteractEvent.EntityInteract e) {
            if (!(e.getTarget() instanceof AbstractHorse c) || !(e.getEntity() instanceof ServerPlayer p) || !c.isTamed()) {
                return;
            }
            if (c.getOwnerUUID() != null && !c.getOwnerUUID().equals(p.getUUID())) {
                return;
            }
            ItemStack st = e.getItemStack();
            int mas = st.is(Items.GOLDEN_CARROT) ? 8 : st.is(Items.GOLDEN_APPLE) ? 10 : st.is(Items.CARROT) || st.is(Items.APPLE) ? 3 : st.is(Items.HAY_BLOCK) ? 2 : 0;
            if (mas == 0) {
                return;
            }
            CompoundTag d = c.getPersistentData();
            long ahora = p.level().getGameTime();
            if (ahora - d.getLong("pol_comio") < 200) {
                return;
            }
            d.putLong("pol_comio", ahora);
            vinculo(p, c, mas);
            p.serverLevel().sendParticles(ParticleTypes.HEART, c.getX(), c.getY() + 1.8, c.getZ(), 3, 0.3, 0.2, 0.3, 0);
        }
    }
}
