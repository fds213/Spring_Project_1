package com.ieum.gw.board.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

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