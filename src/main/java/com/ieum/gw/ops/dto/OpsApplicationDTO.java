package com.ieum.gw.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 신청 현황 한 줄 (ops/application.js). 날짜는 문자열 "2026-11-02T14:03:22"로 보낸다 */
@Getter
@Setter
@ToString
public class OpsApplicationDTO {
    private Long no;              // 신청번호(COMPANY_ID). 업체당 신청이 1건이라 따로 저장하지 않는다
    private String at;            // 체험 신청 시각
    private String company;       // 업체명
    private String companyCode;
    private int users;            // 이용 인원: 지금(만료면 마지막) 기간의 구매 인원
    private long total;           // 월 기준 이용료: 이용 인원 × 1인 월 단가
    private String email;
    private boolean emailVerified;
    private String verifiedAt;    // 이메일 인증 시각. 인증 전이면 null
    private String adminName;
    private String adminNo;
    private String status;        // TRIAL / PAID / EXPIRED
    private boolean paid;         // 지금(만료면 마지막) 기간이 유료면 true
    private String plan;          // MONTHLY / ANNUAL. 유료가 아니면 null
    private String paidAt;        // 첫 승인 시각. 없으면 null
    private String serviceFrom;   // 유료 기간 첫날. 유료가 아니면 null
    private String end;           // 지금(만료면 마지막) 기간의 마지막 날
    private long daysLeft;        // 마지막 날까지 남은 일수
    private String trialEnd;      // 체험 기간의 마지막 날
    private int failedPayments;

    @JsonProperty("isNew") // 필드 이름을 isNew로 쓰면 Jackson이 "new"로 보낸다
    private boolean newApplication; // 신청한 지 24시간 안이면 true. 읽음은 저장하지 않는다
}
