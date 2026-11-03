package com.ieum.gw.approval.dao;

import com.ieum.gw.approval.dto.ApprovalBoxDTO;
import com.ieum.gw.approval.dto.ApprovalBoxHistoryDTO;
import com.ieum.gw.approval.dto.ApprovalBoxStepDTO;
import com.ieum.gw.approval.dto.ApprovalDocumentDTO;
import com.ieum.gw.approval.dto.ApprovalEquipmentDTO;
import com.ieum.gw.approval.dto.ApprovedEquipmentDTO;
import com.ieum.gw.approval.dto.ApprovalHistoryDTO;
import com.ieum.gw.approval.dto.ApprovalLeaveDTO;
import com.ieum.gw.approval.dto.ApprovalStepDTO;
import com.ieum.gw.approval.mapper.ApprovalMapper;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
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
}
