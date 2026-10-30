// 이음 운영자: 업체 관리 (조각 views/ops/company.html). 업체를 누르면 그 업체에 대해 아는 것이 모두 그 줄 아래에 펼쳐진다.
// 이 화면이 만드는 데이터는 없다: 다른 화면이 가진 것(신청과 결제, 사원 관리의 계정, 장애·문의 접수)을 모아 보여 준다.
// 그래서 다른 모듈의 코드 값은 서버가 표시 글자(label)를 같이 준다 (이 화면에 다른 모듈의 코드표를 두지 않는다).
// 불리는 순서: ops/inquiry.js 다음 (그 파일의 h, span, small, dlOf, cardOf, inqCell, titleBtn, dataRow, detailTr, emptyTr를 쓴다).
// 이름이 "firm"으로 시작하는 것은 ops/inquiry.js가 이미 "company..."(companyDetail, renderCompany, ...)를 쓰기 때문이다.
// 서버 API (운영자만. 시안은 demo/ops.js가 답한다):
//   GET /ops/api/companies         → 모든 업체(체험 중·읽기 전용 포함), 가입 최신순
//       [{ companyCode, company, adminName, adminNo, users, total, at, state: { status: TRIAL|PAID|EXPIRED, label } }]
//   GET /ops/api/companies/{code}  → 위 한 건 + {
//       no, email, emailVerified,
//       employees: [{ name, empNo, dept, status, statusLabel }],                         사번이 있는 계정
//       payments:  [{ seq, at, kind, kindLabel, planLabel, amount, users, result, resultLabel, resultTone, reason }],  기록된 순서
//       inquiries: [{ source: COMPANY|GUEST, kindLabel, title, at, stateLabel }]          최신순
//     }   없는 업체는 404. 가입 때 적은 비밀번호는 주지 않는다.

// 펼친 업체는 한 곳. 다시 그려도 그대로 둔다
const firm = { open: null, items: [], detail: null };
const firmPath = code => `/ops/api/companies/${encodeURIComponent(code)}`;

// 화면을 열 때 (JSP 페이지가 열리면 부른다)
function openFirmView() {
  firm.open = null;
  firm.detail = null;
  renderFirms();
}

// 목록을 다시 받아 그린다. 펼친 업체가 있으면 그 상세도 다시 받는다 (다른 탭에서 바뀌었을 때도 이것을 부른다)
function renderFirms() {
  api.get('/ops/api/companies')
    .done(items => {
      firm.items = items;
      if (firm.open && items.some(o => o.companyCode === firm.open)) loadFirmDetail(firm.open);
      else { firm.open = null; firm.detail = null; drawFirms(); }
    })
    .fail(xhr => showToast(apiMessage(xhr, '업체 목록을 불러오지 못했습니다')));
}

function loadFirmDetail(code) {
  api.get(firmPath(code))
    .done(d => {
      if (firm.open !== code) return; // 그사이 다른 업체를 눌렀다
      firm.detail = d;
      drawFirms();
    })
    .fail(xhr => {
      if (firm.open !== code) return;
      firm.open = null;
      firm.detail = null;
      drawFirms();
      showToast(apiMessage(xhr, '업체 정보를 불러오지 못했습니다'));
    });
}

function toggleFirm(code) {
  if (firm.open === code) {
    firm.open = null;
    firm.detail = null;
    drawFirms();
    return;
  }
  firm.open = code;
  firm.detail = null;
  loadFirmDetail(code); // 상세가 오면 펼친다
}

// 짧은 두 줄 항목으로 된 카드 본문. 비어 있으면 빈 카드 대신 조용한 한 문장
function miniList(entries, emptyText) {
  if (!entries.length) return h('p', 'mini-empty', emptyText);
  return h('ul', 'mini-list', ...entries.map(([main, sub]) => h('li', '', main, h('span', 'mini-sub', sub))));
}

