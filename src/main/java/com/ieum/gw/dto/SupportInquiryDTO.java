package com.ieum.gw.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 이음 장애 문의 한 건 (SUPPORT_INQUIRY). 칸 이름은 화면 support/inquiry.js·ops/inquiry.js가 읽는 그대로다 */
@Getter
@Setter
@ToString
public class SupportInquiryDTO {
    private Long no;            // 접수번호(SUPPORT_INQUIRY_ID)
    private String at;          // 보낸 시각
    private String company;     // 업체 이름. 운영자 화면에만 채운다
    private String companyCode; // 운영자 화면에만 채운다
    private String name;        // 보낸 직원 이름. 운영자 화면에만 채운다
    private String title;
    private String body;
    private String state;       // 공통 코드 SUPPORT_STATUS(SUP_*)
    private List<SupportReplyDTO> replies; // 답변, 먼저 쓴 순

    @JsonIgnore
    private Long companyId;     // 답변을 문의와 같은 업체로 저장할 때 쓴다
}
