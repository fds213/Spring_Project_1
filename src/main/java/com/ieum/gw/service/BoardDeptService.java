package com.ieum.gw.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dao.BoardDeptDAO;
import com.ieum.gw.dao.NoticeDAO;
import com.ieum.gw.dto.AttachmentDTO;
import com.ieum.gw.dto.BoardDeptPostDTO;
import com.ieum.gw.dto.IdNameDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.NoticeCommentDTO;
import com.ieum.gw.dto.NoticeFileDTO;

import lombok.RequiredArgsConstructor;

// 부서 게시판 규칙을 맡는 Service (계획서 '게시판': 부서 글은 같은 부서원만 조회).
// 보기·글쓰기·댓글은 같은 부서 사람 누구나, 글 수정·삭제와 댓글 삭제는 쓴 사람 본인만 한다 (업체 관리자도 같다).
// 부서는 요청마다 DB에서 읽는다 (부서를 옮기면 바로 새 부서 글이 보이게). 화면이 보낸 값으로 업체·부서를 정하지 않는다
@Service
@RequiredArgsConstructor
public class BoardDeptService {

    private static final int PAGE_SIZE = 10; // 한 페이지에 10개
    // 없는 글과 다른 업체·다른 부서·지운 글은 같은 말로 답한다 (그런 글이 있는지조차 드러내지 않게)
    private static final String NOT_FOUND = "없는 글이거나 볼 수 없는 글입니다";
    private static final String NO_DEPT = "소속 부서가 없어 부서 게시판을 쓸 수 없습니다";

    private final BoardDeptDAO dao;
    private final NoticeDAO postDao;               // 댓글·첨부 SQL은 글 번호로 찾아서 공지와 같이 쓴다
    private final NoticeService noticeService;     // 제목·내용·댓글 검사와 첨부 규칙은 공지와 같다
    private final AttachmentService attachmentService; // 공통 파일 저장 (담당 1)

    /** 목록 화면에 보낼 값을 만든다 (board.js renderBoardList가 읽는 모양) */
    public Map<String, Object> getPosts(LoginUser user, String q, int page) {
        IdNameDTO dept = myDept(user);
        Long companyId = user.getCompanyId();
        List<String> words = noticeService.splitWords(q);

        // 1. 전체 개수와 페이지 수
        int total = dao.countPosts(companyId, dept.getId(), words);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(Math.max(1, page), pages); // 1 ~ 마지막 페이지 사이로 맞춘다
        int offset = (page - 1) * PAGE_SIZE;

        // 2. 이번 페이지 글 + 목록 번호 (최신 글이 가장 큰 번호)
        List<BoardDeptPostDTO> items = dao.selectPosts(companyId, dept.getId(), words, offset, PAGE_SIZE);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setNo(total - offset - i);
        }

