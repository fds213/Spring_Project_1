package com.ieum.gw.board.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.board.dto.NoticeDTO;
import com.ieum.gw.board.mapper.NoticeMapper;

import lombok.RequiredArgsConstructor;

// 공지사항 DAO: NoticeMapper를 감싸서 Service에 건네준다.
// Service는 Mapper를 직접 부르지 않고 이 DAO만 부른다 (AGENTS.md)
@Repository
@RequiredArgsConstructor
public class NoticeDAO {

    private final NoticeMapper mapper;

    // 공지 목록 (한 페이지)
    public List<NoticeDTO> selectNoticeList(Long companyId, List<String> words, int offset, int size) {
        return mapper.selectNoticeList(companyId, words, offset, size);
    }

    // 공지 전체 개수
    public int countNotices(Long companyId, List<String> words) {
        return mapper.countNotices(companyId, words);
    }
}