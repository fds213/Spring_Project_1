package com.ieum.gw.site.service;

import lombok.Getter;

/** 체험 신청 입력이 규칙에 맞지 않는다. field는 틀린 칸(TrialApplyDTO의 필드 이름), 메시지는 화면에 그대로 보일 문구 */
@Getter
public class TrialFormException extends IllegalArgumentException {

    private final String field;

    public TrialFormException(String field, String message) {
        super(message);
        this.field = field;
    }
}
