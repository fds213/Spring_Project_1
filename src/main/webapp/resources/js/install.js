// "다운로드" = install IEUM as an app (PWA). The installed app opens straight on the groupware login
// (manifest start_url). The login page can require that app window (login.jsp, data-app-only).
// Any button with data-download starts the install; browsers that can't install from a button get a short guide.
const runningAsApp = matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

// Inside the installed app the window is the groupware. If a homepage-style page (site.tag sets body.site)
// opens there, go to the login screen. This also covers an app made from the browser menu while one of these
// pages was open: such an app starts on that page instead of the manifest start_url (login).
if (runningAsApp && document.body.classList.contains('site')) location.replace((document.body.dataset.ctx || '') + '/login');

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

const INSTALL_GUIDE = {
  installed: {
    title: 'IEUM 앱이 설치되었습니다',
    body: '바탕화면이나 홈 화면의 IEUM 앱을 열면 그룹웨어 로그인 화면이 바로 나옵니다.',
  },
  ios: {
    title: '홈 화면에 추가해 주세요',
    body: 'Safari 아래쪽 공유 버튼을 누른 뒤 "홈 화면에 추가"를 고르면 IEUM 앱이 생깁니다.',
  },
  // No install prompt from the browser. In Chrome/Edge the usual reason is that IEUM is already installed for
  // this address (the browser never offers it twice; localhost and 127.0.0.1 count as different addresses), so
  // the guide starts with how to check that, then how to install from the menu, then how to install again.
  manual: {
    title: '설치 창이 뜨지 않았습니다',
    body: '이 주소에 IEUM 앱이 이미 설치되어 있으면 브라우저가 설치 창을 다시 띄우지 않습니다. 아래 순서로 확인해 주세요.',
    steps: [
      ['이미 설치되어 있는지 확인', '주소창 오른쪽에 "앱에서 열기" 아이콘이 보이면 설치된 것입니다. 그 아이콘이나 바탕화면·시작 메뉴의 IEUM 앱을 여세요. 크롬 주소창에 chrome://apps 를 입력해도 설치된 앱을 볼 수 있습니다.'],
      ['설치되어 있지 않다면', '주소창 오른쪽의 설치 아이콘을 누르거나, 브라우저 메뉴(⋮ 또는 …)에서 "앱 설치" 항목을 고르세요.'],
      ['다시 설치하고 싶다면', 'chrome://apps 에서 IEUM 앱을 오른쪽 클릭해 "Chrome에서 삭제"를 고른 뒤, 이 페이지를 새로고침하고 다운로드를 다시 누르세요.'],
    ],
  },
};

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
  showInstallGuide(isIOS ? 'ios' : 'manual');
}

document.querySelectorAll('[data-download]').forEach(b => b.addEventListener('click', startDownload));

// The service worker must come from the top of the site (/<context>/service-worker.js), not /resources/,
// or it can't look after the app's pages. Where it is served from is still to be set up (guide section 6);
// until then the registration just fails quietly.
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register((document.body.dataset.ctx || '') + '/service-worker.js').catch(() => {});
}
