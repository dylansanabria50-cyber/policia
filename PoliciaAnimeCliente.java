package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

// Lado cliente de PoliciaAnime: la babosa de bloques, el monstruo con
// cuernos de alce (modelo propio con codos, rodillas y cuernos/melena con
// resortes) y los efectos de cartas, portales y tesoros.
public class PoliciaAnimeCliente {
    static final ResourceLocation TEX_BABOSA = new ResourceLocation("policia", "textures/entity/babosa.png");
    static final ResourceLocation TEX_MON = new ResourceLocation("policia", "textures/entity/monstruo.png");
    static final ResourceLocation TEX_OJOS = new ResourceLocation("policia", "textures/entity/monstruo_ojos.png");
    static final ResourceLocation TEX_CARTAS = new ResourceLocation("policia", "textures/entity/cartas.png");
    static final ResourceLocation TEX_PORTAL = new ResourceLocation("policia", "textures/entity/portal.png");

    // id de entidad -> {tick de inicio, tick de fin} de la forma de monstruo
    static final Map<Integer, long[]> FORMAS = new HashMap<Integer, long[]>();
    static final Map<Integer, Fisica> FISICA = new HashMap<Integer, Fisica>();
    static final List<Ef> EFS = new ArrayList<Ef>();
    static ModelPart MONSTRUO;

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaAnimeCliente::renderers);
        MinecraftForge.EVENT_BUS.addListener(PoliciaAnimeCliente::jugador);
        MinecraftForge.EVENT_BUS.addListener(PoliciaAnimeCliente::dibujar);
        MinecraftForge.EVENT_BUS.addListener(PoliciaAnimeCliente::tick);
    }

    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(PoliciaAnime.BABOSA.get(), BabosaRender::new);
        e.registerEntityRenderer(PoliciaMercenario.MERC.get(), PoliciaMercenarioRender::new);
    }

    static long ahora() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    public static void recibir(String tipo, CompoundTag d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long t = ahora();
        switch (tipo) {
            case "forma" -> {
                int ent = d.getInt("ent");
                int ticks = d.getInt("ticks");
                if (ticks <= 0) {
                    FORMAS.remove(ent);
                    FISICA.remove(ent);
                } else {
                    long[] v = FORMAS.get(ent);
                    long ini = v != null ? v[0] : t - (PoliciaAnime.FORMA_TICKS - ticks);
                    FORMAS.put(ent, new long[]{ini, t + ticks});
                }
            }
            case "tesoro_ola" -> {
                ListTag l = d.getList("obj", Tag.TAG_COMPOUND);
                for (int i = 0; i < l.size(); i++) {
                    CompoundTag o = l.getCompound(i).copy();
                    o.putDouble("px", d.getDouble("px"));
                    o.putDouble("py", d.getDouble("py"));
                    o.putDouble("pz", d.getDouble("pz"));
                    o.putInt("item", mc.level.random.nextInt(TESOROS.length));
                    EFS.add(new Ef("proyectil", o, t + (i % 3), 6));
                }
            }
            default -> {
                if (tipo.startsWith("ui_")) {
                    PoliciaUICliente.recibir(tipo, d);
                    return;
                }
                int dur = d.contains("dur") ? d.getInt("dur") : 36;
                EFS.add(new Ef(tipo, d, t, dur));
            }
        }
    }

    static boolean monstruo(Entity e) {
        long[] v = FORMAS.get(e.getId());
        return v != null && ahora() < v[1];
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            FORMAS.clear();
            FISICA.clear();
            EFS.clear();
            return;
        }
        long t = ahora();
        FORMAS.entrySet().removeIf(en -> t > en.getValue()[1] + 2);
        FISICA.keySet().removeIf(k -> !FORMAS.containsKey(k));
    }

    // ================= Babosa =================
    public static class BabosaRender extends MobRenderer<PoliciaAnime.Babosa, SlimeModel<PoliciaAnime.Babosa>> {
        public BabosaRender(EntityRendererProvider.Context ctx) {
            super(ctx, new SlimeModel<>(ctx.bakeLayer(ModelLayers.SLIME)), 0.25f);
            this.addLayer(new SlimeOuterLayer<>(this, ctx.getModelSet()));
        }

        @Override
        public void render(PoliciaAnime.Babosa e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
            this.shadowRadius = 0.25f * e.tam();
            super.render(e, yaw, pt, ps, buf, luz);
        }

        @Override
        protected void scale(PoliciaAnime.Babosa e, PoseStack ps, float pt) {
            ps.scale(0.999f, 0.999f, 0.999f);
            ps.translate(0.0f, 0.001f, 0.0f);
            float s = e.tam();
            float sq = Mth.lerp(pt, e.oSquish, e.squish) / (s * 0.5f + 1.0f);
            float k = 1.0f / (sq + 1.0f);
            ps.scale(k * s, 1.0f / k * s, k * s);
        }

        @Override
        public ResourceLocation getTextureLocation(PoliciaAnime.Babosa e) {
            return TEX_BABOSA;
        }
    }

    // ================= Monstruo =================
    static ModelPart modelo() {
        if (MONSTRUO == null) {
            MeshDefinition md = new MeshDefinition();
            PartDefinition root = md.getRoot();
        PartDefinition cadera = root.addOrReplaceChild("cadera", CubeListBuilder.create().texOffs(56, 20).addBox(-4.5f, -2f, -3f, 9, 4, 6), PartPose.offsetAndRotation(0f, 11f, 0f, 0f, 0f, 0f));
        PartDefinition torso = cadera.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-5.5f, -13f, -3.5f, 11, 13, 7).texOffs(86, 20).addBox(-4.5f, -12f, -5.5f, 9, 7, 2).texOffs(92, 32).addBox(-4f, -15f, -0.5f, 8, 3, 4), PartPose.offsetAndRotation(0f, -1f, 0f, 0.3f, 0f, 0f));
        PartDefinition cabeza = torso.addOrReplaceChild("cabeza", CubeListBuilder.create().texOffs(72, 0).addBox(-3.5f, -6f, -6f, 7, 6, 7).texOffs(108, 20).addBox(-2.5f, -4f, -11f, 5, 4, 5).texOffs(18, 48).addBox(-1f, 0f, -5f, 2, 3, 2), PartPose.offsetAndRotation(0f, -13f, -2.5f, -0.3f, 0f, 0f));
        PartDefinition mandibula = cabeza.addOrReplaceChild("mandibula", CubeListBuilder.create().texOffs(40, 41).addBox(-2f, 0f, -5f, 4, 1, 5), PartPose.offsetAndRotation(0f, 0f, -6f, 0f, 0f, 0f));
        PartDefinition orejaD = cabeza.addOrReplaceChild("orejaD", CubeListBuilder.create().texOffs(46, 48).addBox(-3f, -1f, -0.5f, 3, 2, 1), PartPose.offsetAndRotation(-3.5f, -5f, -1f, 0f, 0f, 0.4f));
        PartDefinition cuernoD = cabeza.addOrReplaceChild("cuernoD", CubeListBuilder.create().texOffs(26, 48).addBox(-3f, -1f, -1f, 3, 2, 2), PartPose.offsetAndRotation(-3f, -6f, -2f, 0f, 0f, 0f));
        PartDefinition palmaD = cuernoD.addOrReplaceChild("palmaD", CubeListBuilder.create().texOffs(32, 32).addBox(-8f, -1f, -3.5f, 8, 1, 7).texOffs(54, 48).addBox(-8f, -3f, -3.5f, 1, 2, 1).texOffs(58, 48).addBox(-8f, -3f, -1.5f, 1, 2, 1).texOffs(62, 48).addBox(-8f, -3f, 0.5f, 1, 2, 1).texOffs(66, 48).addBox(-8f, -3f, 2.5f, 1, 2, 1).texOffs(70, 48).addBox(-5f, -3f, 2.5f, 1, 2, 1).texOffs(74, 48).addBox(-2f, -1f, -5.5f, 1, 1, 2), PartPose.offsetAndRotation(-3f, -0.5f, 0f, 0f, 0f, 0.45f));
        PartDefinition brazoD = torso.addOrReplaceChild("brazoD", CubeListBuilder.create().texOffs(36, 0).addBox(-3f, -1.5f, -2.5f, 4, 9, 5), PartPose.offsetAndRotation(-6.5f, -11.5f, 0f, 0f, 0f, 0f));
        PartDefinition antebrazoD = brazoD.addOrReplaceChild("antebrazoD", CubeListBuilder.create().texOffs(100, 0).addBox(-2f, 0f, -2f, 4, 8, 4).texOffs(0, 41).addBox(-2.5f, 8f, -2.5f, 5, 2, 5).texOffs(80, 48).addBox(-2.5f, 10f, -2.5f, 1, 2, 1).texOffs(84, 48).addBox(-0.5f, 10f, -2.5f, 1, 2, 1).texOffs(88, 48).addBox(1.5f, 10f, -2.5f, 1, 2, 1), PartPose.offsetAndRotation(-1f, 7.5f, 0f, 0f, 0f, 0f));
        PartDefinition melena0 = torso.addOrReplaceChild("melena0", CubeListBuilder.create().texOffs(58, 41).addBox(-3.0f, 0f, 0f, 6, 4, 2), PartPose.offsetAndRotation(0f, -12.0f, 3.5f, 0.2f, 0f, 0f));
        PartDefinition melena1 = torso.addOrReplaceChild("melena1", CubeListBuilder.create().texOffs(74, 41).addBox(-2.5f, 0f, 0f, 5, 4, 2), PartPose.offsetAndRotation(0f, -8.5f, 3.5f, 0.2f, 0f, 0f));
        PartDefinition melena2 = torso.addOrReplaceChild("melena2", CubeListBuilder.create().texOffs(88, 41).addBox(-2.0f, 0f, 0f, 4, 4, 2), PartPose.offsetAndRotation(0f, -5.0f, 3.5f, 0.2f, 0f, 0f));
        PartDefinition musloD = cadera.addOrReplaceChild("musloD", CubeListBuilder.create().texOffs(16, 20).addBox(-2.5f, 0f, -2.5f, 5, 6, 5), PartPose.offsetAndRotation(-2.5f, 1f, 0f, 0f, 0f, 0f));
        PartDefinition piernaD = musloD.addOrReplaceChild("piernaD", CubeListBuilder.create().texOffs(0, 32).addBox(-2f, 0f, -2f, 4, 5, 4).texOffs(100, 41).addBox(-2f, 5f, -3f, 4, 1, 5), PartPose.offsetAndRotation(0f, 6f, 0f, 0f, 0f, 0f));
        PartDefinition orejaI = cabeza.addOrReplaceChild("orejaI", CubeListBuilder.create().texOffs(92, 48).addBox(0f, -1f, -0.5f, 3, 2, 1), PartPose.offsetAndRotation(3.5f, -5f, -1f, 0f, 0f, -0.4f));
        PartDefinition cuernoI = cabeza.addOrReplaceChild("cuernoI", CubeListBuilder.create().texOffs(36, 48).addBox(0f, -1f, -1f, 3, 2, 2), PartPose.offsetAndRotation(3f, -6f, -2f, 0f, 0f, 0f));
        PartDefinition palmaI = cuernoI.addOrReplaceChild("palmaI", CubeListBuilder.create().texOffs(62, 32).addBox(0f, -1f, -3.5f, 8, 1, 7).texOffs(100, 48).addBox(7f, -3f, -3.5f, 1, 2, 1).texOffs(104, 48).addBox(7f, -3f, -1.5f, 1, 2, 1).texOffs(108, 48).addBox(7f, -3f, 0.5f, 1, 2, 1).texOffs(112, 48).addBox(7f, -3f, 2.5f, 1, 2, 1).texOffs(116, 48).addBox(4f, -3f, 2.5f, 1, 2, 1).texOffs(120, 48).addBox(1f, -1f, -5.5f, 1, 1, 2), PartPose.offsetAndRotation(3f, -0.5f, 0f, 0f, 0f, -0.45f));
        PartDefinition brazoI = torso.addOrReplaceChild("brazoI", CubeListBuilder.create().texOffs(54, 0).addBox(-1f, -1.5f, -2.5f, 4, 9, 5), PartPose.offsetAndRotation(6.5f, -11.5f, 0f, 0f, 0f, 0f));
        PartDefinition antebrazoI = brazoI.addOrReplaceChild("antebrazoI", CubeListBuilder.create().texOffs(0, 20).addBox(-2f, 0f, -2f, 4, 8, 4).texOffs(20, 41).addBox(-2.5f, 8f, -2.5f, 5, 2, 5).texOffs(0, 54).addBox(1.5f, 10f, -2.5f, 1, 2, 1).texOffs(4, 54).addBox(-0.5f, 10f, -2.5f, 1, 2, 1).texOffs(8, 54).addBox(-2.5f, 10f, -2.5f, 1, 2, 1), PartPose.offsetAndRotation(1f, 7.5f, 0f, 0f, 0f, 0f));
        PartDefinition musloI = cadera.addOrReplaceChild("musloI", CubeListBuilder.create().texOffs(36, 20).addBox(-2.5f, 0f, -2.5f, 5, 6, 5), PartPose.offsetAndRotation(2.5f, 1f, 0f, 0f, 0f, 0f));
        PartDefinition piernaI = musloI.addOrReplaceChild("piernaI", CubeListBuilder.create().texOffs(16, 32).addBox(-2f, 0f, -2f, 4, 5, 4).texOffs(0, 48).addBox(-2f, 5f, -3f, 4, 1, 5), PartPose.offsetAndRotation(0f, 6f, 0f, 0f, 0f, 0f));
            MONSTRUO = LayerDefinition.create(md, 128, 128).bakeRoot();
        }
        return MONSTRUO;
    }

    static ModelPart p(String... ruta) {
        ModelPart m = modelo();
        for (String r : ruta) {
            m = m.getChild(r);
        }
        return m;
    }

    // Resortes de los cuernos y la melena (uno por jugador transformado).
    static class Fisica {
        long nanos;
        float yawPrev = Float.NaN;
        float bobPrev;
        float bobVel;
        float cz, czv, cy, cyv;
        float[] mel = new float[3];
        float[] melv = new float[3];
        int golpePrev;
        float atkPrev;

        void paso(float dt, float dYaw, float bobAcc, float avance) {
            float h = 0.004f;
            int n = Math.max(1, Math.min(20, (int) (dt / h)));
            h = dt / n;
            for (int i = 0; i < n; i++) {
                float az = -170.0f * this.cz - 9.0f * this.czv + bobAcc * 0.0035f;
                float ay = -140.0f * this.cy - 8.0f * this.cyv - dYaw * 0.9f;
                this.czv += az * h;
                this.cz += this.czv * h;
                this.cyv += ay * h;
                this.cy += this.cyv * h;
                for (int k = 0; k < 3; k++) {
                    float am = -70.0f * this.mel[k] - 5.0f * this.melv[k] + bobAcc * 0.004f * (1 + k * 0.4f) - avance * 6.0f;
                    this.melv[k] += am * h;
                    this.mel[k] += this.melv[k] * h;
                }
            }
            this.cz = Mth.clamp(this.cz, -0.45f, 0.45f);
            this.cy = Mth.clamp(this.cy, -0.4f, 0.4f);
            for (int k = 0; k < 3; k++) {
                this.mel[k] = Mth.clamp(this.mel[k], -0.6f, 0.8f);
            }
        }
    }

    static float suave(float x) {
        x = Mth.clamp(x, 0.0f, 1.0f);
        return x * x * (3 - 2 * x);
    }

    static void jugador(RenderPlayerEvent.Pre e) {
        Player pl = e.getEntity();
        long[] v = FORMAS.get(pl.getId());
        if (v == null) {
            return;
        }
        float pt = e.getPartialTick();
        float ahora = ahora() + pt;
        if (ahora >= v[1] || pl.isSleeping() || pl.isFallFlying() || pl.isVisuallySwimming()) {
            return;
        }
        e.setCanceled(true);
        if (pl.isInvisible()) {
            return;
        }
        float desde = ahora - v[0];
        float hasta = v[1] - ahora;
        float crece = Math.min(suave(desde / 12.0f), suave(hasta / 8.0f));
        ModelPart root = modelo();
        root.getAllParts().forEach(ModelPart::resetPose);

        float body = Mth.rotLerp(pt, pl.yBodyRotO, pl.yBodyRot);
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(pt, pl.yHeadRotO, pl.yHeadRot) - body);
        float pitch = Mth.lerp(pt, pl.xRotO, pl.getXRot());
        float edad = pl.tickCount + pt;
        float pos = pl.walkAnimation.position(pt);
        float amt = Math.min(1.0f, pl.walkAnimation.speed(pt));
        if (pl.isPassenger()) {
            amt = 0;
        }
        float w = pos * 0.6662f;
        final float PI = (float) Math.PI;

        ModelPart cadera = p("cadera");
        ModelPart torso = p("cadera", "torso");
        ModelPart cabeza = p("cadera", "torso", "cabeza");
        ModelPart mand = p("cadera", "torso", "cabeza", "mandibula");
        ModelPart brD = p("cadera", "torso", "brazoD");
        ModelPart brI = p("cadera", "torso", "brazoI");
        ModelPart abD = p("cadera", "torso", "brazoD", "antebrazoD");
        ModelPart abI = p("cadera", "torso", "brazoI", "antebrazoI");
        ModelPart muD = p("cadera", "musloD");
        ModelPart muI = p("cadera", "musloI");
        ModelPart piD = p("cadera", "musloD", "piernaD");
        ModelPart piI = p("cadera", "musloI", "piernaI");
        ModelPart cuD = p("cadera", "torso", "cabeza", "cuernoD");
        ModelPart cuI = p("cadera", "torso", "cabeza", "cuernoI");
        ModelPart orD = p("cadera", "torso", "cabeza", "orejaD");
        ModelPart orI = p("cadera", "torso", "cabeza", "orejaI");

        // ---- caminar: muslos, rodillas, cadera y brazos pesados ----
        muD.xRot = Mth.cos(w) * 0.85f * amt;
        muI.xRot = Mth.cos(w + PI) * 0.85f * amt;
        piD.xRot = 0.12f + Math.max(0.0f, -Mth.sin(w)) * 1.1f * amt;
        piI.xRot = 0.12f + Math.max(0.0f, Mth.sin(w)) * 1.1f * amt;
        muD.xRot -= 0.12f;
        muI.xRot -= 0.12f;
        float bob = Math.abs(Mth.sin(w)) * 1.4f * amt;
        cadera.y += bob;
        cadera.yRot = Mth.cos(w) * 0.1f * amt;
        torso.zRot = Mth.cos(w) * 0.07f * amt;
        torso.xRot += amt * 0.12f + Mth.sin(edad * 0.08f) * 0.035f;
        brD.xRot = Mth.cos(w + PI) * 0.75f * amt - 0.1f;
        brI.xRot = Mth.cos(w) * 0.75f * amt - 0.1f;
        brD.zRot = 0.16f + Mth.sin(edad * 0.08f) * 0.03f;
        brI.zRot = -0.16f - Mth.sin(edad * 0.08f) * 0.03f;
        abD.xRot = -0.35f - Math.max(0.0f, Mth.sin(w + PI)) * 0.6f * amt;
        abI.xRot = -0.35f - Math.max(0.0f, Mth.sin(w)) * 0.6f * amt;
        if (pl.isCrouching()) {
            cadera.y += 2.5f;
            torso.xRot += 0.35f;
            cabeza.xRot -= 0.3f;
            muD.xRot -= 0.5f;
            muI.xRot -= 0.5f;
            piD.xRot += 0.8f;
            piI.xRot += 0.8f;
        }
        // ---- cabeza: mira hacia donde mira el jugador ----
        cabeza.yRot = Mth.clamp(headYaw, -70, 70) * PI / 180.0f;
        cabeza.xRot += pitch * PI / 180.0f * 0.8f;
        mand.xRot = 0.06f + Mth.sin(edad * 0.1f) * 0.05f;
        orD.zRot += Mth.sin(edad * 0.31f) * 0.08f;
        orI.zRot -= Mth.sin(edad * 0.27f + 1) * 0.08f;
        // ---- golpe: zarpazo con el codo que se estira ----
        float atk = pl.getAttackAnim(pt);
        if (atk > 0) {
            float s = Mth.sin(atk * PI);
            float s2 = Mth.sin(Mth.sqrt(atk) * PI);
            boolean der = (pl.swingingArm == InteractionHand.MAIN_HAND) == (pl.getMainArm() == HumanoidArm.RIGHT);
            ModelPart br = der ? brD : brI;
            ModelPart ab = der ? abD : abI;
            br.xRot = -2.6f * s2 + 0.8f * s;
            br.zRot += (der ? 1 : -1) * 0.3f * s;
            ab.xRot = -1.2f * Mth.sin(Math.min(1.0f, atk * 1.6f) * PI) * (1 - atk) - 0.2f;
            torso.yRot += (der ? 1 : -1) * 0.45f * s;
            torso.xRot += 0.2f * s;
            mand.xRot = 0.55f * s;
        }
        if (pl.isUsingItem() || pl.isBlocking()) {
            brD.xRot = -1.1f;
            brI.xRot = -1.1f;
            abD.xRot = -1.0f;
            abI.xRot = -1.0f;
            brD.yRot = 0.4f;
            brI.yRot = -0.4f;
        }
        // ---- rugido al transformarse ----
        if (desde < 30) {
            float r = Mth.sin(Mth.clamp((desde - 4) / 24.0f, 0, 1) * PI);
            cabeza.xRot -= 0.7f * r;
            mand.xRot = Math.max(mand.xRot, 0.8f * r);
            brD.zRot += 0.9f * r;
            brI.zRot -= 0.9f * r;
            brD.xRot -= 0.5f * r;
            brI.xRot -= 0.5f * r;
            abD.xRot -= 0.6f * r;
            abI.xRot -= 0.6f * r;
            torso.xRot -= 0.25f * r;
        }
        // ---- fisica de cuernos y melena ----
        Fisica f = FISICA.computeIfAbsent(pl.getId(), k -> new Fisica());
        long n = System.nanoTime();
        float dt = f.nanos == 0 ? 0.016f : Mth.clamp((n - f.nanos) / 1.0e9f, 0.0f, 0.05f);
        f.nanos = n;
        float yawW = body + headYaw;
        float dYaw = Float.isNaN(f.yawPrev) || dt <= 0 ? 0 : Mth.wrapDegrees(yawW - f.yawPrev) / dt * PI / 180.0f;
        f.yawPrev = yawW;
        float bobV = dt <= 0 ? 0 : (bob - f.bobPrev) / dt;
        float bobAcc = dt <= 0 ? 0 : (bobV - f.bobVel) / dt;
        f.bobPrev = bob;
        f.bobVel = bobV;
        if (pl.hurtTime == 9 && f.golpePrev != 9) {
            f.czv += 6.0f;
            f.cyv += (pl.getRandom().nextBoolean() ? 4 : -4);
        }
        f.golpePrev = pl.hurtTime;
        if (atk > 0 && f.atkPrev == 0) {
            f.cyv += 3.5f;
        }
        f.atkPrev = atk;
        if (dt > 0) {
            f.paso(dt, Mth.clamp(dYaw, -30, 30), Mth.clamp(bobAcc, -4000, 4000), amt * (pl.isSprinting() ? 1.6f : 1.0f));
        }
        cuD.zRot += f.cz;
        cuI.zRot -= f.cz;
        cuD.yRot += f.cy;
        cuI.yRot += f.cy;
        for (int k = 0; k < 3; k++) {
            p("cadera", "torso", "melena" + k).xRot += f.mel[k] + amt * 0.35f;
        }

        // ---- dibujar ----
        PoseStack ps = e.getPoseStack();
        MultiBufferSource buf = e.getMultiBufferSource();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - body));
        float esc = Mth.lerp(crece, 0.9375f, 1.2f);
        if (desde < 12) {
            esc += Mth.sin(desde * 2.2f) * 0.04f * (1 - desde / 12.0f);
        }
        ps.scale(-esc, -esc, esc);
        ps.translate(0.0f, -1.501f, 0.0f);
        int over = LivingEntityRenderer.getOverlayCoords(pl, 0.0f);
        root.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(TEX_MON)), e.getPackedLight(), over);
        root.render(ps, buf.getBuffer(RenderType.eyes(TEX_OJOS)), 0xF000F0, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    // ================= Efectos: cartas, portales y tesoros =================
    static class Ef {
        String tipo;
        CompoundTag d;
        long t0;
        int dur;
        boolean estallo;

        Ef(String tipo, CompoundTag d, long t0, int dur) {
            this.tipo = tipo;
            this.d = d;
            this.t0 = t0;
            this.dur = dur;
        }
    }

    static final ItemStack[] TESOROS = new ItemStack[]{new ItemStack(Items.GOLDEN_SWORD), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD), new ItemStack(Items.GOLDEN_AXE), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.AMETHYST_SHARD)};

    static Vec3 lerpPos(Entity e, float pt) {
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    static void quad(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x0, float y0, float x1, float y1, float z, float u0, float v0, float u1, float v1, int luz, float r, float g, float b, float a) {
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(nm, 0, 0, 1).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(nm, 0, 0, 1).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(nm, 0, 0, 1).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(nm, 0, 0, 1).endVertex();
    }

    static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || EFS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        float pt = e.getPartialTick();
        float ahora = ahora() + pt;
        Iterator<Ef> it = EFS.iterator();
        while (it.hasNext()) {
            Ef ef = it.next();
            float t = ahora - ef.t0;
            if (t < 0) {
                continue;
            }
            if (t > ef.dur + 1) {
                it.remove();
                continue;
            }
            try {
                switch (ef.tipo) {
                    case "cartas" -> cartas(mc, ef, t, pt, ps, buf, cp);
                    case "tesoro" -> tesoro(mc, ef, t, pt, ps, buf, cp);
                    case "portal" -> portalFijo(mc, ef, t, ps, buf, cp);
                    case "proyectil" -> proyectil(mc, ef, t, pt, ps, buf, cp);
                    default -> it.remove();
                }
            } catch (Throwable ex) {
                it.remove();
            }
        }
        buf.endBatch();
    }

    // Tres cartas salen del pecho, se abren en abanico, se dan vuelta y vuelan.
    static void cartas(Minecraft mc, Ef ef, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 cp) {
        Entity due = mc.level.getEntity(ef.d.getInt("ent"));
        if (due == null) {
            ef.dur = 0;
            return;
        }
        Entity obj = ef.d.getInt("obj") >= 0 ? mc.level.getEntity(ef.d.getInt("obj")) : null;
        int[] tipos = ef.d.getIntArray("tipos");
        Vec3 base = lerpPos(due, pt);
        float yaw = due instanceof Player pl ? Mth.rotLerp(pt, pl.yBodyRotO, pl.yBodyRot) : due.getYRot();
        double ry = Math.toRadians(yaw);
        Vec3 fw = new Vec3(-Math.sin(ry), 0, Math.cos(ry));
        Vec3 rt = new Vec3(-Math.cos(ry), 0, -Math.sin(ry));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX_CARTAS));
        for (int i = 0; i < tipos.length; i++) {
            float sube = suave(t / 10.0f);
            Vec3 pecho = base.add(0, 1.2, 0).add(fw.scale(0.3));
            Vec3 arriba = base.add(0, 2.35 + Mth.sin((t + i * 4) * 0.35f) * 0.06, 0).add(fw.scale(0.6)).add(rt.scale((i - 1) * 0.55));
            Vec3 pos = pecho.lerp(arriba, sube);
            float giro = 180.0f * (1.0f - suave((t - 7) / 6.0f));
            float rotZ = (i - 1) * -12.0f * sube;
            float esc = 0.4f + 0.6f * sube;
            int tp = tipos[i];
            if (t > 22) {
                float k = suave((t - 22 - i) / 8.0f);
                Vec3 dest;
                if (tp == 1 && obj != null) {
                    dest = lerpPos(obj, pt).add(0, obj.getBbHeight() * 0.6, 0);
                } else if (tp == 3) {
                    double a = t * 0.6 + i * 2.1;
                    dest = base.add(Math.cos(a) * 0.8, 0.3, Math.sin(a) * 0.8);
                } else {
                    dest = base.add(0, 1.1, 0).add(fw.scale(0.25));
                }
                Vec3 arco = arriba.lerp(dest, k).add(0, Mth.sin(k * (float) Math.PI) * 0.6, 0);
                pos = arco;
                giro += k * 720.0f;
                esc *= 1.0f - 0.5f * k;
                if (k >= 1.0f) {
                    if (!ef.estallo && i == tipos.length - 1) {
                        ef.estallo = true;
                    }
                    if (t < 31.5f) {
                        for (int q = 0; q < 2; q++) {
                            mc.level.addParticle(tp == 0 ? ParticleTypes.HEART : tp == 1 ? ParticleTypes.FLAME : tp == 2 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.CLOUD, pos.x + (mc.level.random.nextDouble() - 0.5) * 0.5, pos.y + (mc.level.random.nextDouble() - 0.5) * 0.5, pos.z + (mc.level.random.nextDouble() - 0.5) * 0.5, 0, 0.05, 0);
                        }
                    }
                    continue;
                }
            }
            ps.pushPose();
            ps.translate(pos.x - cp.x, pos.y - cp.y, pos.z - cp.z);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw + giro));
            ps.mulPose(Axis.ZP.rotationDegrees(rotZ));
            ps.scale(esc, esc, esc);
            Matrix4f m = ps.last().pose();
            Matrix3f nm = ps.last().normal();
            float u0 = (1 + tp) * 16 / 128.0f;
            float u1 = u0 + 16 / 128.0f;
            quad(vc, m, nm, 0.22f, -0.33f, -0.22f, 0.33f, 0.004f, u0, 0, u1, 24 / 32.0f, 0xF000F0, 1, 1, 1, 1);
            quad(vc, m, nm, -0.22f, -0.33f, 0.22f, 0.33f, -0.004f, 0, 0, 16 / 128.0f, 24 / 32.0f, 0xF000F0, 1, 1, 1, 1);
            ps.popPose();
        }
    }

    static void anillo(PoseStack ps, MultiBufferSource buf, Vec3 pos, Vec3 cp, float yaw, float radio, float giro, float r, float g, float b) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(TEX_PORTAL));
        ps.pushPose();
        ps.translate(pos.x - cp.x, pos.y - cp.y, pos.z - cp.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        for (int capa = 0; capa < 2; capa++) {
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(capa == 0 ? giro : -giro * 1.7f));
            float rr = radio * (capa == 0 ? 1.0f : 0.72f);
            Matrix4f m = ps.last().pose();
            Matrix3f nm = ps.last().normal();
            quad(vc, m, nm, -rr, -rr, rr, rr, capa * 0.01f, 0, 0, 1, 1, 0xF000F0, r, g, b, capa == 0 ? 0.95f : 0.7f);
            ps.popPose();
        }
        ps.popPose();
    }

    // Portal dorado sobre el jugador (Puerta del tesoro).
    static void tesoro(Minecraft mc, Ef ef, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 cp) {
        Entity due = mc.level.getEntity(ef.d.getInt("ent"));
        if (due == null) {
            ef.dur = 0;
            return;
        }
        float yaw = due instanceof Player pl ? Mth.rotLerp(pt, pl.yBodyRotO, pl.yBodyRot) : due.getYRot();
        double ry = Math.toRadians(yaw);
        Vec3 pos = lerpPos(due, pt).add(0, 3.2, 0).add(Math.sin(ry) * 0.8, 0, -Math.cos(ry) * 0.8);
        float abre = Math.min(suave(t / 8.0f), suave((ef.dur - t) / 8.0f));
        anillo(ps, buf, pos, cp, yaw, 1.3f * abre, t * 9.0f, 1.0f, 0.82f, 0.32f);
        if (mc.level.random.nextInt(3) == 0 && abre > 0.5f) {
            double a = mc.level.random.nextDouble() * Math.PI * 2;
            mc.level.addParticle(ParticleTypes.WAX_OFF, pos.x + Math.cos(a) * 1.2 * Math.cos(ry), pos.y + Math.sin(a) * 1.2, pos.z + Math.cos(a) * 1.2 * Math.sin(ry), 0, 0, 0);
        }
    }

    // Portal violeta fijo (Tienda del otro mundo).
    static void portalFijo(Minecraft mc, Ef ef, float t, PoseStack ps, MultiBufferSource buf, Vec3 cp) {
        Vec3 pos = new Vec3(ef.d.getDouble("x"), ef.d.getDouble("y"), ef.d.getDouble("z"));
        float abre = Math.min(suave(t / 10.0f), suave((ef.dur - t) / 10.0f));
        anillo(ps, buf, pos, cp, ef.d.getFloat("yaw"), 1.35f * abre, t * 7.0f, 0.75f, 0.35f, 1.0f);
        if (mc.level.random.nextInt(2) == 0 && abre > 0.3f) {
            mc.level.addParticle(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, (mc.level.random.nextDouble() - 0.5) * 2, (mc.level.random.nextDouble() - 0.5) * 2, (mc.level.random.nextDouble() - 0.5) * 2);
        }
    }

    // Un tesoro que vuela del portal al enemigo.
    static void proyectil(Minecraft mc, Ef ef, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 cp) {
        Vec3 a = new Vec3(ef.d.getDouble("px"), ef.d.getDouble("py"), ef.d.getDouble("pz"));
        Entity obj = mc.level.getEntity(ef.d.getInt("id"));
        Vec3 b = obj != null ? lerpPos(obj, pt).add(0, obj.getBbHeight() * 0.6, 0) : new Vec3(ef.d.getDouble("x"), ef.d.getDouble("y"), ef.d.getDouble("z"));
        float k = Mth.clamp(t / ef.dur, 0, 1);
        Vec3 pos = a.lerp(b, k);
        if (k >= 1.0f) {
            if (!ef.estallo) {
                ef.estallo = true;
                for (int i = 0; i < 6; i++) {
                    mc.level.addParticle(ParticleTypes.WAX_OFF, pos.x, pos.y, pos.z, (mc.level.random.nextDouble() - 0.5) * 0.3, mc.level.random.nextDouble() * 0.2, (mc.level.random.nextDouble() - 0.5) * 0.3);
                }
            }
            return;
        }
        Vec3 d = b.subtract(a);
        float yaw = (float) (Mth.atan2(d.x, d.z) * 180 / Math.PI);
        float pitch = (float) (Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
        ps.pushPose();
        ps.translate(pos.x - cp.x, pos.y - cp.y, pos.z - cp.z);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.mulPose(Axis.XP.rotationDegrees(-pitch));
        ps.mulPose(Axis.ZP.rotationDegrees(t * 70));
        ps.scale(0.8f, 0.8f, 0.8f);
        mc.getItemRenderer().renderStatic(TESOROS[Math.floorMod(ef.d.getInt("item"), TESOROS.length)], ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, 0);
        ps.popPose();
        if (mc.level.random.nextInt(2) == 0) {
            mc.level.addParticle(ParticleTypes.WAX_OFF, pos.x, pos.y, pos.z, 0, 0, 0);
        }
    }
}
