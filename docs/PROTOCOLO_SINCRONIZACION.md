# Protocolo de sincronización PadelPulse Live · v3

Contrato **único** entre `PadelPulse-Movil` (móvil/tablet) y `PadelPulse-WearOS` (reloj).
Las dos apps implementan exactamente estos mensajes. Si cambias algo aquí, hay que
cambiarlo en los dos lados a la vez y subir `versionCode` en ambas.

Transporte: **Wearable MessageClient** (Google Play Services). No es Bluetooth "a pelo":
el emparejamiento Bluetooth ya lo hace el sistema (Wear OS ↔ teléfono). El código de 4
dígitos de la app es sólo una confirmación de que el usuario está sincronizando el reloj
correcto, no una capa de seguridad.

## Prefijo de rutas

Todas las rutas empiezan por `/padel/`. Es obligatorio: el `intent-filter` del
`WearableListenerService` del móvil filtra por `android:pathPrefix="/padel"`, y sin ese
prefijo los mensajes no llegan cuando la app está en segundo plano.

| Ruta | Dirección | Para qué |
|---|---|---|
| `/padel/hello` | ambos | Presencia y handshake de versión |
| `/padel/pair` | ambos | Emparejamiento por código |
| `/padel/state` | maestro → esclavo | Estado completo del partido |
| `/padel/cmd` | esclavo → maestro | Acción del usuario (punto, deshacer…) |
| `/padel/settings` | ambos | Ajustes (idioma, tema, reglas, nombres) |
| `/padel/health` | reloj → móvil | Pulso, calorías, distancia |
| `/padel/account` | móvil → reloj | Sesión de la cuenta (no se teclea en el reloj) |

Rutas heredadas que se siguen aceptando (v2) para que un reloj o un móvil sin
actualizar no rompan del todo: `/padel/sync`, `/padel/point`, `/padel/bt`. Se traducen
internamente a `state`, `cmd` y `pair`.

## Modos

Un solo campo, `mode`, con tres valores normalizados:

| Valor | Significado | Quién manda |
|---|---|---|
| `SOLO` | Cada dispositivo va por su cuenta | nadie |
| `PHONE` | El móvil lleva el marcador | móvil |
| `WATCH` | El reloj lleva el marcador | reloj |

Equivalencias heredadas que ambos lados normalizan al recibir:
`MOVIL_MANDA` → `PHONE`, `RELOJ_MANDA` → `WATCH`, `solo`/`mobile` → `SOLO`.

## Reglas de flujo

1. **Sólo el maestro emite `/padel/state`.** El esclavo lo aplica tal cual, sin recalcular.
2. **El esclavo nunca emite estado**: manda `/padel/cmd`. El maestro aplica el comando y
   difunde el nuevo `state`. Así no hay dos verdades.
3. En `SOLO` no se intercambia ni `state` ni `cmd`. Sólo `hello` (para pintar el estado
   de conexión) y `health`.
4. `/padel/health` va siempre del reloj al móvil, en cualquier modo: los sensores están
   en el reloj y el móvil nunca debe inventar esos números.
5. **Anti-eco**: cada emisor lleva un `seq` monotónico. El receptor descarta cualquier
   mensaje con `seq` menor o igual al último visto de ese origen, y mientras aplica un
   estado remoto pone un flag que impide reemitir. Sin esto las dos apps se
   retroalimentan y el marcador oscila.
6. Un `state` con `over:true` cierra el partido en ambos lados.

## `/padel/state` — estado canónico

```json
{
  "v": 3,
  "src": "phone",
  "seq": 12,
  "ts": 1754212800000,
  "mode": "PHONE",
  "match":  { "bestOf": 3, "goldenPoint": false, "superTieBreak": true, "currentSet": 2 },
  "teams": {
    "A": { "name": "NOSOTROS", "pts": 2, "games": 4, "sets": 1 },
    "B": { "name": "ELLOS",    "pts": 3, "games": 5, "sets": 0 }
  },
  "flags": {
    "deuce": false, "adv": null, "tiebreak": false, "superTiebreak": false,
    "goldenPointActive": false, "serving": "A", "faults": 0,
    "over": false, "winner": null
  },
  "tb":     { "A": 0, "B": 0, "serving": "A", "n": 0 },
  "health": { "hr": 132, "kcal": 210, "km": 1.84 },
  "clock":  1875
}
```

