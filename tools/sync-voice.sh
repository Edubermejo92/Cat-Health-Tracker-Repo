#!/usr/bin/env bash
# Copia la gramatica y el interprete de voz a las dos apps.
#   tools/voice_grammar.json -> reloj: res/raw/voice_grammar.json
#                            -> movil: code.html (entre /*VOICE_GRAMMAR*/ ... /*END_VOICE_GRAMMAR*/)
#   tools/voice-parser.js    -> movil: code.html (entre /*VOICE_PARSER*/ ... /*END_VOICE_PARSER*/)
# El reloj tiene su copia del interprete en Kotlin (voice/VoiceParser.kt):
# pasa tools/probar-voz.sh para comprobar que los dos siguen diciendo lo mismo.
set -euo pipefail
cd "$(dirname "$0")/.."
cp tools/voice_grammar.json PadelPulse-WearOS/app/src/main/res/raw/voice_grammar.json
python3 - <<'PY'
import json, re
p = 'PadelPulse-Movil/mobile/src/main/assets/code.html'
s = open(p, encoding='utf-8').read()
g = json.dumps(json.load(open('tools/voice_grammar.json', encoding='utf-8')), ensure_ascii=False, separators=(',', ':'))
js = open('tools/voice-parser.js', encoding='utf-8').read()
def put(s, tag, body):
    a, b = '/*' + tag + '*/', '/*END_' + tag + '*/'
    i, j = s.index(a) + len(a), s.index(b)
    return s[:i] + '\n' + body + '\n' + s[j:]
s = put(s, 'VOICE_GRAMMAR', 'const VOICE_GRAMMAR = ' + g + ';')
s = put(s, 'VOICE_PARSER', js.strip())
open(p, 'w', encoding='utf-8').write(s)
print('voz copiada a code.html y a res/raw')
PY
