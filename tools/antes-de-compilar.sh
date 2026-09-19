#!/usr/bin/env bash
#
# Comprueba que la copia de trabajo esta lista para generar los AAB.
#
# Existe porque dos entregas seguidas salieron con el Kotlin nuevo y los assets
# viejos: el code.html se habia quedado atras y nadie se entera hasta que la app
# esta en manos de los testers, porque compila perfectamente.
#
# Uso:  ./tools/antes-de-compilar.sh
#
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
A="$ROOT/PadelPulse-Movil/mobile/src/main/assets"
FALLOS=0
AVISOS=0

rojo()  { printf '  \033[31mFALLO\033[0m  %s\n' "$1"; FALLOS=$((FALLOS+1)); }
ambar() { printf '  \033[33mAVISO\033[0m  %s\n' "$1"; AVISOS=$((AVISOS+1)); }
verde() { printf '  \033[32mOK\033[0m     %s\n' "$1"; }

echo "Comprobando la copia de trabajo..."
echo

# ── 1. Los assets del movil son los que tocan ────────────────────────────────
if [ ! -f "$A/code.html" ]; then
    rojo "no existe mobile/src/main/assets/code.html"
else
    # Ojo: "Google Play" es legitimo. Lo que no puede estar es el login.
    # 'goToAuth' contiene "oAuth", asi que nada de buscar oauth suelto.
    n_google=$(grep -ciE 'con google|with google|GoogleAuth|signInWithGoogle|accounts\.google' "$A/code.html" || true)
    if [ "$n_google" -gt 0 ]; then
        rojo "code.html todavia tiene 'entrar con Google' ($n_google): son los assets VIEJOS"
    else
        verde "code.html no tiene rastro de 'entrar con Google'"
    fi
    if grep -qE 'askAI|AI_PROVIDERS|aiKey' "$A/code.html"; then
        rojo "code.html todavia tiene el arbitro IA: son los assets VIEJOS"
    else
        verde "code.html no tiene rastro del arbitro IA"
    fi

    for marca in syncRev showWheel marcadorCantado APP_EN_PRUEBAS; do
        if grep -q "$marca" "$A/code.html"; then
            verde "code.html tiene $marca"
        else
            rojo "code.html NO tiene $marca: son los assets VIEJOS"
        fi
    done
fi

# ── 2. Nada de sobra en assets ───────────────────────────────────────────────
# Descomprimir un ZIP encima reemplaza lo que coincide, pero nunca borra lo que
# sobra. Esos restos son la senal de que la carpeta no se limpio.
for sobra in index.html netlify.toml _headers watch_code.html; do
    if [ -e "$A/$sobra" ]; then
        rojo "sobra $sobra en los assets del movil: borra la carpeta y vuelve a descomprimir"
    fi
done
if [ -d "$ROOT/PadelPulse-WearOS/app/src/main/assets" ]; then
    rojo "el reloj no debe tener carpeta assets, y la tiene"
else
    verde "el reloj no tiene assets, como debe ser"
fi

# ── 3. Las versiones cuadran entre si ────────────────────────────────────────
vm=$(grep -oE 'versionCode = [0-9]+'  "$ROOT/PadelPulse-Movil/mobile/build.gradle.kts" | grep -oE '[0-9]+')
vw=$(grep -oE 'versionCode = [0-9]+'  "$ROOT/PadelPulse-WearOS/app/build.gradle.kts"   | grep -oE '[0-9]+')
nm=$(grep -oE 'versionName = "[^"]+"' "$ROOT/PadelPulse-Movil/mobile/build.gradle.kts" | cut -d'"' -f2)
nw=$(grep -oE 'versionName = "[^"]+"' "$ROOT/PadelPulse-WearOS/app/build.gradle.kts"   | cut -d'"' -f2)
web=$(grep -oE "WEB_VERSION = '[^']+'" "$A/code.html" 2>/dev/null | cut -d"'" -f2)

