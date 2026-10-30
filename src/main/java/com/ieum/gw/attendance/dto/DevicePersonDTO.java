package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;

/** 단말기에 등록된 사람: 단말기 사용자 번호와 연결된 직원 (device_employee + employee) */
@Getter
@Setter
public class DevicePersonDTO {
    private String deviceUserNo;
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String departmentName;
    private String employmentStatus; // ST_SERVING, ST_RETIRED, ST_LEAVE
}
