<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  공지사항 화면 (/board/notice, /board/notice/view/{id}, /board/notice/write, /board/notice/edit/{id}). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버 주소와 주고받는 값은 docs/front-screen-api.md(프론트 제안), 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="공지사항" crumb="홈" menu="notice">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/board/board.js"></script>
    <script>routeNoticeBoard('notice' + location.pathname.slice((document.body.dataset.ctx + '/board/notice').length));</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view" id="view-notice-list">
      <%-- 공지사항 (board/board.js). 주소: /board/notice · /board/notice/view/3 · /board/notice/write · /board/notice/edit/3.
           목록·글 보기·글쓰기가 이 한 페이지에 있고, 주소를 보고 하나를 보인다 --%>
      <%-- 1. 공지 목록 --%>
      <div class="page-head">
        <h1>공지사항</h1>
        <%-- 글쓰기: 업체 관리자·인사 담당(전사·모든 부서), 부서장(자기 부서)에게만 보인다.
             data-write: 읽기 전용 업체에서는 흐리게 막는다 --%>
        <a href="${ctx}/board/notice/write" class="btn btn-primary" id="btnWrite" data-write hidden>글쓰기</a>
      </div>
      <%-- 공지 구분: 전사 공지(전 직원) / 부서 공지(그 부서만. 업체 관리자·인사 담당은 모든 부서) --%>
      <div class="type-tabs" id="noticeTabs" role="tablist" aria-label="공지 구분">
        <button type="button" role="tab" class="type-tab" data-tab="all" aria-selected="true">전체</button>
        <button type="button" role="tab" class="type-tab" data-tab="DEPT" aria-selected="false" tabindex="-1" id="noticeTabDept">부서 공지</button>
      </div>
      <form class="search-row" id="searchForm" role="search">
        <%-- 실시간 검색: 글자를 칠 때마다(멈추고 0.25초 뒤) 목록이 걸러진다. 비우면 전체 목록 --%>
        <input type="search" class="search-input" id="searchInput" placeholder="제목·내용으로 검색" aria-label="공지 검색" autocomplete="off">
        <span class="result-info" id="resultInfo" role="status"></span>
      </form>
      <div class="table-wrap">
        <table class="table table-fixed">
          <%-- 구분: 전사 공지는 "전사", 부서 공지는 부서 이름. 모든 사람에게 같은 칸이 보인다 --%>
          <colgroup><col style="width: 9%"><col style="width: 13%"><col style="width: 48%"><col style="width: 13%"><col style="width: 17%"></colgroup>
          <thead><tr><th>번호</th><th>구분</th><th>제목</th><th>작성자</th><th>작성일</th></tr></thead>
          <tbody id="noticeRows"></tbody>
        </table>
      </div>
      <nav class="pager" id="pager" aria-label="페이지"></nav>
    </section>

    <section class="view" id="view-notice-detail">
      <%-- 2. 공지 상세 --%>
      <article class="notice-article">
        <h1 class="notice-title" id="detailTitle"></h1>
        <p class="notice-meta" id="detailMeta"></p>
        <%-- 내용은 에디터로 쓴 서식 글. 허용한 태그만 남기고 걸러서(sanitizeHtml) 넣는다 --%>
        <div class="notice-body rich-body" id="detailBody"></div>
        <%-- 첨부파일: 이미지는 미리보기, 나머지는 이름·크기·내려받기 --%>
        <section class="attach-box" id="detailFiles" hidden aria-labelledby="detailFilesHead">
          <h2 id="detailFilesHead">첨부파일 <span class="comment-count" id="detailFileCount"></span></h2>
          <div class="attach-images" id="detailImages"></div>
          <ul class="attach-list" id="detailFileList"></ul>
        </section>
      </article>
      <div class="notice-actions">
        <a href="${ctx}/board/notice" class="btn">목록으로</a>
        <div class="notice-actions-right" id="detailOwnerActions" hidden>
          <%-- 고정하기 / 고정 풀기: 수정 화면에 가지 않고 바로 바꾼다. 수정일은 바뀌지 않는다 --%>
          <button type="button" class="btn" id="btnPin" data-write>고정하기</button>
          <a href="#" class="btn" id="btnEdit" data-write>수정</a>
          <button type="button" class="btn btn-danger" id="btnDelete" data-write>삭제</button>
        </div>
      </div>

      <%-- 공지 댓글: 그 공지를 볼 수 있는 사람이면 누구나. 본인 댓글 삭제, 업체 관리자·인사 담당은 모든 댓글 삭제 --%>
      <section class="comments" aria-labelledby="nCommentHead">
        <h2 id="nCommentHead">댓글 <span class="comment-count" id="nCommentCount">0</span></h2>
        <ul class="comment-list" id="nComments"></ul>
        <p class="empty-note" id="nCommentEmpty" hidden>첫 댓글을 남겨 보세요.</p>
        <form class="comment-form" id="nCommentForm" novalidate>
          <label for="nCommentText" class="sr-only">댓글</label>
          <textarea class="input" id="nCommentText" maxlength="500" rows="3" placeholder="댓글을 입력하세요" aria-describedby="nCommentError nCommentCountNow"></textarea>
          <div class="field-foot">
            <p class="form-error" id="nCommentError" role="alert"></p>
            <span class="counter" id="nCommentCountNow">0 / 500</span>
          </div>
          <div class="comment-form-actions">
            <button type="submit" class="btn btn-primary" id="nCommentSave" data-write>댓글 등록</button>
          </div>
        </form>
      </section>
    </section>

    <section class="view" id="view-notice-form">
      <%-- 3. 공지 작성·수정 (같은 화면) --%>
      <div class="page-head"><h1 id="formTitle">공지 쓰기</h1></div>
      <form class="notice-form" id="noticeForm" novalidate>
        <%-- 공지 범위: 업체 관리자·인사 담당은 전사 또는 아무 부서, 부서장은 자기 부서로 잠김 --%>
        <div class="field">
          <label for="fScope">공지 범위</label>
          <select class="input scope-select" id="fScope" aria-describedby="fScopeHint"></select>
          <p class="field-hint" id="fScopeHint"></p>
        </div>
        <div class="field">
          <label for="fTitle">제목</label>
          <input class="input" id="fTitle" maxlength="100" autocomplete="off" aria-describedby="fTitleError fTitleCount">
          <div class="field-foot">
            <p class="form-error" id="fTitleError" role="alert"></p>
            <span class="counter" id="fTitleCount">0 / 100</span>
          </div>
        </div>
        <%-- 내용: 간단한 에디터 (굵게·기울임·밑줄·크기·글자색·목록). 붙여넣기는 글자만 받는다.
             서버는 저장 전에 허용한 태그 말고는 모두 지운다 --%>
        <div class="field">
          <span class="field-label" id="fContentLabel">내용</span>
          <div class="editor">
            <%-- 툴바는 아래 <template id="editorToolbarTpl">을 복사해 넣는다 (공지·부서 게시판이 같은 툴바) --%>
            <div class="editor-toolbar" role="toolbar" aria-label="글자 꾸미기"></div>
            <div class="editor-area" id="fContent" data-placeholder="공지 내용을 입력하세요" contenteditable="true" role="textbox" aria-multiline="true"
                 aria-labelledby="fContentLabel" aria-describedby="fContentError fContentCount"></div>
          </div>
          <div class="field-foot">
            <p class="form-error" id="fContentError" role="alert"></p>
            <span class="counter" id="fContentCount">0 / 2000</span>
          </div>
        </div>
        <%-- 첨부파일: 최대 5개, 파일당 10MB --%>
        <div class="field">
          <span class="field-label">첨부파일 <span class="muted">(최대 5개 · 이미지·문서 · 파일당 10MB)</span></span>
          <div class="attach-pick">
            <button type="button" class="btn" id="btnPickFiles">파일 선택</button>
            <input type="file" id="fFiles" multiple hidden
                   accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.hwp,.hwpx,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip">
            <span class="muted" id="fFilesInfo">선택한 파일 없음</span>
          </div>
          <ul class="attach-list is-edit" id="fFileList"></ul>
          <p class="form-error" id="fFilesError" role="alert"></p>
        </div>
        <%-- 맨 위 고정: 모든 페이지의 목록 맨 위에 보인다. 최대 5개 --%>
        <div class="field">
          <label class="agree pin-check"><input type="checkbox" id="fPinned"> 목록 맨 위에 고정 <span class="muted">(최대 5개)</span></label>
          <p class="form-error" id="fPinnedError" role="alert"></p>
        </div>
        <div class="bill-actions">
          <button type="button" class="btn" id="btnCancel">취소</button>
          <button type="submit" class="btn btn-primary" id="btnSave">저장</button>
        </div>
      </form>
    </section>

    <%-- 공지사항·부서 게시판이 같이 쓰는 창과 에디터 툴바. 게시판 화면 JSP 끝에 둔다(공통 jspf로 빼도 된다) --%>
    <%-- 공지사항·부서 게시판 확인 창 (board/board.js) --%>
    <dialog class="modal" id="deleteDialog">
      <h2>공지를 삭제할까요?</h2>
      <p>삭제한 공지는 되돌릴 수 없습니다.</p>
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

    <%-- 고정이 5개 꽉 찼을 때: 지금 고정된 공지를 보여 주고, 풀 공지로 바로 갈 수 있게 --%>
    <dialog class="modal modal-wide" id="pinFullDialog">
      <h2>맨 위 고정은 5개까지예요</h2>
      <p>지금 고정된 공지 중 하나의 고정을 먼저 풀어 주세요.</p>
      <ul class="pin-full-list" id="pinFullList"></ul>
      <div class="modal-actions">
        <button type="button" class="btn btn-primary" value="ok">확인</button>
        <button type="button" class="btn" value="cancel" hidden>닫기</button>
      </div>
    </dialog>

    <%-- 에디터 툴바 (공지·부서 게시판이 같이 쓴다. board/board.js가 각 .editor-toolbar에 복사해 넣는다)
         글자 크기: 숫자를 넣고 Enter 또는 칸 밖을 누르면 적용 (10~40, 기본 15). 커서 자리의 크기가 늘 보인다
         굵게·기울임·밑줄·목록: 켜져 있으면 눌린 모양 / 글자색: 지금 색에 링 --%>
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
        <a class="btn" id="imageDialogDownload" download>내려받기</a>
        <button type="button" class="btn btn-primary" value="ok">닫기</button>
        <button type="button" class="btn" value="cancel" hidden>닫기</button>
      </div>
    </dialog>

    <dialog class="modal" id="commentDeleteDialog">
      <h2>댓글을 삭제할까요?</h2>
      <p>삭제한 댓글은 되돌릴 수 없습니다.</p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">취소</button>
        <button type="button" class="btn btn-danger" value="ok">삭제</button>
      </div>
    </dialog>
  </jsp:body>
</ui:layout>
