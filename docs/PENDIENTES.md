# Pendientes para el mod OFICIOS

Ideas aprobadas por Dylan (30/10/2026). Vista previa de todas: artefacto "Animaciones de Oficios".
Todavía NO implementar: primero se siguen sumando ideas.

## Implementar al mod (cuando Dylan diga)
- Animaciones de las 39 habilidades existentes, como en la vista previa (ángulos de PlayerModel, inercia, partículas, temblor de cámara, pausa en golpes).
- Tractor: mismo modelo que la vista previa (cabina con vidrios, ruedas que giran, pala con brazos, carga de tierra visible, faros con G, humo y polvo). Mantener inventario y controles actuales.
- Sonidos: generar .ogg a partir de los sonidos sintetizados de la vista previa.

## Movimientos base del personaje (pestaña Movimiento de la vista previa)
- Aplicar a TODOS los jugadores (solo visual).
- Primera persona: brazo propio con balanceo al caminar, retraso al girar, hundida al aterrizar, leve inclinación al doblar, sacudida al recibir daño y FOV más amplio al correr.
- Quieto (respira, cambia el peso, mira), caminar y correr sin que patinen los pies, inclinación por aceleración y en curvas, arrancar/frenar con derrape, salto y caída con física de Minecraft, aterrizaje con amortiguación, caminar agachado con freno en el borde, escalera, nado y empuje al recibir golpes.

## Habilidades nuevas aprobadas
### Policía
- Dominio de la ley: expansión de dominio (cúpula oscura) con patrulleros en círculo, sirenas, reflectores y policías; los enemigos no pueden salir y los golpes del policía son certeros; al final la cúpula se rompe.
- Cámara de seguridad: cámara en una pared; con una tecla ves lo que ella ve (vista tipo CCTV con REC).
### Albañil
- Ascensor de obra: plataforma que sube y baja por un andamio, con cadenas, polea y contrapeso.
- Taladro de túnel: vehículo perforador (como el tractor: orugas, cabina, cabeza giratoria, cinta de escombros atrás) que abre un túnel de 3 × 3 de 10 bloques y deja antorchas.
- Puño de tierra: golpea el suelo, corre una grieta y sale una mano gigante de piedra que atrapa al mob, lo levanta y lo aplasta.
- Torre instantánea: una torre de piedra sube rapidísimo con el albañil arriba, con cámara girando.
- Grúa torre: levanta un bloque grande o un cofre y lo deja donde mires; el gancho se balancea como péndulo.
### Ladrón
- Señuelo: muñeco inflable con su forma que atrae mobs y policía 8 s, después revienta.
- Trampa de alarma: hilo casi invisible entre dos ganchos; si alguien lo cruza suena la alarma y el intruso brilla.
- Tirolesa: cuerda entre dos puntos altos para deslizarse rápido colgando de una mano (péndulo, frena al final).
- Paso sombra: desaparece en un remolino de hojas y reaparece a la espalda del enemigo.
- Bolsa sin fondo: 9 casillas secretas que no se pierden si la policía lo atrapa.
### Herrero
- Lluvia de metal: golpea el yunque y caen lingotes al rojo del cielo sobre un área marcada.
- Golem de hierro forjado: lo arma a martillazos (piernas, cuerpo, brazos, cabeza), pelea 40 s.
- Mil espadas: portales dorados detrás de él de los que salen espadas forjadas disparadas al enemigo.
- Rueda de adaptación: 3 martillazos forjan una rueda de hierro con runas (diseño propio) que flota sobre la cabeza; cada tipo de daño recibido la hace girar un paso y reduce ese daño (4 pasos = inmune). Dura 60 s, enfriamiento 150 s.
- Escudo de mil placas: decenas de placas de hierro orbitan alrededor y vuelan solas a bloquear flechas y golpes; al final forman un capullo y estallan.
- Forja del tiempo: repara un objeto rebobinando el tiempo; aparece un reloj que gira al revés, la imagen se tiñe y los pedazos vuelven por el mismo camino hasta unirse.
- Campana de bronce: campana mágica; cada martillazo manda una onda dorada que marea a los mobs y repara el equipo de los aliados.
- Armero (sin animar aún): perchero que guarda y repara solo la armadura que dejes ahí.
- Fundición de emergencia (sin animar aún): derrite herramientas rotas y las devuelve como lingotes.
- Horno portátil: horno a su lado que funde minerales al instante mientras dure.

