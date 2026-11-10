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

import com.ieum.gw.dao.BoardDeptDAO;
import com.ieum.gw.dao.NoticeDAO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.BoardDeptPostDTO;
import com.ieum.gw.dto.IdNameDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.NoticeCommentDTO;
import com.ieum.gw.dto.NoticeFileDTO;

/**
 * 부서 게시판 규칙: 같은 부서만 보고, 쓴 사람만 고치고, 다른 업체 글은 거절한다 (AGENTS.md '테스트'의 업체 분리).
 * 가짜 DAO는 실제 SQL처럼 업체·부서가 맞을 때만 글을 돌려준다.
 */
class BoardDeptServiceTest {

    private static final Long COMPANY = 7L;
    private static final Long OTHER_COMPANY = 8L;
    private static final Long DEV = 10L;      // 글이 있는 부서
    private static final Long SALES = 20L;    // 같은 업체의 다른 부서
    private static final Long POST_ID = 100L;
    private static final Long COMMENT_ID = 500L;
    private static final Long FILE_ID = 55L;

    private static final Long WRITER = 1L;    // 개발 부서, 글쓴이
    private static final Long TEAMMATE = 2L;  // 개발 부서, 글쓴이가 아닌 사람
    private static final Long SALES_EMP = 3L; // 영업 부서
    private static final Long NO_DEPT_EMP = 4L; // 부서 없음
    private static final Long OTHER_CO_EMP = 5L; // 다른 업체, 부서 번호는 우연히 개발 부서와 같다

    /** 업체 7 개발 부서의 글 한 건과 댓글 한 건만 있는 가짜 DB */
    static class FakeBoardDAO extends BoardDeptDAO {
        final List<String> writes = new ArrayList<>();
        Long boardId;
        int boardInserts;
        Long savedDept;
        Long savedBoard;
        Long savedAuthor;
        List<String> listWords;
        int listOffset = -1;
        int total;
        boolean postJustRemoved; // 글을 조회한 뒤 잠그기 전에 다른 사람이 지운 상황

        FakeBoardDAO() {
            super(null);
        }

        @Override
        public IdNameDTO selectMyDept(Long companyId, Long employeeId) {
            if (employeeId.equals(NO_DEPT_EMP)) return null;
            if (employeeId.equals(SALES_EMP)) return dept(SALES, "영업팀");
            return dept(DEV, "개발팀");
        }

        /** 부서 번호·이름 묶음 */
        private IdNameDTO dept(Long id, String name) {
            IdNameDTO d = new IdNameDTO();
            d.setId(id);
            d.setName(name);
            return d;
        }

        /** 실제 SQL처럼 업체·부서·글 번호가 모두 맞아야 찾는다 */
        private boolean mine(Long companyId, Long departmentId, Long postId) {
            return COMPANY.equals(companyId) && DEV.equals(departmentId) && POST_ID.equals(postId);
        }

        @Override
        public BoardDeptPostDTO selectPost(Long companyId, Long departmentId, Long postId) {
            if (!mine(companyId, departmentId, postId)) return null;
            BoardDeptPostDTO p = new BoardDeptPostDTO();
            p.setId(postId);
            p.setTitle("개발팀 글");
            p.setAuthorId(WRITER);
            return p;
        }

        /** 실제 SQL처럼 업체·부서·글 번호가 맞고 지우지 않은 글만 잠근다 */
        @Override
        public Long lockPost(Long companyId, Long departmentId, Long postId) {
            return mine(companyId, departmentId, postId) && !postJustRemoved ? postId : null;
        }

        @Override
        public int countPosts(Long companyId, Long departmentId, List<String> words) {
            return total;
        }

        @Override
        public List<BoardDeptPostDTO> selectPosts(Long companyId, Long departmentId, List<String> words, int offset, int size) {
            listWords = words;
            listOffset = offset;
            List<BoardDeptPostDTO> rows = new ArrayList<>();
            rows.add(new BoardDeptPostDTO());
            rows.add(new BoardDeptPostDTO());
            return rows;
        }

        @Override
        public Long selectBoardId(Long companyId, Long departmentId) {
            return boardId;
        }

        @Override
        public int insertBoard(Long companyId, Long departmentId) {
            boardInserts++;
            boardId = 30L;
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
        public int updatePost(Long companyId, Long departmentId, Long postId, Long authorId, String title, String content) {
            if (!mine(companyId, departmentId, postId) || !WRITER.equals(authorId)) return 0;
            writes.add("update");
            return 1;
        }

        @Override
        public int deletePost(Long companyId, Long departmentId, Long postId, Long authorId) {
            if (!mine(companyId, departmentId, postId) || !WRITER.equals(authorId)) return 0;
            writes.add("delete");
            return 1;
        }
    }

