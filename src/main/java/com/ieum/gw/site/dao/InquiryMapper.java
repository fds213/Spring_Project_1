package com.ieum.gw.site.dao;

import org.apache.ibatis.annotations.Mapper;

import com.ieum.gw.site.dto.PublicInquiryDTO;

@Mapper
public interface InquiryMapper {

    int insertInquiry(PublicInquiryDTO dto);
}
