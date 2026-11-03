// 공통 지도 (location/map.jsp). 카카오 지도 위에 우리 업체의 공통 마커를 보여 주고,
// 주소·장소 검색이나 지도 누르기로 위치를 골라 마커를 추가한다.
// - 마커 등록은 누구나, 수정·삭제 버튼은 서버가 canEdit으로 알려 준 사람(업체 관리자·인사 담당)에게만 보인다. 서버도 다시 검사한다.
// - 지도를 못 불러와도 마커 목록은 보인다(계획서 6장). 그때는 검색·추가를 막고 안내만 한다.
// 서버 주소: GET /location/api/map/markers → { markers, canEdit }
//            POST /location/api/map/markers, POST /location/api/map/markers/{id}, POST /location/api/map/markers/{id}/delete
// 사용자가 쓴 글은 모두 textContent로 넣는다 (HTML로 해석되지 않게).

const MAP_API = '/location/api/map/markers';
const MAP_DESC_MAX = 1000;
const MAP_CENTER = { lat: 37.5665, lng: 126.9780 }; // 마커가 없을 때 처음 보이는 곳 (서울시청)
const MAP_LOAD_WAIT = 8000; // 카카오 지도가 이 시간 안에 안 열리면 실패로 본다 (키·도메인이 틀리면 응답이 오지 않는다)

const mapById = id => document.getElementById(id);
function mapEl(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined && text !== null) e.textContent = text;
  return e;
}

// 핀 그림 (우리 파란색). 고른 핀은 크고 진하게, 저장 전 위치는 회색
function mapPinSvg(fill) {
  return 'data:image/svg+xml,' + encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 28 38"><path d="M14 37s12-12.2 12-22.5A12 12 0 0 0 2 14.5C2 24.8 14 37 14 37z" fill="${fill}" stroke="#ffffff" stroke-width="2"/><circle cx="14" cy="14.5" r="4.5" fill="#ffffff"/></svg>`);
}
function mapPinImage(fill, w, h) {
  return new kakao.maps.MarkerImage(mapPinSvg(fill), new kakao.maps.Size(w, h), { offset: new kakao.maps.Point(w / 2, h) });
}
const MAP_PIN_SELECTED_H = 48; // 고른 핀 높이 (말풍선을 이만큼 위에 띄운다)

const mapState = {
  markers: [],       // 서버가 준 마커 목록
  canEdit: false,    // 수정·삭제 버튼을 보일지
  selectedId: null,  // 고른 마커
  ok: false,         // 카카오 지도가 열렸는지
  map: null, geocoder: null, places: null,
  pins: new Map(),   // 마커 번호 → 지도 위 핀
  bubble: null,      // 고른 마커 위 말풍선 (CustomOverlay)
  images: null,      // 핀 그림: { normal, hover, selected, pick }
  pickPin: null,     // 검색·지도 누르기로 고른 (아직 저장 전) 위치 핀
  form: null,        // 팝업에 넣을 값 { id(수정이면), title, address, detailAddress, lat, lng, description }
  relocating: false, // 수정 중에 위치를 다시 고르는 중인지
  saving: false,
};

// ---------- 시작 ----------

function openMapView() {
  mapById('mapSearchForm').addEventListener('submit', e => { e.preventDefault(); mapSearch(mapById('mapQuery').value.trim()); });
  mapById('mapForm').addEventListener('submit', e => { e.preventDefault(); mapSave(); });
  mapById('mfCancel').addEventListener('click', () => mapCloseForm());
  mapById('mapDialog').addEventListener('close', () => { if (!mapState.relocating) mapClearPick(); });
  mapById('mfRepick').addEventListener('click', mapStartRelocate);
  mapById('mapRelocateCancel').addEventListener('click', () => { mapEndRelocate(); mapOpenForm(mapState.form); });
  mapById('mfDesc').addEventListener('input', mapUpdateCount);

  mapInitKakao();
  mapLoad();
}

