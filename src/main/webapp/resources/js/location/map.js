// 공통·개인 지도 (location/map.jsp). 카카오 지도 위에 우리 업체의 공통 마커나 내 개인 마커를 보여 주고,
// 주소·장소 검색이나 지도 누르기로 위치를 골라 마커를 추가한다.
// - 어느 지도인지는 map.jsp의 data-scope로 정해지고, 다른 점은 부르는 서버 주소뿐이다. 화면 동작은 같다.
// - 공통 지도: 마커 등록은 누구나, 수정·삭제 버튼은 서버가 canEdit으로 알려 준 사람(업체 관리자·인사 담당)에게만 보인다. 서버도 다시 검사한다.
// - 개인 지도: 본인 마커만 오고 canEdit은 늘 true다. 서버가 소유자를 다시 검사한다.
// - 지도를 못 불러와도 마커 목록은 보인다(계획서 6장). 그때는 검색·추가를 막고 안내만 한다.
// - 카카오 스크립트는 목록을 먼저 읽은 뒤 여기서 넣는다. 카카오가 응답 없이 멈춰도 목록과 안내는 나오게 하려는 것.
// - 위치 고르기: 지도 빈 곳을 누르거나 검색 결과를 고르면 바로 창을 열지 않고, 회색 핀과 '이 위치에 마커 추가' 말풍선을 먼저 띄운다.
//   기존 핀·말풍선을 누르면 그 마커를 고르기만 한다(추가 창이 뜨지 않음).
// - 추가·수정 창에서 주소를 직접 고쳐 [주소 찾기]로 위치를 바꿀 수 있다. 찾지 않고 고친 주소는 저장하지 않는다(글자 주소와 핀 위치가 달라지지 않게).
// - 쓰기 버튼(저장·수정·삭제)에는 data-write를 붙인다: 체험이 끝난 업체에서는 흐려지고 안내가 뜬다(common.js).
// 서버 주소: GET /location/api/map/markers → { markers, canEdit }
//            POST /location/api/map/markers, POST /location/api/map/markers/{id}, POST /location/api/map/markers/{id}/delete
//            개인 지도는 markers 앞에 personal/ 이 붙는다 (GET /location/api/map/personal/markers ...)
// 사용자가 쓴 글은 모두 textContent로 넣는다 (HTML로 해석되지 않게).

const MAP_API = document.getElementById('view-map').dataset.scope === 'personal'
  ? '/location/api/map/personal/markers'
  : '/location/api/map/markers';
const MAP_DESC_MAX = 1000;
const MAP_CENTER = { lat: 37.5665, lng: 126.9780 }; // 마커가 없을 때 처음 보이는 곳 (서울시청)
const MAP_LOAD_WAIT = 8000; // 카카오 지도가 이 시간 안에 안 열리면 실패로 본다 (응답 없이 멈추거나 키·도메인이 틀린 경우)
const MAP_SDK_URL = 'https://dapi.kakao.com/v2/maps/sdk.js';

const mapById = id => document.getElementById(id);
function mapEl(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined && text !== null) e.textContent = text;
  return e;
}
// 쓰기로 이어지는 버튼: 체험이 끝난 읽기 전용 업체에서는 흐려지고 누르면 안내만 뜬다(common.js의 data-write).
// 창을 열어 다 쓴 뒤에야 저장이 막히는 일이 없게, 창을 여는 버튼에도 붙인다
function mapWriteButton(cls, text, onClick) {
  const b = mapButton(cls, text, onClick);
  b.setAttribute('data-write', '');
  return b;
}
function mapButton(cls, text, onClick) {
  const b = mapEl('button', cls, text);
  b.type = 'button';
  if (onClick) b.addEventListener('click', onClick);
  return b;
}

// 핀 그림 (우리 파란색). 고른 핀은 크고 진하게, 저장 전 위치는 회색
function mapPinSvg(fill) {
  return 'data:image/svg+xml,' + encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 28 38"><path d="M14 37s12-12.2 12-22.5A12 12 0 0 0 2 14.5C2 24.8 14 37 14 37z" fill="${fill}" stroke="#ffffff" stroke-width="2"/><circle cx="14" cy="14.5" r="4.5" fill="#ffffff"/></svg>`);
}
function mapPinImage(fill, w, h) {
  return new kakao.maps.MarkerImage(mapPinSvg(fill), new kakao.maps.Size(w, h), { offset: new kakao.maps.Point(w / 2, h) });
}
const MAP_PIN_H = 33;          // 보통 핀 높이
const MAP_PIN_SELECTED_H = 42; // 고른 핀 높이 (말풍선을 이만큼 위에 띄운다)

