# Cat Health Tracker 🐈

App de gestión, cuidados y salud para gatos. Misma arquitectura y sistema de diseño que
**TestudoTracker**: un único `index.html` autocontenido (HTML + CSS + JS + datos) que se
sirve dentro de un WebView (Median.co, Capacitor…) o se usa directamente como PWA.

Funciona **sin conexión y sin cuenta**: todo se guarda en el `localStorage` del dispositivo.
Opcionalmente, el usuario puede crear una cuenta y sincronizar sus gatos con **Supabase**
para no perderlos al cambiar de móvil.

**Versión actual: v1.2.0 "Siberiano"** — visible en Ajustes dentro de la propia app.

## Versiones

Cada versión nueva sube `APP_VERSION` / `APP_VERSION_NAME` en `index.html` y
`CACHE_VERSION` en `sw.js` a la vez (si no, el service worker sigue sirviendo la caché
vieja). El nombre de cada versión es una raza del catálogo de la app, elegida por algo
del propio release.

| Versión | Nombre | Qué trae |
|---|---|---|
| **1.2.0** | Siberiano — *«sigue a su humano por la casa»* | Notificaciones push en segundo plano: los avisos llegan aunque la app esté cerrada |
| 1.1.0 | — | Cuenta y sincronización con Supabase, alta guiada del gato en 7 pasos, fotos reales de las razas |
| 1.0.0 | — | Lanzamiento inicial: razas, alimentación, cuidados, salud, calendario y recordatorios locales |

## Contenido

| Pestaña | Qué incluye |
|---|---|
| **Inicio** | Recordatorios del día con interruptor y alarma — **con cuenta, llegan como notificación push aunque la app esté cerrada** · selector de mes con consejos estacionales (primavera, verano, otoño e invierno, 8 consejos por estación) |
| **Razas** | 54 razas agrupadas por región de origen, con peso ideal por sexo, longitud, esperanza de vida, escalas de cepillado/actividad/sociabilidad/vocalidad y fichas de pelaje, alimentación, salud, convivencia y curiosidades |
| **Comida** | 151 alimentos en 6 categorías (pienso, húmedo, carne y pescado, vegetal, lácteos y huevo, snacks y suplementos) con 4 niveles de idoneidad: apto / ocasional / no dar / **tóxico** · buscador, filtros y calculadora de ración diaria |
| **Salud** | Perfil del gato con foto · peso, longitud, edad y edad humana · condición corporal frente al rango de su raza · **evaluación automática de si necesita veterinario** · consejos según sus problemas crónicos · gráfico de peso · últimos registros |
| **Cuidados** | 9 bloques con más de 70 fichas: alimentación y agua, pelaje y cepillado, arenero, salud preventiva, señales de alarma, conducta, seguridad en casa, viajes, gatitos y senior |
| **Historial** | Dashboards (peso, longitud y BCS con banda de rango ideal, conteo de vacunas y visitas al veterinario) · **calendario mensual** con próximos avisos · línea de tiempo filtrable |

## Alta guiada del gato

Dar de alta un gato no es un formulario, son 7 pasos con explicación y validación en cada uno,
y una pantalla final de celebración:

1. **Nombre** e icono identificativo (lo único obligatorio).
2. **Foto** — se recorta en cuadrado a 400 px y se comprime.
3. **Raza** — al elegirla se muestra su peso sano por sexo y sus predisposiciones.
4. **Sexo** y esterilización — determinan el rango de peso y las necesidades calóricas.
5. **Edad** — fecha exacta o edad aproximada en años y meses; indica la etapa vital
   y la frecuencia de revisiones que le corresponde.
6. **Peso** — veredicto inmediato frente al rango de su raza y sexo.
7. **Problemas de salud crónicos** — «Está sano» o selección entre 18 de los más frecuentes
   en el gato (renal, piedras urinarias, cistitis idiopática, asma, diabetes, hipertiroidismo,
   artrosis, cardiopatía, alergias, EII, dental, obesidad, FIV/FeLV, epilepsia, ojos,
   estreñimiento, hígado) más un campo libre para añadir otros.
