package com.ieum.gw.service;

import lombok.Getter;

/** 도입 문의 입력이 규칙에 맞지 않는다. field는 틀린 칸(InquirySubmitDTO의 필드 이름), 메시지는 화면에 그대로 보일 문구 */
@Getter
public class InquiryFormException extends IllegalArgumentException {

    private final String field;

    public InquiryFormException(String field, String message) {
        super(message);
        this.field = field;
    }
}