// 사원 관리가 가진 그 업체의 계정. 300명 업체도 다른 카드를 밀어내지 않게 카드 안에서 스크롤하고,
// tabindex로 키보드 사용자도 초점을 옮겨 스크롤할 수 있다
function employeeCard(d) {
  const list = d.employees;
  // 계정마다 한 줄(왼쪽 이름, 오른쪽 사번·부서): 두 줄로 쓰면 상자에 4명밖에 안 보인다
  const body = list.length
    ? h('div', 'emp-scroll', h('ul', 'mini-list', ...list.map(e => h('li', 'emp-row', span(e.name), h('span', 'mini-sub', `${e.empNo} · ${e.dept}${e.status !== 'ST_ACTIVE' ? ` · ${e.statusLabel}` : ''}`)))))
    : h('p', 'mini-empty', '등록된 계정이 없습니다');
  if (list.length) {
    body.tabIndex = 0;
    body.setAttribute('role', 'region');
    body.setAttribute('aria-label', `${d.company} 계정 목록`);
  }
  return cardOf(`계정 · ${list.length}명`, body);
}

function firmDetail(d) {
  const info = cardOf('업체 정보', dlOf([
    ['업체명', d.company],
    ['업체 코드', d.companyCode],
    ['이용 상태', d.state.label],
    ['이용 인원', `${d.users}명`],
    ['월 이용료', won(d.total)],
    ['이메일', d.email],
    ['이메일 인증', d.emailVerified ? '완료' : '대기 (로그인 불가)'],
    ['체험 신청', whenFull(d.at)],
    ['신청번호', d.no],
  ]));

  // 가입 때 적은 비밀번호는 일부러 보이지 않는다 (서버도 주지 않는다)
  const account = cardOf('관리자 계정', dlOf([
    ['이름', d.adminName],
    ['사번', d.adminNo],
  ]));

  // 체험 신청, 그리고 모든 결제 시도(첫 결제, 연장, 만료 뒤 다시 결제, 증설), 최신순.
  // 같은 날짜의 두 시도(실패한 시도 뒤 성공한 시도)는 기록된 순서를 지킨다
  const history = [
    ...d.payments.map(p => [
      // 결과: 결제 완료 / 반려(예전 데이터, 사유와 함께) / 결제 실패, 그리고 증설 환불
      h('span', '', span(p.resultLabel, `status status-${p.resultTone}`),
        ` ${p.kindLabel}${p.planLabel ? ` ${p.planLabel}` : ''} · ${won(p.amount)}`),
      `${whenFull(p.at)}${p.users ? ` · ${p.users}명` : ''}${p.reason ? ` · 사유: ${p.reason}` : ''}`, p.at, p.seq,
    ]),
    [h('span', '', span('체험 신청', 'status status-trial'), ' 14일 무료 체험'), `${whenFull(d.at)} · ${d.no}`, d.at, 0],
  ].sort((a, b) => (new Date(b[2]) - new Date(a[2])) || (b[3] - a[3])).map(([main, sub]) => [main, sub]);
  const pay = cardOf('신청·결제 이력', miniList(history, '결제 이력이 없습니다'));

  const inquiries = cardOf('장애·문의 이력', miniList(
    d.inquiries.map(q => [q.title, `${q.kindLabel} · ${whenFull(q.at)} · ${q.stateLabel}`]),
    '이 업체의 접수 내역이 없습니다'));

  // 한 열: 관리자 카드 바로 아래 계정 목록, 둘이 같은 칸에 들어간다
  return h('div', 'info-grid', info, h('div', 'info-col', account, employeeCard(d)), pay, inquiries);
}

function drawFirms() {
  const rows = firm.items.flatMap(o => {
    const detail = firm.detail && firm.detail.companyCode === o.companyCode ? firm.detail : null;
    const isOpen = firm.open === o.companyCode && !!detail;
    const tr = dataRow(isOpen, () => toggleFirm(o.companyCode),
      inqCell('업체', titleBtn(o.company, isOpen), small(o.companyCode)),
      inqCell('관리자', span(o.adminName), small(o.adminNo)),
      inqCell('이용 인원', span(`${o.users}명`)),
      inqCell('월 이용료', span(won(o.total))),
      inqCell('상태', span(o.state.label, `status status-${o.state.status.toLowerCase()}`)),
      inqCell('가입일', span(when(o.at))));
    // 숫자 열은 신청 현황 표처럼 오른쪽 정렬
    tr.children[2].classList.add('num');
    tr.children[3].classList.add('num');
    return isOpen ? [tr, detailTr(6, firmDetail(detail))] : [tr];
  });
  document.getElementById('firmList').replaceChildren(...(rows.length ? rows : [emptyTr(6, '아직 가입한 업체가 없습니다')]));
}
