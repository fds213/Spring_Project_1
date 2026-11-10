package com.ieum.gw.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.BoardAnonCommentDTO;
import com.ieum.gw.dto.BoardAnonPostDTO;

// 익명 게시판 SQL을 부르는 창구. 메서드 이름 = BoardAnonMapper.xml 의 id
// 쓴 사람 이름이 나가지 않게 글·댓글 조회는 사원 표(EMPLOYEE)를 읽지 않는다.
// 댓글 저장·삭제는 글 번호로 찾아서 공지와 같은 NoticeMapper를 쓴다 (부서 게시판과 같은 방식)
@Mapper
public interface BoardAnonMapper {

    /** 우리 업체 익명 게시판 글 목록 (한 페이지 분량) */
    List<BoardAnonPostDTO> selectPosts(@Param("companyId") Long companyId,
                                       @Param("words") List<String> words,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /** 우리 업체 익명 게시판 글 전체 개수 (페이지 수 계산용) */
    int countPosts(@Param("companyId") Long companyId,
                   @Param("words") List<String> words);

    /** 익명 게시판 글 한 건 (지운 글·다른 업체 글·다른 게시판 글이면 null) */
    BoardAnonPostDTO selectPost(@Param("companyId") Long companyId,
                                @Param("postId") Long postId);

    /** 댓글을 저장하기 전에 글 줄을 잠근다 (지운 글·다른 업체 글이면 null) */
    Long lockPost(@Param("companyId") Long companyId,
                  @Param("postId") Long postId);

    /** 우리 업체 익명 게시판 번호 (BOARD 표에 줄이 없으면 null) */
    Long selectBoardId(@Param("companyId") Long companyId);

    /** 우리 업체 익명 게시판 줄 만들기 (첫 글을 쓸 때) */
    int insertBoard(@Param("companyId") Long companyId);

    /** 글 저장 (성공하면 1) */
    int insertPost(@Param("postId") Long postId,
                   @Param("companyId") Long companyId,
                   @Param("boardId") Long boardId,
                   @Param("authorId") Long authorId,
                   @Param("title") String title,
                   @Param("content") String content);

    /** 글 제목·내용 고치기 (성공하면 1, 없는 글·남의 글이면 0) */
    int updatePost(@Param("companyId") Long companyId,
                   @Param("postId") Long postId,
                   @Param("authorId") Long authorId,
                   @Param("title") String title,
                   @Param("content") String content);

    /** 글 지우기: 삭제 표시만 한다 (성공하면 1, 없는 글·남의 글이면 0) */
    int deletePost(@Param("companyId") Long companyId,
                   @Param("postId") Long postId,
                   @Param("authorId") Long authorId);

    /** 댓글 목록 (쓴 사람 이름 없이) */
    List<BoardAnonCommentDTO> selectComments(@Param("companyId") Long companyId,
                                             @Param("postId") Long postId);
}
