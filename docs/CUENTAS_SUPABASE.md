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

# Entrar con Google

El boton "Continuar con Google" ya esta en las dos apps y en la web, pero
**no funcionara hasta que actives el proveedor**. Eso hay que hacerlo a
mano en dos paneles, porque las credenciales de Google son tuyas y solo
las puedes crear tu.

## 1. Google Cloud Console

<https://console.cloud.google.com/apis/credentials>

- Crea un **ID de cliente de OAuth 2.0**, tipo **Aplicacion web**.
- En **URI de redireccionamiento autorizados** pon exactamente esto:

  ```
  https://fdlcdzlvvxqhzougcjwd.supabase.co/auth/v1/callback
  ```

- Guarda el **ID de cliente** y el **secreto**.

Es tipo "Aplicacion web" aunque sea para una app de movil: el intercambio
lo hace Supabase en su servidor, la app solo abre el navegador.

La primera vez te pedira configurar la **pantalla de consentimiento**
(nombre de la app, correo de contacto y logo). Mientras este en modo
"Prueba" solo entraran los correos que apuntes ahi, que para los testers
va bien; para abrirlo a todo el mundo hay que publicarla.

## 2. Supabase

**Authentication › Providers › Google**: activalo y pega el ID de cliente
y el secreto.

**Authentication › URL Configuration › Additional Redirect URLs**: añade
las dos direcciones de vuelta, una por linea:

```
padelpulse://auth
https://padelpulselive.netlify.app
```

La primera es la de las apps; la segunda, la de la web. Sin ellas Supabase
rechaza la vuelta y el usuario se queda mirando el navegador.

## Como funciona

Google **no permite** iniciar sesion dentro de un WebView: devuelve
`disallowed_useragent` y no hay manera. Por eso la app abre la pantalla de
Google en el navegador del sistema y espera a que vuelva por su propio
enlace, `padelpulse://auth`, declarado en el manifiesto.

La vuelta usa **PKCE**: la app genera un secreto, manda solo su huella, y
al volver canjea el codigo presentando el secreto. Aunque otra app
interceptara el enlace de vuelta, sin ese secreto el codigo no le sirve.
La huella se calcula en Kotlin con `MessageDigest` y no con
`crypto.subtle`, porque en un WebView servido desde `file://` esa API del
navegador no siempre esta disponible.

El reloj no cambia: sigue recibiendo la sesion ya hecha desde el movil,
venga de Google o del correo.

En la web es mas simple: se redirige, se vuelve con la sesion detras de la
almohadilla, y la app la recoge y limpia la barra de direcciones -que ahi
va el token-.

## Comprobado

| Prueba | Resultado |
|--------|-----------|
| El boton sale en la pantalla de entrada | ✅ y el correo sigue debajo |
| En la app usa el puente nativo, no redirige el WebView | ✅ |
| La sesion de Google entra y se guarda | ✅ nombre y correo de Google |
| Llega al reloj al vincular | ✅ como cualquier otra sesion |
| Si el usuario cancela | ✅ mensaje, y no entra |
| En la web redirige a Google y vuelve | ✅ con `redirect_to` correcto |
| La web recoge la sesion y limpia la url | ✅ |
