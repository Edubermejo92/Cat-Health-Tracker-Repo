/* Cat Health Tracker — service worker mínimo.
   Cachea el shell de la app para que funcione sin conexión.
   Sube CACHE_VERSION (a la par de APP_VERSION en index.html) cada vez
   que publiques una versión nueva. */
const CACHE_VERSION = 'cht-1.2.0';
const ASSETS = ['index.html', 'manifest.json'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE_VERSION).then(c => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE_VERSION).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;   // fuentes y CDNs: red directa

  // Las fotos de las razas no cambian nunca: primero la copia local, y solo
  // se piden a la red la primera vez. Asi la pestana de Razas va instantanea
  // y sigue funcionando sin conexion.
  if (url.pathname.startsWith('/img/')) {
    e.respondWith(
      caches.match(req).then(hit => hit || fetch(req).then(res => {
        const copy = res.clone();
        caches.open(CACHE_VERSION).then(c => c.put(req, copy)).catch(() => {});
        return res;
      }).catch(() => hit))
    );
    return;
  }

  e.respondWith(
    fetch(req)
      .then(res => {
        const copy = res.clone();
        caches.open(CACHE_VERSION).then(c => c.put(req, copy)).catch(() => {});
        return res;
      })
      .catch(() => caches.match(req).then(r => r || caches.match('index.html')))
  );
});

/* Notificaciones push en segundo plano.
   Las manda supabase/functions/send-reminder-pushes cada vez que a un
   recordatorio le toca sonar. Llegan aquí incluso con la app cerrada:
   es justo lo que un setInterval dentro de la pestaña no puede lograr. */
self.addEventListener('push', e => {
  let data = {};
  try { data = e.data ? e.data.json() : {}; } catch (err) { data = {}; }
  const title = data.title || 'Cat Health Tracker';
  e.waitUntil(self.registration.showNotification(title, {
    body: data.body || '',
    tag: data.tag,     // mismo tag = mismo aviso: si llega dos veces, se reemplaza en vez de apilarse
    renotify: false,
    data: { tag: data.tag || '' }
  }));
});

self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
      for (const c of list) if ('focus' in c) return c.focus();
      if (self.clients.openWindow) return self.clients.openWindow('./');
    })
  );
});
