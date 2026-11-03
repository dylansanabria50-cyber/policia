package com.example.policia;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Interfaz nueva del lado del cliente: brujula de objetivo arriba de la
// pantalla, casillero del consumible rapido (tecla V) y los datos que
// llegan para las pantallas de mercenarios, objetivos y tutorial.
public class PoliciaUICliente {
    static final KeyMapping K_CONSUMIR = new KeyMapping("RPG: consumible rápido (curarse)", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, 86, "Policia");
    static CompoundTag MERC = new CompoundTag();
    static CompoundTag OBJ = new CompoundTag();
    static CompoundTag FLECHA = new CompoundTag();
    static CompoundTag TUTORIAL = new CompoundTag();
    static long cdHasta;

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaUICliente::teclas);
        MinecraftForge.EVENT_BUS.addListener(PoliciaUICliente::tick);
        MinecraftForge.EVENT_BUS.addListener(PoliciaUICliente::hud);
    }

    static void teclas(RegisterKeyMappingsEvent e) {
        e.register(K_CONSUMIR);
    }

    static void recibir(String tipo, CompoundTag d) {
        switch (tipo) {
            case "ui_merc" -> MERC = d;
            case "ui_obj" -> OBJ = d;
            case "ui_flecha" -> FLECHA = d;
            case "ui_tutorial" -> TUTORIAL = d;
            default -> {
            }
        }
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            FLECHA = new CompoundTag();
            TUTORIAL = new CompoundTag();
            return;
        }
        while (K_CONSUMIR.consumeClick()) {
            if (mc.screen == null) {
                PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("consumir", new CompoundTag()));
                if (mc.level != null && mc.level.getGameTime() >= cdHasta && contar(mc.player) > 0) {
                    cdHasta = mc.level.getGameTime() + PoliciaAcciones.CD_CONSUMO;
                }
            }
        }
    }

    static int contar(LocalPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (PoliciaAcciones.prioridad(st, p) >= 3) {
                n += st.getCount();
            }
        }
        return n;
    }

    static ItemStack mejor(LocalPlayer p) {
        ItemStack m = ItemStack.EMPTY;
        int pri = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            int q = PoliciaAcciones.prioridad(st, p);
            if (q > pri) {
                pri = q;
                m = st;
            }
        }
        return m;
    }

    static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        brujula(mc, g, w);
        // ---- consumible rapido: a la derecha de la barra de objetos ----
        ItemStack st = mejor(mc.player);
        int x = w / 2 + 98;
        int y = h - 22;
        if (mc.player.getOffhandItem().isEmpty() || mc.options.mainHand().get() != net.minecraft.world.entity.HumanoidArm.LEFT) {
            g.fill(x, y, x + 20, y + 20, 0x90000000);
            g.fill(x, y, x + 20, y + 1, 0xFF8A6A3A);
            if (!st.isEmpty()) {
                g.renderItem(st, x + 2, y + 2);
                int n = contar(mc.player);
                if (n > 0) {
                    String s = String.valueOf(n);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.drawString(mc.font, s, x + 19 - mc.font.width(s), y + 11, 0xFFFFFF);
                    g.pose().popPose();
                }
            }
            long falta = mc.level == null ? 0 : cdHasta - mc.level.getGameTime();
            if (falta > 0) {
                int alto = (int) (18 * falta / (float) PoliciaAcciones.CD_CONSUMO);
                g.pose().pushPose();
                g.pose().translate(0, 0, 210);
                g.fill(x + 1, y + 19 - alto, x + 19, y + 19, 0x90FFFFFF);
                g.pose().popPose();
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 220);
            g.drawString(mc.font, "§7" + K_CONSUMIR.getTranslatedKeyMessage().getString(), x + 1, y - 9, 0xFFFFFF);
            g.pose().popPose();
        }
        PoliciaTutorialCliente.hud(mc, g, w, h);
    }

    static java.lang.reflect.Field F_EVENTOS;
    static boolean buscado;

    // Cuantas barras de jefe hay arriba (para no taparlas).
    static int barrasJefe(Minecraft mc) {
        try {
            if (!buscado) {
                buscado = true;
                for (java.lang.reflect.Field f : net.minecraft.client.gui.components.BossHealthOverlay.class.getDeclaredFields()) {
                    if (java.util.Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        F_EVENTOS = f;
                    }
                }
            }
            return F_EVENTOS == null ? 0 : Math.min(3, ((java.util.Map<?, ?>) F_EVENTOS.get(mc.gui.getBossOverlay())).size());
        } catch (Throwable t) {
            return 0;
        }
    }

    // Brujula: una tira arriba con la marca del objetivo, su nombre y distancia.
    static void brujula(Minecraft mc, GuiGraphics g, int w) {
        if (!FLECHA.contains("x") || mc.player == null) {
            return;
        }
        double dx = FLECHA.getInt("x") + 0.5 - mc.player.getX();
        double dz = FLECHA.getInt("z") + 0.5 - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float hacia = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float rel = Mth.wrapDegrees(hacia - mc.player.getYRot());
        int ancho = 182;
        int x0 = w / 2 - ancho / 2;
        int y0 = 4;
        y0 += barrasJefe(mc) * 19;
        g.fill(x0, y0, x0 + ancho, y0 + 10, 0x70000000);
        // marcas de rumbo
        String[] card = {"S", "O", "N", "E"};
        for (int k = 0; k < 4; k++) {
            float r = Mth.wrapDegrees(k * 90.0f - mc.player.getYRot());
            if (Math.abs(r) <= 90) {
                int cx = w / 2 + (int) (r / 90.0f * (ancho / 2 - 6));
                g.drawCenteredString(mc.font, "§7" + card[k], cx, y0 + 1, 0xFFFFFF);
            }
        }
        boolean llegaste = dist < 6;
        if (Math.abs(rel) <= 90) {
            int cx = w / 2 + (int) (rel / 90.0f * (ancho / 2 - 6));
            g.drawCenteredString(mc.font, "§e▼", cx, y0 + 1, 0xFFFFFF);
        } else {
            g.drawString(mc.font, rel < 0 ? "§e◀" : "§e▶", rel < 0 ? x0 + 2 : x0 + ancho - 8, y0 + 1, 0xFFFFFF);
        }
        String t = "§f" + FLECHA.getString("n") + " §7· " + (llegaste ? "§a¡Llegaste!" : (dist >= 1000 ? String.format("%.1f km", dist / 1000) : (int) dist + " m"));
        g.drawCenteredString(mc.font, t, w / 2, y0 + 12, 0xFFFFFF);
    }
}
