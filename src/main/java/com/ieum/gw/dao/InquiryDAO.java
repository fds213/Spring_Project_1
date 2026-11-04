package com.ieum.gw.dao;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.PublicInquiryDTO;
import com.ieum.gw.mapper.InquiryMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InquiryDAO {

    private final InquiryMapper mapper;

    /** 문의 1행 저장. 뽑은 번호를 dto.publicInquiryId에 채운다 */
    public int insert(PublicInquiryDTO dto) {
        return mapper.insertInquiry(dto);
    }
}
