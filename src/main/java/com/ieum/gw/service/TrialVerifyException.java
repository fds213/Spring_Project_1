package com.ieum.gw.service;

/** 이메일 인증·다시 보내기를 들어줄 수 없을 때. status는 화면에 보낼 HTTP 상태다 */
public class TrialVerifyException extends RuntimeException {

    private final int status;

    public TrialVerifyException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
