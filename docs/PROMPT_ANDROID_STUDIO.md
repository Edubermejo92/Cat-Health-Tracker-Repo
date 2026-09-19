# Briefing para Gemini en Android Studio

Pega esto entero en el chat de Gemini al abrir cualquiera de los dos
proyectos. Está escrito para que entienda el contexto **antes** de tocar
nada, porque este proyecto tiene varias trampas que no se ven leyendo un
fichero suelto.

> Si prefieres que Gemini tenga además **el código delante**, usa los prompts
> por app, que son éste mismo más el fuente completo incrustado:
> `docs/PROMPT_MOVIL.md` y `docs/PROMPT_WEAROS.md`.

---

## El encargo

Trabajas en **PadelPulse Live**, un marcador de pádel formado por **dos apps
Android que tienen que ir a la par**:

| | Móvil | Reloj |
|---|---|---|
| Carpeta | `PadelPulse-Movil/` | `PadelPulse-WearOS/` |
| Módulo | `:mobile` | `:app` |
| Interfaz | WebView que carga `assets/code.html` | Jetpack Compose for Wear OS |
| minSdk | 25 | 30 |
| versionCode | 518 | 5180 |

Las dos comparten `applicationId` (`padelpulseapp2.netlify.app`): son **una
sola ficha de Google Play con dos formatos**. compileSdk 35, Gradle 9.5.0.

Son **dos proyectos Gradle independientes**: se abren por separado con
*File → Open* sobre la carpeta que contiene `settings.gradle.kts`. No los
metas uno dentro de otro.

---

## Reglas que no se pueden romper

Si incumples alguna, la app compila igual y falla en producción. Son las que
más tiempo han costado.

### 1. Las dos apps se publican juntas

Comparten `applicationId`, así que Play exige **el mismo keystore** y
**versionCode distinto** en cada una. Los dos AAB se suben a la **misma
versión** de Play. Si sólo subes uno, los usuarios se quedan con una app
nueva y otra vieja y **la sincronización deja de funcionar**.

### 2. El protocolo está escrito en tres sitios

Las dos apps hablan por el **protocolo v3** sobre el Wearable Data Layer (no
es Bluetooth crudo). El contrato está en `docs/PROTOCOLO_SINCRONIZACION.md` y
**mirrorado en tres implementaciones que tienen que ir a la par**:

```
PadelPulse-Movil/mobile/src/main/assets/code.html        → objeto PPSync
PadelPulse-Movil/mobile/src/main/java/.../sync/SyncProtocol.kt
PadelPulse-WearOS/app/src/main/java/.../sync/SyncProtocol.kt
```

Si cambias una ruta o un campo en una sola, **no hay ningún error**: ni al
compilar ni al ejecutar. Simplemente el marcador deja de sincronizarse.

### 3. Tailwind va precompilado

`assets/code.html` usa clases de Tailwind y `assets/tailwind.css` está
**generado**. Si añades o cambias una clase, hay que regenerar:

```sh
./tools/build-web-assets.sh   # regenera tailwind.css
./tools/sync-web.sh           # copia a web/ (la versión Netlify)
```

Si no lo haces, la clase nueva **no tiene estilos** y no avisa nadie. Es el
error más fácil de cometer aquí.

### 4. El `pathPrefix` del manifiesto

El `WearableListenerService` de cada app filtra por `android:pathPrefix="/padel"`.
Tiene que ser idéntico en las dos o los mensajes **no llegan en segundo
plano**.

### 5. Requisitos de calidad de Wear OS

Play ya rechazó una actualización por esto. No lo toques sin saber:

- **Actividad en curso** (`MatchOngoingService.kt`): el partido tiene que
  salir en la esfera del reloj con el crono corriendo. Usa
  `Status.StopwatchPart` para que el reloj avance sin republicar.
- **Indicador de scroll**: el `PositionIndicator` va atado al
  `ScalingLazyListState` **de la pantalla visible**, no a uno compartido. Por
  eso cada pantalla tiene el suyo en `PadelApp.kt`.

---

## Cómo funciona cada app

### Móvil

Todo el interfaz vive en un único `code.html` de ~6.400 líneas, cargado en un
WebView desde `file:///android_asset/`. `MainActivity.kt` (~590 líneas) es el
puente: expone `AndroidBridge` al JavaScript.

Cosas que **no** funcionan dentro de un WebView y por eso van por el puente:
`window.open`, `navigator.share`, `navigator.clipboard` sobre `file://`, y
`speechSynthesis` (irregular y sin control de volumen). Todo eso está
resuelto con intents nativos y `TextToSpeech`.

La app es **offline-first**: Tailwind, la tipografía Lexend y los iconos van
empaquetados (~195 KB). Sólo salen a la red la cuenta, el respaldo del
historial y compartir.

### Reloj

Compose for Wear OS. `GameEngine.kt` (~715 líneas) lleva las reglas de pádel;
`PadelApp.kt` (~1.100) la navegación entre pantallas; `ScoreScreen.kt` (~590)
el marcador.

El reloj **no pide contraseña**: teclear un correo en 45 mm es una tortura.
La sesión le llega ya hecha desde el móvil por `/padel/account`.

---

## Sincronización: sin modos, por revisión

**No hay modo maestro/esclavo.** Antes había que elegir quién mandaba y se
quitó: el usuario quiere puntuar desde donde tenga la mano libre.

Las dos apps puntúan y difunden **el estado entero** (no la acción suelta:
por Bluetooth se pierden mensajes, y así el siguiente estado vuelve a
ponerlas de acuerdo). Los choques se resuelven con un número de **revisión**
que sube en cada cambio local:

| Situación | Qué hace quien recibe |
|---|---|
| `rev` recibida mayor | La aplica |
| `rev` menor | Ignora el marcador, **pero aplica la salud** |
| `rev` iguales | Gana el móvil |

