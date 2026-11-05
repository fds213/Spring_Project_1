package com.ieum.gw.service;

/** 없는 알림이거나 남의 알림이다. 둘을 구별해 알려 주지 않는다(404) */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("알림을 찾을 수 없습니다.");
    }
}
