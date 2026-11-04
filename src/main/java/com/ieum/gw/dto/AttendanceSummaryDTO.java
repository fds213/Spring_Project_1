package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 근태·연차 관리: 직원 한 명의 한 달 근태 요약과 연차 계산에 쓰는 입사 정보 */
@Getter
@Setter
public class AttendanceSummaryDTO {
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String departmentName;
    private String employmentStatus;
    private String hireDate;           // YYYY-MM-DD, 없으면 null
    private String deviceUserNo;       // 단말기 번호, 연결 안 됐으면 null
    private String todayIn;            // HH24:MI:SS, 오늘 기록 없으면 null
    private String todayOut;
    private Integer workDays;          // 그 달 출근한 날 수
    private Integer lateDays;          // 09:10:00 보다 늦은 출근 (10분 유예)
    private Integer earlyDays;         // 18:00:00 전 퇴근
}
