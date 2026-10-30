<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  부서 게시판 화면 (/board/dept, /board/dept/view/{id}, /board/dept/write, /board/dept/edit/{id}). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버가 감싸 보낸 응답({ success, message, data })은 common.js 의 api 가 푼다(docs/front-screen-api.md 2장).
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="부서 게시판" crumb="소통" menu="board-dept">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/board/board.js"></script>
    <script>routeNoticeBoard('board-dept' + location.pathname.slice((document.body.dataset.ctx + '/board/dept').length));</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view" id="view-board-list">
      <%-- ===== 부서 게시판 (board.js) ===== --%>
      <%-- 1. 게시판 목록: 내 부서 글만 보인다 --%>
      <div class="page-head">
        <div>
          <h1 id="bListTitle">부서 게시판</h1>
          <p class="page-sub" id="bListSub"></p>
        </div>
        <div class="page-head-actions">
          <%-- 부서원 누구나 쓸 수 있다. 읽기 전용 업체에서는 흐리게 막는다 --%>
          <a href="${ctx}/board/dept/write" class="btn btn-primary" id="bBtnWrite" data-write>글쓰기</a>
        </div>
      </div>
      <form class="search-row" id="bSearchForm" role="search">
        <input type="search" class="search-input" id="bSearchInput" placeholder="제목·내용으로 검색" aria-label="게시글 검색" autocomplete="off">
        <span class="result-info" id="bResultInfo" role="status"></span>
      </form>
      <div class="table-wrap">
        <table class="table table-fixed">
          <colgroup><col style="width: 10%"><col style="width: 56%"><col style="width: 16%"><col style="width: 18%"></colgroup>
          <thead><tr><th>번호</th><th>제목</th><th>작성자</th><th>작성일</th></tr></thead>
          <tbody id="bRows"></tbody>
        </table>
      </div>
      <nav class="pager" id="bPager" aria-label="페이지"></nav>
    </section>

    <section class="view" id="view-board-detail">
      <%-- 2. 게시글 상세 + 댓글 --%>
      <article class="notice-article">
        <h1 class="notice-title" id="bDetailTitle"></h1>
        <p class="notice-meta" id="bDetailMeta"></p>
        <div class="notice-body rich-body" id="bDetailBody"></div>
        <section class="attach-box" id="bDetailFiles" hidden aria-labelledby="bDetailFilesHead">
          <h2 id="bDetailFilesHead">첨부파일 <span class="comment-count" id="bDetailFileCount"></span></h2>
          <div class="attach-images" id="bDetailImages"></div>
          <ul class="attach-list" id="bDetailFileList"></ul>
        </section>
      </article>
      <div class="notice-actions">
        <a href="${ctx}/board/dept" class="btn">목록으로</a>
        <%-- 수정·삭제: 글쓴이 본인에게만 --%>
        <div class="notice-actions-right" id="bOwnerActions" hidden>
          <a href="#" class="btn" id="bBtnEdit" data-write>수정</a>
          <button type="button" class="btn btn-danger" id="bBtnDelete" data-write>삭제</button>
        </div>
      </div>

      <%-- 댓글: 부서원 누구나 쓰고, 본인 댓글만 지운다 --%>
      <section class="comments" aria-labelledby="bCommentHead">
        <h2 id="bCommentHead">댓글 <span class="comment-count" id="bCommentCount">0</span></h2>
        <ul class="comment-list" id="bComments"></ul>
        <p class="empty-note" id="bCommentEmpty" hidden>첫 댓글을 남겨 보세요.</p>
        <form class="comment-form" id="bCommentForm" novalidate>
          <label for="bCommentText" class="sr-only">댓글</label>
          <textarea class="input" id="bCommentText" maxlength="500" rows="3" placeholder="댓글을 입력하세요" aria-describedby="bCommentError bCommentCountNow"></textarea>
          <div class="field-foot">
            <p class="form-error" id="bCommentError" role="alert"></p>
            <span class="counter" id="bCommentCountNow">0 / 500</span>
          </div>
          <div class="comment-form-actions">
            <button type="submit" class="btn btn-primary" id="bCommentSave" data-write>댓글 등록</button>
          </div>
        </form>
      </section>
    </section>

    <section class="view" id="view-board-form">
      <%-- 3. 게시글 작성·수정 (같은 화면) --%>
      <div class="page-head">
        <div>
          <h1 id="bFormTitle">글쓰기</h1>
          <p class="page-sub" id="bFormSub"></p>
        </div>
      </div>
      <form class="notice-form" id="bForm" novalidate>
        <div class="field">
          <label for="bTitle">제목</label>
          <input class="input" id="bTitle" maxlength="100" autocomplete="off" aria-describedby="bTitleError bTitleCount">
          <div class="field-foot">
            <p class="form-error" id="bTitleError" role="alert"></p>
            <span class="counter" id="bTitleCount">0 / 100</span>
          </div>
        </div>
        <div class="field">
          <span class="field-label" id="bContentLabel">내용</span>
          <div class="editor">
            <div class="editor-toolbar" role="toolbar" aria-label="글자 꾸미기"></div>
            <div class="editor-area" id="bContent" contenteditable="true" role="textbox" aria-multiline="true" data-placeholder="내용을 입력하세요"
                 aria-labelledby="bContentLabel" aria-describedby="bContentError bContentCount"></div>
          </div>
          <div class="field-foot">
            <p class="form-error" id="bContentError" role="alert"></p>
            <span class="counter" id="bContentCount">0 / 2000</span>
          </div>
        </div>
        <%-- 첨부파일: 공지와 같은 규칙 (최대 5개, 파일당 10MB) --%>
        <div class="field">
          <span class="field-label">첨부파일 <span class="muted">(최대 5개 · 이미지·문서 · 파일당 10MB)</span></span>
          <div class="attach-pick">
            <button type="button" class="btn" id="bBtnPickFiles">파일 선택</button>
            <input type="file" id="bFiles" multiple hidden
                   accept=".jpg,.jpeg,.png,.gif,.pdf,.docx,.xlsx,.pptx,.hwpx,.hwp">
            <span class="muted" id="bFilesInfo">선택한 파일 없음</span>
          </div>
          <ul class="attach-list is-edit" id="bFileList"></ul>
          <p class="form-error" id="bFilesError" role="alert"></p>
        </div>
        <div class="bill-actions">
          <button type="button" class="btn" id="bBtnCancel">취소</button>
          <button type="submit" class="btn btn-primary" id="bBtnSave">저장</button>
        </div>
      </form>
    </section>

    <%-- 공지사항·부서 게시판이 같이 쓰는 창과 에디터 툴바. 게시판 화면 JSP 끝에 둔다 --%>
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
