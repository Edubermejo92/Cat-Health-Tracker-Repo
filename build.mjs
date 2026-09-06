#!/usr/bin/env node
/**
 * Prepara la carpeta que se publica en Netlify. Equivalente a build.sh,
 * pero en Node puro para que funcione igual en Windows (PowerShell/cmd),
 * macOS y Linux sin depender de bash.
 *
 *   node build.mjs
 *
 * Deja fuera el README y las migraciones de Supabase: al sitio solo suben
 * los archivos que forman la app. Los ajustes de cabeceras y rutas van en
 * _headers y _redirects (y no en netlify.toml) para que funcionen igual
 * tanto si arrastras la carpeta a mano como si Netlify la construye desde git.
 */

import { existsSync, rmSync, mkdirSync, cpSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const OUT = process.argv[2] || 'dist';

rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });

for (const f of ['index.html', 'manifest.json', 'sw.js']) {
  cpSync(f, join(OUT, f));
}

// Fotos de las razas, si están descargadas. Son opcionales: sin ellas la app
// muestra el emoji de cada raza. Se bajan con:
//   node tools/fetch-breed-photos.mjs
function countJpg(dir) {
  let n = 0;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, entry.name);
    if (entry.isDirectory()) n += countJpg(p);
    else if (entry.name.toLowerCase().endsWith('.jpg')) n++;
  }
  return n;
}
if (existsSync('img') && statSync('img').isDirectory()) {
  cpSync('img', join(OUT, 'img'), { recursive: true });
  console.log(`Incluidas ${countJpg('img')} fotos de razas.`);
} else {
  console.log('Sin fotos de razas (opcional). Para añadirlas: node tools/fetch-breed-photos.mjs');
}

// El HTML y el service worker no se cachean: así un redespliegue llega a los
// usuarios en la siguiente carga, en vez de dejarles una versión vieja.
writeFileSync(join(OUT, '_headers'), `/index.html
  Cache-Control: public, max-age=0, must-revalidate

/sw.js
  Cache-Control: public, max-age=0, must-revalidate

/manifest.json
  Cache-Control: public, max-age=3600

/img/*
  Cache-Control: public, max-age=31536000, immutable

/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  X-Frame-Options: SAMEORIGIN
  Permissions-Policy: microphone=(self), camera=(self), geolocation=()
`);

// App de una sola página: cualquier ruta que no sea un archivo sirve la app.
writeFileSync(join(OUT, '_redirects'), '/*    /index.html    200\n');

console.log(`\nCarpeta lista en ${OUT}/:`);
for (const f of readdirSync(OUT)) console.log('  ' + f);
