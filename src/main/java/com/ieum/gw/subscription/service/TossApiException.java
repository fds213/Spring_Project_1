package com.ieum.gw.subscription.service;

import java.util.Set;

/** 토스 API가 오류로 답했거나 답을 받지 못했다. 네트워크 오류·시간 초과는 status가 0이다 */
public class TossApiException extends RuntimeException {

    // 400번대지만 승인되지 않았다고 단정할 수 없거나(이미 처리됨·처리 중·일시 오류), 구매자가 아니라 우리 설정 문제인 오류
    private static final Set<String> NOT_FINAL = Set.of(
            "ALREADY_PROCESSED_PAYMENT", "ALREADY_PROCESSING_REQUEST", "IDEMPOTENT_REQUEST_PROCESSING",
            "PROVIDER_ERROR", "INVALID_API_KEY", "UNAUTHORIZED_KEY", "INCORRECT_BASIC_AUTH_FORMAT",
            "INVALID_IDEMPOTENCY_KEY");

    private final int status;
    private final String code;

    public TossApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public TossApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
        this.code = "NETWORK";
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** 승인되지 않았음이 확실한 거절인가(한도 초과·결제 시간 만료 등). 이때만 주문을 실패로 닫는다 */
    public boolean isRejected() {
        return status >= 400 && status < 500 && status != 429 && !NOT_FINAL.contains(code);
    }

    /** 같은 승인 요청이 아직 처리 중인가 */
    public boolean isInProgress() {
        return "IDEMPOTENT_REQUEST_PROCESSING".equals(code) || "ALREADY_PROCESSING_REQUEST".equals(code);
    }
}
