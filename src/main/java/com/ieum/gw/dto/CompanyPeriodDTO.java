package com.ieum.gw.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 업체 이용 기간 요약(운영자 화면, SubscriptionService.listCompanyPeriods). 계산은 헤더·이용 결제 화면과 같다 */
@Getter
@Setter
@ToString
public class CompanyPeriodDTO {
    private String status;          // TRIAL(체험 중) / PAID(유료) / EXPIRED(만료)
    private boolean paid;           // 지금(만료면 마지막) 기간이 유료면 true. 만료의 체험·유료를 이것으로 가른다
    private String plan;            // 그 기간의 요금제 MONTHLY / ANNUAL. 체험은 null
    private int seats;              // 그 기간의 구매 인원. 체험은 신청 인원
    private LocalDate firstDay;     // 이용 기간 첫날. 유료는 최초 기준일이라 연장분에서도 처음 시작한 날이다(ServicePeriods.firstDayOf)
    private LocalDate lastDay;      // 그 기간의 마지막 날(다음 날 0시에 만료)
    private long daysLeft;          // 마지막 날까지 남은 일수. 0이면 마지막 날이거나 이미 지났다
    private LocalDate trialLastDay; // 체험 기간의 마지막 날. 체험 기간이 없으면 null
}
