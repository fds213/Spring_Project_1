// Caches the app shell so the installed app opens even when the server is briefly unreachable.
// Bump CACHE when shell files change, or users keep seeing the old version.
const CACHE = 'ieum-shell-v139';
const SHELL = [
  './',
  'index.html',
  'simulator.html',
  'site.html',
  'trial.html',
  'inquiry.html',
  'css/common.css',
  'css/site.css',
  'css/notice.css',
  'js/common.js',
  'js/simulator.js',
  'js/site.js',
  'js/trial.js',
  'js/inquiry.js',
  'js/password-toggle.js',
  'js/select.js',
  'js/install.js',
  'js/ops.js',
  'js/admin-inquiry.js',
  'js/admin-stats.js',
  'js/admin-company.js',
  'js/hr.js',
  'js/notice.js',
  'js/attendance.js',
  'js/schedule.js',
  'js/approval.js',
  'js/org-admin.js',
  'js/seat.js',
  'img/hero-app.png',
  'img/logo-mark.png',
  'img/logo-wordmark.png',
  'icons/favicon.png',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/icon-maskable-512.png',
  'icons/apple-touch-icon.png',
  'manifest.json',
];

// cache: 'reload' fetches every shell file from the server, past the browser's own HTTP cache. The dev server
// (python -m http.server) sends no Cache-Control, so without it a new CACHE version could be filled with the
// copies the browser already had, and bumping the version wouldn't bring the new files
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL.map(u => new Request(u, { cache: 'reload' })))));
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(Promise.all([
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))),
    self.clients.claim(),
  ]));
});

// Network first, cache as fallback: real data (JSP pages, AJAX) must never come from a stale cache.
// The cache is used when the network fails outright, and also when the server answers with an error (5xx): a
// tunnel or proxy that can't reach the server for a moment answers 502, and one missing script then leaves the
// app a blank page (seen 2026-10-29). The error response is passed on only when nothing is cached for it.
// ignoreSearch lets index.html?preview (and the like) fall back to the cached index.html.
const fromCache = req => caches.match(req, { ignoreSearch: true });
// our own files are always checked against the server (no-cache = revalidate, a 304 when unchanged), so a new
// version shows up on a plain reload without Ctrl+F5; other origins (the font CDN) keep the browser's default
const fresh = req => (new URL(req.url).origin === self.location.origin ? new Request(req, { cache: 'no-cache' }) : req);
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  e.respondWith(
    fetch(fresh(e.request))
      .then(res => (res.status >= 500 ? fromCache(e.request).then(hit => hit || res) : res))
      .catch(() => fromCache(e.request).then(hit => hit || Response.error()))
  );
});
