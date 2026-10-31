# Plan de implementación en Minecraft (Forge 1.20.1)

Cómo se pasaría al mod todo lo aprobado en `docs/PENDIENTES.md`: animaciones, movimientos, primera persona, habilidades nuevas y las tandas RPG 1 a 5.
Está escrito para el estado actual del repo: Forge 47.3.0, sin GeckoLib ni mixins, layout plano en `com.example.policia`, próximo ID de mensaje de red **16**.

---

## 0. Reglas generales (valen para todo)

| Tema | Cómo se hace |
|---|---|
| **Datos del jugador** (nivel, XP, atributos, inventario RPG, árbol, diario, logros, caballo, reputación) | Una sola *capability* `PoliciaRPG` (NBT) en el jugador. Se copia en `onClone` (sobrevive a la muerte) y se sincroniza con un paquete `RpgSync` solo cuando algo cambia. |
| **Datos del mundo** (tablero, subastas, gremios, alcalde y leyes, clima, eventos, casas) | `SavedData` por dimensión: `PoliciaMundo` (`level.getDataStorage()`). Se guarda solo con el mundo. |
| **Red** | Mensajes nuevos desde el ID 16, en un `SimpleChannel` aparte (`PoliciaRed`) para no tocar los 0–15 que ya existen. El servidor siempre valida (costos, distancia, cooldowns); el cliente solo pide. |
| **Pantallas** | `Screen` propias con `GuiGraphics`: madera y papel como texturas de 9 cortes, texto con la fuente del juego y animaciones por *partialTick*. Lo que en las vistas previas es `<canvas>` se dibuja con `blit` + `fill`. |
| **HUD** | `RegisterGuiOverlaysEvent`: barra del jefe, misiones activas, barra de XP RPG, avisos de logro, reloj de día y noche. |
| **Animaciones del jugador** | Igual que `PoliciaHerreroAnim`: un `PlayerModel` propio que solo cambia ángulos, más `RenderLayer` para los objetos (yunque, rueda, espadas). El estado se manda a todos los que miran con el paquete `AnimStart(entityId, kind, tick)` y cada cliente reproduce la misma curva. Así lo ven **todos los jugadores** y no se reemplaza la skin. |
| **Física de las animaciones** | Se porta tal cual el motor de la vista previa: paso fijo de 0,25 tick, resortes por articulación, *squash*, rebotes, partículas con gravedad. Es código cliente puro (una clase `PoliciaFisica`), sin nada en el servidor. |
| **Sonidos** | Minecraft no sintetiza audio. Se **graban** los sonidos de la vista previa (WebAudio en modo *offline* → `.ogg`), se ponen en `assets/policia/sounds/` y se registran en `sounds.json`. Los loops (motor, rotor, lluvia) usan `AbstractTickableSoundInstance`. El sonido de la rueda se recrea igual que en la vista previa, sin usar el audio original del anime. |
| **Partículas** | Tipos propios: chispa, brasa, viruta de piedra, humo, pétalo, gota, confeti y luz. Se registran con `ParticleType` + `ParticleProvider`. |
| **Mundo 3D** (estructuras, tablero, urnas, puestos) | Bloques y *block entities* con `BlockEntityRenderer`. Estructuras nuevas en `PoliciaStruct`, con la separación que ya existe. |
| **NPCs** | Siguen el patrón de `PoliciaJefe`/`PoliciaAyudante`: `PathfinderMob` con *goals* propios y el diálogo por `PoliciaDialogo`. |
| **Idioma** | Todo en `es_ar` con voseo; también se dejan las claves en `en_us` para que no aparezcan nombres internos. |
| **Rendimiento** | Partículas con tope por cliente, BER con `shouldRenderOffScreen=false`, nada pesado en `tick()` del servidor (los temporizadores van por tick del mundo, no por jugador). |

---

## 1. Animaciones de los oficios (39 existentes + 20 nuevas)

