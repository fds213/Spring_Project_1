package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 근태 정정 요청 1건 (attendance_correction). 날짜·시각은 DB가 서울 시각 문자열로 만들어 준다 */
@Getter
@Setter
public class AttendanceCorrectionDTO {
    private Long attendanceCorrectionId;
    private Long companyId;
    private Long employeeId;          // 요청한 직원
    private String employeeName;
    private String employeeNo;
    private String departmentName;
    private Long headEmployeeId;      // 요청자 부서의 재직 중인 부서장 (처리자 판단. 없거나 퇴사·휴직이면 null)
    private String workDate;          // YYYY-MM-DD
    private Long attendanceId;        // 그날 근태 (퇴근 누락이면 있음)
    private String originCheckIn;     // HH24:MI:SS, 요청 당시 원래 출근
    private String originCheckOut;    // HH24:MI:SS, 요청 당시 원래 퇴근 (퇴근 누락이면 null)
    private String reqCheckOut;       // HH24:MI, 요청한 퇴근 (저장할 때 넘기는 값도 이 모양)
    private String reason;
    private String statusCode;        // ACR_REQUESTED·ACR_APPROVED·ACR_REJECTED·ACR_CANCELED
    private String approverName;
    private String decidedAt;         // YYYY-MM-DDTHH24:MI:SS
    private String rejectReason;
    private String createdAt;         // YYYY-MM-DDTHH24:MI:SS
}
