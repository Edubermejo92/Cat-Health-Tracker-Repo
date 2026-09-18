# Cuentas de usuario con Supabase

Las dos apps arrancan por la cuenta. El historial deja de vivir solo en el
movil: se guarda en la nube y vuelve solo cuando el usuario entra desde otro
telefono.

## Proyecto

**Ojo: en esta cuenta de Supabase hay tres proyectos.** PadelPulse solo
usa el suyo, y nada de esto toca los otros dos:

| Proyecto | Referencia | Es de |
|----------|------------|-------|
| **PadelPulse Live** | `fdlcdzlvvxqhzougcjwd` | **esta app** |
| Cat Health Tracker | `vwdnipvfjqeeezuhjstk` | otro proyecto, no tocar |
| Edubermejo92's Project | `vjsowjjdeufpeiwmtnmr` | parado, sin usar |

- **Nombre:** PadelPulse Live
- **Referencia:** `fdlcdzlvvxqhzougcjwd`
- **Region:** eu-central-1
- **URL:** `https://fdlcdzlvvxqhzougcjwd.supabase.co`
- **Clave publicable:** `sb_publishable_Q7D-EMj-MW4df-VzAbSNmg_eumYPvtM`

La clave publicable va dentro del HTML a proposito: es la que Supabase
esta pensado para publicar. Lo que protege los datos no es la clave, son
las politicas RLS de abajo. La clave de servicio **no** esta en el codigo
y no debe estarlo nunca.

## Tablas

Las cuatro tienen RLS activado y politicas contra `auth.uid()`, asi que
cada usuario solo ve y escribe lo suyo aunque alguien use la clave
publicable a mano.

| Tabla      | Para que sirve                            | Clave unica            |
|------------|-------------------------------------------|------------------------|
| `profiles` | Nombre visible del usuario                | `id` (= `auth.uid()`)  |
| `matches`  | Partidos terminados, con salud incluida   | `(user_id, local_id)`  |
| `players`  | Agenda: amigos, telefono y pareja habitual | `(user_id, name)` |
| `settings` | Ajustes (idioma, tema, punto de oro...)   | `user_id`              |

`matches` lleva ademas un indice por `(user_id, played_at desc)`, que es
justo como lo pide la app al bajar el historial.

Las cuatro cuelgan de `auth.users` con `ON DELETE CASCADE`: si se borra una
cuenta, se van con ella sus partidos, sus ajustes y su perfil.

El `(user_id, local_id)` unico es el que hace que subir dos veces el mismo
partido no lo duplique: la app manda
`POST /rest/v1/matches?on_conflict=user_id,local_id` con
`Prefer: resolution=merge-duplicates`.

### La agenda

`players` es la agenda de gente con la que juegas. Quien juega un partido
entra solo -nadie va a teclear una lista de amigos a mano- y luego se le
puede poner telefono, correo y notas.

Hay un indice unico parcial, `players_una_pareja_habitual`, que garantiza
**una sola pareja habitual por usuario**: si dos moviles marcan parejas
distintas, gana la marcada mas tarde y la base impide que queden dos.

Las fichas se mezclan por fecha de modificacion: corregir un telefono en el
movil no lo deshace la nube, ni al reves.

`profiles` guarda ademas tus propios datos: nombre, telefono y club.

### Ajustes

El idioma, el tema y las reglas viajan con la cuenta. Se bajan **la primera
vez** que un movil sincroniza esa cuenta; a partir de ahi manda lo local y
se sube en cada sincronizacion. Es a proposito: si alguien cambia el tema en
el movil, no queremos que la nube se lo deshaga al siguiente sync.

## Que hace cada app

### Movil

Arranca en la pantalla de cuenta salvo que ya haya sesion guardada o el
usuario haya elegido antes jugar sin cuenta. Tres caminos:

1. **Entrar** — correo y contraseña contra `/auth/v1/token?grant_type=password`.
2. **Crear cuenta** — `/auth/v1/signup`, con el nombre visible en
   `user_metadata.display_name`.
3. **Jugar sin cuenta** — se marca en el movil y no se vuelve a preguntar.
   Se puede cambiar de idea desde Ajustes › Cuenta.

Al entrar sube los partidos que aun no esten en la nube y baja los que
falten. Al terminar un partido lo respalda en segundo plano, sin cortar la
pantalla de final. Sin cobertura no pasa nada: se juega igual y se
sincroniza a la siguiente.

En **Ajustes › Cuenta** se ve quien esta dentro, cuando fue la ultima
sincronizacion, y hay botones para sincronizar ahora y para salir.

### Reloj

El reloj **no pide contraseña**. Teclear un correo y una contraseña en una
pantalla de 45 mm es una tortura, y Wear OS recomienda delegar el inicio de
sesion en el movil. Lo que hace el reloj es:

- Esperar la sesion del movil, que llega por `/padel/account` en el Data
  Layer: canal cifrado del sistema entre dos dispositivos ya emparejados,
  no sale a internet.
- Guardarla para mostrar de quien es el reloj.
- Ofrecer **JUGAR SIN CUENTA** a quien no quiera nada de esto.

