<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  익명 게시판 화면 (/board/anon, /board/anon/view/{id}, /board/anon/write, /board/anon/edit/{id}). 주소는 BoardController가 받는다.
  같은 업체 직원 누구나 보고 쓴다. 글·댓글에 쓴 사람 대신 '익명'이 보이고, 서버도 이름·사번을 보내지 않는다(업체 관리자도 같다).
  수정·삭제와 댓글 삭제는 쓴 사람 본인만 한다. 첨부파일은 받지 않는다(파일 이름·사진 정보로 쓴 사람이 드러날 수 있어서).
  화면 JS는 부서 게시판과 같은 board/board.js (makeMemberBoard, 요소 id 앞글자 anon).
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="익명 게시판" crumb="소통" menu="board-anon">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/board/board.js"></script>
    <script>routeNoticeBoard('board-anon' + location.pathname.slice((document.body.dataset.ctx + '/board/anon').length));</script>
  </jsp:attribute>
  <jsp:body>
    <%-- 게시판 전용 스타일 (공통 레이아웃에 화면별 CSS 칸이 없어서 본문 맨 앞에서 불러온다) --%>
    <link rel="stylesheet" href="${ctx}/resources/css/board/board.css">
    <section class="view" id="view-anon-list">
      <%-- 1. 목록: 작성자 칸이 없다 --%>
      <div class="page-head">
        <div>
          <h1>익명 게시판</h1>
          <p class="page-sub">글과 댓글에 쓴 사람이 보이지 않습니다. 고치거나 지우는 것은 쓴 사람 본인만 할 수 있어요.</p>
        </div>
        <div class="page-head-actions">
          <%-- 직원 누구나 쓸 수 있다. 읽기 전용 업체에서는 흐리게 막는다 --%>
          <a href="${ctx}/board/anon/write" class="btn btn-primary" id="anonBtnWrite" data-write>글쓰기</a>
        </div>
      </div>
      <form class="search-row" id="anonSearchForm" role="search">
        <input type="search" class="search-input" id="anonSearchInput" placeholder="제목·내용으로 검색" aria-label="게시글 검색" autocomplete="off">
        <span class="result-info" id="anonResultInfo" role="status"></span>
      </form>
      <div class="table-wrap">
        <table class="table table-fixed">
          <colgroup><col style="width: 10%"><col style="width: 72%"><col style="width: 18%"></colgroup>
          <thead><tr><th>번호</th><th>제목</th><th>작성일</th></tr></thead>
          <tbody id="anonRows"></tbody>
        </table>
      </div>
      <nav class="pager" id="anonPager" aria-label="페이지"></nav>
    </section>

    <section class="view" id="view-anon-detail">
      <%-- 2. 게시글 상세 + 댓글 --%>
      <article class="notice-article">
        <h1 class="notice-title" id="anonDetailTitle"></h1>
        <p class="notice-meta" id="anonDetailMeta"></p>
        <div class="notice-body rich-body" id="anonDetailBody"></div>
      </article>
      <div class="notice-actions">
        <a href="${ctx}/board/anon" class="btn">목록으로</a>
        <%-- 수정·삭제: 쓴 사람 본인에게만 --%>
        <div class="notice-actions-right" id="anonOwnerActions" hidden>
          <a href="#" class="btn" id="anonBtnEdit" data-write>수정</a>
          <button type="button" class="btn btn-danger" id="anonBtnDelete" data-write>삭제</button>
        </div>
      </div>

      <%-- 댓글: 직원 누구나 익명으로 쓰고, 본인 댓글만 지운다 --%>
      <section class="comments" aria-labelledby="anonCommentHead">
        <h2 id="anonCommentHead">댓글 <span class="comment-count" id="anonCommentCount">0</span></h2>
        <ul class="comment-list" id="anonComments"></ul>
        <p class="empty-note" id="anonCommentEmpty" hidden>첫 댓글을 남겨 보세요.</p>
        <form class="comment-form" id="anonCommentForm" novalidate>
          <label for="anonCommentText" class="sr-only">댓글</label>
          <textarea class="input" id="anonCommentText" maxlength="500" rows="3" placeholder="댓글을 입력하세요 (익명으로 올라갑니다)" aria-describedby="anonCommentError anonCommentCountNow"></textarea>
          <div class="field-foot">
            <p class="form-error" id="anonCommentError" role="alert"></p>
            <span class="counter" id="anonCommentCountNow">0 / 500</span>
          </div>
          <div class="comment-form-actions">
            <button type="submit" class="btn btn-primary" id="anonCommentSave" data-write>댓글 등록</button>
          </div>
        </form>
      </section>
    </section>

    <section class="view" id="view-anon-form">
      <%-- 3. 게시글 작성·수정 (같은 화면). 첨부파일 칸은 두지 않는다 --%>
      <div class="page-head">
        <div>
          <h1 id="anonFormTitle">익명 글쓰기</h1>
          <p class="page-sub">익명으로 올라갑니다. 쓴 사람이 드러나지 않게 파일은 붙일 수 없어요.</p>
        </div>
      </div>
      <form class="notice-form" id="anonForm" novalidate>
        <div class="field">
          <label for="anonTitle">제목</label>
          <input class="input" id="anonTitle" maxlength="100" autocomplete="off" aria-describedby="anonTitleError anonTitleCount">
          <div class="field-foot">
            <p class="form-error" id="anonTitleError" role="alert"></p>
            <span class="counter" id="anonTitleCount">0 / 100</span>
          </div>
        </div>
        <div class="field">
          <span class="field-label" id="anonContentLabel">내용</span>
          <div class="editor">
            <div class="editor-toolbar" role="toolbar" aria-label="글자 꾸미기"></div>
            <div class="editor-area" id="anonContent" contenteditable="true" role="textbox" aria-multiline="true" data-placeholder="내용을 입력하세요"
                 aria-labelledby="anonContentLabel" aria-describedby="anonContentError anonContentCount"></div>
          </div>
          <div class="field-foot">
            <p class="form-error" id="anonContentError" role="alert"></p>
            <span class="counter" id="anonContentCount">0 / 2000</span>
          </div>
        </div>
        <div class="bill-actions">
          <button type="button" class="btn" id="anonBtnCancel">취소</button>
          <button type="submit" class="btn btn-primary" id="anonBtnSave">저장</button>
        </div>
      </form>
    </section>

    <%-- 게시판 화면들이 같이 쓰는 창과 에디터 툴바 (board/board.js). 게시판 화면 JSP 끝에 둔다 --%>
    <dialog class="modal" id="deleteDialog">
      <h2>게시글을 삭제할까요?</h2>
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