const mapState = {
  markers: [],       // 서버가 준 마커 목록
  canEdit: false,    // 수정·삭제 버튼을 보일지
  selectedId: null,  // 고른 마커
  filter: '',        // 목록에서 찾기 글자 (비어 있으면 전부)
  ok: false,         // 카카오 지도가 열렸는지
  failed: false,     // 실패로 정했는지 (8초가 지난 뒤 늦게 열려도 다시 쓰지 않는다)
  map: null, geocoder: null, places: null,
  images: null,      // 핀 그림: { normal, hover, selected, pick }
  pins: new Map(),   // 마커 번호 → 지도 위 핀
  bubble: null,      // 고른 마커 위 말풍선 (CustomOverlay)
  pickPin: null,     // 고른(아직 저장 전) 위치의 회색 핀
  pickCard: null,    // 회색 핀 위 '이 위치에 마커 추가' 말풍선 (CustomOverlay)
  pickSeq: 0,        // 지도 누르기 순번: 주소 응답이 늦게 오면 그사이 새로 누르거나 취소한 것이므로 버린다
  formSeq: 0,        // 창 안 [주소 찾기] 순번: 창을 닫거나 다른 창을 연 뒤 늦게 온 결과는 버린다
  form: null,        // 창의 값 { id(수정이면), title, address, detailAddress, lat, lng, description }
  confirmedAddress: '', // 위치와 짝이 맞는 주소 (검색·지도로 고른 주소). 창의 주소 칸이 이것과 다르면 저장하지 않는다
  relocating: false, // 수정 중에 지도에서 위치를 다시 고르는 중인지
  saving: false,
  sort: 'recent',    // 목록 순서: recent(최근 수정순, 서버가 준 순서) | name(이름순)
  formStart: '',     // 창을 열 때의 값. 닫을 때 이것과 다르면 '작성 중인 내용' 확인을 띄운다
  lastFocus: null,   // 확인 창을 띄우기 전 커서가 있던 칸. [계속 쓰기]로 돌아오면 그 칸으로 커서를 돌려준다
};

// ---------- 시작 ----------

function openMapView() {
  mapById('mapSearchForm').addEventListener('submit', e => {
    e.preventDefault();
    mapSearchTo(mapById('mapQuery').value.trim(), mapById('mapResults'), r => mapOfferPick(r, true));
  });
  mapById('mapForm').addEventListener('submit', e => { e.preventDefault(); mapSave(); });
  mapById('mfCancel').addEventListener('click', mapAskClose);
  // Esc로 닫을 때도 같은 확인을 거친다. 브라우저의 cancel만 막으면, Esc를 연달아 누를 때 Chrome이 막을 수 없는
  // cancel을 보내 창이 그냥 닫히고 쓰던 내용이 사라진다. 그래서 창이 열려 있는 동안 Esc 키는 페이지 전체에서 먼저 잡는다
  // (확인 창을 닫은 직후에는 글자 칸에 커서가 없어 창 안의 keydown만으로는 놓친다)
  // - 확인 창이 떠 있으면: Esc = [계속 쓰기] (확인 창만 닫고 쓰던 칸으로 돌아간다)
  // - 아니면: 고친 내용이 있는지 보고 확인 창을 띄우거나 닫는다
  // - 한글을 조합하는 중(예: '가나ㄷ')의 Esc는 조합 취소이므로 건드리지 않는다
  document.addEventListener('keydown', e => {
    if (e.key !== 'Escape' || e.isComposing || !mapById('mapDialog').open) return;
    e.preventDefault();
    const discard = mapById('mapDiscardDialog');
    if (discard.open) { discard.close(); mapKeepWriting(); return; }
    mapAskClose();
  }, true);
  mapById('mapDialog').addEventListener('cancel', e => { e.preventDefault(); if (e.cancelable) mapAskClose(); });
  mapById('mapDialog').addEventListener('close', () => {
    mapState.formSeq++; // 찾던 주소 결과는 버린다
    mapById('mfResults').replaceChildren();
    if (!mapState.relocating) mapClearPick();
  });
  mapById('mfRepick').addEventListener('click', mapStartRelocate);
  mapById('mapRelocateCancel').addEventListener('click', () => { mapEndRelocate(); mapClearPick(); mapOpenForm(mapState.form, true); });
  mapById('mfDesc').addEventListener('input', mapUpdateCount);
  // 창 안에서 주소 찾기: 버튼을 누르거나 주소 칸에서 Enter
  mapById('mfFind').addEventListener('click', mapFindInForm);
  mapById('mfAddress').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); mapFindInForm(); } });
  mapById('mfAddress').addEventListener('input', mapUpdateAddressNote);
  mapById('mapFilter').addEventListener('input', e => mapApplyFilter(e.target.value));
  mapById('mapFitAll').addEventListener('click', () => mapFitAll());
  mapById('mapSort').querySelectorAll('button').forEach(b => b.addEventListener('click', () => {
    mapState.sort = b.dataset.sort;
    mapRenderList();
  }));

  mapLoad();        // 1. 마커 목록 먼저 (지도와 상관없이 보인다)
  mapInitKakao();   // 2. 그다음 카카오 지도
}