- **`PoliciaAnimDB`**: cada animación tiene `id`, `len` en ticks y una función `pose(t, pose)` igual a la de la vista previa (los ángulos de `head`, `rArm`, `lArm`, `rLeg`, `lLeg` y `body.y`/`body.z`, más la raíz `rx`/`rz`).
- **`PoliciaAnimModel extends PlayerModel`**: en `setupAnim` aplica la pose con los resortes (`KEYS/PRM` de la vista previa) en lugar de asignar los ángulos directo, para que tengan inercia.
- **`PoliciaAnimLayer`**: dibuja los objetos de cada habilidad: yunque, martillo, rueda de adaptación, placas, espadas, reloj, grúa, etc. Son modelos `.json` de ítem o `ModelPart` armados en código.
- **Efectos de cámara** (solo para quien lanza): `ViewportEvent.ComputeCameraAngles` para el temblor; `ComputeFov` para el zoom; un overlay para el *flash*, la vista CCTV y el tinte de rebobinado.
- **El tractor**: `PoliciaTractor` ya existe; se reemplaza su modelo por el de la vista previa (misma forma y misma animación de ruedas).

### Habilidades grandes (necesitan servidor)

| Habilidad | Servidor | Cliente |
|---|---|---|
| **Mil espadas** | 24 entidades `EspadaVoladora` (proyectil sin gravedad que hace daño en área) | Portales como partículas, espada gigante como BER, cámara cinematográfica opcional |
| **Rueda de adaptación** (dura 1 min, se recarga en 2,5 min) | `MobEffect` "Adaptado": guarda en la capability qué tipo de daño recibiste; cada vez que la rueda gira, ese daño baja un 25 % (hasta 100 %) | Rueda sobre la cabeza (capa), giro con trinquete, sonido propio |
| **Dominio de la ley** | Cúpula de bloques temporales de vidrio (se restauran solos al terminar), NPCs patrulleros y la regla "nadie sale" | Interior con patrulleros y luces; el vidrio se rompe al final |
| **Escudo de mil placas** | Atributo de armadura temporal + bloquea proyectiles (`ProjectileImpactEvent`) | 40 placas orbitando (capa) |
| **Forja del tiempo** | Restaura un ítem roto a la durabilidad que tenía hace 30 s (historial guardado en la capability) | Reloj, piezas que vuelven a su lugar, tinte sepia |
| **Puño de tierra / Torre instantánea / Taladro de túnel / Ascensor / Grúa** | Colocación y rotura de bloques escalonadas por tick, respetando las protecciones (casas, gremios) | Polvo y bloques animados como `FallingBlockEntity` visual |

---

## 2. Movimiento y primera persona (para todos los jugadores, solo visual)

- **10 movimientos**: quieto, caminar, correr, arrancar y frenar, saltar, caída, agachado, escalera, nadar y recibir golpe.
  - Se calculan en `PoliciaAnimModel.setupAnim` a partir de la velocidad real del jugador: inclinación al acelerar, giro del torso y la cabeza que se adelanta en las curvas.
  - No hace falta red: cada cliente ya conoce la posición y la velocidad de los demás.
  - Opción en la configuración para apagarlo.
- **Primera persona**:
  - `RenderHandEvent` para dibujar el brazo propio con resorte: balanceo suave, retraso al girar y hundida al aterrizar.
  - Al correr, el brazo se va hacia atrás estilo anime.
  - `ComputeFov` abre el campo de visión al correr; un overlay pone las líneas de velocidad.
  - `ComputeCameraAngles` inclina un poco la cámara en las curvas y la sacude al recibir daño.

---

## 3. RPG — tanda 1: base

| Vista previa | En el juego |
|---|---|
| **Tablero de madera del pueblo** | Bloque `TableroBlock` (se extiende el `PoliciaBoard` actual). El BER dibuja los papeles clavados. Clic derecho abre la pantalla de pantalla completa con 8 papeles (imagen del ítem o del mob con `renderItem`/`InventoryScreen.renderEntityInInventory`). Las misiones salen de `PoliciaMundo` y se renuevan cada día de juego. |
| **Diálogo con decisiones** | `PoliciaDialogo` con árbol de nodos en JSON (`data/policia/dialogos/*.json`). Cada opción modifica la reputación (Policía, Ladrones, Pueblo) en la capability y puede abrir o cerrar misiones. |
| **Evento del mundo** | `PoliciaEventos`: anuncio con `bossbar` + título, objetivos por bando y tabla de puntos en el HUD. |
| **Ficha de personaje** | Tecla `K`. Atributos que se aplican con `AttributeModifier` (vida, daño, velocidad) y con valores propios (crítico, calidad de forja, enfriamientos). |

## 4. RPG — tanda 2

