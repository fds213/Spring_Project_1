package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 근태 단말기 1대 (attendance_device). 키는 해시로만 찾고 이 객체에 담지 않는다 */
@Getter
@Setter
public class AttendanceDeviceDTO {
    private Long attendanceDeviceId;
    private Long companyId;
    private String companyName;
    private String deviceName;
}