// 카카오 지도 열기. 키가 없거나 스크립트를 못 받았거나 시간 안에 안 열리면 안내를 띄운다
function mapInitKakao() {
  if (window.KAKAO_MAP_FAILED || !window.kakao || !kakao.maps || !kakao.maps.load) { mapFail(); return; }
  const timer = setTimeout(() => { if (!mapState.ok) mapFail(); }, MAP_LOAD_WAIT);
  kakao.maps.load(() => {
    try {
      mapState.map = new kakao.maps.Map(mapById('mapView'), {
        center: new kakao.maps.LatLng(MAP_CENTER.lat, MAP_CENTER.lng), level: 5,
      });
      mapState.map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
      mapState.geocoder = new kakao.maps.services.Geocoder();
      mapState.places = new kakao.maps.services.Places();
      mapState.images = {
        normal: mapPinImage('#2563eb', 28, 38),
        hover: mapPinImage('#1d4ed8', 32, 43),
        selected: mapPinImage('#1e3a8a', 36, MAP_PIN_SELECTED_H),
        pick: mapPinImage('#8a94a3', 28, 38),
      };
      mapState.bubble = new kakao.maps.CustomOverlay({ yAnchor: 1, zIndex: 5 });
      kakao.maps.event.addListener(mapState.map, 'click', e => mapPickAt(e.latLng));
      mapState.ok = true;
      clearTimeout(timer);
      mapDrawPins(true);
    } catch (err) {
      clearTimeout(timer);
      mapFail();
    }
  });
}

function mapFail() {
  mapState.ok = false;
  mapById('mapView').hidden = true;
  mapById('mapFail').hidden = false;
  mapById('mapHint').textContent = '지도를 불러오지 못해 지금은 마커를 추가할 수 없어요. 목록은 아래에서 볼 수 있어요.';
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
      mapDrawPins(selectId === undefined);
      if (mapState.selectedId !== null) mapFocus(mapState.selectedId);
    })
    .fail(xhr => showToast(apiMessage(xhr, '마커를 불러오지 못했습니다')));
}

function mapRenderList() {
  const list = mapById('mapList');
  mapById('mapCount').textContent = mapState.markers.length;
  mapById('mapEmpty').hidden = mapState.markers.length > 0;
  list.replaceChildren(...mapState.markers.map(m => {
    const li = mapEl('li', m.id === mapState.selectedId ? 'is-on' : '');
    const btn = mapEl('button', 'map-item');
    btn.type = 'button';
    btn.setAttribute('aria-expanded', m.id === mapState.selectedId);
    btn.append(mapEl('strong', '', m.title), mapEl('small', '', m.address));
    btn.addEventListener('click', () => mapSelect(m.id === mapState.selectedId ? null : m.id, true));
    // 목록에 마우스를 올리면 지도의 그 핀을 살짝 키운다
    li.addEventListener('mouseenter', () => mapHoverPin(m.id, true));
    li.addEventListener('mouseleave', () => mapHoverPin(m.id, false));
    li.append(btn);
    if (m.id === mapState.selectedId) li.append(mapDetail(m));
    return li;
  }));
}

// 고른 마커의 자세한 내용: 상세주소·설명(이름표를 붙여서), 길찾기·카카오맵 링크, (관리자·인사 담당이면) 수정·삭제
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
    const edit = mapEl('button', 'btn', '수정');
    edit.type = 'button';
    edit.addEventListener('click', () => mapOpenForm({ ...m }));
    const del = mapEl('button', 'btn map-del', '삭제');
    del.type = 'button';
    del.addEventListener('click', () => mapDelete(m));
    actions.append(edit, del);
  }
  box.append(actions);
  if (m.updatedAt) box.append(mapEl('p', 'map-when', `${whenFull(m.updatedAt)} 수정`));
  return box;
}

