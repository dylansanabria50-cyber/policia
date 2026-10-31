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
        CH.registerMessage(1, AnimPlay.class, AnimPlay::enc, AnimPlay::dec, AnimPlay::handle);
        CH.registerMessage(2, SubirAtributo.class, SubirAtributo::enc, SubirAtributo::dec, SubirAtributo::handle);
        CH.registerMessage(3, HabAccion.class, HabAccion::enc, HabAccion::dec, HabAccion::handle);
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

    // Etapa 3: "el jugador X empezo la animacion N" (ver PoliciaAnim).
    public static class AnimPlay {
        int ent;
        int idx;

        AnimPlay(int ent, int idx) {
            this.ent = ent;
            this.idx = idx;
        }

        static void enc(AnimPlay m, FriendlyByteBuf b) {
            b.writeVarInt(m.ent);
            b.writeVarInt(m.idx);
        }

        static AnimPlay dec(FriendlyByteBuf b) {
            return new AnimPlay(b.readVarInt(), b.readVarInt());
        }

        static void handle(AnimPlay m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> PoliciaAnimCliente.start(m.ent, m.idx)));
            c.get().setPacketHandled(true);
        }
    }

    // Etapa 4: el cliente pide gastar un punto en un atributo (ficha, tecla I).
    public static class SubirAtributo {
        int idx;

        SubirAtributo(int idx) {
            this.idx = idx;
        }

        static void enc(SubirAtributo m, FriendlyByteBuf b) {
            b.writeVarInt(m.idx);
        }

        static SubirAtributo dec(FriendlyByteBuf b) {
            return new SubirAtributo(b.readVarInt());
        }

        static void handle(SubirAtributo m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p != null && m.idx >= 0 && m.idx < PoliciaRPG.ATRIBUTOS.length) {
                    PoliciaRPG.subirAtributo(p, PoliciaRPG.ATRIBUTOS[m.idx]);
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    // Etapa 6: arbol de habilidades nuevas (0 desbloquear, 1 equipar, 2 usar, 3 siguiente).
    public static class HabAccion {
        int a;
        String id;

        HabAccion(int a, String id) {
            this.a = a;
            this.id = id;
        }

        static void enc(HabAccion m, FriendlyByteBuf b) {
            b.writeVarInt(m.a);
            b.writeUtf(m.id, 64);
        }

        static HabAccion dec(FriendlyByteBuf b) {
            return new HabAccion(b.readVarInt(), b.readUtf(64));
        }

        static void handle(HabAccion m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p != null) {
                    PoliciaHab.accion(p, m.a, m.id);
                }
            });
            c.get().setPacketHandled(true);
        }
    }
}
