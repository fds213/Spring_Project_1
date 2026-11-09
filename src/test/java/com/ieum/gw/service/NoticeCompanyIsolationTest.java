package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dao.NoticeDAO;
import com.ieum.gw.dto.NoticeCommentDTO;
import com.ieum.gw.dto.NoticeDTO;
import com.ieum.gw.dto.NoticeFileDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;

/**
 * 업체 분리 (AGENTS.md '테스트'): 다른 업체의 공지 번호로 보기·수정·삭제·댓글·내려받기를 하면 거절한다.
 * 가짜 DAO는 실제 SQL처럼 업체 ID가 맞을 때만 자료를 돌려준다. Service가 화면 값이 아니라 로그인한 사람의 업체로 찾는지 본다.
 */
class NoticeCompanyIsolationTest {

    private static final Long MY_COMPANY = 7L;     // 공지가 있는 업체
    private static final Long OTHER_COMPANY = 8L;  // 다른 업체
    private static final Long POST_ID = 100L;      // 업체 7의 공지
    private static final Long COMMENT_ID = 500L;   // 그 공지의 댓글
    private static final Long FILE_ID = 55L;       // 그 공지의 첨부

    /** 업체 7의 공지 한 건만 있는 가짜 DB. 바꾸는 요청이 오면 적어 둔다 */
    static class FakeDAO extends NoticeDAO {
        final List<String> writes = new ArrayList<>();

        FakeDAO() {
            super(null);
        }

        private boolean mine(Long companyId, Long postId) {
            return MY_COMPANY.equals(companyId) && POST_ID.equals(postId);
        }

        @Override
        public NoticeDTO selectNotice(Long companyId, Long postId) {
            if (!mine(companyId, postId)) return null;
            NoticeDTO n = new NoticeDTO();
            n.setId(postId);
            n.setTitle("업체 7 공지");
            return n;
        }

        @Override
        public Long lockNotice(Long companyId, Long postId) {
            return mine(companyId, postId) ? postId : null;
        }

        @Override
        public int updateNotice(Long companyId, Long postId, String title, String content) {
            if (!mine(companyId, postId)) return 0;
            writes.add("update");
            return 1;
        }

        @Override
        public int deleteNotice(Long companyId, Long postId) {
            if (!mine(companyId, postId)) return 0;
            writes.add("delete");
            return 1;
        }

        @Override
        public int deleteCommentsOfPost(Long companyId, Long postId) {
            writes.add("deleteComments");
            return 0;
        }

        @Override
        public List<NoticeCommentDTO> selectComments(Long companyId, Long postId) {
            return new ArrayList<>();
        }

        @Override
        public Long selectCommentAuthor(Long companyId, Long postId, Long commentId) {
            return mine(companyId, postId) && COMMENT_ID.equals(commentId) ? 1L : null;
        }

        @Override
        public int insertComment(Long companyId, Long postId, Long authorId, String content) {
            writes.add("insertComment");
            return 1;
        }

        @Override
        public int deleteComment(Long companyId, Long postId, Long commentId) {
            writes.add("deleteComment");
            return 1;
        }

        @Override
        public List<Long> selectFileIds(Long companyId, Long postId) {
            List<Long> ids = new ArrayList<>();
            if (mine(companyId, postId)) ids.add(FILE_ID);
            return ids;
        }

        @Override
        public List<NoticeFileDTO> selectFiles(Long companyId, Long postId) {
            return new ArrayList<>();
        }
    }

    // 다른 업체(8)의 업체 관리자: 자기 업체에서는 공지를 다 다룰 수 있는 사람
    private static LoginUser otherAdmin() {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setRole("ROLE_ADMIN");
        emp.setCompanyId(OTHER_COMPANY);
        emp.setEmployeeId(1L);
        emp.setUserAccountId(1L);
        return new LoginUser(emp);
    }

    // 404로 거절되는지 (다른 업체 글은 '없는 글'과 같은 답을 받는다)
    private static void assertNotFound(Executable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @DisplayName("다른 업체 관리자가 공지 번호로 글을 열면 404")
    void cannotViewOtherCompanyNotice() {
        NoticeService service = new NoticeService(new FakeDAO(), null);
        assertNotFound(() -> service.getNotice(otherAdmin(), POST_ID));
        assertNotFound(() -> service.getNoticeForm(otherAdmin(), POST_ID));
    }

    @Test
    @DisplayName("다른 업체 관리자가 공지 번호로 수정·삭제하면 404이고 아무것도 바뀌지 않는다")
    void cannotChangeOtherCompanyNotice() {
        FakeDAO dao = new FakeDAO();
        NoticeService service = new NoticeService(dao, null);

        assertNotFound(() -> service.updateNotice(otherAdmin(), POST_ID, "제목", "<p>내용</p>", new ArrayList<>()));
        assertNotFound(() -> service.deleteNotice(otherAdmin(), POST_ID));
        assertEquals(List.of(), dao.writes);
    }

    @Test
    @DisplayName("다른 업체 공지에 댓글을 달거나 지우면 404이고 댓글이 바뀌지 않는다")
    void cannotTouchOtherCompanyComments() {
        FakeDAO dao = new FakeDAO();
        NoticeService service = new NoticeService(dao, null);

        assertNotFound(() -> service.addComment(otherAdmin(), POST_ID, "댓글"));
        assertNotFound(() -> service.deleteComment(otherAdmin(), POST_ID, COMMENT_ID));
        assertEquals(List.of(), dao.writes);
    }

    @Test
    @DisplayName("다른 업체 공지의 첨부 번호로 내려받으면 404")
    void cannotDownloadOtherCompanyFile() {
        NoticeService service = new NoticeService(new FakeDAO(), null);
        assertNotFound(() -> service.getFile(otherAdmin(), POST_ID, FILE_ID));
    }
}