[ "$vw" = "$((vm * 10))" ] \
    && verde "versionCode: movil $vm, reloj $vw" \
    || rojo  "el versionCode del reloj ($vw) deberia ser el del movil por diez ($((vm * 10)))"
[ "$nm" = "$nw" ] \
    && verde "versionName $nm en las dos apps" \
    || rojo  "versionName distinto: movil $nm, reloj $nw"
[ "$web" = "$nm" ] \
    && verde "WEB_VERSION $web coincide" \
    || rojo  "WEB_VERSION ($web) no coincide con versionName ($nm)"

for f in "PadelPulse-Movil/mobile/src/main/java/padelpulseapp2/netlify/app/MainActivity.kt" \
         "PadelPulse-WearOS/app/src/main/java/padelpulseapp2/netlify/app/MainActivity.kt"; do
    av=$(grep -oE 'APP_VERSION = "[^"]+"' "$ROOT/$f" | cut -d'"' -f2)
    [ "$av" = "$nm" ] || rojo "APP_VERSION ($av) no coincide con $nm en $(basename $(dirname $(dirname $(dirname $(dirname $(dirname "$f"))))))"
done

# ── 4. El protocolo, igual en los dos lados ──────────────────────────────────
pm=$(grep -oE 'PROTO_VERSION = [0-9]+' "$ROOT/PadelPulse-Movil/mobile/src/main/java/padelpulseapp2/netlify/app/sync/SyncProtocol.kt" 2>/dev/null | grep -oE '[0-9]+')
pw=$(grep -oE 'PROTO_VERSION = [0-9]+' "$ROOT/PadelPulse-WearOS/app/src/main/java/padelpulseapp2/netlify/app/sync/SyncProtocol.kt"   2>/dev/null | grep -oE '[0-9]+')
if [ -n "${pm:-}" ] && [ -n "${pw:-}" ]; then
    [ "$pm" = "$pw" ] && verde "protocolo v$pm en las dos apps" || rojo "protocolo distinto: movil v$pm, reloj v$pw"
fi

for m in "PadelPulse-Movil/mobile/src/main/AndroidManifest.xml" \
         "PadelPulse-WearOS/app/src/main/AndroidManifest.xml"; do
    grep -q 'pathPrefix="/padel"' "$ROOT/$m" \
        || rojo "falta pathPrefix=\"/padel\" en $m"
done
grep -q 'wearable.standalone' "$ROOT/PadelPulse-WearOS/app/src/main/AndroidManifest.xml" \
  && grep -A1 'wearable.standalone' "$ROOT/PadelPulse-WearOS/app/src/main/AndroidManifest.xml" | grep -q 'value="false"' \
  && verde "standalone=false: Play instalara sola la app en el reloj" \
  || rojo "standalone deberia ser false"
grep -q 'MatchOngoingService' "$ROOT/PadelPulse-WearOS/app/src/main/AndroidManifest.xml" \
  && verde "MatchOngoingService declarado (calidad Wear OS)" \
  || rojo "falta MatchOngoingService en el manifiesto del reloj"

# ── 5. Tailwind al dia ───────────────────────────────────────────────────────
if [ -f "$A/tailwind.css" ] && [ "$A/code.html" -nt "$A/tailwind.css" ]; then
    ambar "code.html es mas nuevo que tailwind.css: pasa ./tools/build-web-assets.sh"
fi

# ── 6. La firma ──────────────────────────────────────────────────────────────
for pr in PadelPulse-Movil PadelPulse-WearOS; do
    [ -f "$ROOT/$pr/keystore.properties" ] \
        || ambar "sin $pr/keystore.properties: el AAB saldria SIN FIRMAR"
done

echo
if [ "$FALLOS" -gt 0 ]; then
    echo "  $FALLOS fallo(s). NO compiles hasta arreglarlos."
    exit 1
fi
[ "$AVISOS" -gt 0 ] && echo "  Listo, con $AVISOS aviso(s)." || echo "  Todo en orden. Puedes compilar."
exit 0
