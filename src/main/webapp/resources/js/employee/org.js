// 조직도 (views/employee/org.jsp). 업체 직원 모두 (요구분석서 3.4 사원·조직: 직원은 조직도에서 부서별 구성원과 직급을 본다).
// 부서 카드를 부서 관리에서 정한 순서대로 놓고, 카드마다 구성원의 이름·직급을 보인다. 검색칸은 받은 목록 안에서 이름·부서·직급으로 거른다.
// 순서(부서 순서, 부서장 먼저, 직급 높은 순, 이름순)와 보일 사람(사용 중 계정, 퇴사자 빼고)은 서버가 정해 준다. 화면은 받은 대로 그린다.
// 서버 (주소는 제안. 사원 모듈 담당이 정하면 맞춘다):
//   GET /employee/api/org → { me, departments: [{ departmentId, departmentName, members: [{ employeeId, employeeName, gradeName, head }] }] }
//     me: 내 employeeId. head: 그 부서의 부서장인지 (사원 관리 목록 API의 head 와 같은 기준: department.head_employee_id)
//     departmentId 가 null 인 묶음은 부서가 정해지지 않은 사람들이다(맨 끝에 둔다)
// util.js 의 h, span, showToast 를 쓴다.
(() => {
  const $ = id => document.getElementById(id);
  const state = { loaded: false, me: null, departments: [] };

  function openOrgView() {
    api.get('/employee/api/org')
      .done(r => {
        state.me = r.me;
        state.departments = (r.departments || []).map(d => ({ ...d, members: d.members || [] }));
        state.loaded = true;
        render();
      })
      .fail(xhr => {
        $('orgList').replaceChildren(h('p', 'org-empty', '조직도를 불러오지 못했습니다'));
        showToast(apiMessage(xhr, '조직도를 불러오지 못했습니다'));
      });
  }
  window.openOrgView = openOrgView;

  // 검색어가 부서 이름에 맞으면 그 부서 사람 모두, 아니면 이름·직급이 맞는 사람만 남긴다
  function filtered(q) {
    if (!q) return state.departments;
    const has = text => (text || '').toLowerCase().includes(q);
    return state.departments
      .map(d => (has(deptName(d)) ? d : { ...d, members: d.members.filter(m => has(m.employeeName) || has(m.gradeName)) }))
      .filter(d => d.members.length);
  }

  const deptName = d => (d.departmentId == null ? '부서 미지정' : d.departmentName);

  function render() {
    if (!state.loaded) return; // 받기 전이나 못 받았을 때는 검색해도 그 안내를 그대로 둔다
    const q = $('orgSearch').value.trim().toLowerCase();
    const list = filtered(q);
    const people = list.reduce((n, d) => n + d.members.length, 0);
    const depts = list.filter(d => d.departmentId != null).length; // '부서 미지정' 묶음은 부서 수에 넣지 않는다
    $('orgResult').textContent = q ? `${people}명 찾음` : `${depts}개 부서 · ${people}명`;
    $('orgList').replaceChildren(...(list.length ? list.map(card)
      : [h('p', 'org-empty', q ? '찾는 사람이 없습니다' : '아직 등록된 부서가 없습니다')]));
  }

  function card(d) {
    const rows = d.members.map(m => {
      const head = !!m.head;
      const tags = [...(head ? [span('부서장', 'status status-approved')] : []), ...(m.employeeId === state.me ? [span('나', 'status')] : [])];
      return h('li', 'org-member' + (head ? ' is-head' : ''),
        span((m.employeeName || '?').slice(0, 1), 'org-avatar'),
        h('span', 'org-who', span(m.employeeName, 'org-name'), span(m.gradeName || '직급 없음', 'org-grade')),
        ...(tags.length ? [h('span', 'org-tags', ...tags)] : []));
    });
    return h('section', 'info-card org-card',
      h('header', 'info-card-head', h('h2', '', deptName(d)), span(`${d.members.length}명`, 'org-count')),
      rows.length ? h('ul', 'org-members', ...rows) : h('p', 'org-empty', '구성원이 없습니다'));
  }

  $('orgSearch').addEventListener('input', render);
  $('orgSearchForm').addEventListener('submit', e => e.preventDefault());
})();
