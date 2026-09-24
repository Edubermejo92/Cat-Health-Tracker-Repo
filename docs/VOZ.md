# Árbitro por voz

Canta los puntos y se suman, en el móvil o en el reloj. Los dos entienden exactamente lo
mismo: comparten la gramática (`tools/voice_grammar.json`) y el intérprete (`tools/voice-parser.js`
en el móvil, `voice/VoiceParser.kt` en el reloj, copia paso a paso).

- **Móvil**: botón del micrófono de la cabecera (modo árbitro, se queda escuchando).
- **Reloj**: toca la fila del logo, arriba del marcador (🎙 en rojo = escuchando), o Controles → Árbitro por voz.
- Solo escucha uno a la vez: al encender uno, el otro se apaga solo.
- Se escucha en el **idioma de la app**, no en el del teléfono.

## Qué se puede decir

| Qué | Cómo se nombra a la pareja |
|---|---|
| Punto | nombre de la pareja, **nombre de cualquiera de sus dos jugadores**, "pareja A/B", izquierda/derecha, saque/resto |
| Marcador | dos puntos seguidos ("quince treinta"), uno + "iguales" ("treinta iguales"), "iguales" solo = 40-40 |
| Ventaja, juego, set | la palabra + la pareja ("ventaja rojos", "juego para Edu") |
| Falta, doble falta, deshacer, nueva partida, cambio de saque, "cómo vamos" | la palabra sola |

Si no queda claro de qué pareja es, la app pregunta "¿Para quién?" en vez de adivinar.

## Ejemplos por idioma

| Idioma | Punto | Marcador | Iguales | Ventaja | Juego | Deshacer |
|---|---|---|---|---|---|---|
| Español | punto para Juan | quince treinta | cuarenta iguales | ventaja rojos | juego Edu | deshacer |
| English | point Juan | fifteen thirty | deuce | advantage team b | game left | undo |
| Italiano | punto Juan | quindici trenta | parità | vantaggio squadra b | gioco Edu | annulla |
| Français | point pour Juan | quinze trente | égalité | avantage équipe b | jeu Edu | annuler |
| Deutsch | Punkt für Juan | fünfzehn dreißig | Einstand | Vorteil Team B | Spiel Edu | rückgängig |
| Suomi | piste Juan | viisitoista kolmekymmentä | tasan | etu joukkue b | peli Edu | peruuta |
| Português | ponto para Juan | quinze trinta | iguais | vantagem dupla b | jogo Edu | desfazer |
| Nederlands | punt voor Juan | vijftien dertig | deuce | voordeel team b | game Edu | ongedaan |
| Svenska | poäng till Juan | femton trettio | lika | fördel lag b | game Edu | ångra |
| Русский | очко команда а | пятнадцать тридцать | ровно | больше команда б | гейм команда а | отмена |
| 中文 | A队得分 | 十五比三十 | 平分 | B队占先 | A队这局 | 撤销 |
| 日本語 | Aチームのポイント | フィフティーン サーティ | デュース | アドバンテージ Bチーム | ゲーム Aチーム | 取り消し |
| 한국어 (solo reloj) | 에이팀 포인트 | 피프틴 서티 | 듀스 | 어드밴티지 B팀 | 게임 에이팀 | 취소 |
| العربية | نقطة للفريق أ | خمسة عشر ثلاثين | تعادل | أفضلية الفريق ب | شوط الفريق أ | تراجع |

## Cambiar o añadir palabras

1. Edita `tools/voice_grammar.json`.
2. `./tools/sync-voice.sh` lo copia al reloj (`res/raw`) y a `code.html`.
3. `./tools/probar-voz.sh` pasa las frases de `tools/voice_tests.json`; añade allí la frase nueva con su resultado esperado.
4. `./tools/antes-de-compilar.sh` falla si alguna de las dos apps tiene una gramática distinta.