// 카카오 지도 열기: 스크립트를 넣고, 8초 안에 지도가 안 열리면 안내를 띄운다.
// 키가 없거나 스크립트를 못 받으면 바로 안내. 8초가 지난 뒤 늦게 열리면 그 지도는 쓰지 않는다
function mapInitKakao() {
  const key = (mapById('view-map').dataset.kakaoKey || '').trim();
  if (!key) { mapFail(); return; }
  const timer = setTimeout(mapFail, MAP_LOAD_WAIT);
  const script = document.createElement('script');
  script.src = `${MAP_SDK_URL}?appkey=${encodeURIComponent(key)}&libraries=services&autoload=false`;
  script.onerror = () => { clearTimeout(timer); mapFail(); };
  script.onload = () => {
    if (mapState.failed) return;
    if (!window.kakao || !kakao.maps || !kakao.maps.load) { clearTimeout(timer); mapFail(); return; }
    kakao.maps.load(() => {
      if (mapState.failed) return; // 이미 실패 안내를 띄웠다
      try {
        mapState.map = new kakao.maps.Map(mapById('mapView'), {
          center: new kakao.maps.LatLng(MAP_CENTER.lat, MAP_CENTER.lng), level: 5,
        });
        mapState.map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
        mapState.map.addControl(new kakao.maps.MapTypeControl(), kakao.maps.ControlPosition.TOPRIGHT); // 일반·위성 지도
        mapState.geocoder = new kakao.maps.services.Geocoder();
        mapState.places = new kakao.maps.services.Places();
        mapState.images = {
          normal: mapPinImage('#2563eb', 24, MAP_PIN_H),
          hover: mapPinImage('#1d4ed8', 27, 37),
          selected: mapPinImage('#1e3a8a', 31, MAP_PIN_SELECTED_H),
          pick: mapPinImage('#8a94a3', 24, MAP_PIN_H),
        };
        mapState.bubble = new kakao.maps.CustomOverlay({ yAnchor: 1, zIndex: 5 });
        mapState.pickCard = new kakao.maps.CustomOverlay({ yAnchor: 1, zIndex: 6 });
        // 빈 곳을 누르면 그 위치를 고른다 (기존 핀은 clickable이라 여기까지 오지 않는다)
        // 0.25초 기다렸다가 고른다: 그사이 한 번 더 누르면(더블클릭 = 확대) 고르기를 취소한다.
        // 바로 고르면 지도가 누른 곳으로 움직이면서 말풍선이 마우스 밑으로 와서, 두 번째 클릭이 말풍선을 누르게 된다
        kakao.maps.event.addListener(mapState.map, 'click', e => {
          const seq = ++mapState.pickSeq;
          setTimeout(() => { if (seq === mapState.pickSeq) mapPickAt(e.latLng); }, 250);
        });
        kakao.maps.event.addListener(mapState.map, 'dblclick', () => { mapState.pickSeq++; });
        clearTimeout(timer);
        mapState.ok = true;
        mapDrawPins(true);
        if (mapState.selectedId !== null) mapFocus(mapState.selectedId);
        mapUpdateMapTools();
        // 지도가 열리기 전에 수정 창을 열어 두었으면, 이제 주소 칸·버튼 잠금을 풀고 그 위치에 회색 핀을 꽂는다
        mapUpdateFormTools();
        const f = mapState.form;
        if (mapById('mapDialog').open && f && f.lat !== undefined && f.lat !== null) mapShowPick(f.lat, f.lng, true);
      } catch (err) {
        clearTimeout(timer);
        mapFail();
      }
    });
  };
  document.head.append(script);
}

function mapFail() {
  if (mapState.failed) return;
  mapState.failed = true;
  mapState.ok = false;
  mapById('mapView').hidden = true;
  mapById('mapFail').hidden = false;
  mapById('mapHint').textContent = '지도를 불러오지 못해 지금은 마커를 추가할 수 없어요. 목록은 아래에서 볼 수 있어요.';
}

// 지도 위 말풍선 안을 누르면 지도까지 눌린 것으로 치지 않게 한다 (지도 '누르기 → 위치 고르기'가 같이 일어나지 않게)
function mapStopMap(el) {
  ['mousedown', 'touchstart', 'click', 'dblclick'].forEach(type =>
    el.addEventListener(type, e => { e.stopPropagation(); if (window.kakao && kakao.maps.event.preventMap) kakao.maps.event.preventMap(); }));
}

// ---------- 마커 목록 ----------

// 서버에서 마커를 다시 받는다. selectId를 주면 그 마커를 골라 보여 준다 (방금 저장한 마커)
function mapLoad(selectId) {
  api.get(MAP_API)
    .done(res => {
      mapState.markers = res.markers || [];
      mapState.canEdit = !!res.canEdit;
      if (selectId !== undefined) mapState.selectedId = selectId;
      if (!mapState.markers.some(m => m.id === mapState.selectedId)) mapState.selectedId = null;
      mapRenderList();
      // 방금 저장한 마커가 목록 화면 밖(이름순이면 아래쪽일 수 있다)에 있으면 그 줄까지 내린다
      const on = selectId !== undefined && mapById('mapList').querySelector('li.is-on');
      if (on) mapScrollListTo(on);
      mapDrawPins(selectId === undefined);
      if (mapState.selectedId !== null) mapFocus(mapState.selectedId);
    })
    .fail(xhr => showToast(apiMessage(xhr, '마커를 불러오지 못했습니다')));
}

// 목록에서 찾기 글자에 맞는 마커 (제목·주소·상세주소·설명, 대소문자·띄어쓰기 무시)
function mapVisibleMarkers() {
  const q = mapState.filter.replace(/\s+/g, '').toLowerCase();
  if (!q) return mapState.markers;
  return mapState.markers.filter(m =>
    [m.title, m.address, m.detailAddress, m.description].some(v => v && v.replace(/\s+/g, '').toLowerCase().includes(q)));
}

