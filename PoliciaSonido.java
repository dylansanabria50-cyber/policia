package com.example.policia;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// Sonidos propios: sirena del patrullero y música para persecuciones y
// golpes grandes (se reproduce en el cliente del jugador, en bucle).
public class PoliciaSonido {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "policia");
    public static final RegistryObject<SoundEvent> SIRENA = SOUNDS.register("patrullero.sirena", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("policia", "patrullero.sirena")));
    public static final RegistryObject<SoundEvent> PERSECUCION = SOUNDS.register("musica.persecucion", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("policia", "musica.persecucion")));
    public static final RegistryObject<SoundEvent> GOLPE = SOUNDS.register("musica.golpe", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("policia", "musica.golpe")));

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
        PoliciaMod.NET.registerMessage(13, Music.class, Music::enc, Music::dec, Music::handle);
    }

    // 0 = parar, 1 = persecución, 2 = golpe
    static void music(ServerPlayer p, int kind) {
        String key = "mus_cur";
        if (p.getPersistentData().getInt(key) == kind) {
            return;
        }
        p.getPersistentData().putInt(key, kind);
        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), new Music(kind));
    }

    public static class Music {
        final int kind;

        Music(int kind) {
            this.kind = kind;
        }

        static void enc(Music m, FriendlyByteBuf b) {
            b.writeVarInt(m.kind);
        }

        static Music dec(FriendlyByteBuf b) {
            return new Music(b.readVarInt());
        }

        static void handle(Music m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.play(m.kind)));
            c.get().setPacketHandled(true);
        }
    }

    public static class Client {
        static SoundInstance cur;

        static void play(int kind) {
            Minecraft mc = Minecraft.getInstance();
            if (cur != null) {
                mc.getSoundManager().stop(cur);
                cur = null;
            }
            if (kind <= 0) {
                return;
            }
            mc.getMusicManager().stopPlaying();
            ResourceLocation id = new ResourceLocation("policia", kind == 1 ? "musica.persecucion" : "musica.golpe");
            cur = new SimpleSoundInstance(id, SoundSource.MUSIC, 0.8f, 1.0f, SoundInstance.createUnseededRandom(), true, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
            mc.getSoundManager().play(cur);
        }
    }
}
