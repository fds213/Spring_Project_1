package com.ieum.gw.ops.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 비회원 접수 한 건 (PUBLIC_INQUIRY). 칸 이름은 화면 ops/inquiry.js가 읽는 그대로다 */
@Getter
@Setter
@ToString
public class OpsGuestInquiryDTO {
    private Long no;        // 접수번호(PUBLIC_INQUIRY_ID)
    private String at;      // 접수 시각
    private String company;
    private String name;
    private String email;
    private String phone;   // 선택 칸이라 없으면 null
    private String title;
    private String body;
    private String state;   // 공통 코드 INQUIRY_STATUS(IQ_*)
}
