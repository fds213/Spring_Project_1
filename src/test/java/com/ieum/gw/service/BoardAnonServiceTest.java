package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.dao.BoardAnonDAO;
import com.ieum.gw.dao.NoticeDAO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.BoardAnonCommentDTO;
import com.ieum.gw.dto.BoardAnonPostDTO;
import com.ieum.gw.dto.LoginUser;

/**
 * 익명 게시판 규칙: 응답에 쓴 사람이 실리지 않고(업체 관리자도), 쓴 사람만 고치며, 다른 업체 글은 거절한다
 * (AGENTS.md '테스트'의 업체 분리). 가짜 DAO는 실제 SQL처럼 업체·글 번호가 맞을 때만 글을 돌려준다.
 */
class BoardAnonServiceTest {

    private static final Long COMPANY = 7L;
    private static final Long OTHER_COMPANY = 8L;
    private static final Long POST_ID = 100L;
    private static final Long COMMENT_ID = 500L;

    // 사원 ID는 응답 JSON에서 찾기 쉬운 값으로 둔다 (다른 숫자와 겹치지 않게)
    private static final Long WRITER = 731_001L;       // 글쓴이 (댓글 하나도 이 사람이 씀)
    private static final Long COWORKER = 731_002L;     // 같은 업체, 글쓴이가 아닌 사람
    private static final Long OTHER_CO_EMP = 731_003L; // 다른 업체

    /** 업체 7의 익명 게시판 글 한 건과 댓글 한 건만 있는 가짜 DB */
    static class FakeAnonDAO extends BoardAnonDAO {
        final List<String> writes = new ArrayList<>();
        Long boardId;
        int boardInserts;
        Long savedBoard;
        Long savedAuthor;
        List<String> listWords;
        int listOffset = -1;
        int total;
        boolean postJustRemoved; // 글을 조회한 뒤 잠그기 전에 다른 사람이 지운 상황

        FakeAnonDAO() {
            super(null);
        }

        /** 실제 SQL처럼 업체·글 번호가 모두 맞아야 찾는다 */
        private boolean ours(Long companyId, Long postId) {
            return COMPANY.equals(companyId) && POST_ID.equals(postId);
        }

        @Override
        public BoardAnonPostDTO selectPost(Long companyId, Long postId) {
            if (!ours(companyId, postId)) return null;
            BoardAnonPostDTO p = new BoardAnonPostDTO();
            p.setId(postId);
            p.setTitle("익명 글");
            p.setContent("<p>내용</p>");
            p.setCreatedAt("2026-11-10T10:00:00");
            p.setAuthorId(WRITER);
            return p;
        }

        @Override
        public Long lockPost(Long companyId, Long postId) {
            return ours(companyId, postId) && !postJustRemoved ? postId : null;
        }

        @Override
        public int countPosts(Long companyId, List<String> words) {
            return total;
        }

        @Override
        public List<BoardAnonPostDTO> selectPosts(Long companyId, List<String> words, int offset, int size) {
            listWords = words;
            listOffset = offset;
            List<BoardAnonPostDTO> rows = new ArrayList<>();
            rows.add(selectPost(COMPANY, POST_ID));
            rows.add(new BoardAnonPostDTO());
            return rows;
        }

        @Override
        public Long selectBoardId(Long companyId) {
            return boardId;
        }

        @Override
        public int insertBoard(Long companyId) {
            boardInserts++;
            boardId = 40L;
            return 1;
        }

        @Override
        public int insertPost(Long postId, Long companyId, Long boardId, Long authorId, String title, String content) {
            savedBoard = boardId;
            savedAuthor = authorId;
            writes.add("insert");
            return 1;
        }

        @Override
        public int updatePost(Long companyId, Long postId, Long authorId, String title, String content) {
            if (!ours(companyId, postId) || !WRITER.equals(authorId)) return 0;
            writes.add("update");
            return 1;
        }

        @Override
        public int deletePost(Long companyId, Long postId, Long authorId) {
            if (!ours(companyId, postId) || !WRITER.equals(authorId)) return 0;
            writes.add("delete");
            return 1;
        }

        @Override
        public List<BoardAnonCommentDTO> selectComments(Long companyId, Long postId) {
            BoardAnonCommentDTO c = new BoardAnonCommentDTO();
            c.setId(COMMENT_ID);
            c.setText("익명 댓글");
            c.setCreatedAt("2026-11-10T10:05:00");
            c.setAuthorId(WRITER);
            List<BoardAnonCommentDTO> rows = new ArrayList<>();
            rows.add(c);
            return rows;
        }
    }

