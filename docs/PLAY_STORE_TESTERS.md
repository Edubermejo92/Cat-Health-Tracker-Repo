# Subir PadelPulse Live a Play Store y repartirlo a los testers

Objetivo: que un tester instale **la app del móvil y la del reloj** y las use
juntas. Esta guía va paso a paso, en el orden real en que hay que hacerlo.

> Los nombres de los menús de Play Console cambian cada pocos meses. Si un menú
> no se llama exactamente así, busca el texto entre comillas en el buscador de
> arriba de Play Console: te lleva igual.

---

## 0. Lo que hay que entender antes de tocar nada

Las dos apps comparten `applicationId`: **`padelpulseapp2.netlify.app`**.

Eso no es un descuido, es la forma correcta de publicar una app con versión de
reloj. Y tiene tres consecuencias que mandan sobre todo lo demás:

1. **Es UNA sola ficha en Play Console**, no dos. No crees una segunda app para
   el reloj: Play la rechazará por paquete duplicado.
2. **Las dos tienen que ir firmadas con el MISMO keystore.** Si firmas el reloj
   con otra clave, Play rechaza el artefacto.
3. **Los `versionCode` tienen que ser distintos.** Aquí van en series separadas:

   | App | `versionCode` | `versionName` |
   |---|---|---|
   | Móvil | `120` (serie 1xx) | 1.2.0 |
   | Reloj | `1120` (serie 1xxx) | 1.2.0 |

   Al sacar una versión nueva sube los dos, cada uno en su serie.

Además, la app del reloj está marcada como **compañera** (no autónoma) en su
manifest:

```xml
<meta-data android:name="com.google.android.wearable.standalone" android:value="false" />
```

Esto es lo que hace que **Play instale la app en el reloj del tester de forma
automática** cuando instala la del móvil. Es justo lo que quieres para las
pruebas. (Si algún día prefieres que se pueda instalar sola desde el reloj sin
tener el móvil, cambia ese valor a `true` y vuelve a subir.)

---

## 1. Generar los dos AAB firmados

Ver `docs/FIRMAR_Y_COMPILAR.md`. En resumen, desde Android Studio:

- Abre `PadelPulse-Movil` → **Build → Generate Signed App Bundle / APK → Android App Bundle**
- Repite con `PadelPulse-WearOS`
- Usa **el mismo `padelpulse.jks`, el mismo alias y la misma contraseña** en los dos

Te quedan dos ficheros:

```
PadelPulse-Movil/mobile/release/mobile-release.aab      ← móvil/tablet
PadelPulse-WearOS/app/release/app-release.aab           ← reloj
```

---

## 2. Activar el formato "Wear OS" en la ficha

En Play Console, dentro de la app:

1. **Probar y publicar → Configuración → Formatos** (en inglés *Advanced
   settings → Form factors*).
2. **Añadir formato → Wear OS**.
3. Play te pedirá completar la declaración de Wear OS y, más adelante,
   **capturas específicas de reloj** (mínimo 1, cuadradas o redondas).

Sin este paso, Play acepta el AAB del reloj pero no lo sirve a los relojes.

---

## 3. Crear la prueba interna con las dos apps

**Probar y publicar → Pruebas → Prueba interna → Crear nueva versión**

1. Sube **los dos AAB en la MISMA versión (release)**. Sí, los dos en la misma
   pantalla: Play detecta por el manifest cuál es de reloj
   (`android.hardware.type.watch`) y cuál de móvil, y le da a cada dispositivo
   el que le toca.
2. Comprueba en el resumen de la versión que aparecen **dos artefactos**, uno
   marcado como *Wear OS*. Si solo ves uno, es que el paso 2 no está hecho o el
   `versionCode` del reloj choca con el del móvil.
3. Notas de la versión → **Revisar versión → Iniciar lanzamiento**.

### Prueba interna vs. prueba cerrada

| | Prueba interna | Prueba cerrada |
|---|---|---|
| Testers | hasta 100 | hasta 2.000 por lista |
| Disponible en | minutos | horas (pasa revisión) |
| Requisito de 12 testers / 14 días | **no** | **sí**, si vas a producción como cuenta personal |

