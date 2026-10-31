package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.joml.Quaternionf;

// Etapa 2: movimientos mejorados para TODOS los jugadores (solo visual) y
// primera persona con inercia, igual que en la vista previa:
//  - el cuerpo se inclina al acelerar y hacia atras al frenar,
//  - se ladea hacia adentro en las curvas y la cabeza se adelanta al giro,
//  - aterrizaje con amortiguacion (se aplasta y rebota), estirada al saltar,
//  - respiracion cuando esta quieto, brazos mas amplios al correr,
//  - escalera trepando con brazos y piernas, brazos al saltar y al caer,
//  - empujon hacia atras al recibir un golpe.
// En primera persona: el brazo tiene resorte al girar la camara, se hunde
// al aterrizar, al correr se va hacia atras (estilo anime) con lineas de
// velocidad en los bordes, y la camara se inclina un poco en las curvas.
// Todo se apaga desde config/policia-cliente.toml.
public class PoliciaMovimiento {
    static class St {
        boolean init;
        long t;
        double px, py, pz;
        float yaw, spd, acc, vy, run;
        float lean, leanV, bank, bankV, sq, sqV, hurt;
        boolean ground = true;
        int hurtPrev;
    }

    static final Map<Integer, St> ST = new HashMap<Integer, St>();
    static final Set<Integer> PUSHED = new HashSet<Integer>();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderPlayerEvent.Pre.class, PoliciaMovimiento::pre);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, RenderPlayerEvent.Post.class, PoliciaMovimiento::post);
        MinecraftForge.EVENT_BUS.addListener(PoliciaMovimiento::camara);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, RenderHandEvent.class, PoliciaMovimiento::mano);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaMovimiento::overlays);
    }

    static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("policia_velocidad", PoliciaMovimiento::lineas);
    }

    // ---------------- Estado de cada jugador ----------------
    static St st(Player e) {
        if (ST.size() > 256) {
            ST.clear();
        }
        return ST.computeIfAbsent(e.getId(), k -> new St());
    }

    static void actualizar(St s, Player e, float pt) {
        long now = System.nanoTime();
        double x = Mth.lerp((double) pt, e.xo, e.getX());
        double y = Mth.lerp((double) pt, e.yo, e.getY());
        double z = Mth.lerp((double) pt, e.zo, e.getZ());
        float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        if (!s.init) {
            s.init = true;
            s.t = now;
            s.px = x;
            s.py = y;
            s.pz = z;
            s.yaw = yaw;
            s.ground = e.onGround();
            return;
        }
        float dt = Mth.clamp((float) (now - s.t) / 1.0E9f, 0.001f, 0.1f);
        s.t = now;
        float I = PoliciaConfig.intensidad();
        double dx = x - s.px;
        double dz = z - s.pz;
        float spd = (float) Math.sqrt(dx * dx + dz * dz) / dt;
        float vy = (float) (y - s.py) / dt;
        if (spd > 40.0f) {
            spd = s.spd;
            vy = 0.0f;
        }
        float yr = Mth.wrapDegrees(yaw - s.yaw) / dt;
        s.px = x;
        s.py = y;
        s.pz = z;
        s.yaw = yaw;
        float ns = s.spd + (spd - s.spd) * Math.min(1.0f, dt * 10.0f);
        float rawAcc = (ns - s.spd) / dt;
        s.acc += (rawAcc - s.acc) * Math.min(1.0f, dt * 6.0f);
        s.spd = ns;
        s.run += ((e.isSprinting() ? 1.0f : 0.0f) - s.run) * Math.min(1.0f, dt * 6.0f);
        boolean g = e.onGround();
        boolean libre = !e.isPassenger() && !e.isSwimming() && !e.isFallFlying() && !e.onClimbable() && !e.isSleeping();
        float leanT = 0.0f;
        float bankT = 0.0f;
        if (libre) {
            leanT = g ? Mth.clamp(s.spd / 5.6f * 5.0f + s.acc * 0.8f, -7.0f, 11.0f) : Mth.clamp(s.spd / 5.6f * 3.0f, 0.0f, 5.0f);
            bankT = g ? Mth.clamp(yr * 0.025f * Math.min(1.0f, s.spd / 3.0f), -9.0f, 9.0f) : 0.0f;
        }
        s.leanV += ((leanT * I - s.lean) * 60.0f - s.leanV * 12.0f) * dt;
        s.lean = Mth.clamp(s.lean + s.leanV * dt, -16.0f, 16.0f);
        s.bankV += ((bankT * I - s.bank) * 60.0f - s.bankV * 12.0f) * dt;
        s.bank = Mth.clamp(s.bank + s.bankV * dt, -12.0f, 12.0f);
        // Aterrizaje: se aplasta segun la velocidad de caida y rebota.
        if (g && !s.ground && s.vy < -6.0f && libre) {
            s.sqV += Math.min(1.5f, -s.vy / 14.0f) * 6.0f * I;
        }
        float stretchT = (!g && vy > 2.0f && libre) ? -0.05f * I : 0.0f;
        s.sqV += ((stretchT - s.sq) * 120.0f - s.sqV * 10.0f) * dt;
        s.sq = Mth.clamp(s.sq + s.sqV * dt, -0.2f, 0.4f);
        s.ground = g;
        s.vy = vy;
        // Golpe recibido: empujon hacia atras.
        if (e.hurtTime > 0 && s.hurtPrev == 0) {
            s.leanV -= 120.0f * I;
            s.hurt = 1.0f;
        }
        s.hurtPrev = e.hurtTime;
        s.hurt = Math.max(0.0f, s.hurt - dt * 3.0f);
    }

    // ---------------- Tercera persona: todo el cuerpo ----------------
    static void pre(RenderPlayerEvent.Pre e) {
        if (!PoliciaConfig.mov()) {
            return;
        }
        Player p = e.getEntity();
        if (p.isSleeping()) {
            return;
        }
        St s = st(p);
        PoliciaMovimiento.actualizar(s, p, e.getPartialTick());
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        PUSHED.add(p.getId());
        float yaw = (float) Math.toRadians(Mth.rotLerp(e.getPartialTick(), p.yBodyRotO, p.yBodyRot));
        float fx = -Mth.sin(yaw);
        float fz = Mth.cos(yaw);
        if (Math.abs(s.lean) > 0.01f) {
            // Eje a la derecha del cuerpo: inclina la parte de arriba hacia adelante.
            ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(s.lean), Mth.cos(yaw), 0.0f, Mth.sin(yaw)));
        }
        if (Math.abs(s.bank) > 0.01f) {
            ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(s.bank), fx, 0.0f, fz));
        }
        if (Math.abs(s.sq) > 0.001f) {
            ps.scale(1.0f + s.sq * 0.5f, 1.0f - s.sq, 1.0f + s.sq * 0.5f);
        }
    }

    static void post(RenderPlayerEvent.Post e) {
        if (PUSHED.remove(e.getEntity().getId())) {
            e.getPoseStack().popPose();
        }
    }

    // Lo llama el modelo del jugador (PoliciaHerreroAnim.AnimModel) despues
    // de la animacion normal de Minecraft.
    static void pose(PlayerModel<?> m, AbstractClientPlayer e, float age) {
        if (!PoliciaConfig.mov()) {
            return;
        }
        St s = ST.get(e.getId());
        if (s == null || e.isSleeping()) {
            return;
        }
        float I = PoliciaConfig.intensidad();
        boolean g = e.onGround();
        boolean golpeando = m.attackTime > 0.0f;
        // Correr: brazos y piernas mas amplios, codos un poco afuera.
        if (s.run > 0.05f && g && !golpeando && !e.isCrouching()) {
            float k = 1.0f + 0.3f * s.run * I;
            m.rightArm.xRot *= k;
            m.leftArm.xRot *= k;
            m.rightArm.zRot += 0.08f * s.run;
            m.leftArm.zRot -= 0.08f * s.run;
            m.rightLeg.xRot *= 1.0f + 0.12f * s.run * I;
            m.leftLeg.xRot *= 1.0f + 0.12f * s.run * I;
        }
        // La cabeza se adelanta hacia donde dobla.
        m.head.yRot += Mth.clamp(s.bank * 0.025f, -0.22f, 0.22f);
        // Quieto: respira.
        if (s.spd < 0.3f && g && !e.isCrouching() && !golpeando) {
            float b = Mth.sin(age * 0.067f);
            m.rightArm.zRot += 0.02f * b * I;
            m.leftArm.zRot -= 0.02f * b * I;
            m.head.xRot += 0.015f * b * I;
        }
        // Frenada: una pierna adelante y los brazos para equilibrar.
        if (s.acc < -5.0f && s.spd > 1.0f && g && !golpeando) {
            float k = Mth.clamp((-s.acc - 5.0f) / 10.0f, 0.0f, 1.0f) * Math.min(1.0f, I);
            m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, -0.55f);
            m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, 0.35f);
            m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -0.5f);
            m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -0.3f);
        }
        // Escalera: trepa con brazos y piernas alternados.
        if (e.onClimbable() && !g && !e.isPassenger()) {
            float ph = age * 0.45f;
            float mv = Mth.clamp(Math.abs(s.vy) / 2.0f, 0.0f, 1.0f);
            m.rightArm.xRot = -2.5f + Mth.sin(ph) * 0.45f * mv;
            m.leftArm.xRot = -2.5f - Mth.sin(ph) * 0.45f * mv;
            m.rightArm.zRot = 0.0f;
            m.leftArm.zRot = 0.0f;
            m.rightLeg.xRot = -0.35f - Mth.sin(ph) * 0.35f * mv;
            m.leftLeg.xRot = -0.35f + Mth.sin(ph) * 0.35f * mv;
            m.head.xRot = Math.min(m.head.xRot, -0.2f);
        } else if (!g && !e.isSwimming() && !e.isFallFlying() && !e.isPassenger() && !golpeando) {
            // En el aire: brazos arriba al subir, abiertos al caer fuerte.
            if (s.vy > 1.0f) {
                m.rightArm.xRot -= 0.3f * I;
                m.leftArm.xRot -= 0.3f * I;
                m.rightArm.zRot += 0.15f * I;
                m.leftArm.zRot -= 0.15f * I;
            } else if (s.vy < -6.0f) {
                float k = Mth.clamp((-s.vy - 6.0f) / 10.0f, 0.0f, 1.0f) * I;
                m.rightArm.zRot += 0.6f * k;
                m.leftArm.zRot -= 0.6f * k;
                m.rightLeg.zRot += 0.12f * k;
                m.leftLeg.zRot -= 0.12f * k;
            }
        }
        // Golpe: cabeza para atras.
        if (s.hurt > 0.0f) {
            m.head.xRot -= s.hurt * 0.25f * I;
        }
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    // ---------------- Primera persona ----------------
    static boolean fpInit;
    static long fpT;
    static long manoAplicada;
    static float fpYaw, fpPitch, fpRun, fpDip, fpDipV, lagX, lagY, lagVX, lagVY, roll, fpVy;
    static double fpY;
    static boolean fpGround = true;

    static boolean primeraActiva(Minecraft mc) {
        return PoliciaConfig.primera() && mc.player != null && mc.options.getCameraType().isFirstPerson() && !mc.player.isPassenger();
    }

    static void camara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (!primeraActiva(mc)) {
            fpInit = false;
            fpRun = 0.0f;
            return;
        }
        LocalPlayer p = mc.player;
        float pt = (float) e.getPartialTick();
        long now = System.nanoTime();
        float yaw = p.getViewYRot(pt);
        float pitch = p.getViewXRot(pt);
        double y = Mth.lerp((double) pt, p.yo, p.getY());
        if (!fpInit) {
            fpInit = true;
            fpT = now;
            fpYaw = yaw;
            fpPitch = pitch;
            fpY = y;
            return;
        }
        float dt = Mth.clamp((float) (now - fpT) / 1.0E9f, 0.001f, 0.1f);
        fpT = now;
        float I = PoliciaConfig.intensidad();
        float dyaw = Mth.wrapDegrees(yaw - fpYaw) / dt;
        float dpitch = (pitch - fpPitch) / dt;
        fpYaw = yaw;
        fpPitch = pitch;
        float vy = (float) (y - fpY) / dt;
        fpY = y;
        if (Math.abs(vy) > 60.0f) {
            vy = 0.0f;
        }
        boolean g = p.onGround();
        float runT = p.isSprinting() ? (g ? 1.0f : 0.6f) : 0.0f;
        fpRun += (runT - fpRun) * Math.min(1.0f, dt * 8.0f);
        if (g && !fpGround && fpVy < -6.0f) {
            fpDipV += Math.min(1.5f, -fpVy / 14.0f) * 6.0f * I;
        }
        fpGround = g;
        fpVy = vy;
        fpDipV += (-fpDip * 120.0f - fpDipV * 11.0f) * dt;
        fpDip = Mth.clamp(fpDip + fpDipV * dt, -0.5f, 1.0f);
        float lx = Mth.clamp(-dyaw * 0.002f, -1.0f, 1.0f);
        float ly = Mth.clamp(dpitch * 0.002f, -1.0f, 1.0f);
        lagVX += ((lx - lagX) * 90.0f - lagVX * 13.0f) * dt;
        lagX += lagVX * dt;
        lagVY += ((ly - lagY) * 90.0f - lagVY * 13.0f) * dt;
        lagY += lagVY * dt;
        float rollT = Mth.clamp(dyaw * 0.006f, -2.5f, 2.5f) * I;
        roll += (rollT - roll) * Math.min(1.0f, dt * 6.0f);
        e.setRoll(e.getRoll() + roll);
        e.setPitch(e.getPitch() + fpDip * 2.5f);
    }

    static void mano(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!primeraActiva(mc)) {
            return;
        }
        long now = System.nanoTime();
        boolean principal = e.getHand() == InteractionHand.MAIN_HAND;
        // La transformacion queda puesta para la otra mano en el mismo
        // cuadro: se aplica una sola vez.
        if (!principal && now - manoAplicada < 4000000L) {
            return;
        }
        if (principal) {
            manoAplicada = now;
        }
        PoseStack ps = e.getPoseStack();
        float k = fpRun;
        ps.translate(lagX * 0.06f, -lagY * 0.05f - fpDip * 0.10f - 0.10f * k, 0.10f * k);
        if (k > 0.01f) {
            ps.mulPose(Axis.XP.rotationDegrees(18.0f * k));
        }
    }

    // Lineas de velocidad en los bordes de la pantalla al correr.
    static void lineas(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!PoliciaConfig.lineas() || !primeraActiva(mc) || fpRun < 0.05f || mc.options.hideGui) {
            return;
        }
        Random r = new Random(System.nanoTime() / 60000000L);
        float cx = w / 2.0f;
        float cy = h / 2.0f;
        float rad = (float) Math.sqrt(cx * cx + cy * cy);
        int n = 30;
        for (int i = 0; i < n; i++) {
            if (r.nextFloat() < 0.35f) {
                continue;
            }
            float a = (float) (i * Math.PI * 2.0 / n + r.nextFloat() * 0.15f);
            float largo = rad * (0.12f + r.nextFloat() * 0.16f);
            float r1 = rad * 0.98f;
            int alpha = (int) (fpRun * (60 + r.nextInt(70)));
            int col = (Math.min(255, alpha) << 24) | 0xFFFFFF;
            for (float d = 0.0f; d < largo; d += 3.0f) {
                float rr = r1 - d;
                int x = (int) (cx + Mth.cos(a) * rr);
                int y = (int) (cy + Mth.sin(a) * rr);
                if (x < -2 || y < -2 || x > w + 2 || y > h + 2) {
                    continue;
                }
                g.fill(x, y, x + 2, y + 2, col);
            }
        }
    }
}