function mapApplyFilter(text) {
  mapState.filter = text.trim();
  const visible = mapVisibleMarkers();
  if (mapState.selectedId !== null && !visible.some(m => m.id === mapState.selectedId)) {
    mapState.selectedId = null;
    if (mapState.bubble) mapState.bubble.setMap(null);
  }
  mapRenderList();
  mapDrawPins(false);
}

// 목록에 보일 순서. 최근 수정순은 서버가 준 순서 그대로, 이름순은 한글 가나다순 (숫자는 크기대로: 지점 2 → 지점 10)
function mapSorted(markers) {
  if (mapState.sort !== 'name') return markers;
  return [...markers].sort((a, b) => String(a.title).localeCompare(String(b.title), 'ko', { numeric: true }));
}

function mapRenderList() {
  const list = mapById('mapList');
  const visible = mapSorted(mapVisibleMarkers());
  const total = mapState.markers.length;
  mapById('mapCount').textContent = mapState.filter ? `${visible.length} / ${total}` : total;
  mapById('mapEmpty').hidden = total > 0;
  mapById('mapNoMatch').hidden = !(total > 0 && visible.length === 0);
  mapById('mapFilter').hidden = total === 0 && !mapState.filter; // 마커가 없으면 찾을 것도 없다
  mapById('mapSort').hidden = total < 2; // 두 개 이상일 때만 순서가 의미 있다
  mapById('mapSort').querySelectorAll('button').forEach(b => {
    const on = b.dataset.sort === mapState.sort;
    b.classList.toggle('is-on', on);
    b.setAttribute('aria-pressed', on);
  });
  mapUpdateMapTools();
  list.replaceChildren(...visible.map(m => {
    const li = mapEl('li', m.id === mapState.selectedId ? 'is-on' : '');
    const btn = mapButton('map-item', null, () => mapSelect(m.id === mapState.selectedId ? null : m.id, true));
    btn.setAttribute('aria-expanded', m.id === mapState.selectedId);
    btn.append(mapEl('strong', '', m.title), mapEl('small', '', m.address));
    // 목록에 마우스를 올리면 지도의 그 핀을 살짝 키운다
    li.addEventListener('mouseenter', () => mapHoverPin(m.id, true));
    li.addEventListener('mouseleave', () => mapHoverPin(m.id, false));
    li.append(btn);
    if (m.id === mapState.selectedId) li.append(mapDetail(m));
    return li;
  }));
}

// 고른 마커의 자세한 내용: 상세주소·설명(이름표를 붙여서), 길찾기·카카오맵 링크, (canEdit이면) 수정·삭제
function mapDetail(m) {
  const box = mapEl('div', 'map-detail');
  const dl = mapEl('dl', 'map-info');
  const row = (label, value) => { if (value) dl.append(mapEl('dt', '', label), mapEl('dd', '', value)); };
  row('상세주소', m.detailAddress);
  row('설명', m.description);
  if (dl.childElementCount) box.append(dl);

  const actions = mapEl('div', 'map-actions');
  // 카카오맵 링크: 이름에 쉼표가 있으면 링크 형식이 깨지므로 빈칸으로 바꾼다
  const name = encodeURIComponent(String(m.title).replace(/,/g, ' '));
  const link = (label, url) => {
    const a = mapEl('a', 'btn', label);
    a.href = url;
    a.target = '_blank';
    a.rel = 'noopener';
    return a;
  };
  actions.append(
    link('길찾기', `https://map.kakao.com/link/to/${name},${m.lat},${m.lng}`),
    link('카카오맵', `https://map.kakao.com/link/map/${name},${m.lat},${m.lng}`));
  if (mapState.canEdit) {
    const edit = mapButton('btn', '수정', () => mapOpenForm({ ...m }));
    edit.setAttribute('data-write', '');
    const del = mapButton('btn map-del', '삭제', () => mapDelete(m));
    del.setAttribute('data-write', '');
    actions.append(edit, del);
  }
  box.append(actions);
  // 수정·삭제 버튼이 없는 사람에게 이유를 알려 준다
  if (!mapState.canEdit) box.append(mapEl('p', 'map-perm-note', '수정·삭제는 관리자·인사 담당에게 요청하세요.'));
  if (m.updatedAt) box.append(mapEl('p', 'map-when', `${whenFull(m.updatedAt)} 수정`));
  return box;
}

function mapSelect(id, moveMap) {
  mapState.pickSeq++; // 아직 안 온 지도 누르기 응답은 버린다 (빈 곳을 누르고 바로 핀을 고른 경우)
  mapState.selectedId = id;
  if (id !== null) mapClearPick(); // 기존 마커를 고르면 고르던 새 위치는 접는다
  mapRenderList();
  // 지도에서 핀을 눌렀을 때도 목록의 그 줄(수정·삭제·길찾기 칸)이 보이게 한다
  const on = mapById('mapList').querySelector('li.is-on');
  if (on) mapScrollListTo(on);
  mapPaintPins();
  if (id !== null && moveMap) mapFocus(id);
  if (id === null && mapState.bubble) mapState.bubble.setMap(null);
}

// 목록 칸 안에서만 그 줄이 보이게 스크롤한다.
// scrollIntoView는 페이지 전체도 움직여서, 휴대폰(지도 위·목록 아래)에서 핀을 누르면 지도가 화면 밖으로 밀려났다
function mapScrollListTo(li) {
  const list = mapById('mapList');
  const lr = list.getBoundingClientRect();
  const r = li.getBoundingClientRect();
  let delta = 0;
  if (r.top < lr.top) delta = r.top - lr.top;
  else if (r.bottom > lr.bottom) delta = Math.min(r.bottom - lr.bottom, r.top - lr.top);
  if (delta) list.scrollBy({ top: delta, behavior: 'smooth' });
}

