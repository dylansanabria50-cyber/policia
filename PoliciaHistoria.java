package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Historia principal: "La campana del alba", en 5 capitulos.
// Celestina, la cronista de la comarca, cuenta que unos bandidos robaron la
// Campana del Alba, la reliquia que protege a las aldeas. Cada capitulo
// tiene un objetivo y al volver a hablar con ella una DECISION que cambia
// la reputacion con las facciones y el final.
// Los datos van en rpg.historia (se sincronizan con la ficha):
//   cap (0 = sin empezar, 1-5, 6 = terminada), listo (objetivo cumplido),
//   cont {muertes, elites, paradas0, guardian}, dec {botin, gema, tuerto},
//   fin ("heroe", "sombras" o "cronista").
// Celestina vive en el mundo (PoliciaMundo, seccion "historia"); si no
// esta, aparece cerca de la primera parada de carruaje o del spawn.
public class PoliciaHistoria {
    static final String NPC = "pol_cronista";

    static void init() {
        MinecraftForge.EVENT_BUS.register(new EventosHistoria());
    }

    static CompoundTag datos(ServerPlayer p) {
        CompoundTag r = PoliciaRPG.datos(p);
        if (!r.contains("historia")) {
            r.put("historia", new CompoundTag());
        }
        return r.getCompound("historia");
    }

    // ---------------- Capitulos ----------------
    static final String[] TITULO = {"", "El robo de la campana", "Tras el rastro", "El campamento del Tuerto", "El eco bajo la tierra", "La campana del alba"};

    static String objetivo(ServerPlayer p, CompoundTag h) {
        CompoundTag c = h.getCompound("cont");
        return switch (h.getInt("cap")) {
            case 1 -> "Derrotá monstruos (" + Math.min(5, c.getInt("muertes")) + "/5) y un élite (" + Math.min(1, c.getInt("elites")) + "/1)";
            case 2 -> "Descubrí paradas nuevas (" + Math.min(2, paradas(p) - c.getInt("paradas0")) + "/2) y conseguí una gema" + (tieneGema(p) ? " ✔" : "");
            case 3 -> "Derrotá élites del Tuerto (" + Math.min(3, c.getInt("elites")) + "/3)";
            case 4 -> "Vencé al Guardián del Eco en la Cueva del Eco" + (c.getBoolean("guardian") ? " ✔" : "");
            case 5 -> "Llegá al nivel 12 (" + PoliciaRPG.nivel(p) + "/12) con la Campana del Alba";
            default -> "";
        };
    }

    static boolean cumplido(ServerPlayer p, CompoundTag h) {
        CompoundTag c = h.getCompound("cont");
        return switch (h.getInt("cap")) {
            case 1 -> c.getInt("muertes") >= 5 && c.getInt("elites") >= 1;
            case 2 -> paradas(p) - c.getInt("paradas0") >= 2 && tieneGema(p);
            case 3 -> c.getInt("elites") >= 3;
            case 4 -> c.getBoolean("guardian");
            case 5 -> PoliciaRPG.nivel(p) >= 12 && tieneCampana(p);
            default -> false;
        };
    }

    static int paradas(ServerPlayer p) {
        return PoliciaRPG.datos(p).getCompound("paradas").size();
    }

    static boolean tieneGema(ServerPlayer p) {
        for (ItemStack st : p.getInventory().items) {
            if (st.getItem() instanceof PoliciaBotin.Gema) {
                return true;
            }
        }
        return false;
    }

    static boolean esCampana(ItemStack st) {
        return st.is(Items.BELL) && st.hasTag() && st.getTag().getBoolean("pol_campana");
    }

    static boolean tieneCampana(ServerPlayer p) {
        for (ItemStack st : p.getInventory().items) {
            if (esCampana(st)) {
                return true;
            }
        }
        return false;
    }

