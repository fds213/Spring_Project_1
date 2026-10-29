// Caches the app shell so the installed app opens even when the server is briefly unreachable.
// Bump CACHE when shell files change, or users keep seeing the old version.
const CACHE = 'ieum-shell-v122';
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

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
  );
  self.clients.claim();
});

// Network first, cache as fallback: real data (JSP pages, AJAX) must never come from a stale cache
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  e.respondWith(fetch(e.request).catch(() => caches.match(e.request)));
});
