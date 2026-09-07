# Publicar las webs en Netlify

Hay dos sitios y una sola cuenta: **EBLDigital** (equipo `edubermejo92`).

| Sitio | Carpeta del repositorio | Que es |
|-------|-------------------------|--------|
| `padelpulselive-wearos` | `web-wearos/` | Pagina de presentacion del reloj |
| *(pendiente de nombre)* | `web/` | La app del movil, en version web |

## El nombre `padelpulselive` esta cogido

Netlify exige que los nombres sean unicos en **toda** la plataforma, no solo
dentro de tu cuenta, y `padelpulselive` ya lo tiene alguien. En el equipo
EBLDigital no esta, asi que o lo tiene otra cuenta tuya antigua, o lo tiene
un tercero.

Dos salidas:

- **Entrar con la cuenta que lo tiene** y desplegar `web/` ahi. La direccion
  que ya conocen tus testers no cambia.
- **Elegir otro nombre** en EBLDigital (`padel-pulse-live`, `padelpulse-app`,
  lo que prefieras) y avisar del cambio de direccion.

## Como desplegar

### Opcion A — conectar el repositorio (recomendado)

Se despliega solo cada vez que se sube algo a la rama.

En **Site configuration › Build & deploy › Continuous deployment**:

| Ajuste | `padelpulselive-wearos` | el sitio del movil |
|--------|-------------------------|--------------------|
| Repository | `Edubermejo92/Nuevo_repo` | igual |
| Branch | `padelpulse-live-wearos` | `padelpulse-live-mobile` |
| **Base directory** | `web-wearos` | `web` |
| Build command | *(vacio)* | *(vacio)* |
| Publish directory | `.` | `.` |

Lo importante es la **base directory**: con ella cada sitio lee su propio
`netlify.toml` y no se pisan. Por eso no hay ningun `netlify.toml` en la raiz
del repositorio; si lo hubiera, mandaria sobre los dos sitios a la vez.

### Opcion B — arrastrar la carpeta

En **Deploys › Deploy manually**, arrastra la carpeta (`web-wearos` o `web`).
Rapido para salir del paso, pero hay que repetirlo a mano en cada cambio.

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

La pagina del reloj no necesita nada de esto: no tiene inicio de sesion.
