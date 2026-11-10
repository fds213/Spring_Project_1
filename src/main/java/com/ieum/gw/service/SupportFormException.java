package com.ieum.gw.service;

/** 이음 장애 문의·답변 입력이 규칙에 맞지 않는다(400). 메시지는 화면에 그대로 보일 문구 */
public class SupportFormException extends RuntimeException {

    public SupportFormException(String message) {
        super(message);
    }
}
