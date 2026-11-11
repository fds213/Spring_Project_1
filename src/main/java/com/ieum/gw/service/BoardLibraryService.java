package com.ieum.gw.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dao.BoardLibraryDAO;
import com.ieum.gw.dao.NoticeDAO;
import com.ieum.gw.dto.AttachmentDTO;
import com.ieum.gw.dto.BoardLibraryPostDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.NoticeFileDTO;

import lombok.RequiredArgsConstructor;

// 자료실 규칙을 맡는 Service (요구분석서 3장 '추가 구현 범위': 업체 직원이 문서 파일을 올리고 내려받는 곳).
// - 글에 파일을 붙이는 방식이다. 첨부 규칙(5개·파일당 10MB·허용 형식)은 공지·부서 게시판과 같고, 자료실 글은 파일이 1개 이상 있어야 한다.
// - 보기·내려받기·올리기는 같은 업체 직원 누구나. 글 수정·삭제는 올린 사람 본인만 한다 (업체 관리자도 같다, 부서 게시판과 같은 규칙).
// - 댓글은 두지 않는다 (요구사항은 올리고 내려받는 곳까지다).
// 업체는 로그인한 사람 정보에서만 꺼낸다. 화면이 보낸 값으로 업체·작성자를 정하지 않는다 (AGENTS.md)
@Service
@RequiredArgsConstructor
public class BoardLibraryService {

    private static final int PAGE_SIZE = 10; // 한 페이지에 10개
    // 없는 글과 다른 업체·다른 게시판·지운 글은 같은 말로 답한다 (그런 글이 있는지조차 드러내지 않게)
    private static final String NOT_FOUND = "없는 자료이거나 볼 수 없는 자료입니다";
    private static final String NO_FILE = "자료실 글에는 파일을 1개 이상 붙여 주세요";

    private final BoardLibraryDAO dao;
    private final NoticeDAO postDao;                   // 글 번호 뽑기와 첨부(POST_FILE) SQL은 공지와 같이 쓴다
    private final NoticeService noticeService;         // 제목·내용 검사와 첨부 규칙은 공지와 같다
    private final AttachmentService attachmentService; // 공통 파일 저장 (담당 1)

    /** 목록 화면에 보낼 값을 만든다 (board.js 자료실 목록이 읽는 모양) */
    public Map<String, Object> getPosts(LoginUser user, String q, int page) {
        Long companyId = user.getCompanyId();
        List<String> words = noticeService.splitWords(q);

        // 1. 전체 개수와 페이지 수
        int total = dao.countPosts(companyId, words);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(Math.max(1, page), pages); // 1 ~ 마지막 페이지 사이로 맞춘다
        int offset = (page - 1) * PAGE_SIZE;

        // 2. 이번 페이지 글 + 목록 번호 (최신 글이 가장 큰 번호)
        List<BoardLibraryPostDTO> items = dao.selectPosts(companyId, words, offset, PAGE_SIZE);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setNo(total - offset - i);
        }

