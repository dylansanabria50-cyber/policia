package com.example.policia;

import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

// Eleccion del oficio la primera vez que el jugador entra al mundo: el
// servidor manda Open, el cliente muestra PoliciaOficioScreen y responde
// con PoliciaMod.Act(70 + opcion). Una vez elegido (incluso DESEMPLEADO)
// queda guardado en "pol_chosen" y la ventana no vuelve a salir.
public class PoliciaOficio {
    // Mismo orden que las opciones de la ventana; la ultima es DESEMPLEADO.
    static final String[] JOBS = new String[]{"policia", "albanil", "ladron", "herrero", ""};
    static final String[] LABEL = new String[]{"policia", "albanil", "ladron", "herrero legendario", ""};
    static final String[] HINT = new String[]{
            "Pulsa B para activar el modo policia",
            "Pulsa B para activar el modo albanil",
            "Pulsa B para activar el modo ladron",
            "Pulsa B para activar el modo herrero",
            ""
    };

    static void init() {
        PoliciaMod.NET.registerMessage(10, Open.class, Open::enc, Open::dec, Open::handle);
    }

    static boolean chosen(ServerPlayer p) {
        return p.getPersistentData().getBoolean("pol_chosen");
    }

    // Al entrar: los que ya tenian oficio (mundos viejos) no vuelven a ver
    // la ventana; los demas la reciben hasta que elijan.
    static void login(ServerPlayer p) {
        PoliciaBook.refresh(p);
        CompoundTag d = p.getPersistentData();
        if (d.getBoolean("pol_chosen")) {
            return;
        }
        if (!PoliciaMod.job(p).isEmpty()) {
            d.putBoolean("pol_chosen", true);
            return;
        }
        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), (Object) new Open());
    }

    static void choose(ServerPlayer p, int opt) {
        CompoundTag d = p.getPersistentData();
        if (d.getBoolean("pol_chosen") || opt < 0 || opt >= JOBS.length) {
            return;
        }
        d.putBoolean("pol_chosen", true);
        if (JOBS[opt].isEmpty()) {
            // DESEMPLEADO: el personaje queda vanilla, sin oficio.
            PoliciaMod.msg(p, "Elegiste quedarte desempleado: tu personaje sigue igual que siempre");
            return;
        }
        PoliciaOficio.assign(p, JOBS[opt], LABEL[opt], HINT[opt]);
    }

    // Asigna un oficio (lo usa la ventana y tambien los items de oficio
    // del modo creativo).
    static void assign(ServerPlayer sp, String job, String label, String hint) {
        sp.getPersistentData().putString("pol_job", job);
        sp.getPersistentData().putBoolean("pol_chosen", true);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.6f);
        sp.serverLevel().sendParticles((ParticleOptions) ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1.0, sp.getZ(), 24, 0.6, 0.8, 0.6, 0.05);
        PoliciaMod.msg(sp, "Ahora eres " + label + ". " + hint);
        PoliciaMod.sync(sp);
        PoliciaBook.give(sp, job);
    }

    public static class Open {
        static void enc(Open m, FriendlyByteBuf b) {
        }

        static Open dec(FriendlyByteBuf b) {
            return new Open();
        }

        static void handle(Open m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> PoliciaOficioScreen.pending = true));
            c.get().setPacketHandled(true);
        }
    }
}
