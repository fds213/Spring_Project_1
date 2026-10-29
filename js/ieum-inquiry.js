// 이음 장애 문의(사원): 직원이 이음 운영자에게 보내는 장애·문의 (ieum-inquiry). 운영자는 이것을 "회원 접수"에서 보고 답변한다 (admin-inquiry.js).
// 우리 회사 IT 담당자에게 가는 접수(IT 서비스 > 장애·문의 접수, it-service.js)와 다른 것이다 (요구분석서 3.7 문의 구분).
// 저장소는 운영자 화면이 읽는 COMPANY_KEY 하나를 같이 쓴다. 한 건의 모양은 admin-inquiry.js 가 쓰는 그대로이고
// (no, type, at, company, writer, coAdmins, title, body, reply, notice), 사원 화면이 본인 것을 가려내려고 companyCode, writerEmpNo 를 더했다.
// 누가 볼 수 있나: 쓴 본인과 같은 업체의 업체 관리자. 다른 업체 것은 보이지 않는다 (서버는 업체를 로그인한 사용자 정보로만 정한다).
// 이용 기간이 끝난 업체(읽기 전용)는 새 문의를 등록할 수 없다. 이미 쓴 문의와 답변은 읽을 수 있다 (요구분석서 3.11).
// 사진 첨부는 이 시안에 없다 (IT 서비스 접수와 같다). 이 파일은 admin-inquiry.js 뒤, common.js 앞에 로드된다.

const IQ_TITLE_MAX = 60;
const IQ_BODY_MAX = 1000;
const iqEl = id => document.getElementById(id);
const iqNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const iqCell = (label, ...parts) => {
  const td = iqNode('td', '', iqNode('div', 'cell', ...parts));
  td.dataset.label = label; // 폰에서 값 앞에 붙는 열 이름
  return td;
};
const iqState = { open: null };

const iqIsAdmin = () => userRoles.includes('ADMIN');
const iqVisible = q => q.companyCode === session.company && (q.writerEmpNo === session.id || iqIsAdmin());
const iqMine = () => loadStore(COMPANY_KEY, []).filter(iqVisible).sort((a, b) => new Date(b.at) - new Date(a.at));

// 접수번호: C-월일-그날의 순번. 운영자 화면의 번호 모양(G-1027-005)과 같은 꼴이다
function iqNextNo(list) {
  const d = new Date();
  const day = `${pad2(d.getMonth() + 1)}${pad2(d.getDate())}`;
  const n = list.filter(q => q.no.startsWith(`C-${day}-`)).length + 1;
  return `C-${day}-${String(n).padStart(3, '0')}`;
}

function openIeumInquiryView() {
  iqEl('iqError').textContent = '';
  renderIeumInquiry();
}

iqEl('iqForm').addEventListener('submit', e => {
  e.preventDefault();
  const err = iqEl('iqError');
  if (readOnly) { err.textContent = readOnlyMessage(); return; }
  const type = iqEl('iqType').value;
  const title = iqEl('iqTitle').value.trim();
  const body = iqEl('iqBody').value.trim();
  if (!['INCIDENT', 'INQUIRY'].includes(type)) { err.textContent = '구분을 골라 주세요'; return; }
  if (!title) { err.textContent = '제목을 입력해 주세요'; iqEl('iqTitle').focus(); return; }
  if (title.length > IQ_TITLE_MAX) { err.textContent = `제목은 ${IQ_TITLE_MAX}자까지 쓸 수 있습니다`; return; }
  if (!body) { err.textContent = '내용을 입력해 주세요'; iqEl('iqBody').focus(); return; }
  if (body.length > IQ_BODY_MAX) { err.textContent = `내용은 ${IQ_BODY_MAX}자까지 쓸 수 있습니다`; return; }
  const list = loadStore(COMPANY_KEY, []);
  const q = {
    no: iqNextNo(list), type, at: new Date().toISOString(), company: session.companyName, companyCode: session.company,
    writer: session.name, writerEmpNo: session.id,
    // 같이 볼 수 있는 업체 관리자 수 (운영자 화면의 "열람 가능"에 쓴다)
    coAdmins: employeesOf(session.company).filter(x => x.status === 'ACTIVE' && (x.roles || []).includes('ADMIN') && x.empNo !== session.id).length,
    title, body,
  };
  list.push(q);
  saveStore(COMPANY_KEY, list);
  iqEl('iqForm').reset();
  err.textContent = '';
  iqState.open = q.no;
  showToast(`${q.no} 접수했습니다. 이음 운영자가 확인하고 답변합니다`);
  renderIeumInquiry();
});

