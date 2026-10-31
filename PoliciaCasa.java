package com.example.policia;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

// Etapa 8: casa propia. La "Escritura de terreno" (papel, cartel y 3
// esmeraldas) se usa en el suelo y te da un terreno de 17 x 17. Ahi nadie
// mas puede romper, poner, abrir cofres ni puertas (salvo los invitados) y
// las explosiones no lo dañan. Tu casa es una parada de carruaje gratis.
// Comandos: /casa, /casa invitar <jugador>, /casa echar <jugador>, /casa abandonar.
public class PoliciaCasa {
    static final int R = 8;
    public static final RegistryObject<Item> ESCRITURA = PoliciaShield.ITEMS.register("escritura_terreno", () -> new Escritura(new Item.Properties().stacksTo(16)));
    static final Map<UUID, String> DONDE = new HashMap<UUID, String>();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Eventos());
    }

    static CompoundTag casas(MinecraftServer s) {
        return PoliciaMundo.get(s).getSeccion("casas");
    }

    static CompoundTag casaDe(MinecraftServer s, UUID u) {
        CompoundTag c = casas(s);
        return c.contains(u.toString()) ? c.getCompound(u.toString()) : null;
    }

    // Casa que contiene la posicion (o null).
    static CompoundTag casaEn(MinecraftServer s, BlockPos pos) {
        CompoundTag c = casas(s);
        for (String k : c.getAllKeys()) {
            CompoundTag t = c.getCompound(k);
            if (Math.abs(pos.getX() - t.getInt("x")) <= t.getInt("r") && Math.abs(pos.getZ() - t.getInt("z")) <= t.getInt("r")) {
                return t;
            }
        }
        return null;
    }

    static boolean puede(Player p, CompoundTag casa) {
        if (casa == null || p.hasPermissions(2) && p.isCreative()) {
            return true;
        }
        String u = p.getUUID().toString();
        if (u.equals(casa.getString("dueno"))) {
            return true;
        }
        ListTag inv = casa.getList("inv", Tag.TAG_STRING);
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getString(i).equals(u)) {
                return true;
            }
        }
        return false;
    }

    static boolean enSuCasa(ServerPlayer p) {
        CompoundTag c = casaEn(p.server, p.blockPosition());
        return c != null && p.getUUID().toString().equals(c.getString("dueno"));
    }

    static boolean bloqueado(Level l, Player p, BlockPos pos) {
        if (l.isClientSide || l.dimension() != Level.OVERWORLD || l.getServer() == null) {
            return false;
        }
        CompoundTag c = casaEn(l.getServer(), pos);
        if (c != null && !puede(p, c)) {
            p.displayClientMessage(Component.literal("§cEsta es la casa de " + c.getString("n")), true);
            return true;
        }
        return false;
    }

    public static class Escritura extends Item {
        Escritura(Item.Properties pr) {
            super(pr);
        }

        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            Level l = ctx.getLevel();
            if (l.isClientSide || !(ctx.getPlayer() instanceof ServerPlayer p)) {
                return InteractionResult.SUCCESS;
            }
            if (l.dimension() != Level.OVERWORLD) {
                PoliciaMod.msg(p, "Solo podés tener casa en el mundo normal");
                return InteractionResult.FAIL;
            }
            if (casaDe(p.server, p.getUUID()) != null) {
                PoliciaMod.msg(p, "Ya tenés casa. Usá /casa abandonar si querés mudarte");
                return InteractionResult.FAIL;
            }
            BlockPos c = ctx.getClickedPos();
            CompoundTag todas = casas(p.server);
            for (String k : todas.getAllKeys()) {
                CompoundTag t = todas.getCompound(k);
                if (Math.abs(c.getX() - t.getInt("x")) <= R + t.getInt("r") && Math.abs(c.getZ() - t.getInt("z")) <= R + t.getInt("r")) {
                    PoliciaMod.msg(p, "Ese terreno se pisa con la casa de " + t.getString("n"));
                    return InteractionResult.FAIL;
                }
            }
            CompoundTag t = new CompoundTag();
            t.putString("dueno", p.getUUID().toString());
            t.putString("n", p.getName().getString());
            t.putInt("x", c.getX());
            t.putInt("y", c.getY() + 1);
            t.putInt("z", c.getZ());
            t.putInt("r", R);
            t.put("inv", new ListTag());
            todas.put(p.getUUID().toString(), t);
            PoliciaMundo.get(p.server).setDirty();
            if (!p.isCreative()) {
                ctx.getItemInHand().shrink(1);
            }
            borde((ServerLevel) l, c, R);
            l.playSound(null, c, SoundEvents.VILLAGER_CELEBRATE, SoundSource.PLAYERS, 1.0f, 1.0f);
            PoliciaMod.msg(p, "¡Compraste tu terreno de 17 × 17! Nadie más puede tocar nada adentro. /casa para ver opciones");
            PoliciaNivel.logro(p, "casa");
            PoliciaRPG.sync(p);
            return InteractionResult.CONSUME;
        }

        @Override
        public void appendHoverText(net.minecraft.world.item.ItemStack st, Level level, java.util.List<Component> tip, net.minecraft.world.item.TooltipFlag f) {
            tip.add(Component.literal("§7Clic derecho en el suelo: tu terreno de 17 × 17"));
            tip.add(Component.literal("§8Protegido, y es una parada de carruaje gratis"));
        }
    }

    static void borde(ServerLevel sl, BlockPos c, int r) {
        for (int i = -r; i <= r; i++) {
            for (int[] q : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                BlockPos b = c.offset(q[0], 0, q[1]);
                int y = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getX(), b.getZ());
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, b.getX() + 0.5, y + 0.3, b.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
        }
    }

    public static class Eventos {
        @SubscribeEvent
        public void romper(BlockEvent.BreakEvent e) {
            if (e.getLevel() instanceof Level l && bloqueado(l, e.getPlayer(), e.getPos())) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void poner(BlockEvent.EntityPlaceEvent e) {
            if (e.getEntity() instanceof Player p && e.getLevel() instanceof Level l && bloqueado(l, p, e.getPos())) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void usar(PlayerInteractEvent.RightClickBlock e) {
            Level l = e.getLevel();
            BlockState st = l.getBlockState(e.getPos());
            boolean protegido = l.getBlockEntity(e.getPos()) != null || st.getBlock() instanceof DoorBlock || st.getBlock() instanceof TrapDoorBlock || st.getBlock() instanceof FenceGateBlock;
            if (protegido && bloqueado(l, e.getEntity(), e.getPos())) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void explosion(ExplosionEvent.Detonate e) {
            Level l = e.getLevel();
            if (l.isClientSide || l.getServer() == null || l.dimension() != Level.OVERWORLD) {
                return;
            }
            e.getAffectedBlocks().removeIf(b -> casaEn(l.getServer(), b) != null);
        }

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 11 || p.serverLevel().dimension() != Level.OVERWORLD) {
                return;
            }
            CompoundTag c = casaEn(p.server, p.blockPosition());
            String ahora = c == null ? "" : c.getString("dueno");
            String antes = DONDE.put(p.getUUID(), ahora);
            if (antes != null && !antes.equals(ahora) && c != null) {
                boolean mia = ahora.equals(p.getUUID().toString());
                p.displayClientMessage(Component.literal(mia ? "§aEntraste a tu casa" : "§eCasa de " + c.getString("n")), true);
            }
        }

        @SubscribeEvent
        public void comandos(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("casa")
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CompoundTag t = casaDe(p.server, p.getUUID());
                        if (t == null) {
                            PoliciaMod.msg(p, "No tenés casa. Hacé una Escritura de terreno (papel, cartel y 3 esmeraldas) y usala en el suelo");
                            return 0;
                        }
                        p.displayClientMessage(Component.literal("§6Tu casa: §f" + t.getInt("x") + ", " + t.getInt("y") + ", " + t.getInt("z") + " §7(17 × 17) · invitados: " + t.getList("inv", Tag.TAG_STRING).size()), false);
                        borde(p.serverLevel(), new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")), t.getInt("r"));
                        return 1;
                    })
                    .then(Commands.literal("invitar").then(Commands.argument("jugador", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CompoundTag t = casaDe(p.server, p.getUUID());
                        ServerPlayer o = p.server.getPlayerList().getPlayerByName(StringArgumentType.getString(c, "jugador"));
                        if (t == null || o == null) {
                            PoliciaMod.msg(p, t == null ? "No tenés casa" : "Ese jugador no está conectado");
                            return 0;
                        }
                        t.getList("inv", Tag.TAG_STRING).add(StringTag.valueOf(o.getUUID().toString()));
                        t.put("inv", t.getList("inv", Tag.TAG_STRING));
                        PoliciaMundo.get(p.server).setDirty();
                        PoliciaMod.msg(p, o.getName().getString() + " ya puede usar tu casa");
                        o.displayClientMessage(Component.literal("§a" + p.getName().getString() + " te invitó a su casa"), false);
                        return 1;
                    })))
                    .then(Commands.literal("echar").then(Commands.argument("jugador", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CompoundTag t = casaDe(p.server, p.getUUID());
                        ServerPlayer o = p.server.getPlayerList().getPlayerByName(StringArgumentType.getString(c, "jugador"));
                        if (t == null || o == null) {
                            return 0;
                        }
                        ListTag inv = t.getList("inv", Tag.TAG_STRING);
                        inv.removeIf(x -> x.getAsString().equals(o.getUUID().toString()));
                        t.put("inv", inv);
                        PoliciaMundo.get(p.server).setDirty();
                        PoliciaMod.msg(p, o.getName().getString() + " ya no puede usar tu casa");
                        return 1;
                    })))
                    .then(Commands.literal("abandonar").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        casas(p.server).remove(p.getUUID().toString());
                        PoliciaMundo.get(p.server).setDirty();
                        PoliciaMod.msg(p, "Dejaste tu terreno. Podés usar otra escritura en otro lado");
                        return 1;
                    })));
        }
    }
}
