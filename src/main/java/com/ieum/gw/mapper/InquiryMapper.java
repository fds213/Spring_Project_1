package com.ieum.gw.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.ieum.gw.dto.PublicInquiryDTO;

@Mapper
public interface InquiryMapper {

    int insertInquiry(PublicInquiryDTO dto);
}
