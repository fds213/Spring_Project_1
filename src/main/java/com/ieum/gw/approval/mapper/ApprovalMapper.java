package com.ieum.gw.approval.mapper;

import com.ieum.gw.attendance.dto.LeaveDayDTO;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ApprovalMapper {
    BigDecimal sumPendingLeaveDays(Long companyId, Long employeeId);

    List<LeaveDayDTO> selectPendingLeaveDays(Long companyId, Long employeeId, String fromDate, String toDate);
}
