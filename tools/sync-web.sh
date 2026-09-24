#!/usr/bin/env bash
# Copia la app web (assets del movil) a web/ para desplegar en Netlify.
# Ejecuta antes ./tools/build-web-assets.sh si has tocado clases de Tailwind.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
A="$ROOT/PadelPulse-Movil/mobile/src/main/assets"
W="$ROOT/web"
mkdir -p "$W"
rm -rf "$W/fonts"
cp -r "$A/fonts" "$W/fonts"
cp "$A/tailwind.css" "$W/tailwind.css"
cp "$A/logo.png" "$W/logo.png"
cp "$A/code.html" "$W/index.html"

# En la web tailwind.css se cachea una semana (_headers). Si la URL no cambia,
# el navegador mezcla la pagina nueva con los estilos viejos: faltan las clases
# nuevas y, por ejemplo, el logo sale a tamaño natural, gigante. Con la version
# en la URL cada despliegue pide su propia hoja de estilos.
VER="$(grep -o "WEB_VERSION = '[^']*'" "$W/index.html" | head -1 | cut -d"'" -f2)"
sed -i "s|href=\"tailwind.css\"|href=\"tailwind.css?v=$VER\"|" "$W/index.html"
grep -q "tailwind.css?v=$VER" "$W/index.html" || { echo "❌ no se pudo versionar tailwind.css"; exit 1; }
echo "✅ web/ actualizado desde los assets de la app"
