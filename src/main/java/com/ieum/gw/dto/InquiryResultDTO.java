package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 도입 문의 접수 결과(완료 화면에 보여 줄 값). 봇 막기 칸에 걸려 저장하지 않았으면 no·email은 null이다
@Getter
@Setter
@ToString
public class InquiryResultDTO {
    private Long no;      // 접수번호(PUBLIC_INQUIRY_ID). 안내용이고 이 번호로 조회하는 기능은 없다(WEB-03)
    private String email; // 정리한 이메일(소문자)
    private boolean mailSent; // 접수 메일을 메일 서버에 넘겼는가. 꺼져 있거나 실패했거나 하루 제한에 걸리면 false
}
