package com.ieum.gw.subscription.service;

/**
 * 결제 요청을 들어줄 수 없을 때. status는 화면에 보낼 HTTP 상태(docs/front-screen-api.md 2장)이고,
 * retry가 true면 같은 요청을 다시 보내도 된다(승인 결과를 아직 모를 때).
 */
public class BillingException extends RuntimeException {

    private final int status;
    private final boolean retry;

    public BillingException(int status, String message) {
        this(status, message, false);
    }

    public BillingException(int status, String message, boolean retry) {
        super(message);
        this.status = status;
        this.retry = retry;
    }

    public int getStatus() {
        return status;
    }

    public boolean isRetry() {
        return retry;
    }
}
