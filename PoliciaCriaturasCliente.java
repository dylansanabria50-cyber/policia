package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataInputStream;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Cliente de las criaturas: arma el modelo generado desde la vista previa,
// reproduce las poses horneadas (con mezcla entre animaciones y un freno en
// cada impacto), calcula los resortes en vivo, dibuja la capa que brilla y
// las estelas de los ataques. Tambien el temblor de camara y la Luna roja.
public class PoliciaCriaturasCliente {
    static final float PAUSA = 2.5f;
    static boolean LUNA;
    static float temblor;

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaCriaturasCliente::renderers);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCriaturasCliente::tick);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCriaturasCliente::camara);
        MinecraftForge.EVENT_BUS.addListener(PoliciaCriaturasCliente::niebla);
    }

    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        for (int i = 0; i < PoliciaCriaturas.ID.length; i++) {
            final int k = i;
            e.registerEntityRenderer(PoliciaCriaturas.TIPOS.get(i).get(), ctx -> new Render(ctx, k));
        }
    }

    public static void recibir(String tipo, CompoundTag d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        switch (tipo) {
            case "luna_roja" -> LUNA = d.getBoolean("roja");
            case "criatura_temblor" -> {
                double dist = mc.player.position().distanceTo(new Vec3(d.getDouble("x"), d.getDouble("y"), d.getDouble("z")));
                double r = d.getDouble("r");
                if (dist < r) {
                    temblor = Math.max(temblor, d.getFloat("f") * (float) (1 - dist / r));
                }
            }
            case "criatura_raices" -> {
                double x = d.getDouble("x");
                double y = d.getDouble("y");
                double z = d.getDouble("z");
                BlockParticleOption raiz = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
                BlockParticleOption musgo = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState());
                for (int k = 0; k < 14; k++) {
                    double a = k / 14.0 * Math.PI * 2;
                    double rr = 1.3 + (k % 2) * 0.6;
                    for (int h = 0; h < 6; h++) {
                        mc.level.addParticle(h % 2 == 0 ? raiz : musgo, x + Math.cos(a) * rr, y + h * 0.3, z + Math.sin(a) * rr, 0, 0.25, 0);
                    }
                }
            }
            default -> {
            }
        }
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            temblor *= 0.82f;
            if (temblor < 0.01f) {
                temblor = 0;
            }
            if (Minecraft.getInstance().level == null) {
                LUNA = false;
            }
        }
    }

    static void camara(ViewportEvent.ComputeCameraAngles e) {
        if (temblor > 0) {
            float t = (float) (System.nanoTime() / 1.0e9);
            e.setPitch(e.getPitch() + Mth.sin(t * 61) * temblor * 1.6f);
            e.setYaw(e.getYaw() + Mth.sin(t * 47 + 1) * temblor * 1.2f);
            e.setRoll(e.getRoll() + Mth.sin(t * 53 + 2) * temblor * 2.2f);
        }
    }

    // Luna roja: la niebla y el horizonte se tiñen de rojo de noche.
    static void niebla(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        if (!LUNA || mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
            return;
        }
        float noche = Mth.clamp(1.0f - mc.level.getSkyDarken(1.0f) / 11.0f, 0, 1);
        noche = 1 - noche;
        float k = 0.35f + 0.55f * noche;
        e.setRed(Mth.lerp(k, e.getRed(), 0.55f));
        e.setGreen(Mth.lerp(k, e.getGreen(), 0.06f));
        e.setBlue(Mth.lerp(k, e.getBlue(), 0.05f));
    }

    // ================= Datos de cada criatura =================
    static class Datos {
        String[] huesos;
        int[] padres;
        float[][] resortes;
        float[][] estelas;
        int estelaColor;
        boolean translucido;
        float escala;
        float[][][][] anims;
        float[] durs;
        int cabeza = -1;
    }

    static LayerDefinition capa(int tipo) {
        return switch (tipo) {
            case 0 -> PoliciaCriaturasModelos.ciervo();
            case 1 -> PoliciaCriaturasModelos.escorpion();
            case 2 -> PoliciaCriaturasModelos.yeti();
            case 3 -> PoliciaCriaturasModelos.sapo();
            default -> PoliciaCriaturasModelos.golem();
        };
    }

    static Datos datosBase(int tipo) {
        Datos d = new Datos();
        switch (tipo) {
            case 0 -> {
                d.huesos = PoliciaCriaturasModelos.HUESOS_CIERVO;
                d.padres = PoliciaCriaturasModelos.PADRES_CIERVO;
                d.resortes = PoliciaCriaturasModelos.RESORTES_CIERVO;
                d.estelas = PoliciaCriaturasModelos.ESTELAS_CIERVO;
                d.estelaColor = PoliciaCriaturasModelos.ESTELA_CIERVO;
                d.translucido = PoliciaCriaturasModelos.TRANSLUCIDO_CIERVO;
                d.escala = PoliciaCriaturasModelos.ESCALA_CIERVO;
            }
            case 1 -> {
                d.huesos = PoliciaCriaturasModelos.HUESOS_ESCORPION;
                d.padres = PoliciaCriaturasModelos.PADRES_ESCORPION;
                d.resortes = PoliciaCriaturasModelos.RESORTES_ESCORPION;
                d.estelas = PoliciaCriaturasModelos.ESTELAS_ESCORPION;
                d.estelaColor = PoliciaCriaturasModelos.ESTELA_ESCORPION;
                d.translucido = PoliciaCriaturasModelos.TRANSLUCIDO_ESCORPION;
                d.escala = PoliciaCriaturasModelos.ESCALA_ESCORPION;
            }
            case 2 -> {
                d.huesos = PoliciaCriaturasModelos.HUESOS_YETI;
                d.padres = PoliciaCriaturasModelos.PADRES_YETI;
                d.resortes = PoliciaCriaturasModelos.RESORTES_YETI;
                d.estelas = PoliciaCriaturasModelos.ESTELAS_YETI;
                d.estelaColor = PoliciaCriaturasModelos.ESTELA_YETI;
                d.translucido = PoliciaCriaturasModelos.TRANSLUCIDO_YETI;
                d.escala = PoliciaCriaturasModelos.ESCALA_YETI;
            }
            case 3 -> {
                d.huesos = PoliciaCriaturasModelos.HUESOS_SAPO;
                d.padres = PoliciaCriaturasModelos.PADRES_SAPO;
                d.resortes = PoliciaCriaturasModelos.RESORTES_SAPO;
                d.estelas = PoliciaCriaturasModelos.ESTELAS_SAPO;
                d.estelaColor = PoliciaCriaturasModelos.ESTELA_SAPO;
                d.translucido = PoliciaCriaturasModelos.TRANSLUCIDO_SAPO;
                d.escala = PoliciaCriaturasModelos.ESCALA_SAPO;
            }
            default -> {
                d.huesos = PoliciaCriaturasModelos.HUESOS_GOLEM;
                d.padres = PoliciaCriaturasModelos.PADRES_GOLEM;
                d.resortes = PoliciaCriaturasModelos.RESORTES_GOLEM;
                d.estelas = PoliciaCriaturasModelos.ESTELAS_GOLEM;
                d.estelaColor = PoliciaCriaturasModelos.ESTELA_GOLEM;
                d.translucido = PoliciaCriaturasModelos.TRANSLUCIDO_GOLEM;
                d.escala = PoliciaCriaturasModelos.ESCALA_GOLEM;
            }
        }
        for (int i = 0; i < d.huesos.length; i++) {
            if ("cabeza".equals(d.huesos[i])) {
                d.cabeza = i;
            }
        }
        return d;
    }

    // Lee las poses horneadas: por animacion, cuadro, hueso (+ raiz) y canal
    // (dx, dy, dz, rotX, rotY, rotZ, escX, escY, escZ). Los canales que no
    // vienen valen el reposo.
    static void cargarAnims(Datos d, ResourceManager rm, int tipo, float[][] reposoRot) {
        ResourceLocation rl = new ResourceLocation("policia", "criaturas/" + PoliciaCriaturas.ARCHIVO[tipo] + ".bin");
        try (InputStream in = rm.getResource(rl).orElseThrow().open(); DataInputStream ds = new DataInputStream(in)) {
            ds.readInt();
            int nb = ds.readInt();
            int na = ds.readInt();
            d.anims = new float[na][][][];
            d.durs = new float[na];
            for (int a = 0; a < na; a++) {
                d.durs[a] = ds.readFloat();
                int nf = ds.readShort();
                float[][][] fr = new float[nf][nb + 1][9];
                for (int tb = 0; tb <= nb; tb++) {
                    for (int f = 0; f < nf; f++) {
                        float[] v = fr[f][tb];
                        v[0] = 0;
                        v[1] = 0;
                        v[2] = 0;
                        v[3] = tb < nb ? reposoRot[tb][0] : 0;
                        v[4] = tb < nb ? reposoRot[tb][1] : 0;
                        v[5] = tb < nb ? reposoRot[tb][2] : 0;
                        v[6] = 1;
                        v[7] = 1;
                        v[8] = 1;
                    }
                    int mask = ds.readShort();
                    for (int k = 0; k < 9; k++) {
                        if ((mask & (1 << k)) == 0) {
                            continue;
                        }
                        float esc = k >= 3 && k < 6 ? 1000f : k >= 6 ? 500f : tb == nb ? 1000f : 100f;
                        for (int f = 0; f < nf; f++) {
                            fr[f][tb][k] = ds.readShort() / esc;
                        }
                    }
                }
                d.anims[a] = fr;
            }
        } catch (Exception ex) {
            d.anims = null;
        }
    }

    // ================= Modelo =================
    public static class Modelo extends EntityModel<PoliciaCriaturas.Criatura> {
        final ModelPart root;
        final ModelPart[] partes;
        final float[][] reposo;

        Modelo(ModelPart root, Datos d) {
            this.root = root;
            this.partes = new ModelPart[d.huesos.length];
            this.reposo = new float[d.huesos.length][];
            for (int i = 0; i < d.huesos.length; i++) {
                ModelPart padre = d.padres[i] < 0 ? root : this.partes[d.padres[i]];
                this.partes[i] = padre.getChild(d.huesos[i]);
                ModelPart p = this.partes[i];
                this.reposo[i] = new float[]{p.x, p.y, p.z, p.xRot, p.yRot, p.zRot};
            }
        }

        @Override
        public void setupAnim(PoliciaCriaturas.Criatura e, float a, float b, float c, float d, float f) {
        }

        @Override
        public void renderToBuffer(PoseStack ps, VertexConsumer vc, int luz, int over, float r, float g, float b, float a) {
            this.root.render(ps, vc, luz, over, r, g, b, a);
        }
    }

    // Estado de cada criatura en el cliente.
    static class Estado {
        int anim = -1;
        int animAntes = 0;
        float tAntes;
        float t;
        float cambio = -100;
        float reloj;
        long nanos;
        float rawAntes = -1;
        int atkAntes;
        final float[] raiz = new float[9];
        float[][] ang;
        float[][] vel;
        float[][] prevP;
        float[][] prevV;
        boolean iniciado;
        Vec3[][] estela;
        int estelaN;
        float vivo;
        Matrix4f[] mundo;
    }

    static Estado estado(PoliciaCriaturas.Criatura e, Datos d) {
        if (!(e.cliente instanceof Estado)) {
            Estado s = new Estado();
            int n = d.resortes.length;
            s.ang = new float[n][3];
            s.vel = new float[n][3];
            s.prevP = new float[n][3];
            s.prevV = new float[n][3];
            s.estela = new Vec3[d.estelas.length][16];
            s.mundo = new Matrix4f[d.huesos.length];
            for (int i = 0; i < s.mundo.length; i++) {
                s.mundo[i] = new Matrix4f();
            }
            e.cliente = s;
        }
        return (Estado) e.cliente;
    }

    // ================= Renderer =================
    public static class Render extends MobRenderer<PoliciaCriaturas.Criatura, Modelo> {
        final int tipo;
        final Datos d;
        final ResourceLocation tex;
        final ResourceLocation brillo;

        public Render(EntityRendererProvider.Context ctx, int tipo) {
            super(ctx, crearModelo(tipo), new float[]{0.6f, 1.0f, 0.9f, 0.7f, 1.6f}[tipo]);
            this.tipo = tipo;
            this.d = DATOS[tipo];
            cargarAnims(this.d, ctx.getResourceManager(), tipo, reposoRot(this.model));
            this.tex = new ResourceLocation("policia", "textures/entity/criatura_" + PoliciaCriaturas.ARCHIVO[tipo] + ".png");
            this.brillo = new ResourceLocation("policia", "textures/entity/criatura_" + PoliciaCriaturas.ARCHIVO[tipo] + "_brillo.png");
            this.addLayer(new Brillo(this));
        }

        static final Datos[] DATOS = new Datos[5];

        static Modelo crearModelo(int tipo) {
            DATOS[tipo] = datosBase(tipo);
            return new Modelo(capa(tipo).bakeRoot(), DATOS[tipo]);
        }

        static float[][] reposoRot(Modelo m) {
            float[][] r = new float[m.reposo.length][3];
            for (int i = 0; i < r.length; i++) {
                r[i][0] = m.reposo[i][3];
                r[i][1] = m.reposo[i][4];
                r[i][2] = m.reposo[i][5];
            }
            return r;
        }

        @Override
        public ResourceLocation getTextureLocation(PoliciaCriaturas.Criatura e) {
            return this.tex;
        }

        @Override
        protected RenderType getRenderType(PoliciaCriaturas.Criatura e, boolean visible, boolean translucido, boolean brilla) {
            if (visible && this.d.translucido) {
                return RenderType.entityTranslucent(this.tex);
            }
            return super.getRenderType(e, visible, translucido, brilla);
        }

        @Override
        protected float getFlipDegrees(PoliciaCriaturas.Criatura e) {
            return this.tipo == 4 ? 0 : 90;
        }

        @Override
        protected void scale(PoliciaCriaturas.Criatura e, PoseStack ps, float pt) {
            Estado s = estado(e, this.d);
            float[] r = s.raiz;
            ps.translate(r[0], r[1], r[2]);
            if (r[3] != 0 || r[4] != 0 || r[5] != 0) {
                ps.mulPose(new Quaternionf().rotationZYX(r[5], r[4], r[3]));
            }
            ps.scale(this.d.escala, this.d.escala, this.d.escala);
        }

        @Override
        public void render(PoliciaCriaturas.Criatura e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
            if (this.d.anims != null) {
                this.pose(e, pt);
            }
            super.render(e, yaw, pt, ps, buf, luz);
            if (this.d.anims != null) {
                this.estelas(e, pt, ps, buf);
            }
        }

        float[] muestra(int a, float t, boolean bucle, int tb, float[] out) {
            float[][][] fr = this.d.anims[a];
            int n = fr.length - 1;
            float f = t * 20f;
            if (bucle && n > 0) {
                f = f % n;
                if (f < 0) {
                    f += n;
                }
            } else {
                f = Mth.clamp(f, 0, n);
            }
            int i = (int) Math.floor(f);
            int j = Math.min(n, i + 1);
            float k = f - i;
            float[] va = fr[i][tb];
            float[] vb = fr[j][tb];
            for (int c = 0; c < 9; c++) {
                out[c] = va[c] + (vb[c] - va[c]) * k;
            }
            return out;
        }

        void pose(PoliciaCriaturas.Criatura e, float pt) {
            Datos d = this.d;
            Estado s = estado(e, d);
            Modelo m = this.model;
            float ahora = e.level().getGameTime() + pt;
            long nn = System.nanoTime();
            float dt = s.nanos == 0 ? 0.016f : Mth.clamp((nn - s.nanos) / 1.0e9f, 0.0f, 0.05f);
            s.nanos = nn;
            int atk = e.ataque();
            int anim;
            boolean bucle;
            float t;
            if (atk != 0 && atk < d.anims.length && e.isAlive()) {
                float raw = ahora - e.inicio();
                float ef = raw;
                for (int k : PoliciaCriaturas.IMPACTO[this.tipo][atk]) {
                    if (raw > k) {
                        ef -= Math.min(raw - k, PAUSA);
                    }
                    if (s.atkAntes == atk && s.rawAntes < k && raw >= k) {
                        this.impulso(s, atk == 3 && this.tipo == 4 || atk == 5 && this.tipo == 2 ? 45 : 30);
                        s.vivo = 1;
                    }
                }
                s.rawAntes = raw;
                anim = atk;
                bucle = false;
                t = ef / 20f;
            } else {
                float sp = e.walkAnimation.speed(pt);
                int correr = PoliciaCriaturas.CORRER[this.tipo];
                if (sp > 0.55f && correr >= 0) {
                    anim = correr;
                    s.reloj += dt;
                } else if (sp > 0.06f) {
                    anim = 1;
                    s.reloj += dt * Mth.clamp(sp * 2.4f, 0.6f, 1.5f);
                } else {
                    anim = 0;
                    s.reloj += dt;
                }
                bucle = true;
                t = s.reloj;
                s.rawAntes = -1;
            }
            s.atkAntes = atk;
            if (anim != s.anim) {
                s.animAntes = s.anim < 0 ? anim : s.anim;
                s.tAntes = s.t;
                s.cambio = ahora;
                s.anim = anim;
                if (!bucle) {
                    s.reloj = 0;
                }
            }
            s.t = t;
            float peso = Mth.clamp((ahora - s.cambio) / 4f, 0, 1);
            boolean bucleAntes = s.animAntes <= 2;
            float[] va = new float[9];
            float[] vb = new float[9];
            int nb = d.huesos.length;
            for (int tb = 0; tb <= nb; tb++) {
                this.muestra(anim, t, bucle, tb, vb);
                if (peso < 1) {
                    this.muestra(s.animAntes, s.tAntes, bucleAntes, tb, va);
                    for (int c = 0; c < 9; c++) {
                        vb[c] = va[c] + (vb[c] - va[c]) * peso;
                    }
                }
                if (tb < nb) {
                    ModelPart p = m.partes[tb];
                    float[] r = m.reposo[tb];
                    p.x = r[0] + vb[0];
                    p.y = r[1] + vb[1];
                    p.z = r[2] + vb[2];
                    p.xRot = vb[3];
                    p.yRot = vb[4];
                    p.zRot = vb[5];
                    p.xScale = vb[6];
                    p.yScale = vb[7];
                    p.zScale = vb[8];
                } else {
                    System.arraycopy(vb, 0, s.raiz, 0, 9);
                }
            }
            // la cabeza sigue la mirada cuando no ataca
            if (d.cabeza >= 0 && atk == 0) {
                float cuerpo = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
                float cab = Mth.wrapDegrees(Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - cuerpo);
                float pitch = Mth.lerp(pt, e.xRotO, e.getXRot());
                m.partes[d.cabeza].yRot += Mth.clamp(cab, -50, 50) * Mth.DEG_TO_RAD * 0.6f;
                m.partes[d.cabeza].xRot += pitch * Mth.DEG_TO_RAD * 0.4f;
            }
            this.fisica(e, s, pt, dt);
        }

        void impulso(Estado s, float f) {
            java.util.Random r = new java.util.Random();
            for (float[] v : s.vel) {
                v[0] += (r.nextFloat() - 0.5f) * f;
                v[2] += (r.nextFloat() - 0.5f) * f;
            }
        }

        // Matriz de cada hueso en el mundo (igual que la arma Minecraft al dibujar).
        void matrices(PoliciaCriaturas.Criatura e, Estado s, float pt, int desde) {
            Modelo m = this.model;
            Datos d = this.d;
            Matrix4f base = new Matrix4f();
            double ex = Mth.lerp(pt, e.xo, e.getX());
            double ey = Mth.lerp(pt, e.yo, e.getY());
            double ez = Mth.lerp(pt, e.zo, e.getZ());
            float cuerpo = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
            base.translate((float) ex, (float) ey, (float) ez);
            base.rotateY((180f - cuerpo) * Mth.DEG_TO_RAD);
            base.scale(-1, -1, 1);
            base.translate(s.raiz[0], s.raiz[1], s.raiz[2]);
            base.rotateZYX(s.raiz[5], s.raiz[4], s.raiz[3]);
            base.scale(d.escala);
            base.translate(0, -1.501f, 0);
            for (int i = desde; i < d.huesos.length; i++) {
                ModelPart p = m.partes[i];
                Matrix4f padre = d.padres[i] < 0 ? base : s.mundo[d.padres[i]];
                Matrix4f mm = s.mundo[i].set(padre);
                mm.translate(p.x / 16f, p.y / 16f, p.z / 16f);
                mm.rotateZYX(p.zRot, p.yRot, p.xRot);
                mm.scale(p.xScale, p.yScale, p.zScale);
            }
        }

        // Resortes: la punta del hueso se atrasa con la aceleracion y la
        // gravedad la tira para abajo (igual que en la vista previa).
        void fisica(PoliciaCriaturas.Criatura e, Estado s, float pt, float dt) {
            Datos d = this.d;
            this.matrices(e, s, pt, 0);
            if (dt <= 0) {
                return;
            }
            Vector3f pos = new Vector3f();
            Vector3f f = new Vector3f();
            Vector3f tq = new Vector3f();
            Quaternionf q = new Quaternionf();
            for (int i = 0; i < d.resortes.length; i++) {
                float[] r = d.resortes[i];
                int h = (int) r[0];
                s.mundo[h].getTranslation(pos);
                if (!s.iniciado) {
                    s.prevP[i][0] = pos.x;
                    s.prevP[i][1] = pos.y;
                    s.prevP[i][2] = pos.z;
                    continue;
                }
                float vx = (pos.x - s.prevP[i][0]) / dt;
                float vy = (pos.y - s.prevP[i][1]) / dt;
                float vz = (pos.z - s.prevP[i][2]) / dt;
                float ax = (vx - s.prevV[i][0]) / dt;
                float ay = (vy - s.prevV[i][1]) / dt;
                float az = (vz - s.prevV[i][2]) / dt;
                s.prevP[i][0] = pos.x;
                s.prevP[i][1] = pos.y;
                s.prevP[i][2] = pos.z;
                s.prevV[i][0] = vx;
                s.prevV[i][1] = vy;
                s.prevV[i][2] = vz;
                float la = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                if (la > 400) {
                    ax *= 400 / la;
                    ay *= 400 / la;
                    az *= 400 / la;
                }
                f.set(-ax * 0.12f, -9.8f * r[4] - ay * 0.12f, -az * 0.12f);
                s.mundo[h].getUnnormalizedRotation(q);
                q.normalize().conjugate();
                q.transform(f);
                tq.set(r[5], r[6], r[7]).cross(f).mul(r[3]);
                float[] ang = s.ang[i];
                float[] vel = s.vel[i];
                int pasos = 4;
                float hh = dt / pasos;
                for (int k = 0; k < pasos; k++) {
                    vel[0] += (tq.x - r[1] * ang[0] - r[2] * vel[0]) * hh;
                    vel[1] += (tq.y - r[1] * ang[1] - r[2] * vel[1]) * hh;
                    vel[2] += (tq.z - r[1] * ang[2] - r[2] * vel[2]) * hh;
                    ang[0] += vel[0] * hh;
                    ang[1] += vel[1] * hh;
                    ang[2] += vel[2] * hh;
                }
                for (int k = 0; k < 3; k++) {
                    ang[k] = Mth.clamp(ang[k], -r[8], r[8]);
                }
                ModelPart p = this.model.partes[h];
                p.xRot += ang[0];
                p.yRot += ang[1];
                p.zRot += ang[2];
                this.matrices(e, s, pt, h);
            }
            if (!s.iniciado) {
                s.iniciado = true;
            }
        }

        // Estelas: cintas que siguen garras, punos o aguijon durante los ataques.
        void estelas(PoliciaCriaturas.Criatura e, float pt, PoseStack ps, MultiBufferSource buf) {
            Datos d = this.d;
            Estado s = estado(e, d);
            boolean ataca = e.ataque() != 0;
            s.vivo = ataca ? Math.max(s.vivo, 0.6f) : s.vivo * 0.85f;
            Vector3f w = new Vector3f();
            double ex = Mth.lerp(pt, e.xo, e.getX());
            double ey = Mth.lerp(pt, e.yo, e.getY());
            double ez = Mth.lerp(pt, e.zo, e.getZ());
            for (int i = 0; i < d.estelas.length; i++) {
                float[] es = d.estelas[i];
                s.mundo[(int) es[0]].transformPosition(es[1] / 16f, es[2] / 16f, es[3] / 16f, w);
                Vec3[] h = s.estela[i];
                System.arraycopy(h, 0, h, 1, h.length - 1);
                h[0] = new Vec3(w.x, w.y, w.z);
            }
            s.estelaN = Math.min(16, s.estelaN + 1);
            if (s.vivo < 0.05f || s.estelaN < 4) {
                return;
            }
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            VertexConsumer vc = buf.getBuffer(RenderType.lightning());
            Matrix4f mat = ps.last().pose();
            float cr = ((d.estelaColor >> 16) & 255) / 255f;
            float cg = ((d.estelaColor >> 8) & 255) / 255f;
            float cb = (d.estelaColor & 255) / 255f;
            for (int i = 0; i < d.estelas.length; i++) {
                Vec3[] h = s.estela[i];
                if (h[1] == null || h[0].distanceTo(h[1]) < 0.035) {
                    continue;
                }
                float ancho = d.estelas[i][4];
                int n = Math.min(s.estelaN, h.length);
                Vec3 ladoA = null;
                for (int k = 0; k < n - 1; k++) {
                    Vec3 a = h[k];
                    Vec3 b = h[k + 1];
                    if (a == null || b == null) {
                        break;
                    }
                    Vec3 tan = a.subtract(b);
                    if (tan.lengthSqr() < 1e-8) {
                        continue;
                    }
                    Vec3 lado = tan.cross(cam.subtract(a)).normalize();
                    float wa = ancho * (1 - k / (float) n);
                    float wb = ancho * (1 - (k + 1) / (float) n);
                    Vec3 la = ladoA != null ? ladoA : lado.scale(wa);
                    Vec3 lb = lado.scale(wb);
                    ladoA = lb;
                    float al = 0.6f * s.vivo * (1 - k / (float) n);
                    float bl = 0.6f * s.vivo * (1 - (k + 1) / (float) n);
                    vert(vc, mat, a.add(la), ex, ey, ez, cr, cg, cb, al);
                    vert(vc, mat, a.subtract(la), ex, ey, ez, cr, cg, cb, al);
                    vert(vc, mat, b.subtract(lb), ex, ey, ez, cr, cg, cb, bl);
                    vert(vc, mat, b.add(lb), ex, ey, ez, cr, cg, cb, bl);
                }
            }
        }

        static void vert(VertexConsumer vc, Matrix4f m, Vec3 p, double ex, double ey, double ez, float r, float g, float b, float a) {
            vc.vertex(m, (float) (p.x - ex), (float) (p.y - ey), (float) (p.z - ez)).color(r, g, b, a).endVertex();
        }
    }

    // Capa que brilla en la oscuridad (ojos, nucleo, astas).
    static class Brillo extends RenderLayer<PoliciaCriaturas.Criatura, Modelo> {
        final Render r;

        Brillo(Render r) {
            super(r);
            this.r = r;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int luz, PoliciaCriaturas.Criatura e, float a, float b, float pt, float c, float d, float f) {
            if (e.isInvisible()) {
                return;
            }
            float k = LUNA ? 1.0f : 0.85f;
            this.getParentModel().renderToBuffer(ps, buf.getBuffer(RenderType.eyes(this.r.brillo)), 0xF000F0, OverlayTexture.NO_OVERLAY, k, LUNA ? 0.6f : k, LUNA ? 0.6f : k, 1.0f);
        }
    }
}
