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
}