function renderIeumInquiry() {
  const mine = iqMine();
  const rows = [];
  mine.forEach(q => {
    const isOpen = iqState.open === q.no;
    const answered = !!q.reply;
    const tr = iqNode('tr', 'row-link' + (isOpen ? ' is-open' : ''),
      iqCell('번호', iqNode('span', '', q.no)),
      iqCell('구분', iqNode('span', '', TYPE_SHORT[typeOf(q)])),
      iqCell('제목', iqNode('span', '', q.title), ...(q.writerEmpNo !== session.id ? [iqNode('small', 'cell-sub', q.writer)] : [])),
      iqCell('상태', iqNode('span', `status status-${answered ? 'paid' : 'waiting'}`, answered ? '답변 완료' : '답변 대기')),
      iqCell('접수일', iqNode('span', '', whenFull(q.at))));
    tr.addEventListener('click', () => { iqState.open = isOpen ? null : q.no; renderIeumInquiry(); });
    rows.push(tr);
    if (isOpen) {
      iqMarkRead(q);
      const td = iqNode('td', '', iqNode('div', 'slide open', iqNode('div', 'slide-inner', iqDetail(q))));
      td.colSpan = 5;
      rows.push(iqNode('tr', 'row-detail', td));
    }
  });
  if (!rows.length) {
    const td = iqNode('td', '', '보낸 문의가 없습니다');
    td.colSpan = 5;
    rows.push(iqNode('tr', 'row-empty', td));
  }
  iqEl('iqList').replaceChildren(...rows);
  iqEl('iqCount').textContent = `${mine.length}건 (답변 대기 ${mine.filter(q => !q.reply).length}건)`;
}

// 답변 알림을 열어 보면 읽음으로 바뀐다. 실제로는 업체 관리자 화면이 알림을 열 때 서버가 바꾼다 (admin-inquiry.js markNoticeRead 와 같은 뜻)
function iqMarkRead(q) {
  if (!q.notice || q.notice.readAt) return;
  const list = loadStore(COMPANY_KEY, []);
  const x = list.find(z => z.no === q.no);
  if (!x || !x.notice) return;
  x.notice.readAt = new Date().toISOString();
  saveStore(COMPANY_KEY, list);
}

function iqDetail(q) {
  const info = iqNode('dl', 'detail');
  [['접수번호', q.no], ['유형', TYPE_LABEL[typeOf(q)]], ['접수 시각', whenFull(q.at)], ['접수자', q.writer],
    ['열람 가능', q.coAdmins ? `${q.writer} 외 같은 업체 관리자 ${q.coAdmins}명` : q.writer], ['제목', q.title], ['내용', q.body]]
    .forEach(([k, v]) => info.append(iqNode('dt', '', k), iqNode('dd', '', v)));
  const answer = q.reply
    ? iqNode('div', 'inq-body', iqNode('p', '', q.reply.body), iqNode('p', 'inq-hint', `답변 ${whenFull(q.reply.savedAt)}${q.reply.editedAt ? ` · 수정 ${whenFull(q.reply.editedAt)}` : ''}`))
    : iqNode('div', 'inq-body', iqNode('p', 'muted', '아직 답변이 없습니다. 이음 운영자가 확인하면 알림으로 알려 드립니다.'));
  return iqNode('div', 'inq-grid',
    iqNode('section', 'info-card', iqNode('header', 'info-card-head', iqNode('h2', '', '접수 내용')), info),
    iqNode('section', 'info-card', iqNode('header', 'info-card-head', iqNode('h2', '', '운영자 답변')), answer));
}

// 운영자가 다른 탭에서 답변을 저장하면 열려 있는 화면에 바로 보인다
window.addEventListener('storage', e => {
  if (!session || session.type !== 'EMPLOYEE' || e.key !== COMPANY_KEY) return;
  if (iqEl('view-ieum-inquiry').classList.contains('on')) renderIeumInquiry();
});