        // 3. 화면에 보낼 모양
        Map<String, Object> result = new HashMap<>();
        result.put("page", page);
        result.put("pages", pages);
        result.put("total", total);
        result.put("items", items);
        result.put("dept", dept.getName());
        return result;
    }

    /** 글 한 건 (board.js renderBoardDetail이 읽는 모양). 같은 부서 사람만 본다 */
    public BoardDeptPostDTO getPost(LoginUser user, Long id) {
        BoardDeptPostDTO post = findPost(user, myDept(user), id);
        post.setMine(user.getEmployeeId().equals(post.getAuthorId()));
        post.setComments(listComments(user, id));
        post.setFiles(listFiles(user, id));
        return post;
    }

    /** 글쓰기·수정 화면을 열 때 보낼 값 (board.js fillBoardForm이 읽는 모양). id가 있으면 본인 글만 연다 */
    public Map<String, Object> getForm(LoginUser user, Long id) {
        IdNameDTO dept = myDept(user);

        BoardDeptPostDTO post = null;
        if (id != null) {
            post = findOwnPost(user, dept, id, "본인이 쓴 글만 수정할 수 있습니다");
            post.setFiles(listFiles(user, id));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("post", post);
        result.put("dept", dept.getName());
        return result;
    }

    /** 글 저장. 부서는 로그인한 사람의 부서로 정한다. 성공하면 새 글 번호를 돌려준다 */
    @Transactional
    public Map<String, Object> createPost(LoginUser user, String title, String content, List<NoticeFileDTO> files) {
        IdNameDTO dept = myDept(user);

        // 1. 값 검사 (주소로 직접 보내는 요청도 있으니 서버가 다시 본다)
        title = (title == null) ? "" : title.trim();
        content = (content == null) ? "" : content;
        noticeService.validate(title, content);

        // 2. 우리 부서 게시판 번호 (그 부서의 첫 글이면 같은 트랜잭션에서 줄을 만든다)
        Long companyId = user.getCompanyId();
        Long boardId = dao.selectBoardId(companyId, dept.getId());
        if (boardId == null) {
            dao.insertBoard(companyId, dept.getId());
            boardId = dao.selectBoardId(companyId, dept.getId());
        }

        // 3. 번호를 먼저 뽑고 저장한 뒤 첨부파일을 붙인다
        Long postId = postDao.selectNextPostId();
        dao.insertPost(postId, companyId, boardId, user.getEmployeeId(), title, content);
        noticeService.syncFiles(user, postId, files);

        Map<String, Object> result = new HashMap<>();
        result.put("id", postId);
        return result;
    }

    /** 글 수정 (본인 글만). 성공하면 글 번호를 돌려준다 */
    @Transactional
    public Map<String, Object> updatePost(LoginUser user, Long id, String title, String content, List<NoticeFileDTO> files) {
        IdNameDTO dept = myDept(user);
        findOwnPost(user, dept, id, "본인이 쓴 글만 수정할 수 있습니다");

        title = (title == null) ? "" : title.trim();
        content = (content == null) ? "" : content;
        noticeService.validate(title, content);

        // 고친 줄이 0이면 그사이 지워진 글이다
        int changed = dao.updatePost(user.getCompanyId(), dept.getId(), id, user.getEmployeeId(), title, content);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        noticeService.syncFiles(user, id, files);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    /** 글 삭제 (본인 글만, 진짜로 지우지 않고 삭제 표시). 댓글도 같이 삭제 표시한다 */
    @Transactional
    public Map<String, Object> deletePost(LoginUser user, Long id) {
        IdNameDTO dept = myDept(user);
        findOwnPost(user, dept, id, "본인이 쓴 글만 삭제할 수 있습니다");

        int changed = dao.deletePost(user.getCompanyId(), dept.getId(), id, user.getEmployeeId());
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        postDao.deleteCommentsOfPost(user.getCompanyId(), id);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    /** 댓글 등록: 같은 부서 사람 누구나. 그 글의 댓글 목록 전체를 돌려준다 */
    @Transactional
    public List<NoticeCommentDTO> addComment(LoginUser user, Long postId, String text) {
        findPost(user, myDept(user), postId);

        postDao.insertComment(user.getCompanyId(), postId, user.getEmployeeId(), noticeService.checkComment(text));
        return listComments(user, postId);
    }

    /** 댓글 삭제: 본인 댓글만. 남은 댓글 목록 전체를 돌려준다 */
    @Transactional
    public List<NoticeCommentDTO> deleteComment(LoginUser user, Long postId, Long commentId) {
        findPost(user, myDept(user), postId);

        Long authorId = postDao.selectCommentAuthor(user.getCompanyId(), postId, commentId);
        if (authorId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 댓글이거나 이미 삭제된 댓글입니다");
        }
        if (!authorId.equals(user.getEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다");
        }

        postDao.deleteComment(user.getCompanyId(), postId, commentId);
        return listComments(user, postId);
    }

    /** 파일 올리기 (/board/api/files, 공지와 같이 쓰는 주소): 공지를 쓸 수 있는 사람이거나 부서가 있는 사람만 */
    public List<NoticeFileDTO> uploadFiles(LoginUser user, List<MultipartFile> files) throws IOException {
        if (!noticeService.isManager(user)) {
            myDept(user); // 부서가 없으면 여기서 403으로 멈춘다
        }
        return noticeService.storeFiles(user, files);
    }

    /** 내려받기 전 확인: 그 글을 볼 수 있고, 그 파일이 정말 그 글에 붙은 파일인지 */
    public AttachmentDTO getFile(LoginUser user, Long postId, Long fileId) {
        findPost(user, myDept(user), postId);
        if (!postDao.selectFileIds(user.getCompanyId(), postId).contains(fileId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 파일입니다");
        }
        return attachmentService.get(fileId, user.getCompanyId());
    }

    /** 로그인한 사람의 지금 부서. 부서가 없으면 403 */
    private IdNameDTO myDept(LoginUser user) {
        IdNameDTO dept = dao.selectMyDept(user.getCompanyId(), user.getEmployeeId());
        if (dept == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NO_DEPT);
        }
        return dept;
    }

    /** 우리 부서의 글 한 건. 없거나 다른 업체·다른 부서·지운 글이면 404 */
    private BoardDeptPostDTO findPost(LoginUser user, IdNameDTO dept, Long id) {
        BoardDeptPostDTO post = dao.selectPost(user.getCompanyId(), dept.getId(), id);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return post;
    }

    /** 우리 부서의 내 글 한 건. 남의 글이면 403 */
    private BoardDeptPostDTO findOwnPost(LoginUser user, IdNameDTO dept, Long id, String forbidden) {
        BoardDeptPostDTO post = findPost(user, dept, id);
        if (!user.getEmployeeId().equals(post.getAuthorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbidden);
        }
        return post;
    }

    /** 댓글 목록 + 각 댓글의 삭제 버튼 여부 (본인 댓글만) */
    private List<NoticeCommentDTO> listComments(LoginUser user, Long postId) {
        List<NoticeCommentDTO> comments = postDao.selectComments(user.getCompanyId(), postId);
        for (NoticeCommentDTO c : comments) {
            c.setCanDelete(c.getAuthorId().equals(user.getEmployeeId()));
        }
        return comments;
    }

    /** 첨부 목록 + 각 파일의 내려받기 주소 */
    private List<NoticeFileDTO> listFiles(LoginUser user, Long postId) {
        List<NoticeFileDTO> files = postDao.selectFiles(user.getCompanyId(), postId);
        for (NoticeFileDTO f : files) {
            f.setUrl("/board/api/dept/posts/" + postId + "/files/" + f.getId());
        }
        return files;
    }
}