// ---------- 지도 위 핀 ----------

// 마커마다 핀을 다시 꽂는다. fit이면 모든 핀이 보이게 지도 범위를 맞춘다
function mapDrawPins(fit) {
  if (!mapState.ok) return;
  mapState.pins.forEach(pin => pin.setMap(null));
  mapState.pins.clear();
  mapVisibleMarkers().forEach(m => {
    const pos = new kakao.maps.LatLng(m.lat, m.lng);
    // clickable: 핀을 누르면 지도 '누르기'(위치 고르기)는 일어나지 않고 그 마커만 고른다
    const pin = new kakao.maps.Marker({ map: mapState.map, position: pos, title: m.title, clickable: true, image: mapState.images.normal });
    kakao.maps.event.addListener(pin, 'mouseover', () => mapHoverPin(m.id, true));
    kakao.maps.event.addListener(pin, 'mouseout', () => mapHoverPin(m.id, false));
    kakao.maps.event.addListener(pin, 'click', () => mapSelect(m.id, true));
    mapState.pins.set(m.id, pin);
  });
  if (fit) mapFitAll();
  mapPaintPins();
}

// 보이는 핀이 모두 한 화면에 들어오게 (하나뿐이면 그 핀을 가운데로 알맞게 확대)
function mapFitAll() {
  if (!mapState.ok) return;
  const visible = mapVisibleMarkers();
  if (!visible.length) return;
  if (visible.length === 1) {
    mapState.map.setLevel(4);
    mapState.map.setCenter(new kakao.maps.LatLng(visible[0].lat, visible[0].lng));
    return;
  }
  const bounds = new kakao.maps.LatLngBounds();
  visible.forEach(m => bounds.extend(new kakao.maps.LatLng(m.lat, m.lng)));
  mapState.map.setBounds(bounds);
}

// 지도 위 도구: [전체 보기]는 핀이 있을 때만, 빈 지도 안내는 마커가 하나도 없을 때만
function mapUpdateMapTools() {
  const ok = mapState.ok;
  mapById('mapFitAll').hidden = !ok || mapVisibleMarkers().length === 0;
  mapById('mapEmptyHint').hidden = !ok || mapState.markers.length > 0;
}

// 핀 그림을 지금 고른 마커에 맞춘다
function mapPaintPins() {
  if (!mapState.ok) return;
  mapState.pins.forEach((pin, id) => {
    const on = id === mapState.selectedId;
    pin.setImage(on ? mapState.images.selected : mapState.images.normal);
    pin.setZIndex(on ? 3 : 1);
  });
}

function mapHoverPin(id, on) {
  if (!mapState.ok || id === mapState.selectedId) return;
  const pin = mapState.pins.get(id);
  if (!pin) return;
  pin.setImage(on ? mapState.images.hover : mapState.images.normal);
  pin.setZIndex(on ? 2 : 1);
}

// 그 마커로 지도를 옮기고 제목 말풍선을 띄운다. 말풍선을 눌러도 지도 '누르기'가 되지 않는다
function mapFocus(id) {
  if (!mapState.ok) return;
  const m = mapState.markers.find(x => x.id === id);
  const pin = mapState.pins.get(id);
  if (!m || !pin) return;
  mapState.map.panTo(pin.getPosition());
  const wrap = mapEl('div', 'map-bubble-wrap');
  wrap.style.paddingBottom = `${MAP_PIN_SELECTED_H + 6}px`;
  const bubble = mapEl('div', 'map-bubble');
  bubble.append(mapEl('strong', '', m.title), mapEl('small', '', m.address)); // 글자로만 넣는다
  mapStopMap(bubble);
  wrap.append(bubble);
  mapState.bubble.setContent(wrap);
  mapState.bubble.setPosition(pin.getPosition());
  mapState.bubble.setMap(mapState.map);
}

// ---------- 위치 찾기 (검색) ----------

// 주소로 먼저 찾고, 없으면 장소 이름으로 찾아 box에 결과를 보여 준다. 결과를 누르면 onPick(위치)
// alive를 주면 응답이 왔을 때 물어보고, false면(그사이 창을 닫았거나 다른 창을 열었으면) 결과를 보이지 않는다
function mapSearchTo(q, box, onPick, alive) {
  if (!q) { showToast('주소나 장소 이름을 입력해 주세요'); return; }
  if (!mapState.ok) { showToast('지도를 불러오지 못해 검색할 수 없어요'); return; }
  const S = kakao.maps.services.Status;
  const stale = () => alive && !alive();
  const show = items => stale() || box.replaceChildren(...items.map(r => {
    const li = mapEl('li');
    const btn = mapButton('', null, () => { box.replaceChildren(); onPick(r); });
    btn.append(mapEl('strong', '', r.label));
    if (r.sub) btn.append(mapEl('small', '', r.sub));
    li.append(btn);
    return li;
  }));
  mapState.geocoder.addressSearch(q, (res, status) => {
    if (status === S.OK && res.length) {
      show(res.slice(0, 8).map(r => ({
        title: '',
        label: r.road_address ? r.road_address.address_name : r.address_name, // 도로명 주소를 크게
        address: r.road_address ? r.road_address.address_name : r.address_name,
        sub: r.road_address ? `지번 ${r.address_name}` : '',
        lat: +r.y, lng: +r.x,
      })));
      return;
    }
    mapState.places.keywordSearch(q, (places, st) => {
      if (st === S.OK && places.length) {
        show(places.slice(0, 8).map(p => ({
          title: p.place_name, label: p.place_name,
          address: p.road_address_name || p.address_name,
          sub: p.road_address_name || p.address_name,
          lat: +p.y, lng: +p.x,
        })));
      } else if (!stale()) {
        // 장소 검색이 오류면 주소 검색 결과와 상관없이 '검색하지 못함'
        box.replaceChildren(mapEl('li', 'map-results-none', st === S.ZERO_RESULT
          ? '검색 결과가 없습니다. 도로명 주소나 다른 이름으로 찾아보세요.'
          : '검색하지 못했습니다. 잠시 뒤 다시 해 주세요.'));
      }
    });
  });
}

