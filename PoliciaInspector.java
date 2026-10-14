package com.example.policia;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Inspector de obras: cuando un albañil construyó bastante, cada tanto lo
// visita un inspector que le pone una nota del 1 al 10 según tamaño,
// variedad de bloques, iluminación y detalles. Con nota alta hay premio.
public class PoliciaInspector {
    static final int MIN_BLOCKS = 150;

    static void init() {
        MinecraftForge.EVENT_BUS.register((Object) new PoliciaInspector());
    }

    static boolean isLight(String id) {
        return id.contains("torch") || id.contains("lantern") || id.contains("lamp") || id.contains("glowstone") || id.contains("shroomlight") || id.contains("campfire") || id.contains("candle");
    }

    static boolean isDetail(String id) {
        return id.contains("stairs") || id.contains("slab") || id.contains("door") || id.contains("glass") || id.contains("carpet") || id.contains("pot") || id.contains("fence") || id.contains("wall") || id.contains("banner") || id.contains("bed") || id.contains("bookshelf") || id.contains("trapdoor") || id.contains("sign") || id.contains("painting") || id.contains("button") || id.contains("lectern");
    }

    @SubscribeEvent
    public void place(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !"albanil".equals(PoliciaMod.job(p))) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        BlockState s = e.getPlacedBlock();
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        d.putInt("insp_n", d.getInt("insp_n") + 1);
        if (PoliciaInspector.isLight(id)) d.putInt("insp_l", d.getInt("insp_l") + 1);
        if (PoliciaInspector.isDetail(id)) d.putInt("insp_d", d.getInt("insp_d") + 1);
        ListTag types = d.getList("insp_ty", 8);
        if (types.size() < 40) {
            boolean has = false;
            for (Tag t : types) {
                if (t.getAsString().equals(id)) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                types.add(StringTag.valueOf(id));
                d.put("insp_ty", types);
            }
        }
        int y = e.getPos().getY();
        if (!d.contains("insp_y0")) {
            d.putInt("insp_y0", y);
            d.putInt("insp_y1", y);
        } else {
            d.putInt("insp_y0", Math.min(y, d.getInt("insp_y0")));
            d.putInt("insp_y1", Math.max(y, d.getInt("insp_y1")));
        }
        d.putLong("insp_at", e.getPos().asLong());
    }

    static int[] grade(CompoundTag d) {
        int n = d.getInt("insp_n");
        int size = Math.min(4, n / 100);
        int variety = Math.min(3, d.getList("insp_ty", 8).size() / 5);
        int l = d.getInt("insp_l");
        int light = l >= 12 ? 2 : (l >= 4 ? 1 : 0);
        int detail = d.getInt("insp_d") >= 15 ? 1 : 0;
        int h = d.getInt("insp_y1") - d.getInt("insp_y0");
        int bonus = h >= 6 && size >= 2 ? 1 : 0;
        int total = Math.max(1, Math.min(10, size + variety + light + detail + bonus));
        return new int[]{total, size, variety, light, detail + bonus};
    }

    static void reset(CompoundTag d) {
        for (String k : new String[]{"insp_n", "insp_l", "insp_d", "insp_ty", "insp_y0", "insp_y1"}) {
            d.remove(k);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 200 != 0) {
            return;
        }
        if (!"albanil".equals(PoliciaMod.job(p)) || !PoliciaMod.on(p) || p.level().dimension() != Level.OVERWORLD) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        if (d.hasUUID("insp_e")) {
            if (now > d.getLong("insp_end")) {
                net.minecraft.world.entity.Entity v = sl.getEntity(d.getUUID("insp_e"));
                if (v != null) v.discard();
                d.remove("insp_e");
                PoliciaMod.msg(p, "El inspector de obras se cansó de esperar y se fue");
                d.putLong("insp_next", now + 12000L);
            }
            return;
        }
        if (d.getInt("insp_n") < MIN_BLOCKS || now < d.getLong("insp_next")) {
            return;
        }
        if (p.getRandom().nextInt(3) != 0) {
            return;
        }
        BlockPos at = BlockPos.of(d.getLong("insp_at"));
        if (at.distSqr(p.blockPosition()) > 64 * 64) {
            at = p.blockPosition();
        }
        double a = p.getRandom().nextDouble() * Math.PI * 2.0;
        BlockPos sp = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos((int) (p.getX() + Math.cos(a) * 6), 0, (int) (p.getZ() + Math.sin(a) * 6)));
        Villager v = EntityType.VILLAGER.create(sl);
        if (v == null) return;
        v.setVillagerData(v.getVillagerData().setType(VillagerType.PLAINS).setProfession(VillagerProfession.CARTOGRAPHER).setLevel(5));
        v.setCustomName(Component.literal("Inspector de obras"));
        v.setCustomNameVisible(true);
        v.moveTo(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0.0f, 0.0f);
        v.getPersistentData().putUUID("insp_for", p.getUUID());
        v.setPersistenceRequired();
        sl.addFreshEntity(v);
        d.putUUID("insp_e", v.getUUID());
        d.putLong("insp_end", now + 2400L);
        sl.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0f, 0.9f);
        p.sendSystemMessage(Component.literal("§6¡Llegó el inspector de obras a revisar tu construcción! Hablale (clic derecho) antes de 2 minutos."));
    }

    @SubscribeEvent
    public void talk(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof Villager v)) {
            return;
        }
        if (!v.getPersistentData().hasUUID("insp_for")) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!v.getPersistentData().getUUID("insp_for").equals(p.getUUID())) {
            PoliciaMod.msg(p, "<Inspector> Estoy revisando la obra de otro albañil.");
            return;
        }
        CompoundTag d = p.getPersistentData();
        int[] g = PoliciaInspector.grade(d);
        int nota = g[0];
        ServerLevel sl = p.serverLevel();
        String verdict = nota >= 9 ? "¡Una obra maestra!" : (nota >= 7 ? "Muy buen trabajo." : (nota >= 5 ? "Aprobado, pero se puede mejorar." : "Esto no pasa la inspección."));
        p.sendSystemMessage(Component.literal("§6<Inspector de obras> §f" + verdict + " §eNota: " + nota + "/10"));
        p.sendSystemMessage(Component.literal("§7  Tamaño " + g[1] + "/4 · Variedad " + g[2] + "/3 · Iluminación " + g[3] + "/2 · Detalles " + g[4] + "/1"));
        if (nota >= 5) {
            int em = PoliciaEstacion.scale(sl, "albanil", nota * 2);
            PoliciaBoss.give(p, new ItemStack(Items.EMERALD, em));
            d.putInt("pol_xp", PoliciaMod.xp(p) + nota);
            p.sendSystemMessage(Component.literal("§aPremio: " + em + " esmeraldas y " + nota + " XP de oficio"));
            sl.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0f, 1.0f);
        } else {
            p.sendSystemMessage(Component.literal("§7Consejo: usá más tipos de bloques, iluminá bien y agregá detalles (escaleras, puertas, vidrio, alfombras)."));
            sl.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
        }
        if (nota >= 8) {
            PoliciaDiario.add(sl, "El inspector le puso un " + nota + " a la obra del albañil " + p.getName().getString());
        }
        PoliciaInspector.reset(d);
        d.remove("insp_e");
        d.putLong("insp_next", sl.getGameTime() + 24000L + p.getRandom().nextInt(12000));
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, v.getX(), v.getY() + 1.0, v.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        v.discard();
        PoliciaMod.sync(p);
    }
}
