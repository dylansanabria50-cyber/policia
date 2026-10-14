package com.example.policia;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

// Libro escrito que se entrega al asignarse un oficio, con las habilidades
// y teclas de ese oficio.
public class PoliciaBook {
    static final String[] ALBANIL = new String[]{"OFICIO ALBANIL\n\nB: activar o desactivar el modo albanil.\nJ: usar la habilidad elegida.\nK: cambiar de habilidad.\n\nLas habilidades se desbloquean en el arbol de habilidades (clic en el icono) y cuestan XP.", "PASIVAS\n(con el modo albanil activo, se tienen al tener el oficio)\n\nManos de obra: picas piedra, ladrillos, tierra, arena y grava un 20% mas rapido.\n\nAhorro de materiales: 10% de no gastar el bloque que colocas.", "Andamio seguro: sin danio por caidas de hasta 6 bloques.\n\nOjo de minero: ves todas las menas a 3 bloques, aunque no tengas ayudantes.", "1. PICO (J)\n\nTe da un pico de diamante durante 7 s con Fuerza I, Prisa minera I y Fortuna II.\nNo se puede soltar ni guardar en cofres; se puede encantar y conserva los encantamientos.\nEnfriamiento: 8 s.", "2. MURO (J)\n\nPared de 3 x 3 frente a ti. Hasta 3 muros seguidos (J de nuevo). Duran 15 s. Enfriamiento: 5 s.\n\nMejora a REFUGIO: casa con puerta, cama y cofre durante 35 s. Enfriamiento: 40 s.", "3. AYUDANTE (J)\n\nNivel I: 1 ayudante con pico de piedra. Nivel II: 3 con hierro. Nivel III: 4 con diamante.\nDuran 60 s. Enfriamiento: 30 s.\nMientras estan activos ves menas cercanas (radio 4).", "Teclas de los ayudantes:\n\nH: cambiar la orden (seguir, picar delante de mi, buscar minerales).\nX (mantener): elegir el area; rueda = largo, Mayus + rueda = ancho.\nY / N: aceptar o rechazar su aviso de mineral.\nU: ver lo que llevan.", "4. LINTERNA (J o G)\n\nUna luz que sigue tu mirada hasta 48 bloques durante 60 s.\nEnfriamiento: 20 s.\n\nCosto de desbloqueo: 10 XP (AYUDANTE II 15 XP, AYUDANTE III 20 XP).", "5. TRACTOR (J)\n\nPala cargadora amarilla con cabina. Dura 45 s. Enfriamiento: 35 s. Desbloqueo: 15 XP.\nJ: invocar / bajar / volver a subir.\nMayus + J: retirarlo.\nClic derecho: montar.", "Conduciendo el tractor:\n\nW / S: avanzar y retroceder.\nA / D: girar.\nESPACIO: sube la pala. CTRL: la baja.\nG: luces.\nU: su inventario.\nMayus: bajarte.\n\nRompe lo natural que tiene delante, pero nunca construcciones de jugadores.", "PLANO DE ALBANIL\n(lo vende el Capataz)\n\nClic derecho en dos esquinas opuestas de tu construccion: la copia (max. 32 por lado, 2048 bloques).\nClic derecho en el suelo: tus ayudantes la construyen ahi, mirando hacia donde mires.", "Usan los bloques de tu inventario y de la bolsa de los ayudantes. Si falta algo, esperan.\nMas ayudantes = mas rapido.\n\nMayus + clic al aire: cancelar la obra o borrar el plano."};
    static final String[] POLICIA = new String[]{"OFICIO POLICIA\n\nB: activar o desactivar el modo policia.\nJ: usar la habilidad elegida.\nK: cambiar de habilidad.\n\nLas habilidades se desbloquean en el arbol de habilidades (clic en el icono) y cuestan XP.", "PASIVAS\n(con el modo policia activo, se tienen al tener el oficio)\n\nChaleco reglamentario: -15% de danio de flechas y proyectiles.", "Autoridad: Heroe de la aldea I (mejores precios) y los saqueadores que te ven se asustan un momento.\n\nRecompensa: +2 XP de oficio por cada monstruo que matas cerca de una aldea.", "1. ESCUDO BALISTICO (J)\n\nTe protege durante unos segundos y luego entra en enfriamiento.\n\n2. REFUERZO (J)\n\nLlama a policias que luchan a tu lado. Tecla R: cambia la orden (proteger / atacar a quien te ataco / dividirse y atacar). Se mejora hasta el nivel 4 con XP.", "3. TANQUE (J)\n\nApunta y haz clic derecho para disparar un proyectil explosivo.\n\n4. TANQUE MOVIL\n\nPilotable: W/A/S/D para moverte, clic derecho para disparar (3 disparos). Mayus: bajarte. J: volver a subir.", "5. HELICOPTERO (J)\n\nW / S: avanzar. A / D: desplazarte.\nESPACIO: subir. CTRL: bajar.\nG: modo linterna.\nMayus: salir.", "6. ESPOSAS\n7. PERRO K9\n8. SIRENA Y TORRETA\n9. DRON DE VIGILANCIA\n\nElige la habilidad con K y usala con J. Cada una se desbloquea en el arbol de habilidades.", "COMISARIO Y MISIONES\n\nEn cada aldea hay un comisario junto a la campana. Clic derecho: tablero con 3 misiones (se renuevan cada dia). Solo atiende a policias.\n\nMisiones: atrapar fugitivo, patrulla de 3 puntos, rescate, limpiar campamento, evidencia, escolta y bombas. Cada 3 cumplidas se desbloquea cazar al jefe de banda.", "ENEMIGOS\n\nLadron, evadido y contrabandista huyen: esposalos. Pandilleros con garrote y arco, lideres, jefe de banda con barra de vida y francotiradores de torre (usa el escudo balistico). Saboteadores dejan bombas: rompe el bloque de TNT para desactivarlo. Recompensas: XP, esmeraldas y objetos especiales.", "MISION ESPECIAL\n\nTras cazar al primer jefe de banda, el tablero ofrece El cerebro de la organizacion: dos pistas y una guarida con el jefe final, que se teletransporta e invoca refuerzos.\n\nPremio: ESCUDO DE BURBUJA (J). Esfera azul de 14 bloques que te sigue 23 s: todo lo de adentro va muy lento menos tu. Enfriamiento 26 s."};
    static final String[] LADRON = new String[]{"OFICIO LADRON\n\nTe transformas con la tecla B (modo ladron). Se elige en la ventana de oficios al entrar al mundo por primera vez.\n\nTodo se hace con el modo activo.", "PASIVAS\n(con el modo ladron activo, se tienen al tener el oficio)\n\nPies livianos: agachado, los monstruos te detectan a un cuarto de la distancia.\n\nBolsillos profundos: al morir conservas 3 objetos al azar.", "Mano rapida: con cada carterismo exitoso, 30% de llevarte una esmeralda extra.\n\nHuida: si quedas con 3 corazones o menos, corres mas rapido 5 s (una vez cada 60 s).", "1. CARTERISMO\n\nAgachate (Mayus) detras de un jugador o mob y haz clic derecho. Si tienes suerte se abren sus bolsillos (lo que llevaria al morir) y puedes sacar cosas.\nProbabilidad base: 30%. Si fallas te descubren.", "2. SUERTE (trebol)\n\nSube la probabilidad del carterismo: nivel 1 = 40%, 2 = 45%, 3 = 50%, 4 = 55%. Cada nivel reemplaza al anterior. Se mejora con XP en la rama de habilidades.", "3. SILBIDO (J)\n\nLos mobs a 12 bloques miran para otro lado 7 s y quedan lentos: asi te acercas por la espalda. Enfriamiento: 15 s. Desbloqueo: 10 XP.", "GOLPES GRANDES\n(menu de misiones del Jefe de ladrones)\n\nAsalto a la boveda: aparece una boveda llena en el 2.o sotano del casino. Al abrirla suena la alarma y llegan agentes: escapa a 120 bloques en 3 min.", "Golpe al Perista: robale los bolsillos (por detras). Da la alarma: escapa a 100 bloques en 2:30.\n\nSi escapas cobras XP y esmeraldas extra. Si moris o se acaba el tiempo, fallas. Un golpe por dia."};
    static final String[] HERRERO = new String[]{"OFICIO HERRERO LEGENDARIO\n\nB: activar o desactivar el modo herrero.\nJ: usar la habilidad elegida.\nK: cambiar de habilidad.\nO: abrir el Analizador del Forjador.\n\nLas habilidades se desbloquean solas a medida que ganas XP: no hace falta arbol ni costo para elegirlas, solo alcanzar el XP necesario.", "SERVICIO Y JEFE\n\nServicio: otros jugadores se agachan y te hacen clic derecho con un objeto en la mano para pagarte esmeraldas por repararlo o mejorarlo.\n\nTu jefe es el Maestro herrero, en las herrerias: vende materiales de forja y ahi podes renunciar.", "PASIVAS\n(con el modo herrero activo, se tienen al tener el oficio)\n\nPiel de hierro: +2 de armadura y -30% de danio por fuego.\n\nManos de forja: los hornos y altos hornos a 6 bloques funden al doble de rapido.", "Durabilidad: tus herramientas y armadura se gastan un 25% menos.\n\nOjo de herrero: las menas de hierro y oro a veces sueltan pepitas o un lingote extra.", "1. GRITO DEL FORJADOR (J)\n\nGrito de area: una onda sonora sale de vos hasta 6 bloques; los enemigos dentro sueltan lo que tienen en la mano y retroceden asustados, debilitados unos segundos. Disponible desde el principio. Enfriamiento: 25 s.", "2. YUNQUE PORTATIL (J)\n\nSacas un yunque de la espalda, lo alzas con una mano y lo plantas en el piso. Mientras estes cerca (8 bloques): Fuerza y Heroe de la aldea. Los monstruos cerca del yunque quedan con Lentitud I y, si los matas, sueltan el doble de experiencia (y ganas el doble de XP de oficio). Al terminar los 40 s el yunque desaparece. Desbloqueo: 15 XP. Enfriamiento: 45 s (empieza al terminar).", "3. MARCA DEL HERRERO (J)\n\nMarca al enemigo que mires (brilla y queda visible) y reduce su armadura durante 15 s, haciendo que reciba mas danio de cualquiera. Desbloqueo: 25 XP. Enfriamiento: 20 s.", "4. FORJA INTERIOR (J)\n\nFunde 1 lingote de hierro de tu inventario y te cura 2 corazones al instante. Sin lingote no se activa. Desbloqueo: 35 XP. Enfriamiento: 8 s.", "5. FARO DE FORJA (J)\n\nEnciende una fogata de forja: cura y da inmunidad al fuego a quien este cerca durante 60 s. Desbloqueo: 45 XP. Enfriamiento: 50 s.", "6. VINCULO DE ACERO (J)\n\nTe vinculas con el jugador mas cercano por 30 s: una cadena translucida los conecta, y si uno recibe danio el otro se cura una parte. Desbloqueo: 55 XP. Enfriamiento: 60 s (empieza al terminar).", "7. REPARACION MAESTRA (J)\n\nSacas el yunque de la espalda, lo plantas y golpeas con el martillo como forjando: repara por completo cualquier objeto gastado que tengas en la mano (herramientas, armas, armadura, escudo, arco, elitros...), le quita el costo acumulado de yunque y deja como nuevo un yunque astillado. No gasta XP. Desbloqueo: 70 XP. Enfriamiento: 60 s (empieza al terminar).", "8. YUNQUE GUARDIAN (J)\n\nPlantas un yunque que intercepta flechas y proyectiles cercanos, destruyendolos con chispas y un golpe de escudo. Al terminar los 40 s el yunque desaparece. Desbloqueo: 85 XP. Enfriamiento: 45 s.", "9. ANALIZADOR DEL FORJADOR (pasiva, tecla O)\n\nSiempre activa al llegar a 35 XP. Sostene cualquier item 2 s en la mano secundaria para aprender su receta (aviso dorado en el centro de pantalla). Con O abris una ventana con todos los items de cualquier mod: elegi uno y abajo ves su receta y el boton Craftear. Los que no tenes a mano se aprenden con el boton Pedir mision (21 misiones distintas: minar, cazar, talar, pescar, comerciar, fundir, explorar alturas y mas; en verde mientras esta en curso). Las recetas son aleatorias: Otra receta la cambia y Confirmar la deja fija para siempre."};

