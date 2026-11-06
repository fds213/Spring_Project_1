package com.ieum.gw.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.AttendanceApprovalStepDTO;
import com.ieum.gw.dto.EmployeeHireDTO;
import com.ieum.gw.dto.LeaveCycleDTO;
import com.ieum.gw.dto.LeaveGrantDTO;
import com.ieum.gw.dto.LeaveRequestDTO;
import com.ieum.gw.dto.LeaveSlotDTO;

@Mapper
public interface LeaveMapper {

    EmployeeHireDTO selectEmployeeHire(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    // 연차 부여 행 (직원마다 입사일부터 하나)
    LeaveCycleDTO selectCycleOn(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("day") String day);

    LeaveCycleDTO selectCycleOnForUpdate(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("day") String day);

    int insertCycle(LeaveCycleDTO cycle);

    BigDecimal selectLedgerSum(@Param("companyId") Long companyId, @Param("leaveCycleId") Long leaveCycleId);

    // 연차 추가 부여 (LC_GRANT)

    /** 부여 행의 추가 부여(±) 합 */
    BigDecimal selectGrantSum(@Param("companyId") Long companyId, @Param("leaveCycleId") Long leaveCycleId);

    /** 추가 부여(+)·되돌리기(-) 한 줄을 사유와 함께 넣는다 */
    int insertLedgerGrant(@Param("companyId") Long companyId, @Param("leaveCycleId") Long leaveCycleId,
                          @Param("deltaDays") BigDecimal deltaDays, @Param("actorAccountId") Long actorAccountId,
                          @Param("note") String note);

    /** 직원 한 명의 추가 부여 내역 (최근 순) */
    List<LeaveGrantDTO> selectGrantHistory(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    List<LeaveCycleDTO> selectCompanyCyclesOn(@Param("companyId") Long companyId, @Param("day") String day);

    // 사용 기록
    List<LeaveSlotDTO> selectTakenSlots(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("days") List<String> days);

    int insertSlot(LeaveSlotDTO slot);

    int insertLedgerUse(@Param("companyId") Long companyId, @Param("leaveCycleId") Long leaveCycleId,
                        @Param("leaveRequestDayId") Long leaveRequestDayId, @Param("deltaDays") BigDecimal deltaDays,
                        @Param("actorAccountId") Long actorAccountId);

    // 연차 화면 (전자결재의 휴가 신청서)
    BigDecimal selectPendingDays(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                                 @Param("startsOn") String startsOn, @Param("endsOn") String endsOn);

    List<LeaveRequestDTO> selectRequests(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                                         @Param("startsOn") String startsOn, @Param("endsOn") String endsOn);

    List<AttendanceApprovalStepDTO> selectSteps(@Param("companyId") Long companyId, @Param("documentIds") List<Long> documentIds);
}
