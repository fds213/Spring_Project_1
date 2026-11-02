package com.ieum.gw.subscription.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 이용 결제 화면 전체 값(GET /subscription/api/billing, billing.js). 날짜는 문자열 "2026-11-02T00:00"로 보낸다 */
@Getter
@Setter
@ToString
public class BillingDTO {
    private String status;        // TRIAL(체험 중) / PAID(유료) / EXPIRED(만료)
    private boolean paid;         // 지금(만료면 마지막) 기간이 유료면 true
    private String plan;          // 유료일 때 MONTHLY
    private String company;       // 업체명
    private String companyCode;
    private int users;            // 지금 기간의 인원. 체험은 신청 인원
    private int accountsInUse;    // 사용 중 계정 수. 결제 인원은 이보다 적을 수 없다(PAY-01)
    private int maxUsers;         // 한 번에 살 수 있는 인원
    private long pricePerUser;    // 1인 월 단가
    private int trialSeats;       // 체험 중 계정 한도
    private String end;           // 마지막 날(다음 날 0시에 만료)
    private long daysLeft;        // 마지막 날까지 남은 일수. 0이면 마지막 날이거나 이미 만료
    private String trialFrom;     // 체험 첫날
    private String trialEnd;      // 체험 마지막 날
    private String serviceFrom;   // 유료 기간 첫날. 체험이면 null
    private boolean canPay;       // 이번 범위에서는 유료 기간이 한 번도 없는 업체만 결제한다
    private String mode;          // FIRST(첫 결제)
    private int failedPayments;   // 결제 실패 건수
    private List<BillingPaymentDTO> payments; // 최신순
}