// ---------- 위치 고르기 (지도 누르기 · 검색 결과) ----------

// 지도 빈 곳을 누른 곳의 주소를 찾아 '이 위치에 마커 추가' 말풍선을 띄운다
function mapPickAt(latLng) {
  if (mapById('mapDialog').open) return;
  const lat = latLng.getLat();
  const lng = latLng.getLng();
  const seq = ++mapState.pickSeq;
  mapState.geocoder.coord2Address(lng, lat, (res, status) => {
    if (seq !== mapState.pickSeq) return; // 그사이 다시 눌렀거나 취소했다
    if (status !== kakao.maps.services.Status.OK || !res.length) {
      showToast('이 위치의 주소를 찾지 못했어요. 도로나 건물 가까이를 눌러 보세요');
      return;
    }
    const r = res[0];
    const address = r.road_address ? r.road_address.address_name : r.address.address_name;
    // 누른 곳을 지도 가운데로 옮긴다: 가장자리를 눌렀을 때 말풍선이 지도 밖으로 잘리지 않게
    mapOfferPick({ title: r.road_address && r.road_address.building_name ? r.road_address.building_name : '', address, lat, lng }, true);
  });
}

// 고른 위치에 회색 핀과 말풍선을 띄운다. 창은 말풍선의 버튼을 눌러야 열린다 (실수로 누른 것이면 닫거나 다른 곳을 누르면 된다)
function mapOfferPick(r, moveMap) {
  if (mapById('mapDialog').open || !mapState.ok) return;
  mapState.pickSeq++;
  mapById('mapResults').replaceChildren(); // 위 검색 결과를 고르지 않고 지도를 눌렀으면 남은 결과 목록은 접는다
  mapSelect(null, false);
  mapShowPick(r.lat, r.lng, moveMap);

  const wrap = mapEl('div', 'map-bubble-wrap');
  wrap.style.paddingBottom = `${MAP_PIN_H + 6}px`;
  const card = mapEl('div', 'map-pick');
  if (r.title) card.append(mapEl('strong', '', r.title));
  card.append(mapEl('small', '', r.address));
  const actions = mapEl('div', 'map-pick-actions');
  actions.append(
    mapWriteButton('btn btn-primary', mapState.relocating ? '이 위치로 바꾸기' : '이 위치에 마커 추가', () => mapChoose(r)),
    mapButton('btn', '닫기', () => { mapClearPick(); if (mapState.relocating) { mapEndRelocate(); mapOpenForm(mapState.form, true); } }));
  card.append(actions);
  mapStopMap(card);
  wrap.append(card);
  mapState.pickCard.setContent(wrap);
  mapState.pickCard.setPosition(new kakao.maps.LatLng(r.lat, r.lng));
  mapState.pickCard.setMap(mapState.map);
}

// 말풍선의 버튼을 눌렀다: 창을 연다 (수정 중 위치 다시 고르기면 그 마커의 위치만 바꾼다)
function mapChoose(r) {
  // 창이 이미 열려 있으면 늦게 온 응답이다 (빠르게 두 번 누름 등). 고치던 내용을 덮어쓰지 않는다
  if (mapById('mapDialog').open) return;
  mapState.pickSeq++;
  if (mapState.pickCard) mapState.pickCard.setMap(null); // 핀은 두고 말풍선만 접는다
  if (mapState.relocating && mapState.form) {
    mapEndRelocate();
    mapOpenForm({ ...mapState.form, address: r.address, lat: r.lat, lng: r.lng }, true);
    return;
  }
  mapOpenForm({ id: null, title: r.title || '', address: r.address, detailAddress: '', lat: r.lat, lng: r.lng, description: '' });
}

function mapShowPick(lat, lng, moveMap) {
  if (!mapState.ok) return;
  const pos = new kakao.maps.LatLng(lat, lng);
  if (!mapState.pickPin) mapState.pickPin = new kakao.maps.Marker({ position: pos, image: mapState.images.pick, zIndex: 4 });
  mapState.pickPin.setPosition(pos);
  mapState.pickPin.setMap(mapState.map);
  if (moveMap) mapState.map.panTo(pos);
}

