# Contexto para continuar el mod "policia" en otra conversación

Pegá este archivo (o pedile a Claude que lea `docs/CONTEXTO.md` del repo) al empezar una conversación nueva.

## Proyecto
- Mod de **Minecraft Forge 1.20.1** (forge 47.3.0, mappings oficiales, Java 17).
- Repo: `dylansanabria50-cyber/policia`, rama `main`. Fuentes Java en la raíz (layout plano), paquete `com.example.policia`, modid `policia`. Recursos en `assets/policia/` y `data/policia/`.
- El usuario juega en la instancia de CurseForge **"PRUEBA 01"** (`C:\Users\DYLAN\curseforge\minecraft\Instances\PRUEBA 01\mods`).
- Idioma: todo en **español rioplatense** (voseo), con tildes y ñ.

## Cómo se compila y entrega
- Se compila con **GitHub Actions** (`.github/workflows/build.yml`) en cada push a `main`.
- El jar queda en: https://github.com/dylansanabria50-cyber/policia/releases/download/ultima/policia-1.0.0.jar
- El log del build se publica en el release `ultima-log` (asset `build-log.txt`).
- Para ver el estado: `https://api.github.com/repos/dylansanabria50-cyber/policia/actions/runs?head_sha=<sha>`.
- Al usuario se le da siempre el link del jar y un resumen corto en español de qué cambió.

## Reglas técnicas aprendidas
- En el juego real los nombres de Minecraft están ofuscados: la reflexión debe buscar campos/métodos por tipo o comportamiento, nunca por nombre.
- Algunos `SoundEvents` son `Holder` → usar `.value()` (NOTE_BLOCK_*, UI_BUTTON_CLICK, RAID_HORN...).
- `Mob.interact` es final → usar `mobInteract`. `positionRider(Entity, MoveFunction)` debe colocar al pasajero (si está vacío, el jugador queda flotando).
- `shouldRenderAtSqrDistance` NO debe devolver siempre false (hacía invisibles a los NPC).
- `x instanceof T t` falla si `x` ya es de tipo `T` (Java 17).
- IDs de mensajes de red usados: 0–15 (Act, Sync, AlbSync, Board x2, Team, Analizador x2, HerSync, HerAnim, Oficio.Open, Jefe.Dialog, Dado.Roll, Sonido.Music, Caja.Open, Caja.Try). El próximo libre es **16**.
- Los datos de jugador que deben sobrevivir a la muerte se agregan a la lista de `onClone` en `PoliciaMod`.

## Oficios y sistemas (archivo principal de cada uno)
- **Policía**: `PoliciaMod`, `PoliciaTreeScreen`, `PoliciaPatrullero`, `PoliciaRastreo`, `PoliciaJuicio`, `PoliciaMision`.
- **Albañil**: `PoliciaAlbanil`, `PoliciaAyudante`, `PoliciaTractor`, `PoliciaPlano`, `PoliciaPedido`, `PoliciaInspector`, `PoliciaDemolicion`.
- **Ladrón**: `PoliciaLadron`, `PoliciaDado`(+Screen), `PoliciaGolpe`, `PoliciaCaja`(+Screen), `PoliciaMercado`.
- **Herrero Legendario**: `PoliciaHerrero` (árbol con `COST`/`PRE`, máscara `her_un`), `PoliciaHerreroScreen`, `PoliciaHerreroAnim`, `PoliciaAnalizador`(+Screen), `PoliciaServicio`, `PoliciaForja`, `PoliciaAprendiz`, `PoliciaEncargo`.
- **Jefes y misiones**: `PoliciaBoss`, `PoliciaJefe`, `PoliciaDialogo`, `PoliciaOficio`(+Screen).
- **Mundo**: `PoliciaStruct` (estructuras y separación), `PoliciaEventos`, `PoliciaDiario`, `PoliciaEstacion`, `PoliciaContratos`, `PoliciaSonido`, `PoliciaPasivas`, `PoliciaBook` (libros de cada oficio).

## Estado al cerrar esta conversación
- Todo compila (último build OK). Nada de lo último fue probado en el juego por Claude; el usuario prueba y reporta.
- Últimos cambios: árbol del herrero estilo policía (clic derecho/doble clic para desbloquear gastando XP, raíz Grito), descripciones del herrero corregidas, estructuras más espaciadas y sin chocar.
- Historial completo de lo hecho: ver `docs/CONVERSACION.md` y el `git log` (un commit por cambio, en español).
