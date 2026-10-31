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
        CH.registerMessage(4, ForjaAbrir.class, ForjaAbrir::enc, ForjaAbrir::dec, ForjaAbrir::handle);
        CH.registerMessage(5, ForjaResultado.class, ForjaResultado::enc, ForjaResultado::dec, ForjaResultado::handle);
        CH.registerMessage(6, QuitarAnillo.class, QuitarAnillo::enc, QuitarAnillo::dec, QuitarAnillo::handle);
        CH.registerMessage(7, ViajeIr.class, ViajeIr::enc, ViajeIr::dec, ViajeIr::handle);
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

    // Etapa 7: el servidor abre el minijuego de forja en el cliente.
    public static class ForjaAbrir {
        int rareza;

        ForjaAbrir(int rareza) {
            this.rareza = rareza;
        }

        static void enc(ForjaAbrir m, FriendlyByteBuf b) {
            b.writeVarInt(m.rareza);
        }

        static ForjaAbrir dec(FriendlyByteBuf b) {
            return new ForjaAbrir(b.readVarInt());
        }

        static void handle(ForjaAbrir m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> PoliciaForjaScreen.abrir(Math.max(0, Math.min(4, m.rareza)))));
            c.get().setPacketHandled(true);
        }
    }

    // Etapa 7: el cliente manda la puntuacion del minijuego (el servidor la valida).
    public static class ForjaResultado {
        int puntos;

        ForjaResultado(int puntos) {
            this.puntos = puntos;
        }

        static void enc(ForjaResultado m, FriendlyByteBuf b) {
            b.writeVarInt(m.puntos);
        }

        static ForjaResultado dec(FriendlyByteBuf b) {
            return new ForjaResultado(b.readVarInt());
        }

        static void handle(ForjaResultado m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p != null) {
                    PoliciaRareza.resultado(p, m.puntos);
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    // Etapa 7: sacarse el anillo (boton de la ficha).
    public static class QuitarAnillo {
        static void enc(QuitarAnillo m, FriendlyByteBuf b) {
        }

        static QuitarAnillo dec(FriendlyByteBuf b) {
            return new QuitarAnillo();
        }

        static void handle(QuitarAnillo m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p != null) {
                    PoliciaRareza.quitarAnillo(p);
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    // Etapa 8: viajar en carruaje a una parada ("casa" = tu casa).
    public static class ViajeIr {
        String clave;

        ViajeIr(String clave) {
            this.clave = clave;
        }

        static void enc(ViajeIr m, FriendlyByteBuf b) {
            b.writeUtf(m.clave, 64);
        }

        static ViajeIr dec(FriendlyByteBuf b) {
            return new ViajeIr(b.readUtf(64));
        }

        static void handle(ViajeIr m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p != null) {
                    PoliciaViaje.viajar(p, m.clave);
                }
            });
            c.get().setPacketHandled(true);
        }
    }
}
