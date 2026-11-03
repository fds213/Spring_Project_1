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
    <%-- 카카오 지도: autoload=false로 불러와 map.js가 kakao.maps.load로 연다. services는 주소·장소 검색 --%>
    <c:if test="${not empty kakaoKey}">
      <script src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=${fn:escapeXml(kakaoKey)}&amp;libraries=services&amp;autoload=false"
              onerror="window.KAKAO_MAP_FAILED = true"></script>
    </c:if>
    <script src="${ctx}/resources/js/location/map.js"></script>
    <script>openMapView();</script>
  </jsp:attribute>
  <jsp:body>
    <link rel="stylesheet" href="${ctx}/resources/css/location/map.css">
    <section class="view on" id="view-map">
      <div class="page-head"><h1>공통 지도</h1></div>

      <div class="map-layout">
        <%-- 왼쪽: 검색 + 마커 목록 --%>
        <section class="info-card map-side">
          <form class="map-search" id="mapSearchForm" novalidate>
            <label class="sr-only" for="mapQuery">주소나 장소 검색</label>
            <input class="input" id="mapQuery" type="search" maxlength="100" autocomplete="off" placeholder="주소나 장소 이름 (예: 판교역로 235)">
            <button type="submit" class="btn btn-primary">검색</button>
          </form>
          <p class="map-hint" id="mapHint">주소나 장소를 검색하거나 지도를 눌러 위치를 고르면 마커를 추가할 수 있어요.</p>
          <%-- 수정 중에 '위치 다시 고르기'를 누르면 보이는 안내 --%>
          <div class="map-relocate" id="mapRelocate" hidden>
            <span>새 위치를 검색하거나 지도를 눌러 고르세요.</span>
            <button type="button" class="link-btn" id="mapRelocateCancel">취소</button>
          </div>
          <ul class="map-results" id="mapResults" aria-label="검색 결과"></ul>

          <header class="map-list-head">
            <h2>마커 <span class="map-count" id="mapCount">0</span></h2>
          </header>
          <ul class="map-list" id="mapList" aria-label="마커 목록"></ul>
          <p class="empty-note" id="mapEmpty" hidden>아직 마커가 없습니다. 위에서 위치를 검색해 첫 마커를 추가해 보세요.</p>
        </section>

        <%-- 오른쪽: 카카오 지도 --%>
        <section class="info-card map-main">
          <div class="map-view" id="mapView" aria-label="지도"></div>
          <div class="map-fail" id="mapFail" hidden>
            <strong>지도를 불러오지 못했습니다</strong>
            <p>마커 목록은 왼쪽에서 그대로 볼 수 있어요. 지도는 카카오 지도 키와 등록 도메인을 확인한 뒤 다시 열어 주세요.</p>
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
        <div class="field">
          <label for="mfAddress">주소</label>
          <div class="input-action">
            <input class="input" id="mfAddress" readonly>
            <button type="button" class="btn" id="mfRepick" hidden>위치 다시 고르기</button>
          </div>
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
          <button type="submit" class="btn btn-primary" id="mfSave">저장</button>
        </div>
      </form>
    </dialog>

    <%-- 마커 삭제 확인 --%>
    <dialog class="modal" id="mapDeleteDialog">
      <h2>마커를 삭제할까요?</h2>
      <p id="mapDeleteText">삭제한 마커는 되돌릴 수 없습니다.</p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">취소</button>
        <button type="button" class="btn map-danger" value="ok">삭제</button>
      </div>
    </dialog>
  </jsp:body>
</ui:layout>
