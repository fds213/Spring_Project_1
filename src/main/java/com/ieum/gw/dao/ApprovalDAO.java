package com.ieum.gw.dao;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.ApprovalBoxDTO;
import com.ieum.gw.dto.ApprovalBoxHistoryDTO;
import com.ieum.gw.dto.ApprovalBoxStepDTO;
import com.ieum.gw.dto.ApprovalDocumentDTO;
import com.ieum.gw.dto.ApprovalEquipmentDTO;
import com.ieum.gw.dto.ApprovalFileDTO;
import com.ieum.gw.dto.ApprovalHistoryDTO;
import com.ieum.gw.dto.ApprovalLeaveDTO;
import com.ieum.gw.dto.ApprovalStepDTO;
import com.ieum.gw.dto.ApprovedEquipmentDTO;
import com.ieum.gw.dto.LeaveDayDTO;
import com.ieum.gw.mapper.ApprovalMapper;

import lombok.RequiredArgsConstructor;

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

    /** 임시저장 문서를 새로 만든다 */
    public void insertDraft(ApprovalDocumentDTO document) {
        mapper.insertDraft(document);
    }

    /** 임시저장 문서를 고친다. DRAFT가 아니면 0 */
    public int updateDraft(ApprovalDocumentDTO document) {
        return mapper.updateDraft(document);
    }

    /** 임시저장 문서를 제출 상태로 바꾼다. DRAFT가 아니면 0 */
    public int submitDraft(ApprovalDocumentDTO document) {
        return mapper.submitDraft(document);
    }

    /** 내 임시저장 문서를 지운다. 내 문서가 아니거나 DRAFT가 아니면 0 */
    public int deleteDraft(Long companyId, Long employeeId, long documentId) {
        return mapper.deleteDraft(companyId, employeeId, documentId);
    }

    /** 내 임시저장 문서 목록 (최근에 저장한 순) */
    public List<ApprovalBoxDTO> selectDraftDocuments(Long companyId, Long employeeId) {
        return mapper.selectDraftDocuments(companyId, employeeId);
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

    /** 원본 문서로 이미 만든 재기안 문서 수를 센다 */
    public int countRedrafts(Long companyId, Long originalDocumentId) {
        return mapper.countRedrafts(companyId, originalDocumentId);
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

    public int rejectStep(Long companyId, Long employeeId, Integer stepNo, long documentId, String opinion) {
        return mapper.rejectStep(companyId, employeeId, stepNo, documentId, opinion);
    }

    public int rejectDocument(Long companyId, long documentId) {
        return mapper.rejectDocument(companyId, documentId);
    }


    public List<LeaveDayDTO> selectLeaveDays(Long companyId, long documentId) {
        return mapper.selectLeaveDays(companyId, documentId);
    }

    public ApprovalEquipmentDTO selectEquipmentRequest(Long companyId, long documentId) {
        return mapper.selectEquipmentRequest(companyId, documentId);
    }

    public List<Long> selectWaitingDocumentIds(Long companyId, Long employeeId) {
        return mapper.selectWaitingDocumentIds(companyId, employeeId);
    }
    
    // 결재 문서에 첨부 붙이기
    public int insertFile(Long companyId, Long approvalDocumentId, Long attachmentId) {
        return mapper.insertFile(companyId, approvalDocumentId, attachmentId);
    }
    
    // 결재함에 보이는 문서들의 첨부
    public List<ApprovalFileDTO> selectBoxFiles(Long companyId, Long employeeId) {
        return mapper.selectBoxFiles(companyId, employeeId);
    }

    // 결재함에 보이는 문서의 첨부인지 (1이면 내려받기 가능)
    public int countBoxFile(Long companyId, Long employeeId, Long documentId, Long attachmentId) {
        return mapper.countBoxFile(companyId, employeeId, documentId, attachmentId);
    }
}
