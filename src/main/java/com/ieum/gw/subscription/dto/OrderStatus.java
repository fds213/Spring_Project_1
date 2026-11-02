package com.ieum.gw.subscription.dto;

/** PAYMENT_ORDER.STATUS_CODE. 공통 코드 ORDER_STATUS와 이름이 같다 */
public enum OrderStatus {
    ORD_CREATED,   // 주문 생성: 서버가 인원·금액을 정했다. 결제창을 열기 전
    ORD_CONFIRMED, // 결제 승인 확인: 토스 승인을 서버가 확인하고 기록했다. 아직 이용 기간에 반영하기 전
    ORD_APPLIED,   // 이용 반영 완료: 유료 기간을 만들었다
    ORD_FAILED     // 결제 실패: 유료 권한을 주지 않고 끝냈다
}
