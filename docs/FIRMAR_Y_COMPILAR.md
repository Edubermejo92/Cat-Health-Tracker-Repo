# Compilar y firmar las dos apps en Android Studio

Las dos carpetas son **proyectos Gradle independientes**. Se abren por separado:

```
PadelPulse-Movil/     ← app de móvil, tablet y ordenador (módulo :mobile)
PadelPulse-WearOS/    ← app de reloj (módulo :app)
```

En Android Studio: **File → Open** y selecciona la carpeta del proyecto (la que
tiene `settings.gradle.kts`), no la carpeta padre.

---

## 1. Antes de compilar: `local.properties`

Ese fichero no viaja en el repositorio porque contiene la ruta del SDK de tu
ordenador. Android Studio lo crea solo al abrir el proyecto. Si no lo hace,
créalo a mano en la raíz de cada proyecto:

```properties
sdk.dir=C\:\\Users\\34620\\AppData\\Local\\Android\\Sdk
```

---

## 2. La firma: el mismo keystore en las dos

**Esto es obligatorio.** Las dos apps comparten `applicationId`, así que Google
Play exige que estén firmadas con la misma clave. Si firmas el reloj con otra,
Play rechaza el artefacto.

Cada proyecto lee la firma de un `keystore.properties` en su raíz:

```properties
storeFile=C:/Users/34620/AndroidStudioProjects/PADELPULSELIVEWEAR_OS/padelpulse.jks
storePassword=padelpulse123
keyAlias=padelpulse
keyPassword=padelpulse123
```

- Ese fichero está en `.gitignore` **a propósito**: lleva contraseñas y no debe
  subirse nunca a GitHub. Tienes una plantilla en `keystore.properties.example`.
- Si mueves el `.jks`, actualiza sólo la línea `storeFile`.
- Si el fichero no existe, el proyecto compila igual pero **sin firmar**, y
  tendrás que usar el diálogo de firma de Android Studio a mano.

> Cambiar de keystore después de publicar **no tiene vuelta atrás** salvo que
> tengas activada la firma de apps de Play y pidas un reinicio de clave a
> Google. Guarda `padelpulse.jks` y sus contraseñas en sitio seguro y con copia.

---

## 3. Generar los AAB firmados

Para cada proyecto, por separado:

1. **Build → Generate Signed App Bundle / APK…**
2. Elige **Android App Bundle**
3. Selecciona `padelpulse.jks`, alias `padelpulse`, y las contraseñas
4. Variante: **release**
5. Finalizar

Salida:

```
PadelPulse-Movil/mobile/release/mobile-release.aab
PadelPulse-WearOS/app/release/app-release.aab
```

También vale por línea de comandos:

```bash
cd PadelPulse-Movil  && ./gradlew bundleRelease
cd PadelPulse-WearOS && ./gradlew bundleRelease
```

(En Windows: `gradlew.bat bundleRelease`.)

---

## 4. Probar en dispositivo antes de subir

Para probar la sincronización de verdad hacen falta **móvil y reloj emparejados
con la misma cuenta de Google**:

```bash
# Móvil conectado por USB
cd PadelPulse-Movil && ./gradlew installRelease

# Reloj: por Wi-Fi (Depuración por Wi-Fi en Opciones de desarrollador del reloj)
adb connect 192.168.1.XX:5555
cd PadelPulse-WearOS && ./gradlew installRelease
```

Las builds de `debug` también van firmadas con el keystore de release (así la
sincronización funciona entre las dos sin líos de firmas distintas).

---

## 5. Al tocar la interfaz del móvil

La app del móvil es un WebView que carga `assets/code.html`. Los estilos son
**Tailwind precompilado** en `assets/tailwind.css` (la app no descarga nada de
internet: en pista no siempre hay cobertura).

Si añades o cambias clases de Tailwind en `code.html`, hay que regenerar el CSS:

```bash
./tools/build-web-assets.sh
```

Si no lo haces, la clase nueva simplemente no tendrá estilos. Es el error más
fácil de cometer en este proyecto.

Para actualizar también la web de Netlify con los mismos cambios:

```bash
./tools/sync-web.sh     # deja web/ listo para desplegar
```

---

## 6. Al tocar la sincronización

Los mensajes entre las dos apps están definidos en
`docs/PROTOCOLO_SINCRONIZACION.md` y hay **tres implementaciones que tienen que
ir a la par**:

