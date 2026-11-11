<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  자료실 화면 (/board/library, /board/library/view/{id}, /board/library/write, /board/library/edit/{id}). 주소는 BoardController가 받는다.
  같은 업체 직원 누구나 파일을 붙인 글을 올리고 내려받는다. 파일은 1개 이상, 첨부 규칙은 공지·부서 게시판과 같다(5개·파일당 10MB).
  수정·삭제는 올린 사람 본인만 한다. 댓글은 두지 않는다.
  화면 JS는 부서 게시판과 같은 board/board.js (makeMemberBoard, 요소 id 앞글자 lib).
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="자료실" crumb="소통" menu="board-library">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/board/board.js"></script>
    <script>routeNoticeBoard('board-library' + location.pathname.slice((document.body.dataset.ctx + '/board/library').length));</script>
  </jsp:attribute>
  <jsp:body>
    <%-- 게시판 전용 스타일 (공통 레이아웃에 화면별 CSS 칸이 없어서 본문 맨 앞에서 불러온다) --%>
    <link rel="stylesheet" href="${ctx}/resources/css/board/board.css">
    <section class="view" id="view-lib-list">
      <%-- 1. 목록: 제목 옆 클립이 첨부 수를 알려 준다 --%>
      <div class="page-head">
        <div>
          <h1>자료실</h1>
          <p class="page-sub">회사 사람 누구나 자료를 올리고 내려받을 수 있어요.</p>
        </div>
        <div class="page-head-actions">
          <%-- 직원 누구나 올릴 수 있다. 읽기 전용 업체에서는 흐리게 막는다 --%>
          <a href="${ctx}/board/library/write" class="btn btn-primary" id="libBtnWrite" data-write>자료 올리기</a>
        </div>
      </div>
      <form class="search-row" id="libSearchForm" role="search">
        <input type="search" class="search-input" id="libSearchInput" placeholder="제목·내용으로 검색" aria-label="자료 검색" autocomplete="off">
        <span class="result-info" id="libResultInfo" role="status"></span>
      </form>
      <div class="table-wrap">
        <table class="table table-fixed">
          <colgroup><col style="width: 10%"><col style="width: 56%"><col style="width: 16%"><col style="width: 18%"></colgroup>
          <thead><tr><th>번호</th><th>제목</th><th>작성자</th><th>작성일</th></tr></thead>
          <tbody id="libRows"></tbody>
        </table>
      </div>
      <nav class="pager" id="libPager" aria-label="페이지"></nav>
    </section>

    <section class="view" id="view-lib-detail">
      <%-- 2. 자료 상세: 첨부파일 내려받기 --%>
      <article class="notice-article">
        <h1 class="notice-title" id="libDetailTitle"></h1>
        <p class="notice-meta" id="libDetailMeta"></p>
        <div class="notice-body rich-body" id="libDetailBody"></div>
        <section class="attach-box" id="libDetailFiles" hidden aria-labelledby="libDetailFilesHead">
          <h2 id="libDetailFilesHead">첨부파일 <span class="comment-count" id="libDetailFileCount"></span></h2>
          <div class="attach-images" id="libDetailImages"></div>
          <ul class="attach-list" id="libDetailFileList"></ul>
        </section>
      </article>
      <div class="notice-actions">
        <a href="${ctx}/board/library" class="btn">목록으로</a>
        <%-- 수정·삭제: 올린 사람 본인에게만 --%>
        <div class="notice-actions-right" id="libOwnerActions" hidden>
          <a href="#" class="btn" id="libBtnEdit" data-write>수정</a>
          <button type="button" class="btn btn-danger" id="libBtnDelete" data-write>삭제</button>
        </div>
      </div>
    </section>

    <section class="view" id="view-lib-form">
      <%-- 3. 자료 올리기·수정 (같은 화면) --%>
      <div class="page-head">
        <div>
          <h1 id="libFormTitle">자료 올리기</h1>
          <p class="page-sub">파일을 1개 이상 붙여 주세요. 올린 사람의 이름이 함께 보입니다.</p>
        </div>
      </div>
      <form class="notice-form" id="libForm" novalidate>
        <div class="field">
          <label for="libTitle">제목</label>
          <input class="input" id="libTitle" maxlength="100" autocomplete="off" aria-describedby="libTitleError libTitleCount">
          <div class="field-foot">
            <p class="form-error" id="libTitleError" role="alert"></p>
            <span class="counter" id="libTitleCount">0 / 100</span>
          </div>
        </div>
        <div class="field">
          <span class="field-label" id="libContentLabel">내용</span>
          <div class="editor">
            <div class="editor-toolbar" role="toolbar" aria-label="글자 꾸미기"></div>
            <div class="editor-area" id="libContent" contenteditable="true" role="textbox" aria-multiline="true" data-placeholder="자료 설명을 입력하세요"
                 aria-labelledby="libContentLabel" aria-describedby="libContentError libContentCount"></div>
          </div>
          <div class="field-foot">
            <p class="form-error" id="libContentError" role="alert"></p>
            <span class="counter" id="libContentCount">0 / 2000</span>
          </div>
        </div>
        <%-- 첨부파일: 공지와 같은 규칙 (최대 5개, 파일당 10MB). 자료실은 1개 이상 --%>
        <div class="field">
          <span class="field-label">첨부파일 <span class="muted">(1~5개 · 이미지·문서 · 파일당 10MB)</span></span>
          <div class="attach-pick">
            <button type="button" class="btn" id="libBtnPickFiles">파일 선택</button>
            <input type="file" id="libFiles" multiple hidden
                   accept=".jpg,.jpeg,.png,.gif,.pdf,.docx,.xlsx,.pptx,.hwpx,.hwp">
            <span class="muted" id="libFilesInfo">선택한 파일 없음</span>
          </div>
          <ul class="attach-list is-edit" id="libFileList"></ul>
          <p class="form-error" id="libFilesError" role="alert"></p>
        </div>
        <div class="bill-actions">
          <button type="button" class="btn" id="libBtnCancel">취소</button>
          <button type="submit" class="btn btn-primary" id="libBtnSave">저장</button>
        </div>
      </form>
    </section>

    <%-- 게시판 화면들이 같이 쓰는 창과 에디터 툴바 (board/board.js). 게시판 화면 JSP 끝에 둔다 --%>
    <dialog class="modal" id="deleteDialog">
      <h2>자료를 삭제할까요?</h2>
      <p>삭제한 글은 되돌릴 수 없습니다.</p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">취소</button>
        <button type="button" class="btn btn-danger" value="ok">삭제</button>
      </div>
    </dialog>

    <dialog class="modal" id="leaveDialog">
      <h2>작성 중인 내용이 사라집니다</h2>
      <p>저장하지 않고 나갈까요?</p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">계속 쓰기</button>
        <button type="button" class="btn btn-primary" value="ok">나가기</button>
      </div>
    </dialog>

    <%-- 에디터 툴바 (board/board.js가 각 .editor-toolbar에 복사해 넣는다. 공지·부서 게시판과 같은 모양) --%>
    <template id="editorToolbarTpl">
      <label class="ed-size-wrap" title="글자 크기 (10~40)">
        <span class="sr-only">글자 크기 (10~40)</span>
        <input class="ed-size" type="text" inputmode="numeric" maxlength="2" value="15" autocomplete="off">
      </label>
      <span class="ed-sep" aria-hidden="true"></span>
      <button type="button" class="ed-btn" data-cmd="bold" aria-label="굵게" aria-pressed="false" title="굵게"><b>B</b></button>
      <button type="button" class="ed-btn" data-cmd="italic" aria-label="기울임" aria-pressed="false" title="기울임"><i>I</i></button>
      <button type="button" class="ed-btn" data-cmd="underline" aria-label="밑줄" aria-pressed="false" title="밑줄"><u>U</u></button>
      <span class="ed-sep" aria-hidden="true"></span>
      <span class="ed-colors" role="group" aria-label="글자색">
        <button type="button" class="ed-color" data-color="#1f1f1d" style="--c:#1f1f1d" aria-label="검정" aria-pressed="false" title="검정"></button>
        <button type="button" class="ed-color" data-color="#a32d2d" style="--c:#a32d2d" aria-label="빨강" aria-pressed="false" title="빨강"></button>
        <button type="button" class="ed-color" data-color="#185fa5" style="--c:#185fa5" aria-label="파랑" aria-pressed="false" title="파랑"></button>
        <button type="button" class="ed-color" data-color="#2b6a2f" style="--c:#2b6a2f" aria-label="초록" aria-pressed="false" title="초록"></button>
        <button type="button" class="ed-color" data-color="#8a4b08" style="--c:#8a4b08" aria-label="주황" aria-pressed="false" title="주황"></button>
      </span>
      <span class="ed-sep" aria-hidden="true"></span>
      <button type="button" class="ed-btn" data-cmd="insertUnorderedList" aria-label="글머리 목록" aria-pressed="false" title="글머리 목록">• 목록</button>
      <button type="button" class="ed-btn" data-cmd="insertOrderedList" aria-label="번호 목록" aria-pressed="false" title="번호 목록">1. 목록</button>
      <button type="button" class="ed-btn" data-cmd="removeFormat" aria-label="서식 지우기" title="서식 지우기">서식 지우기</button>
    </template>

    <%-- 첨부 이미지 크게 보기 --%>
    <dialog class="modal modal-image" id="imageDialog" aria-label="이미지 크게 보기">
      <img id="imageDialogImg" alt="">
      <div class="modal-actions">
        <a class="btn" id="imageDialogDownload" download>다운로드</a>
        <button type="button" class="btn btn-primary" value="ok">닫기</button>
        <button type="button" class="btn" value="cancel" hidden>닫기</button>
      </div>
    </dialog>
  </jsp:body>
</ui:layout>
