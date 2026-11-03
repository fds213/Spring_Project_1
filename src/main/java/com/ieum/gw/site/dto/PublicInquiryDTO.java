package com.ieum.gw.site.dto;

import lombok.Getter;
import lombok.Setter;

// 홈페이지 문의 1행(PUBLIC_INQUIRY). 상태(IQ_RECEIVED)와 등록·수정 일시는 DB 기본값을 쓴다
@Getter
@Setter
public class PublicInquiryDTO {
    private Long publicInquiryId; // 저장할 때 시퀀스에서 뽑아 채운다
    private String companyName;
    private String requesterName;
    private String email;
    private String phone;         // 선택 칸. 비었으면 null
    private String title;
    private String content;
}
