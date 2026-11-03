package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

// Alquimia y cocina: seis comidas y seis elixires propios que dan mejoras
// temporales. Se hacen en la mesa de crafteo (recetas en data/policia).
// Hechos por el oficio que corresponde salen "de calidad" (+50 % de
// duracion): los elixires si los hace un Medico y las comidas si las hace
// un Mercader (cocinero de caravana). Cada receta da un poco de XP.
public class PoliciaCocina {
    // {id, nombre, nutricion, saturacion x10, bol?}
    static final String[][] COMIDAS = {
            {"guiso_campo", "Guiso de campo", "10", "10", "bol"},
            {"pan_viajero", "Pan del viajero", "6", "7", ""},
            {"brocheta_cazador", "Brocheta del cazador", "8", "9", ""},
            {"sopa_minero", "Sopa del minero", "8", "8", "bol"},
            {"tarta_miel", "Tarta de miel", "7", "8", ""},
            {"caldo_helado", "Caldo helado", "6", "7", "bol"}};
    static final String[][] ELIX = {
            {"elixir_vigor", "Elixir de vigor"},
            {"elixir_furia", "Elixir de furia"},
            {"elixir_piedra", "Elixir de piel de piedra"},
            {"elixir_sombra", "Elixir de sombra"},
            {"elixir_sabio", "Elixir del sabio"},
            {"elixir_energia", "Elixir de energía"}};
    static final int ELIXIRES = ELIX.length;
    static final List<RegistryObject<Item>> ITEMS = new ArrayList<RegistryObject<Item>>();

