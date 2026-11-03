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
        CH.registerMessage(8, Esquivar.class, Esquivar::enc, Esquivar::dec, Esquivar::handle);
        CH.registerMessage(9, DanoNum.class, DanoNum::enc, DanoNum::dec, DanoNum::handle);
        CH.registerMessage(10, EnergiaSync.class, EnergiaSync::enc, EnergiaSync::dec, EnergiaSync::handle);
        CH.registerMessage(11, QuitarCasillero.class, QuitarCasillero::enc, QuitarCasillero::dec, QuitarCasillero::handle);
        CH.registerMessage(12, HistoriaDialogo.class, HistoriaDialogo::enc, HistoriaDialogo::dec, HistoriaDialogo::handle);
        CH.registerMessage(13, HistoriaElegir.class, HistoriaElegir::enc, HistoriaElegir::dec, HistoriaElegir::handle);
        CH.registerMessage(14, FaccionReclamar.class, FaccionReclamar::enc, FaccionReclamar::dec, FaccionReclamar::handle);
        CH.registerMessage(15, Efecto.class, Efecto::enc, Efecto::dec, Efecto::handle);
        CH.registerMessage(16, Accion.class, Accion::enc, Accion::dec, Accion::handle);
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

    // Combate: rodar hacia (dx, dz).
    public static class Esquivar {
        float dx;
        float dz;

        Esquivar(float dx, float dz) {
            this.dx = dx;
            this.dz = dz;
        }

        static void enc(Esquivar m, FriendlyByteBuf b) {
            b.writeFloat(m.dx);
            b.writeFloat(m.dz);
        }

        static Esquivar dec(FriendlyByteBuf b) {
            return new Esquivar(b.readFloat(), b.readFloat());
        }

        static void handle(Esquivar m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p != null) {
                    PoliciaCombate.esquivar(p, m.dx, m.dz);
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    // Combate: numero de dano flotante.
    public static class DanoNum {
        int ent;
        float cant;
        int tipo;

        DanoNum(int ent, float cant, int tipo) {
            this.ent = ent;
            this.cant = cant;
            this.tipo = tipo;
        }

        static void enc(DanoNum m, FriendlyByteBuf b) {
            b.writeVarInt(m.ent);
            b.writeFloat(m.cant);
            b.writeByte(m.tipo);
        }

        static DanoNum dec(FriendlyByteBuf b) {
            return new DanoNum(b.readVarInt(), b.readFloat(), b.readByte());
        }

        static void handle(DanoNum m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> PoliciaCombateCliente.numero(m.ent, m.cant, m.tipo)));
            c.get().setPacketHandled(true);
        }
    }

    // Combate: energia del jugador.
    public static class EnergiaSync {
        float v;
        float max;

        EnergiaSync(float v, float max) {
            this.v = v;
            this.max = max;
        }

        static void enc(EnergiaSync m, FriendlyByteBuf b) {
            b.writeFloat(m.v);
            b.writeFloat(m.max);
        }

        static EnergiaSync dec(FriendlyByteBuf b) {
            return new EnergiaSync(b.readFloat(), b.readFloat());
        }

        static void handle(EnergiaSync m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                PoliciaCombateCliente.ENERGIA = m.v;
                PoliciaCombateCliente.ENERGIA_MAX = m.max;
            });
            c.get().setPacketHandled(true);
        }
    }

    // Ficha: sacar lo que hay en un casillero (anillo, anillo2, amuleto...).
    public static class QuitarCasillero {
        String slot;

        QuitarCasillero(String slot) {
            this.slot = slot;
        }

        static void enc(QuitarCasillero m, FriendlyByteBuf b) {
            b.writeUtf(m.slot, 16);
        }

        static QuitarCasillero dec(FriendlyByteBuf b) {
            return new QuitarCasillero(b.readUtf(16));
        }

        static void handle(QuitarCasillero m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p != null && java.util.Arrays.asList("anillo", "anillo2", "amuleto", "capa", "cinturon").contains(m.slot)) {
                    PoliciaRareza.quitar(p, m.slot);
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    // Historia: lo que dice el NPC (lineas separadas por \n) y las opciones.
    public static class HistoriaDialogo {
        int ent;
        String nombre;
        String texto;
        String opciones;

        HistoriaDialogo(int ent, String nombre, String texto, String opciones) {
            this.ent = ent;
            this.nombre = nombre;
            this.texto = texto;
            this.opciones = opciones;
        }

        static void enc(HistoriaDialogo m, FriendlyByteBuf b) {
            b.writeVarInt(m.ent);
            b.writeUtf(m.nombre);
            b.writeUtf(m.texto);
            b.writeUtf(m.opciones);
        }

        static HistoriaDialogo dec(FriendlyByteBuf b) {
            return new HistoriaDialogo(b.readVarInt(), b.readUtf(), b.readUtf(), b.readUtf());
        }

        static void handle(HistoriaDialogo m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> PoliciaHistoriaScreen.abrir(m.ent, m.nombre, m.texto, m.opciones)));
            c.get().setPacketHandled(true);
        }
    }

    public static class HistoriaElegir {
        int op;

        HistoriaElegir(int op) {
            this.op = op;
        }

        static void enc(HistoriaElegir m, FriendlyByteBuf b) {
            b.writeVarInt(m.op);
        }

        static HistoriaElegir dec(FriendlyByteBuf b) {
            return new HistoriaElegir(b.readVarInt());
        }

        static void handle(HistoriaElegir m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p != null) {
                    PoliciaHistoria.elegir(p, m.op);
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    public static class FaccionReclamar {
        String id;
        int nivel;

        FaccionReclamar(String id, int nivel) {
            this.id = id;
            this.nivel = nivel;
        }

        static void enc(FaccionReclamar m, FriendlyByteBuf b) {
            b.writeUtf(m.id, 32);
            b.writeVarInt(m.nivel);
        }

        static FaccionReclamar dec(FriendlyByteBuf b) {
            return new FaccionReclamar(b.readUtf(32), b.readVarInt());
        }

        static void handle(FaccionReclamar m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p != null) {
                    PoliciaFacciones.reclamar(p, m.id, m.nivel);
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    // Servidor -> cliente: algo para mostrar o actualizar (efectos visuales,
    // formas, datos de pantallas). Lo reparte PoliciaAnimeCliente.recibir.
    public static class Efecto {
        String tipo;
        CompoundTag d;

        public Efecto(String tipo, CompoundTag d) {
            this.tipo = tipo;
            this.d = d;
        }

        static void enc(Efecto m, FriendlyByteBuf b) {
            b.writeUtf(m.tipo, 64);
            b.writeNbt(m.d);
        }

        static Efecto dec(FriendlyByteBuf b) {
            String t = b.readUtf(64);
            CompoundTag d = b.readNbt();
            return new Efecto(t, d == null ? new CompoundTag() : d);
        }

        static void handle(Efecto m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> PoliciaAnimeCliente.recibir(m.tipo, m.d)));
            c.get().setPacketHandled(true);
        }
    }

    // Cliente -> servidor: un pedido desde una pantalla (especializacion,
    // mercenarios, bestiario...). Lo reparte PoliciaAcciones.recibir.
    public static class Accion {
        String tipo;
        CompoundTag d;

        public Accion(String tipo, CompoundTag d) {
            this.tipo = tipo;
            this.d = d;
        }

        static void enc(Accion m, FriendlyByteBuf b) {
            b.writeUtf(m.tipo, 64);
            b.writeNbt(m.d);
        }

        static Accion dec(FriendlyByteBuf b) {
            String t = b.readUtf(64);
            CompoundTag d = b.readNbt();
            return new Accion(t, d == null ? new CompoundTag() : d);
        }

        static void handle(Accion m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (p != null) {
                    PoliciaAcciones.recibir(p, m.tipo, m.d);
                }
            });
            c.get().setPacketHandled(true);
        }
    }
}
