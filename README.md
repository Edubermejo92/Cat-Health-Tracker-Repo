# PadelPulse Live · Móvil

App Android de móvil, tablet y Chromebook: marcador, estadísticas, historial,
agenda de jugadores y comandos de voz. WebView + Kotlin.

> **Esta rama lleva solo la app del móvil.** La del reloj está en
> `padelpulse-live-wearos`, y la rama `claude/padelpulse-apps-coordination-dixwgy`
> tiene las dos juntas.

| Carpeta | Qué es |
|---|---|
| `PadelPulse-Movil/` | La app. Todo el interfaz vive en `mobile/src/main/assets/code.html` |
| `web/` | La misma app, lista para desplegar en Netlify |
| `tools/` | Scripts para regenerar los assets web |
| `docs/` | Protocolo de sincronización, firma, Play Store, cuentas y Netlify |

## Cuidado al tocar el protocolo

Las dos apps hablan por el **protocolo v3**, escrito igual en tres sitios:

- `PadelPulse-Movil/mobile/src/main/assets/code.html` — el objeto `PPSync`
- `PadelPulse-Movil/.../sync/SyncProtocol.kt`
- `PadelPulse-WearOS/.../sync/SyncProtocol.kt` *(en la otra rama)*

Al estar las apps en ramas separadas, **es fácil que se desincronicen sin que
nadie se entere**: cambias una ruta o un campo aquí, compila, y el reloj deja
de entenderse con el móvil sin ningún error visible. Si tocas el protocolo,
tócalo también en la rama del reloj y deja el contrato al día en
[`docs/PROTOCOLO_SINCRONIZACION.md`](docs/PROTOCOLO_SINCRONIZACION.md).

## Play Store

Las dos apps comparten `applicationId` (`padelpulseapp2.netlify.app`) porque son
**una sola ficha con dos formatos**. Eso obliga a firmarlas con el mismo
keystore y a darles `versionCode` distintos: el móvil va en la serie 5xx y el
reloj en la 50xx.

## Empezar

- **Compilar y firmar** → [`docs/FIRMAR_Y_COMPILAR.md`](docs/FIRMAR_Y_COMPILAR.md)
- **Subir a Play Store y repartir a testers** → [`docs/PLAY_STORE_TESTERS.md`](docs/PLAY_STORE_TESTERS.md)
- **Cuentas y login con Google** → [`docs/CUENTAS_SUPABASE.md`](docs/CUENTAS_SUPABASE.md)
- **Publicar la web** → [`docs/NETLIFY.md`](docs/NETLIFY.md)

## Sin conexión

La app no depende de internet para funcionar. Tailwind, la tipografía Lexend y
los iconos van empaquetados en `assets/` (~195 KB): en una pista sin cobertura
la app arranca y se ve igual. Solo salen a la red la cuenta, el respaldo del
historial y compartir resultados.

Si tocas clases de Tailwind en `assets/code.html`, ejecuta después:

```bash
./tools/build-web-assets.sh   # regenera tailwind.css
./tools/sync-web.sh           # copia a web/
```