| Vista previa | En el juego |
|---|---|
| **Mapa de viaje en carruaje** | Ítem/bloque "Parada de carruaje" en cada pueblo; se desbloquea al pisarla por primera vez. La pantalla del mapa dibuja la comarca (fondo pintado + pueblos reales con sus coordenadas). Al viajar: cobra, pantalla de viaje con el carruaje y teletransporte (`teleportTo`), con 3 s de espera que se cancelan si te pegan. |
| **Mazmorra Cueva del Eco** | Estructura nueva (`PoliciaStruct`) con salas. Jefe `GuardianEco` = `PoliciaBoss` con fases: 50 % → furia (más velocidad y ataque de salto con onda en el suelo que golpea en un radio). Barra con "fantasma" en el overlay. Botín en un cofre con *loot table*. |
| **Subasta y mercado** | Atril en la plaza (block entity). La subasta vive en `PoliciaMundo`: tiempo, pujas y +10 s si alguien puja al final. Al cerrar, el objeto va al buzón del ganador. Los precios del mercado los calcula `PoliciaMercado` con la oferta y la demanda reales; el diario (`PoliciaDiario`) publica los titulares. |
| **Misión en grupo: golpe al banco** | Se extiende `PoliciaGolpe` + `PoliciaGrupo`: roles por oficio, objetivos por rol (túnel, bóveda, puerta) y la policía como bando rival real. El progreso de cada uno va en el HUD de grupo que ya existe. |
| **Casa propia** | Cartel "Se vende" (bloque) en lotes generados en los pueblos. Al comprar se registra la zona en `PoliciaMundo`: `BlockEvent.BreakEvent`/`EntityPlaceEvent` se cancelan para los que no son dueños. Cama, cofre seguro (la policía no lo puede revisar) y buzón. Mejoras (huerta, farol, establo) colocan estructuras chicas. |
| **Día y noche** | Se lee el `dayTime` real. Los efectos (sigilo del ladrón, mercado negro, patrullas, precios) se aplican al cambiar de franja, con un aviso. |

## 5. RPG — tanda 3: interfaz del aventurero

| Vista previa | En el juego |
|---|---|
| **Inventario y equipo** | Se usa el inventario de Minecraft (no se inventa otro): se agregan la **rareza** (NBT `rareza` + color del nombre + brillo en el borde con `ItemDecorator`), estadísticas en el *tooltip* (`ItemTooltipEvent`) y el **anillo** como casillero extra (pestaña en el inventario con un `Slot` propio guardado en la capability). El muñeco con equipo es el del inventario normal. |
| **Árbol de habilidades por oficio** | Ya existe `PoliciaTreeScreen` / el árbol del herrero (`COST`/`PRE`). Se unifica en un solo `PoliciaArbol` con nodos en JSON por oficio, rangos 1/3, habilidad suprema y "devolver puntos". La pantalla copia el estilo de la vista previa: líneas que se encienden y anillos de rango. |
| **Diario de misiones** | Ítem "Diario" (tecla `J`) que abre una pantalla de libro con pestañas (Activas, Cumplidas, Historia). Usa los datos de `PoliciaMision`/`PoliciaContratos`. La página que da vuelta se anima con la escala de la textura. El croquis se dibuja con líneas. |
| **Minijuego de forja** | Se integra en `PoliciaForja`: clic derecho en el yunque con un lingote abre la pantalla. Calentar (mantener) → martillar 5 veces (barra de tiempo) → templar en un caldero con agua cerca. La calidad sale del servidor (el cliente manda el *timing* y el servidor lo valida con margen) y da la rareza/estrellas en el NBT de la espada. |
| **Nivel y logros** | XP RPG propia (separada de la XP de encantar), en la capability. Al subir de nivel: overlay con rayos, confeti de partículas, fanfarria y puntos. Los logros usan **advancements** propios (`data/policia/advancements/`), así aparecen en la pantalla de logros del juego con el aviso nativo, más un aviso estilo vista previa. |

## 6. RPG — tanda 4: vida en la comarca

