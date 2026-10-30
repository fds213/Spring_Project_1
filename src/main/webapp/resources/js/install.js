// "다운로드" = 이음을 앱(PWA)으로 설치한다. 설치한 앱은 그룹웨어 로그인 화면에서 바로 열린다
// (manifest start_url). 로그인 페이지는 그 앱 창에서만 열리게 할 수 있다 (login.jsp, data-app-only).
// data-download 가 붙은 버튼은 모두 설치를 시작한다. 버튼으로 설치할 수 없는 브라우저에는 짧은 안내를 보여 준다.
const runningAsApp = matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
const UA = navigator.userAgent;

// 설치한 앱 안에서는 창이 곧 그룹웨어다. 소개 사이트 같은 페이지(site.tag 가 body.site 를 붙인다)가
// 거기서 열리면 로그인 화면으로 보낸다. 이런 페이지를 연 채로 브라우저 메뉴에서 만든 앱도 여기서 처리된다:
// 그런 앱은 manifest start_url(로그인) 대신 그 페이지에서 시작하기 때문이다.
if (runningAsApp && document.body.classList.contains('site')) location.replace((document.body.dataset.ctx || '') + '/login');
const isIOS = /iphone|ipad|ipod/i.test(UA) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

// Chrome/Edge(PC, Android)는 페이지를 설치할 수 있을 때 이 이벤트를 보낸다. 버튼에 쓰려고 받아 둔다
let installPrompt = null;
window.addEventListener('beforeinstallprompt', e => {
  e.preventDefault();
  if (!runningAsApp) installPrompt = e;
});
window.addEventListener('appinstalled', () => {
  installPrompt = null;
  showInstallGuide('installed');
});

// 브라우저가 설치 창을 넘겨주지 않았을 때 보여 준다: 버튼으로 설치할 수 없거나, 이음이
// 이미 설치되어 있다(그러면 Chrome이 더 권하지 않는다). 브라우저마다 안내 하나, 메뉴 이름은 2026-10 기준.
const ALREADY = ' 이미 설치했다면 설치된 IEUM 앱을 열면 됩니다.';
const INSTALL_GUIDE = {
  installed: {
    title: 'IEUM 앱이 설치되었습니다',
    body: '앱을 실행하여 IEUM 그룹웨어를 사용해보세요!',
  },
  chrome: {
    title: '주소창에서 설치해 주세요',
    body: '주소창 오른쪽 끝의 설치 아이콘(모니터 모양)을 누르거나, 오른쪽 위 ⋮ 메뉴 > 전송, 저장, 공유에서 설치 항목을 고르세요.' + ALREADY,
  },
  edge: {
    title: '주소창에서 설치해 주세요',
    body: '주소창 오른쪽의 앱 설치 아이콘을 누르거나, 오른쪽 위 ⋯ 메뉴 > 앱 > 이 사이트를 앱으로 설치를 고르세요.' + ALREADY,
  },
  android: {
    title: '홈 화면에 설치해 주세요',
    body: '오른쪽 위 ⋮ 메뉴에서 "앱 설치" 또는 "홈 화면에 추가"를 고르세요.' + ALREADY,
  },
  ios: {
    title: '홈 화면에 추가해 주세요',
    body: 'Safari 아래쪽 공유 버튼을 누른 뒤 "홈 화면에 추가"를 고르면 IEUM 앱이 생깁니다.' + ALREADY,
  },
  macSafari: {
    title: 'Dock에 추가해 주세요',
    body: 'Safari 위쪽 메뉴 막대에서 파일 > Dock에 추가를 고르면 IEUM 앱이 생깁니다. 크롬이나 엣지에서는 주소창의 설치 아이콘으로 설치할 수 있습니다.' + ALREADY,
  },
  other: {
    title: '크롬이나 엣지에서 설치해 주세요',
    body: '이 브라우저에서는 IEUM 앱을 설치할 수 없습니다. 크롬이나 엣지로 이 주소를 열고 다운로드를 다시 눌러 주세요.',
  },
};
// 이 브라우저에 맞는 안내 (Edge와 Whale도 user agent에 "Chrome"이 들어 있어 먼저 확인한다)
function guideKind() {
  if (isIOS) return 'ios';
  if (/Edg\//.test(UA)) return 'edge';
  if (/Android/i.test(UA)) return /Chrome\//.test(UA) ? 'android' : 'other';
  if (/Chrome\//.test(UA)) return 'chrome';
  if (/Macintosh/.test(UA) && /Safari\//.test(UA)) return 'macSafari';
  return 'other';
}

function showInstallGuide(kind) {
  let dialog = document.getElementById('installGuide');
  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'installGuide';
    dialog.className = 'modal';
    const title = document.createElement('h2');
    const body = document.createElement('p');
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'btn btn-primary btn-block';
    close.textContent = '확인';
    close.addEventListener('click', () => dialog.close());
    dialog.append(title, body, close);
    document.body.append(dialog);
  }
  const guide = INSTALL_GUIDE[kind];
  dialog.querySelector('h2').textContent = guide.title;
  dialog.querySelector('p').textContent = guide.body;
  if (!dialog.open) dialog.showModal();
}

async function startDownload() {
  if (installPrompt) {
    installPrompt.prompt();
    await installPrompt.userChoice; // "설치됨" 안내는 appinstalled 이벤트에서 보여 준다
    installPrompt = null;
    return;
  }
  showInstallGuide(guideKind());
}

document.querySelectorAll('[data-download]').forEach(b => b.addEventListener('click', startDownload));

// 서비스 워커는 /resources/ 가 아니라 사이트 맨 위(/<context>/service-worker.js)에서 받아야
// 앱의 페이지를 맡을 수 있다. 어디서 내려 줄지는 아직 정하지 않았다(가이드 6장).
// 그때까지는 등록이 조용히 실패한다.
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register((document.body.dataset.ctx || '') + '/service-worker.js').catch(() => {});
}
