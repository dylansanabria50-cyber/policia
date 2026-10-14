package com.example.policia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

// Dado del carterismo: al intentar robar se tira un dado de 6 caras con
// animación pixel art en pantalla. 6 = robás, 5 = tirás de nuevo, 1 a 4 = te
// descubren. La Suerte inclina el dado; el Carterismo maestro saca 6 seguro.
public class PoliciaDado {
    // Ticks que dura cada tirada en pantalla (rodar + mostrar la cara).
    static final int PER_ROLL = 40;
    static final Map<UUID, Pending> PENDING = new HashMap<UUID, Pending>();

    static class Pending {
        LivingEntity target;
        boolean sure;
        int last;
        int ticks;
    }

    static void init() {
        PoliciaMod.NET.registerMessage(12, Roll.class, Roll::enc, Roll::dec, Roll::handle);
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaDado());
    }

    static void roll(ServerPlayer p, LivingEntity t, boolean sure, int luck) {
        ArrayList<Integer> rolls = new ArrayList<Integer>();
        if (sure) {
            rolls.add(6);
        } else {
            while (rolls.size() < 6) {
                int r = 1 + p.getRandom().nextInt(6);
                // La Suerte (niveles 1-4) puede convertir un fallo en un 6.
                if (r <= 4 && p.getRandom().nextFloat() < luck * 0.07f) {
                    r = 6;
                }
                rolls.add(r);
                if (r != 5) break;
            }
        }
        Pending pd = new Pending();
        pd.target = t;
        pd.sure = sure;
        pd.last = rolls.get(rolls.size() - 1);
        pd.ticks = rolls.size() * PER_ROLL - 4;
        PENDING.put(p.getUUID(), pd);
        PoliciaDado.freeze(p, t);
        int[] a = new int[rolls.size()];
        for (int i = 0; i < a.length; ++i) a[i] = rolls.get(i);
        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), new Roll(a));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> en = it.next();
            Pending pd = en.getValue();
            if (--pd.ticks > 0) continue;
            it.remove();
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null || !p.isAlive()) continue;
            LivingEntity t = pd.target;
            if (t == null || !t.isAlive() || t.level() != p.level() || p.distanceToSqr(t) > 36.0) {
                PoliciaMod.msg(p, "Tu objetivo se alejó antes de que terminara el dado");
                continue;
            }
            PoliciaLadron.pickResult(p, t, pd.last == 6, pd.sure, pd.last);
            Freeze fz = FROZEN.get(p.getUUID());
            if (fz != null) {
                if (pd.last == 6 && p.containerMenu != p.inventoryMenu) {
                    fz.phase = 2;
                    fz.left = 1200;
                } else {
                    PoliciaDado.release(p.getUUID());
                }
            }
        }
    }

    // ---- El objetivo queda quieto mirando hacia donde miraba ----
    static final Map<UUID, Freeze> FROZEN = new HashMap<UUID, Freeze>();

    static class Freeze {
        LivingEntity t;
        double x, y, z;
        float yaw, pitch, head, body;
        boolean setNoAi;
        int phase = 1;
        int left = 600;
    }

    static void freeze(ServerPlayer p, LivingEntity t) {
        PoliciaDado.release(p.getUUID());
        Freeze f = new Freeze();
        f.t = t;
        f.x = t.getX();
        f.y = t.getY();
        f.z = t.getZ();
        f.yaw = t.getYRot();
        f.pitch = t.getXRot();
        f.head = t.getYHeadRot();
        f.body = t.yBodyRot;
        if (t instanceof net.minecraft.world.entity.Mob m) {
            m.getNavigation().stop();
            if (!m.isNoAi()) {
                m.setNoAi(true);
                m.getPersistentData().putBoolean("pol_frz", true);
                f.setNoAi = true;
            }
        }
        t.setDeltaMovement(0.0, Math.min(0.0, t.getDeltaMovement().y), 0.0);
        FROZEN.put(p.getUUID(), f);
    }

    static void release(UUID thief) {
        Freeze f = FROZEN.remove(thief);
        if (f == null) return;
        if (f.setNoAi && f.t instanceof net.minecraft.world.entity.Mob m) {
            m.setNoAi(false);
            m.getPersistentData().remove("pol_frz");
        }
        if (f.t instanceof ServerPlayer v) {
            v.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            v.removeEffect(net.minecraft.world.effect.MobEffects.JUMP);
        }
    }

    // Si el mundo se cerró con un mob congelado, se descongela al cargar.
    @SubscribeEvent
    public void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide && e.getEntity() instanceof net.minecraft.world.entity.Mob m && m.getPersistentData().getBoolean("pol_frz")) {
            boolean mine = false;
            for (Freeze f : FROZEN.values()) {
                if (f.t == m) mine = true;
            }
            if (!mine) {
                m.setNoAi(false);
                m.getPersistentData().remove("pol_frz");
            }
        }
    }

    @SubscribeEvent
    public void hold(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.START || FROZEN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Freeze>> it = FROZEN.entrySet().iterator();
        ArrayList<UUID> done = new ArrayList<UUID>();
        while (it.hasNext()) {
            Map.Entry<UUID, Freeze> en = it.next();
            Freeze f = en.getValue();
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(en.getKey());
            LivingEntity t = f.t;
            if (p == null || !p.isAlive() || t == null || !t.isAlive() || t.isRemoved() || --f.left <= 0
                    || f.phase == 2 && p.containerMenu == p.inventoryMenu
                    || f.phase == 1 && !PENDING.containsKey(en.getKey())) {
                done.add(en.getKey());
                continue;
            }
            t.setYRot(f.yaw);
            t.setXRot(f.pitch);
            t.setYHeadRot(f.head);
            t.yBodyRot = f.body;
            if (t instanceof ServerPlayer v) {
                v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, 10, 128, false, false));
                if (v.distanceToSqr(f.x, f.y, f.z) > 0.04 || Math.abs(net.minecraft.util.Mth.wrapDegrees(v.getYRot() - f.yaw)) > 3.0f) {
                    v.connection.teleport(f.x, f.y, f.z, f.yaw, f.pitch);
                }
            } else {
                t.setDeltaMovement(0.0, Math.min(0.0, t.getDeltaMovement().y), 0.0);
                if (t.distanceToSqr(f.x, t.getY(), f.z) > 0.01) {
                    t.teleportTo(f.x, t.getY(), f.z);
                }
            }
        }
        for (UUID u : done) {
            PoliciaDado.release(u);
        }
    }

    public static class Roll {
        final int[] rolls;

        Roll(int[] rolls) {
            this.rolls = rolls;
        }

        static void enc(Roll m, FriendlyByteBuf b) {
            b.writeVarIntArray(m.rolls);
        }

        static Roll dec(FriendlyByteBuf b) {
            return new Roll(b.readVarIntArray());
        }

        static void handle(Roll m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PoliciaDadoScreen.start(m.rolls)));
            c.get().setPacketHandled(true);
        }
    }
}