    /** 댓글 저장·삭제와 글 번호를 맡는 가짜 공지 DAO */
    static class FakePostDAO extends NoticeDAO {
        final List<String> writes = new ArrayList<>();

        FakePostDAO() {
            super(null);
        }

        @Override
        public Long selectNextPostId() {
            return POST_ID;
        }

        @Override
        public Long selectCommentAuthor(Long companyId, Long postId, Long commentId) {
            return COMPANY.equals(companyId) && COMMENT_ID.equals(commentId) ? WRITER : null;
        }

        @Override
        public int insertComment(Long companyId, Long postId, Long authorId, String content) {
            writes.add("comment:" + content);
            return 1;
        }

        @Override
        public int deleteComment(Long companyId, Long postId, Long commentId) {
            writes.add("commentDelete");
            return 1;
        }

        @Override
        public int deleteCommentsOfPost(Long companyId, Long postId) {
            writes.add("commentsOfPost");
            return 1;
        }
    }

    private FakeAnonDAO board;
    private FakePostDAO posts;
    private BoardAnonService service;

    /** 시험마다 빈 가짜 DB로 Service를 새로 만든다 */
    @BeforeEach
    void setUp() {
        board = new FakeAnonDAO();
        posts = new FakePostDAO();
        service = new BoardAnonService(board, posts, new NoticeService(posts, null));
    }

