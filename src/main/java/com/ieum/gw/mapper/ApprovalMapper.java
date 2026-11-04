package com.ieum.gw.mapper;

import com.ieum.gw.dto.ApprovalBoxDTO;
import com.ieum.gw.dto.ApprovalBoxHistoryDTO;
import com.ieum.gw.dto.ApprovalBoxStepDTO;
import com.ieum.gw.dto.ApprovalDocumentDTO;
import com.ieum.gw.dto.ApprovalEquipmentDTO;
import com.ieum.gw.dto.ApprovedEquipmentDTO;
import com.ieum.gw.dto.ApprovalHistoryDTO;
import com.ieum.gw.dto.ApprovalLeaveDTO;
import com.ieum.gw.dto.ApprovalStepDTO;
import com.ieum.gw.dto.LeaveDayDTO;
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

    List<ApprovalBoxDTO> selectBoxDocuments(Long companyId, Long employeeId);

    List<ApprovalBoxStepDTO> selectBoxSteps(Long companyId, Long employeeId);

    List<ApprovalBoxHistoryDTO> selectBoxHistory(Long companyId, Long employeeId);

    int countWaitingForMe(Long companyId, Long employeeId);

    ApprovalDocumentDTO lockDocument(Long companyId, long documentId);

    List<ApprovalStepDTO> selectDocumentSteps(Long companyId, long documentId);

    int approveStep(Long companyId, Long employeeId, Integer stepNo, long documentId, String opinion);

    int approveDocument(Long companyId, long documentId);
}