    /** 댓글·첨부를 맡는 가짜 공지 DAO. 업체 7의 글에 댓글 하나(글쓴이가 씀)와 첨부 하나가 있다 */
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
        public List<NoticeCommentDTO> selectComments(Long companyId, Long postId) {
            NoticeCommentDTO c = new NoticeCommentDTO();
            c.setId(COMMENT_ID);
            c.setAuthorId(WRITER);
            List<NoticeCommentDTO> rows = new ArrayList<>();
            rows.add(c);
            return rows;
        }

        @Override
        public Long selectCommentAuthor(Long companyId, Long postId, Long commentId) {
            return COMMENT_ID.equals(commentId) ? WRITER : null;
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

        @Override
        public List<NoticeFileDTO> selectFiles(Long companyId, Long postId) {
            NoticeFileDTO f = new NoticeFileDTO();
            f.setId(FILE_ID);
            List<NoticeFileDTO> rows = new ArrayList<>();
            rows.add(f);
            return rows;
        }

        @Override
        public List<Long> selectFileIds(Long companyId, Long postId) {
            return new ArrayList<>(List.of(FILE_ID));
        }

        @Override
        public int deletePostFile(Long companyId, Long postId, Long attachmentId) {
            return 1;
        }
    }

    private FakeBoardDAO board;
    private FakePostDAO posts;
    private BoardDeptService service;

    /** 시험마다 빈 가짜 DB로 Service를 새로 만든다 */
    @BeforeEach
    void setUp() {
        board = new FakeBoardDAO();
        posts = new FakePostDAO();
        service = new BoardDeptService(board, posts, new NoticeService(posts, null), null);
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

    @Test
    @DisplayName("같은 부서 사람은 글을 보고, 글쓴이만 내 글로 표시되며 첨부 주소는 부서 게시판 주소다")
    void teammateSeesPost() {
        BoardDeptPostDTO byWriter = service.getPost(emp(WRITER), POST_ID);
        BoardDeptPostDTO byTeammate = service.getPost(emp(TEAMMATE), POST_ID);

        assertTrue(byWriter.isMine());
        assertFalse(byTeammate.isMine());
        assertEquals("/board/api/dept/posts/100/files/55", byTeammate.getFiles().get(0).getUrl());
    }

    @Test
    @DisplayName("다른 부서 사람이 글 번호로 보기·수정·삭제·댓글·내려받기를 하면 404로 거절한다")
    void otherDeptIsRejected() {
        LoginUser sales = emp(SALES_EMP);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.getPost(sales, POST_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getForm(sales, POST_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updatePost(sales, POST_ID, "제목", "내용", null));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deletePost(sales, POST_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.addComment(sales, POST_ID, "댓글"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteComment(sales, POST_ID, COMMENT_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getFile(sales, POST_ID, FILE_ID));
        assertTrue(board.writes.isEmpty());
        assertTrue(posts.writes.isEmpty());
    }

    @Test
    @DisplayName("다른 업체 사람은 부서 번호가 같아도 글을 볼 수 없다")
    void otherCompanyIsRejected() {
        LoginUser other = emp(OTHER_CO_EMP);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.getPost(other, POST_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deletePost(other, POST_ID));
        assertTrue(board.writes.isEmpty());
    }

    @Test
    @DisplayName("부서가 없는 사람은 목록·글쓰기·첨부 올리기가 403이다")
    void noDeptIsForbidden() {
        LoginUser none = emp(NO_DEPT_EMP);

        assertStatus(HttpStatus.FORBIDDEN, () -> service.getPosts(none, "", 1));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.getForm(none, null));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.createPost(none, "제목", "내용", null));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.uploadFiles(none, null));
    }

    @Test
    @DisplayName("부서가 없어도 공지를 쓰는 업체 관리자는 첨부를 올릴 수 있다 (권한 검사를 지나 파일 검사까지 간다)")
    void managerWithoutDeptCanUpload() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.uploadFiles(user(NO_DEPT_EMP, "ROLE_ADMIN"), null));
    }

