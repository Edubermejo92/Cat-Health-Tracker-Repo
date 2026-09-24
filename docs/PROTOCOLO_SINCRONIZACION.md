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
| `/padel/state` | ambos | Estado completo del partido, con su `rev` |
| `/padel/cmd` | ambos | Acción suelta. Solo la mandan versiones antiguas |
| `/padel/settings` | ambos | Ajustes (idioma, tema, reglas, nombres) |
| `/padel/health` | reloj → móvil | Pulso, calorías, distancia |
| `/padel/account` | móvil → reloj | Sesión de la cuenta (no se teclea en el reloj) |
| `/padel/history` | móvil → reloj | Últimos 30 partidos de la cuenta, para el Historial del reloj |

Rutas heredadas que se siguen aceptando (v2) para que un reloj o un móvil sin
actualizar no rompan del todo: `/padel/sync`, `/padel/point`, `/padel/bt`. Se traducen
internamente a `state`, `cmd` y `pair`.

## Sin modos: los dos a la vez

Antes había que elegir quién mandaba —Solo, Móvil o Reloj— y era una pregunta
que el usuario no tiene por qué responder: quiere puntuar desde donde tenga la
mano libre. **Ya no hay modos.** Las dos apps van siempre sincronizadas y
puntúa cualquiera de las dos.

### Quién gana si los dos puntúan a la vez

Cada aparato lleva un número de **revisión** (`rev`) que sube en cada cambio
hecho en él y viaja dentro del estado. Al recibir un estado:

| Situación | Qué se hace |
|---|---|
| `rev` recibida **mayor** que la propia | Se aplica: el otro va por delante |
| `rev` recibida **menor** | Se ignora el marcador, pero **la salud sí se aplica** |
| `rev` **iguales** | Gana el móvil |

El empate se resuelve siempre a favor del móvil, no por preferencia sino
porque hace falta una regla estable: si cada aparato eligiera distinto, los
dos marcadores quedarían diferentes para siempre.

Tras aplicar un estado remoto, el aparato **adopta la revisión recibida**, de
forma que los dos siguen contando desde el mismo número.

### Por qué se manda el estado entero

Se difunde el marcador completo y no la acción suelta. Por Bluetooth se
pierden mensajes, y con acciones sueltas un punto perdido dejaría los
marcadores descuadrados hasta el final del partido; con el estado completo, el
siguiente mensaje vuelve a poner a los dos de acuerdo.

`/padel/cmd` se sigue **aceptando** para no dejar tirada a una app sin
actualizar, pero ninguna versión nueva lo manda.

### El campo `mode`

Se sigue enviando con el valor `SYNC` para que una versión antigua que aún lo
espera no se quede colgada. Al recibirlo, se ignora.

## Reglas de flujo

1. **Los dos emiten `/padel/state`** en cuanto cambia algo en su lado, subiendo antes
   su `rev`. El que recibe lo aplica tal cual, sin recalcular.
2. **Quien recibe no reemite.** Mientras aplica un estado remoto pone un flag que
   impide difundir; si no, las dos apps se mandarían el mismo estado sin parar.
3. Un estado con `rev` menor que la propia se descarta —salvo la salud, que se aplica
   igual porque viene de los sensores del reloj y nunca es "vieja".
4. `/padel/health` va siempre del reloj al móvil: los sensores están en el reloj y el
   móvil nunca debe inventar esos números.
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

## `/padel/cmd` — acción suelta (solo versiones antiguas)

```json
{ "v": 3, "src": "watch", "seq": 7, "action": "point", "team": "A" }
```

| `action` | `team` | Efecto en quien lo recibe |
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

Junto con `/padel/history`, es la única ruta que el reloj procesa **aunque
su app esté cerrada**: el servicio guarda la sesión igual, para que ya esté
puesta cuando el usuario levante la muñeca. Todas las demás necesitan el
partido en marcha.

## `/padel/history` — historial de la cuenta

El reloj no habla con Supabase. El móvil ya sincroniza el historial de cada
usuario y le pasa al reloj sus últimos 30 partidos, en el mismo formato que
el reloj usa para los suyos. Se manda tras cada `/padel/account`, al terminar
una sincronización con la nube y al guardar un partido.

```json
{
  "v": 3, "src": "phone", "seq": 44,
  "signedIn": true,
  "played": 57, "won": 31,
  "matches": [
    { "date": 1757260800000, "teamA": "AZULES", "teamB": "ROJOS",
      "scoreA": 2, "scoreB": 1, "gamesA": 16, "gamesB": 13,
      "winner": "A", "duration": 4210, "kcal": 540 }
  ]
}
```

- `played` y `won` cuentan todo el historial, no solo lo que viaja.
- `scoreA`/`scoreB` son sets; `gamesA`/`gamesB`, juegos totales.
- Sin sesión, `matches` va vacío y el reloj vuelve a enseñar los partidos
  jugados con él.
