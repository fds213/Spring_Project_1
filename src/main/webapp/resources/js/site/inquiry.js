// 소개 사이트 > 도입 문의 (views/site/inquiry.jsp). 로그인 없이 연다 (요구분석서 WEB-02).
// 입력 → 접수(같은 페이지에서 완료 화면으로 바뀐다). 개인정보 동의는 화면에서만 확인하고 서버로 보내지 않는다.
//
//   POST /inquiry/api/submit  { companyName, name, email, phone, title, content, website }
//     → 200 { no (접수번호), email }
//     → 400 입력 오류 { message, field } (field = 보내는 값의 이름) / 429 너무 잦음·500 그 밖 { message }
// 주소·값 이름은 화면이 먼저 정한 제안이다(서버 담당이 정하면 그에 맞춘다, AGENTS.md '수정 범위').
// 값 이름은 운영자 화면(ops/inquiry.js)이 받는 비회원 문의 목록과 맞췄다. website 는 사람에게 안 보이는 봇 막기 칸이다.
(() => {
  const $ = id => document.getElementById(id);
  const SUBMIT_PATH = '/inquiry/api/submit';
  const CONTENT_MAX = 1000;
  const EMAIL = /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/; // 체험 신청(SiteService)과 같은 규칙
  const PHONE = /^01[016789]-\d{3,4}-\d{4}$/;
  const FIELDS = ['companyName', 'name', 'email', 'phone', 'title', 'content'];
  let sending = false;

  // 칸을 틀린 것으로 표시하고 아래에 문구를 보인다. 문구가 비면 표시를 지운다
  function setError(input, message) {
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    const box = input.closest('.form-row').querySelector('.field-error');
    if (box) box.textContent = message || '';
  }

  const readForm = () => ({
    companyName: $('companyName').value.trim(),
    name: $('name').value.trim(),
    email: $('email').value.trim(),
    phone: $('phone').value.trim(),
    title: $('title').value.trim(),
    content: $('content').value.trim(),
    website: $('website').value, // 봇 막기: 사람은 비워 둔다
  });

  // 보내기 전 확인. 틀린 칸이 있으면 첫 칸으로 초점
  function validate(f) {
    const wrong = [];
    const check = (id, message) => { setError($(id), message); if (message) wrong.push(id); };
    check('companyName', f.companyName ? '' : '회사명을 입력해 주세요');
    check('name', f.name ? '' : '이름을 입력해 주세요');
    check('email', !f.email ? '이메일을 입력해 주세요' : EMAIL.test(f.email) ? '' : '이메일 형식이 맞지 않습니다');
    check('phone', !f.phone || PHONE.test(f.phone) ? '' : '휴대전화는 010-0000-0000 형식으로 입력해 주세요');
    check('title', f.title ? '' : '문의 제목을 입력해 주세요');
    check('content', f.content ? '' : '문의 내용을 입력해 주세요');
    check('agree', $('agree').checked ? '' : '개인정보 수집 및 이용에 동의해 주세요');
    if (wrong.length) $(wrong[0]).focus();
    return wrong.length === 0;
  }

  // 휴대전화: 숫자만 받아 010-0000-0000 모양으로
  $('phone').addEventListener('input', e => {
    const d = e.target.value.replace(/\D/g, '').slice(0, 11);
    e.target.value = d.length < 4 ? d : d.length < 8 ? `${d.slice(0, 3)}-${d.slice(3)}`
      : `${d.slice(0, 3)}-${d.slice(3, d.length - 4)}-${d.slice(-4)}`;
  });

  // 문의 내용 글자 수
  const updateCounter = () => { $('counter').textContent = `${$('content').value.length.toLocaleString('ko-KR')} / ${CONTENT_MAX.toLocaleString('ko-KR')}자`; };
  $('content').addEventListener('input', updateCounter);

  // 개인정보 동의 내용 펼치기
  $('btnTerms').addEventListener('click', e => {
    const open = $('terms').hidden;
    $('terms').hidden = !open;
    e.currentTarget.setAttribute('aria-expanded', open);
    e.currentTarget.textContent = open ? '접기' : '내용 보기';
  });

  [...FIELDS, 'agree'].forEach(id => $(id).addEventListener(id === 'agree' ? 'change' : 'input', () => setError($(id), '')));

  // 접수: 칸이 틀렸다는 답이면 그 칸에, 그 밖의 실패는 버튼 위에 안내
  $('inqForm').addEventListener('submit', e => {
    e.preventDefault();
    if (sending) return;
    const f = readForm();
    if (!validate(f)) return;
    const btn = $('btnSubmit');
    const error = $('inqError');
    sending = true;
    btn.disabled = true;
    btn.textContent = '접수하는 중…';
    error.hidden = true;
    api.post(SUBMIT_PATH, f)
      .done(r => showDone(f, r || {}))
      .fail(xhr => {
        const message = apiMessage(xhr, '문의를 접수하지 못했습니다. 잠시 뒤 다시 시도해 주세요.');
        const field = xhr.responseJSON && xhr.responseJSON.field;
        if (xhr.status === 400 && FIELDS.includes(field)) {
          setError($(field), message);
          $(field).focus();
          return;
        }
        error.textContent = message;
        error.hidden = false;
      })
      .always(() => { sending = false; btn.disabled = false; btn.textContent = '문의 접수'; });
  });

  // 접수 완료: 입력 화면을 숨기고, 접수번호와 받은 내용을 보여 준다 (답변이 갈 이메일을 다시 확인하게)
  function showDone(f, r) {
    const node = (tag, cls, text) => { const el = document.createElement(tag); if (cls) el.className = cls; el.textContent = text; return el; };
    const rows = [['회사명', f.companyName], ['이름', f.name], ['이메일', r.email || f.email]];
    if (f.phone) rows.push(['휴대전화', f.phone]);
    rows.push(['문의 제목', f.title]);
    $('summary').replaceChildren(...rows.flatMap(([k, v]) => [node('dt', '', k), node('dd', '', v)]),
      node('dt', '', '문의 내용'), node('dd', 'clamp', f.content));
    $('doneNo').textContent = r.no || '-';
    $('doneNo').closest('.done-no').hidden = !r.no;
    $('inqLayout').hidden = true;
    $('inqDone').hidden = false;
    window.scrollTo({ top: 0 });
    $('inqDone').focus({ preventScroll: true });
  }

  // 다시 작성하기: 빈 양식으로 돌아간다 (새 문의로 접수된다)
  $('btnEdit').addEventListener('click', () => {
    $('inqForm').reset();
    FIELDS.forEach(id => setError($(id), ''));
    updateCounter();
    $('inqDone').hidden = true;
    $('inqLayout').hidden = false;
    $('companyName').focus();
  });

  updateCounter();
})();
