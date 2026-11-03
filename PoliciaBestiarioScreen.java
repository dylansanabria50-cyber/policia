package com.example.policia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;

// Bestiario: lista de criaturas que mataste con su modelo, vida, dano,
// debilidades y tu rango de conocimiento (que da dano extra).
public class PoliciaBestiarioScreen extends Screen {
    static final int W = 340;
    static final int H = 232;
    static final Map<String, LivingEntity> CACHE = new HashMap<String, LivingEntity>();
    final Screen padre;
    int x0;
    int y0;
    int sel;
    int scroll;
    final List<String> claves = new ArrayList<String>();
    int total;

    public PoliciaBestiarioScreen(Screen padre) {
        super(Component.literal("Bestiario"));
        this.padre = padre;
    }

    static CompoundTag b() {
        CompoundTag d = PoliciaRPG.CLIENTE;
        return d == null ? new CompoundTag() : d.getCompound("bestiario");
    }

    @Override
    protected void init() {
        this.x0 = (this.width - W) / 2;
        this.y0 = Math.max(4, (this.height - H) / 2);
        PoliciaRed.CH.sendToServer(new PoliciaRed.Accion("sync", new CompoundTag()));
        this.addRenderableWidget(Button.builder(Component.literal("Volver"), b -> this.onClose()).bounds(this.x0 + W - 90, this.y0 + H - 24, 80, 18).build());
        this.total = 0;
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            if (t.getCategory() == MobCategory.MONSTER) {
                this.total++;
            }
        }
        this.rehacer();
    }

    void rehacer() {
        this.claves.clear();
        CompoundTag b = b();
        this.claves.addAll(b.getAllKeys());
        this.claves.sort((a, c) -> Integer.compare(b.getInt(c), b.getInt(a)));
        if (this.sel >= this.claves.size()) {
            this.sel = 0;
        }
    }

    @Override
    public void tick() {
        if (b().getAllKeys().size() != this.claves.size()) {
            this.rehacer();
        }
    }

    static LivingEntity entidad(String k) {
        if (CACHE.containsKey(k)) {
            return CACHE.get(k);
        }
        LivingEntity le = null;
        try {
            EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(k));
            Entity e = t.create(Minecraft.getInstance().level);
            if (e instanceof LivingEntity l) {
                le = l;
            }
        } catch (Throwable ignored) {
        }
        CACHE.put(k, le);
        return le;
    }

    static String notas(String k, LivingEntity e) {
        StringBuilder sb = new StringBuilder();
        String id = k.contains(":") ? k.substring(k.indexOf(':') + 1) : k;
        String especial = switch (id) {
            case "creeper" -> "Explota si se acerca: pegale y alejate.";
            case "enderman" -> "No lo mires a los ojos. El agua lo lastima.";
            case "blaze" -> "Las bolas de nieve le hacen daño.";
            case "skeleton", "stray" -> "Peleá cerca: de lejos te gana.";
            case "spider", "cave_spider" -> "Trepa paredes. La araña de cueva envenena.";
            case "witch" -> "Se cura con pociones: atacá rápido.";
            case "zombie", "husk", "drowned" -> "Lento y duro. El ahogado puede tirar tridentes.";
            case "phantom" -> "Aparece si no dormís en días.";
            case "slime", "magma_cube" -> "Se divide al morir.";
            case "ravager" -> "Embiste: esquivá hacia el costado.";
            case "evoker" -> "Invoca colmillos y vexes: atacalo primero.";
            case "vindicator" -> "Pega muy fuerte con el hacha.";
            case "pillager" -> "Ballesta: cubrite con escudo.";
            case "wither_skeleton" -> "Su golpe marchita. Puede soltar su cráneo.";
            case "guardian", "elder_guardian" -> "Rayo que carga: cortale la vista.";
            case "piglin", "piglin_brute" -> "El oro los calma (salvo al bruto).";
            case "ghast" -> "Devolvé sus bolas de fuego de un golpe.";
            case "warden" -> "No hagas ruido. Mejor no pelear.";
            default -> "";
        };
        if (!especial.isEmpty()) {
            sb.append(especial).append("\n");
        }
        if (e != null) {
            MobType mt = e.getMobType();
            if (mt == MobType.UNDEAD) {
                sb.append("No-muerto: Castigo le hace más daño y las pociones de curación lo lastiman.\n");
            } else if (mt == MobType.ARTHROPOD) {
                sb.append("Artrópodo: Perdición de los artrópodos.\n");
            } else if (mt == MobType.ILLAGER) {
                sb.append("Illager: los golems de hierro lo atacan.\n");
            } else if (mt == MobType.WATER) {
                sb.append("Acuático: Empalamiento le hace más daño.\n");
            }
            if (e.getType().fireImmune()) {
                sb.append("Inmune al fuego.\n");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int lx = this.x0 + 8;
        int ly = this.y0 + 28;
        for (int i = 0; i < 16; i++) {
            int k = i + this.scroll;
            if (k >= this.claves.size()) {
                break;
            }
            if (mx >= lx && mx < lx + 120 && my >= ly + i * 11 && my < ly + i * 11 + 11) {
                this.sel = k;
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        this.scroll = Math.max(0, Math.min(Math.max(0, this.claves.size() - 16), this.scroll - (int) Math.signum(d)));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.padre);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int x = this.x0;
        int y = this.y0;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF6B4B22);
        g.fill(x, y, x + W, y + H, 0xF0201A14);
        g.fill(x, y, x + W, y + 22, 0xFF3A2B18);
        g.drawCenteredString(this.font, "§d§lBESTIARIO", x + W / 2, y + 7, 0xFFFFFF);
        CompoundTag b = b();
        int monstruos = 0;
        for (String k : this.claves) {
            EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(k));
            if (t.getCategory() == MobCategory.MONSTER) {
                monstruos++;
            }
        }
        g.drawString(this.font, "§7Monstruos: §f" + monstruos + "/" + this.total, x + 8, y + H - 19, 0xFFFFFF);
        g.fill(x + 6, y + 26, x + 130, y + H - 26, 0x60000000);
        if (this.claves.isEmpty()) {
            g.drawString(this.font, "§7Vacío: cazá algo", x + 10, y + 30, 0xFFFFFF);
        }
        for (int i = 0; i < 16; i++) {
            int k = i + this.scroll;
            if (k >= this.claves.size()) {
                break;
            }
            String c = this.claves.get(k);
            EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(c));
            int ly = y + 28 + i * 11;
            if (k == this.sel) {
                g.fill(x + 7, ly - 1, x + 129, ly + 10, 0x60E7B84A);
            }
            String n = t.getDescription().getString();
            if (this.font.width(n) > 92) {
                n = this.font.plainSubstrByWidth(n, 88) + "…";
            }
            int r = PoliciaBestiario.rango(b.getInt(c));
            g.drawString(this.font, (r >= 3 ? "§6" : r >= 2 ? "§e" : r >= 1 ? "§a" : "§f") + n, x + 9, ly, 0xFFFFFF);
            String cnt = String.valueOf(b.getInt(c));
            g.drawString(this.font, "§7" + cnt, x + 127 - this.font.width(cnt), ly, 0xFFFFFF);
        }
        if (!this.claves.isEmpty()) {
            String c = this.claves.get(this.sel);
            LivingEntity e = entidad(c);
            int n = b.getInt(c);
            int r = PoliciaBestiario.rango(n);
            int px = x + 138;
            g.drawString(this.font, "§f§l" + BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(c)).getDescription().getString(), px, y + 28, 0xFFFFFF);
            if (e != null) {
                float alto = Math.max(0.5f, e.getBbHeight());
                int esc = (int) Math.min(40, 50 / alto);
                g.enableScissor(px, y + 40, px + 80, y + 140);
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 40, y + 132, esc, px + 40 - mx, y + 80 - my, e);
                g.disableScissor();
                int ty = y + 44;
                g.drawString(this.font, "§c❤ §f" + (int) e.getMaxHealth() + " de vida", px + 86, ty, 0xFFFFFF);
                if (e.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                    g.drawString(this.font, "§6⚔ §f" + String.format("%.0f", e.getAttributeValue(Attributes.ATTACK_DAMAGE)) + " de daño", px + 86, ty + 12, 0xFFFFFF);
                }
                g.drawString(this.font, "§7Muertes: §f" + n, px + 86, ty + 24, 0xFFFFFF);
                g.drawString(this.font, "§7Rango: §e" + PoliciaBestiario.RANGO[r], px + 86, ty + 36, 0xFFFFFF);
                g.drawString(this.font, r > 0 ? "§a+" + (int) (PoliciaBestiario.BONO[r - 1] * 100) + " % de daño" : "§8sin bono", px + 86, ty + 48, 0xFFFFFF);
                if (r < 3) {
                    g.drawString(this.font, "§8Próximo: " + PoliciaBestiario.UMBRAL[r] + " muertes", px + 86, ty + 60, 0xFFFFFF);
                }
            }
            int ny = y + 146;
            for (var line : this.font.split(Component.literal("§7" + notas(c, e)), W - 146)) {
                if (ny > y + H - 30) {
                    break;
                }
                g.drawString(this.font, line, px, ny, 0xFFFFFF);
                ny += 10;
            }
        }
        super.render(g, mx, my, pt);
    }
}
