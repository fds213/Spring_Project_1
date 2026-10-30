package com.ieum.gw.board.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.board.dto.NoticeSaveDTO;
import com.ieum.gw.board.service.NoticeService;
import com.ieum.gw.common.dto.LoginUser;

import lombok.RequiredArgsConstructor;

// 게시판(공지사항·부서 게시판) Controller.
// 화면 주소 → JSP 연결은 docs/front-screen-api.md 1장, 데이터 주소는 2장(/board/api/...)을 따른다.
@Controller
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {

    private final NoticeService noticeService;

    // ---------- 화면 ----------

    // 공지사항: 네 주소 모두 같은 JSP(WEB-INF/views/board/notice.jsp)를 연다
    @GetMapping({"/notice", "/notice/view/{id}", "/notice/write", "/notice/edit/{id}"})
    public String notice() {
        return "board/notice";
    }

    // 부서 게시판: 네 주소 모두 같은 JSP(WEB-INF/views/board/dept.jsp)를 연다
    @GetMapping({"/dept", "/dept/view/{id}", "/dept/write", "/dept/edit/{id}"})
    public String dept() {
        return "board/dept";
    }

    // ---------- 데이터 (board.js가 부른다) ----------

    // 공지 목록: GET /board/api/notices?q=검색어&page=1
    @GetMapping("/api/notices")
    @ResponseBody
    public Map<String, Object> noticeList(@AuthenticationPrincipal LoginUser loginUser,
                                          @RequestParam(defaultValue = "") String q,
                                          @RequestParam(defaultValue = "1") int page) {
        return noticeService.getNoticeList(loginUser, q, page);
    }

    // 공지 쓰기 화면을 열 때: GET /board/api/notices/form
    // 쓸 권한이 없으면 403 → 화면이 안내하고 목록으로 돌아간다
    @GetMapping("/api/notices/form")
    @ResponseBody
    public Map<String, Object> noticeForm(@AuthenticationPrincipal LoginUser loginUser) {
        return noticeService.getNoticeForm(loginUser);
    }

    // 공지 저장: POST /board/api/notices  (본문 JSON: { scope, title, content, pinned, files })
    // 성공하면 { id: 새 글 번호 } → 화면이 그 글 보기로 이동
    @PostMapping("/api/notices")
    @ResponseBody
    public Map<String, Object> noticeCreate(@AuthenticationPrincipal LoginUser loginUser,
                                            @RequestBody NoticeSaveDTO form) {
        return noticeService.createNotice(loginUser, form.getTitle(), form.getContent());
    }

    // ---------- 오류 응답 ----------

    // Service가 던진 403·400·404를 화면이 읽는 모양 { "message": "안내 문구" }으로 바꾼다
    // (docs/front-screen-api.md 2장의 실패 규칙. 이 Controller 안에서만 동작한다)
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        String message = (e.getReason() != null) ? e.getReason() : "요청을 처리하지 못했습니다";
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", message));
    }
}