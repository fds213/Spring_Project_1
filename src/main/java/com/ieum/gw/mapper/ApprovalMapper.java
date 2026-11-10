package com.ieum.gw.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

@Mapper
public interface ApprovalMapper {
    BigDecimal sumPendingLeaveDays(Long companyId, Long employeeId);

    List<LeaveDayDTO> selectPendingLeaveDays(Long companyId, Long employeeId, String fromDate, String toDate);

    void insertDocument(ApprovalDocumentDTO document);

    // 임시저장 문서 새로 만들기
    void insertDraft(ApprovalDocumentDTO document);

    // 임시저장 문서 고치기 (DRAFT일 때만)
    int updateDraft(ApprovalDocumentDTO document);

    // 임시저장 문서를 제출 상태로 바꾸기 (DRAFT일 때만)
    int submitDraft(ApprovalDocumentDTO document);

    // 임시저장 문서 지우기 (내 문서이고 DRAFT일 때만)
    int deleteDraft(@Param("companyId") Long companyId,
                    @Param("employeeId") Long employeeId,
                    @Param("documentId") long documentId);

    // 내 임시저장 문서 목록 (최근에 저장한 순)
    List<ApprovalBoxDTO> selectDraftDocuments(@Param("companyId") Long companyId,
                                              @Param("employeeId") Long employeeId);

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

    int countRedrafts(Long companyId, Long originalDocumentId);

    List<ApprovalStepDTO> selectDocumentSteps(Long companyId, long documentId);

    int approveStep(Long companyId, Long employeeId, Integer stepNo, long documentId, String opinion);

    int approveDocument(Long companyId, long documentId);

    int rejectStep(Long companyId, Long employeeId, Integer stepNo, long documentId, String opinion);

    int rejectDocument(Long companyId, long documentId);

    List<LeaveDayDTO> selectLeaveDays(Long companyId, long documentId);

    ApprovalEquipmentDTO selectEquipmentRequest(@Param("companyId") Long companyId,
                                               @Param("documentId") long documentId);

    List<Long> selectWaitingDocumentIds(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    // 결재 문서에 첨부 붙이기
    int insertFile(@Param("companyId") Long companyId,
                   @Param("approvalDocumentId") Long approvalDocumentId,
                   @Param("attachmentId") Long attachmentId);
    
    // 결재함에 보이는 문서들의 첨부
    List<ApprovalFileDTO> selectBoxFiles(@Param("companyId") Long companyId,
                                         @Param("employeeId") Long employeeId);

    // 결재함에 보이는 문서의 첨부인지 (1이면 내려받기 가능)
    int countBoxFile(@Param("companyId") Long companyId,
                     @Param("employeeId") Long employeeId,
                     @Param("documentId") Long documentId,
                     @Param("attachmentId") Long attachmentId);
}