Cuando llega la sesion, la pantalla pasa sola al marcador. Si el movil
cierra sesion, manda `{"action":"signout"}` y el reloj se entera.

El mensaje se procesa aunque la app del reloj este cerrada: el
`WearListenerService` lo guarda igual, para que la sesion ya este puesta
cuando el usuario levante la muñeca.

## El enlace del correo tiene que volver a la app

Este es el fallo que se vio en pruebas: el usuario se registra, le llega el
correo *"Confirm your email address"*, pulsa el enlace y **no pasa nada**.

La causa es que Supabase, si no le dices otra cosa, manda al usuario a la
**Site URL** del proyecto, que por defecto es `http://localhost:3000`. En un
movil eso no existe.

La app ya manda la direccion de vuelta correcta (`padelpulse://auth` desde la
app, la del sitio desde la web) en el registro, en el reenvio y en la
recuperacion de contraseña. Pero **Supabase rechaza cualquier direccion que no
tenga dada de alta** y, cuando la rechaza, usa la de por defecto. Asi que hay
que ponerla:

**Authentication › URL Configuration**

| Campo | Valor |
|-------|-------|
| Site URL | la direccion de la web, o `padelpulse://auth` si no hay web |
| Additional Redirect URLs | `padelpulse://auth` (una por linea, y tambien la de la web) |

### La alternativa: no pedir confirmacion

Para un grupo de testers, lo mas comodo es quitar el paso entero:

**Authentication › Providers › Email** → desactivar **Confirm email**.

Asi el registro entra directo, sin correo, sin enlace y sin nada que pueda
fallar. La app lo detecta sola: si el servidor devuelve sesion, entra; si
devuelve que falta confirmar, enseña la pantalla del correo. No hay que tocar
codigo ni volver a subir nada a Play.

Tiene un coste: sin confirmar, cualquiera puede registrarse con un correo que
no es suyo. Para un marcador de padel no es grave; para algo con datos
sensibles, si.

## Confirmacion de correo

Si en el panel de Supabase esta activada la confirmacion de correo, al
crear la cuenta no viene sesion todavia y la app avisa de que hay que
mirar el buzon. Con la confirmacion desactivada se entra directamente.
Se cambia en **Authentication › Providers › Email**.

## Comprobaciones hechas contra el proyecto real

Con usuarios de prueba creados y borrados en el momento (la base quedo
vacia, comprobado despues):

| Prueba | Resultado |
|--------|-----------|
| Un usuario ve sus partidos | ✅ solo los suyos |
| Un usuario ve los de otro | ✅ no ve ninguno |
| Un usuario escribe un partido a nombre de otro | ✅ bloqueado |
| Un usuario modifica los partidos de otro | ✅ 0 filas |
| Sin sesion (clave publicable a pelo) lee partidos | ✅ no ve nada |
| Sin sesion escribe | ✅ bloqueado |
| Sesion sin usuario (token caducado) lee | ✅ no ve nada |
| Corregir un telefono duplica la ficha | ✅ no, la actualiza |
| Marcar una segunda pareja habitual | ✅ bloqueado por el indice |
| Otro usuario ve o cambia tu agenda | ✅ no ve nada, 0 filas cambiadas |
| Sin sesion lee la agenda | ✅ no ve nada |
| El perfil se crea solo al registrarse | ✅ lo hace el disparador |

El analizador de seguridad de Supabase no da ningun aviso.

---

# Por que no hay "entrar con Google"

Se probo y se quito. Montarlo exige crear credenciales en Google Cloud,
configurar una pantalla de consentimiento y dar de alta a cada tester a mano
mientras la app no este verificada por Google. Para un grupo de gente que solo
quiere apuntar el marcador de un partido, es mucho tramite a cambio de
ahorrarse teclear una contraseña.

Con correo y contraseña se entra igual de bien, y **sin cuenta tambien se
juega**: solo se pierde el historial.

Si algun dia interesa recuperarlo, lo que hacia falta era:

- Un ID de cliente de OAuth 2.0 de tipo *Aplicacion web* en Google Cloud, con
  `https://fdlcdzlvvxqhzougcjwd.supabase.co/auth/v1/callback` como URI de
  redireccionamiento.
- Activar el proveedor en **Authentication › Providers › Google**.
- Abrir la pantalla de Google **fuera del WebView**: Google rechaza el inicio
  de sesion dentro de uno con `disallowed_useragent`.

La maquinaria de volver a la app por `padelpulse://auth` sigue en su sitio,
porque la usa la recuperacion de contraseña.

---

---

# El proyecto se pausa solo

Supabase **pausa los proyectos del plan gratuito** tras unos días sin
actividad. Mientras está pausado no funciona nada: ni entrar, ni registrarse,
ni sincronizar. La app dirá "Sin conexion con el servidor", que es cierto pero
despista, porque el móvil sí tiene internet.

Se reactiva desde el panel: **Project Settings › General › Restore project**.
Tarda un par de minutos.

Si va a haber testers usándolo de verdad, conviene pasar al plan de pago para
que no se pause; si no, hay que acordarse de entrar cada pocos días.

---

# Contraseñas

## No se pueden ver. Ni tú, ni yo, ni Supabase

