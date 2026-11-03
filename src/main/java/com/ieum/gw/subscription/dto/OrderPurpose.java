package com.ieum.gw.subscription.dto;

/** PAYMENT_ORDER.PURPOSE_CODE, SUBSCRIPTION_CHANGE.CHANGE_TYPE. 공통 코드 PAYMENT_PURPOSE와 이름이 같다 */
public enum OrderPurpose {
    PP_FIRST, // 최초 유료 전환: 승인일부터 유료 기간을 시작한다
    PP_RENEW  // 기간 연장: 지금 만료 시각에 새 기간을 이어 붙인다(SUB-03)
}
