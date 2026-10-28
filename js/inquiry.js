// Prototype only: 도입 문의 페이지. Uses setError, clearOnInput, loadStore, saveStore from site.js.
//
// In the real project:
// - 접수: POST /inquiry ($.ajax, CSRF from common.js). The server checks every rule below again, makes the
//   접수번호 (Q-날짜-순번, from an Oracle SEQUENCE), saves the inquiry as RECEIVED and returns
//   { success, message, data: { inquiryNo, createdAt } }.
// - 답변: written in the 이음 운영자 메뉴 (장애/문의 > 비회원 접수, js/admin-inquiry.js), sent only by email.
//   The prototype hands each inquiry to that screen through INQ_KEY below.
//   The visitor never looks the answer up on the homepage, so there is no password or 문의 조회.
// - 접수 메일 (requirements v1.4 WEB-02, decided 2026-10-28): right after saving, the server mails the visitor a
//   "문의가 접수되었습니다" notice with the inquiry in the body (접수번호, 접수 시각, 회사명, 이름, 휴대전화, 제목, 내용;
//   not a file attachment) so they can check what they sent. Format: the mail guide doc, section 6. Sent outside
//   the transaction like the answer mail; if it fails, the inquiry is still saved and shows to the operator.
// - No per-address limit (decided 2026-10-28): a company may route every inquiry to one shared address
//   (a team lead, the person in charge), so the same e-mail can file any number of inquiries a day.
//
// Everything sits inside this function so its names (form, counter...) can't clash with
// the same names in site.js, which loads on the same page.
(() => {
  const INQ_KEY = 'ieum-demo-inquiries-v2'; // read by js/admin-inquiry.js (SITE_INQ_KEY)

  const $ = id => document.getElementById(id);
  const form = $('inqForm');
  const layout = $('inqLayout');
  const doneBox = $('inqDone');
  const btnSubmit = $('btnSubmit');
  const counter = $('counter');

  clearOnInput(form);

  // phone: digits only, hyphens added while typing (010-1234-5678)
  $('phone').addEventListener('input', e => {
    const d = e.target.value.replace(/\D/g, '').slice(0, 11);
    e.target.value = d.length < 4 ? d
      : d.length < 8 ? `${d.slice(0, 3)}-${d.slice(3)}`
      : `${d.slice(0, 3)}-${d.slice(3, d.length - 4)}-${d.slice(-4)}`;
  });

  // content: character counter
  $('content').addEventListener('input', () => {
    counter.textContent = `${$('content').value.length.toLocaleString('ko-KR')} / 1,000자`;
  });

  // privacy terms: open and close the details
  const btnTerms = $('btnTerms');
  btnTerms.addEventListener('click', () => {
    const terms = $('terms');
    terms.hidden = !terms.hidden;
    btnTerms.setAttribute('aria-expanded', !terms.hidden);
    btnTerms.textContent = terms.hidden ? '내용 보기' : '접기';
  });
  const agreeError = $('agree').closest('.form-row').querySelector('.field-error');
  $('agree').addEventListener('change', e => { if (e.target.checked) agreeError.textContent = ''; });

  const RULES = [
    ['companyName', v => v.trim() ? '' : '회사명을 입력해 주세요.'],
    ['name', v => v.trim() ? '' : '이름을 입력해 주세요.'],
    ['email', v => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim()) ? '' : '이메일 형식을 확인해 주세요.'],
    // optional: empty is fine, but if typed it must be a valid number
    ['phone', v => (v === '' || /^01\d-?\d{3,4}-?\d{4}$/.test(v)) ? '' : '휴대전화 번호를 확인해 주세요.'],
    ['title', v => v.trim() ? '' : '문의 제목을 입력해 주세요.'],
    ['content', v => v.trim().length >= 10 ? '' : '문의 내용을 10자 이상 입력해 주세요.'],
  ];

  function validate() {
    let firstBad = null;
    for (const [id, rule] of RULES) {
      const el = $(id);
      const msg = rule(el.value);
      setError(el, msg);
      if (msg && !firstBad) firstBad = el;
    }
    const agree = $('agree');
    agreeError.textContent = agree.checked ? '' : '개인정보 수집 및 이용에 동의해 주세요.';
    if (!agree.checked && !firstBad) firstBad = agree;
    if (firstBad) firstBad.focus();
    return !firstBad;
  }

  // 2026.10.27 14:12
  function formatDate(iso) {
    const d = new Date(iso);
    const p = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  function showDone(q) {
    $('doneNo').textContent = q.inquiryNo;
    const rows = [
      ['접수일시', formatDate(q.createdAt)],
      ['회사명', q.companyName],
      ['이름', q.name],
      ['이메일', q.email, 'em'],
      ['휴대전화', q.phone || '입력하지 않음'],
      ['문의 제목', q.title],
      ['문의 내용', q.content, 'clamp'],
    ];
    // textContent only: what the visitor typed is never read as HTML (same reason as <c:out> in JSP)
    $('summary').replaceChildren(...rows.flatMap(([k, v, cls]) => {
      const dt = document.createElement('dt');
      dt.textContent = k;
      const dd = document.createElement('dd');
      if (cls === 'clamp') {
        const span = document.createElement('span');
        span.className = 'clamp';
        span.textContent = v;
        dd.append(span);
      } else {
        dd.textContent = v;
        if (cls) dd.className = cls;
      }
      return [dt, dd];
    }));
    layout.hidden = true;
    doneBox.hidden = false;
    window.scrollTo(0, 0);
    doneBox.focus({ preventScroll: true });
  }

  form.addEventListener('submit', e => {
    e.preventDefault();
    if (btnSubmit.disabled || !validate()) return;

    // bots fill the hidden field: show success but save nothing
    const isBot = $('website').value !== '';

    // lock the button while "sending" so a double click can't make two inquiries
    btnSubmit.disabled = true;
    btnSubmit.textContent = '접수 중입니다…';

    setTimeout(() => {
      const list = loadStore(INQ_KEY, []);
      const now = new Date();
      const ymd = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`;
      const inquiry = {
        inquiryNo: `Q-${ymd}-${String(list.length + 1).padStart(4, '0')}`,
        companyName: $('companyName').value.trim(),
        name: $('name').value.trim(),
        email: $('email').value.trim(),
        phone: $('phone').value,
        title: $('title').value.trim(),
        content: $('content').value.trim(),
        status: 'RECEIVED',
        createdAt: now.toISOString(),
        // filled in later by the 운영자 메뉴 when the answer is sent
        answerContent: null,
        answeredAt: null,
      };
      if (!isBot) saveStore(INQ_KEY, [...list, inquiry]);

      btnSubmit.disabled = false;
      btnSubmit.textContent = '문의 접수';
      showDone(inquiry);
    }, 700);
  });

  // 다시 작성하기: back to the form with everything still filled in, cursor on the email box.
  // Sending again makes a new inquiry (decide with backend whether to allow editing instead).
  $('btnEdit').addEventListener('click', () => {
    doneBox.hidden = true;
    layout.hidden = false;
    window.scrollTo(0, 0);
    $('email').focus({ preventScroll: true });
    $('email').select();
  });
})();
