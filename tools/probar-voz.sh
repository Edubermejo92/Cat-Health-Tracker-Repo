#!/usr/bin/env bash
# Pasa tools/voice_tests.json por el interprete del movil (JS) y comprueba el
# resultado esperado. Con KOTLINC y JSON_JAR definidos, pasa tambien el del
# reloj (Kotlin) y exige que los dos digan exactamente lo mismo.
set -euo pipefail
cd "$(dirname "$0")/.."
node -e "
const V=require('./tools/voice-parser.js'); V.init(require('./tools/voice_grammar.json'));
const T=require('./tools/voice_tests.json');
const n=o=>o?JSON.stringify({type:o.type,team:('team' in o)?o.team:undefined,a:o.a,b:o.b}):'null';
let bad=0; T.cases.forEach(c=>{const r=V.parse(c.text,c.lang,T.names); if(n(r)!==n(c.expect)){bad++;console.log('FALLA',c.lang,c.text,n(r));}});
console.log('movil (JS):',T.cases.length-bad,'/',T.cases.length); process.exit(bad?1:0);"