    static ItemStack campana() {
        ItemStack st = new ItemStack(Items.BELL);
        st.getOrCreateTag().putBoolean("pol_campana", true);
        st.setHoverName(Component.literal("§6Campana del Alba").withStyle(s -> s.withItalic(false)));
        return st;
    }

    static void empezarCap(ServerPlayer p, CompoundTag h, int cap) {
        h.putInt("cap", cap);
        h.putBoolean("listo", false);
        CompoundTag c = new CompoundTag();
        c.putInt("paradas0", paradas(p));
        h.put("cont", c);
        h.putString("obj", cap <= 5 ? objetivo(p, h) : "");
        if (cap <= 5) {
            titulo(p, "§6Capítulo " + cap, "§f" + TITULO[cap]);
            p.displayClientMessage(Component.literal("§6Historia · §f" + objetivo(p, h)), false);
        }
        PoliciaRPG.sync(p);
    }

    static void titulo(ServerPlayer p, String t, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    static void premio(ServerPlayer p, int xp, int esm) {
        PoliciaNivel.dar(p, xp);
        ItemStack e = new ItemStack(Items.EMERALD, esm);
        if (!p.getInventory().add(e)) {
            p.drop(e, false);
        }
        p.displayClientMessage(Component.literal("§a+" + xp + " XP y " + esm + " esmeraldas"), false);
    }

    static void dar(ServerPlayer p, ItemStack st) {
        if (!p.getInventory().add(st)) {
            p.drop(st, false);
        }
    }

    // ---------------- Charla con Celestina ----------------
    // El servidor arma lo que dice y las opciones; el cliente responde con
    // el numero de opcion (PoliciaRed.HistoriaElegir).
    static void hablar(ServerPlayer p, Entity npc) {
        CompoundTag h = datos(p);
        int cap = h.getInt("cap");
        List<String> l = new ArrayList<String>();
        List<String> op = new ArrayList<String>();
        if (cap == 0) {
            l.add("¿Vos sos el que todos nombran? Qué suerte. Soy Celestina, la cronista de la comarca.");
            l.add("Anoche robaron la Campana del Alba. Mientras sonaba cada mañana, ninguna sombra se animaba a entrar a las aldeas.");
            l.add("Sin ella, los monstruos se vuelven más fuertes cada noche. Algunos ya andan con fuerzas que no son de este mundo.");
            l.add("Necesito a alguien que sepa pelear. ¿Me vas a ayudar?");
            op.add("Cuente conmigo.");
            op.add("Ahora no puedo.");
        } else if (cap >= 6) {
            String fin = h.getString("fin");
            l.add(fin.equals("sombras") ? "La campana suena para vos ahora, rey de las sombras. Espero que sepas lo que hacés." : fin.equals("cronista") ? "La campana está a salvo en mi torre. Gracias por confiar en mí." : "Cada mañana la campana suena y la gente sale tranquila. Es gracias a vos, héroe.");
            l.add("Tu historia ya está escrita en mis crónicas. Pero la comarca siempre necesita manos: hablá con los jefes de los oficios.");
            op.add("Hasta pronto, Celestina.");
        } else if (!h.getBoolean("listo")) {
            l.add(recordatorio(cap));
            l.add("Objetivo: " + objetivo(p, h));
            op.add("Ya voy.");
        } else {
            switch (cap) {
                case 1 -> {
                    l.add("¡Volviste! Y traés lo que esos monstruos llevaban encima. Hay bolsas con el sello de la aldea.");
                    l.add("Ese oro es de los vecinos... aunque nadie se daría cuenta si te lo guardás.");
                    op.add("Devolvé todo a la aldea.");
                    op.add("Me lo quedo. Me lo gané.");
                }
                case 2 -> {
                    l.add("Esa gema... la reconozco. Es de la Campana: se desprendió cuando la arrancaron.");
                    l.add("Con ella puedo rastrear el resto. Pero el Perista paga fortunas por estas piedras.");
                    op.add("Tomá, rastreala.");
                    op.add("Se la vendo al Perista.");
                }
                case 3 -> {
                    l.add("Derrotaste a sus mejores hombres. El Tuerto está atado afuera, pidiendo clemencia.");
                    l.add("Dice que la campana la tiene el Guardián del Eco, en la cueva. ¿Qué hacemos con él?");
                    op.add("Entregalo a la Guardia.");
                    op.add("Perdonalo: que nos ayude.");
                    op.add("Me uno a su banda.");
                }
                case 4 -> {
                    l.add("¡La Campana del Alba! Todavía se siente el eco de la cueva en el metal.");
                    l.add("Para que vuelva a sonar con fuerza, tenés que llevarla vos: tu nombre tiene que pesar en la comarca.");
                    op.add("Entendido.");
                }
                default -> {
                    l.add("Llegó el momento. La campana responde a quien la lleva.");
                    l.add("¿Dónde querés que suene la Campana del Alba?");
                    op.add("En la plaza de la aldea, para todos.");
                    op.add("En mi escondite. Que suene para mí.");
                    op.add("Guardala vos, Celestina.");
                }
            }
        }
        PoliciaRed.toPlayer(p, new PoliciaRed.HistoriaDialogo(npc.getId(), "Celestina, la cronista", String.join("\n", l), String.join("\n", op)));
    }

    static String recordatorio(int cap) {
        return switch (cap) {
            case 1 -> "Los monstruos llevan cosas robadas de la aldea. Derrotalos y fijate si alguno es uno de esos élites con brillo.";
            case 2 -> "Recorré otras aldeas y preguntá. Dicen que una gema de la campana anda dando vueltas.";
            case 3 -> "El Tuerto tiene a sus élites rondando. Derrotá a tres y va a tener que hablar.";
            case 4 -> "La Cueva del Eco está bajo tierra, lejos del spawn. Cuando tengas nivel 10 aparece en el mapa de viaje.";
            default -> "La campana necesita a alguien con renombre. Volvé cuando seas más fuerte.";
        };
    }

    static void elegir(ServerPlayer p, int op) {
        CompoundTag h = datos(p);
        int cap = h.getInt("cap");
        if (cap == 0) {
            if (op == 0) {
                p.displayClientMessage(Component.literal("§7Celestina: «Que la campana te guíe.»"), false);
                empezarCap(p, h, 1);
            }
            return;
        }
        if (cap >= 6 || !h.getBoolean("listo")) {
            return;
        }
        CompoundTag dec = h.getCompound("dec");
        switch (cap) {
            case 1 -> {
                dec.putInt("botin", op);
                if (op == 0) {
                    PoliciaFacciones.sumar(p, "guardia", 15);
                    PoliciaFacciones.sumar(p, "artesanos", 10);
                    premio(p, 120, 5);
                } else {
                    PoliciaFacciones.sumar(p, "sombras", 15);
                    PoliciaFacciones.sumar(p, "guardia", -10);
                    premio(p, 120, 20);
                }
            }
            case 2 -> {
                dec.putInt("gema", op);
                quitarUnaGema(p);
                if (op == 0) {
                    PoliciaFacciones.sumar(p, "cronistas", 20);
                    premio(p, 160, 5);
                } else {
                    PoliciaFacciones.sumar(p, "mercaderes", 15);
                    PoliciaFacciones.sumar(p, "sombras", 5);
                    premio(p, 160, 25);
                }
            }
            case 3 -> {
                dec.putInt("tuerto", op);
                if (op == 0) {
                    PoliciaFacciones.sumar(p, "guardia", 25);
                    PoliciaFacciones.sumar(p, "sombras", -15);
                } else if (op == 1) {
                    PoliciaFacciones.sumar(p, "cronistas", 10);
                    PoliciaFacciones.sumar(p, "sombras", 5);
                } else {
                    PoliciaFacciones.sumar(p, "sombras", 30);
                    PoliciaFacciones.sumar(p, "guardia", -25);
                }
                premio(p, 220, 10);
            }
            case 4 -> premio(p, 250, 10);
            default -> {
                String fin = op == 0 ? "heroe" : op == 1 ? "sombras" : "cronista";
                h.putString("fin", fin);
                quitarCampana(p);
                ItemStack recompensa;
                if (fin.equals("heroe")) {
                    PoliciaFacciones.sumar(p, "guardia", 20);
                    PoliciaFacciones.sumar(p, "artesanos", 20);
                    recompensa = new ItemStack(PoliciaBotin.AMULETO_VIDA.get());
                } else if (fin.equals("sombras")) {
                    PoliciaFacciones.sumar(p, "sombras", 40);
                    recompensa = new ItemStack(PoliciaBotin.CAPA_SOMBRAS.get());
                } else {
                    PoliciaFacciones.sumar(p, "cronistas", 40);
                    recompensa = new ItemStack(PoliciaBotin.AMULETO_SABIO.get());
                }
                PoliciaRareza.ponerRareza(recompensa, 4);
                recompensa.getOrCreateTag().put("pol_afijos", PoliciaBotin.parsear("vida:4,xp:10"));
                dar(p, recompensa);
                premio(p, 500, 40);
                titulo(p, fin.equals("sombras") ? "§5Rey de las sombras" : fin.equals("cronista") ? "§bAmigo de la cronista" : "§6Héroe de la comarca", "§7Fin de «La campana del alba»");
                PoliciaDiario.add(p.serverLevel(), fin.equals("sombras") ? "La Campana del Alba suena en un escondite. Nadie sabe para quién." : "¡Volvió a sonar la Campana del Alba! " + p.getName().getString() + " la recuperó.");
                PoliciaNivel.logro(p, "historia");
            }
        }
        h.put("dec", dec);
        if (cap == 4) {
            dar(p, campana());
        }
        empezarCap(p, h, cap + 1);
    }

    static void quitarUnaGema(ServerPlayer p) {
        for (ItemStack st : p.getInventory().items) {
            if (st.getItem() instanceof PoliciaBotin.Gema) {
                st.shrink(1);
                return;
            }
        }
    }

    static void quitarCampana(ServerPlayer p) {
        for (ItemStack st : p.getInventory().items) {
            if (esCampana(st)) {
                st.shrink(1);
                return;
            }
        }
    }

    // ---------------- Celestina en el mundo ----------------
    static Villager buscarNpc(ServerLevel sl) {
        CompoundTag w = PoliciaMundo.get(sl.getServer()).getSeccion("historia");
        if (w.hasUUID("npc")) {
            Entity e = sl.getEntity(w.getUUID("npc"));
            if (e instanceof Villager v && v.isAlive()) {
                return v;
            }
        }
        return null;
    }

    static BlockPos lugarNpc(MinecraftServer s) {
        CompoundTag w = PoliciaMundo.get(s).getSeccion("historia");
        if (w.contains("x") && w.getBoolean("pueblo")) {
            return new BlockPos(w.getInt("x"), w.getInt("y"), w.getInt("z"));
        }
        ServerLevel ow = s.overworld();
        BlockPos base = ow.getSharedSpawnPos();
        CompoundTag par = PoliciaMundo.get(s).getSeccion("paradas");
        for (String k : par.getAllKeys()) {
            CompoundTag t = par.getCompound(k);
            base = new BlockPos(t.getInt("x") + 4, 0, t.getInt("z") + 4);
            break;
        }
        // todavia no se mudo a un pueblo: lugar provisorio (no se guarda)
        int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
        return new BlockPos(base.getX(), y, base.getZ());
    }

    // Celestina se muda al pueblo donde esta el jugador (el que lo dejo el
    // carruaje la primera vez). Si ya existia en otro lado, se la lleva.
    static void mudar(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        CompoundTag w = PoliciaMundo.get(p.server).getSeccion("historia");
        net.minecraft.world.phys.Vec3 lado = net.minecraft.world.phys.Vec3.directionFromRotation(0, p.getYRot() + 70).scale(3.5);
        BlockPos pos = null;
        for (int i = 0; i < 8 && pos == null; i++) {
            net.minecraft.world.phys.Vec3 v = lado.yRot((float) (i * Math.PI / 4));
            int x = (int) Math.floor(p.getX() + v.x);
            int z = (int) Math.floor(p.getZ() + v.z);
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos q = new BlockPos(x, y, z);
            if (Math.abs(y - p.getY()) <= 2 && sl.getBlockState(q.below()).getFluidState().isEmpty()) {
                pos = q;
            }
        }
        if (pos == null) {
            pos = p.blockPosition();
        }
        w.putInt("x", pos.getX());
        w.putInt("y", pos.getY());
        w.putInt("z", pos.getZ());
        w.putBoolean("pueblo", true);
        Villager v = buscarNpc(sl);
        if (v != null) {
            v.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        } else {
            w.remove("npc");
        }
        PoliciaMundo.get(p.server).setDirty();
        asegurarNpc(p.server);
        Villager n = buscarNpc(sl);
        if (n != null) {
            n.lookAt(p, 360, 360);
            n.setYHeadRot(n.getYRot());
        }
    }

    // Aparece si no esta (solo cuando su chunk esta cargado).
    static void asegurarNpc(MinecraftServer s) {
        ServerLevel ow = s.overworld();
        if (!PoliciaMundo.get(s).getSeccion("historia").getBoolean("pueblo")) {
            return;
        }
        BlockPos pos = lugarNpc(s);
        if (!ow.isLoaded(pos) || buscarNpc(ow) != null) {
            return;
        }
        for (Villager v : ow.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(pos).inflate(16))) {
            if (v.getPersistentData().getBoolean(NPC)) {
                guardarNpc(s, v);
                return;
            }
        }
        Villager v = EntityType.VILLAGER.create(ow);
        if (v == null) {
            return;
        }
        v.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        v.finalizeSpawn(ow, ow.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(5));
        v.setCustomName(Component.literal("§e❖ Celestina, la cronista"));
        v.setCustomNameVisible(true);
        v.setInvulnerable(true);
        v.setNoAi(true);
        v.setPersistenceRequired();
        v.getPersistentData().putBoolean(NPC, true);
        ow.addFreshEntity(v);
        guardarNpc(s, v);
    }

