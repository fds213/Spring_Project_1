package com.ieum.gw.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.BoardAnonCommentDTO;
import com.ieum.gw.dto.BoardAnonPostDTO;
import com.ieum.gw.mapper.BoardAnonMapper;

import lombok.RequiredArgsConstructor;

// 익명 게시판 DAO: BoardAnonMapper를 감싸서 Service에 건네준다
@Repository
@RequiredArgsConstructor
public class BoardAnonDAO {

    private final BoardAnonMapper mapper;

    /** 우리 업체 익명 게시판 글 목록 (한 페이지) */
    public List<BoardAnonPostDTO> selectPosts(Long companyId, List<String> words, int offset, int size) {
        return mapper.selectPosts(companyId, words, offset, size);
    }

    /** 우리 업체 익명 게시판 글 전체 개수 */
    public int countPosts(Long companyId, List<String> words) {
        return mapper.countPosts(companyId, words);
    }

    /** 익명 게시판 글 한 건 (없으면 null) */
    public BoardAnonPostDTO selectPost(Long companyId, Long postId) {
        return mapper.selectPost(companyId, postId);
    }

    /** 글 줄 잠그기 (지운 글·다른 업체 글이면 null) */
    public Long lockPost(Long companyId, Long postId) {
        return mapper.lockPost(companyId, postId);
    }

    /** 우리 업체 익명 게시판 번호 (없으면 null) */
    public Long selectBoardId(Long companyId) {
        return mapper.selectBoardId(companyId);
    }

    /** 우리 업체 익명 게시판 줄 만들기 */
    public int insertBoard(Long companyId) {
        return mapper.insertBoard(companyId);
    }

    /** 글 저장 (성공하면 1) */
    public int insertPost(Long postId, Long companyId, Long boardId, Long authorId, String title, String content) {
        return mapper.insertPost(postId, companyId, boardId, authorId, title, content);
    }

    /** 글 고치기 (성공하면 1) */
    public int updatePost(Long companyId, Long postId, Long authorId, String title, String content) {
        return mapper.updatePost(companyId, postId, authorId, title, content);
    }

    /** 글 지우기 (성공하면 1) */
    public int deletePost(Long companyId, Long postId, Long authorId) {
        return mapper.deletePost(companyId, postId, authorId);
    }

    /** 댓글 목록 (쓴 사람 이름 없이) */
    public List<BoardAnonCommentDTO> selectComments(Long companyId, Long postId) {
        return mapper.selectComments(companyId, postId);
    }
}
