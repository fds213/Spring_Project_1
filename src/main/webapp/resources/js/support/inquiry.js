// 이음 장애 문의 (views/support/inquiry.jsp, OPS-04 최소판). 직원이 이음 운영팀에 문의를 보내고, 내가 보낸 문의에서 답변을 본다.
// 업체 관리자에게는 같은 업체 직원들이 보낸 문의 표가 더 있다(읽기만). 표가 있는 화면에서만 불러온다.
// 페이지가 열리면 openSupportInquiryView()를 부른다. 만료 업체는 보내기 버튼이 막히고(data-write, common.js) 서버도 거절한다.
// 작은 DOM 도구(h, span, dlOf, cardOf, text, inqCell, titleBtn, dataRow, detailTr, emptyTr)는 util.js에 있다.
// 문의와 답변은 사람이 쓴 글이라 textContent로만 넣는다 (innerHTML을 쓰지 않는다).

const SUPPORT_PATH = '/support/api/inquiries';
// 표 종류별 tbody·주소·빈 표 문구. sender가 true면 보낸 사람 칸을 더 보인다
const SUPPORT_LISTS = {
  mine: { box: 'supportList', path: SUPPORT_PATH, empty: '아직 보낸 문의가 없습니다', sender: false },
  company: { box: 'supportCompanyList', path: '/support/admin/api/inquiries', empty: '우리 회사 직원이 보낸 문의가 없습니다', sender: true },
};
// 상태는 공통 코드 SUPPORT_STATUS다. 운영자가 답변을 달면 SUP_PROCESSING이 된다
const SUPPORT_STATE = {
  SUP_RECEIVED: { label: '답변 대기', tone: 'wait' },
  SUP_PROCESSING: { label: '답변 완료', tone: 'done' },
};
// 이름표에 없는 상태가 와도 목록 전체가 멈추지 않게 받은 값을 그대로 보인다
const supportState = q => SUPPORT_STATE[q.state] || { label: q.state, tone: 'wait' };

const supportItems = { mine: [], company: [] }; // 서버가 최신순으로 준다
const supportOpen = { mine: null, company: null }; // 표마다 펼친 문의 번호 (한 번에 하나)
let supportSending = false;

// 이 화면에 있는 표 종류. 업체 관리자가 아니면 company 표가 없다
const supportKinds = () => Object.keys(SUPPORT_LISTS).filter(kind => document.getElementById(SUPPORT_LISTS[kind].box));

// 화면을 열 때 (JSP 페이지가 열리면 부른다)
function openSupportInquiryView() {
  document.getElementById('supportForm').addEventListener('submit', sendSupportInquiry);
  supportKinds().forEach(kind => { renderSupportList(kind); loadSupportList(kind); });
}

// 목록을 다시 받아 그린다
function loadSupportList(kind) {
  api.get(SUPPORT_LISTS[kind].path)
    .done(items => { supportItems[kind] = items; renderSupportList(kind); })
    .fail(xhr => showToast(apiMessage(xhr, '문의 목록을 불러오지 못했습니다')));
}

// 문의를 보낸다. 서버도 같은 검사를 다시 한다
function sendSupportInquiry(e) {
  e.preventDefault();
  if (supportSending) return;
  const titleBox = document.getElementById('supportTitle');
  const bodyBox = document.getElementById('supportBody');
  const errorBox = document.getElementById('supportError');
  const sendBtn = document.getElementById('supportSend');
  const title = titleBox.value.trim();
  const body = bodyBox.value.trim();
  errorBox.textContent = !title ? '제목을 입력해 주세요.' : !body ? '내용을 입력해 주세요.' : '';
  if (!title || !body) { (title ? bodyBox : titleBox).focus(); return; }

  supportSending = true;
  sendBtn.disabled = true;
  api.post(SUPPORT_PATH, { title, body })
    .done(res => {
      titleBox.value = '';
      bodyBox.value = '';
      supportOpen.mine = res.no; // 방금 보낸 문의를 펼쳐 보여 준다
      showToast('문의를 보냈습니다');
      supportKinds().forEach(loadSupportList);
    })
    .fail(xhr => { errorBox.textContent = apiMessage(xhr, '문의를 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.'); })
    .always(() => { supportSending = false; sendBtn.disabled = false; });
}

// 펼친 줄: 보낸 내용과 답변
function supportDetail(kind, q) {
  const left = cardOf('보낸 내용', dlOf([['접수번호', q.no], ['보낸 시각', whenFull(q.at)],
    ...(SUPPORT_LISTS[kind].sender ? [['보낸 사람', q.name]] : []), ['제목', q.title], ['내용', text(q.body)]]));
  const replies = q.replies.length
    ? h('ul', 'reply-log', ...q.replies.map(r => h('li', '',
      h('div', 'reply-meta', span('이음 운영팀'), span(whenFull(r.at)), r.editedAt ? span('수정됨') : null),
      h('div', 'reply-text', r.body))))
    : h('p', 'inq-hint', '아직 답변이 없습니다. 운영팀이 답변을 등록하면 여기에 보입니다.');
  return h('div', 'inq-grid', left, cardOf('답변', h('div', 'inq-body', replies)));
}

function renderSupportList(kind) {
  const list = SUPPORT_LISTS[kind];
  const cols = list.sender ? 4 : 3;
  const rows = supportItems[kind].flatMap(q => {
    const isOpen = supportOpen[kind] === q.no;
    const state = supportState(q);
    const tr = dataRow(isOpen, () => { supportOpen[kind] = isOpen ? null : q.no; renderSupportList(kind); },
      inqCell('보낸 시각', span(when(q.at))),
      list.sender ? inqCell('보낸 사람', span(q.name)) : null,
      inqCell('제목', titleBtn(q.title, isOpen)),
      inqCell('상태', span(state.label, `status status-${state.tone}`)));
    return isOpen ? [tr, detailTr(cols, supportDetail(kind, q))] : [tr];
  });
  document.getElementById(list.box).replaceChildren(...(rows.length ? rows : [emptyTr(cols, list.empty)]));
}
