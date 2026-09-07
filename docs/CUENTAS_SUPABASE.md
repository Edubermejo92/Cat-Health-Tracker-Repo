# Cuentas de usuario con Supabase

Las dos apps arrancan por la cuenta. El historial deja de vivir solo en el
movil: se guarda en la nube y vuelve solo cuando el usuario entra desde otro
telefono.

## Proyecto

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
| `matches`  | Partidos terminados                       | `(user_id, local_id)`  |
| `players`  | Jugadores y parejas                       | `(user_id, name)`      |
| `settings` | Ajustes (idioma, tema, punto de oro...)   | `user_id`              |

`matches` lleva ademas un indice por `(user_id, played_at desc)`, que es
justo como lo pide la app al bajar el historial.

El `(user_id, local_id)` unico es el que hace que subir dos veces el mismo
partido no lo duplique: la app manda
`POST /rest/v1/matches?on_conflict=user_id,local_id` con
`Prefer: resolution=merge-duplicates`.

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
