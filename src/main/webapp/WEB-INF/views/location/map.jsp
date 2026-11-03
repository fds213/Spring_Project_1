<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  공통 지도 (/location/map). Controller: location/controller/WorkMapController, 데이터: /location/api/map/...
  - 카카오 지도 JavaScript 키(kakaoKey)는 서버가 config/kakao.properties에서 읽어 넘긴다. 키가 없거나 지도를 못 불러오면
    지도 자리에 안내가 나오고 마커 목록은 그대로 보인다(계획서 6장: 지도 API가 실패해도 다른 기능은 쓴다).
  - 마커 등록은 누구나, 수정·삭제는 업체 관리자·인사 담당(서버가 canEdit으로 알려 주고 저장할 때 다시 검사한다).
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="지도" crumb="소통" menu="location">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <%-- 카카오 지도 스크립트는 여기서 부르지 않는다: 카카오가 응답 없이 멈추면 뒤의 map.js까지 멈추기 때문.
         map.js가 마커 목록을 먼저 읽고, 그다음 카카오 스크립트를 넣어 8초 안에 안 열리면 안내를 띄운다 --%>
    <script src="${ctx}/resources/js/location/map.js"></script>
    <script>openMapView();</script>
  </jsp:attribute>
  <jsp:body>
    <link rel="stylesheet" href="${ctx}/resources/css/location/map.css">
    <%-- data-kakao-key: 카카오 지도 JavaScript 키 (비어 있으면 지도 대신 안내) --%>
    <section class="view on" id="view-map" data-kakao-key="${fn:escapeXml(kakaoKey)}">
      <div class="page-head"><h1>공통 지도</h1></div>

      <div class="map-layout">
        <%-- 왼쪽: 검색 + 마커 목록 --%>
        <section class="info-card map-side">
          <form class="map-search" id="mapSearchForm" novalidate>
            <label class="sr-only" for="mapQuery">주소나 장소 검색</label>
            <input class="input" id="mapQuery" type="search" maxlength="100" autocomplete="off" placeholder="주소나 장소 이름 (예: 판교역로 235)">
            <button type="submit" class="btn btn-primary">검색</button>
          </form>
          <p class="map-hint" id="mapHint">주소나 장소를 검색하거나 지도의 빈 곳을 누른 뒤 [이 위치에 마커 추가]를 누르세요. 핀을 누르면 그 마커를 볼 수 있어요.</p>
          <%-- 수정 중에 '위치 다시 고르기'를 누르면 보이는 안내 --%>
          <div class="map-relocate" id="mapRelocate" hidden>
            <span>새 위치를 검색하거나 지도를 눌러 고르세요.</span>
            <button type="button" class="link-btn" id="mapRelocateCancel">취소</button>
          </div>
          <ul class="map-results" id="mapResults" aria-label="검색 결과"></ul>

          <header class="map-list-head">
            <h2>마커 <span class="map-count" id="mapCount">0</span></h2>
            <%-- 목록 순서: 최근 수정순(서버 순서) / 이름순. 마커가 두 개 이상일 때만 보인다 --%>
            <div class="map-sort" id="mapSort" role="group" aria-label="목록 순서" hidden>
              <button type="button" class="map-sort-btn is-on" data-sort="recent" aria-pressed="true">최근순</button>
              <button type="button" class="map-sort-btn" data-sort="name" aria-pressed="false">이름순</button>
            </div>
          </header>
          <%-- 목록에서 찾기: 제목·주소·설명에 들어 있는 마커만 목록과 지도에 남긴다 --%>
          <label class="sr-only" for="mapFilter">마커 목록에서 찾기</label>
          <input class="input map-filter" id="mapFilter" type="search" maxlength="50" autocomplete="off" placeholder="목록에서 찾기 (이름·주소)" hidden>
          <ul class="map-list" id="mapList" aria-label="마커 목록"></ul>
          <p class="empty-note" id="mapEmpty" hidden>아직 마커가 없습니다. 위에서 위치를 검색해 첫 마커를 추가해 보세요.</p>
          <p class="empty-note" id="mapNoMatch" hidden>찾는 마커가 없습니다.</p>
        </section>

        <%-- 오른쪽: 카카오 지도 --%>
        <section class="info-card map-main">
          <div class="map-view" id="mapView" aria-label="지도"></div>
          <%-- 지도 위 도구: 모든 핀이 한 화면에 들어오게 --%>
          <button type="button" class="btn map-fit" id="mapFitAll" hidden>전체 보기</button>
          <%-- 마커가 하나도 없을 때 지도 위 안내 --%>
          <p class="map-empty-hint" id="mapEmptyHint" hidden>검색창에서 찾거나 지도의 빈 곳을 눌러 첫 마커를 추가해 보세요.</p>
          <div class="map-fail" id="mapFail" hidden>
            <strong>지도를 불러오지 못했습니다</strong>
            <p>마커 목록은 그대로 볼 수 있어요. 지도는 카카오 지도 키·등록 도메인·카카오맵 사용 설정을 확인한 뒤 다시 열어 주세요.</p>
          </div>
        </section>
      </div>
    </section>

    <%-- 마커 추가·수정 팝업 --%>
    <dialog class="modal map-dialog" id="mapDialog" aria-labelledby="mapDialogTitle">
      <h2 id="mapDialogTitle">마커 추가</h2>
      <form id="mapForm" novalidate>
        <div class="field">
          <label for="mfTitle">제목</label>
          <input class="input" id="mfTitle" maxlength="100" autocomplete="off" placeholder="예: 본사, 판교 창고">
        </div>
        <%-- 주소: 직접 고쳐 [주소 찾기]로 위치를 바꾸거나, [지도에서 고르기]로 지도를 눌러 고른다.
             찾지 않고 고친 주소는 저장하지 않는다 (글자 주소와 핀 위치가 달라지지 않게) --%>
        <div class="field">
          <label for="mfAddress">주소</label>
          <div class="input-action">
            <input class="input" id="mfAddress" maxlength="500" autocomplete="off" placeholder="주소나 장소 이름">
            <button type="button" class="btn" id="mfFind" disabled>주소 찾기</button>
          </div>
          <p class="map-address-note" id="mfAddressNote" hidden>주소를 고쳤어요. [주소 찾기]로 찾아 위치를 골라 주세요.</p>
          <ul class="map-results map-form-results" id="mfResults" aria-label="주소 찾기 결과"></ul>
          <button type="button" class="link-btn map-repick" id="mfRepick" hidden>지도에서 직접 고르기</button>
        </div>
        <div class="field">
          <label for="mfDetail">상세주소 <small class="muted">(선택)</small></label>
          <input class="input" id="mfDetail" maxlength="200" autocomplete="off" placeholder="예: 3층 301호">
        </div>
        <div class="field">
          <label for="mfDesc">설명 <small class="muted">(선택)</small> <small class="map-counter" id="mfDescCount">0 / 1000</small></label>
          <textarea class="input map-textarea" id="mfDesc" maxlength="1000" rows="4" placeholder="예: 주차는 건물 뒤편, 방문 전 안내 데스크에 연락"></textarea>
        </div>
        <p class="form-error" id="mfError" role="alert"></p>
        <div class="modal-actions">
          <button type="button" class="btn" id="mfCancel">취소</button>
          <button type="submit" class="btn btn-primary" id="mfSave" data-write>저장</button>
        </div>
      </form>
    </dialog>

    <%-- 작성 중 닫기 확인: 추가·수정 창에서 고친 내용이 있는데 [취소]·Esc를 누르면 --%>
    <dialog class="modal" id="mapDiscardDialog">
      <h2>작성 중인 내용이 있어요</h2>
      <p>닫으면 고친 내용은 저장되지 않습니다.</p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">계속 쓰기</button>
        <button type="button" class="btn btn-primary" value="ok">닫기</button>
      </div>
    </dialog>

    <%-- 마커 삭제 확인 --%>
    <dialog class="modal" id="mapDeleteDialog">
      <h2>마커를 삭제할까요?</h2>
      <p id="mapDeleteText">삭제한 마커는 되돌릴 수 없습니다.</p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">취소</button>
        <button type="button" class="btn map-danger" value="ok" data-write>삭제</button>
      </div>
    </dialog>
  </jsp:body>
</ui:layout>
