package com.ieum.gw.board.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.board.dao.NoticeDAO;
import com.ieum.gw.board.dto.NoticeDTO;
import com.ieum.gw.common.dto.LoginUser;

import lombok.RequiredArgsConstructor;

// 공지사항 규칙을 맡는 Service.
// 업체는 로그인한 사람 정보(LoginUser)에서만 꺼낸다. 화면이 보낸 값으로 업체를 정하지 않는다 (AGENTS.md)
@Service
@RequiredArgsConstructor
public class NoticeService {

    private static final int PAGE_SIZE = 10; // 한 페이지에 10개
    private static final int TITLE_MAX = 100;    // 제목 글자 수 (board.js와 같게)
    private static final int CONTENT_MAX = 2000; // 내용 글자 수: 서식을 뺀 글자 기준 (board.js와 같게)
    private static final int CONTENT_HTML_MAX = 100000; // 서식까지 포함한 길이 상한 (터무니없이 큰 요청 막기)

    private final NoticeDAO dao;

    // 공지 목록 화면에 보낼 값을 만든다 (board.js renderList가 읽는 모양)
    public Map<String, Object> getNoticeList(LoginUser user, String q, int page) {
        Long companyId = user.getCompanyId();
        List<String> words = splitWords(q);

        // 1. 전체 개수와 페이지 수
        int total = dao.countNotices(companyId, words);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(Math.max(1, page), pages); // 1 ~ 마지막 페이지 사이로 맞춘다
        int offset = (page - 1) * PAGE_SIZE;

        // 2. 이번 페이지 공지 + 목록 번호 (최신 글이 가장 큰 번호)
        List<NoticeDTO> items = dao.selectNoticeList(companyId, words, offset, PAGE_SIZE);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setNo(total - offset - i);
        }

        // 3. 이 사람이 할 수 있는 일 (서버가 저장할 때도 다시 검사한다)
        boolean manager = isManager(user);
        Map<String, Object> perms = new HashMap<>();
        perms.put("canWrite", manager);   // 글쓰기 버튼: 업체 관리자·인사 담당
        perms.put("allDepts", manager);   // 모든 부서 공지를 보는 사람

        // 4. 화면에 보낼 모양
        Map<String, Object> result = new HashMap<>();
        result.put("page", page);
        result.put("pages", pages);
        result.put("total", total);
        result.put("items", items);
        result.put("pinned", new ArrayList<NoticeDTO>()); // 맨 위 고정: DB에 칸이 생기기 전까지 비어 있음
        result.put("myDept", "부서");                       // 부서 공지가 생기면 실제 부서 이름으로
        result.put("perms", perms);
        return result;
    }

    // ---------- 글쓰기 ----------

    // 공지 쓰기 화면을 열 때 보낼 값 (board.js fillForm이 읽는 모양)
    public Map<String, Object> getNoticeForm(LoginUser user) {
        checkManager(user); // 관리자·인사 담당이 아니면 여기서 403으로 멈춘다

        Map<String, Object> result = new HashMap<>();
        result.put("notice", null);            // 새 글이라 채울 값이 없다 (수정 단계에서 채운다)
        result.put("scopes", List.of("ALL"));  // 고를 수 있는 범위: 부서 공지가 보류라 전사 공지만
        result.put("scopeLocked", false);      // 범위 선택 상자를 잠글지 (부서장용, 지금은 안 씀)
        result.put("myDept", "부서");           // 부서 공지가 생기면 실제 부서 이름으로
        return result;
    }

    // 공지 저장. 성공하면 새 글 번호를 돌려준다 → 화면이 그 글 보기로 이동
    @Transactional
    public Map<String, Object> createNotice(LoginUser user, String title, String content) {
        checkManager(user);

        // 1. 값 검사 (화면도 검사하지만, 주소로 직접 보내는 요청도 있으니 서버가 다시 본다)
        title = (title == null) ? "" : title.trim();
        content = (content == null) ? "" : content;
        validate(title, content);

        // 2. 우리 업체의 공지 게시판 번호
        Long companyId = user.getCompanyId();
        Long boardId = dao.selectNoticeBoardId(companyId);
        if (boardId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "공지 게시판이 아직 준비되지 않았습니다. 업체 관리자에게 알려 주세요");
        }

        // 3. 번호를 먼저 뽑고 저장 (업체·작성자는 로그인 정보에서만 꺼낸다)
        Long postId = dao.selectNextPostId();
        dao.insertNotice(postId, companyId, boardId, user.getEmployeeId(), title, content);

        Map<String, Object> result = new HashMap<>();
        result.put("id", postId);
        return result;
    }

    // 관리자·인사 담당이 아니면 403
    private void checkManager(LoginUser user) {
        if (!isManager(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "공지를 쓸 권한이 없습니다");
        }
    }

    // 제목·내용 검사. 틀리면 400과 안내 문구
    private void validate(String title, String content) {
        String text = textOf(content);
        String message = null;
        if (title.isEmpty())                          message = "제목을 입력해 주세요";
        else if (title.length() > TITLE_MAX)          message = "제목은 " + TITLE_MAX + "자까지 쓸 수 있습니다";
        else if (text.isEmpty())                      message = "내용을 입력해 주세요";
        else if (text.length() > CONTENT_MAX)         message = "내용은 " + CONTENT_MAX + "자까지 쓸 수 있습니다";
        else if (content.length() > CONTENT_HTML_MAX) message = "내용이 너무 깁니다";
        if (message != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    // 서식(HTML 태그)을 뺀 글자만 남긴다 (board.js textOf와 같은 기준으로 센다)
    private String textOf(String html) {
        return html
                .replaceAll("(?i)<br\\s*/?>", "\n")           // 줄바꿈
                .replaceAll("(?i)</(p|div|li)>", "\n")         // 문단·목록 끝도 줄바꿈
                .replaceAll("<[^>]*>", "")                     // 나머지 태그는 지운다
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
                .replace("\u200B", "")                         // 에디터가 넣는 보이지 않는 글자
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    // 검색어를 띄어쓰기로 나눈다. 비어 있으면 null (= 검색 안 함)
    private List<String> splitWords(String q) {
        if (q == null || q.trim().isEmpty()) return null;
        return Arrays.asList(q.trim().split("\\s+"));
    }

    // 업체 관리자 또는 인사 담당인가
    private boolean isManager(LoginUser user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
    }
}