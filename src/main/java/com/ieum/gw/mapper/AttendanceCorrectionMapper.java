package com.ieum.gw.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.AttendanceCorrectionDTO;
import com.ieum.gw.dto.AttendanceDTO;

/** 근태 정정 요청 (attendance_correction). 승인하면 근태(attendance)의 퇴근 시각을 채운다 */
@Mapper
public interface AttendanceCorrectionMapper {

    /** 직원의 그날 근태 1건을 잠그며 읽는다 (요청 때 원래 값을 복사하기 전) */
    AttendanceDTO selectAttendanceOnForUpdate(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                                              @Param("workDate") String workDate);

    /** 정정 요청 1건을 넣는다. 원래 출퇴근은 화면 값이 아니라 근태 행에서 복사한다 */
    int insertCorrection(AttendanceCorrectionDTO dto);

    /** 직원 본인의 그 달 정정 요청 (최근 순) */
    List<AttendanceCorrectionDTO> selectMyCorrections(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                                                      @Param("month") String month);

    /** 정정 요청 1건과 요청자 부서장을 잠그며 읽는다 (처리·취소 전) */
    AttendanceCorrectionDTO selectCorrectionForUpdate(@Param("companyId") Long companyId, @Param("id") Long id);

    /** 이 사람이 처리할 대기 요청 (부서장이면 자기 부서원, 업체 관리자면 부서장이 없거나 부서장 본인이 요청한 것) */
    List<AttendanceCorrectionDTO> selectInbox(@Param("companyId") Long companyId, @Param("approverId") Long approverId,
                                              @Param("admin") boolean admin);

    /** 대기 중인 요청을 요청자 본인이 취소한다 */
    int cancelCorrection(@Param("companyId") Long companyId, @Param("id") Long id, @Param("employeeId") Long employeeId);

    /** 대기 중인 요청을 승인·반려로 바꾼다 */
    int decideCorrection(@Param("companyId") Long companyId, @Param("id") Long id, @Param("status") String status,
                         @Param("approverId") Long approverId, @Param("rejectReason") String rejectReason);

    /** 승인: 요청한 퇴근 시각을 그날 근태에 넣는다 (퇴근이 비어 있을 때만) */
    int applyCheckOut(@Param("companyId") Long companyId, @Param("id") Long id);
}
