package com.ieum.gw.notification.dto;

/** NOTIFICATION.NOTIFICATION_TYPE. 공통 코드 NOTIFICATION_TYPE과 이름이 같다. 제목은 공통 코드 이름과 같다 */
public enum NotificationType {
    NT_APPROVAL_REQUEST("결재 요청", "'%s' 문서의 결재를 요청받았습니다."),
    NT_APPROVAL_DONE("결재 완료", "'%s' 문서가 최종 승인되었습니다."),
    NT_APPROVAL_REJECT("결재 반려", "'%s' 문서가 반려되었습니다."),
    NT_TICKET_ASSIGNED("티켓 배정", "'%s' 티켓이 배정되었습니다."),
    NT_TICKET_RESOLVED("티켓 처리 완료", "'%s' 티켓이 처리되었습니다. 수령·조치 결과를 확인해 주세요.");

    private final String title;
    private final String messageFormat;

    NotificationType(String title, String messageFormat) {
        this.title = title;
        this.messageFormat = messageFormat;
    }

    public String getTitle() {
        return title;
    }

    /** 문서·티켓 제목을 넣은 알림 내용. 제목은 원본 표에서 200자 이내다(MESSAGE는 1000자) */
    public String messageFor(String targetTitle) {
        return String.format(messageFormat, targetTitle);
    }
}
