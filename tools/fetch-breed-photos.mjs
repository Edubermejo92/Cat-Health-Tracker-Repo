#!/usr/bin/env node
/**
 * Descarga una foto real para cada raza desde Wikimedia Commons.
 *
 *   node tools/fetch-breed-photos.mjs
 *
 * Por qué Wikimedia y no una búsqueda de imágenes: la inmensa mayoría de las
 * fotos de gatos que hay por internet tienen derechos de autor, y publicar una
 * app con ellas es motivo de retirada en Google Play. Commons expone la licencia
 * de cada archivo, así que este script solo se queda con las que permiten uso
 * comercial y guarda el autor para poder citarlo, que es lo que exigen las
 * licencias Creative Commons.
 *
 * No necesita instalar nada: usa el fetch de Node 18+ y pide a Wikimedia la
 * miniatura ya redimensionada, así que tampoco hace falta procesar imágenes.
 *
 * Resultado:
 *   img/breeds/{id}.jpg      una foto por raza
 *   img/breeds/credits.json  autor, licencia y enlace al original
 */

import { writeFile, mkdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const OUT_DIR = join(ROOT, 'img', 'breeds');
const WIDTH = 640;                     // ancho de la miniatura que pedimos a Commons
const UA = 'CatHealthTracker/1.0 (https://cathealthtrackerapp.netlify.app)';

/* Licencias que permiten uso comercial. Todo lo demás se descarta:
   más vale quedarse sin foto que publicar una que no se puede usar. */
const ALLOWED = [
  /^cc0/i, /^public domain/i, /^pd/i, /^cc[- ]by(?![- ]nc)/i, /^cc[- ]by[- ]sa/i, /^attribution/i
];
/* Estas exigen citar al autor; el script guarda el crédito para todas igualmente. */

const BREEDS = {
  europeo:'European Shorthair',            british_sh:'British Shorthair',
  british_lh:'British Longhair',           chartreux:'Chartreux',
  noruego:'Norwegian Forest cat',          siberiano:'Siberian cat',
  neva:'Neva Masquerade',                  angora:'Turkish Angora',
  van:'Turkish Van',                       ruso_azul:'Russian Blue',
  manx:'Manx cat',                         cornish_rex:'Cornish Rex',
  devon_rex:'Devon Rex',                   scottish_fold:'Scottish Fold',
  birmano_sag:'Birman',                    peterbald:'Peterbald',
  siames:'Siamese cat',                    thai:'Thai cat',
  persa:'Persian cat',                     himalayo:'Himalayan cat',
  burmes:'Burmese cat',                    korat:'Korat',
  japones_bobtail:'Japanese Bobtail',      singapura:'Singapura cat',
  khao_manee:'Khao Manee',                 oriental:'Oriental Shorthair',
  balines:'Balinese cat',                  kurilian:'Kurilian Bobtail',
  tonkines:'Tonkinese cat',                abisinio:'Abyssinian cat',
  somali:'Somali cat',                     sokoke:'Sokoke',
  arabian_mau:'Arabian Mau',               egipcio_mau:'Egyptian Mau',
  maine_coon:'Maine Coon',                 american_sh:'American Shorthair',
  american_curl:'American Curl',           american_bobtail:'American Bobtail',
  ragdoll:'Ragdoll',                       ragamuffin:'Ragamuffin cat',
  bombay:'Bombay cat',                     ocicat:'Ocicat',
  sphynx:'Sphynx cat',                     munchkin:'Munchkin cat',
  selkirk_rex:'Selkirk Rex',               laperm:'LaPerm',
  exotico:'Exotic Shorthair',              snowshoe:'Snowshoe cat',
  australian_mist:'Australian Mist',       bengala:'Bengal cat',
  savannah:'Savannah cat',                 toyger:'Toyger',
  chausie:'Chausie',                       serengeti:'Serengeti cat'
};

const stripHtml = s => String(s || '').replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim();
const sleep = ms => new Promise(r => setTimeout(r, ms));

async function getJson(url){
  const res = await fetch(url, { headers: { 'User-Agent': UA, 'Accept': 'application/json' } });
  if(!res.ok) throw new Error('HTTP ' + res.status);
  return res.json();
}

/** Imagen principal del artículo de la raza en la Wikipedia en inglés. */
async function leadImageTitle(article){
  const url = 'https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2'
            + '&prop=pageimages&piprop=original&redirects=1&titles=' + encodeURIComponent(article);
  const data = await getJson(url);
  const page = data?.query?.pages?.[0];
  if(!page || page.missing) return null;
  const original = page.original?.source;
  if(!original) return null;
  // De la URL del archivo sacamos su nombre en Commons
  const name = decodeURIComponent(original.split('/').pop());
  return 'File:' + name;
}

/** Licencia, autor y miniatura ya redimensionada. */
async function fileInfo(fileTitle){
  const url = 'https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2'
            + '&prop=imageinfo&iiprop=url|extmetadata|mime&iiurlwidth=' + WIDTH
            + '&titles=' + encodeURIComponent(fileTitle);
  const data = await getJson(url);
  const info = data?.query?.pages?.[0]?.imageinfo?.[0];
  if(!info) return null;
  const meta = info.extmetadata || {};
  return {
    mime: info.mime,
    thumb: info.thumburl || info.url,
    descriptionUrl: info.descriptionurl,
    license: stripHtml(meta.LicenseShortName?.value) || stripHtml(meta.License?.value) || '',
    author: stripHtml(meta.Artist?.value) || stripHtml(meta.Credit?.value) || 'Wikimedia Commons'
  };
}

const licenceOk = l => ALLOWED.some(re => re.test(l));

async function main(){
  await mkdir(OUT_DIR, { recursive: true });
  const credits = {};
  const skipped = [];
  const ids = Object.keys(BREEDS);
  let done = 0;

  for(const id of ids){
    const article = BREEDS[id];
    process.stdout.write(`[${String(++done).padStart(2)}/${ids.length}] ${article.padEnd(24)} `);
    try {
      const fileTitle = await leadImageTitle(article);
      if(!fileTitle){ console.log('sin imagen en el artículo'); skipped.push([id, 'sin imagen']); continue; }

      const info = await fileInfo(fileTitle);
      if(!info){ console.log('sin datos del archivo'); skipped.push([id, 'sin datos']); continue; }

      if(!/^image\/(jpeg|png|webp)$/.test(info.mime || '')){
        console.log('formato no válido (' + info.mime + ')'); skipped.push([id, 'formato ' + info.mime]); continue;
      }
      if(!licenceOk(info.license)){
        console.log('licencia no apta: ' + (info.license || '?')); skipped.push([id, 'licencia ' + info.license]); continue;
      }

      const img = await fetch(info.thumb, { headers: { 'User-Agent': UA } });
      if(!img.ok){ console.log('descarga HTTP ' + img.status); skipped.push([id, 'HTTP ' + img.status]); continue; }
      const buf = Buffer.from(await img.arrayBuffer());
      await writeFile(join(OUT_DIR, id + '.jpg'), buf);

      credits[id] = { author: info.author, license: info.license, source: info.descriptionUrl };
      console.log(`ok  ${(buf.length/1024).toFixed(0).padStart(4)} KB  ${info.license}`);
    } catch(err){
      console.log('error: ' + err.message);
      skipped.push([id, err.message]);
    }
    await sleep(250);                    // no atosigar a la API de Wikimedia
  }

  await writeFile(join(OUT_DIR, 'credits.json'), JSON.stringify(credits, null, 1));

  console.log(`\nDescargadas ${Object.keys(credits).length} de ${ids.length} fotos en img/breeds/`);
  if(skipped.length){
    console.log('\nSin foto (la app mostrará el emoji en su lugar):');
    for(const [id, why] of skipped) console.log('  - ' + id + ': ' + why);
    console.log('\nPuedes poner una foto propia en img/breeds/<id>.jpg y añadir su');
    console.log('autor y licencia a img/breeds/credits.json.');
  }
  console.log('\nAhora ejecuta:  bash build.sh    y arrastra dist/ a Netlify.');
}

main().catch(err => { console.error(err); process.exit(1); });
