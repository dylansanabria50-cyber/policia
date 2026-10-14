package com.example.policia;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Lado cliente: teclas, HUD, camara en tercera persona para vehiculos,
// mira del tanque, cabina del helicoptero y efecto de "disfraz" de skin.
public class PoliciaClient {
    static final String CAT = "Policia";
    static final KeyMapping K_FORM = new KeyMapping("Policia: Activar modo policia", 66, "Policia");
    static final KeyMapping K_USE = new KeyMapping("Policia: Usar habilidad", 74, "Policia");
    static final KeyMapping K_NEXT = new KeyMapping("Policia: Cambiar habilidad", 75, "Policia");
    static final KeyMapping K_LIGHT = new KeyMapping("Policia: Modo linterna (helicoptero)", 71, "Policia");
    static final KeyMapping K_ORDER = new KeyMapping("Albanil: orden a los ayudantes", 72, "Policia");
    static final KeyMapping K_YES = new KeyMapping("Albanil: aceptar aviso del ayudante", 89, "Policia");
    static final KeyMapping K_NO = new KeyMapping("Albanil: rechazar aviso del ayudante", 78, "Policia");
    static final KeyMapping K_BANK = new KeyMapping("Albanil: inventario de los ayudantes", 85, "Policia");
    static final KeyMapping K_ZONE = new KeyMapping("Albanil: elegir area de picado (mantener)", 88, "Policia");
    static final KeyMapping K_REFS = new KeyMapping("Policia: orden a los refuerzos", 82, "Policia");
    static final KeyMapping K_ANALYZE = new KeyMapping("Herrero: abrir el Analizador del Forjador", 79, "Policia");
    static final ResourceLocation TEX_POL = new ResourceLocation("policia", "textures/entity/policia.png");
    static final ResourceLocation TEX_ALB = new ResourceLocation("policia", "textures/entity/albanil.png");
    static final ResourceLocation TEX_LAD = new ResourceLocation("policia", "textures/entity/ladron.png");
    static ResourceLocation TEX_SKIN = TEX_POL;
    static final ResourceLocation TEX_FRONT = new ResourceLocation("policia", "textures/entity/escudo_frente.png");
    static final ResourceLocation TEX_BACK = new ResourceLocation("policia", "textures/entity/escudo_atras.png");
    static final Map<UUID, PoliciaMod.Sync> STATE = new HashMap<UUID, PoliciaMod.Sync>();
    static final Map<UUID, ResourceLocation> ORIG_SKIN = new HashMap<UUID, ResourceLocation>();
    static final Map<UUID, String> ORIG_MODEL = new HashMap<UUID, String>();
    static Field F_TEX;
    static Field F_MODEL;
    static boolean reflectTried;
    static boolean reflectOk;
    static Method CAM_MOVE;
    static boolean camTried;
    static Method CAM_SET;
    static boolean camSetTried;
    static final int MORPH_TICKS = 40;
    static final Map<UUID, Boolean> LAST_ON;
    static final Map<UUID, Morph> MORPH;

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaClient::keys);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaClient::overlays);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::tick);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::camera);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::hideRider);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::hideHand);
        MinecraftForge.EVENT_BUS.addListener(PoliciaHerreroAnim::firstPerson);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::render);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::screenInit);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::livingPre);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::noClicks);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::screenPre);
        MinecraftForge.EVENT_BUS.addListener(PoliciaXray::tick);
        MinecraftForge.EVENT_BUS.addListener(PoliciaXray::render);
        MinecraftForge.EVENT_BUS.addListener(PoliciaXray::scroll);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaClient::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaClient::renderers);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaClient::armorLayers);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaExtraRender::layers);
        MinecraftForge.EVENT_BUS.addListener(PoliciaExtraRender::livingPost);
        MinecraftForge.EVENT_BUS.addListener(PoliciaExtraRender::handFirst);
    }

    static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> ItemProperties.register((Item) PoliciaShield.SHIELD.get(), new ResourceLocation("blocking"), (st, lv, en, sd) -> en != null && en.isUsingItem() && en.getUseItem() == st ? 1.0f : 0.0f));
    }

    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer((EntityType) PoliciaTank.TANK.get(), PoliciaTankRender::new);
        e.registerEntityRenderer((EntityType) PoliciaRefuerzo.AGENT.get(), PoliciaRefuerzoRender::new);
        e.registerEntityRenderer((EntityType) PoliciaAyudante.AYU.get(), PoliciaAyudanteRender::new);
        e.registerEntityRenderer((EntityType) PoliciaTractor.TRACTOR.get(), PoliciaTractorRender::new);
        e.registerEntityRenderer((EntityType) PoliciaBubble.BUBBLE.get(), PoliciaBubbleRender::new);
        e.registerEntityRenderer((EntityType) PoliciaBoss.BOSS.get(), PoliciaBossRender::new);
        e.registerEntityRenderer((EntityType) PoliciaMision.PJ.get(), PoliciaMisionRender::new);
        e.registerEntityRenderer((EntityType) PoliciaMision.COM.get(), PoliciaMisionRender::new);
        e.registerEntityRenderer((EntityType) PoliciaMision.VAN.get(), PoliciaMisionRender::new);
        PoliciaExtraRender.register(e);
        e.registerEntityRenderer((EntityType) PoliciaHeli.HELI.get(), PoliciaHeliRender::new);
    }

    // Reemplaza la capa de armadura del jugador por una que se puede ocultar
    // sin tocar skins, otros mods ni el modelo del jugador en si: no cancela
    // nada del jugador, solo decide si esa capa en particular se dibuja.
    static Field F_LAYERS;
    static Field F_RMODEL;
    static boolean layersTried;

    // Busca los campos "layers" y "model" de LivingEntityRenderer por su
    // TIPO y no por nombre: en el juego real (fuera del entorno de
    // desarrollo) los nombres internos estan ofuscados, asi que buscarlos
    // por nombre fallaba en silencio.
    static void findLayersField() {
        if (layersTried) {
            return;
        }
        layersTried = true;
        try {
            for (Field f : LivingEntityRenderer.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (F_LAYERS == null && List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    F_LAYERS = f;
                } else if (F_RMODEL == null && net.minecraft.client.model.EntityModel.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    F_RMODEL = f;
                }
            }
        } catch (Throwable t) {
            F_LAYERS = null;
            F_RMODEL = null;
        }
    }

    static void armorLayers(EntityRenderersEvent.AddLayers e) {
        PoliciaClient.findLayersField();
        if (F_LAYERS == null) {
            return;
        }
        for (String skin : e.getSkins()) {
            Object r = e.getSkin(skin);
            if (!(r instanceof LivingEntityRenderer)) continue;
            try {
                List list = (List) F_LAYERS.get(r);
                for (int i = 0; i < list.size(); ++i) {
                    Object layer = list.get(i);
                    if (layer instanceof HumanoidArmorLayer && !(layer instanceof HideArmorLayer)) {
                        list.set(i, new HideArmorLayer((RenderLayerParent) r, (RenderLayer) layer));
                    }
                }
            } catch (Throwable ignored) {
            }
            // Animacion del Herrero: modelo de jugador que puede mover los
            // brazos durante Yunque/Reparacion, mas la capa que dibuja el
            // yunque y el martillo. Si algo falla, el jugador se ve normal.
            try {
                if (F_RMODEL != null && !(F_RMODEL.get(r) instanceof PoliciaHerreroAnim.AnimModel)) {
                    boolean slim = "slim".equals(skin);
                    net.minecraft.client.model.geom.ModelPart root = e.getContext().bakeLayer(slim ? net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM : net.minecraft.client.model.geom.ModelLayers.PLAYER);
                    F_RMODEL.set(r, new PoliciaHerreroAnim.AnimModel(root, slim));
                }
                List list = (List) F_LAYERS.get(r);
                list.add(new PoliciaHerreroAnim.AnimLayer((RenderLayerParent) r));
            } catch (Throwable ignored) {
            }
        }
    }

    static class HideArmorLayer extends RenderLayer {
        final RenderLayer wrapped;

        HideArmorLayer(RenderLayerParent parent, RenderLayer wrapped) {
            super(parent);
            this.wrapped = wrapped;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, Entity entity, float limbSwing, float limbSwingAmount, float pt, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity instanceof Player p) {
                PoliciaMod.Sync s = STATE.get(p.getUUID());
                if (s != null && s.hideArmor) {
                    return;
                }
            }
            this.wrapped.render(ps, buf, light, entity, limbSwing, limbSwingAmount, pt, ageInTicks, netHeadYaw, headPitch);
        }
    }

    // Camera tiene dos metodos protegidos (double, double, double): uno
    // pone la posicion (setPosition) y otro la corre segun hacia donde mira
    // (move). En el juego real sus nombres estan ofuscados, asi que se
    // reconocen por lo que HACEN: el que deja la camara exactamente en el
    // punto pedido es setPosition; el otro es move.
    static void findCamMethods(Camera cam) {
        try {
            java.util.List<Method> cands = new java.util.ArrayList<Method>();
            for (Method m : Camera.class.getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getReturnType() != Void.TYPE || pt.length != 3) continue;
                if (pt[0] != Double.TYPE || pt[1] != Double.TYPE || pt[2] != Double.TYPE) continue;
                m.setAccessible(true);
                cands.add(m);
            }
            Vec3 orig = cam.getPosition();
            Method set = null;
            Method mv = null;
            for (Method m : cands) {
                m.invoke(cam, 1234.5, 77.25, -321.75);
                Vec3 now = cam.getPosition();
                if (Math.abs(now.x - 1234.5) < 1.0E-6 && Math.abs(now.y - 77.25) < 1.0E-6 && Math.abs(now.z + 321.75) < 1.0E-6) {
                    set = m;
                } else {
                    mv = m;
                }
                if (set != null) {
                    set.invoke(cam, orig.x, orig.y, orig.z);
                }
            }
            if (set != null) {
                set.invoke(cam, orig.x, orig.y, orig.z);
            }
            CAM_SET = set;
            CAM_MOVE = mv;
        } catch (Throwable t) {
            CAM_SET = null;
            CAM_MOVE = null;
        }
    }

    static void camera(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (!(mc.player.getVehicle() instanceof PoliciaTank.TankEntity || mc.player.getVehicle() instanceof PoliciaHeli.HeliEntity || mc.player.getVehicle() instanceof PoliciaTractor.TractorEntity)) {
            return;
        }
        if (!camTried) {
            camTried = true;
            PoliciaClient.findCamMethods(e.getCamera());
        }
        if (CAM_MOVE == null || CAM_SET == null) {
            return;
        }
        try {
            Camera cam = e.getCamera();
            Vec3 eye = mc.player.getEyePosition((float) e.getPartialTick());
            Vector3f lk = cam.getLookVector();
            Vector3f up = cam.getUpVector();
            Vec3 tgt = eye.add((double) lk.x() * -18.0 + (double) up.x() * 3.0, (double) lk.y() * -18.0 + (double) up.y() * 3.0, (double) lk.z() * -18.0 + (double) up.z() * 3.0);
            double total = eye.distanceTo(tgt);
            double f = 1.0;
            for (int i = 0; i < 8; ++i) {
                Vec3 o = new Vec3((double) (i & 1) * 0.2 - 0.1, (double) (i >> 1 & 1) * 0.2 - 0.1, (double) (i >> 2 & 1) * 0.2 - 0.1);
                BlockHitResult h = mc.level.clip(new ClipContext(eye.add(o), tgt.add(o), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, (Entity) mc.player));
                if (h.getType() == HitResult.Type.MISS) continue;
                f = Math.min(f, Math.max(0.0, (eye.add(o).distanceTo(h.getLocation()) - 0.1) / total));
            }
            CAM_SET.invoke(cam, eye.x, eye.y, eye.z);
            CAM_MOVE.invoke(cam, -18.0 * f, 3.0 * f, 0.0);
        } catch (Throwable t) {
            CAM_MOVE = null;
        }
    }

    static void hideRider(RenderPlayerEvent.Pre e) {
        if (e.getEntity().getVehicle() instanceof PoliciaTank.TankEntity) {
            e.setCanceled(true);
        }
    }

    static void hideHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.player.getVehicle() instanceof PoliciaTank.TankEntity || mc.player.getVehicle() instanceof PoliciaHeli.HeliEntity || mc.player.getVehicle() instanceof PoliciaTractor.TractorEntity)) {
            e.setCanceled(true);
        }
    }

    static void keys(RegisterKeyMappingsEvent e) {
        e.register(K_FORM);
        e.register(K_USE);
        e.register(K_NEXT);
        e.register(K_LIGHT);
        e.register(K_ORDER);
        e.register(K_YES);
        e.register(K_NO);
        e.register(K_BANK);
        e.register(K_ZONE);
        e.register(K_REFS);
        e.register(K_ANALYZE);
    }

    static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("policia", PoliciaClient::hud);
        e.registerAboveAll("policia_forja", PoliciaHerreroAnim::forgeHud);
    }

    static void screenInit(ScreenEvent.Init.Post e) {
        if (e.getScreen() instanceof InventoryScreen) {
            e.addListener((GuiEventListener) new TreeButton());
            e.addListener((GuiEventListener) new ArmorHideButton());
        }
    }

    static void screenPre(ScreenEvent.Render.Pre e) {
        Screen screen = e.getScreen();
        if (!(screen instanceof InventoryScreen)) {
            return;
        }
        InventoryScreen inv = (InventoryScreen) screen;
        TreeButton tb = null;
        for (Object o : inv.children()) {
            TreeButton t;
            if (!(o instanceof TreeButton)) continue;
            tb = t = (TreeButton) ((Object) o);
        }
        if (tb == null) {
            return;
        }
        int left = inv.getGuiLeft();
        int top = inv.getGuiTop();
        int maxRight = left + 124;
        for (Object o : inv.children()) {
            AbstractWidget w;
            if (!(o instanceof AbstractWidget) || (w = (AbstractWidget) o) == tb || w.getY() < top + 55 || w.getY() > top + 70 || w.getX() < left + 100 || w.getX() + w.getWidth() <= maxRight) continue;
            maxRight = w.getX() + w.getWidth();
        }
        PoliciaMod.Sync st = PoliciaClient.mine();
        tb.visible = st != null && st.on;
        tb.setX(maxRight + 2);
        tb.setY(top + 61);
        for (Object o : inv.children()) {
            if (o instanceof ArmorHideButton ab) {
                ab.setX(left + 7);
                ab.setY(top + 6);
            }
        }
    }

    static void livingPre(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity livingEntity = e.getEntity();
        if (!(livingEntity instanceof Player)) {
            return;
        }
        Player p = (Player) livingEntity;
        PoliciaMod.Sync s = STATE.get(p.getUUID());
    }

    static void noClicks(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc0 = Minecraft.getInstance();
        if (mc0.player != null && (mc0.player.getVehicle() instanceof PoliciaHeli.HeliEntity || mc0.player.getVehicle() instanceof PoliciaTractor.TractorEntity)) {
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        if (mc0.player != null && mc0.player.getVehicle() instanceof PoliciaTank.TankEntity) {
            if (e.isUseItem()) {
                PoliciaClient.send(3);
            }
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        PoliciaMod.Sync s = PoliciaClient.mine();
    }

    static void send(int id) {
        PoliciaMod.NET.sendToServer((Object) new PoliciaMod.Act(id));
    }

    static void onSync(PoliciaMod.Sync m) {
        STATE.put(m.id, m);
    }

    static boolean lad() {
        PoliciaMod.Sync s = PoliciaClient.mine();
        return s != null && s.jb == 3;
    }

    static boolean her() {
        PoliciaMod.Sync s = PoliciaClient.mine();
        return s != null && s.jb == 4;
    }

    static boolean alb() {
        PoliciaMod.Sync s = PoliciaClient.mine();
        return s != null && s.jb == 2;
    }

    static PoliciaMod.Sync mine() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : STATE.get(mc.player.getUUID());
    }

    static void findFields() {
        if (reflectTried) {
            return;
        }
        reflectTried = true;
        try {
            for (Field f : PlayerInfo.class.getDeclaredFields()) {
                if (F_TEX == null && Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    F_TEX = f;
                    continue;
                }
                if (F_MODEL != null || f.getType() != String.class) continue;
                f.setAccessible(true);
                F_MODEL = f;
            }
            reflectOk = F_TEX != null;
        } catch (Throwable t) {
            reflectOk = false;
        }
    }

    static NativeImage readTexture(Minecraft mc, ResourceLocation rl) {
        NativeImage img = new NativeImage(64, 64, true);
        RenderSystem.bindTexture(mc.getTextureManager().getTexture(rl).getId());
        img.downloadTexture(0, false);
        return img;
    }

    static Morph startMorph(Minecraft mc, UUID id, boolean forming, ResourceLocation origRl) throws Exception {
        Morph m = new Morph();
        m.forming = forming;
        m.start = mc.level.getGameTime();
        m.orig = PoliciaClient.readTexture(mc, origRl);
        try (InputStream in = mc.getResourceManager().getResourceOrThrow(TEX_SKIN).open()) {
            m.pol = NativeImage.read(in);
        }
        m.dyn = new DynamicTexture(new NativeImage(64, 64, true));
        m.rl = new ResourceLocation("policia", "morph/" + id.toString().toLowerCase());
        mc.getTextureManager().register(m.rl, (AbstractTexture) m.dyn);
        MORPH.put(id, m);
        return m;
    }

    static float noise(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ h >>> 13) * 1274126177;
        h ^= h >>> 16;
        return (float) (h & 0x3FF) / 1023.0f;
    }

    static void stepMorph(Morph m, long now) {
        float p = Math.min(1.0f, (float) (now - m.start) / 40.0f);
        NativeImage out = m.dyn.getPixels();
        if (out == null) {
            return;
        }
        NativeImage from = m.forming ? m.orig : m.pol;
        NativeImage to = m.forming ? m.pol : m.orig;
        float t = p * 1.25f - 0.1f;
        for (int y = 0; y < 64; ++y) {
            for (int x = 0; x < 64; ++x) {
                int al;
                float k = 0.55f * PoliciaClient.noise(x, y) + 0.45f * (1.0f - (float) y / 64.0f);
                int a = from.getPixelRGBA(x, y);
                int b = to.getPixelRGBA(x, y);
                int px = k < t - 0.12f ? b : (k < t ? ((al = Math.max(a >>> 24, b >>> 24)) == 0 ? 0 : al << 24 | 0xFF0000 | 0xE600 | 0x50) : a);
                out.setPixelRGBA(x, y, px);
            }
        }
        m.dyn.upload();
    }

    static void applySkin(Minecraft mc) {
        PoliciaClient.findFields();
        if (!reflectOk || mc.getConnection() == null || mc.level == null) {
            return;
        }
        for (Map.Entry<UUID, PoliciaMod.Sync> en : STATE.entrySet()) {
            UUID id = en.getKey();
            if (en.getValue().jb == 4) {
                // El Herrero no tiene skin propia todavia: conserva la del
                // jugador en vez de ponerle la de policia por defecto.
                continue;
            }
            TEX_SKIN = en.getValue().jb == 3 ? TEX_LAD : (en.getValue().jb == 2 ? TEX_ALB : TEX_POL);
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info == null) continue;
            try {
                Map tex = (Map) F_TEX.get(info);
                ResourceLocation cur = (ResourceLocation) tex.get(MinecraftProfileTexture.Type.SKIN);
                boolean want = en.getValue().on;
                Boolean last = LAST_ON.put(id, want);
                Morph m = MORPH.get(id);
                if (m == null && last != null && last != want && cur != null) {
                    ResourceLocation orig = cur;
                    if (want) {
                        if (!TEX_SKIN.equals((Object) cur)) {
                            ORIG_SKIN.put(id, cur);
                            if (F_MODEL != null) {
                                ORIG_MODEL.put(id, (String) F_MODEL.get(info));
                            }
                        }
                    } else {
                        orig = ORIG_SKIN.get(id);
                    }
                    if (orig != null) {
                        try {
                            m = PoliciaClient.startMorph(mc, id, want, orig);
                        } catch (Throwable t) {
                            m = null;
                        }
                    }
                }
                if (m != null) {
                    long el = mc.level.getGameTime() - m.start;
                    if (el < 40L) {
                        String origModel;
                        PoliciaClient.stepMorph(m, mc.level.getGameTime());
                        tex.put(MinecraftProfileTexture.Type.SKIN, m.rl);
                        boolean secondHalf = el * 2L >= 40L;
                        String string = origModel = ORIG_MODEL.get(id) == null ? "default" : ORIG_MODEL.get(id);
                        if (F_MODEL == null) continue;
                        F_MODEL.set(info, secondHalf == m.forming ? "slim" : origModel);
                        continue;
                    }
                    MORPH.remove(id);
                    mc.getTextureManager().release(m.rl);
                    m.orig.close();
                    m.pol.close();
                    if (m.forming) {
                        tex.put(MinecraftProfileTexture.Type.SKIN, TEX_SKIN);
                        if (F_MODEL == null) continue;
                        F_MODEL.set(info, "slim");
                        continue;
                    }
                    ResourceLocation o = ORIG_SKIN.remove(id);
                    if (o != null) {
                        tex.put(MinecraftProfileTexture.Type.SKIN, o);
                    }
                    if (F_MODEL == null) continue;
                    F_MODEL.set(info, ORIG_MODEL.remove(id));
                    continue;
                }
                if (want) {
                    if (cur == null) continue;
                    if (!TEX_SKIN.equals((Object) cur)) {
                        ORIG_SKIN.put(id, cur);
                        if (F_MODEL != null) {
                            ORIG_MODEL.put(id, (String) F_MODEL.get(info));
                        }
                    }
                    tex.put(MinecraftProfileTexture.Type.SKIN, TEX_SKIN);
                    if (F_MODEL == null) continue;
                    F_MODEL.set(info, "slim");
                    continue;
                }
                if (!TEX_SKIN.equals((Object) cur)) continue;
                ResourceLocation o = ORIG_SKIN.remove(id);
                if (o != null) {
                    tex.put(MinecraftProfileTexture.Type.SKIN, o);
                }
                if (F_MODEL == null) continue;
                F_MODEL.set(info, ORIG_MODEL.remove(id));
            } catch (Throwable t) {
                reflectOk = false;
            }
        }
    }

    static void tick(TickEvent.ClientTickEvent e) {
        boolean zk;
        boolean free;
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            STATE.clear();
            ORIG_SKIN.clear();
            ORIG_MODEL.clear();
            LAST_ON.clear();
            MORPH.clear();
            return;
        }
        if (PoliciaOficioScreen.pending && mc.screen == null && mc.level != null) {
            PoliciaOficioScreen.pending = false;
            mc.setScreen(new PoliciaOficioScreen());
        }
        PoliciaClient.applySkin(mc);
        boolean bl = free = mc.screen == null;
        while (K_FORM.consumeClick()) {
            if (!free) continue;
            PoliciaClient.send(0);
        }
        while (K_USE.consumeClick()) {
            if (!free) continue;
            if (PoliciaHerreroAnim.forging()) {
                PoliciaHerreroAnim.press();
                PoliciaClient.send(63);
                continue;
            }
            PoliciaClient.send(1);
        }
        while (K_NEXT.consumeClick()) {
            if (!free) continue;
            PoliciaClient.send(2);
        }
        while (K_LIGHT.consumeClick()) {
            if (!free) continue;
            PoliciaClient.send(4);
        }
        while (K_ORDER.consumeClick()) {
            if (!free) continue;
            PoliciaClient.send(7);
        }
        while (K_REFS.consumeClick()) {
            if (!free) continue;
            PoliciaClient.send(61);
        }
        while (K_YES.consumeClick()) {
            if (!free || !PoliciaClient.alb()) continue;
            PoliciaClient.send(8);
        }
        while (K_NO.consumeClick()) {
            if (!free || !PoliciaClient.alb()) continue;
            PoliciaClient.send(9);
        }
        while (K_BANK.consumeClick()) {
            if (!free || !PoliciaClient.alb()) continue;
            PoliciaClient.send(50);
        }
        while (K_ANALYZE.consumeClick()) {
            if (!free) continue;
            mc.setScreen(new PoliciaAnalizadorScreen());
        }
        boolean bl2 = zk = K_ZONE.isDown() && free && PoliciaClient.alb() && PoliciaAlbanil.CL.hl >= 1;
        if (zk) {
            PoliciaXray.zoning = true;
            mc.player.displayClientMessage(Component.literal("Area de picado: largo " + PoliciaXray.zl + " x ancho " + PoliciaXray.zw + " (rueda: largo, Mayus+rueda: ancho). Suelta X para fijarla"), true);
        } else if (PoliciaXray.zoning) {
            PoliciaXray.zoning = false;
            if (free && PoliciaClient.alb()) {
                PoliciaClient.send(1000 + PoliciaXray.zl * 10 + PoliciaXray.zw);
            }
        }
        if (free && (mc.player.getVehicle() instanceof PoliciaHeli.HeliEntity || mc.player.getVehicle() instanceof PoliciaTractor.TractorEntity)) {
            if (mc.options.keyUp.isDown()) {
                PoliciaClient.send(5);
            } else if (mc.options.keyDown.isDown()) {
                PoliciaClient.send(6);
            }
        }
    }

    static final String[] JOB_TAG = new String[]{"", "Policia", "Albanil", "Ladron", "Herrero Legendario"};

    // Oficio escrito arriba del nombre del jugador: blanco para todos y
    // dorado para el Herrero Legendario. Se dibuja como el cartel del
    // nombre (se ve a traves de las paredes atenuado, como vanilla).
    static void render(RenderPlayerEvent.Post e) {
        Player p = e.getEntity();
        PoliciaMod.Sync s = STATE.get(p.getUUID());
        if (s == null || s.jb < 1 || s.jb >= JOB_TAG.length) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (p.isInvisible() || mc.options.hideGui || p == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (mc.getEntityRenderDispatcher().distanceToSqr(p) > 4096.0) {
            return;
        }
        String text = JOB_TAG[s.jb];
        int color = s.jb == 4 ? 0xFFFFD24A : 0xFFFFFFFF;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        // El nombre va a la altura del jugador + 0.5; el oficio, una linea arriba.
        float y = p.getBbHeight() + 0.5f + (p == mc.player ? 0.0f : 0.27f);
        ps.translate(0.0f, y, 0.0f);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f m = ps.last().pose();
        int bg = (int) (mc.options.getBackgroundOpacity(0.25f) * 255.0f) << 24;
        float x = (float) (-mc.font.width(text) / 2);
        boolean see = !p.isDiscrete();
        mc.font.drawInBatch(text, x, 0.0f, color & 0x00FFFFFF | 0x20000000, false, m, e.getMultiBufferSource(), see ? net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH : net.minecraft.client.gui.Font.DisplayMode.NORMAL, bg, e.getPackedLight());
        if (see) {
            mc.font.drawInBatch(text, x, 0.0f, color, false, m, e.getMultiBufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, e.getPackedLight());
        }
        ps.popPose();
    }

    static void drawFace(PoseStack ps, VertexConsumer vc, float w, float h, float z, int light, boolean flip) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float nz = flip ? -1.0f : 1.0f;
        float u0 = flip ? 0.0f : 1.0f;
        float u1 = flip ? 1.0f : 0.0f;
        PoliciaClient.v(vc, m, n, -w, -h, z, u0, 1.0f, nz, light);
        PoliciaClient.v(vc, m, n, w, -h, z, u1, 1.0f, nz, light);
        PoliciaClient.v(vc, m, n, w, h, z, u1, 0.0f, nz, light);
        PoliciaClient.v(vc, m, n, -w, h, z, u0, 0.0f, nz, light);
    }

    static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float vv, float nz, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0f, 0.0f, nz).endVertex();
    }

    static void scope(GuiGraphics g, Minecraft mc, PoliciaTank.TankEntity t, int sw, int sh) {
        int y;
        int x;
        double rad;
        int G = -12787602;
        int GD = -2009276306;
        int RED = -53200;
        int cx = sw / 2;
        int cy = sh / 2;
        float pt = mc.getFrameTime();
        double dist = mc.player.pick(96.0, pt, false).getLocation().distanceTo(mc.player.getEyePosition(pt));
        g.fill(cx - 52, cy, cx - 5, cy + 1, -12787602);
        g.fill(cx + 5, cy, cx + 52, cy + 1, -12787602);
        g.fill(cx, cy - 44, cx + 1, cy - 5, -12787602);
        g.fill(cx, cy + 5, cx + 1, cy + 44, -12787602);
        for (int k = 1; k <= 4; ++k) {
            g.fill(cx + k * 12, cy - 3, cx + k * 12 + 1, cy + 4, -12787602);
            g.fill(cx - k * 12, cy - 3, cx - k * 12 + 1, cy + 4, -12787602);
            g.fill(cx - 3, cy + k * 10, cx + 4, cy + k * 10 + 1, -12787602);
            g.fill(cx - 3, cy - k * 10, cx + 4, cy - k * 10 + 1, -12787602);
        }
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, -53200);
        g.fill(cx - 84, cy - 50, cx + 84, cy - 49, -2009276306);
        g.fill(cx - 84, cy + 49, cx + 84, cy + 50, -2009276306);
        g.fill(cx - 84, cy - 50, cx - 83, cy + 50, -2009276306);
        g.fill(cx + 83, cy - 50, cx + 84, cy + 50, -2009276306);
        double r0 = (double) sh * 0.38 / Math.sin(Math.toRadians(14.0));
        int xm = sw - 70;
        for (int d = -140; d <= 140; ++d) {
            rad = Math.toRadians((double) d / 10.0);
            x = xm - (int) (r0 * (1.0 - Math.cos(rad)));
            y = cy + (int) (r0 * Math.sin(rad));
            g.fill(x, y, x + 2, y + 1, -12787602);
        }
        for (int i = 0; i < 4; ++i) {
            rad = Math.toRadians(-14.0 + 28.0 * (double) i / 3.0);
            x = xm - (int) (r0 * (1.0 - Math.cos(rad)));
            y = cy + (int) (r0 * Math.sin(rad));
            g.fill(x, y, x + 9, y + 1, -12787602);
            g.drawString(mc.font, String.valueOf(200 + 200 * i), x + 13, y - 4, -12787602, true);
        }
        double fr = Math.max(0.0, Math.min(1.0, (dist - 16.0) / 80.0));
        double rm = Math.toRadians(-14.0 + 28.0 * fr);
        int mx = xm - (int) (r0 * (1.0 - Math.cos(rm)));
        int my = cy + (int) (r0 * Math.sin(rm));
        g.fill(mx - 12, my - 2, mx - 3, my + 3, -53200);
        g.drawString(mc.font, "DIST " + (int) dist + " m", cx - 84, cy - 62, -12787602, true);
        String est = t.fireState() >= 0 ? "CARGANDO" : "LISTO";
        String obus = t.isMobile() ? "OBUS " + t.shotsLeft() + "/3" : "OBUS 1/1";
        g.drawString(mc.font, obus + "  " + est, cx - 84, cy + 56, -12787602, true);
    }

    static void cockpit(GuiGraphics g, Minecraft mc, PoliciaHeli.HeliEntity h, int sw, int sh) {
        int D = -15394787;
        int L = -12959412;
        int P = -15987182;
        int top = (int) ((float) sh * 0.035f);
        int dash = (int) ((float) sh * 0.86f);
        g.fill(0, 0, sw, top, -15394787);
        g.fill(0, top, sw, top + 2, -12959412);
        for (int y = top; y < dash; y += 2) {
            float k = (float) (y - top) / (float) (dash - top);
            int w = (int) ((float) sw * (0.012f + 0.04f * k));
            g.fill(0, y, w, y + 2, -15394787);
            g.fill(w, y, w + 2, y + 2, -12959412);
            g.fill(sw - w, y, sw, y + 2, -15394787);
            g.fill(sw - w - 2, y, sw - w, y + 2, -12959412);
        }
        g.fill(0, dash, sw, sh, -15394787);
        g.fill(0, dash, sw, dash + 3, -12959412);
        g.fill(0, dash + 3, sw, dash + 6, -15987182);
        int t = mc.player.tickCount;
        int cx = sw / 2;
        int cw = Math.max(90, sw / 6);
        int ch = (int) ((float) sh * 0.085f);
        int cy0 = dash + 12;
        g.fill(cx - cw / 2 - 2, cy0 - 2, cx + cw / 2 + 2, cy0 + ch + 2, -12959412);
        g.fill(cx - cw / 2, cy0, cx + cw / 2, cy0 + ch, -16374758);
        g.drawString(mc.font, "ALT " + (int) h.getY() + " m", cx - cw / 2 + 5, cy0 + 5, -12787602, false);
        g.drawString(mc.font, h.isLight() ? "LINTERNA ON" : "VUELO", cx - cw / 2 + 5, cy0 + 16, h.isLight() ? -8080 : -12787602, false);
        for (int i = -1; i <= 1; i += 2) {
            int gx = cx + i * (cw / 2 + 46);
            int gy = cy0 + ch / 2;
            g.fill(gx - 16, gy - 16, gx + 16, gy + 16, -12959412);
            g.fill(gx - 14, gy - 14, gx + 14, gy + 14, -15987182);
            double a = Math.sin((double) t * 0.04 + (double) i) * 1.8 - 1.57;
            for (int k = 0; k < 11; ++k) {
                int px = gx + (int) (Math.cos(a) * (double) k);
                int py = gy + (int) (Math.sin(a) * (double) k);
                g.fill(px, py, px + 2, py + 2, -44992);
            }
            g.fill(gx - 2, gy - 2, gx + 3, gy + 3, -5196616);
        }
        boolean alt = t / 6 % 2 == 0;
        for (int k = 0; k < 6; ++k) {
            int lx = cx - 60 + k * 24;
            int ly = dash + ch + 18;
            int col = k % 2 == 0 ? (alt ? -53200 : -11530224) : (alt ? -15720368 : -13606657);
            g.fill(lx, ly, lx + 8, ly + 5, col);
        }
        g.fill(cx - 3, sh - 24, cx + 4, sh, -14539219);
        g.fill(cx - 7, sh - 32, cx + 8, sh - 20, -13749445);
        g.fill(cx - 7, sh - 32, cx + 8, sh - 29, -12959412);
    }

    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Object st;
        Object line;
        Minecraft mc = Minecraft.getInstance();
        PoliciaMod.Sync s = PoliciaClient.mine();
        if (mc.player == null || mc.options.hideGui || s == null || !s.on) {
            return;
        }
        if (s.jb == 3) {
            PoliciaLadronScreen.hud(g, mc, s, sw, sh);
            return;
        }
        if (s.jb == 2) {
            PoliciaAlbanilScreen.hud(g, mc, s, sw, sh);
            return;
        }
        if (s.jb == 4) {
            PoliciaHerreroScreen.hud(g, mc, s, sw, sh);
            return;
        }
        Entity entity = mc.player.getVehicle();
        if (entity instanceof PoliciaTank.TankEntity) {
            PoliciaTank.TankEntity tk = (PoliciaTank.TankEntity) entity;
            if (mc.options.getCameraType().isFirstPerson()) {
                PoliciaClient.scope(g, mc, tk, sw, sh);
            }
        }
        if ((entity = mc.player.getVehicle()) instanceof PoliciaHeli.HeliEntity) {
            PoliciaHeli.HeliEntity hl = (PoliciaHeli.HeliEntity) entity;
            if (mc.options.getCameraType().isFirstPerson()) {
                PoliciaClient.cockpit(g, mc, hl, sw, sh);
            }
        }
        boolean up = (s.un >> 4 & 1) == 1;
        ArrayList<Object> ls = new ArrayList<Object>();
        ArrayList<Integer> cs = new ArrayList<Integer>();
        ls.add("ARBOL DE HABILIDADES - XP " + s.xp);
        cs.add(-1523648);
        int num = 0;
        for (int i = 0; i < PoliciaMod.SKILLS.length; ++i) {
            if (i == 4) continue;
            boolean open = i == 0 || (s.un >> i & 1) == 1;
            String nm = i == 2 && up ? PoliciaMod.SKILLS[4] : PoliciaMod.SKILLS[i];
            line = (i == s.sel && open ? "> " : "  ") + ++num + ". " + nm;
            if (!open && i == 9) {
                line = (String) line + "  [mision especial]";
            } else if (!open) {
                line = (String) line + "  [" + (PoliciaMod.LEVEL_COST[i] > 0 ? PoliciaMod.LEVEL_COST[i] + " niveles" : "10 XP") + "]";
            }
            ls.add(line);
            cs.add(open ? (i == s.sel ? -1 : -5192482) : -9408400);
        }
        line = mc.player.getVehicle();
        if (line instanceof PoliciaTank.TankEntity) {
            PoliciaTank.TankEntity tk0 = (PoliciaTank.TankEntity) ((Object) line);
            st = tk0.isMobile() ? "TANQUE: WASD mover, clic derecho disparar (" + tk0.shotsLeft() + "), Shift bajar" : "TANQUE: clic derecho para disparar";
        } else if (s.sel == 2) {
            st = s.tcd > 0 ? "Tanque en enfriamiento: " + (s.tcd + 19) / 20 + " s" : (up ? "Tanque movil listo (J)" : "Tanque listo (J)");
        } else if (s.sel == 1) {
            st = s.rcd > 0 ? "Refuerzo en enfriamiento: " + (s.rcd + 19) / 20 + " s" : "Refuerzo nv " + Math.max(1, s.rl) + " listo (J)";
        } else if (s.sel >= 5) {
            int cc = s.sel == 5 ? s.c5 : (s.sel == 6 ? s.c6 : (s.sel == 7 ? s.c7 : (s.sel == 9 ? s.c9 : s.c8)));
            st = PoliciaMod.SKILLS[s.sel] + (cc > 0 ? " en enfriamiento: " + (cc + 19) / 20 + " s" : " listo (J)");
        } else {
            PoliciaHeli.HeliEntity hh;
            Entity cc;
            st = s.sel == 3 ? ((cc = mc.player.getVehicle()) instanceof PoliciaHeli.HeliEntity ? ((hh = (PoliciaHeli.HeliEntity) cc).isLight() ? "MODO LINTERNA: la luz sigue tu mirada. W/S/A/D mover, ESPACIO subir, CTRL bajar (tecla de linterna para apagar)" : "HELICOPTERO: W/S avanzar, A/D desplazar, ESPACIO subir, CTRL bajar, Shift salir, tecla de linterna: luz") : "Helicoptero listo (J)") : (s.shield > 0 ? "Escudo activo: " + (s.shield + 19) / 20 + " s" : (s.cooldown > 0 ? "Escudo en enfriamiento: " + (s.cooldown + 19) / 20 + " s" : "Escudo listo (J)"));
        }
        ls.add(st);
        cs.add(-8331009);
        float sc = 0.75f;
        int wmax = 0;
        for (Object string : ls) {
            wmax = Math.max(wmax, mc.font.width((String) string));
        }
        g.pose().pushPose();
        g.pose().translate((float) (sw - 6) - (float) wmax * sc, 6.0f, 0.0f);
        g.pose().scale(sc, sc, 1.0f);
        for (int i = 0; i < ls.size(); ++i) {
            int n = i == 0 ? 0 : (i == ls.size() - 1 ? i * 10 + 6 : i * 10 + 2);
            g.drawString(mc.font, (String) ls.get(i), 0, n, cs.get(i).intValue(), true);
        }
        g.pose().popPose();
    }

    static {
        reflectTried = false;
        reflectOk = false;
        camTried = false;
        camSetTried = false;
        LAST_ON = new HashMap<UUID, Boolean>();
        MORPH = new HashMap<UUID, Morph>();
    }

    static class TreeButton extends Button {
        TreeButton() {
            super(0, 0, 20, 18, Component.literal(""), b -> Minecraft.getInstance().setScreen(PoliciaClient.her() ? new PoliciaHerreroScreen() : (PoliciaClient.lad() ? new PoliciaLadronScreen() : (PoliciaClient.alb() ? new PoliciaAlbanilScreen() : new PoliciaTreeScreen()))), DEFAULT_NARRATION);
            this.setTooltip(Tooltip.create(Component.literal("Rama de habilidades")));
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            super.renderWidget(g, mx, my, pt);
            g.renderItem(PoliciaClient.her() ? new ItemStack((ItemLike) PoliciaPlaca.MARTILLO.get()) : PoliciaClient.lad() ? new ItemStack((ItemLike) PoliciaPlaca.GUANTE.get()) : (PoliciaClient.alb() ? new ItemStack((ItemLike) Items.WOODEN_PICKAXE) : new ItemStack((ItemLike) PoliciaShield.SHIELD.get())), this.getX() + 2, this.getY() + 1);
        }
    }

    static class ArmorHideButton extends Button {
        ArmorHideButton() {
            super(0, 0, 20, 18, Component.literal(""), b -> PoliciaClient.send(62), DEFAULT_NARRATION);
            this.setTooltip(Tooltip.create(Component.literal("Ocultar o mostrar tu armadura puesta")));
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            super.renderWidget(g, mx, my, pt);
            PoliciaMod.Sync st = PoliciaClient.mine();
            boolean hidden = st != null && st.hideArmor;
            g.renderItem(new ItemStack((ItemLike) (hidden ? Items.LEATHER_CHESTPLATE : Items.IRON_CHESTPLATE)), this.getX() + 2, this.getY() + 1);
        }
    }

    static class Morph {
        long start;
        boolean forming;
        DynamicTexture dyn;
        ResourceLocation rl;
        NativeImage orig;
        NativeImage pol;

        Morph() {
        }
    }
}