function mapClearPick() {
  mapState.pickSeq++; // 접은 뒤 늦게 온 응답이 회색 핀을 다시 띄우지 않게
  if (mapState.pickPin) mapState.pickPin.setMap(null);
  if (mapState.pickCard) mapState.pickCard.setMap(null);
}

// 수정 중 '지도에서 고르기': 창을 잠시 닫고 검색·지도 누르기를 기다린다
function mapStartRelocate() {
  mapState.form = mapReadForm();
  mapState.relocating = true;
  mapById('mapDialog').close();
  mapById('mapRelocate').hidden = false;
  mapById('mapQuery').focus();
}

function mapEndRelocate() {
  mapState.pickSeq++; // 아직 안 온 주소 응답은 버린다
  mapState.relocating = false;
  mapById('mapRelocate').hidden = true;
}

// ---------- 추가·수정 창 ----------

// restoring: 위치를 다시 고르고 돌아온 것이면 true. 처음 열 때의 값(formStart)을 그대로 둔다
function mapOpenForm(f, restoring) {
  // 위치를 다시 고르던 중에 다른 마커의 [수정]을 눌렀으면 '위치 고르는 중'을 끝낸다 (안내 띠·다음 클릭이 꼬이지 않게)
  if (mapState.relocating) mapEndRelocate();
  mapState.formSeq++; // 앞 창에서 찾던 주소 결과가 이 창에 뜨지 않게
  mapState.form = f;
  mapState.confirmedAddress = (f.address || '').trim(); // 주소 칸은 앞뒤 빈칸을 빼고 비교하므로 여기도 뺀다
  const editing = f.id !== null && f.id !== undefined;
  mapById('mapDialogTitle').textContent = editing ? '마커 수정' : '마커 추가';
  mapById('mfTitle').value = f.title || '';
  mapById('mfAddress').value = f.address || '';
  mapById('mfDetail').value = f.detailAddress || '';
  mapById('mfDesc').value = f.description || '';
  mapUpdateFormTools();
  mapById('mfResults').replaceChildren();
  mapById('mfError').textContent = '';
  mapUpdateCount();
  mapUpdateAddressNote();
  if (mapState.ok && f.lat !== undefined && f.lat !== null) mapShowPick(f.lat, f.lng, true);
  if (!restoring) mapState.formStart = mapFormValues();
  const dlg = mapById('mapDialog');
  if (!dlg.open) dlg.showModal();
  mapById('mfTitle').focus();
}

// 창의 지도 관련 버튼: 지도가 열렸을 때만 [지도에서 직접 고르기]·[주소 찾기]·주소 고치기를 쓸 수 있다.
// 창을 연 뒤에 카카오 지도가 늦게 열려도 여기를 다시 불러 잠금을 푼다
function mapUpdateFormTools() {
  const ok = mapState.ok;
  mapById('mfRepick').hidden = !ok;
  mapById('mfFind').disabled = !ok;
  mapById('mfAddress').readOnly = !ok; // 지도를 못 불러오면 주소를 찾을 수 없으므로 고치지 못하게
}

// 창의 지금 값 (제목·주소·상세주소·설명과 고른 위치). 처음 값과 비교해 고쳤는지 본다
function mapFormValues() {
  const f = mapState.form || {};
  const v = id => mapById(id).value.trim();
  return JSON.stringify([v('mfTitle'), v('mfAddress'), v('mfDetail'), v('mfDesc'), f.lat ?? null, f.lng ?? null]);
}

// [취소]·Esc: 고친 내용이 있으면 정말 닫을지 묻는다. 고친 게 없으면 바로 닫는다 (저장은 묻지 않는다)
function mapAskClose() {
  if (mapState.saving) return; // 저장 응답을 기다리는 중: 끝나면 창이 저절로 닫힌다 (닫으면 '저장되지 않습니다'만 남는다)
  const dlg = mapById('mapDiscardDialog');
  if (dlg.open) return;        // 확인 창이 이미 떠 있다 (Esc가 여러 번 들어와도 창은 하나)
  if (mapFormValues() === mapState.formStart) { mapCloseForm(); return; }
  const active = document.activeElement;
  mapState.lastFocus = mapById('mapDialog').contains(active) ? active : null;
  dlg.querySelectorAll('button').forEach(b => {
    b.onclick = () => { dlg.close(); if (b.value === 'ok') mapCloseForm(); else mapKeepWriting(); };
  });
  dlg.showModal();
}

// [계속 쓰기]: 확인 창을 띄우기 전 커서가 있던 글자 칸으로 돌아간다.
// [취소] 버튼을 눌러 띄웠으면(커서가 버튼에 있었으면) 제목 칸으로 (버튼으로 돌아가면 Enter 한 번에 다시 취소된다)
function mapKeepWriting() {
  const el = mapState.lastFocus;
  const typing = el && el.isConnected && !el.disabled && el.matches('input, textarea');
  (typing ? el : mapById('mfTitle')).focus();
}

function mapCloseForm() {
  mapEndRelocate();
  mapById('mapDialog').close();
}