        // 3. 화면에 보낼 모양
        Map<String, Object> result = new HashMap<>();
        result.put("page", page);
        result.put("pages", pages);
        result.put("total", total);
        result.put("items", items);
        return result;
    }

    /** 글 한 건 + 첨부 목록. 우리 업체 직원이면 누구나 본다 */
    public BoardLibraryPostDTO getPost(LoginUser user, Long id) {
        BoardLibraryPostDTO post = findPost(user, id);
        post.setMine(user.getEmployeeId().equals(post.getAuthorId()));
        post.setFiles(listFiles(user, id));
        return post;
    }

    /** 글쓰기·수정 화면을 열 때 보낼 값. id가 있으면 본인 글만 연다 */
    public Map<String, Object> getForm(LoginUser user, Long id) {
        BoardLibraryPostDTO post = null;
        if (id != null) {
            post = findOwnPost(user, id, "본인이 올린 자료만 수정할 수 있습니다");
            post.setFiles(listFiles(user, id));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("post", post);
        return result;
    }

    /** 글 저장. 먼저 올려 둔 파일을 붙인다. 성공하면 새 글 번호를 돌려준다 */
    @Transactional
    public Map<String, Object> createPost(LoginUser user, String title, String content, List<NoticeFileDTO> files) {
        // 1. 값 검사 (주소로 직접 보내는 요청도 있으니 서버가 다시 본다)
        title = (title == null) ? "" : title.trim();
        content = (content == null) ? "" : content;
        noticeService.validate(title, content);
        requireFile(files);

        // 2. 우리 업체 자료실 번호 (첫 글이면 같은 트랜잭션에서 줄을 만든다)
        Long companyId = user.getCompanyId();
        Long boardId = dao.selectBoardId(companyId);
        if (boardId == null) {
            dao.insertBoard(companyId);
            boardId = dao.selectBoardId(companyId);
        }

        // 3. 번호를 먼저 뽑고 저장한 뒤 첨부파일을 붙인다 (내가 올린 우리 업체 파일만 붙는다)
        Long postId = postDao.selectNextPostId();
        dao.insertPost(postId, companyId, boardId, user.getEmployeeId(), title, content);
        noticeService.syncFiles(user, postId, files);

        Map<String, Object> result = new HashMap<>();
        result.put("id", postId);
        return result;
    }

    /** 글 수정 (본인 글만). 첨부도 화면이 보낸 목록에 맞춘다. 성공하면 글 번호를 돌려준다 */
    @Transactional
    public Map<String, Object> updatePost(LoginUser user, Long id, String title, String content, List<NoticeFileDTO> files) {
        findOwnPost(user, id, "본인이 올린 자료만 수정할 수 있습니다");

        title = (title == null) ? "" : title.trim();
        content = (content == null) ? "" : content;
        noticeService.validate(title, content);
        requireFile(files);

        // 고친 줄이 0이면 그사이 지워진 글이다
        int changed = dao.updatePost(user.getCompanyId(), id, user.getEmployeeId(), title, content);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        noticeService.syncFiles(user, id, files);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    /** 글 삭제 (본인 글만, 진짜로 지우지 않고 삭제 표시). 지운 글의 파일은 글을 볼 수 없으니 내려받을 수도 없다 */
    @Transactional
    public Map<String, Object> deletePost(LoginUser user, Long id) {
        findOwnPost(user, id, "본인이 올린 자료만 삭제할 수 있습니다");

        int changed = dao.deletePost(user.getCompanyId(), id, user.getEmployeeId());
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    /** 파일 올리기 (/board/api/library/files): 우리 업체 직원 누구나. 저장·형식·개수 검사는 공지와 같다 */
    public List<NoticeFileDTO> uploadFiles(LoginUser user, List<MultipartFile> files) throws IOException {
        return noticeService.storeFiles(user, files);
    }

    /** 내려받기 전 확인: 그 자료를 볼 수 있고, 그 파일이 정말 그 글에 붙은 파일인지 */
    public AttachmentDTO getFile(LoginUser user, Long postId, Long fileId) {
        findPost(user, postId);
        if (!postDao.selectFileIds(user.getCompanyId(), postId).contains(fileId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 파일입니다");
        }
        return attachmentService.get(fileId, user.getCompanyId());
    }

    /** 붙일 파일 번호가 하나도 없으면 400 (자료실 글은 파일이 있어야 한다) */
    private void requireFile(List<NoticeFileDTO> files) {
        boolean any = files != null && files.stream().filter(Objects::nonNull).anyMatch(f -> f.getId() != null);
        if (!any) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_FILE);
        }
    }

    /** 우리 업체 자료실 글 한 건. 없거나 다른 업체·다른 게시판·지운 글이면 404 */
    private BoardLibraryPostDTO findPost(LoginUser user, Long id) {
        BoardLibraryPostDTO post = (id == null) ? null : dao.selectPost(user.getCompanyId(), id);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return post;
    }

    /** 우리 업체 자료실의 내 글 한 건. 남의 글이면 403 */
    private BoardLibraryPostDTO findOwnPost(LoginUser user, Long id, String forbidden) {
        BoardLibraryPostDTO post = findPost(user, id);
        if (!user.getEmployeeId().equals(post.getAuthorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbidden);
        }
        return post;
    }

    /** 첨부 목록 + 각 파일의 내려받기 주소 */
    private List<NoticeFileDTO> listFiles(LoginUser user, Long postId) {
        List<NoticeFileDTO> files = postDao.selectFiles(user.getCompanyId(), postId);
        for (NoticeFileDTO f : files) {
            f.setUrl("/board/api/library/posts/" + postId + "/files/" + f.getId());
        }
        return files;
    }
}
