package com.ieum.gw.site.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

// 무료 체험 신청 입력(POST /trial/api/apply의 JSON 본문, ACC-01).
// 비밀번호 평문이 들어 있으므로 @ToString을 붙이지 않는다(로그에 남지 않게)
@Getter
@Setter
public class TrialApplyDTO {
    private String companyName; // 업체명 1~200자
    private BigDecimal seats;   // 이용 인원, 관리자 포함 1~10명. Integer로 받으면 Jackson이 10.9를 10으로 잘라 받아서 BigDecimal로 받고 정수인지 본다
    private String email;       // 담당자 이메일. 업체 이메일이자 관리자 계정 이메일
    private String adminName;   // 관리자 이름 1~100자
    private String password;    // 관리자 비밀번호 평문 8자 이상·UTF-8 72바이트 이하. employee가 BCrypt로 해시한다
}