| Vista previa | En el juego |
|---|---|
| **Comercio entre jugadores** | Clic derecho con mayúscula sobre otro jugador → pedido de comercio. Es un `AbstractContainerMenu` compartido con 2 × 8 casilleros + pesos. **Antiestafa**: cualquier cambio pone los dos "Listo" en falso (validado en el servidor). Al confirmar, la cuenta 3-2-1 corre en el servidor y el intercambio es atómico (si a alguno no le entra, se cancela). |
| **Gremio y estandarte** | Comando/pantalla para fundar (cuesta pesos). El estandarte es un **banner de Minecraft** con patrones propios (`BannerPattern` registrados con los símbolos de la vista previa), así ondea solo. Lista de miembros, rangos, cofre del gremio y chat en `PoliciaMundo`. |
| **Juicio en el tribunal** | Ya existe `PoliciaJuicio`. Se le agrega la sala (estructura), el jurado de 6 NPCs y la jueza. Las pruebas son ítems reales que se juntan durante la investigación (huellas de `PoliciaRastreo`, foto de la cámara, botín). El medidor y el veredicto se calculan en el servidor; la condena usa la celda/multa que ya hay. |
| **Pesca** | Evento `ItemFishedEvent`: cuando pica, en lugar del premio directo se abre una pantalla chica con el minijuego de recoger (la zona sigue al pez). Peces propios (ítems) por rareza, con la condición de noche. La colección y los récords en cm van en la capability. |
| **Clima y estaciones** | Las **estaciones** van en `PoliciaMundo` (cambian cada N días de juego). El cliente tiñe pasto y hojas con `RegisterColorHandlersEvent`: primavera más clara, otoño naranja, invierno blanco, más partículas de pétalos u hojas. El **clima** usa la lluvia y la tormenta de Minecraft; la **niebla** se hace con `ViewportEvent.RenderFog`; la **nieve en invierno** aplica el bioma frío visual y capas de nieve en los techos cerca de los pueblos. Efectos de juego: cultivos, forja, huellas y sigilo. |

## 7. RPG — tanda 5: oficios en acción

| Vista previa | En el juego |
|---|---|
| **Establo y caballo** | Se usa el **caballo de Minecraft** (`Horse`) más datos propios en su NBT: comida, ánimo, limpieza y vínculo. Zanahoria = sube comida y vínculo (corazones); el ítem "Cepillo" saca el barro (capa de textura sucia por encima). El vínculo da velocidad y salto (`AttributeModifier`). **Carrera**: circuito con vallas (estructura) y checkpoints; Martina es un NPC jinete con un *goal* que sigue los puntos. |
| **Granja** | Se usan los cultivos de Minecraft para trigo, zanahoria y calabaza. Se agrega la **flor de luna** (`CropBlock` propio que solo crece de noche y emite luz). Los cuervos son un mob chico que busca cultivos maduros sin espantapájaros cerca. El espantapájaros es un bloque. El regado usa la tierra húmeda normal (la regadera es un ítem que moja la tierra en un área). |
| **Persecución** | Es el juego real: el policía persigue al ladrón en el pueblo. Se suman la **barra de aliento** (overlay; correr la gasta), las **huellas** que deja el ladrón (partícula + rastro de `PoliciaRastreo`), la **bomba de humo con señuelo** (ítem del ladrón: nube de partículas + un `ArmorStand`/NPC copia que se deshace) y la **linterna** de noche (ítem que da luz dinámica alrededor y en un cono, con un oscurecimiento en el overlay). |
| **Mercado negro** | NPC "Encapuchado" que aparece de noche en un claro del bosque, en un lugar distinto cada noche (lo anuncian rumores en el diario). La pantalla de regateo usa la paciencia y el precio mínimo calculados en el servidor. El **riesgo de policía** sube con el tiempo: si llega a 100, avisa a los policías conectados (marca en el mapa) y el NPC desaparece. |
| **Elección de alcalde** | Cada 7 días de juego. Los candidatos son jugadores que se anotan (o NPCs si no hay nadie). Se vota en la urna de la municipalidad (bloque) y la campaña pone afiches que dan intención de voto. El conteo es un evento con overlay y fuegos artificiales reales (`FireworkRocketEntity`). Las **leyes** del ganador se aplican como reglas globales en `PoliciaMundo` (multas, precios, patrullas) durante 7 días. |

## 7b. RPG — tanda 6: lo que faltaba

