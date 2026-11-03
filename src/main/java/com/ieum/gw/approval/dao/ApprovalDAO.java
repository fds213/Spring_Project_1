package com.ieum.gw.approval.dao;

import com.ieum.gw.approval.mapper.ApprovalMapper;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ApprovalDAO {
    private final ApprovalMapper mapper;

    public BigDecimal sumPendingLeaveDays(Long companyId, Long employeeId) {
        return mapper.sumPendingLeaveDays(companyId, employeeId);
    }

    public List<LeaveDayDTO> selectPendingLeaveDays(Long companyId, Long employeeId, String fromDate, String toDate) {
        return mapper.selectPendingLeaveDays(companyId, employeeId, fromDate, toDate);
    }
}
