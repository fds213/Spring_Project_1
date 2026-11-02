package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;

/** 출근·퇴근 기록을 남길 때 Mapper에 넘기는 값 */
@Getter
@Setter
public class PunchDTO {
    private Long attendanceId;        // 출근: selectKey 로 채운다, 퇴근: 오늘 근태에서 가져온다
    private Long companyId;
    private Long employeeId;
    private Long attendanceDeviceId;
    private String eventType;         // ATT_IN, ATT_OUT (공통 코드)
    private String eventKey;          // 같은 요청이 두 번 저장되지 않게 하는 고유값
}