    static {
        for (int i = 0; i < COMIDAS.length; i++) {
            final int k = i;
            ITEMS.add(PoliciaShield.ITEMS.register(COMIDAS[i][0], () -> new Comida(k)));
        }
        for (int i = 0; i < ELIX.length; i++) {
            final int k = i;
            ITEMS.add(PoliciaShield.ITEMS.register(ELIX[i][0], () -> new Elixir(k)));
        }
    }

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosCocina());
    }

    static List<Item> todos() {
        List<Item> l = new ArrayList<Item>();
        for (RegistryObject<Item> r : ITEMS) {
            l.add(r.get());
        }
        return l;
    }

    static ItemStack elixir(int i, int n) {
        return new ItemStack(ITEMS.get(COMIDAS.length + Math.floorMod(i, ELIX.length)).get(), n);
    }

    static ItemStack comida(int i, int n) {
        return new ItemStack(ITEMS.get(Math.floorMod(i, COMIDAS.length)).get(), n);
    }

    static MobEffectInstance ef(MobEffect e, int seg, int amp) {
        return new MobEffectInstance(e, seg * 20, amp);
    }

    static List<MobEffectInstance> efectosComida(int i) {
        List<MobEffectInstance> l = new ArrayList<MobEffectInstance>();
        switch (i) {
            case 0 -> {
                l.add(ef(MobEffects.REGENERATION, 12, 0));
                l.add(ef(MobEffects.DAMAGE_RESISTANCE, 60, 0));
            }
            case 1 -> l.add(ef(MobEffects.MOVEMENT_SPEED, 90, 0));
            case 2 -> l.add(ef(MobEffects.DAMAGE_BOOST, 45, 0));
            case 3 -> {
                l.add(ef(MobEffects.DIG_SPEED, 120, 0));
                l.add(ef(MobEffects.NIGHT_VISION, 90, 0));
            }
            case 4 -> l.add(ef(MobEffects.ABSORPTION, 90, 0));
            default -> l.add(ef(MobEffects.FIRE_RESISTANCE, 120, 0));
        }
        return l;
    }

    static List<MobEffectInstance> efectosElixir(int i) {
        List<MobEffectInstance> l = new ArrayList<MobEffectInstance>();
        switch (i) {
            case 0 -> {
                l.add(ef(MobEffects.REGENERATION, 20, 1));
                l.add(ef(MobEffects.ABSORPTION, 60, 1));
            }
            case 1 -> {
                l.add(ef(MobEffects.DAMAGE_BOOST, 45, 1));
                l.add(ef(MobEffects.MOVEMENT_SPEED, 45, 0));
            }
            case 2 -> {
                l.add(ef(MobEffects.DAMAGE_RESISTANCE, 60, 1));
                l.add(ef(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
            }
            case 3 -> {
                l.add(ef(MobEffects.INVISIBILITY, 60, 0));
                l.add(ef(MobEffects.NIGHT_VISION, 120, 0));
            }
            case 4 -> l.add(ef(MobEffects.LUCK, 300, 0));
            default -> l.add(ef(MobEffects.DIG_SPEED, 60, 1));
        }
        return l;
    }

    // Los efectos que da un objeto de este archivo (para el Alquimista).
    static List<MobEffectInstance> efectosDe(ItemStack st) {
        if (st.getItem() instanceof Comida c) {
            return efectosComida(c.tipo);
        }
        if (st.getItem() instanceof Elixir e) {
            return efectosElixir(e.tipo);
        }
        return new ArrayList<MobEffectInstance>();
    }

    static boolean calidad(ItemStack st) {
        return st.hasTag() && st.getTag().getBoolean("pol_calidad");
    }

    static void aplicar(LivingEntity le, List<MobEffectInstance> efs, boolean calidad) {
        for (MobEffectInstance m : efs) {
            le.addEffect(new MobEffectInstance(m.getEffect(), (int) (m.getDuration() * (calidad ? 1.5 : 1.0)), m.getAmplifier()));
        }
    }

    static void tooltip(List<Component> tt, List<MobEffectInstance> efs, ItemStack st) {
        boolean cal = calidad(st);
        for (MobEffectInstance m : efs) {
            int s = (int) (m.getDuration() / 20 * (cal ? 1.5 : 1.0));
            String amp = m.getAmplifier() > 0 ? " " + "I".repeat(m.getAmplifier() + 1) : "";
            tt.add(Component.literal((m.getEffect().isBeneficial() ? "§9" : "§c") + m.getEffect().getDisplayName().getString() + amp + " §7(" + s / 60 + ":" + String.format("%02d", s % 60) + ")"));
        }
        if (cal) {
            tt.add(Component.literal("§6★ De calidad: +50 % de duración").withStyle(ChatFormatting.ITALIC));
        }
    }

    public static class Comida extends Item {
        final int tipo;

        Comida(int tipo) {
            super(new Item.Properties().stacksTo(COMIDAS[tipo][4].isEmpty() ? 16 : 1).food(new FoodProperties.Builder().nutrition(Integer.parseInt(COMIDAS[tipo][2])).saturationMod(Integer.parseInt(COMIDAS[tipo][3]) / 10.0f).alwaysEat().build()));
            this.tipo = tipo;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack st, Level level, LivingEntity le) {
            boolean cal = calidad(st);
            ItemStack r = super.finishUsingItem(st, level, le);
            if (!level.isClientSide) {
                aplicar(le, efectosComida(this.tipo), cal);
            }
            if (!COMIDAS[this.tipo][4].isEmpty()) {
                if (r.isEmpty()) {
                    return new ItemStack(Items.BOWL);
                }
                if (le instanceof Player p && !p.getAbilities().instabuild && !p.getInventory().add(new ItemStack(Items.BOWL))) {
                    p.drop(new ItemStack(Items.BOWL), false);
                }
            }
            return r;
        }

        @Override
        public void appendHoverText(ItemStack st, Level level, List<Component> tt, TooltipFlag f) {
            tooltip(tt, efectosComida(this.tipo), st);
        }

        @Override
        public Component getName(ItemStack st) {
            return Component.literal(COMIDAS[this.tipo][1]);
        }
    }

    public static class Elixir extends Item {
        final int tipo;

        Elixir(int tipo) {
            super(new Item.Properties().stacksTo(8));
            this.tipo = tipo;
        }

        @Override
        public UseAnim getUseAnimation(ItemStack st) {
            return UseAnim.DRINK;
        }

        @Override
        public int getUseDuration(ItemStack st) {
            return 32;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            return ItemUtils.startUsingInstantly(level, p, hand);
        }

        @Override
        public boolean isFoil(ItemStack st) {
            return true;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack st, Level level, LivingEntity le) {
            boolean cal = calidad(st);
            if (!level.isClientSide) {
                aplicar(le, efectosElixir(this.tipo), cal);
                if (le instanceof ServerPlayer sp) {
                    if (this.tipo == 4) {
                        PoliciaRPG.sumarXp(sp, cal ? 120 : 80);
                    }
                    if (this.tipo == 5) {
                        sp.getPersistentData().putFloat("energia", PoliciaCombate.energiaMax(sp));
                        PoliciaCombate.sync(sp);
                    }
                    sp.serverLevel().sendParticles(ParticleTypes.EFFECT, sp.getX(), sp.getY() + 1, sp.getZ(), 15, 0.3, 0.5, 0.3, 0.1);
                }
            }
            if (le instanceof Player p && p.getAbilities().instabuild) {
                return st;
            }
            st.shrink(1);
            if (st.isEmpty()) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            if (le instanceof Player p && !p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                p.drop(new ItemStack(Items.GLASS_BOTTLE), false);
            }
            return st;
        }

        @Override
        public void appendHoverText(ItemStack st, Level level, List<Component> tt, TooltipFlag f) {
            tooltip(tt, efectosElixir(this.tipo), st);
            if (this.tipo == 4) {
                tt.add(Component.literal("§a+80 XP de nivel"));
            }
            if (this.tipo == 5) {
                tt.add(Component.literal("§9Recupera toda la energía de combate"));
            }
        }

        @Override
        public Component getName(ItemStack st) {
            return Component.literal("§d" + ELIX[this.tipo][1]);
        }
    }

    public static class EventosCocina {
        @SubscribeEvent
        public void crafteo(PlayerEvent.ItemCraftedEvent e) {
            ItemStack st = e.getCrafting();
            if (!(e.getEntity() instanceof ServerPlayer p) || !(st.getItem() instanceof Comida || st.getItem() instanceof Elixir)) {
                return;
            }
            String of = PoliciaMod.job(p);
            boolean cal = st.getItem() instanceof Elixir ? "medico".equals(of) : "mercader".equals(of);
            if (cal) {
                CompoundTag t = st.getOrCreateTag();
                t.putBoolean("pol_calidad", true);
            }
            PoliciaRPG.sumarXp(p, st.getItem() instanceof Elixir ? 8 : 4);
            p.serverLevel().playSound(null, p.blockPosition(), st.getItem() instanceof Elixir ? SoundEvents.BREWING_STAND_BREW : SoundEvents.SMOKER_SMOKE, SoundSource.PLAYERS, 0.6f, 1.2f);
            if (cal) {
                p.displayClientMessage(Component.literal("§6★ Te salió de calidad §7(+50 % de duración)"), true);
            }
        }
    }
}
