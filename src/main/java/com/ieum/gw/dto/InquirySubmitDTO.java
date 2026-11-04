package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

// 도입 문의 입력(POST /inquiry/api/submit의 JSON 본문, WEB-02). 값 이름은 화면 JS(site/inquiry.js)가 보내는 그대로다.
// 개인정보 동의는 화면에서만 확인하고 서버로 오지 않는다
@Getter
@Setter
public class InquirySubmitDTO {
    private String companyName; // 회사명 1~50자
    private String name;        // 이름 1~20자
    private String email;       // 답변 받을 이메일 1~100자
    private String phone;       // 휴대전화 010-0000-0000 모양 (선택)
    private String title;       // 문의 제목 1~50자
    private String content;     // 문의 내용 1~1,000자
    private String website;     // 봇 막기 칸. 사람에게는 안 보여서 값이 있으면 저장하지 않는다
}
