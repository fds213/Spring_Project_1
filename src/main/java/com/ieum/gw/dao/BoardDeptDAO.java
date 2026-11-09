package com.ieum.gw.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.BoardDeptPostDTO;
import com.ieum.gw.dto.IdNameDTO;
import com.ieum.gw.mapper.BoardDeptMapper;

import lombok.RequiredArgsConstructor;

// 부서 게시판 DAO: BoardDeptMapper를 감싸서 Service에 건네준다
@Repository
@RequiredArgsConstructor
public class BoardDeptDAO {

    private final BoardDeptMapper mapper;

    /** 로그인한 사람의 지금 부서 (없으면 null) */
    public IdNameDTO selectMyDept(Long companyId, Long employeeId) {
        return mapper.selectMyDept(companyId, employeeId);
    }

    /** 우리 부서 글 목록 (한 페이지) */
    public List<BoardDeptPostDTO> selectPosts(Long companyId, Long departmentId, List<String> words, int offset, int size) {
        return mapper.selectPosts(companyId, departmentId, words, offset, size);
    }

    /** 우리 부서 글 전체 개수 */
    public int countPosts(Long companyId, Long departmentId, List<String> words) {
        return mapper.countPosts(companyId, departmentId, words);
    }

    /** 우리 부서 글 한 건 (없으면 null) */
    public BoardDeptPostDTO selectPost(Long companyId, Long departmentId, Long postId) {
        return mapper.selectPost(companyId, departmentId, postId);
    }

    /** 글 줄 잠그기 (지운 글·다른 부서 글이면 null) */
    public Long lockPost(Long companyId, Long departmentId, Long postId) {
        return mapper.lockPost(companyId, departmentId, postId);
    }

    /** 우리 부서 게시판 번호 (없으면 null) */
    public Long selectBoardId(Long companyId, Long departmentId) {
        return mapper.selectBoardId(companyId, departmentId);
    }

    /** 우리 부서 게시판 줄 만들기 */
    public int insertBoard(Long companyId, Long departmentId) {
        return mapper.insertBoard(companyId, departmentId);
    }

    /** 글 저장 (성공하면 1) */
    public int insertPost(Long postId, Long companyId, Long boardId, Long authorId, String title, String content) {
        return mapper.insertPost(postId, companyId, boardId, authorId, title, content);
    }

    /** 글 고치기 (성공하면 1) */
    public int updatePost(Long companyId, Long departmentId, Long postId, Long authorId, String title, String content) {
        return mapper.updatePost(companyId, departmentId, postId, authorId, title, content);
    }

    /** 글 지우기 (성공하면 1) */
    public int deletePost(Long companyId, Long departmentId, Long postId, Long authorId) {
        return mapper.deletePost(companyId, departmentId, postId, authorId);
    }
}