El empate se resuelve siempre a favor del móvil no por preferencia, sino
porque hace falta una regla estable: si cada aparato eligiera distinto, los
marcadores quedarían diferentes para siempre.

**Anti-eco**: mientras se aplica un estado remoto hay un flag que impide
reemitir. Sin él las dos apps se mandan el mismo estado sin parar.

---

## Cuentas (Supabase)

Proyecto `fdlcdzlvvxqhzougcjwd`. Cuatro tablas con RLS contra `auth.uid()`.
Sin `supabase-js`: llamadas REST a mano desde `code.html`.

- El **historial pide cuenta**; el marcador funciona sin ella.
- Los partidos se guardan localmente aunque no haya cuenta, y suben al
  registrarse.
- **No hay "entrar con Google"**: se quitó, exigía credenciales de Google
  Cloud y dar de alta a cada tester a mano.
- La vuelta de los enlaces del correo entra por `padelpulse://auth`
  (`AuthLink.kt` + intent-filter en el manifiesto). El de confirmar cuenta
  entra directo; el de contraseña perdida pide una nueva.

**No hay árbitro IA.** Se quitó entero: exigía que el usuario pegase su propia
API Key, rompía el offline-first y podía inventarse la acción. La voz funciona
con reglas locales, al instante y sin red.

Detalle: las contraseñas **no se pueden ver**, ni el usuario ni nadie.
Supabase guarda un hash bcrypt. No añadas ninguna pantalla que las muestre.

---

## Qué mejorar

Esto es lo que está pendiente de verdad, con la ruta del fichero. Empieza por
lo de arriba.

### Móvil

1. **`code.html` tiene 6.445 líneas en un solo fichero.** Es el mayor
   problema de mantenimiento del proyecto. Partirlo en módulos (estado,
   reglas de pádel, sincronización, cuentas, vistas) sin romper el
   offline-first: no puede haber peticiones de red para cargar la app.

2. **Reconocimiento de voz sólo rico en español.** `processVoiceLocal()` en
   `code.html` tiene expresiones regulares muy completas para español y
   parciales para el resto. La app soporta 13 idiomas.

3. **Estado global `S`.** Todo el estado del partido está en un objeto
   global mutable. Cualquier refactor tiene que mantener `saveState()` /
   `saveStateOnly()`, que distinguen si hay que difundir al reloj.

### Reloj

4. **No hay buzón para mensajes con la app cerrada.** El móvil tiene
   `sync/PendingInbox.kt` y el reloj no: en `WearListenerService.kt`, si
   `MainActivity.gameEngine` es null se descarta todo salvo la cuenta. Un
   punto marcado en el móvil con la app del reloj cerrada **se pierde**.
   Hacer el equivalente de `PendingInbox` para el reloj.

5. **El reloj no tiene agenda ni invitaciones.** El móvil sí (amigos,
   pareja habitual, invitar). Decidir si tiene sentido en la muñeca; si no,
   documentarlo para que nadie lo intente.

### Las dos

6. **No hay tests.** La verificación se ha hecho con Playwright contra el
   `code.html` y con revisiones estructurales de Kotlin, todo fuera del
   proyecto. No hay `src/test` ni `src/androidTest`. Lo más rentable sería
   cubrir `GameEngine.kt` (reglas de pádel: ventajas, punto de oro,
   tie-break, super tie-break) con JUnit, que es lógica pura y sin Android.

7. **El protocolo mirrorado en tres sitios** (regla 2). Generarlo desde una
   única fuente evitaría la clase de fallo más peligrosa del proyecto.

---

## Cómo verificar lo que toques

Compilar no basta: casi todos los fallos de este proyecto compilan bien.

```sh
cd PadelPulse-Movil  && ./gradlew installRelease   # móvil por USB
cd PadelPulse-WearOS && ./gradlew installRelease   # reloj por Wi-Fi (adb connect)
```

Para probar la sincronización de verdad hacen falta **móvil y reloj
emparejados con la misma cuenta de Google**. Con el emulador no vale.

Prueba mínima tras tocar la sincronización:

1. Puntúa en el móvil → el reloj lo refleja.
2. Puntúa en el reloj → el móvil lo refleja.
3. Cierra la app del reloj, puntúa en el móvil, ábrela → ver si se pone al día.
4. Puntúa en los dos casi a la vez → los dos tienen que acabar igual.

Si tocas `code.html`, **ejecuta `./tools/build-web-assets.sh`** antes de
compilar, o las clases nuevas saldrán sin estilos.

---

## Documentación del repositorio

| Fichero | Qué cuenta |
|---|---|
| `docs/PROTOCOLO_SINCRONIZACION.md` | El contrato entre las dos apps |
| `docs/FIRMAR_Y_COMPILAR.md` | Firma, AAB y la receta paso a paso |
| `docs/PLAY_STORE_TESTERS.md` | Play Console e invitaciones |
| `docs/CUENTAS_SUPABASE.md` | Cuentas, RLS, correos, contraseñas |
| `docs/VOZ.md` | Micrófono y voz de la app |
| `docs/NETLIFY.md` | La versión web |

---

## Cómo quiero que trabajes

- **Pregunta antes de cambiar el protocolo o el esquema de Supabase.** Son
  los dos sitios donde un error no se ve hasta que falla en la pista.
- **Explica el porqué**, no sólo el qué: este proyecto tiene varias
  decisiones que parecen raras y tienen motivo (el estado entero en vez de la
  acción, el empate a favor del móvil, el reloj sin contraseñas).
- **No toques los requisitos de calidad de Wear OS** sin decirlo: ya costaron
  un rechazo de Play.
- Comentarios y mensajes de commit **en español**, como el resto del
  proyecto.
