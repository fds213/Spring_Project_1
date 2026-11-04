package com.ieum.gw.dao;

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
import com.ieum.gw.mapper.ApprovalMapper;
import lombok.RequiredArgsConstructor;
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

    public void insertDocument(ApprovalDocumentDTO document) {
        mapper.insertDocument(document);
    }

    public void insertStep(ApprovalStepDTO stepDto) {
        mapper.insertStep(stepDto);
    }

    public void insertHistory(ApprovalHistoryDTO history) {
        mapper.insertHistory(history);
    }

    public void insertLeaveRequest(ApprovalLeaveDTO leave) {
        mapper.insertLeaveRequest(leave);
    }

    public void insertLeaveDay(Long companyId, Long documentId, LeaveDayDTO day) {
        mapper.insertLeaveDay(companyId, documentId, day);
    }

    public void insertEquipmentRequest(ApprovalEquipmentDTO equipment) {
        mapper.insertEquipmentRequest(equipment);
    }

    public ApprovedEquipmentDTO selectApprovedEquipment(Long companyId, Long approvalDocumentId) {
        return mapper.selectApprovedEquipment(companyId, approvalDocumentId);
    }

    public List<ApprovalBoxDTO> selectBoxDocuments(Long companyId, Long employeeId) {
        return mapper.selectBoxDocuments(companyId, employeeId);
    }

    public List<ApprovalBoxStepDTO> selectBoxSteps(Long companyId, Long employeeId) {
        return mapper.selectBoxSteps(companyId, employeeId);
    }

    public List<ApprovalBoxHistoryDTO> selectBoxHistory(Long companyId, Long employeeId) {
        return mapper.selectBoxHistory(companyId, employeeId);
    }

    public int countWaitingForMe(Long companyId, Long employeeId) {
        return mapper.countWaitingForMe(companyId, employeeId);
    }

    public ApprovalDocumentDTO lockDocument(Long companyId, long documentId) {
        return mapper.lockDocument(companyId, documentId);
    }

    public List<ApprovalStepDTO> selectDocumentSteps(Long companyId, long documentId) {
        return mapper.selectDocumentSteps(companyId, documentId);
    }

    public int approveStep(Long companyId, Long employeeId, Integer stepNo, long documentId, String opinion) {
        return mapper.approveStep(companyId, employeeId, stepNo, documentId, opinion);
    }

    public int approveDocument(Long companyId, long documentId) {
        return mapper.approveDocument(companyId, documentId);
    }
}
