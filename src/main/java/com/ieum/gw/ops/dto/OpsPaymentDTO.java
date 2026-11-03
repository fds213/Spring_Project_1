package com.ieum.gw.ops.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 업체 관리 상세의 결제 시도 한 건 */
@Getter
@Setter
@ToString
public class OpsPaymentDTO {
    private String resultLabel; // 결제 완료, 반영 대기(승인했지만 이용 기간에 아직 반영 못 함), 결제 실패
    private String resultTone;  // 상태 색: paid, pending, failed
    private String kindLabel;   // 첫 결제, 연장
    private String planLabel;   // 월간, 연간
    private long amount;
    private String at;          // 승인 시각. 실패 건은 기록한 시각
    private Integer users;      // 구매 인원
    private String reason;      // 실패 사유. 승인 건은 null
    private int seq;            // 기록된 순서(1부터). 시각이 같으면 큰 쪽이 나중이다
}
