package com.ieum.gw.attendance.service;

/** 근태 요청을 거절할 때: HTTP 상태와 화면에 그대로 보여 줄 안내 문구 */
public class AttendanceException extends RuntimeException {

    private final int status;

    public AttendanceException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
