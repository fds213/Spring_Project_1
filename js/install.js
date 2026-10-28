// "다운로드" = install IEUM as an app (PWA). The installed app opens straight on the groupware login
// (manifest start_url), and the groupware only runs inside that app window (common.js checks runningAsApp).
// Any button with data-download starts the install; browsers that can't install from a button get a short guide.
const runningAsApp = matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
const UA = navigator.userAgent;

// Inside the installed app the window is the groupware. If the intro, trial or inquiry page (body.site) opens
// there, go to the login screen. This also covers an app made from the browser menu while one of these pages
// was open: such an app starts on that page instead of the manifest start_url (index.html).
if (runningAsApp && document.body.classList.contains('site')) location.replace('index.html');
const isIOS = /iphone|ipad|ipod/i.test(UA) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

// Chrome/Edge (PC, Android) fire this when the page can be installed; keep it for the button
let installPrompt = null;
window.addEventListener('beforeinstallprompt', e => {
  e.preventDefault();
  if (!runningAsApp) installPrompt = e;
});
window.addEventListener('appinstalled', () => {
  installPrompt = null;
  showInstallGuide('installed');
});

// Shown when the browser didn't hand over its install prompt: it can't install from a button, or IEUM is
// already installed (Chrome stops offering it then). One guide per browser; menu names as of 2026-10.
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
// which guide fits this browser (Edge and Whale also say "Chrome" in their user agent, so check them first)
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
    const steps = document.createElement('ol');
    steps.className = 'install-steps';
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'btn btn-primary btn-block';
    close.textContent = '확인';
    close.addEventListener('click', () => dialog.close());
    dialog.append(title, body, steps, close);
    document.body.append(dialog);
  }
  const guide = INSTALL_GUIDE[kind];
  dialog.querySelector('h2').textContent = guide.title;
  dialog.querySelector('p').textContent = guide.body;
  // numbered steps (manual guide only): a bold lead and its explanation, written with textContent
  const list = dialog.querySelector('.install-steps');
  list.replaceChildren(...(guide.steps || []).map(([lead, text]) => {
    const li = document.createElement('li');
    const strong = document.createElement('strong');
    strong.textContent = lead;
    const span = document.createElement('span');
    span.textContent = text;
    li.append(strong, span);
    return li;
  }));
  list.hidden = !guide.steps;
  dialog.classList.toggle('modal-wide', !!guide.steps);
  if (!dialog.open) dialog.showModal();
}

async function startDownload() {
  if (installPrompt) {
    installPrompt.prompt();
    await installPrompt.userChoice; // "installed" guide comes from the appinstalled event
    installPrompt = null;
    return;
  }
  showInstallGuide(guideKind());
}

document.querySelectorAll('[data-download]').forEach(b => b.addEventListener('click', startDownload));

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('service-worker.js');
}
