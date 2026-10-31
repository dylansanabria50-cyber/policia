package com.example.policia;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Etapa 6: arbol de habilidades nuevas de cada oficio (las de la vista
// previa). Se desbloquean con puntos de habilidad (1 + nivel RPG / 3) y
// piden un nivel minimo. Se equipa una y se usa con la tecla Z
// (Mayus + Z cambia a la siguiente desbloqueada).
// Las que duran varios ticks usan TAREAS (se corren en el tick del server).
public class PoliciaHab {
    public static class Def {
        final String id;
        final String oficio;
        final String nombre;
        final String desc;
        final int nivel;
        final int costo;
        final int cd;
        final String anim;

        Def(String id, String oficio, String nombre, String desc, int nivel, int costo, int cdSeg, String anim) {
            this.id = id;
            this.oficio = oficio;
            this.nombre = nombre;
            this.desc = desc;
            this.nivel = nivel;
            this.costo = costo;
            this.cd = cdSeg * 20;
            this.anim = anim;
        }
    }

    static final List<Def> DEFS = new ArrayList<Def>();

    static Def def(String id, String oficio, String nombre, String desc, int nivel, int costo, int cdSeg, String anim) {
        Def d = new Def(id, oficio, nombre, desc, nivel, costo, cdSeg, anim);
        DEFS.add(d);
        return d;
    }

    static {
        def("camara", "policia", "Cámara de seguridad", "Atornillás una cámara donde mirás: durante 60 s marca a los monstruos y ladrones que pasen a 20 bloques y te avisa.", 5, 1, 45, "pol_camara");
        def("dominio", "policia", "Dominio de la ley", "Cúpula de 10 bloques durante 20 s: los monstruos no pueden salir, quedan lentos y marcados, y cada golpe tuyo es crítico.", 20, 2, 150, "pol_dominio");
        def("grua", "albanil", "Grúa torre", "Primer uso: engancha el bloque o cofre que mirás. Segundo uso: lo deja donde mirás (hasta 24 bloques), con todo su contenido.", 5, 1, 20, "alb_grua");
        def("ascensor", "albanil", "Ascensor de obra", "Te sube hasta lo más alto de la construcción o el acantilado que tenés al lado (hasta 40 bloques).", 8, 1, 25, "alb_ascensor");
        def("taladro", "albanil", "Taladro de túnel", "Abre un túnel de 3 × 3 de 10 bloques hacia donde mirás y deja antorchas. Solo rompe terreno natural.", 12, 1, 40, "alb_taladro");
        def("puno", "albanil", "Puño de tierra", "Una mano de piedra sale bajo el enemigo que mirás, lo levanta y lo aplasta contra el piso.", 15, 1, 30, "alb_puno");
        def("torre", "albanil", "Torre instantánea", "Una torre de piedra te sube 8 bloques en un momento. Se deshace sola al minuto.", 18, 1, 45, "alb_torre");
        // Las de los otros oficios se registran en sus clases.
        PoliciaHabExtra.cargar();
        PoliciaHabOficios.cargar();
    }

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Eventos());
        MinecraftForge.EVENT_BUS.register(new PoliciaHabExtra.Eventos());
    }

    static Def buscar(String id) {
        for (Def d : DEFS) {
            if (d.id.equals(id)) {
                return d;
            }
        }
        return null;
    }

    static List<Def> deOficio(String oficio) {
        List<Def> l = new ArrayList<Def>();
        for (Def d : DEFS) {
            if (d.oficio.equals(oficio)) {
                l.add(d);
            }
        }
        return l;
    }

    // ---------------- Datos ----------------
    static CompoundTag datos(LivingEntity p) {
        CompoundTag r = p instanceof net.minecraft.world.entity.player.Player pl ? PoliciaRPG.datos(pl) : new CompoundTag();
        if (!r.contains("hab")) {
            r.put("hab", new CompoundTag());
        }
        return r.getCompound("hab");
    }

    static int puntosTotales(int nivel) {
        return 1 + nivel / 3;
    }

    static int gastados(CompoundTag h) {
        int n = 0;
        CompoundTag u = h.getCompound("u");
        for (String k : u.getAllKeys()) {
            Def d = buscar(k);
            if (d != null) {
                n += d.costo;
            }
        }
        return n;
    }

    static boolean tiene(CompoundTag h, String id) {
        return h.getCompound("u").getBoolean(id);
    }

    // ---------------- Acciones (llegan por PoliciaRed mensaje 3) ----------------
    // 0 desbloquear, 1 equipar, 2 usar la equipada, 3 equipar la siguiente
    static void accion(ServerPlayer p, int a, String id) {
        CompoundTag h = datos(p);
        String oficio = PoliciaMod.job(p);
        if (a == 0) {
            Def d = buscar(id);
            if (d == null || !d.oficio.equals(oficio) || tiene(h, id)) {
                return;
            }
            int nivel = PoliciaRPG.nivel(p);
            if (nivel < d.nivel) {
                PoliciaMod.msg(p, "Necesitás nivel " + d.nivel + " para " + d.nombre);
                return;
            }
            if (puntosTotales(nivel) - gastados(h) < d.costo) {
                PoliciaMod.msg(p, "No tenés puntos de habilidad suficientes (subí de nivel)");
                return;
            }
            CompoundTag u = h.getCompound("u");
            u.putBoolean(id, true);
            h.put("u", u);
            if (!h.contains("sel") || buscar(h.getString("sel")) == null) {
                h.putString("sel", id);
            }
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
            PoliciaMod.msg(p, "Desbloqueaste " + d.nombre + " (tecla Z para usarla)");
            PoliciaNivel.logro(p, "habilidad_nueva");
            PoliciaRPG.sync(p);
        } else if (a == 1) {
            if (tiene(h, id)) {
                h.putString("sel", id);
                PoliciaRPG.sync(p);
            }
        } else if (a == 3) {
            List<Def> l = deOficio(oficio);
            int cur = -1;
            for (int i = 0; i < l.size(); i++) {
                if (l.get(i).id.equals(h.getString("sel"))) {
                    cur = i;
                }
            }
            for (int k = 1; k <= l.size(); k++) {
                Def d = l.get((cur + k + l.size()) % l.size());
                if (tiene(h, d.id)) {
                    h.putString("sel", d.id);
                    p.displayClientMessage(net.minecraft.network.chat.Component.literal("§6Habilidad Z: §f" + d.nombre), true);
                    PoliciaRPG.sync(p);
                    return;
                }
            }
        } else if (a == 2) {
            usar(p, h.getString("sel"));
        }
    }

    static void usar(ServerPlayer p, String id) {
        Def d = buscar(id);
        CompoundTag h = datos(p);
        if (d == null || !tiene(h, id)) {
            PoliciaMod.msg(p, "No tenés ninguna habilidad nueva equipada: abrí la ficha (I) → Habilidades");
            return;
        }
        String oficio = PoliciaMod.job(p);
        if (!d.oficio.equals(oficio)) {
            PoliciaMod.msg(p, d.nombre + " es de otro oficio");
            return;
        }
        if (necesitaModo(oficio) && !p.getPersistentData().getBoolean("pol_on")) {
            PoliciaMod.msg(p, "Activá el modo de tu oficio (B) primero");
            return;
        }
        long ahora = p.level().getGameTime();
        long hasta = h.getCompound("cd").getLong(id);
        if (hasta > ahora) {
            PoliciaMod.msg(p, d.nombre + " en enfriamiento: " + (hasta - ahora + 19) / 20 + " s");
            return;
        }
        boolean ok = ejecutar(p, d);
        if (ok) {
            CompoundTag cd = h.getCompound("cd");
            cd.putLong(id, ahora + d.cd);
            h.put("cd", cd);
            PoliciaRPG.sync(p);
        }
    }

    static boolean necesitaModo(String oficio) {
        return oficio.equals("policia") || oficio.equals("albanil") || oficio.equals("ladron") || oficio.equals("herrero");
    }

    // Corre la habilidad. Devuelve true si se uso (empieza el enfriamiento).
    // La animacion la juega cada habilidad cuando de verdad se usa.
    static boolean ejecutar(ServerPlayer p, Def d) {
        boolean ok = switch (d.oficio) {
            case "policia", "albanil" -> PoliciaHabObra.usar(p, d.id);
            default -> PoliciaHabExtra.usar(p, d.id);
        };
        if (ok && d.anim != null) {
            PoliciaAnim.play(p, d.anim);
        }
        return ok;
    }

    // ---------------- Utilidades ----------------
    static BlockHitResult bloque(ServerPlayer p, double rango) {
        HitResult h = p.pick(rango, 1.0f, false);
        return h instanceof BlockHitResult b && h.getType() == HitResult.Type.BLOCK ? b : null;
    }

    static LivingEntity mirando(ServerPlayer p, double rango) {
        Vec3 ojo = p.getEyePosition();
        Vec3 fin = ojo.add(p.getLookAngle().scale(rango));
        HitResult bh = p.pick(rango, 1.0f, false);
        double max = bh.getType() == HitResult.Type.MISS ? rango * rango : bh.getLocation().distanceToSqr(ojo);
        AABB caja = p.getBoundingBox().expandTowards(p.getLookAngle().scale(rango)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, ojo, fin, caja, e -> e instanceof LivingEntity && e != p && e.isAlive() && !e.isSpectator(), max);
        return eh != null && eh.getEntity() instanceof LivingEntity le ? le : null;
    }

    static boolean aliado(ServerPlayer p, Entity e) {
        return e == p || e instanceof ServerPlayer || (e instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isOwnedBy(p));
    }

    // ---------------- Tareas por tick ----------------
    // paso.test(t) se llama con t = 0, 1, 2... cada tick; devuelve true al terminar.
    static final List<IntPredicate> NUEVAS = new ArrayList<IntPredicate>();
    static final List<Object[]> TAREAS = new ArrayList<Object[]>();

    static void tarea(IntPredicate paso) {
        NUEVAS.add(paso);
    }

    public static class Eventos {
        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            for (IntPredicate n : NUEVAS) {
                TAREAS.add(new Object[]{n, new int[]{0}});
            }
            NUEVAS.clear();
            Iterator<Object[]> it = TAREAS.iterator();
            while (it.hasNext()) {
                Object[] t = it.next();
                int[] c = (int[]) t[1];
                boolean fin;
                try {
                    fin = ((IntPredicate) t[0]).test(c[0]++);
                } catch (Throwable ex) {
                    fin = true;
                }
                if (fin || c[0] > 20 * 60 * 10) {
                    it.remove();
                }
            }
        }

        // /oficio: si no tenes oficio (desempleado o renunciaste), vuelve a
        // abrir la ventana para elegir uno (incluye Medico y Mercader).
        @SubscribeEvent
        public void comandos(net.minecraftforge.event.RegisterCommandsEvent e) {
            e.getDispatcher().register(net.minecraft.commands.Commands.literal("oficio").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                if (!PoliciaMod.job(p).isEmpty()) {
                    PoliciaMod.msg(p, "Ya tenés oficio (" + PoliciaFicha.oficio(PoliciaMod.job(p)) + "): renunciá con tu jefe para elegir otro");
                    return 0;
                }
                p.getPersistentData().putBoolean("pol_chosen", false);
                PoliciaOficio.login(p);
                return 1;
            }));
        }

        @SubscribeEvent
        public void parar(ServerStoppingEvent e) {
            TAREAS.clear();
            NUEVAS.clear();
        }
    }
}
