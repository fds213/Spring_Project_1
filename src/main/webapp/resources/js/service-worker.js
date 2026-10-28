// IEUM 앱(PWA) 서비스 워커. 화면 틀 파일(CSS, JS, 이미지, 아이콘)을 캐시해 두어
// 서버가 잠깐 응답하지 않아도 설치한 앱이 열리게 한다.
// 사이트 맨 위 경로(/<컨텍스트>/service-worker.js)로 내려줘야 앱 전체를 맡는다.
//   servlet-context.xml 예: <resources mapping="/service-worker.js" location="/resources/js/" />
// CSS·JS를 바꾸면 CACHE 이름을 올린다. 안 올리면 설치한 앱에 예전 파일이 남을 수 있다.
const CACHE = 'ieum-shell-v2';

// 이 파일 위치(/<컨텍스트>/) 기준 경로. JSP 화면은 로그인이 필요하고 내용이 바뀌므로 넣지 않는다.
// resources/lib/ 의 파일(jQuery, 글꼴)은 빠져 있어도 설치가 깨지지 않게 넣지 않는다.
const SHELL = [
  'manifest.json',
  'resources/css/common.css',
  'resources/css/site.css',
  'resources/js/common.js',
  'resources/js/install.js',
  'resources/js/password-toggle.js',
  'resources/js/select.js',
  'resources/js/join.js',
  'resources/js/site.js',
  'resources/img/logo-mark.png',
  'resources/img/logo-wordmark.png',
  'resources/icons/favicon.png',
  'resources/icons/icon-192.png',
  'resources/icons/icon-512.png',
  'resources/icons/icon-maskable-512.png',
  'resources/icons/apple-touch-icon.png',
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

// 네트워크 먼저, 실패할 때만 캐시: 화면(JSP)과 AJAX 데이터는 오래된 캐시로 보여주면 안 된다
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  e.respondWith(fetch(e.request).catch(() => caches.match(e.request)));
});
