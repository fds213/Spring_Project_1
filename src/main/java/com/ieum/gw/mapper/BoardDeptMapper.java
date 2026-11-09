package com.ieum.gw.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.BoardDeptPostDTO;
import com.ieum.gw.dto.IdNameDTO;

// 부서 게시판 SQL을 부르는 창구. 메서드 이름 = BoardDeptMapper.xml 의 id
// 댓글·첨부 SQL은 글 번호로 찾아서 공지와 같은 NoticeMapper를 쓴다
@Mapper
public interface BoardDeptMapper {

    /** 로그인한 사람의 지금 부서 번호·이름 (부서가 없거나 사용 중이 아니면 null) */
    IdNameDTO selectMyDept(@Param("companyId") Long companyId,
                           @Param("employeeId") Long employeeId);

    /** 우리 부서 글 목록 (한 페이지 분량) */
    List<BoardDeptPostDTO> selectPosts(@Param("companyId") Long companyId,
                                       @Param("departmentId") Long departmentId,
                                       @Param("words") List<String> words,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /** 우리 부서 글 전체 개수 (페이지 수 계산용) */
    int countPosts(@Param("companyId") Long companyId,
                   @Param("departmentId") Long departmentId,
                   @Param("words") List<String> words);

    /** 우리 부서 글 한 건 (지운 글·다른 부서 글이면 null) */
    BoardDeptPostDTO selectPost(@Param("companyId") Long companyId,
                                @Param("departmentId") Long departmentId,
                                @Param("postId") Long postId);

    /** 우리 부서 게시판 번호 (BOARD 표에 줄이 없으면 null) */
    Long selectBoardId(@Param("companyId") Long companyId,
                       @Param("departmentId") Long departmentId);

    /** 우리 부서 게시판 줄 만들기 (그 부서의 첫 글을 쓸 때) */
    int insertBoard(@Param("companyId") Long companyId,
                    @Param("departmentId") Long departmentId);

    /** 글 저장 (성공하면 1) */
    int insertPost(@Param("postId") Long postId,
                   @Param("companyId") Long companyId,
                   @Param("boardId") Long boardId,
                   @Param("authorId") Long authorId,
                   @Param("title") String title,
                   @Param("content") String content);

    /** 글 제목·내용 고치기 (성공하면 1, 없는 글·남의 글이면 0) */
    int updatePost(@Param("companyId") Long companyId,
                   @Param("departmentId") Long departmentId,
                   @Param("postId") Long postId,
                   @Param("authorId") Long authorId,
                   @Param("title") String title,
                   @Param("content") String content);

    /** 글 지우기: 삭제 표시만 한다 (성공하면 1, 없는 글·남의 글이면 0) */
    int deletePost(@Param("companyId") Long companyId,
                   @Param("departmentId") Long departmentId,
                   @Param("postId") Long postId,
                   @Param("authorId") Long authorId);
}