    static void guardarNpc(MinecraftServer s, Villager v) {
        CompoundTag w = PoliciaMundo.get(s).getSeccion("historia");
        w.putUUID("npc", v.getUUID());
        PoliciaMundo.get(s).setDirty();
    }

    static void contar(ServerPlayer p, String que) {
        CompoundTag h = datos(p);
        int cap = h.getInt("cap");
        if (cap < 1 || cap > 5 || h.getBoolean("listo")) {
            return;
        }
        CompoundTag c = h.getCompound("cont");
        if (que.equals("guardian") && cap != 4) {
            return;
        }
        if (que.equals("guardian")) {
            c.putBoolean("guardian", true);
        } else {
            c.putInt(que, c.getInt(que) + 1);
        }
        h.put("cont", c);
        revisar(p, h);
    }

    static void revisar(ServerPlayer p, CompoundTag h) {
        int cap = h.getInt("cap");
        if (cap < 1 || cap > 5 || h.getBoolean("listo")) {
            return;
        }
        if (cumplido(p, h)) {
            h.putBoolean("listo", true);
            BlockPos b = lugarNpc(p.server);
            p.displayClientMessage(Component.literal("§6Historia · §a¡Objetivo cumplido! §fVolvé a hablar con Celestina §7(X " + b.getX() + ", Z " + b.getZ() + ")"), false);
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.5f);
        }
        h.putString("obj", h.getBoolean("listo") ? "Volvé a hablar con Celestina" : objetivo(p, h));
        PoliciaRPG.sync(p);
    }

    public static class EventosHistoria {
        @SubscribeEvent
        public void tocar(PlayerInteractEvent.EntityInteract e) {
            if (e.getLevel().isClientSide || !(e.getEntity() instanceof ServerPlayer p) || !e.getTarget().getPersistentData().getBoolean(NPC)) {
                return;
            }
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            hablar(p, e.getTarget());
        }

        @SubscribeEvent
        public void muerte(LivingDeathEvent e) {
            LivingEntity m = e.getEntity();
            if (!(m instanceof Monster) || !(e.getSource().getEntity() instanceof ServerPlayer p)) {
                return;
            }
            CompoundTag h = datos(p);
            int cap = h.getInt("cap");
            if (cap == 1) {
                contar(p, "muertes");
            }
            if ((cap == 1 || cap == 3) && PoliciaCombate.esElite(m)) {
                contar(p, "elites");
            }
        }

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 40 != 13) {
                return;
            }
            CompoundTag h = datos(p);
            if (p.tickCount % 80 == 13 && PoliciaRPG.datos(p).getBoolean("intro") && !PoliciaCine.ocupado(p)) {
                CompoundTag w = PoliciaMundo.get(p.server).getSeccion("historia");
                if (!w.getBoolean("pueblo")) {
                    net.minecraft.world.level.levelgen.structure.StructureStart s = p.serverLevel().structureManager().getStructureWithPieceAt(p.blockPosition(), net.minecraft.tags.StructureTags.VILLAGE);
                    if (s != null && s.isValid() && p.serverLevel().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                        mudar(p);
                    }
                } else if (p.tickCount % 400 == 13) {
                    asegurarNpc(p.server);
                    // si quedo una Celestina vieja de antes de mudarse, se va
                    for (Villager v : p.serverLevel().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(64), x -> x.getPersistentData().getBoolean(NPC))) {
                        if (!w.hasUUID("npc") || !v.getUUID().equals(w.getUUID("npc"))) {
                            v.discard();
                        }
                    }
                }
            }
            // aviso para empezar (una vez, al llegar a nivel 2)
            if (h.getInt("cap") == 0 && !h.getBoolean("aviso") && PoliciaRPG.nivel(p) >= 2) {
                h.putBoolean("aviso", true);
                BlockPos b = lugarNpc(p.server);
                p.displayClientMessage(Component.literal("§eUna anciana pregunta por vos: §6Celestina, la cronista§e, te espera en §fX " + b.getX() + ", Z " + b.getZ() + " §7(/historia)"), false);
                h.putString("obj", "Hablá con Celestina, la cronista");
                PoliciaRPG.sync(p);
            }
            int cap = h.getInt("cap");
            if (cap == 2 || cap == 5) {
                String antes = h.getString("obj");
                revisar(p, h);
                if (antes.equals(h.getString("obj"))) {
                    return;
                }
            }
        }

        // /historia: capitulo, objetivo y donde esta Celestina.
        @SubscribeEvent
        public void comandos(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("historia").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                CompoundTag h = datos(p);
                BlockPos b = lugarNpc(p.server);
                int cap = h.getInt("cap");
                String s = cap == 0 ? "§6La campana del alba §7· sin empezar" : cap >= 6 ? "§6La campana del alba §7· terminada (" + h.getString("fin") + ")" : "§6Capítulo " + cap + ": §f" + TITULO[cap] + "\n§7" + (h.getBoolean("listo") ? "Volvé a hablar con Celestina" : objetivo(p, h));
                p.displayClientMessage(Component.literal(s + "\n§7Celestina está en §fX " + b.getX() + ", Y " + b.getY() + ", Z " + b.getZ()), false);
                return 1;
            }));
        }
    }
}
