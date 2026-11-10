package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

// 저장할 장애 문의 1행(SUPPORT_INQUIRY)
@Getter
@Setter
public class SupportInquirySaveDTO {
    private Long no;         // 저장할 때 시퀀스에서 뽑아 채운다
    private Long companyId;  // 로그인 사용자의 업체
    private Long employeeId; // 로그인 사용자
    private String title;
    private String body;
}
