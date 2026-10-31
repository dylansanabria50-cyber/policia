package com.example.policia;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

// Canal de red nuevo para el modo RPG (etapa 1). Va aparte del canal
// "policia:main" para no tocar los mensajes 0-15 que ya existen.
// Cada mensaje nuevo se registra aca con el siguiente numero libre.
public class PoliciaRed {
    public static final SimpleChannel CH = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(PoliciaMod.ID, "red"))
            .networkProtocolVersion(() -> "1")
            .clientAcceptedVersions(v -> true)
            .serverAcceptedVersions(v -> true)
            .simpleChannel();

    static void init() {
        CH.registerMessage(0, RpgSync.class, RpgSync::enc, RpgSync::dec, RpgSync::handle);
    }

    static void toPlayer(ServerPlayer p, Object msg) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    // Datos RPG del jugador (nivel, XP, atributos...) enviados a su cliente.
    public static class RpgSync {
        CompoundTag data;

        RpgSync() {
        }

        RpgSync(CompoundTag data) {
            this.data = data;
        }

        static void enc(RpgSync m, FriendlyByteBuf b) {
            b.writeNbt(m.data);
        }

        static RpgSync dec(FriendlyByteBuf b) {
            RpgSync m = new RpgSync();
            m.data = b.readNbt();
            if (m.data == null) {
                m.data = new CompoundTag();
            }
            return m;
        }

        static void handle(RpgSync m, Supplier<NetworkEvent.Context> c) {
            // Solo guarda una copia: no toca clases de cliente, asi el
            // servidor dedicado puede cargar esta clase sin problemas.
            c.get().enqueueWork(() -> PoliciaRPG.CLIENTE = m.data);
            c.get().setPacketHandled(true);
        }
    }
}
