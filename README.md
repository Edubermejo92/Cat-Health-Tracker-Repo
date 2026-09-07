# PadelPulse Live · Wear OS

App de reloj: puntúa desde la muñeca y aporta pulso, calorías y distancia
reales. Kotlin + Jetpack Compose for Wear OS.

> **Esta rama lleva solo la app del reloj.** La del móvil está en
> `padelpulse-live-mobile`, y la rama `claude/padelpulse-apps-coordination-dixwgy`
> tiene las dos juntas.

| Carpeta | Qué es |
|---|---|
| `PadelPulse-WearOS/` | La app del reloj |
| `docs/` | Protocolo de sincronización, firma, Play Store y cuentas |

## Cuidado al tocar el protocolo

Las dos apps hablan por el **protocolo v3**, escrito igual en tres sitios:

- `PadelPulse-WearOS/.../sync/SyncProtocol.kt`
- `PadelPulse-Movil/.../sync/SyncProtocol.kt` *(en la otra rama)*
- `PadelPulse-Movil/mobile/src/main/assets/code.html` — el objeto `PPSync`
  *(en la otra rama)*

Al estar las apps en ramas separadas, **es fácil que se desincronicen sin que
nadie se entere**: cambias una ruta o un campo aquí, compila, y el reloj deja
de entenderse con el móvil sin ningún error visible. Si tocas el protocolo,
tócalo también en la rama del móvil y deja el contrato al día en
[`docs/PROTOCOLO_SINCRONIZACION.md`](docs/PROTOCOLO_SINCRONIZACION.md).

Ojo también con el `pathPrefix` del `WearListenerService` en el manifiesto:
tiene que ser `/padel` en las dos apps o los mensajes no llegan en segundo
plano.

## Play Store

Las dos apps comparten `applicationId` (`padelpulseapp2.netlify.app`) porque son
**una sola ficha con dos formatos**. Eso obliga a firmarlas con el mismo
keystore y a darles `versionCode` distintos: el reloj va en la serie 50xx y el
móvil en la 5xx.

Con `com.google.android.wearable.standalone=false`, Play instala esta app en el
reloj automáticamente cuando el usuario instala la del móvil. No hay que
buscarla en la tienda del reloj.

## Requisitos de calidad de Play

Dos cosas que Play exige a las apps de Wear OS y que ya están puestas. Si se
tocan, la actualización se rechaza:

- **Actividad en curso** (`MatchOngoingService.kt`): el partido tiene que salir
  en la esfera del reloj con el crono corriendo.
- **Indicador de scroll**: el `PositionIndicator` va atado al estado de la
  lista **de la pantalla visible**, no a uno compartido. Por eso cada pantalla
  tiene su propio `ScalingLazyListState` en `PadelApp.kt`.

## La cuenta

El reloj no pide contraseñas: teclear un correo en una pantalla de 45 mm es una
tortura. La sesión llega ya hecha desde el móvil por el Data Layer, y quien no
quiera cuenta juega igual. Ver [`docs/CUENTAS_SUPABASE.md`](docs/CUENTAS_SUPABASE.md).

## Empezar

- **Compilar y firmar** → [`docs/FIRMAR_Y_COMPILAR.md`](docs/FIRMAR_Y_COMPILAR.md)
- **Subir a Play Store y repartir a testers** → [`docs/PLAY_STORE_TESTERS.md`](docs/PLAY_STORE_TESTERS.md)
- **Cómo hablan entre ellas** → [`docs/PROTOCOLO_SINCRONIZACION.md`](docs/PROTOCOLO_SINCRONIZACION.md)