    static void pages(ItemStack b, String[] pg) {
        ListTag pages = new ListTag();
        for (String s : pg) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(s))));
        }
        b.getOrCreateTag().put("pages", (Tag) pages);
    }

    // Actualiza en silencio el libro del oficio que el jugador ya tiene.
    static void refresh(ServerPlayer p) {
        String job = PoliciaMod.job(p);
        String[] pg = "albanil".equals(job) ? ALBANIL : ("policia".equals(job) ? POLICIA : ("ladron".equals(job) ? LADRON : ("herrero".equals(job) ? HERRERO : null)));
        if (pg == null) {
            return;
        }
        for (int i = 0; i < p.getInventory().getContainerSize(); ++i) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.hasTag() && job.equals(s.getTag().getString("pol_book"))) {
                PoliciaBook.pages(s, pg);
            }
        }
    }

    static void give(ServerPlayer p, String job) {
        String[] pg = "albanil".equals(job) ? ALBANIL : ("policia".equals(job) ? POLICIA : ("ladron".equals(job) ? LADRON : ("herrero".equals(job) ? HERRERO : null)));
        if (pg == null) {
            return;
        }
        for (int i = 0; i < p.getInventory().getContainerSize(); ++i) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.hasTag() || !job.equals(s.getTag().getString("pol_book"))) continue;
            PoliciaBook.pages(s, pg);
            return;
        }
        ItemStack b = new ItemStack((ItemLike) Items.WRITTEN_BOOK);
        CompoundTag t = b.getOrCreateTag();
        t.putString("title", "albanil".equals(job) ? "Guia del Albanil" : ("ladron".equals(job) ? "Guia del Ladron" : ("herrero".equals(job) ? "Guia del Herrero Legendario" : "Guia del Policia")));
        t.putString("author", "Oficios");
        t.putString("pol_book", job);
        PoliciaBook.pages(b, pg);
        if (!p.getInventory().add(b)) {
            p.drop(b, false);
        }
        PoliciaMod.msg(p, "Recibiste un libro con las habilidades y teclas de tu oficio");
    }
}
