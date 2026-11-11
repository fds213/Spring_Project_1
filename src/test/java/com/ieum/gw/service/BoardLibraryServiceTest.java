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

import com.ieum.gw.dao.BoardLibraryDAO;
import com.ieum.gw.dao.NoticeDAO;
import com.ieum.gw.dto.AttachmentDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.BoardLibraryPostDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.NoticeFileDTO;

/**
 * 자료실 규칙: 파일이 있어야 저장되고, 올린 사람만 고치며, 다른 업체 자료·파일은 거절한다 (AGENTS.md '테스트'의 업체 분리).
 * 가짜 DAO는 실제 SQL처럼 업체·글 번호가 맞을 때만 글을 돌려준다.
 */
class BoardLibraryServiceTest {

    private static final Long COMPANY = 7L;
    private static final Long OTHER_COMPANY = 8L;
    private static final Long POST_ID = 200L;     // 이미 있는 자료
    private static final Long NEW_POST_ID = 201L; // 새로 저장할 때 뽑히는 번호
    private static final Long FILE_ID = 66L;     // 글에 붙어 있는 파일
    private static final Long NEW_FILE_ID = 67L; // 방금 올린, 아직 안 붙은 파일

    private static final Long WRITER = 1L;       // 올린 사람
    private static final Long COWORKER = 2L;     // 같은 업체, 올린 사람이 아닌 사람
    private static final Long OTHER_CO_EMP = 3L; // 다른 업체

    /** 업체 7의 자료실 글 한 건만 있는 가짜 DB */
    static class FakeLibraryDAO extends BoardLibraryDAO {
        final List<String> writes = new ArrayList<>();
        Long boardId;
        int boardInserts;
        Long savedBoard;
        Long savedAuthor;

        FakeLibraryDAO() {
            super(null);
        }

        /** 실제 SQL처럼 업체·글 번호가 모두 맞아야 찾는다 */
        private boolean ours(Long companyId, Long postId) {
            return COMPANY.equals(companyId) && POST_ID.equals(postId);
        }

        @Override
        public BoardLibraryPostDTO selectPost(Long companyId, Long postId) {
            if (!ours(companyId, postId)) return null;
            BoardLibraryPostDTO p = new BoardLibraryPostDTO();
            p.setId(postId);
            p.setTitle("양식 모음");
            p.setWriterName("김이음");
            p.setAuthorId(WRITER);
            return p;
        }

        @Override
        public Long selectBoardId(Long companyId) {
            return boardId;
        }

        @Override
        public int insertBoard(Long companyId) {
            boardInserts++;
            boardId = 50L;
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
    }

    /** 첨부(POST_FILE)와 글 번호를 맡는 가짜 공지 DAO. 업체 7의 글에 파일 하나가 붙어 있다 */
    static class FakePostDAO extends NoticeDAO {
        final List<String> writes = new ArrayList<>();

        FakePostDAO() {
            super(null);
        }

        @Override
        public Long selectNextPostId() {
            return NEW_POST_ID;
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
            return COMPANY.equals(companyId) && POST_ID.equals(postId) ? new ArrayList<>(List.of(FILE_ID)) : new ArrayList<>();
        }

        @Override
        public int insertPostFile(Long companyId, Long postId, Long attachmentId) {
            writes.add("attach:" + attachmentId);
            return 1;
        }

        @Override
        public int deletePostFile(Long companyId, Long postId, Long attachmentId) {
            writes.add("detach:" + attachmentId);
            return 1;
        }
    }

    /** 공통 파일 기능 대신: 업체 7의 파일만 있고, 올린 사람은 WRITER다 */
    static class FakeAttachmentService extends AttachmentService {
        FakeAttachmentService() {
            super(null, null);
        }

        @Override
        public AttachmentDTO get(Long attachmentId, Long companyId) {
            if (!COMPANY.equals(companyId)) {
                throw new IllegalArgumentException("파일을 찾을 수 없습니다");
            }
            AttachmentDTO a = new AttachmentDTO();
            a.setAttachmentId(attachmentId);
            a.setCompanyId(companyId);
            a.setUploaderAccountId(WRITER);
            return a;
        }

        @Override
        public void markAttached(Long attachmentId, Long companyId) {
            // 가짜: 늘 성공
        }
    }

    private FakeLibraryDAO board;
    private FakePostDAO posts;
    private BoardLibraryService service;

