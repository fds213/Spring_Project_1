package com.ieum.gw.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 연차 추가 부여 내역 한 줄 (leave_ledger 의 LC_GRANT). 늘린 것은 +, 되돌린 것은 - */
@Getter
@Setter
public class LeaveGrantDTO {
    private Long leaveLedgerId;
    private BigDecimal deltaDays;
    private String note;        // 부여 사유 (필수)
    private String createdAt;   // YYYY-MM-DDTHH24:MI:SS
    private String actorName;   // 처리한 사람 (사원이 없는 계정이면 null)
}
