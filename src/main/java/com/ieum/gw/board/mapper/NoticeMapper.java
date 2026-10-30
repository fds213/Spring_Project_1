package com.ieum.gw.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.board.dto.NoticeDTO;

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
    // ---------- 글쓰기 ----------

    // 우리 업체의 공지 게시판 번호 (BOARD 표에 공지 게시판 줄이 없으면 null)
    Long selectNoticeBoardId(@Param("companyId") Long companyId);

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