Para probar día a día, usa **interna**. La **cerrada** sólo la necesitas si tu
cuenta es personal (creada después de nov-2023) y tienes que cumplir el
requisito de *12 testers durante 14 días seguidos* antes de poder publicar en
producción.

---

## 4. Dar de alta a los testers

1. En **Prueba interna → Testers**, crea una lista de correo electrónico y
   añade los emails.
2. **Deben ser cuentas de Google reales** (las que usan en el móvil). Un alias
   de empresa que no sea cuenta Google no vale.
3. Copia el **enlace de participación** (*opt-in URL*) y mándaselo.

### Lo que tiene que hacer el tester (mándale esto tal cual)

1. Abre el enlace **con la misma cuenta de Google que usas en el móvil** y pulsa
   *Convertirme en tester*.
2. Pulsa **"Descargar en Google Play"** e instala **PadelPulse Live** en el móvil.
3. Asegúrate de que el reloj está emparejado con ese móvil y con **la misma
   cuenta de Google**.
4. La app del reloj se instala sola en unos minutos. Si no aparece:
   Play Store en el reloj → **Aplicaciones de tu teléfono** → PadelPulse Live →
   *Instalar*.

> **Este es el fallo número uno.** Si el reloj está con otra cuenta de Google, o
> el tester aceptó la invitación con una cuenta distinta a la del móvil, la app
> del reloj no aparece nunca. No es un bug de la app.

---

## 5. Comprobar que la sincronización funciona

Con las dos apps instaladas:

1. Móvil: **Ajustes → Sincronización con reloj** → elige quién manda
   (*Móvil* o *Reloj*). Apunta el código de 4 dígitos.
2. Reloj: abre PadelPulse → *Empezar* → idioma → modo → **teclea ese código**
   (o pulsa *Vincular sin código*).
3. El móvil enseña "Reloj vinculado" y el reloj pasa solo al marcador.
4. Suma un punto en el dispositivo que manda: tiene que aparecer en el otro en
   menos de un segundo.
5. En el reloj, el pulso y las calorías empiezan a llegar al móvil solos.

Si la píldora del móvil pone **SIN RELOJ**, es que Play Services no ve el reloj:
comprueba que el reloj está emparejado y que la app Wear OS / Galaxy Wearable
está abierta al menos una vez.

---

## 6. Errores frecuentes al subir

| Mensaje de Play | Qué pasa | Solución |
|---|---|---|
| *"Ya se ha usado el código de versión 120"* | Repites `versionCode` | Sube el número en `build.gradle.kts` |
| *"El App Bundle no está firmado con la clave correcta"* | Has firmado con otro keystore | Firma los dos con `padelpulse.jks` |
| *"Se debe usar un ID de aplicación diferente"* | Has creado una segunda app para el reloj | Borra esa ficha; todo va en una |
| El reloj no recibe la app | `standalone` mal o cuentas distintas | Ver puntos 0 y 4 |
| *"Faltan capturas de Wear OS"* | Formato Wear añadido sin material gráfico | Sube 1-8 capturas de reloj |

---

## 7. Cuando pases a producción

- Sube los dos AAB otra vez, con `versionCode` nuevos, en la pista de producción.
- La ficha de Wear OS necesita sus propias capturas y descripción corta.
- Recuerda actualizar **las dos apps a la vez**: hablan la versión 3 del
  protocolo (`docs/PROTOCOLO_SINCRONIZACION.md`) y, si una se queda atrás, la
  otra lo detecta y avisa al usuario, pero no se sincronizan.

---

## Sobre el segundo Netlify para Wear OS

**No hace falta, y no serviría de nada.** La app del reloj es Kotlin/Compose
nativo, no una web dentro de un WebView como la del móvil: no hay nada que
alojar. Y un reloj no tiene navegador útil para abrir una web.

Lo que sí tiene sentido, y ya está preparado en `web/`:

- `web/` es **la misma app del móvil** lista para desplegar en
  `padelpulselive.netlify.app` (arrastra la carpeta a Netlify).
- Así el sitio y la app comparten código: `tools/sync-web.sh` copia los assets.
- Si quieres, añade en esa web una sección "Descarga la app" con el enlace de
  participación de la prueba interna. Eso sí ayuda a los testers.
