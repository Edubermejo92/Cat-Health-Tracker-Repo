# Prompt para Google Stitch — diseño de PadelPulse Live en Wear OS

Copia todo el bloque en Stitch. Si te deja elegir dispositivo, usa un lienzo
personalizado de 454 × 454. Si el resultado sale demasiado cargado, pide las
pantallas por partes ("solo la página 1 Marcador", luego el resto).

```
Diseña la interfaz de una app de Wear OS para smartwatch REDONDO (lienzo 454 × 454 px, pantalla circular) llamada "PadelPulse Live": un marcador de pádel para llevar en la muñeca durante el partido. Estilo Material 3 para Wear OS, tema oscuro con fondo negro puro (#000000, pantallas OLED), color de acento verde neón #00FD87, texto principal blanco, texto secundario gris claro, avisos en ámbar #FFB020 y peligro en rojo suave #FF5A5A. Tipografía Lexend (Bold/Black para cifras y títulos). Todo el texto de la interfaz en español.

REGLAS DE CALIDAD DE WEAR OS (obligatorias en todas las pantallas)
- Todo el contenido dentro del círculo visible: márgenes laterales de ~10 % del ancho y superior/inferior de ~18 %; nada cortado por el borde redondo.
- Hora del sistema arriba (TimeText curvado) sin que ningún contenido quede debajo de ella (salvo en la pantalla del logo).
- Objetivos táctiles de 48 × 48 dp como mínimo, aunque el icono visible sea más pequeño.
- La app debe aguantar el tamaño de letra del sistema al 130 % y al 200 % sin cortar texto: los botones crecen en altura y el texto pasa a varias líneas; nada de "…" en etiquetas importantes.
- Las pantallas con lista usan columna con escalado en los bordes (ScalingLazyColumn), barra de desplazamiento visible a la derecha (PositionIndicator curvo) y se desplazan con la corona/bisel giratorio.
- Navegación horizontal entre las 4 páginas principales con indicador de página (4 puntos abajo, el activo en verde). Deslizar a la derecha desde la primera página cierra la app (gesto del sistema), así que la página 1 no tiene elementos que se arrastren en horizontal.
- Contraste mínimo 4,5:1 para texto; ningún dato importante solo con color (acompañar con icono o texto).
- Pocos elementos por pantalla, textos cortos, sin publicidad, sin teclado: los nombres se dictan por voz o se eligen de listas.
- Mientras se juega existe una "actividad en curso" (icono del partido en la esfera); muéstralo como detalle en la página 1.
- Arranque corto: como máximo 2 pantallas antes del marcador.

══════════════════════════════════════
PANTALLAS ANTES DEL MARCADOR
══════════════════════════════════════

A. LOGO (siempre, ~1 segundo, sin interacción obligatoria)
- Logo "PadelPulse Live" centrado, sobre negro, con una aparición suave (fundido + ligero zoom).
- Solo si ya está sincronizado con el móvil: pastilla pequeña verde "SINCRONIZADO" debajo del logo.
- Botón opcional "EMPEZAR" en verde (por si no se quiere esperar); pasa sola al marcador.
- Sin hora encima del logo para que respire; sin textos largos.

B. CUENTA (solo la primera vez y solo si no hay sesión; lista con barra de desplazamiento)
- Logo pequeño arriba y título "TU CUENTA" en verde.
- Texto corto: "En el reloj no se escriben contraseñas. Inicia sesión en el móvil y el reloj entra solo."
- Botón principal verde: "INICIAR SESIÓN EN EL MÓVIL" (abre la app en el teléfono).
- Botón secundario destacado: "JUGAR SIN CUENTA" (se juega igual; la cuenta solo guarda el historial).
- Pastilla de estado del móvil en gris (SOLO RELOJ / BUSCANDO PADELPULSE…), nunca en rojo.
- Variante cuando llega la sesión: "SESIÓN INICIADA" + nombre del usuario en grande, y pasa sola al marcador en 1 segundo.

C. CONTINUAR PARTIDO (solo si quedó un partido a medias; lista con barra de desplazamiento)
- Logo pequeño y título "PARTIDO ANTERIOR".
- Tarjeta con las parejas ("EDU · ROJOS") y el resultado grande: sets – sets · juegos – juegos (ej. "1–0 · 3–2").
- Botón verde "CONTINUAR" y botón rojo suave "NUEVA PARTIDA" (guarda el anterior en el historial como "sin terminar" y empieza de cero).

D. PERMISOS DEL SISTEMA (solo la primera vez; diálogos de Android, no los diseñes, solo deja constancia)
- "Actividad física" (pasos y actividad en curso) y "Notificaciones" (partido visible en la esfera).

Flujo:
- Primera vez sin sesión: Permisos → Cuenta → Logo → Marcador.
- Primera vez con sesión ya iniciada en el móvil: Permisos → Logo → Marcador.
- Siguientes veces (con o sin sesión): Logo → Marcador.
- Con un partido a medias: Logo → Continuar partido → Marcador.
- No se pregunta el idioma (español por defecto, se cambia en Ajustes) ni el pulso al arrancar (se activa en Ajustes).

══════════════════════════════════════
LAS 4 PÁGINAS PRINCIPALES (se pasan deslizando)
══════════════════════════════════════

PÁGINA 1 — MARCADOR (la principal, sin scroll)
- Fila superior: botón micrófono (árbitro por voz), logo "PadelPulse Live" con un pequeño punto de estado (verde = sincronizado con el móvil, gris = solo reloj) que lleva a Ajustes, botón altavoz (voz que canta los puntos) y botón escudo 🛡 (bloquea la detección automática de ejercicio del reloj; verde activo, ámbar ⚠ si el reloj no lo permite).
- Debajo, a izquierda y derecha, los nombres de las dos parejas (cada pareja puede mostrar dos jugadores, uno encima del otro). Tocar un nombre hace que esa pareja saque: la que saca aparece en VERDE con una pelota amarilla delante.
- Centro: tanteo grande de cada pareja (0, 15, 30, 40, AD), el de quien saca en verde con brillo suave. Entre ambos, una mini pista vista desde arriba con la pelota en el cuadro de saque (tocarla alterna el saque). Una pastilla pequeña encima del tanteo para estados especiales: IGUALES, VENTAJA, PUNTO DE ORO, TIE-BREAK.
- Las dos mitades de la pantalla son zonas táctiles: tocar la mitad izquierda suma punto a la pareja A y la derecha a la B (con un destello breve del lado tocado).
- Fila de control bajo el tanteo: botón "−" (restar punto) a cada lado y, en medio, una pastilla con JUEGOS de A · SETS (ej. 1-0) · JUEGOS de B, que al tocarla abre la corrección de juegos y sets.
- Aros finos pegados al borde: juegos del set en curso llenándose a cada lado y puntos de sets ganados.
- Línea de estado pequeña: sets jugados (6-4 · 3-6), cronómetro del partido y pulso ♥ si hay. Nunca un "sin móvil" en rojo: jugar sin móvil es normal.
- Banda inferior pegada al borde redondo: botón ancho "FALTA" (pasa a "2º SAQUE" en ámbar tras la primera falta).

PÁGINA 2 — SALUD (sin scroll)
- Arco de pulso alrededor del borde (sobre 200 ppm), partido arriba para no tapar la hora.
- Cronómetro arriba, icono ♥, pulso grande en el centro con "PPM" debajo; "–" si no hay dato (nunca inventar cifras).
- Abajo, dos datos: 🔥 KCAL y 🏃 KM.

PÁGINA 3 — AJUSTES (lista con scroll y barra lateral)
Título "AJUSTES" y grupos con rótulo pequeño en verde:
- PARTIDO: "Nuevo partido" (estilo peligro, subtítulo "Guarda este y empieza otro") y "Deshacer · Último punto".
- NOMBRE DE EQUIPOS: Pareja A, Jugador A1, Jugador A2, Pareja B, Jugador B1, Jugador B2 (cada fila con su valor actual debajo en verde).
- COLOR DE LA APP: fila de 6–8 círculos de color, el elegido con borde blanco.
- SETS: selector "Al mejor de" 1 / 3 / 5 (botones grandes, el elegido en verde), interruptor "Súper tie-break", fila "Corregir sets 1 – 0".
- JUEGOS: interruptor "Punto de oro", fila "Corregir juegos 3 – 2".
- VOZ E IDIOMA: "Idioma 🇪🇸 Español", interruptor "Voz del marcador", "Árbitro por voz", control de volumen con − y +.
- PANTALLA: brillo con − y +.
- MÁS: "Pulso ♥" (explica el uso antes de pedir permiso), "Invita a un amigo", "Política de privacidad" (obligatoria por Play) y "Salir de la app" (pide un segundo toque).
- Al final, versión de la app en gris.

PÁGINA 4 — SINCRONIZACIÓN (lista con scroll y barra lateral)
- Título "SINCRONIZACIÓN" y pastilla de estado: SINCRONIZADO (verde), BUSCANDO PADELPULSE… (gris), FALTA LA APP EN EL MÓVIL (ámbar) o SOLO RELOJ (gris). Nunca rojo.
- Tarjeta con tres comprobaciones con icono ✓ / ✕ / …: "Enlace con Galaxy S24" (Bluetooth del sistema), "PadelPulse está en el móvil", "PadelPulse del móvil responde"; si algo falla, una frase corta con qué hacer (p. ej. "Activa el Bluetooth y abre la app Galaxy Wearable o Wear OS del móvil").
- Botones: "BUSCAR MÓVIL", "INSTALAR EN EL MÓVIL" (ámbar, solo si falta la app: abre la Play Store del teléfono), "SINCRONIZAR AHORA" (verde, subtítulo "Marcador, idioma, nombres e historial").
- CUENTA: sin sesión, texto "En el reloj no se escriben contraseñas" y botón "INICIAR SESIÓN EN EL MÓVIL"; con sesión, "Sesión iniciada" con nombre y correo.
- Botón "HISTORIAL · Partidos por día".
- Nota pequeña al final: "Idioma, nombres, color y reglas se sincronizan solos con el móvil."

══════════════════════════════════════
SUBPANTALLAS (lista, barra de desplazamiento y botón de volver)
══════════════════════════════════════
1. Confirmar nuevo partido: "¿NUEVO PARTIDO?", parejas y resultado actual, texto "Este partido se guarda en el historial", botones "SÍ, GUARDAR Y EMPEZAR" (peligro) y "NO, SEGUIR JUGANDO" (verde).
2. Corregir marcador: dos ruedas numéricas (pickers) para juegos A – B y otras dos para sets A – B, botones OK y CANCELAR.
3. Historial por días: resumen (jugados · ganados · %), cabeceras HOY / AYER / "LUN 12 OCT" y tarjetas por partido con parejas, resultado en sets grande, juegos, hora, duración, kcal y la etiqueta "sin terminar" si aplica; las ganadas con borde verde. Sin cuenta: candado y "Inicia sesión en el móvil".
4. Idioma: lista con bandera y nombre, el actual resaltado.
5. Editar nombre: botón "Dictar 🎙", nombres recientes y atajos (YO, RIVAL, PAREJA A…).
6. Aviso del pulso (se abre desde Ajustes > Pulso; requisito de la política de salud de Google Play): icono ♥ en verde, título "PULSO EN EL MARCADOR", texto "PadelPulse Live puede leer tu pulso del sensor del reloj para mostrártelo mientras juegas. No es un uso médico. Se queda en tus dispositivos y solo se guarda en tu cuenta si sincronizas.", botones "PERMITIR" (verde), "AHORA NO" (gris) y enlace "Política de privacidad".
7. Política de privacidad: título "POLÍTICA DE PRIVACIDAD", resumen corto de datos de salud, código QR a la política completa (padelpulselive.com/politica-de-privacidad) y botón "‹ VOLVER".
8. Fin de partido: trofeo 🏆, "¡GANA EDU!", resultado en sets grande visto desde el ganador, sets jugados (6-4 · 6-3), duración y kcal, botón verde "NUEVO PARTIDO".

══════════════════════════════════════
ENTREGA
══════════════════════════════════════
Todas en formato redondo 454 × 454:
- Pantallas de arranque: A (Logo), B (Cuenta y su variante "Sesión iniciada"), C (Continuar partido).
- Las 4 páginas principales: Marcador, Salud, Ajustes (varias capturas bajando la lista) y Sincronización (con estado SINCRONIZADO y con estado FALTA LA APP EN EL MÓVIL).
- Las 8 subpantallas.
- Variantes con la letra del sistema al 200 % de: Cuenta, Continuar partido, Marcador y Ajustes, para comprobar que nada se corta.
```