| Vista previa | En el juego |
|---|---|
| **NPCs con «!» y «?»** | Encima del NPC se dibuja el marcador en 3D (`EntityRenderer`: billboard con textura + brillo), que gira y rebota. El estado sale de la capability del jugador que mira (cada jugador ve su propio marcador): se sincroniza la lista de NPCs con misión para ese jugador. Clic derecho → `PoliciaDialogo`. |
| **Misiones diarias por oficio** | Pool en JSON por oficio. Se sortean al cambiar el día de juego (o a las 00:00 reales, configurable). Progreso por eventos (bloques puestos, ladrones atrapados, espadas forjadas…). La racha y el cofre diario van en la capability. Se ven en el diario y en el HUD. |
| **Zonas con nivel** | Zonas como rectángulos o radios alrededor de cada estructura/bioma, guardadas en `PoliciaMundo`. Al cruzar el borde: título grande (`Gui.setTitle`) con el nivel. Si te supera por 5 o más: aviso rojo, viñeta, +50 % de daño (`LivingHurtEvent`) y carruaje bloqueado. Los mobs de cada zona escalan su vida y daño con el nivel. |
| **Ciudad central con gremios** | Estructura grande nueva en `PoliciaStruct` (una por mundo, cerca del spawn), con las 4 sedes y la fuente. Los maestros son NPCs (patrón `PoliciaJefe`) con pantalla de gremio: rango (en la capability), encargos y tienda con objetos bloqueados por rango. |
| **Tienda** | NPC tendero con `MerchantMenu` propio o una pantalla de carrito. Los precios salen de `PoliciaMercado` y se aplican los descuentos de las leyes del alcalde. Vender usa los mismos precios con margen. |
| **Banco** | Bloque mostrador con pantalla de cuenta. Los saldos (personal y de gremio) van en `PoliciaMundo`, así se pueden usar desde cualquier banco. El interés se cobra por día de juego; el préstamo tiene vencimiento, recargo y embargo. La bóveda es un BER cuya pila de oro crece con el saldo total. El **asalto** se integra con `PoliciaGolpe`: alarma, el seguro devuelve el 80 % y se publica un SE BUSCA en el tablero. |
| **Armero / Fundición de emergencia** (Herrero) | Armero: bloque perchero que guarda armadura (como un soporte de armadura) y la repara de a poco, con la animación del martillito encantado. Fundición: habilidad que convierte herramientas rotas del inventario en lingotes según su material, con la animación del crisol y la lingotera. |

---

## 8. Orden de implementación propuesto (por etapas, cada una compilable y probable)

1. **Base técnica**:
   - capability `PoliciaRPG` + `PoliciaMundo` + canal `PoliciaRed` (ID 16 en adelante);
   - sonidos grabados a `.ogg`;
   - partículas propias;
   - motor de física de animación.
2. **Movimientos + primera persona** (solo visual, para todos): no toca datos y se nota enseguida.
3. **Animaciones de los 39 oficios + tractor nuevo**.
4. **Nivel, atributos, ficha, logros (advancements)**: todo lo demás da XP.
5. **Tablero, diálogo con decisiones, diario de misiones, NPCs con «!»/«?», diarias por oficio, zonas con nivel**.
6. **Árbol de habilidades unificado + las 20 habilidades nuevas** (las grandes de a una).
7. **Forja (minijuego) + rarezas + inventario/anillo**.
8. **Mapa de viaje, casas, día y noche**.
9. **Mazmorra + jefe con fases**.
10. **Comercio, subasta/mercado, gremios, ciudad central, tienda y banco**.
11. **Golpe al banco en grupo, persecución, juicio con jurado, mercado negro**.
12. **Granja, establo y carrera, pesca**.
13. **Clima y estaciones, elección de alcalde**.

En cada etapa:
- se hace un commit por cambio, en español;
- se compila con GitHub Actions y se entrega el link del jar;
- se hace backup del jar anterior;
- se prueba en la instancia "PRUEBA 01" antes de pasar a la siguiente.

## 9. Riesgos y decisiones a confirmar

- **Tamaño**: es mucho contenido. Separarlo en módulos con interruptores en la configuración (`policia-common.toml`) permite apagar sistemas si algo falla.
- **Compatibilidad**: no usar mixins mantiene la compatibilidad. Las estaciones (tinte de pasto y hojas) pueden chocar con mods de *shaders*: se dejan desactivables.
- **Servidor vs un jugador**: todo se valida en el servidor, así funciona igual en LAN y en servidor.
- **Inspiración anime** (Rueda de adaptación, Dominio, Mil espadas): diseño, nombres y sonidos propios. No se copia arte ni audio original.
- **Contenido de otros mods**: no copiar código ni assets de mods con licencia ARR.