Supabase guarda un **hash bcrypt**, no la contraseña. Es un cálculo de una sola
dirección: sirve para comprobar si la que escribes coincide, pero no se puede
deshacer para recuperar la original. Esto no es una limitación que convenga
sortear —es lo que hace que una filtración de la base de datos no regale las
contraseñas de nadie—, así que **no hay ninguna pantalla que muestre la
contraseña de un usuario**, y no debe haberla.

Lo que sí existe es ponerse una nueva.

## He perdido la contraseña

En la pantalla de entrar, debajo de "Crear una cuenta nueva".

1. El usuario escribe su correo y pulsa el enlace.
2. La app llama a `POST /auth/v1/recover` con la dirección de vuelta
   (`padelpulse://auth` en la app, la del sitio en la web).
3. Supabase manda el correo con un enlace **de un solo uso**.
4. Al abrirlo, la app detecta que la sesión viene marcada como `recovery` y,
   en vez de entrar sin más, pide la contraseña nueva dos veces.
5. Se guarda con `PUT /auth/v1/user` y a partir de ahí la vieja no vale.

**Un correo que no existe recibe la misma respuesta que uno que sí.** Es a
propósito: si contestáramos distinto, cualquiera podría averiguar qué correos
están dados de alta probando uno a uno.

## Requisito en Supabase

Para que salga el correo hace falta que `padelpulse://auth` y la dirección de
la web estén en **Authentication › URL Configuration › Additional Redirect
URLs**. Sin eso el correo sale, pero el enlace no vuelve a la app.

### El límite de correos es un problema real

Comprobado contra el servidor: tras **un solo registro**, la siguiente
petición de recuperación devolvió `429 over_email_send_rate_limit`. El correo
integrado de Supabase deja muy pocos envíos por hora y está pensado solo para
desarrollo.

Con testers de verdad esto se nota enseguida: uno se registra, el siguiente
pide recuperar la contraseña y **no le llega nada**. Antes de repartir la app,
conecta un SMTP propio en **Authentication › Emails › SMTP Settings**
(Resend, Brevo, SendGrid o el que prefieras; todos tienen plan gratuito
suficiente para esto).

## Comprobado

| Prueba | Resultado |
|--------|-----------|
| El enlace sale al entrar, no al crear cuenta | ✅ |
| Sin correo escrito | ✅ avisa, no manda nada |
| Con correo válido | ✅ se manda y sale "mira tu correo" |
| Correo no registrado | ✅ misma pantalla, **no se manda nada** |
| Abrir el enlace | ✅ pide contraseña nueva, no entra sin más |
| Las dos no coinciden / muy corta | ✅ avisa |
| Guardar la nueva | ✅ entra, y **la vieja deja de valer** |

---

# Los correos que recibe el usuario

Supabase manda por defecto unos correos en inglés, firmados *"Supabase Auth"*
y sin ninguna relación con PadelPulse. En `docs/emails/` hay dos plantillas
listas para sustituirlos:

| Fichero | Dónde va | Asunto sugerido |
|---------|----------|-----------------|
| `confirmar-cuenta.html` | Authentication › Emails › **Confirm signup** | `Confirma tu cuenta · PadelPulse Live` |
| `recuperar-contrasena.html` | Authentication › Emails › **Reset password** | `Tu nueva contraseña · PadelPulse Live` |

Se pegan tal cual en el cuadro de texto de la plantilla, sustituyendo lo que
haya. El asunto se cambia en el campo de arriba.

## Llaman al usuario por su nombre

La app guarda el nombre al registrarse, y la plantilla lo usa:

```
{{ if .Data.display_name }}Hola, {{ .Data.display_name }}{{ else }}Hola{{ end }}
```

`.Data` es lo que se manda en `data` al registrarse —aquí, `display_name`— y
queda en `auth.raw_user_meta_data`. El `if` está para quien se registró sin
poner nombre: en vez de un "Hola," cojo, saluda sin más.

## Por qué están hechas así

Los clientes de correo no son navegadores. No entienden flex, ni grid, ni
hojas de estilo externas, y muchos bloquean las imágenes. Por eso:

- **Todo en tablas** y con los estilos escritos en línea.
- **Ninguna imagen.** La cabecera es tipográfica, así que se ve igual aunque
  el cliente bloquee la descarga de imágenes —que es lo normal la primera vez
  que alguien te escribe—.
- **Botón "a prueba de balas"**: una celda de tabla con `bgcolor`, no un `div`
  con fondo, que Outlook no pinta.
- **Texto de vista previa** oculto, para que en la bandeja se lea algo mejor
  que el principio del correo.
- **El enlace en texto** debajo del botón, por si el botón no funciona.

Comprobado renderizando las dos, con nombre y sin él: 600 px de ancho, sin
desbordes y sin ninguna petición externa.

## Acuérdate del SMTP

Con el correo integrado de Supabase estos correos salen con remitente de
Supabase y **con el límite de envíos por hora**. Al conectar un SMTP propio
(Authentication › Emails › SMTP Settings) puedes poner tu propio remitente,
que es lo que hace que el correo no parezca de un tercero.
