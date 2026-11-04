package com.ieum.gw.dao;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.AttendanceApprovalStepDTO;
import com.ieum.gw.dto.EmployeeHireDTO;
import com.ieum.gw.dto.LeaveCycleDTO;
import com.ieum.gw.dto.LeaveRequestDTO;
import com.ieum.gw.dto.LeaveSlotDTO;
import com.ieum.gw.mapper.LeaveMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class LeaveDAO {
    private final LeaveMapper mapper;

    // 입사일
    public EmployeeHireDTO selectEmployeeHire(Long companyId, Long employeeId) { return mapper.selectEmployeeHire(companyId, employeeId); }

    // 연차 부여 행(주기)
    public LeaveCycleDTO selectCycleOn(Long companyId, Long employeeId, String day) { return mapper.selectCycleOn(companyId, employeeId, day); }
    public LeaveCycleDTO selectCycleOnForUpdate(Long companyId, Long employeeId, String day) { return mapper.selectCycleOnForUpdate(companyId, employeeId, day); }
    public List<LeaveCycleDTO> selectCompanyCyclesOn(Long companyId, String day) { return mapper.selectCompanyCyclesOn(companyId, day); }
    public int insertCycle(LeaveCycleDTO cycle) { return mapper.insertCycle(cycle); }

    // 사용·차감 기록
    public int insertSlot(LeaveSlotDTO slot) { return mapper.insertSlot(slot); }
    public int insertLedgerUse(Long companyId, Long leaveCycleId, Long leaveRequestDayId, BigDecimal deltaDays, Long actorAccountId) { return mapper.insertLedgerUse(companyId, leaveCycleId, leaveRequestDayId, deltaDays, actorAccountId); }

    /** 쓴 날이 없으면 null이 와서 0으로 바꾼다 */
    public BigDecimal selectLedgerSum(Long companyId, Long leaveCycleId) {
        BigDecimal sum = mapper.selectLedgerSum(companyId, leaveCycleId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /** 날짜가 없으면 IN () 이 되지 않게 묻지 않는다 */
    public List<LeaveSlotDTO> selectTakenSlots(Long companyId, Long employeeId, List<String> days) {
        return days.isEmpty() ? List.of() : mapper.selectTakenSlots(companyId, employeeId, days);
    }

    // 휴가 신청 내역
    public List<LeaveRequestDTO> selectRequests(Long companyId, Long employeeId, String startsOn, String endsOn) { return mapper.selectRequests(companyId, employeeId, startsOn, endsOn); }
    public List<AttendanceApprovalStepDTO> selectSteps(Long companyId, List<Long> documentIds) { return mapper.selectSteps(companyId, documentIds); }

    /** 결재 중인 휴가가 없으면 null이 와서 0으로 바꾼다 */
    public BigDecimal selectPendingDays(Long companyId, Long employeeId, String startsOn, String endsOn) {
        BigDecimal days = mapper.selectPendingDays(companyId, employeeId, startsOn, endsOn);
        return days == null ? BigDecimal.ZERO : days;
    }
}