function mapSelect(id, moveMap) {
  mapState.selectedId = id;
  mapRenderList();
  mapPaintPins();
  if (id !== null && moveMap) mapFocus(id);
  if (id === null && mapState.bubble) mapState.bubble.setMap(null);
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

// ---------- 지도 위 핀 ----------

// 마커마다 핀을 다시 꽂는다. fit이면 모든 핀이 보이게 지도 범위를 맞춘다
function mapDrawPins(fit) {
  if (!mapState.ok) return;
  mapState.pins.forEach(pin => pin.setMap(null));
  mapState.pins.clear();
  const bounds = new kakao.maps.LatLngBounds();
  mapState.markers.forEach(m => {
    const pos = new kakao.maps.LatLng(m.lat, m.lng);
    const pin = new kakao.maps.Marker({ map: mapState.map, position: pos, title: m.title, clickable: true, image: mapState.images.normal });
    kakao.maps.event.addListener(pin, 'mouseover', () => mapHoverPin(m.id, true));
    kakao.maps.event.addListener(pin, 'mouseout', () => mapHoverPin(m.id, false));
    kakao.maps.event.addListener(pin, 'click', () => mapSelect(m.id, true));
    mapState.pins.set(m.id, pin);
    bounds.extend(pos);
  });
  if (fit && mapState.markers.length) mapState.map.setBounds(bounds);
  mapPaintPins();
}

// 그 마커로 지도를 옮기고 제목 말풍선을 띄운다
function mapFocus(id) {
  if (!mapState.ok) return;
  const m = mapState.markers.find(x => x.id === id);
  const pin = mapState.pins.get(id);
  if (!m || !pin) return;
  mapState.map.panTo(pin.getPosition());
  // 말풍선: 제목 + 주소. 글자로만 넣는다 (HTML로 해석되지 않게). 핀 높이만큼 위에 띄운다
  const wrap = mapEl('div', 'map-bubble-wrap');
  wrap.style.paddingBottom = `${MAP_PIN_SELECTED_H + 6}px`;
  const bubble = mapEl('div', 'map-bubble');
  bubble.append(mapEl('strong', '', m.title), mapEl('small', '', m.address));
  wrap.append(bubble);
  mapState.bubble.setContent(wrap);
  mapState.bubble.setPosition(pin.getPosition());
  mapState.bubble.setMap(mapState.map);
}

// ---------- 위치 고르기 (검색 · 지도 누르기) ----------

// 주소로 먼저 찾고, 없으면 장소 이름으로 찾는다
function mapSearch(q) {
  const box = mapById('mapResults');
  if (!q) { mapById('mapQuery').focus(); return; }
  if (!mapState.ok) { showToast('지도를 불러오지 못해 검색할 수 없어요'); return; }
  const S = kakao.maps.services.Status;
  mapState.geocoder.addressSearch(q, (res, status) => {
    if (status === S.OK && res.length) {
      mapShowResults(res.slice(0, 8).map(r => ({
        title: '', label: r.address_name,
        address: r.road_address ? r.road_address.address_name : r.address_name,
        sub: r.road_address ? r.address_name : '',
        lat: +r.y, lng: +r.x,
      })));
      return;
    }
    mapState.places.keywordSearch(q, (places, st) => {
      if (st === S.OK && places.length) {
        mapShowResults(places.slice(0, 8).map(p => ({
          title: p.place_name, label: p.place_name,
          address: p.road_address_name || p.address_name,
          sub: p.road_address_name || p.address_name,
          lat: +p.y, lng: +p.x,
        })));
      } else {
        box.replaceChildren(mapEl('li', 'map-results-none', st === S.ZERO_RESULT || status === S.ZERO_RESULT
          ? '검색 결과가 없습니다. 도로명 주소나 다른 이름으로 찾아보세요.'
          : '검색하지 못했습니다. 잠시 뒤 다시 해 주세요.'));
      }
    });
  });
}

function mapShowResults(items) {
  mapById('mapResults').replaceChildren(...items.map(r => {
    const li = mapEl('li');
    const btn = mapEl('button');
    btn.type = 'button';
    btn.append(mapEl('strong', '', r.label));
    if (r.sub) btn.append(mapEl('small', '', r.sub));
    btn.addEventListener('click', () => mapChoose(r));
    li.append(btn);
    return li;
  }));
}

// 지도를 누른 곳의 주소를 찾아 그 위치를 고른다
function mapPickAt(latLng) {
  const lat = latLng.getLat();
  const lng = latLng.getLng();
  mapState.geocoder.coord2Address(lng, lat, (res, status) => {
    if (status !== kakao.maps.services.Status.OK || !res.length) {
      showToast('이 위치의 주소를 찾지 못했어요. 도로나 건물 가까이를 눌러 보세요');
      return;
    }
    const r = res[0];
    const address = r.road_address ? r.road_address.address_name : r.address.address_name;
    mapChoose({ title: r.road_address && r.road_address.building_name ? r.road_address.building_name : '', address, lat, lng });
  });
}

// 위치를 골랐다: 그 자리에 임시 핀을 꽂고 팝업을 연다 (수정 중 위치 다시 고르기면 그 마커의 위치만 바꾼다)
function mapChoose(r) {
  mapById('mapResults').replaceChildren();
  mapShowPick(r.lat, r.lng);
  if (mapState.relocating && mapState.form) {
    mapEndRelocate();
    mapOpenForm({ ...mapState.form, address: r.address, lat: r.lat, lng: r.lng });
    return;
  }
  mapOpenForm({ id: null, title: r.title || '', address: r.address, detailAddress: '', lat: r.lat, lng: r.lng, description: '' });
}

function mapShowPick(lat, lng) {
  if (!mapState.ok) return;
  const pos = new kakao.maps.LatLng(lat, lng);
  if (!mapState.pickPin) mapState.pickPin = new kakao.maps.Marker({ position: pos, image: mapState.images.pick, zIndex: 4 });
  mapState.pickPin.setPosition(pos);
  mapState.pickPin.setMap(mapState.map);
  mapState.map.panTo(pos);
}

function mapClearPick() {
  if (mapState.pickPin) mapState.pickPin.setMap(null);
}

// 수정 중 '위치 다시 고르기': 팝업을 잠시 닫고 검색·지도 누르기를 기다린다
function mapStartRelocate() {
  mapState.form = mapReadForm();
  mapState.relocating = true;
  mapById('mapDialog').close();
  mapById('mapRelocate').hidden = false;
  mapById('mapQuery').focus();
}

function mapEndRelocate() {
  mapState.relocating = false;
  mapById('mapRelocate').hidden = true;
}

// ---------- 추가·수정 팝업 ----------

function mapOpenForm(f) {
  mapState.form = f;
  const editing = f.id !== null && f.id !== undefined;
  mapById('mapDialogTitle').textContent = editing ? '마커 수정' : '마커 추가';
  mapById('mfTitle').value = f.title || '';
  mapById('mfAddress').value = f.address || '';
  mapById('mfDetail').value = f.detailAddress || '';
  mapById('mfDesc').value = f.description || '';
  mapById('mfRepick').hidden = !editing || !mapState.ok;
  mapById('mfError').textContent = '';
  mapUpdateCount();
  const dlg = mapById('mapDialog');
  if (!dlg.open) dlg.showModal();
  mapById('mfTitle').focus();
}

function mapCloseForm() {
  mapEndRelocate();
  mapById('mapDialog').close();
}

// 팝업의 값 (위치는 고를 때 정한 값을 그대로 쓴다)
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

// 저장: 새 마커는 누구나, 수정은 관리자·인사 담당 (화면 검사는 안내용, 서버가 다시 본다)
function mapSave() {
  if (mapState.saving) return;
  const f = mapReadForm();
  const err = !f.title ? '제목을 입력해 주세요'
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
      mapLoad(res.id);
    })
    .fail(xhr => { mapById('mfError').textContent = apiMessage(xhr, '저장하지 못했습니다'); })
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
        .fail(xhr => showToast(apiMessage(xhr, '삭제하지 못했습니다')));
    };
  });
  dlg.showModal();
}
