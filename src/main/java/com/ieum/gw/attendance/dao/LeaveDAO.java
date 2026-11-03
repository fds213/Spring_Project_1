package com.ieum.gw.attendance.dao;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.attendance.dto.ApprovalStepDTO;
import com.ieum.gw.attendance.dto.EmployeeHireDTO;
import com.ieum.gw.attendance.dto.LeaveCycleDTO;
import com.ieum.gw.attendance.dto.LeaveRequestDTO;
import com.ieum.gw.attendance.dto.LeaveSlotDTO;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class LeaveDAO {
    private final LeaveMapper mapper;

    public EmployeeHireDTO selectEmployeeHire(Long companyId, Long employeeId) {
        return mapper.selectEmployeeHire(companyId, employeeId);
    }

    public LeaveCycleDTO selectCycleOn(Long companyId, Long employeeId, String day) {
        return mapper.selectCycleOn(companyId, employeeId, day);
    }

    public LeaveCycleDTO selectCycleOnForUpdate(Long companyId, Long employeeId, String day) {
        return mapper.selectCycleOnForUpdate(companyId, employeeId, day);
    }

    public int insertCycle(LeaveCycleDTO cycle) {
        return mapper.insertCycle(cycle);
    }

    public BigDecimal selectLedgerSum(Long companyId, Long leaveCycleId) {
        BigDecimal sum = mapper.selectLedgerSum(companyId, leaveCycleId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    public List<LeaveCycleDTO> selectCompanyCyclesOn(Long companyId, String day) {
        return mapper.selectCompanyCyclesOn(companyId, day);
    }

    public List<LeaveSlotDTO> selectTakenSlots(Long companyId, Long employeeId, List<String> days) {
        return days.isEmpty() ? List.of() : mapper.selectTakenSlots(companyId, employeeId, days);
    }

    public int insertSlot(LeaveSlotDTO slot) {
        return mapper.insertSlot(slot);
    }

    public int insertLedgerUse(Long companyId, Long leaveCycleId, Long leaveRequestDayId, BigDecimal deltaDays, Long actorAccountId) {
        return mapper.insertLedgerUse(companyId, leaveCycleId, leaveRequestDayId, deltaDays, actorAccountId);
    }

    public BigDecimal selectPendingDays(Long companyId, Long employeeId, String startsOn, String endsOn) {
        BigDecimal days = mapper.selectPendingDays(companyId, employeeId, startsOn, endsOn);
        return days == null ? BigDecimal.ZERO : days;
    }

    public List<LeaveRequestDTO> selectRequests(Long companyId, Long employeeId, String startsOn, String endsOn) {
        return mapper.selectRequests(companyId, employeeId, startsOn, endsOn);
    }

    public List<ApprovalStepDTO> selectSteps(Long companyId, List<Long> documentIds) {
        return mapper.selectSteps(companyId, documentIds);
    }
}
