package com.ieum.gw.subscription.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** SERVICE_PERIOD 한 행 (업체 이용 기간: 체험 PD_TRIAL 또는 유료 PD_PAID) */
@Getter
@Setter
@ToString
public class ServicePeriodDTO {
    private Long servicePeriodId;         // SERVICE_PERIOD_ID   insert 전에는 null
    private Long companyId;               // COMPANY_ID
    private boolean trial;                // PERIOD_TYPE이 PD_TRIAL이면 true (코드값은 Mapper XML에만 쓴다)
    private LocalDateTime startsAt;       // STARTS_AT           이 시각부터 쓴다
    private LocalDateTime endsAt;         // ENDS_AT             이 시각부터 못 쓴다(만료)
    private Integer purchasedSeats;       // PURCHASED_SEATS     계약 인원. 체험은 신청 인원
    private LocalDate billingAnchorDate;  // BILLING_ANCHOR_DATE 최초 기간 기준일. 체험은 신청일
}
