// 체험 남은 일수(DEM-02)·만료 읽기 전용(DEM-03·SUB-04) 팝업. 그날 처음 연 화면에서 한 번 띄운다. 문구와 버튼은 views/subscription/trial-notice.jspf
// 오늘 봤는지는 이 브라우저에 계정별로 적어 둔다. 그래서 같은 사람도 기기·브라우저가 다르면 따로 뜬다.
// 체험 안내와 만료 안내는 같은 저장값을 쓴다. 만료는 0시에 바뀌어 같은 날 두 안내가 겹치지 않는다.
(() => {
  const dialog = document.getElementById('trialNoticeDialog');
  if (!dialog) return;

  const now = new Date();
  const today = `${now.getFullYear()}-${now.getMonth() + 1}-${now.getDate()}`;
  const key = `ieum-trial-notice-${dialog.dataset.account}`;
  try {
    if (localStorage.getItem(key) === today) return;
    // 뜨는 순간 본 것으로 적는다. 닫지 않고 다른 화면으로 가도 그날은 다시 뜨지 않는다
    localStorage.setItem(key, today);
  } catch (e) {
    return; // 저장소가 막히면 화면마다 뜨게 되니 띄우지 않는다(헤더의 남은 일수 띠는 보인다)
  }

  dialog.querySelector('[value="cancel"]').addEventListener('click', () => dialog.close());
  // 그날 처음 연 화면이 이용 결제 화면이면 [즉시 구매]·[결제하기]는 같은 화면을 다시 여는 대신 창만 닫는다
  const pay = dialog.querySelector('a.btn-primary');
  if (pay && pay.pathname === location.pathname) {
    pay.addEventListener('click', e => { e.preventDefault(); dialog.close(); });
  }
  dialog.showModal();
})();