    /** 시험마다 빈 가짜 DB로 Service를 새로 만든다 */
    @BeforeEach
    void setUp() {
        board = new FakeLibraryDAO();
        posts = new FakePostDAO();
        AttachmentService files = new FakeAttachmentService();
        service = new BoardLibraryService(board, posts, new NoticeService(posts, files), files);
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

    /** 화면이 보내는 첨부 목록 (파일 번호만) */
    private static List<NoticeFileDTO> filesOf(Long... ids) {
        List<NoticeFileDTO> list = new ArrayList<>();
        for (Long id : ids) {
            NoticeFileDTO f = new NoticeFileDTO();
            f.setId(id);
            list.add(f);
        }
        return list;
    }

    @Test
    @DisplayName("같은 업체 직원은 자료를 보고, 올린 사람만 내 글로 표시되며 첨부 주소는 자료실 주소다")
    void coworkerSeesPost() {
        BoardLibraryPostDTO byWriter = service.getPost(emp(WRITER), POST_ID);
        BoardLibraryPostDTO byCoworker = service.getPost(emp(COWORKER), POST_ID);

        assertTrue(byWriter.isMine());
        assertFalse(byCoworker.isMine());
        assertEquals("김이음", byCoworker.getWriterName());
        assertEquals("/board/api/library/posts/200/files/66", byCoworker.getFiles().get(0).getUrl());
    }

    @Test
    @DisplayName("파일 없이 저장·수정하면 400이고 아무것도 저장하지 않는다")
    void fileIsRequired() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createPost(emp(WRITER), "제목", "내용", null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createPost(emp(WRITER), "제목", "내용", new ArrayList<>()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.updatePost(emp(WRITER), POST_ID, "제목", "내용", filesOf()));
        assertTrue(board.writes.isEmpty());
        assertTrue(posts.writes.isEmpty());
    }

    @Test
    @DisplayName("업체의 첫 자료면 자료실 줄을 만들고, 올린 파일을 붙이며, 작성자는 로그인한 사람이다")
    void firstPostCreatesBoardAndAttaches() {
        Map<String, Object> result = service.createPost(emp(WRITER), " 양식 ", "내용", filesOf(NEW_FILE_ID));

        assertEquals(NEW_POST_ID, result.get("id"));
        assertEquals(1, board.boardInserts);
        assertEquals(50L, board.savedBoard);
        assertEquals(WRITER, board.savedAuthor);
        assertEquals(List.of("attach:" + NEW_FILE_ID), posts.writes);
    }

    @Test
    @DisplayName("남이 올린 파일 번호를 붙이려 하면 400이다 (내가 올린 파일만 붙는다)")
    void cannotAttachOthersFile() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createPost(emp(COWORKER), "제목", "내용", filesOf(NEW_FILE_ID)));
    }

    @Test
    @DisplayName("다른 업체 사람이 글 번호로 보기·수정·삭제·내려받기를 하면 404로 거절한다")
    void otherCompanyIsRejected() {
        LoginUser other = emp(OTHER_CO_EMP);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.getPost(other, POST_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getForm(other, POST_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updatePost(other, POST_ID, "제목", "내용", filesOf(FILE_ID)));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deletePost(other, POST_ID));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getFile(other, POST_ID, FILE_ID));
        assertTrue(board.writes.isEmpty());
        assertTrue(posts.writes.isEmpty());
    }

    @Test
    @DisplayName("올린 사람이 아니면 업체 관리자여도 수정 화면·수정·삭제가 403이다")
    void onlyWriterCanChange() {
        LoginUser coworker = emp(COWORKER);
        LoginUser admin = user(COWORKER, "ROLE_ADMIN");

        assertStatus(HttpStatus.FORBIDDEN, () -> service.getForm(coworker, POST_ID));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updatePost(admin, POST_ID, "제목", "내용", filesOf(FILE_ID)));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.deletePost(admin, POST_ID));
        assertTrue(board.writes.isEmpty());
    }

    @Test
    @DisplayName("올린 사람은 파일을 바꿔 수정하고 삭제할 수 있다")
    void writerCanChange() {
        service.updatePost(emp(WRITER), POST_ID, "새 제목", "새 내용", filesOf(NEW_FILE_ID));
        service.deletePost(emp(WRITER), POST_ID);

        assertEquals(List.of("update", "delete"), board.writes);
        assertEquals(List.of("detach:" + FILE_ID, "attach:" + NEW_FILE_ID), posts.writes);
    }

    @Test
    @DisplayName("글에 붙지 않은 파일 번호로 내려받으면 404다")
    void fileMustBelongToPost() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getFile(emp(COWORKER), POST_ID, NEW_FILE_ID));
        assertEquals(FILE_ID, service.getFile(emp(COWORKER), POST_ID, FILE_ID).getAttachmentId());
    }

    @Test
    @DisplayName("파일 올리기는 부서·역할을 따지지 않고 파일 검사까지 간다 (빈 요청은 400)")
    void anyoneCanUpload() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.uploadFiles(emp(COWORKER), null));
    }
}
