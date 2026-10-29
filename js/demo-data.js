// 시안 전용: 사원 화면(전자결재·IT 서비스·이음 장애 문의)을 로그인만 하면 바로 볼 수 있게, 시연용 직원과 그 직원의 티켓·장비·결재 문서·문의를
// 한 번 넣어 둔다. 실제 JSP 에는 넣지 않는다 (실제로는 DB 에 시연 데이터를 따로 넣는다).
// - 직원은 로그인 화면의 시안용 계정 버튼으로 들어간다. 비밀번호는 다른 시안 계정과 같은 DEMO_PW 다.
// - 한 번만 넣는다 (DEMO_SEED_KEY). 이미 있는 사번은 건드리지 않아서 직접 만든 계정과 데이터는 그대로 남는다.
//   처음 상태로 되돌리려면 이 사이트의 Local Storage 를 비우면 된다 (개발자 도구 > Application).
// - 사번은 업체 관리자(20260001) 다음 번호다 (관리자와 같은 해). 날짜는 넣는 날을 기준으로 앞뒤로 잡는다.
// 이 파일은 ieum-inquiry.js 뒤, common.js 앞에 로드된다 (staffOf, saveStaff, DEMO_PW, 각 화면의 저장소 키는 앞 파일에서 온다).

const DEMO_SEED_KEY = 'ieum-demo-seed-employee-v1';

// 사원이 쓰는 화면을 보여 주려고 넣는 직원. 부서장은 head, 인사 담당은 HR, IT 담당은 IT 역할이다 (역할은 업체 관리자만 부여한다)
const DEMO_STAFF = {
  ieum0002: [
    { name: '김사원', empNo: '20260002', dept: '개발팀', rank: '사원', roles: [] },
    { name: '박부장', empNo: '20260003', dept: '개발팀', rank: '부장', roles: [], head: true },
    { name: '이인사', empNo: '20260004', dept: '경영지원팀', rank: '과장', roles: ['HR'] },
    { name: '최아이티', empNo: '20260005', dept: '경영지원팀', rank: '대리', roles: ['IT'] },
  ],
  // 이용 기간이 끝나 읽기 전용인 업체의 사원 (새로 쓰는 동작이 막히는지 보려는 용도)
  ieum0003: [
    { name: '한만료', empNo: '20260002', dept: '경영지원팀', rank: '사원', roles: [] },
  ],
};

const seedAgo = hours => new Date(Date.now() - hours * 3600000).toISOString();
// 오늘에서 offset 일 떨어진 평일 (주말·기본 휴일은 연차로 세지 않으므로 피한다)
function seedWorkday(offset) {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  const step = offset < 0 ? -1 : 1;
  while (d.getDay() === 0 || d.getDay() === 6 || DEFAULT_HOLIDAYS[dayKeyOf(d)]) d.setDate(d.getDate() + step);
  return dayKeyOf(d);
}

