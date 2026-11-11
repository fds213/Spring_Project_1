package com.ieum.gw.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dao.BoardAnonDAO;
import com.ieum.gw.dao.NoticeDAO;
import com.ieum.gw.dto.BoardAnonCommentDTO;
import com.ieum.gw.dto.BoardAnonPostDTO;
import com.ieum.gw.dto.LoginUser;

import lombok.RequiredArgsConstructor;

// 익명 게시판 규칙을 맡는 Service (요구분석서 3장 '추가 구현 범위': 작성자 이름을 보이지 않는 업체 게시판).
// - 보기·글쓰기·댓글은 같은 업체 직원 누구나. 글 수정·삭제와 댓글 삭제는 쓴 사람 본인만 한다 (업체 관리자도 같다).
// - 쓴 사람은 본인 확인을 위해 DB에 남기지만, 응답에는 이름·사번·부서·사원 ID를 넣지 않는다. 업체 관리자에게도 같다.
//   본인 글·본인 댓글인지(mine·canDelete)만 그 사람에게 알려 준다. 댓글도 익명이다.
// - 첨부파일은 받지 않는다: 파일 이름이나 사진 속 정보(찍은 기기·위치)로 쓴 사람이 드러날 수 있어서다.
// 업체는 로그인한 사람 정보에서만 꺼낸다. 화면이 보낸 값으로 업체·작성자를 정하지 않는다 (AGENTS.md)
@Service
@RequiredArgsConstructor
public class BoardAnonService {

    private static final int PAGE_SIZE = 10; // 한 페이지에 10개
    // 없는 글과 다른 업체·다른 게시판·지운 글은 같은 말로 답한다 (그런 글이 있는지조차 드러내지 않게)
    private static final String NOT_FOUND = "없는 글이거나 볼 수 없는 글입니다";

    private final BoardAnonDAO dao;
    private final NoticeDAO postDao;           // 댓글 저장·삭제와 글 번호 뽑기는 글 번호로 찾아서 공지와 같이 쓴다
    private final NoticeService noticeService; // 제목·내용·댓글 검사와 검색어 나누기는 공지와 같다

