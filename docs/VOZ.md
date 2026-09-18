# La voz: escuchar y que se oiga

Dos cosas distintas que se confunden con facilidad: **el micrófono**, que
escucha al árbitro, y **la voz de la app**, que canta el marcador.

## Modo árbitro (el micrófono)

El botón del micrófono está en la cabecera del marcador, al lado del estado
del reloj.

Antes escuchaba **una sola frase** por pulsación, lo que en un partido no
sirve: nadie va a sacar el móvil del bolsillo antes de cada punto. Y no
marcaba estado, así que el indicador "en vivo" que ya existía en la cabecera
no se encendía nunca.

Ahora el micrófono se queda abierto y se vuelve a abrir solo después de cada
frase, con **700 ms de respiro** para no grabarse a sí misma la voz de la app
cantando el punto.

En la cabecera se ve en qué punto está:

| Indicador | Qué pasa |
|-----------|----------|
| 🎙️ ESCUCHANDO, parpadeando | El micrófono está abierto |
| El texto de lo que va oyendo | Va reconociendo la frase |
| 📶 UN MOMENTO | Está entendiendo lo dicho |

Que se vea lo que va oyendo es lo que evita la duda de "¿me está cogiendo o
estoy hablando a la nada?".

### Lo que no se cuenta

Que entre punto y punto no se oiga nada es lo normal, así que los silencios y
los "no te he entendido" **no molestan con avisos**: el micrófono se vuelve a
abrir sin decir nada. Solo se avisa de lo que de verdad importa, como que
falte el permiso de micrófono.

### El idioma

Se escucha en el idioma de la app, no en el del teléfono. Parece obvio y no lo
era: antes se usaba el del sistema, así que un móvil en inglés no entendía
"punto para nosotros".

## La voz de la app

### Por el motor de Android, no por el del navegador

La interfaz del móvil es un WebView, y `speechSynthesis` del navegador ahí es
irregular: a veces no hay voces cargadas, a veces no suena, y **no deja
controlar el volumen**. Había un puente nativo para hablar pero la app no lo
usaba. Ahora sí, y el camino del navegador se queda solo para la versión web.

### El volumen

En **Ajustes › Audio › Volumen de la voz**, de 0 a 100 %. Al soltar el mando
se oye una frase de prueba, para ajustarlo sin salir a la pista.

Hay una trampa que conviene entender: **el volumen del TTS es relativo al del
teléfono**. Por muy alto que se pida, si el móvil está a la mitad se oye a la
mitad. Por eso está el botón **"Móvil al máximo"**, que sube el volumen
multimedia del aparato; es lo que de verdad se nota al otro lado de la pista.

La voz sale por el canal **multimedia** (`USAGE_MEDIA`), que es el que el
usuario sube con los botones del lateral y el que no se silencia solo.
