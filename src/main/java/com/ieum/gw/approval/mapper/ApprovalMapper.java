package com.ieum.gw.approval.mapper;

import com.ieum.gw.approval.dto.ApprovalDocumentDTO;
import com.ieum.gw.approval.dto.ApprovalEquipmentDTO;
import com.ieum.gw.approval.dto.ApprovedEquipmentDTO;
import com.ieum.gw.approval.dto.ApprovalHistoryDTO;
import com.ieum.gw.approval.dto.ApprovalLeaveDTO;
import com.ieum.gw.approval.dto.ApprovalStepDTO;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ApprovalMapper {
    BigDecimal sumPendingLeaveDays(Long companyId, Long employeeId);

    List<LeaveDayDTO> selectPendingLeaveDays(Long companyId, Long employeeId, String fromDate, String toDate);

    void insertDocument(ApprovalDocumentDTO document);

    void insertStep(ApprovalStepDTO stepDto);

    void insertHistory(ApprovalHistoryDTO history);

    void insertLeaveRequest(ApprovalLeaveDTO leave);

    void insertLeaveDay(Long companyId, @Param("approvalDocumentId") Long documentId, LeaveDayDTO day);

    void insertEquipmentRequest(ApprovalEquipmentDTO equipment);

    ApprovedEquipmentDTO selectApprovedEquipment(Long companyId, Long approvalDocumentId);
}
