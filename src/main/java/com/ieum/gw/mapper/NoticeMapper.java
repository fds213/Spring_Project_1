package com.ieum.gw.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.NoticeCommentDTO;
import com.ieum.gw.dto.NoticeDTO;
import com.ieum.gw.dto.NoticeFileDTO;

// 공지사항 SQL을 부르는 창구. 메서드 이름 = NoticeMapper.xml 의 id
@Mapper
public interface NoticeMapper {

    // 공지 목록 (한 페이지 분량)
    List<NoticeDTO> selectNoticeList(@Param("companyId") Long companyId,
                                     @Param("words") List<String> words,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    // 공지 전체 개수 (페이지 수 계산용)
    int countNotices(@Param("companyId") Long companyId,
                     @Param("words") List<String> words);

    // ---------- 글 보기 ----------

    // 공지 한 건 (우리 업체의 지우지 않은 공지만. 없으면 null)
    NoticeDTO selectNotice(@Param("companyId") Long companyId,
                           @Param("postId") Long postId);

    // 댓글을 저장하기 전에 공지 줄을 잠근다 (지운 공지·다른 업체 공지면 null)
    Long lockNotice(@Param("companyId") Long companyId,
                    @Param("postId") Long postId);

    // ---------- 수정 · 삭제 ----------

    // 공지 제목·내용 고치기 (고친 줄 수: 1이면 성공, 0이면 없는 글·지운 글)
    int updateNotice(@Param("companyId") Long companyId,
                     @Param("postId") Long postId,
                     @Param("title") String title,
                     @Param("content") String content);

    // 공지 지우기: 진짜로 지우지 않고 삭제 표시만 한다 (고친 줄 수: 1이면 성공)
    int deleteNotice(@Param("companyId") Long companyId,
                     @Param("postId") Long postId);

    // ---------- 댓글 ----------

    // 공지 한 개의 댓글 목록 (지우지 않은 것만, 오래된 순)
    List<NoticeCommentDTO> selectComments(@Param("companyId") Long companyId,
                                          @Param("postId") Long postId);

    // 댓글 쓴 사람의 사원 ID (없는 댓글·지운 댓글이면 null). 삭제 권한 확인용
    Long selectCommentAuthor(@Param("companyId") Long companyId,
                             @Param("postId") Long postId,
                             @Param("commentId") Long commentId);

    // 댓글 저장
    int insertComment(@Param("companyId") Long companyId,
                      @Param("postId") Long postId,
                      @Param("authorId") Long authorId,
                      @Param("content") String content);

    // 댓글 하나 지우기 (삭제 표시)
    int deleteComment(@Param("companyId") Long companyId,
                      @Param("postId") Long postId,
                      @Param("commentId") Long commentId);

    // 공지를 지울 때 그 공지의 댓글도 모두 지우기 (삭제 표시)
    int deleteCommentsOfPost(@Param("companyId") Long companyId,
                             @Param("postId") Long postId);
    // ---------- 첨부파일 ----------

    // 공지 한 개의 첨부파일 목록 (이름·크기·종류)
    List<NoticeFileDTO> selectFiles(@Param("companyId") Long companyId,
                                    @Param("postId") Long postId);

    // 공지 한 개에 붙은 파일 번호들 (수정할 때 뺄 것·더할 것 비교, 내려받기 확인용)
    List<Long> selectFileIds(@Param("companyId") Long companyId,
                             @Param("postId") Long postId);

    // 공지에 파일 붙이기
    int insertPostFile(@Param("companyId") Long companyId,
                       @Param("postId") Long postId,
                       @Param("attachmentId") Long attachmentId);

    // 공지에서 파일 떼기 (수정할 때 뺀 파일)
    int deletePostFile(@Param("companyId") Long companyId,
                       @Param("postId") Long postId,
                       @Param("attachmentId") Long attachmentId);
    // ---------- 글쓰기 ----------

    // 우리 업체의 공지 게시판 번호 (BOARD 표에 공지 게시판 줄이 없으면 null)
    Long selectNoticeBoardId(@Param("companyId") Long companyId);

    // 우리 업체의 공지 게시판 줄 만들기 (첫 글을 쓸 때)
    int insertNoticeBoard(@Param("companyId") Long companyId);

    // 새 게시글 번호 하나 뽑기 (시퀀스 SQ_POST). 저장 뒤 화면을 그 글로 보내려고 먼저 뽑는다
    Long selectNextPostId();

    // 공지 저장 (저장된 줄 수를 돌려준다: 성공하면 1)
    int insertNotice(@Param("postId") Long postId,
                     @Param("companyId") Long companyId,
                     @Param("boardId") Long boardId,
                     @Param("authorId") Long authorId,
                     @Param("title") String title,
                     @Param("content") String content);
}