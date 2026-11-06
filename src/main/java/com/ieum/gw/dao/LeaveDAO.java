package com.ieum.gw.dao;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.AttendanceApprovalStepDTO;
import com.ieum.gw.dto.EmployeeHireDTO;
import com.ieum.gw.dto.LeaveCycleDTO;
import com.ieum.gw.dto.LeaveGrantDTO;
import com.ieum.gw.dto.LeaveRequestDTO;
import com.ieum.gw.dto.LeaveSlotDTO;
import com.ieum.gw.mapper.LeaveMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class LeaveDAO {
    private final LeaveMapper mapper;

    // 입사일

    /** 직원의 입사일을 읽는다 */
    public EmployeeHireDTO selectEmployeeHire(Long companyId, Long employeeId) { return mapper.selectEmployeeHire(companyId, employeeId); }

    // 연차 부여 행(주기)

    /** 그 날이 들어 있는 연차 부여 행을 읽는다 */
    public LeaveCycleDTO selectCycleOn(Long companyId, Long employeeId, String day) { return mapper.selectCycleOn(companyId, employeeId, day); }

    /** 그 날이 들어 있는 연차 부여 행을 잠그며 읽는다(같은 직원 동시 차감 막기) */
    public LeaveCycleDTO selectCycleOnForUpdate(Long companyId, Long employeeId, String day) { return mapper.selectCycleOnForUpdate(companyId, employeeId, day); }

    /** 업체 직원들의 입사일이 들어 있는 연차 부여 행과 내역 합을 한 번에 읽는다 */
    public List<LeaveCycleDTO> selectCompanyCyclesAtHire(Long companyId) { return mapper.selectCompanyCyclesAtHire(companyId); }

    /** 연차 부여 행(15일)을 만든다 */
    public int insertCycle(LeaveCycleDTO cycle) { return mapper.insertCycle(cycle); }

    // 사용·차감 기록

    /** 휴가가 차지하는 시간대를 하나 넣는다 */
    public int insertSlot(LeaveSlotDTO slot) { return mapper.insertSlot(slot); }

    /** 연차 사용(음수) 내역을 남긴다 */
    public int insertLedgerUse(Long companyId, Long leaveCycleId, Long leaveRequestDayId, BigDecimal deltaDays, Long actorAccountId) { return mapper.insertLedgerUse(companyId, leaveCycleId, leaveRequestDayId, deltaDays, actorAccountId); }

    /** 부여 행의 사용·복원 합을 읽는다(쓴 날이 없으면 null이 와서 0으로 바꾼다) */
    public BigDecimal selectLedgerSum(Long companyId, Long leaveCycleId) {
        BigDecimal sum = mapper.selectLedgerSum(companyId, leaveCycleId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    // 연차 추가 부여 (LC_GRANT)

    /** 부여 행의 추가 부여(±) 합을 읽는다(없으면 null이 와서 0으로 바꾼다) */
    public BigDecimal selectGrantSum(Long companyId, Long leaveCycleId) {
        BigDecimal sum = mapper.selectGrantSum(companyId, leaveCycleId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /** 추가 부여(+)·되돌리기(-) 한 줄을 사유와 함께 남긴다 */
    public int insertLedgerGrant(Long companyId, Long leaveCycleId, BigDecimal deltaDays, Long actorAccountId, String note) { return mapper.insertLedgerGrant(companyId, leaveCycleId, deltaDays, actorAccountId, note); }

    /** 직원 한 명의 추가 부여 내역을 최근 순으로 읽는다 */
    public List<LeaveGrantDTO> selectGrantHistory(Long companyId, Long employeeId) { return mapper.selectGrantHistory(companyId, employeeId); }

    /** 승인된 휴가가 그 날들에 이미 차지한 시간대를 읽는다(날짜가 없으면 IN () 이 되지 않게 묻지 않는다) */
    public List<LeaveSlotDTO> selectTakenSlots(Long companyId, Long employeeId, List<String> days) {
        return days.isEmpty() ? List.of() : mapper.selectTakenSlots(companyId, employeeId, days);
    }

    // 휴가 신청 내역

    /** 기간에 걸친 휴가 신청서를 읽는다 */
    public List<LeaveRequestDTO> selectRequests(Long companyId, Long employeeId, String startsOn, String endsOn) { return mapper.selectRequests(companyId, employeeId, startsOn, endsOn); }

    /** 휴가 신청서 여러 건의 결재선을 한 번에 읽는다 */
    public List<AttendanceApprovalStepDTO> selectSteps(Long companyId, List<Long> documentIds) { return mapper.selectSteps(companyId, documentIds); }

    /** 기간 안의 결재 중 휴가 일수를 읽는다(없으면 null이 와서 0으로 바꾼다) */
    public BigDecimal selectPendingDays(Long companyId, Long employeeId, String startsOn, String endsOn) {
        BigDecimal days = mapper.selectPendingDays(companyId, employeeId, startsOn, endsOn);
        return days == null ? BigDecimal.ZERO : days;
    }
}
