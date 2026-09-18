# Publicar las webs en Netlify

La web es **la app del movil**: `web/index.html` es una copia de `code.html`,
el mismo archivo que va dentro del APK. No hay web del reloj, porque el reloj
es una app Compose nativa.

## Donde esta el sitio

El sitio `padelpulselive` **no esta en el equipo EBLDigital**: el nombre esta
cogido en Netlify -que los exige unicos en toda la plataforma-, asi que lo
tiene otra cuenta. Para desplegar hay que entrar con esa cuenta.

## Como desplegar

### Opcion A — conectar el repositorio (recomendado)

Se despliega solo cada vez que se sube algo a la rama.

En **Site configuration › Build & deploy › Continuous deployment**:

| Ajuste | Valor |
|--------|-------|
| Repository | `Edubermejo92/Nuevo_repo` |
| Branch | `padelpulse-live-mobile` |
| **Base directory** | `web` |
| Build command | *(vacio)* |
| Publish directory | `.` |

La **base directory** es lo importante: con ella el sitio lee `web/netlify.toml`
y no hace falta ningun `netlify.toml` en la raiz del repositorio.

### Opcion B — arrastrar la carpeta

En **Deploys › Deploy manually**, arrastra la carpeta `web`. Rapido para salir
del paso, pero hay que repetirlo a mano en cada cambio.

## Acuerdate de sincronizar la web antes de desplegar

`web/index.html` es una copia de `code.html`, el mismo archivo que va dentro
de la app Android. Si tocas la app, regenera la web antes de publicar:

```sh
./tools/build-web-assets.sh   # obligatorio si cambiaron clases de Tailwind
./tools/sync-web.sh           # copia code.html y los recursos a web/
```

## Y avisa a Supabase de la direccion

Entrar con Google necesita que la direccion del sitio este en la lista de
**Authentication › URL Configuration › Additional Redirect URLs**. Si cambias
el nombre del sitio, cambia tambien ahi. Ver `CUENTAS_SUPABASE.md`.

Si cambias de sitio o de nombre, acuerdate tambien de esto o el login con
Google dejara de volver a la app.

---

## Cómo saber si lo desplegado está al día

La web enseña su versión abajo del todo en **Ajustes**, con el sufijo `· web`:

```
PadelPulse Live 5.1.7 · web
```

Si la que ves ahí no coincide con la del repositorio, **lo desplegado es
viejo** y estás mirando cambios de hace varias versiones. Ha pasado: se
reportó que la web seguía enseñando el botón de Google cuando el código no lo
tiene desde la 5.1.0.

Recuerda además que el navegador cachea: tras desplegar, recarga forzando
(mantener pulsado el botón de recargar, o abrir en una pestaña de incógnito).

## Antes de desplegar, sincroniza

`web/index.html` es una copia de `code.html`. Si tocaste la app, regenera:

```sh
./tools/build-web-assets.sh   # obligatorio si cambiaron clases de Tailwind
./tools/sync-web.sh           # copia code.html y los recursos a web/
```

Los dos ficheros tienen que quedar idénticos:

```sh
diff <(md5sum < web/index.html) <(md5sum < PadelPulse-Movil/mobile/src/main/assets/code.html)
```
