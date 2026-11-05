<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  자산 관리 화면 (/asset/admin/list, IT 담당·업체 관리자)
  - 요구분석서 3.4 IT 자산, 통합 계획서 4장 IT 자산
  - 요청서 범위: 상태 카드로 거르는 자산 목록, 직원에게 지급·회수
  - 동작은 resources/js/asset/asset-admin.js (서버 주소·값은 그 파일 맨 위)
  - 이 주소를 받는 Controller 는 asset 모듈 담당이 만든다
  - 자산 등록·지급 이력은 서버에 아직 없어 화면에도 없다
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="자산 관리" crumb="IT 서비스" menu="asset-all">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/asset/asset-admin.js"></script>
    <script>openAssetAdmin();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-asset-admin">
      <div class="page-head"><h1>자산 관리</h1></div>
      <p class="muted join-scope">
        같은 장비는 한 사람에게만 지급됩니다. 회수하면 다시 지급할 수 있는 상태로 돌아갑니다.
        장비 지급 신청으로 생긴 지급은 티켓 처리에서 합니다.
      </p>

      <%-- 상태 카드: 개수와 거르기는 asset-admin.js 가 받은 목록으로 채운다 --%>
      <div class="cards" id="aaFilters">
        <button type="button" class="card card-filter" data-filter="ALL" aria-pressed="true">
          <small>전체</small><strong id="aaCntALL">-</strong>
        </button>
        <button type="button" class="card card-filter" data-filter="AS_AVAILABLE" aria-pressed="false">
          <small>지급 가능</small><strong id="aaCntAS_AVAILABLE">-</strong>
        </button>
        <button type="button" class="card card-filter" data-filter="AS_IN_USE" aria-pressed="false">
          <small>사용 중</small><strong id="aaCntAS_IN_USE">-</strong>
        </button>
      </div>

      <section class="info-card table-gap">
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>자산번호</th>
                <th>종류</th>
                <th>이름</th>
                <th>일련번호</th>
                <th>배치 지점</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody id="aaList"></tbody>
          </table>
        </div>
      </section>
    </section>
  </jsp:body>
</ui:layout>