Detalles que importan (aquí es donde fallaba la v2):

- `teams.X.pts` es **siempre el índice de punto 0-3** (0/15/30/40). Nunca la cadena
  `"AD"`, nunca los puntos del tie-break.
- La ventaja va en `flags.adv`: `"A"`, `"B"` o `null`. Nunca la cadena `"null"`
  (`JSONObject.optString` sobre `JSONObject.NULL` devuelve literalmente `"null"`, que es
  *truthy*: ése era el bug que dejaba el marcador clavado en 40).
- Los puntos del tie-break y del super tie-break van **sólo** en `tb`. `flags.tiebreak` /
  `flags.superTiebreak` dicen cuál está activo.
- `teams.X.sets` son sets ganados, no el historial. El historial es local de cada app.
- `clock` es el cronómetro del partido en segundos.

## `/padel/cmd` — acción del esclavo

```json
{ "v": 3, "src": "watch", "seq": 7, "action": "point", "team": "A" }
```

| `action` | `team` | Efecto en el maestro |
|---|---|---|
| `point` | `A`/`B` | Suma un punto |
| `minus` | `A`/`B` | Resta un punto |
| `undo` | — | Deshace la última jugada |
| `fault` | `A`/`B` | Falta de saque (2ª falta = doble falta) |
| `serve` | `A`/`B` | Cambia quién saca |
| `reset` | — | Partido nuevo |

## `/padel/pair` — emparejamiento

```json
{ "v": 3, "action": "request", "code": "4821", "device": "Galaxy Watch6" }
```

- `request`: lo manda el reloj con el código que ha tecleado. `code:"AUTO"` pide
  vincular sin código.
- `accept` / `reject`: respuesta del móvil. El móvil acepta si el código coincide con el
  suyo, o si la petición es `AUTO` y tiene el auto-emparejado activo (por defecto sí).
- El código lo genera y lo guarda **el móvil**, y es lo que se muestra en su pantalla de
  ajustes. El reloj no genera códigos.

## `/padel/settings`

```json
{ "v": 3, "src": "phone", "lang": "es", "theme": "neon", "color": "#00FD87",
  "goldenPoint": false, "superTieBreak": true, "bestOf": 3,
  "nameA": "NOSOTROS", "nameB": "ELLOS", "mode": "PHONE" }
```

Los ajustes se propagan en los dos sentidos y en cualquier modo: idioma, tema y nombres
de pareja deben verse igual en la muñeca y en el móvil. `theme` es el identificador
(`neon`, `fuego`, `hielo`, `clasico`, `noche`, `oro`); `color` va incluido por
compatibilidad con la v2.

## `/padel/health`

```json
{ "v": 3, "src": "watch", "hr": 132, "kcal": 210, "km": 1.84, "steps": 2450 }
```

El móvil marca `S.healthFromWatch = true` al recibirlo y deja de estimar calorías por su
cuenta. Si un valor es 0 se muestra `-`, no se inventa.

## `/padel/hello`

```json
{ "v": 3, "src": "watch", "app": "1.2.0", "proto": 3, "mode": "WATCH", "paired": true }
```

Se manda al conectar, al volver a primer plano y cada 30 s mientras hay partido. Sirve
para pintar el indicador de conexión y para detectar versiones de protocolo distintas
(si `proto` no coincide, cada app avisa al usuario de que actualice la otra).

## `/padel/account` — sesión de la cuenta

El reloj no pide contraseña: la sesión la inicia el móvil y se la pasa por
aquí. Viaja por el canal cifrado del sistema entre dos dispositivos ya
emparejados, no sale a internet.

El móvil lo manda al aceptar un emparejamiento, al entrar y al salir.

```json
{
  "v": 3, "src": "phone", "seq": 42,
  "action": "session",
  "email": "edu@ejemplo.com",
  "name": "Edu",
  "token": "<access_token>",
  "refresh": "<refresh_token>",
  "expires": 1757260800000
}
```

Al cerrar sesión en el móvil:

```json
{ "v": 3, "src": "phone", "seq": 43, "action": "signout" }
```

Es la única ruta que el reloj procesa **aunque su app esté cerrada**: el
servicio guarda la sesión igual, para que ya esté puesta cuando el usuario
levante la muñeca. Todas las demás necesitan el partido en marcha.