## Modo RPG (aprobado: 1, 4 y 6)
- Niveles y atributos: nivel 1–50 con barra de XP; puntos en Fuerza, Vitalidad, Agilidad, Destreza e Ingenio; atributo principal por oficio (Policía Vitalidad, Albañil Fuerza, Ladrón Agilidad, Herrero Destreza).
- Misiones e historia: NPC con ! y ? que dan misiones con diálogo y elecciones; diarias por oficio; historia principal por capítulos; jefes con barra grande y fases.
- Mundo: zonas con nivel recomendado; ciudad central con gremios de cada oficio; tienda, banco y tablero de misiones.
- (aprobado) Cadenas de misiones por oficio, decisiones con consecuencias, misiones en grupo, eventos del mundo; mazmorras, día/noche con efectos, transporte y viaje rápido, casas propias, economía. Ver plan en la conversación del 30/10/2026; reutilizar PoliciaJefe, PoliciaBoard, PoliciaEventos, PoliciaDiario, PoliciaMercado, PoliciaContratos, PoliciaStruct, PoliciaCamp.
- Tablero del pueblo: tablero de madera viejo en cada aldea con papeles colgados; clic derecho abre pantalla completa; cada papel (SE BUSCA, RECOMPENSA, SE NECESITA, PEDIDO, AVISO, MAZMORRA, TRANSPORTE, SUBASTA) tiene imagen, texto, objetivos, recompensas y requisitos; Aceptar pone sello y lo suma a Misiones activas. Vista previa: artefacto "Pueblo de Oficios".
- Diálogo con decisiones (texto letra por letra, 3 opciones, finales distintos, barras de reputación Policía/Ladrones/Pueblo), cartel de evento del mundo con objetivos por oficio y tabla de puntos en vivo, ficha de personaje con reparto de puntos y estadísticas derivadas. Vista previa: "Pueblo de Oficios".

## Vistas previas RPG (tanda 2) — APROBADAS (todas, con el pulido)
Vista previa: "Pueblo de Oficios" v2.
- **Mapa de viaje en carruaje.** Mapa de pergamino con pueblos y caminos. Las paradas se desbloquean al visitar el pueblo. El pasaje se cobra según la distancia y el carruaje anima el viaje.
- **Mazmorra "Cueva del Eco".**
  - Minimapa de salas, grupo con barras de vida y barra del jefe con daño en "fantasma".
  - Fase 2 al 50 % ("enfurecido"); números de daño, críticos en amarillo y botín al ganar.
- **Subasta y mercado.**
  - Lote con cuenta regresiva e historial de pujas.
  - Gráfico del precio del hierro en 14 días y titular del diario que explica la suba.
- **Misión en grupo "El golpe al banco".** Los roles por oficio son Albañil, Ladrón, Herrero y Policía rival. Se arma con "Buscar grupo" y muestra el progreso por rol.
- **Casa propia.** Cartel SE VENDE. Incluye terreno protegido, cama, cofre seguro y buzón; se compra y se entrega la llave.
- **Día y noche.** Reloj con efectos por franja:
  - de noche: sigilo del ladrón y mercado negro;
  - de día: patrullas dobles y obras.

### Pulido de la tanda 2 (aprobada: "me gustaron todas")
Ajustes visuales y de animación incluidos en "Pueblo de Oficios" v3:
- **Mapa.**
  - Pergamino con bosques, montañas nevadas, río, mar con olas y un barco, y sombras de nubes.
  - Humo de chimeneas, bandera en el pueblo actual y ruta resaltada al pasar el cursor, con escalas y pasaje.
  - El carruaje tiene caballo, ruedas que giran, polvo y sonido de cascos, y el mapa se oscurece de noche.
- **Mazmorra.**
  - Pelea en tiempo real: el golem anticipa el golpe, salta y lanza una onda que daña al grupo.
  - Fase 2 con brasas y ojos rojos; la habilidad "Mil espadas" tiene enfriamiento.
  - Al morir se desarma en bloques, y aparece el cofre con rayos de luz.
- **Subasta.**
  - Monedas que vuelan, rivales que pujan solos y +10 s si alguien puja al final.
  - Sello VENDIDO, precios del mercado en vivo, gráfico que se dibuja con valores al pasar el cursor y noticias que se desplazan.
- **Golpe al banco.** Escena en corte animada:
  - el túnel se excava y la bóveda se abre;
  - el herrero hace chispas y el patrullero llega con sirena;
  - huida, con final de éxito o de captura.
- **Casa.**
  - Cartel que se balancea y sale volando al comprar, confeti, el dueño entra y la chimenea humea.
  - Ventanas encendidas de noche; mejoras de huerta, farol y establo.
- **Día y noche.**
  - Panorama del pueblo con sol y luna en arco, estrellas, nubes, molino, faroles y luciérnagas.
  - De día patrullas; de noche, un ladrón y un policía con farol.
  - Reloj de 24 h y ciclo rápido. La hora es compartida: el mapa, la casa y el golpe cambian con ella.
