package com.ieum.gw.service;

/** 답변을 들어줄 수 없을 때(입력 오류, 겹친 요청). status는 화면에 보낼 HTTP 상태다 */
public class OpsReplyException extends RuntimeException {

    private final int status;

    public OpsReplyException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