    /** 그 사원·역할로 로그인한 사람 */
    private static LoginUser user(Long employeeId, String role) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setRole(role);
        emp.setCompanyId(employeeId.equals(OTHER_CO_EMP) ? OTHER_COMPANY : COMPANY);
        emp.setEmployeeId(employeeId);
        emp.setUserAccountId(employeeId);
        return new LoginUser(emp);
    }

    /** 일반 직원으로 로그인한 사람 */
    private static LoginUser emp(Long employeeId) {
        return user(employeeId, "ROLE_EMP");
    }

    /** 호출이 그 HTTP 상태로 거절되는지 본다 */
    private static void assertStatus(HttpStatus status, Executable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call);
        assertEquals(status, e.getStatus());
    }

    /** 화면이 받는 JSON 그대로 (Controller가 돌려주는 값을 Jackson이 바꾸는 것과 같다) */
    private static String json(Object value) throws Exception {
        return new ObjectMapper().writeValueAsString(value);
    }

    /** 응답 JSON에 쓴 사람을 가리키는 칸·값이 없는지 본다 */
    private static void assertNoWriter(String json) {
        assertFalse(json.contains(String.valueOf(WRITER)), json);
        assertFalse(json.contains("authorId"), json);
        assertFalse(json.contains("writer"), json);
        assertFalse(json.contains("employee"), json);
    }

    @Test
    @DisplayName("업체 관리자가 보는 목록·글 보기·댓글 응답에도 쓴 사람의 사원 ID·이름 칸이 없다")
    void responsesHideWriter() throws Exception {
        LoginUser admin = user(COWORKER, "ROLE_ADMIN");
        board.total = 2;

        assertNoWriter(json(service.getPosts(admin, "", 1)));
        assertNoWriter(json(service.getPost(admin, POST_ID)));
        assertNoWriter(json(service.addComment(admin, POST_ID, "댓글")));
    }

    @Test
    @DisplayName("글쓴이 본인이 받는 글 보기·수정 화면 응답에도 사원 ID는 없고 본인 표시만 있다")
    void writerSeesOnlyMineFlag() throws Exception {
        BoardAnonPostDTO mine = service.getPost(emp(WRITER), POST_ID);

        assertTrue(mine.isMine());
        assertTrue(mine.getComments().get(0).isCanDelete());
        assertNoWriter(json(mine));
        assertNoWriter(json(service.getForm(emp(WRITER), POST_ID)));
    }

    @Test
    @DisplayName("같은 업체 다른 직원에게는 내 글·내 댓글 표시가 꺼져 있다")
    void coworkerIsNotMine() {
        BoardAnonPostDTO seen = service.getPost(emp(COWORKER), POST_ID);

        assertFalse(seen.isMine());
        assertFalse(seen.getComments().get(0).isCanDelete());
    }

    @Test
    @DisplayName("다른 업체 사람이 글 번호로 보기·수정·삭제·댓글을 하면 404로 거절한다")
    void otherCompanyIsRejected() {
        LoginUser other = emp(OTHER_CO_EMP);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.getPost(other, POST_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getForm(other, POST_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updatePost(other, POST_ID, "제목", "내용"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deletePost(other, POST_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.addComment(other, POST_ID, "댓글"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteComment(other, POST_ID, COMMENT_ID));
        assertTrue(board.writes.isEmpty());
        assertTrue(posts.writes.isEmpty());
    }

    @Test
    @DisplayName("글쓴이가 아니면 업체 관리자여도 수정 화면·수정·삭제가 403이다")
    void onlyWriterCanChange() {
        LoginUser coworker = emp(COWORKER);
        LoginUser admin = user(COWORKER, "ROLE_ADMIN");

        assertStatus(HttpStatus.FORBIDDEN, () -> service.getForm(coworker, POST_ID));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updatePost(admin, POST_ID, "제목", "내용"));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.deletePost(admin, POST_ID));
        assertTrue(board.writes.isEmpty());
    }

    @Test
    @DisplayName("글쓴이는 수정할 수 있고, 삭제하면 그 글의 댓글도 함께 삭제 표시한다")
    void writerCanChange() {
        service.updatePost(emp(WRITER), POST_ID, "새 제목", "새 내용");
        service.deletePost(emp(WRITER), POST_ID);

        assertEquals(List.of("update", "delete"), board.writes);
        assertEquals(List.of("commentsOfPost"), posts.writes);
    }

    @Test
    @DisplayName("업체의 첫 글이면 익명 게시판 줄을 만들고, 작성자는 로그인한 사람으로 남긴다")
    void firstPostCreatesBoard() {
        Map<String, Object> result = service.createPost(emp(COWORKER), " 제목 ", "내용");

        assertEquals(POST_ID, result.get("id"));
        assertEquals(1, board.boardInserts);
        assertEquals(40L, board.savedBoard);
        assertEquals(COWORKER, board.savedAuthor);
    }

    @Test
    @DisplayName("이미 익명 게시판 줄이 있으면 새로 만들지 않는다")
    void existingBoardIsReused() {
        board.boardId = 41L;

        service.createPost(emp(WRITER), "제목", "내용");

        assertEquals(0, board.boardInserts);
        assertEquals(41L, board.savedBoard);
    }

    @Test
    @DisplayName("제목이나 내용이 비어 있으면 저장하지 않고 400이다")
    void emptyTitleIsRejected() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createPost(emp(WRITER), "  ", "내용"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createPost(emp(WRITER), "제목", "<p></p>"));
        assertTrue(board.writes.isEmpty());
    }

    @Test
    @DisplayName("직원 누구나 댓글을 달고, 남의 댓글은 업체 관리자여도 지울 수 없다")
    void commentRules() {
        service.addComment(emp(COWORKER), POST_ID, " 동의합니다 ");
        assertEquals(List.of("comment:동의합니다"), posts.writes);

        assertStatus(HttpStatus.FORBIDDEN, () -> service.deleteComment(user(COWORKER, "ROLE_ADMIN"), POST_ID, COMMENT_ID));
        assertEquals(List.of("comment:동의합니다"), posts.writes);

        service.deleteComment(emp(WRITER), POST_ID, COMMENT_ID);
        assertEquals(List.of("comment:동의합니다", "commentDelete"), posts.writes);
    }

    @Test
    @DisplayName("글이 막 지워져 글 줄을 잠그지 못하면 댓글을 저장하지 않고 404다")
    void commentIsRejectedWhenPostWasJustRemoved() {
        board.postJustRemoved = true;

        assertStatus(HttpStatus.NOT_FOUND, () -> service.addComment(emp(COWORKER), POST_ID, "댓글"));

        assertTrue(posts.writes.isEmpty());
    }

    @Test
    @DisplayName("목록은 검색어를 나눠 넘기고, 최신 글이 큰 번호인 목록 번호를 붙인다")
    void listShape() {
        board.total = 23;

        Map<String, Object> result = service.getPosts(emp(WRITER), "회식 장소", 2);

        assertEquals(3, result.get("pages"));
        assertEquals(List.of("회식", "장소"), board.listWords);
        assertEquals(10, board.listOffset);
        @SuppressWarnings("unchecked")
        List<BoardAnonPostDTO> items = (List<BoardAnonPostDTO>) result.get("items");
        assertEquals(13, items.get(0).getNo());
        assertEquals(12, items.get(1).getNo());
    }
}
