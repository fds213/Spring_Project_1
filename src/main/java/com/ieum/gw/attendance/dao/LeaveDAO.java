package com.ieum.gw.attendance.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.attendance.dto.ApprovalStepDTO;
import com.ieum.gw.attendance.dto.EmployeeCareerDTO;
import com.ieum.gw.attendance.dto.LeaveCycleDTO;
import com.ieum.gw.attendance.dto.LeaveRequestDTO;
import com.ieum.gw.attendance.dto.LeaveUsageDTO;
import com.ieum.gw.attendance.mapper.LeaveMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class LeaveDAO {
    private final LeaveMapper mapper;

    public EmployeeCareerDTO selectEmployeeCareer(Long companyId, Long employeeId) {
        return mapper.selectEmployeeCareer(companyId, employeeId);
    }

    public LeaveCycleDTO selectCurrentCycle(Long companyId, Long employeeId, String today) {
        return mapper.selectCurrentCycle(companyId, employeeId, today);
    }

    public LeaveUsageDTO selectUsage(Long companyId, Long employeeId, String startsOn, String endsOn) {
        return mapper.selectUsage(companyId, employeeId, startsOn, endsOn);
    }

    public List<LeaveRequestDTO> selectRequests(Long companyId, Long employeeId, String startsOn, String endsOn) {
        return mapper.selectRequests(companyId, employeeId, startsOn, endsOn);
    }

    public List<ApprovalStepDTO> selectSteps(Long companyId, List<Long> documentIds) {
        return mapper.selectSteps(companyId, documentIds);
    }
}