8. **«¡FELICIDADES! Ya has registrado a *(nombre)*»** con su foto y un resumen de la ficha.

Los problemas crónicos no se quedan en un dato: alimentan una tarjeta de consejos específicos
en el panel de Salud y entran en la evaluación veterinaria (por ejemplo, un gato renal sin
revisión en más de 6 meses genera una alerta roja).

Editar un gato reutiliza el mismo asistente, precargado y con «Guardar y salir» en cada paso.

## Funciones

- **Hasta 20 gatos**, cada uno con su ficha, su foto y su historial independiente.
- **Registros**: peso, visita veterinaria, vacuna, antiparasitario, aseo, malestar y nota.
- **Dictado por voz**: botón 🎤 en las notas que transcribe automáticamente lo que dices
  (Web Speech API; requiere permiso de micrófono).
- **Registro de malestar**: 22 síntomas frecuentes en chips + nivel de gravedad.
- **Calendario con alarmas**: comprobación cada 20 segundos con la app abierta (aviso a
  pantalla completa, sonido y posponer 10 minutos), y **notificación push real con la app
  cerrada** si el usuario tiene cuenta y da permiso — ver [«Notificaciones push»](#notificaciones-push-en-segundo-plano).
  Frecuencias: diaria, varias veces al día, días concretos de la semana, cada N días,
  mensual, anual y una sola vez.
- **Avisos automáticos deducidos del historial**: próxima vacuna (+365 d), próximo
  antiparasitario (+30 d) y próxima revisión (+365 d, o +182 d a partir de los 8 años).
- **Evaluación de salud**: cruza peso con el rango de la raza y el sexo, tendencia del
  peso, BCS, edad, última vacuna, último antiparasitario, última revisión y malestares
  recientes, y devuelve un veredicto en tres niveles con los motivos concretos.
- **Calculadora de ración**: RER = 70 × peso^0,75 con factores por estado
  (gatito, adulto, esterilizado, senior, dieta, gestación, lactancia) y reparto
  seco/húmedo en gramos.
- **Bilingüe** español / inglés, con detección del idioma del dispositivo.
- **Exportación** de todos los datos a JSON.

## Fotos de las razas

Las fichas muestran una foto real de cada raza cuando el archivo existe en
`img/breeds/{id}.jpg`. Si falta, la app cae automáticamente al emoji de la raza,
así que las fotos son opcionales y nunca rompen nada.

**Por qué no vienen incluidas.** Casi todas las fotos de gatos que circulan por
internet tienen derechos de autor, y publicar una app con ellas es motivo de
retirada en Google Play. `tools/fetch-breed-photos.mjs` las descarga de Wikimedia
Commons, que publica la licencia de cada archivo, **descartando todo lo que no
permita uso comercial** y guardando el autor de cada foto para poder citarlo.

```bash
node tools/fetch-breed-photos.mjs   # descarga las fotos y genera credits.json
node build.mjs                      # deja dist/ listo, con las fotos dentro
```

Estos dos comandos son idénticos en Windows (PowerShell o cmd), macOS y Linux:
son scripts de Node, no de bash. **Hace falta tener el repositorio completo**
descargado (no solo la carpeta `dist/` de un despliegue anterior), porque
`tools/` y `build.mjs` no forman parte de lo que se publica en Netlify — ver
[«Publicación web»](#publicación-web-netlify) más abajo para cómo bajarlo.

No necesita instalar nada (usa el `fetch` de Node 18+ y pide a Wikimedia la
miniatura ya redimensionada). Al terminar informa de qué razas se quedaron sin
foto y por qué. Para cualquiera de ellas puedes poner una imagen propia en
`img/breeds/<id>.jpg` y añadir su autor y licencia a `img/breeds/credits.json`.

**Atribución.** Las licencias Creative Commons obligan a citar al autor. La app
lo hace en dos sitios: un crédito discreto sobre la foto en la ficha de cada raza,
y una pantalla completa en *Ajustes → Créditos de las fotos*, con autor, licencia
y enlace al original. Si sustituyes una foto, actualiza también su entrada en
`credits.json`.

## Cuenta y sincronización (Supabase)

La app es **local primero**. Sin cuenta funciona entera; con cuenta, los gatos, registros,
recordatorios y fotos se sincronizan y se recuperan en cualquier móvil.

- **Registro y acceso** con correo y contraseña, y recuperación de contraseña.
- **Sincronización automática** al entrar, al recuperar la conexión y 3 segundos después
  de cada cambio, más un botón «Sincronizar ahora» en Ajustes.
- **Orden de sincronización**: primero se suben las bajas de este móvil, después se baja lo
  remoto y por último se sube solo lo que aquí es más nuevo. Ese orden es deliberado: subir
  antes de bajar resucitaría los gatos borrados desde otro dispositivo.
- **Borrado lógico** (`deleted_at`) para que las bajas se propaguen entre dispositivos.
- **Fotos** en un bucket privado de Supabase Storage, en la carpeta del propio usuario.
- **Sin librerías**: cliente REST propio contra la API de Supabase, coherente con el resto
  de la app, que no tiene ninguna dependencia de JavaScript.

### Seguridad

Cada tabla tiene RLS activo y políticas `auth.uid() = user_id`. Está verificado contra la
base de datos real que una cuenta **no puede** leer los gatos de otra, renombrarlos, colgar
registros de un gato ajeno ni insertar filas a nombre de otro usuario. El límite de 20 gatos
se aplica también en el servidor mediante un trigger, no solo en la interfaz.

La clave publicable que aparece en `index.html` es pública por diseño: lo que protege los
datos es RLS, no el secreto de la clave.

### Coste y límites del plan gratuito

Medido sobre la base real: **un usuario con 20 gatos y un año completo de registros
(3.000 filas) ocupa 1,4 MB**. Con los 500 MB del plan gratuito caben del orden de 350
usuarios en ese uso máximo, y bastantes más en un uso normal de 1-3 gatos. Coste: **0 €/mes**.

Tres cosas a tener en cuenta antes de publicar la app:

1. **El proyecto se pausa** tras 7 días sin ninguna actividad en el plan gratuito. Con
   usuarios reales sincronizando no ocurre; si pasa, se reactiva desde el panel de Supabase.
2. **El correo integrado de Supabase está limitado a unos pocos envíos por hora** y es solo
   para pruebas. Para una app publicada hay que configurar un SMTP propio (Resend, Brevo y
   similares tienen plan gratuito), o los correos de confirmación no llegarán a los usuarios.
3. **La confirmación de correo viene activada** por defecto. La app ya lo contempla: tras
   registrarse muestra «revisa tu correo y confirma la dirección». Se puede desactivar en
   Authentication → Providers → Email si prefieres un alta sin fricción.

### Esquema

El esquema vive en el repositorio, no solo en la nube:

```
supabase/migrations/
  20260830120500_cat_health_tracker_schema.sql    tablas, índices, triggers, límite de 20 gatos
  20260830120517_cat_health_tracker_rls.sql       seguridad por filas
  20260830120529_cat_photos_storage.sql           bucket privado de fotos y sus políticas
  20260830120547_lock_down_handle_new_user.sql    cierra las funciones SECURITY DEFINER
  20260830123000_cats_chronic_conditions.sql      problemas crónicos del gato
  20260830233000_push_notifications_schema.sql    tablas de notificaciones push (ver más abajo)
  20260830233100_push_secrets_access.sql          acceso a Supabase Vault desde la Edge Function
  20260830233200_push_cron_schedule.sql           cron que dispara los envíos cada 5 minutos
```

Para levantarlo en otro proyecto: `supabase db push`, o pegar los archivos en orden en el
editor SQL. Después, cambia `CLOUD.url` y `CLOUD.key` al principio del `<script>`.

## Notificaciones push en segundo plano

El motor de alarmas original (`checkAlarms`, un `setInterval` en el propio JS) solo suena
con la pestaña abierta: es lo máximo que puede hacer JavaScript corriendo dentro de una
página. Para que un aviso llegue con **el móvil bloqueado o la app cerrada**, hace falta algo
fuera del navegador que sepa la hora de cada recordatorio y se lo mande al dispositivo — eso
es lo que monta este bloque, y es **el motivo por el que las notificaciones en segundo plano
requieren una cuenta**: sin usuario identificado no hay dónde guardar en qué dispositivo
avisar. Sin cuenta, la app sigue avisando igual mientras la pestaña permanece abierta.

**Cómo funciona, de punta a punta:**

1. Al activar las notificaciones (`enableNotifications()`), el navegador se suscribe a Web
   Push (`PushManager.subscribe`) con la clave pública VAPID como `applicationServerKey`. Si
   hay sesión iniciada, esa suscripción (endpoint + claves de cifrado + zona horaria del
   dispositivo) se guarda en `push_subscriptions`.
2. `pg_cron` invoca cada 5 minutos, dentro de la propia base de datos, la función
   `send-reminder-pushes` (vía `pg_net`, HTTP asíncrono — nunca sale del proyecto de Supabase).
3. La función mira, **en la zona horaria de cada suscripción** (no la del servidor), qué
   recordatorios activos tocan ahora mismo — mismo cálculo de frecuencia (`remOccursOn` /
   `remTimes`) que usa el cliente, reimplementado en Deno con comentarios en paralelo para
   que sea fácil mantener las dos versiones sincronizadas si cambia la lógica.
4. Para cada aviso que toca, inserta una fila en `push_log` (clave: recordatorio + fecha +
   hora); si ya existía, no reenvía — así una ventana de tolerancia de 15 minutos (para
   cubrir hasta 2 pasadas de cron perdidas) no duplica avisos.
5. Envía el Web Push firmado con la clave VAPID privada. Si el navegador dio de baja el
   endpoint (410/404 — desinstaló, borró datos del sitio), la suscripción se borra sola.
6. El `service worker` (`sw.js`) recibe el push incluso con la app cerrada y muestra la
   notificación del sistema; al tocarla, enfoca la app si ya está abierta en una pestaña o
   abre una nueva.

**Secretos: nunca en un archivo.** Las claves VAPID y el secreto que autentica al cron
viven cifrados en **Supabase Vault** (ya instalado en el proyecto), leídos en el momento de
ejecutarse por `public.get_secret()`, una función con el `EXECUTE` revocado a `anon` y
`authenticated` — solo `service_role` (la Edge Function) puede llamarla. Verificado con
`has_function_privilege()` contra la base real, no simulado.

**Autenticación de la Edge Function.** Se despliega con `verify_jwt=false` — no por
descuido, sino porque implementa su propia autenticación: exige la cabecera
`x-cron-secret` con el valor guardado en Vault, y devuelve 401 si no coincide (probado
contra la función real, no solo en el código). Es exactamente el caso que la documentación
de Supabase contempla para desactivar la verificación de JWT por defecto.

**Reproducirlo en un proyecto nuevo** (el actual ya está desplegado y no necesita nada de esto):

```bash
# 1. Genera un par de claves VAPID (no necesita red, es criptografía local)
npx web-push generate-vapid-keys

# 2. Guárdalas en Vault desde el editor SQL de Supabase (nunca en un archivo del repo)
select vault.create_secret('<clave-publica>',  'vapid_public_key',  'Clave VAPID publica');
select vault.create_secret('<clave-privada>',  'vapid_private_key', 'Clave VAPID privada');
select vault.create_secret('<secreto-aleatorio>', 'cron_secret',    'Secreto para pg_cron');

# 3. Aplica las migraciones de supabase/migrations/ (incluidas las tres de push)

# 4. Despliega la función
supabase functions deploy send-reminder-pushes --no-verify-jwt

# 5. Ajusta la URL dentro de trigger_send_reminder_pushes() en la migración
#    push_cron_schedule.sql a la de tu propio proyecto antes de aplicarla

# 6. Pon tu propia clave pública en VAPID_PUBLIC_KEY, en index.html
```

**Limitaciones que conviene conocer:**

- Un aviso puede llegar hasta ~15 minutos después de su hora exacta (la ventana de
  tolerancia del paso 4), nunca antes. Para una alarma de "dar de comer a las 8:00", es un
  margen razonable; no lo es para algo que necesite precisión al minuto.
- Requiere que el usuario tenga cuenta y haya concedido el permiso de notificaciones. La
  interfaz distingue explícitamente ambos casos (banner de Inicio y bloque de Ajustes).
- En iOS, Web Push solo funciona si la app se ha **añadido a la pantalla de inicio** (modo
  PWA instalada); Safari en una pestaña normal no lo soporta, limitación del propio sistema.
- Si el usuario tiene varios dispositivos con sesión iniciada, cada uno se suscribe por
  separado y todos reciben el aviso — es el comportamiento esperado, no un fallo.

## Publicación web (Netlify)

Servir la app por HTTPS no es cosmético: **el dictado por voz y las notificaciones no
funcionan desde `file://`**, y la recuperación de contraseña de Supabase necesita una URL
a la que volver. Además, si el WebView apunta a la URL en vez de empaquetar el archivo,
puedes corregir contenido sin pasar por la revisión de Google Play.

**La app está publicada en https://cathealthtrackerapp.netlify.app** (equipo EBLDigital,
site ID `a130d2b2-264d-4ca1-9ba9-f9edebd9d2aa`), desplegada de forma manual con Netlify Drop.
Solo se publica el contenido de `dist/`: el README, `tools/` y las migraciones se quedan fuera.

**Importante: dos carpetas distintas, no las confundas.**

- **El repositorio** (este proyecto entero: `index.html`, `build.mjs`, `tools/`,
  `README.md`...). Es lo que necesitas para *generar* el sitio o para descargar las fotos
  de las razas. Se descarga con `git clone`, o como .zip desde GitHub
  (botón verde *Code → Download ZIP* en la página del repositorio).
- **`dist/`** (5-6 archivos: `index.html`, `manifest.json`, `sw.js`, `_headers`,
  `_redirects`, y `img/` si hay fotos). Es lo único que *se sube* a Netlify, y se genera
  a partir del repositorio con `node build.mjs`. Un .zip de `dist/` no sirve para ejecutar
  `tools/fetch-breed-photos.mjs`: no tiene ese archivo.

**Opción A — subida manual (arrastrar y soltar).** Con el repositorio completo descargado,
en su carpeta:

```bash
node build.mjs          # crea dist/ (funciona igual en Windows, macOS y Linux)
```

*(`bash build.sh` hace exactamente lo mismo si prefieres bash y lo tienes instalado —
en Windows necesitarías Git Bash o WSL; `node build.mjs` no depende de nada de eso.)*

Y arrastra la carpeta `dist/` (o su contenido comprimido en un .zip) al área de *Deploys*
del sitio *cathealthtrackerapp*, para conservar la misma URL. Soltarla en
[app.netlify.com/drop](https://app.netlify.com/drop) crearía un sitio nuevo cada vez.

Importante: arrastra `dist/`, **no** la carpeta del repositorio. En un despliegue manual
Netlify no ejecuta la sección `[build]` de `netlify.toml`; por eso las cabeceras y las rutas
viven en `_headers` y `_redirects`, que `build.mjs` deja dentro de `dist/` y sí se aplican.

**Opción B — conectar el repositorio.** En Netlify → *cathealthtrackerapp* → *Import from Git*,
elige este repositorio y la rama. Netlify lee `netlify.toml`, ejecuta `node build.mjs` y
despliega solo con hacer push. Es la más cómoda a medio plazo: no hay que descargar ni
arrastrar nada nunca más.

**Opción C — desde tu ordenador con la CLI**, en la carpeta del repositorio:

```bash
npm i -g netlify-cli
netlify login
netlify link --id a130d2b2-264d-4ca1-9ba9-f9edebd9d2aa
netlify deploy --build --prod
```

### Después del primer despliegue: configura Supabase

En el panel de Supabase → **Authentication → URL Configuration**:

- **Site URL**: `https://cathealthtrackerapp.netlify.app`
- **Redirect URLs**: añade `https://cathealthtrackerapp.netlify.app/**`

Sin esto, el enlace del correo de «He olvidado mi contraseña» no lleva a ninguna parte.

## Archivos

```
index.html            La app completa (sin dependencias externas salvo la fuente)
manifest.json         Manifiesto PWA
sw.js                 Service worker (caché del shell para uso sin conexión)
build.mjs             Genera dist/ con los archivos publicables + _headers y _redirects
build.sh              Lo mismo que build.mjs, para quien prefiera bash (no en Windows)
tools/                Script de descarga de las fotos de las razas
img/breeds/           Fotos de las razas (opcional, lo genera el script)
netlify.toml          Configuración de despliegue
supabase/migrations/  Esquema de la base de datos
supabase/functions/   Edge Function que envía las notificaciones push
```

## Uso

Ábrelo directamente en el navegador o sírvelo:

```bash
npx http-server -p 8080 .
# http://localhost:8080/index.html
```

Para empaquetar como app Android/iOS, apunta el WebView (Median.co u otro) a `index.html`,
igual que en TestudoTracker.

## Logo e iconos

El favicon, el icono de la cabecera y el de la pantalla de splash salen de un recorte
cuadrado (cara del gato, centrada) del logo real de la app — un boceto a lápiz sobre papel
con el texto «Cat Health Tracker». Todo va **incrustado como `data:` URI**, igual que el resto
de la app: sin archivos de imagen sueltos para esto.

- **Formato:** JPEG en vez de PNG para los tamaños grandes. Un PNG sin pérdida de un boceto
  detallado con textura de papel pesa 7-8 veces más (el de 512 px pasaba de 66 KB a 483 KB);
  a esta calidad de boceto, JPEG no se nota y ahorra bastante en un archivo que se descarga
  entero en cada visita. Los favicons de 16/32 px se quedan en PNG (más nítidos en ese tamaño)
  y con un punto extra de contraste, porque a 16 px el trazo fino del lápiz se difumina.
- **Versión "maskable"**: `manifest.json` incluye una variante con ~22 % de margen relleno
  del mismo tono de papel del propio logo, para que un marco circular o squircle de Android
  no le corte las orejas al gato.
- **Si cambias el logo**, la fuente y el recorte exacto quedan documentados (no versionados,
  por peso) en el historial de esta conversación; en resumen: recorta un cuadrado centrado en
  la cara, guarda una copia con ~22 % de margen relleno del color de fondo para la versión
  maskable, exporta ambas a varios tamaños en JPEG (o PNG para 16/32 px) y sustituye los
  `data:` URI de `<link rel="icon">`, `<link rel="apple-touch-icon">`, `.hdr-logo img`,
  `.splash-logo img` en `index.html`, y el array `icons` de `manifest.json`.

## Personalización

- **Color:** los cuatro tonos de la marca están en `:root` (`--g1` … `--g4`) al principio
  del `<style>`. El comentario de esa línea incluye los valores verdes originales de
  TestudoTracker por si quieres volver a ellos.
- **Afiliados:** la constante `AMAZON_TAG` al principio del `<script>` está vacía. Si pones
  tu etiqueta, se añade automáticamente a todos los enlaces de compra.
- **Nube:** el objeto `CLOUD` (justo antes de la sección de sincronización) tiene la URL y la
  clave publicable del proyecto de Supabase. Si lo dejas vacío, la app funciona solo en local.
- **Idiomas:** añade el código nuevo a cada entrada de `I18N`, a `LANG_FLAGS` / `LANG_CODES`
  y un botón en `#lang-menu`.

## Aviso

Herramienta informativa y de seguimiento. No sustituye el diagnóstico ni el criterio de un
veterinario colegiado. Ante cualquier señal de alarma, acude a tu veterinario.