function ensureDemoEmployeeData() {
  if (loadStore(DEMO_SEED_KEY, false)) return;
  const code = 'ieum0002';
  if (!findCompanyOrder(code)) return; // 시안 업체가 아직 없으면 다음에 다시 시도한다 (표시를 남기지 않는다)

  // 1) 직원. 처음 부르면 업체 관리자 레코드가 먼저 만들어진다
  Object.entries(DEMO_STAFF).forEach(([c, people]) => {
    const list = staffOf(c);
    const have = new Set(list.map(e => e.empNo));
    people.filter(p => !have.has(p.empNo)).forEach(p => list.push({
      ...p, status: 'ACTIVE', pw: DEMO_PW, email: `${p.empNo}@example.com`, at: seedAgo(24 * 400), createdAt: seedAgo(24 * 400),
    }));
    saveStaff(c, list);
  });

  const K = { empNo: '20260002', name: '김사원', dept: '개발팀' };
  const P = { empNo: '20260003', name: '박부장', dept: '개발팀' };
  const H = { empNo: '20260004', name: '이인사', dept: '경영지원팀' };
  const I = { empNo: '20260005', name: '최아이티', dept: '경영지원팀' };
  // 이미 같은 id 의 데이터가 있으면 기존 것을 남긴다 (직접 만든 데이터를 덮어쓰지 않는다)
  const put = (key, add) => {
    const all = loadStore(key, {});
    const list = (all[code] = all[code] || []);
    add(list);
    const seen = new Set();
    all[code] = list.filter(x => !seen.has(x.id) && (seen.add(x.id), true));
    saveStore(key, all);
  };
  const act = (p, action, hoursAgo, comment = '') => ({ step: 0, by: p.empNo, name: p.name, action, at: seedAgo(hoursAgo), comment });

  // 2) 연차: 김사원 — 오후 반차 승인 1건(지난 평일), 연차 승인 대기 1건(박부장 차례). 박부장 — 이인사 차례인 연차 1건
  put(LEAVE_KEY, list => list.push(
    { id: 'L-DEMO-1', empNo: K.empNo, name: K.name, dept: K.dept, kind: 'FULL', from: seedWorkday(12), to: seedWorkday(12), days: 1, reason: '가족 행사', at: seedAgo(6), status: 'REQUESTED', step: 0, line: [P.empNo, H.empNo], history: [] },
    { id: 'L-DEMO-2', empNo: K.empNo, name: K.name, dept: K.dept, kind: 'PM', from: seedWorkday(-6), to: seedWorkday(-6), days: 0.5, reason: '병원 진료', at: seedAgo(24 * 9), status: 'APPROVED', step: 0, line: [P.empNo], history: [act(P, 'APPROVE', 24 * 8)], decidedAt: seedAgo(24 * 8) },
    { id: 'L-DEMO-3', empNo: P.empNo, name: P.name, dept: P.dept, kind: 'FULL', from: seedWorkday(15), to: seedWorkday(15), days: 1, reason: '개인 사정', at: seedAgo(8), status: 'REQUESTED', step: 0, line: [H.empNo], history: [] }));

  // 3) 장비 지급 신청서: 박부장 차례 2건(김사원 노트북, 이인사 키보드·마우스), 승인 1건(지급 티켓 T-0003), 반려 1건(재기안 시연)
  put(EA_KEY, list => list.push(
    { id: 'E-DEMO-1', empNo: K.empNo, name: K.name, dept: K.dept, at: seedAgo(5), title: '장비 지급 신청서 · 노트북 1대', fields: { kind: '노트북', qty: 1, branchId: 'B1', reason: '사용 연수가 3년이 넘어 교체가 필요합니다' }, line: [P.empNo, H.empNo], step: 0, status: 'REQUESTED', history: [] },
    { id: 'E-DEMO-3', empNo: H.empNo, name: H.name, dept: H.dept, at: seedAgo(4), title: '장비 지급 신청서 · 키보드·마우스 2대', fields: { kind: '키보드·마우스', qty: 2, branchId: 'B1', reason: '신규 입사자 지급용' }, line: [P.empNo], step: 0, status: 'REQUESTED', history: [] },
    { id: 'E-DEMO-2', empNo: K.empNo, name: K.name, dept: K.dept, at: seedAgo(60), title: '장비 지급 신청서 · 모니터 1대', fields: { kind: '모니터', qty: 1, branchId: 'B1', reason: '듀얼 모니터로 작업하려고 합니다' }, line: [P.empNo], step: 0, status: 'APPROVED', history: [act(P, 'APPROVE', 48, '확인했습니다')], decidedAt: seedAgo(48), ticketId: 'T-0003' },
    { id: 'E-DEMO-4', empNo: K.empNo, name: K.name, dept: K.dept, at: seedAgo(100), title: '장비 지급 신청서 · 모바일 기기 1대', fields: { kind: '모바일 기기', qty: 1, branchId: 'B1', reason: '업무 연락용' }, line: [P.empNo], step: 0, status: 'REJECTED', history: [act(P, 'REJECT', 90, '업무에 필요한 이유를 더 구체적으로 적어 주세요')] }));
  put(EA_DRAFT_KEY, list => list.push(
    { id: 'D-DEMO-1', empNo: K.empNo, form: 'LEAVE', fields: { kind: 'FULL', from: seedWorkday(20), to: seedWorkday(20), reason: '여름휴가' }, line: [P.empNo, ''], savedAt: seedAgo(3) }));

  // 4) IT 티켓(처리 중·완료·장비 지급 접수·접수)과 지급된 장비
  const log = (...steps) => steps.map(([who, text, h]) => ({ at: seedAgo(h), by: who.empNo, name: who.name, text }));
  put(TICKET_KEY, list => list.push(
    { id: 'T-0001', type: 'INCIDENT', title: '사무실 프린터 인쇄가 안 됩니다', body: '어제 오후부터 인쇄 대기열에서 멈춰 있습니다.', branchId: 'B1', assetId: '', empNo: K.empNo, name: K.name, dept: K.dept, status: 'IN_PROGRESS', at: seedAgo(30),
      history: log([K, '접수했습니다', 30], [{ empNo: 'IT', name: 'IT 담당자(시연)' }, '처리를 시작했습니다', 20]), comments: [{ at: seedAgo(18), by: K.empNo, name: K.name, body: '다른 PC에서도 같은 증상입니다' }] },
    { id: 'T-0002', type: 'INQUIRY', title: 'VPN 계정 발급 문의', body: '재택근무용 VPN 계정을 받으려면 어떻게 하나요?', branchId: 'B1', assetId: '', empNo: K.empNo, name: K.name, dept: K.dept, status: 'DONE', at: seedAgo(120),
      history: log([K, '접수했습니다', 120], [{ empNo: 'IT', name: 'IT 담당자(시연)' }, '처리를 시작했습니다', 110], [{ empNo: 'IT', name: 'IT 담당자(시연)' }, '완료했습니다', 100]), comments: [] },
    { id: 'T-0003', type: 'EQUIPMENT', title: '장비 지급 · 모니터 1대', body: '듀얼 모니터로 작업하려고 합니다', branchId: 'B1', assetId: '', empNo: K.empNo, name: K.name, dept: K.dept, status: 'RECEIVED', at: seedAgo(48), kind: '모니터', qty: 1, docId: 'E-DEMO-2',
      history: log([K, '접수했습니다', 48]), comments: [] },
    { id: 'T-0004', type: 'INCIDENT', title: '회의실 TV가 켜지지 않습니다', body: '3층 회의실 TV 전원이 들어오지 않습니다.', branchId: 'B2', assetId: '', empNo: P.empNo, name: P.name, dept: P.dept, status: 'RECEIVED', at: seedAgo(2),
      history: log([P, '접수했습니다', 2]), comments: [] }));
  put(ASSET_KEY, list => list.push(
    { id: 'A-DEMO-1', name: '노트북', serial: 'SN-DEMO-0001', branchId: 'B1', empNo: K.empNo, assignedAt: seedAgo(24 * 200), ticketId: '' },
    { id: 'A-DEMO-2', name: '키보드·마우스', serial: 'SN-DEMO-0002', branchId: 'B1', empNo: K.empNo, assignedAt: seedAgo(24 * 200), ticketId: '' },
    { id: 'A-DEMO-3', name: '노트북', serial: 'SN-DEMO-0003', branchId: 'B1', empNo: H.empNo, assignedAt: seedAgo(24 * 150), ticketId: '' }));

  // 5) 이음 장애 문의: 답변 완료 1건(알림은 안 읽음), 답변 대기 1건. 운영자의 "회원 접수"에도 같은 건이 보인다
  const inqs = loadStore(COMPANY_KEY, []);
  const mk = (type, title, body, hours) => ({
    no: iqNextNo(inqs), type, at: seedAgo(hours), company: '유료물산', companyCode: code, writer: K.name, writerEmpNo: K.empNo, coAdmins: 1, title, body,
  });
  const answered = mk('INCIDENT', '비밀번호 찾기에서 인증 코드가 오지 않습니다', '등록한 이메일로 코드를 받으려는데 10분이 지나도 메일이 오지 않습니다.', 52);
  inqs.push(answered);
  const reply = '메일 발송 설정을 확인해 다시 보냈습니다. 스팸함도 함께 확인해 주세요.';
  answered.reply = { body: reply, savedAt: seedAgo(40), editedAt: null };
  answered.notice = { summary: summarize(reply), link: `#inquiry/${answered.no}`, createdAt: seedAgo(40), readAt: null };
  inqs.push(mk('INQUIRY', '연장 결제 안내 메일은 언제 오나요?', '만료 며칠 전에 안내 메일이 오는지 궁금합니다.', 5));
  saveStore(COMPANY_KEY, inqs);

  saveStore(DEMO_SEED_KEY, true);
}
ensureDemoEmployeeData();
