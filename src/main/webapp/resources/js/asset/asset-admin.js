// IT 서비스 > 자산 관리 (views/asset/admin/list.jsp)
// 대상: IT 담당·업체 관리자 (요구분석서 3.4 IT 자산, 통합 계획서 4장 IT 자산)
// 요청서 범위: 상태 카드로 거르는 목록, 지급·회수. 누를 수 있는 버튼은 서버가 준 actions 로 정한다.
//
// 서버(asset 모듈, AssetAdminController)에 맞춘 주소·값
//   GET  /asset/admin/api/assets
//        → [{ id, assetNo, name, serial, kind, status, branchId, version }]  (모든 상태, 이 업체만)
//   GET  /asset/admin/api/assets/{id}
//        → { id, assetNo, name, serial, kind, status, branchId, version, actions: { assign, return } }
//   POST /asset/admin/api/assets/{id}/assign  { employeeId, version }
//   POST /asset/admin/api/assets/{id}/return  { version, reason (1~300자) }
//   - kind: ASSET_TYPE 코드 (util.js assetTypeName 으로 이름을 붙인다)
//   - status: ASSET_STATUS 코드 (AS_*)
//   - 실패: { message }. 409 는 다른 사람이 먼저 바꾼 것이다
//
// 지급할 직원과 지점 이름: 자산 쪽에 목록 API가 없어 결재 작성의 선택 목록을 빌려 쓴다
//   GET /approval/api/form-options
//     approvers: 같은 업체의 재직 직원(나는 빠짐) [{ employeeId, employeeName, departmentName, gradeName }]
//     branches:  사용 중 지점 [{ branchId, branchName }]
//   - 그래서 나에게는 지급할 수 없고, 사용 중지한 지점에 놓인 자산은 지점이 '-'로 보인다
//   - 자산 쪽에 목록이 생기면 그것으로 바꾼다
//
// 서버에 아직 없어 화면에도 없는 것: 사용자·지급일·지급 이력, 자산 등록
// 상태를 바꾸는 요청에는 받은 version 을 같이 보낸다.
//   같은 장비를 두 사람에게 동시에 지급하려 하면 서버가 409 로 거절하고 화면은 새로 받는다.
// 장비 지급 신청서로 생긴 지급은 티켓 처리 화면에서 한다(그 티켓과 함께 끝나야 해서).
//   여기 지급은 신청 없이 바로 주는 경우다.
// util.js 의 h, span, inqCell, titleBtn, dataRow, detailTr, emptyTr, dlOf, cardOf,
//   writeBtn, textBox, pickSelect, assetTypeName 을 쓴다.
(() => {
  // 공통 코드 ASSET_STATUS 이름 그대로
  const STATUS = {
    AS_AVAILABLE: '지급 가능',
    AS_IN_USE: '사용 중',
    AS_INACTIVE: '사용 중지',
    AS_DISPOSED: '폐기',
  };
  const STATUS_CLASS = {
    AS_AVAILABLE: 'requested',
    AS_IN_USE: 'active',
    AS_INACTIVE: 'expired',
    AS_DISPOSED: 'failed',
  };
  // 상태 카드: 전체와 지급·회수에 쓰는 두 상태. 사용 중지·폐기는 전체에서 보인다
  const EMPTY = {
    ALL: '등록된 자산이 없습니다',
    AS_AVAILABLE: '지급할 수 있는 자산이 없습니다',
    AS_IN_USE: '사용 중인 자산이 없습니다',
  };
  const REASON_MAX = 300; // 서버(AssetAllocationService.returnAsset)와 같은 값
  const COLS = 6;

  const $ = id => document.getElementById(id);
  const state = {
    filter: 'ALL',     // 고른 상태 카드
    open: null,        // 펼친 자산 id
    detail: null,      // 펼친 자산의 상세 응답
    seq: 0,            // 늦게 온 목록 응답을 버리기 위한 번호
    items: null,       // 받은 전체 목록
    people: [],        // 지급할 수 있는 직원
    branches: new Map(), // 지점 id → 이름
  };

  function openAssetAdmin() {
    api.get('/approval/api/form-options')
      .done(o => {
        state.people = (o && o.approvers) || [];
        state.branches = new Map(((o && o.branches) || []).map(b => [b.branchId, b.branchName]));
        if (state.items) render();
      })
      .fail(xhr => showToast(apiMessage(xhr, '직원·지점 목록을 불러오지 못했습니다. 지급할 직원을 고를 수 없습니다')));
    load();
  }
  window.openAssetAdmin = openAssetAdmin;

  const branchName = id => (id != null && state.branches.get(id)) || '-';
  // '홍길동 (개발팀 · 대리)'. 부서·직급이 없으면 이름만
  const personLabel = p => {
    const sub = [p.departmentName, p.gradeName].filter(Boolean).join(' · ');
    return sub ? `${p.employeeName} (${sub})` : p.employeeName;
  };

  // ---------- 목록: 한 번에 받아 상태 카드로 화면에서 거른다 ----------
  function load() {
    const my = ++state.seq;
    api.get('/asset/admin/api/assets')
      .done(list => {
        if (my !== state.seq) return; // 늦게 온 앞 응답은 버린다
        state.items = list || [];
        render();
      })
      .fail(xhr => showToast(apiMessage(xhr, '자산 목록을 불러오지 못했습니다')));
  }

  function render() {
    const all = state.items || [];
    const count = k => (k === 'ALL' ? all.length : all.filter(a => a.status === k).length);
    Object.keys(EMPTY).forEach(k => { $(`aaCnt${k}`).textContent = `${count(k)}개`; });
    document.querySelectorAll('#aaFilters .card-filter')
      .forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === state.filter));

    const items = all.filter(a => state.filter === 'ALL' || a.status === state.filter);
    // 열어 둔 자산이 바꾸면서 이 카드에서 빠져도(지급 가능 → 사용 중 등) 맨 위에 남겨 결과를 보게 한다
    const d = state.detail;
    if (state.open && d && d.id === state.open && !items.some(a => a.id === state.open)) {
      items.unshift(d);
    }

    const rows = items.flatMap(a => {
      const isOpen = state.open === a.id;
      const tr = dataRow(isOpen, () => toggle(a.id),
        inqCell('자산번호', titleBtn(a.assetNo, isOpen)),
        inqCell('종류', span(assetTypeName(a.kind) || '-')),
        inqCell('이름', span(a.name || '-')),
        inqCell('일련번호', span(a.serial || '-')),
        inqCell('배치 지점', span(branchName(a.branchId))),
        inqCell('상태', statusBadge(a.status)));
      if (!isOpen) return [tr];
      const content = d && d.id === a.id ? detailView(d) : span('불러오는 중…', 'muted');
      return [tr, detailTr(COLS, content)];
    });
    $('aaList').replaceChildren(...(rows.length ? rows : [emptyTr(COLS, EMPTY[state.filter])]));
  }

  const statusBadge = s => span(STATUS[s] || s, `status status-${STATUS_CLASS[s] || 'requested'}`);

  // 카드를 누르면 그 상태만 보이고, 열린 상세는 닫는다(목록은 이미 받아 두었다)
  document.querySelectorAll('#aaFilters .card-filter').forEach(b => b.addEventListener('click', () => {
    Object.assign(state, { filter: b.dataset.filter, open: null, detail: null });
    render();
  }));

  // ---------- 상세 ----------
  function toggle(id) {
    state.open = state.open === id ? null : id;
    state.detail = null;
    render();
    if (state.open) loadDetail(id);
  }

  function loadDetail(id) {
    api.get(`/asset/admin/api/assets/${encodeURIComponent(id)}`)
      .done(d => {
        if (state.open !== id) return; // 그사이 다른 줄을 열었다
        state.detail = d;
        render();
      })
      .fail(xhr => {
        showToast(apiMessage(xhr, '자산을 불러오지 못했습니다'));
        state.open = null;
        render();
      });
  }

  // 바꾼 뒤(또는 409 로 거절된 뒤) 상세를 먼저 새로 받고 목록을 받는다(카드에서 빠진 열린 자산을 위에 남기려고)
  function refresh() {
    const id = state.open;
    if (!id) { load(); return; }
    api.get(`/asset/admin/api/assets/${encodeURIComponent(id)}`)
      .done(d => { if (state.open === id) state.detail = d; })
      .fail(() => { state.open = null; state.detail = null; })
      .always(load);
  }

  function detailView(d) {
    const left = cardOf('자산 정보', dlOf([
      ['자산번호', d.assetNo],
      ['종류', assetTypeName(d.kind) || '-'],
      ['이름', d.name || '-'],
      ['일련번호', d.serial || '-'],
      ['배치 지점', branchName(d.branchId)],
      ['상태', STATUS[d.status] || d.status],
    ]));

    const actions = actionBlocks(d);
    let right;
    if (actions.length) {
      right = h('section', 'info-card', h('header', 'info-card-head', h('h2', '', '지급·회수')), ...actions);
    } else {
      // 버튼이 없는 이유를 알린다. 사용 중인데 회수가 없으면 서버의 지급 기록이 맞지 않는 경우다
      const why = d.status === 'AS_IN_USE'
        ? '지급 기록을 확인할 수 없어 회수할 수 없습니다. 자산 담당에게 알려 주세요.'
        : `${STATUS[d.status] || d.status} 상태라 지급·회수할 수 없습니다.`;
      right = cardOf('지급·회수', h('p', 'inq-body inq-hint', why));
    }

    const grid = h('div', 'inq-grid', left, right);
    grid.addEventListener('click', e => e.stopPropagation()); // 상세 안을 눌러도 줄이 접히지 않게
    return grid;
  }

  // 서버가 준 actions 마다 칸을 만든다: 지급 가능이면 지급, 사용 중이면 회수
  function actionBlocks(d) {
    const a = d.actions || {};
    const blocks = [];

    if (a.assign) {
      const options = state.people.map(p => [personLabel(p), p.employeeId]);
      const who = pickSelect('지급할 직원', '직원 고르기', options);
      const go = writeBtn('지급', 'btn btn-primary', b => {
        if (!who.value) { showToast('지급할 직원을 골라 주세요'); who.focus(); return; }
        post(d, 'assign', { employeeId: Number(who.value) }, b, '지급했습니다');
      });
      blocks.push(h('div', 'inq-body', h('div', 'inq-actions', who, go)));
    }

    if (a.return) {
      // 회수: 사유를 서버 이력에 남긴다
      const reason = textBox(`회수 사유 (${REASON_MAX}자까지)`, REASON_MAX, '회수 사유');
      const go = writeBtn('회수', 'btn', b => {
        const text = reason.value.trim();
        if (!text) { showToast('회수 사유를 적어 주세요'); reason.focus(); return; }
        post(d, 'return', { reason: text }, b, '회수했습니다. 지급 가능으로 돌아갑니다');
      });
      blocks.push(h('div', 'inq-body', reason, h('div', 'inq-actions', go)));
    }

    return blocks;
  }

  // 상태 바꾸기: version 을 같이 보낸다. 409(다른 사람이 먼저 바꿈)면 새로 받는다
  function post(d, action, body, btn, done) {
    btn.disabled = true;
    api.post(`/asset/admin/api/assets/${encodeURIComponent(d.id)}/${action}`, { ...body, version: d.version })
      .done(() => {
        showToast(done);
        refresh();
      })
      .fail(xhr => {
        if (xhr.status === 409) {
          showToast(apiMessage(xhr, '다른 사람이 먼저 바꿨습니다. 새로 불러왔습니다'));
          refresh();
          return;
        }
        showToast(apiMessage(xhr));
        btn.disabled = false;
      });
  }
})();
