package com.ieum.gw.dto;

import lombok.Getter;

// 발송 결과. 실패 사유는 발송 기록(MAIL_OUTBOX.LAST_ERROR)에 남고 운영자 화면에 그대로 보여 줄 수 있는 말이다
@Getter
public class MailResultDTO {
    private final boolean ok;
    private final String reason;

    private MailResultDTO(boolean ok, String reason) {
        this.ok = ok;
        this.reason = reason;
    }

    public static MailResultDTO sent() {
        return new MailResultDTO(true, null);
    }

    public static MailResultDTO failed(String reason) {
        return new MailResultDTO(false, reason);
    }
}
