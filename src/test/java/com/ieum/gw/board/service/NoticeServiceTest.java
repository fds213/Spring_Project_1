package com.ieum.gw.board.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.board.dao.NoticeDAO;
import com.ieum.gw.board.dto.NoticeDTO;
import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;

class NoticeServiceTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BOARD_ID = 30L;
    private static final Long POST_ID = 100L;

    /** 게시판 줄과 저장한 글을 적어 두는 가짜 DAO */
    static class FakeNoticeDAO extends NoticeDAO {
        Long boardId;              // 지금 있는 공지 게시판 줄 (없으면 null)
        int boardInserts;          // 게시판 줄을 만든 횟수
        Long savedCompanyId;
        Long savedBoardId;
        Long listCompanyId;        // 목록을 부른 업체
        List<String> listWords;    // 목록 검색어
        int listOffset = -1;
        int listSize = -1;

        FakeNoticeDAO(Long boardId) {
            super(null);
            this.boardId = boardId;
        }

        @Override
        public Long selectNoticeBoardId(Long companyId) {
            return boardId;
        }

        @Override
        public int insertNoticeBoard(Long companyId) {
            boardInserts++;
            boardId = BOARD_ID;
            return 1;
        }

        @Override
        public Long selectNextPostId() {
            return POST_ID;
        }

        @Override
        public int insertNotice(Long postId, Long companyId, Long boardId, Long authorId, String title, String content) {
            savedCompanyId = companyId;
            savedBoardId = boardId;
            return 1;
        }

        @Override
        public List<NoticeDTO> selectNoticeList(Long companyId, List<String> words, int offset, int size) {
            listCompanyId = companyId;
            listWords = words;
            listOffset = offset;
            listSize = size;
            return new ArrayList<>();
        }

        @Override
        public List<Long> selectFileIds(Long companyId, Long postId) {
            return new ArrayList<>();
        }
    }

    private static LoginUser user(String role) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setRole(role);
        emp.setCompanyId(COMPANY_ID);
        emp.setEmployeeId(1L);
        emp.setUserAccountId(1L);
        return new LoginUser(emp);
    }

    @Test
    @DisplayName("공지 게시판 줄이 없는 새 업체는 첫 공지를 저장할 때 줄을 만들고 그 게시판에 저장한다")
    void createsBoardOnFirstNotice() {
        FakeNoticeDAO dao = new FakeNoticeDAO(null);

        Map<String, Object> result = new NoticeService(dao, null)
                .createNotice(user("ROLE_ADMIN"), "첫 공지", "<p>내용</p>", new ArrayList<>());

        assertEquals(POST_ID, result.get("id"));
        assertEquals(1, dao.boardInserts);
        assertEquals(COMPANY_ID, dao.savedCompanyId);
        assertEquals(BOARD_ID, dao.savedBoardId);
    }

    @Test
    @DisplayName("공지 게시판 줄이 이미 있으면 새로 만들지 않고 그 게시판에 저장한다")
    void usesExistingBoard() {
        FakeNoticeDAO dao = new FakeNoticeDAO(11L);

        new NoticeService(dao, null).createNotice(user("ROLE_HR"), "공지", "<p>내용</p>", new ArrayList<>());

        assertEquals(0, dao.boardInserts);
        assertEquals(11L, dao.savedBoardId);
    }

    @Test
    @DisplayName("공지를 쓸 권한이 없으면 403이고 게시판 줄도 만들지 않는다")
    void forbiddenCreatesNothing() {
        FakeNoticeDAO dao = new FakeNoticeDAO(null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> new NoticeService(dao, null).createNotice(user("ROLE_EMP"), "공지", "<p>내용</p>", new ArrayList<>()));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals(0, dao.boardInserts);
        assertEquals(null, dao.savedBoardId);
    }

    @Test
    @DisplayName("메인 화면 최신 공지: 우리 업체 공지를 검색어 없이 처음부터 정한 개수만큼, 많아도 10개")
    void recentNotices() {
        FakeNoticeDAO dao = new FakeNoticeDAO(BOARD_ID);
        NoticeService service = new NoticeService(dao, null);

        service.getRecentNotices(COMPANY_ID, 5);
        assertEquals(COMPANY_ID, dao.listCompanyId);
        assertEquals(0, dao.listWords.size());
        assertEquals(0, dao.listOffset);
        assertEquals(5, dao.listSize);

        service.getRecentNotices(COMPANY_ID, 100); // 너무 많이 달라고 해도 10개
        assertEquals(10, dao.listSize);
    }
}