    /** 목록 화면에 보낼 값을 만든다 (board.js 익명 게시판 목록이 읽는 모양) */
    public Map<String, Object> getPosts(LoginUser user, String q, int page) {
        Long companyId = user.getCompanyId();
        List<String> words = noticeService.splitWords(q);

        // 1. 전체 개수와 페이지 수
        int total = dao.countPosts(companyId, words);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(Math.max(1, page), pages); // 1 ~ 마지막 페이지 사이로 맞춘다
        int offset = (page - 1) * PAGE_SIZE;

        // 2. 이번 페이지 글 + 목록 번호 (최신 글이 가장 큰 번호)
        List<BoardAnonPostDTO> items = dao.selectPosts(companyId, words, offset, PAGE_SIZE);
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

    /** 글 한 건 + 댓글. 우리 업체 직원이면 누구나 본다 */
    public BoardAnonPostDTO getPost(LoginUser user, Long id) {
        BoardAnonPostDTO post = findPost(user, id);
        post.setMine(user.getEmployeeId().equals(post.getAuthorId()));
        post.setComments(listComments(user, id));
        return post;
    }

    /** 글쓰기·수정 화면을 열 때 보낼 값. id가 있으면 본인 글만 연다 */
    public Map<String, Object> getForm(LoginUser user, Long id) {
        BoardAnonPostDTO post = null;
        if (id != null) {
            post = findOwnPost(user, id, "본인이 쓴 글만 수정할 수 있습니다");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("post", post);
        return result;
    }

    /** 글 저장. 쓴 사람은 로그인한 사람으로 남긴다. 성공하면 새 글 번호를 돌려준다 */
    @Transactional
    public Map<String, Object> createPost(LoginUser user, String title, String content) {
        // 1. 값 검사 (주소로 직접 보내는 요청도 있으니 서버가 다시 본다)
        title = (title == null) ? "" : title.trim();
        content = (content == null) ? "" : content;
        noticeService.validate(title, content);

        // 2. 우리 업체 익명 게시판 번호 (첫 글이면 같은 트랜잭션에서 줄을 만든다)
        Long companyId = user.getCompanyId();
        Long boardId = dao.selectBoardId(companyId);
        if (boardId == null) {
            dao.insertBoard(companyId);
            boardId = dao.selectBoardId(companyId);
        }

        // 3. 번호를 먼저 뽑고 저장
        Long postId = postDao.selectNextPostId();
        dao.insertPost(postId, companyId, boardId, user.getEmployeeId(), title, content);

        Map<String, Object> result = new HashMap<>();
        result.put("id", postId);
        return result;
    }

    /** 글 수정 (본인 글만). 성공하면 글 번호를 돌려준다 */
    @Transactional
    public Map<String, Object> updatePost(LoginUser user, Long id, String title, String content) {
        findOwnPost(user, id, "본인이 쓴 글만 수정할 수 있습니다");

        title = (title == null) ? "" : title.trim();
        content = (content == null) ? "" : content;
        noticeService.validate(title, content);

        // 고친 줄이 0이면 그사이 지워진 글이다
        int changed = dao.updatePost(user.getCompanyId(), id, user.getEmployeeId(), title, content);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    /** 글 삭제 (본인 글만, 진짜로 지우지 않고 삭제 표시). 댓글도 같이 삭제 표시한다 */
    @Transactional
    public Map<String, Object> deletePost(LoginUser user, Long id) {
        findOwnPost(user, id, "본인이 쓴 글만 삭제할 수 있습니다");

        int changed = dao.deletePost(user.getCompanyId(), id, user.getEmployeeId());
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        postDao.deleteCommentsOfPost(user.getCompanyId(), id);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    /** 댓글 등록: 같은 업체 직원 누구나. 그 글의 댓글 목록 전체를 돌려준다 */
    @Transactional
    public List<BoardAnonCommentDTO> addComment(LoginUser user, Long postId, String text) {
        // 글 줄을 잠근 뒤 저장한다. 글 삭제와 겹치면 삭제가 먼저일 때는 여기서 404, 댓글이 먼저일 때는 삭제가 이 댓글까지 삭제 표시한다
        if (postId == null || dao.lockPost(user.getCompanyId(), postId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }

        postDao.insertComment(user.getCompanyId(), postId, user.getEmployeeId(), noticeService.checkComment(text));
        return listComments(user, postId);
    }

    /** 댓글 삭제: 본인 댓글만 (업체 관리자도 남의 댓글은 못 지운다). 남은 댓글 목록 전체를 돌려준다 */
    @Transactional
    public List<BoardAnonCommentDTO> deleteComment(LoginUser user, Long postId, Long commentId) {
        findPost(user, postId);

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

    /** 우리 업체 익명 게시판 글 한 건. 없거나 다른 업체·다른 게시판·지운 글이면 404 */
    private BoardAnonPostDTO findPost(LoginUser user, Long id) {
        BoardAnonPostDTO post = (id == null) ? null : dao.selectPost(user.getCompanyId(), id);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return post;
    }

    /** 우리 업체 익명 게시판의 내 글 한 건. 남의 글이면 403 */
    private BoardAnonPostDTO findOwnPost(LoginUser user, Long id, String forbidden) {
        BoardAnonPostDTO post = findPost(user, id);
        if (!user.getEmployeeId().equals(post.getAuthorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbidden);
        }
        return post;
    }

    /** 댓글 목록 + 각 댓글의 삭제 버튼 여부 (본인 댓글만) */
    private List<BoardAnonCommentDTO> listComments(LoginUser user, Long postId) {
        List<BoardAnonCommentDTO> comments = dao.selectComments(user.getCompanyId(), postId);
        for (BoardAnonCommentDTO c : comments) {
            c.setCanDelete(user.getEmployeeId().equals(c.getAuthorId()));
        }
        return comments;
    }
}
