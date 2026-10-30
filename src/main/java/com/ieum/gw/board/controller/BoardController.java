package com.ieum.gw.board.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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
}