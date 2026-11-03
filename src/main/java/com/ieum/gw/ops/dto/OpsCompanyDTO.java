package com.ieum.gw.ops.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 업체 관리 목록 한 줄 (ops/company.js). 날짜는 문자열 "2026-11-02T14:03:22"로 보낸다 */
@Getter
@Setter
@ToString
public class OpsCompanyDTO {
    private String companyCode;
    private String company;     // 업체명
    private String adminName;
    private String adminNo;
    private int users;          // 이용 인원: 지금(만료면 마지막) 기간의 구매 인원
    private long total;         // 월 기준 이용료: 이용 인원 × 1인 월 단가. 연간 할인 전 값이다
    private OpsStateDTO state;
    private String at;          // 체험 신청 시각
}