// 창 안에서 주소 찾기: 고른 결과로 위치와 주소를 바꾸고, 뒤의 회색 핀도 그 자리로 옮긴다
// 찾기마다 순번을 붙여, 창을 닫거나 다른 창을 연 뒤에 늦게 온 결과는 버린다
function mapFindInForm() {
  const seq = ++mapState.formSeq;
  const alive = () => seq === mapState.formSeq && mapById('mapDialog').open;
  mapById('mfResults').replaceChildren(); // 앞에서 찾은 결과는 먼저 지운다 (주소 칸을 비우고 다시 찾을 때 옛 결과가 남지 않게)
  mapSearchTo(mapById('mfAddress').value.trim(), mapById('mfResults'), r => {
    if (!alive()) return;
    mapById('mfError').textContent = ''; // 위치를 새로 골랐으니 '주소를 찾아 주세요' 오류는 지운다
    const address = (r.address || '').trim();
    mapState.form = { ...mapReadForm(), address, lat: r.lat, lng: r.lng };
    mapState.confirmedAddress = address;
    mapById('mfAddress').value = address;
    if (!mapById('mfTitle').value.trim() && r.title) mapById('mfTitle').value = r.title;
    mapShowPick(r.lat, r.lng, true);
    mapUpdateAddressNote();
  }, alive);
}

// 주소 칸을 고쳤는데 아직 찾지 않았으면 알려 준다
function mapUpdateAddressNote() {
  const changed = mapById('mfAddress').value.trim() !== mapState.confirmedAddress;
  mapById('mfAddressNote').hidden = !changed;
}

// 창의 값 (위치는 검색·지도로 고른 값을 그대로 쓴다)
function mapReadForm() {
  const f = mapState.form || {};
  return {
    id: f.id ?? null,
    title: mapById('mfTitle').value.trim(),
    address: f.address,
    detailAddress: mapById('mfDetail').value.trim(),
    lat: f.lat,
    lng: f.lng,
    description: mapById('mfDesc').value.trim(),
  };
}

function mapUpdateCount() {
  mapById('mfDescCount').textContent = `${mapById('mfDesc').value.length} / ${MAP_DESC_MAX}`;
}

// 저장: 공통 지도의 새 마커는 누구나, 수정은 관리자·인사 담당, 개인 지도는 본인 (화면 검사는 안내용, 서버가 다시 본다)
function mapSave() {
  if (mapState.saving) return;
  const f = mapReadForm();
  const typed = mapById('mfAddress').value.trim();
  const err = !f.title ? '제목을 입력해 주세요'
    : typed !== mapState.confirmedAddress ? '고친 주소를 [주소 찾기]로 찾아 위치를 골라 주세요'
    : !f.address || f.lat === undefined || f.lng === undefined ? '주소나 장소를 검색해 위치를 골라 주세요'
    : f.description.length > MAP_DESC_MAX ? `설명은 ${MAP_DESC_MAX}자까지 쓸 수 있습니다` : '';
  mapById('mfError').textContent = err;
  if (err) return;

  const body = { title: f.title, address: f.address, detailAddress: f.detailAddress, lat: f.lat, lng: f.lng, description: f.description };
  const editing = f.id !== null;
  mapState.saving = true;
  mapById('mfSave').disabled = true;
  api.post(editing ? `${MAP_API}/${f.id}` : MAP_API, body)
    .done(res => {
      mapState.form = null;
      mapById('mapDialog').close();
      mapClearPick();
      showToast(editing ? '마커를 고쳤습니다' : '마커를 추가했습니다');
      // 목록 찾기 글자와 맞지 않으면 방금 저장한 마커가 안 보이므로 찾기를 비운다
      mapById('mapFilter').value = '';
      mapState.filter = '';
      mapLoad(res.id);
    })
    .fail(xhr => {
      // 고치는 사이 다른 사람(관리자·인사 담당)이 이 마커를 지웠다: 창에 남아 있어 봐야 저장할 수 없으므로
      // 창을 닫고 안내한 뒤 목록을 다시 받는다 (지워진 마커가 목록에 남지 않게)
      if (editing && xhr.status === 404) {
        mapCloseForm();
        showToast(apiMessage(xhr, '이미 삭제된 마커입니다'));
        mapState.selectedId = null;
        if (mapState.bubble) mapState.bubble.setMap(null);
        mapLoad();
        return;
      }
      mapById('mfError').textContent = apiMessage(xhr, '저장하지 못했습니다');
    })
    .always(() => { mapState.saving = false; mapById('mfSave').disabled = false; });
}

// ---------- 삭제 ----------

function mapDelete(m) {
  const dlg = mapById('mapDeleteDialog');
  mapById('mapDeleteText').textContent = `'${m.title}' 마커를 지웁니다. 삭제한 마커는 되돌릴 수 없습니다.`;
  dlg.querySelectorAll('button').forEach(b => {
    b.onclick = () => {
      dlg.close();
      if (b.value !== 'ok') return;
      api.post(`${MAP_API}/${m.id}/delete`)
        .done(() => {
          showToast('마커를 삭제했습니다');
          mapState.selectedId = null;
          if (mapState.bubble) mapState.bubble.setMap(null);
          mapLoad();
        })
        .fail(xhr => {
          showToast(apiMessage(xhr, '삭제하지 못했습니다'));
          if (xhr.status === 404) { // 이미 지워진 마커: 말풍선을 접고 목록을 다시 받아 없앤다
            mapState.selectedId = null;
            if (mapState.bubble) mapState.bubble.setMap(null);
            mapLoad();
          }
        });
    };
  });
  dlg.showModal();
}
