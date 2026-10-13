package com.example.policia;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

// Items que asignan el oficio al jugador: Placa de policia, Balde de
// albanil y Guante de ladron.
public class PoliciaPlaca {
    public static final RegistryObject<Item> PLACA = PoliciaShield.ITEMS.register("placa_policia",
            () -> new JobBadge(new Item.Properties().stacksTo(1), "policia", "policia", "Pulsa B para activar el modo policia"));
    public static final RegistryObject<Item> BALDE = PoliciaShield.ITEMS.register("balde_albanil",
            () -> new JobBadge(new Item.Properties().stacksTo(1), "albanil", "albanil", "Pulsa B para activar el modo albanil"));
    public static final RegistryObject<Item> GUANTE = PoliciaShield.ITEMS.register("guante_ladron",
            () -> new JobBadge(new Item.Properties().stacksTo(1), "ladron", "ladron", "Pulsa B para activar el modo ladron"));
    public static final RegistryObject<Item> MARTILLO = PoliciaShield.ITEMS.register("martillo_herrero",
            () -> new JobBadge(new Item.Properties().stacksTo(1).fireResistant(), "herrero", "herrero legendario", "Pulsa B para activar el modo herrero"));

    public static void init() {
    }

    public static class JobBadge extends Item {
        final String job;
        final String label;
        final String hint;

        public JobBadge(Item.Properties p, String job, String label, String hint) {
            super(p);
            this.job = job;
            this.label = label;
            this.hint = hint;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                String cur = PoliciaMod.job(sp);
                if (!cur.isEmpty()) {
                    PoliciaMod.msg(sp, cur.equals(this.job) ? "Ya eres " + this.label + ": la placa no sirve de nada" : "Ya tienes un oficio (" + cur + "): solo se puede tener uno");
                    return InteractionResultHolder.fail(st);
                }
                sp.getPersistentData().putString("pol_job", this.job);
                st.shrink(1);
                level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
                level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.6f);
                sp.serverLevel().sendParticles((ParticleOptions) ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1.0, sp.getZ(), 24, 0.6, 0.8, 0.6, 0.05);
                PoliciaMod.msg(sp, "Ahora eres " + this.label + ". " + this.hint);
                PoliciaMod.sync(sp);
                PoliciaBook.give(sp, this.job);
            }
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
    }
}