    @Test
    @DisplayName("부서원은 공지 권한이 없어도 첨부를 올릴 수 있다 (권한 검사를 지나 파일 검사까지 간다)")
    void teammateCanUpload() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.uploadFiles(emp(TEAMMATE), null));
    }

    @Test
    @DisplayName("그 부서의 첫 글이면 부서 게시판 줄을 만들고, 작성자는 로그인한 사람이다")
    void firstPostCreatesBoard() {
        Map<String, Object> result = service.createPost(emp(TEAMMATE), " 제목 ", "내용", null);

        assertEquals(POST_ID, result.get("id"));
        assertEquals(1, board.boardInserts);
        assertEquals(30L, board.savedBoard);
        assertEquals(TEAMMATE, board.savedAuthor);
    }

    @Test
    @DisplayName("이미 게시판 줄이 있으면 새로 만들지 않는다")
    void existingBoardIsReused() {
        board.boardId = 31L;

        service.createPost(emp(WRITER), "제목", "내용", null);

        assertEquals(0, board.boardInserts);
        assertEquals(31L, board.savedBoard);
    }

    @Test
    @DisplayName("제목이나 내용이 비어 있으면 저장하지 않고 400이다")
    void emptyTitleIsRejected() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createPost(emp(WRITER), "  ", "내용", null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createPost(emp(WRITER), "제목", "<p></p>", null));
        assertTrue(board.writes.isEmpty());
    }

    @Test
    @DisplayName("글쓴이가 아니면 같은 부서여도, 업체 관리자여도 수정·삭제가 403이다")
    void onlyWriterCanChange() {
        LoginUser teammate = emp(TEAMMATE);
        LoginUser admin = user(TEAMMATE, "ROLE_ADMIN");

        assertStatus(HttpStatus.FORBIDDEN, () -> service.getForm(teammate, POST_ID));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updatePost(teammate, POST_ID, "제목", "내용", null));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.deletePost(admin, POST_ID));
        assertTrue(board.writes.isEmpty());
    }

    @Test
    @DisplayName("글쓴이는 수정할 수 있고, 삭제하면 그 글의 댓글도 함께 삭제 표시한다")
    void writerCanChange() {
        service.updatePost(emp(WRITER), POST_ID, "새 제목", "새 내용", null);
        service.deletePost(emp(WRITER), POST_ID);

        assertEquals(List.of("update", "delete"), board.writes);
        assertEquals(List.of("commentsOfPost"), posts.writes);
    }

    @Test
    @DisplayName("부서원 누구나 댓글을 달고, 삭제 버튼은 본인 댓글에만 보인다")
    void commentRules() {
        List<NoticeCommentDTO> comments = service.addComment(emp(TEAMMATE), POST_ID, " 확인했습니다 ");

        assertEquals(List.of("comment:확인했습니다"), posts.writes);
        assertFalse(comments.get(0).isCanDelete());
        assertTrue(service.getPost(emp(WRITER), POST_ID).getComments().get(0).isCanDelete());
    }

    @Test
    @DisplayName("글이 막 지워져 글 줄을 잠그지 못하면 댓글을 저장하지 않고 404다")
    void commentIsRejectedWhenPostWasJustRemoved() {
        board.postJustRemoved = true;

        assertStatus(HttpStatus.NOT_FOUND, () -> service.addComment(emp(TEAMMATE), POST_ID, "댓글"));

        assertTrue(posts.writes.isEmpty());
    }

    @Test
    @DisplayName("남의 댓글은 업체 관리자여도 지울 수 없고, 본인 댓글은 지운다")
    void onlyCommentWriterCanDelete() {
        assertStatus(HttpStatus.FORBIDDEN, () -> service.deleteComment(user(TEAMMATE, "ROLE_ADMIN"), POST_ID, COMMENT_ID));
        assertTrue(posts.writes.isEmpty());

        service.deleteComment(emp(WRITER), POST_ID, COMMENT_ID);
        assertEquals(List.of("commentDelete"), posts.writes);
    }

    @Test
    @DisplayName("글에 붙지 않은 파일 번호로 내려받으면 404다")
    void fileMustBelongToPost() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getFile(emp(WRITER), POST_ID, 999L));
    }

    @Test
    @DisplayName("목록은 내 부서 이름과 검색어, 최신 글이 큰 번호인 목록 번호를 돌려준다")
    void listShape() {
        board.total = 23;

        Map<String, Object> result = service.getPosts(emp(WRITER), "회의 자료", 2);

        assertEquals("개발팀", result.get("dept"));
        assertEquals(3, result.get("pages"));
        assertEquals(List.of("회의", "자료"), board.listWords);
        assertEquals(10, board.listOffset);
        @SuppressWarnings("unchecked")
        List<BoardDeptPostDTO> items = (List<BoardDeptPostDTO>) result.get("items");
        assertEquals(13, items.get(0).getNo());
        assertEquals(12, items.get(1).getNo());
    }
}