| Lado | Fichero |
|---|---|
| Móvil (JS) | `PadelPulse-Movil/mobile/src/main/assets/code.html` → objeto `PPSync` |
| Móvil (Kotlin) | `PadelPulse-Movil/mobile/src/main/java/.../sync/` |
| Reloj (Kotlin) | `PadelPulse-WearOS/app/src/main/java/.../sync/` |

Si cambias un mensaje, sube `SyncProtocol.VERSION` / `PP_PROTO` en los tres y
sube `versionCode` en las dos apps. Cada app avisa al usuario si detecta que la
otra habla una versión distinta del protocolo.

---

## 7. Estructura del repositorio

```
PadelPulse-Movil/     proyecto Android del móvil
PadelPulse-WearOS/    proyecto Android del reloj
web/                  la misma app del móvil, lista para Netlify
tools/                scripts de compilación de assets web
docs/                 protocolo, firma y guía de Play Store
```

---

## 7. Receta rápida: generar los dos AAB

Los pasos, de principio a fin, para sacar los dos artefactos que se suben a
Play. **Hay que hacerlo con los dos: comparten ficha y el protocolo de
sincronización cambia en ambos lados a la vez.**

### Antes de empezar

1. Descomprime los dos ZIP en carpetas **separadas**. No metas una dentro de la
   otra: son dos proyectos Gradle independientes y Android Studio se lía.
2. Copia tu `padelpulse.jks` donde lo tengas y crea un `keystore.properties` en
   la raíz de **cada** proyecto (mira el punto 2 de este documento).
3. Comprueba que los `versionCode` son mayores que los ya publicados. En esta
   entrega: **móvil 516** y **reloj 5160**.

### App del móvil

1. **File → Open** → selecciona la carpeta `PadelPulse-Movil` (la que tiene
   `settings.gradle.kts`).
2. Espera a que termine el *Gradle sync* de abajo a la derecha. Si pide
   actualizar el plugin de Android o Gradle, **di que no**: el proyecto está
   probado con las versiones que trae.
3. **Build → Generate Signed App Bundle / APK…**
4. **Android App Bundle** → *Next*.
5. *Key store path*: tu `padelpulse.jks`. Contraseña, alias `padelpulse` y su
   contraseña. Marca *Remember passwords* si quieres.
6. *Next* → variante **release** → **Create**.
7. Al terminar, abajo a la derecha sale un aviso con enlace **locate**. El
   fichero está en:

   ```
   PadelPulse-Movil/mobile/release/mobile-release.aab
   ```

### App del reloj

Lo mismo, pero abriendo `PadelPulse-WearOS`, y el resultado es:

```
PadelPulse-WearOS/app/release/app-release.aab
```

**Usa el mismo keystore que en el móvil.** Si firmas con otro, Play rechaza el
artefacto: las dos apps comparten `applicationId`.

### En Play Console

Los dos AAB van a la **misma versión**, no a dos versiones distintas:

1. **Pruebas → Prueba interna** (o la pista que uses) → *Crear versión*.
2. Sube **los dos ficheros** a esa misma versión.
3. Play los coloca solo en su formato: el de `versionCode` 5160 como Wear OS y
   el 516 como teléfono. Si solo subes uno, los testers se quedan con una app
   nueva y otra vieja, y **la sincronización deja de funcionar**.
4. Notas de la versión → *Guardar* → *Revisar versión* → *Lanzar*.

### Si algo falla

| Síntoma | Qué pasa |
|---|---|
| `INSTALL_FAILED_OLDER_SDK` | Estás instalando el AAB del móvil en el reloj, o al revés |
| Play rechaza por firma | Los dos AAB no están firmados con el mismo keystore |
| "Ya existe una versión con este código" | Sube el `versionCode` en `build.gradle.kts` |
| El reloj no se sincroniza tras actualizar | Solo has subido un AAB; el protocolo tiene que ir a la par |
| Compila sin firmar | Falta `keystore.properties` en la raíz de ese proyecto |

### Por línea de comandos

Si prefieres no usar el diálogo (lee la firma de `keystore.properties`):

```bash
cd PadelPulse-Movil  && ./gradlew bundleRelease
cd PadelPulse-WearOS && ./gradlew bundleRelease
```

En Windows, `gradlew.bat bundleRelease`.
