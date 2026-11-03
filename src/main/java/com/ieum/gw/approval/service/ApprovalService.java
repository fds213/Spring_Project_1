package com.ieum.gw.approval.service;

import com.ieum.gw.approval.dao.ApprovalDAO;
import com.ieum.gw.approval.dto.*;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
import com.ieum.gw.attendance.service.AttendanceService;
import com.ieum.gw.common.code.AssetType;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.employee.service.EmployeeService;
import com.ieum.gw.location.service.BranchService;
import com.ieum.gw.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 결재가 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {
    // 장비 신청서에서 고를 수 있는 장비 종류: 공통 코드 ASSET_TYPE 중 사용 중(USE_YN = 'Y')인 것.
    // 티켓 createEquipmentTicket의 허용 목록과 같게 둔다(다르면 최종 승인 때 티켓 생성이 실패한다)
    private static final List<AssetType> EQUIPMENT_KINDS =
            List.of(AssetType.AT_LAPTOP, AssetType.AT_DESKTOP, AssetType.AT_MONITOR, AssetType.AT_OTHER);
    private static final int EQUIPMENT_QUANTITY = 1; // 계획서 3장 B: 수량 1개
    private static final int PURPOSE_MAX = 1000;     // EQUIPMENT_REQUEST.PURPOSE VARCHAR2(1000 CHAR)

    private final EmployeeService eService;
    private final AttendanceService aService;
    private final ApprovalDAO dao;
    private final BranchService bService;
    private final NotificationService nService;

    private List<Long> parseLine(List<String> line) {

        if(line == null || line.isEmpty()) throw new IllegalArgumentException("1단계 결재자를 골라주세요.");

        return line.stream().filter(StringUtils::hasText).map(Long::valueOf).collect(Collectors.toList());
    }

    private boolean overlaps(LeaveDayDTO day, LeaveDayDTO p) {
        return day.getLeaveDate().equals(p.getLeaveDate()) && ("LV_FULL".equals(day.getLeaveType())|| "LV_FULL".equals(p.getLeaveType()) || day.getLeaveType().equals(p.getLeaveType()));
    }

    public Map<String, Object> getFormOptions(Long companyId, Long employeeId) {
        List<ApproverDTO> approvers = eService.listActiveEmp(companyId, employeeId).stream()
                .map(e -> new ApproverDTO(e.getEmployeeId(), e.getEmployeeName(), e.getDepartmentName(), e.getGradeName()))
                .collect(Collectors.toList());

        BigDecimal remain = aService.getRemainingLeaveDays(companyId, employeeId);
        BigDecimal pending = dao.sumPendingLeaveDays(companyId, employeeId);
        Map<String, BigDecimal> leave = new HashMap<>();
        leave.put("available", remain.subtract(pending));
        leave.put("pendingDays", pending);

        // 화면(approval.js)은 장비 종류를 { code, codeName }으로 읽는다
        List<Map<String, String>> equipKinds = EQUIPMENT_KINDS.stream()
                .map(t -> Map.of("code", t.name(), "codeName", t.getLabel()))
                .collect(Collectors.toList());

        Map<String, Object> options = new HashMap<>();
        options.put("approvers", approvers);
        options.put("branches", bService.getBranches(companyId)); // 사용 중인 우리 업체 지점 (branchId, branchName)
        options.put("equipKinds", equipKinds);
        options.put("leave", leave);

        return options;
    }


    /**
     * 내가 지금 처리할 차례인 결재 문서 수(헤더 배지 'approval'). 결재함 '내 순번 대기' 목록과 같은 조건으로 센다.
     */
    public int countWaitingForMe(Long companyId, Long employeeId) {
        // TODO(서장호): 구현. 구현 전에는 0을 돌려준다.
        return 0;
    }

    /**
     * 결재 완료(APPROVED)된 장비 신청의 지급 정보(장비 종류·수량·사용 목적). 티켓의 지급 대기 목록·관리자 상세가 부른다.
     * 문서 내용·결재 첨부는 넘기지 않는다. 결재 중·반려 문서, 휴가 문서, 다른 업체 문서면 null.
     *
     * @param companyId 부르는 쪽이 로그인 사용자 정보에서 꺼낸 업체 ID
     */
    @Transactional(readOnly = true)
    public ApprovedEquipmentDTO getApprovedEquipmentInfo(Long companyId, Long approvalDocumentId) {
        if(companyId == null || approvalDocumentId == null) return null;

        return dao.selectApprovedEquipment(companyId, approvalDocumentId);
    }

    @Transactional
    public Long submit(LoginUser user, ApprovalSubmitDTO dto) {

        // 양식 확인
        boolean isLeave = "DOC_LEAVE".equals(dto.getDocumentType());
        if(!isLeave && !"DOC_EQUIPMENT".equals(dto.getDocumentType())) {
            throw new IllegalArgumentException("지원하지 않는 양식");
        }

        // 결재자 검사
        List<Long> approverIds = parseLine(dto.getLine());

        if(approverIds.isEmpty()) {
            throw new IllegalArgumentException("1단계 결재자를 골라주세요.");
        } else if(approverIds.size() > 2) {
            throw new IllegalArgumentException("결재자는 최대 2명입니다.");
        }

        if(approverIds.contains(user.getEmployeeId())) throw new IllegalArgumentException("자신은 결재자로 선택할 수 없습니다.");
        if(approverIds.size() == 2 && approverIds.get(0).equals(approverIds.get(1))) throw new IllegalArgumentException("결재자는 모두 다른 사람이어야 합니다.");

        for (Long id : approverIds) {
            if(!eService.isActiveEmployee(user.getCompanyId(), id)) throw new IllegalArgumentException("재직 중인 사람만 결재자로 지정할 수 있습니다.");
        }

        ApprovalFieldsDTO fields = dto.getFields();
        if(fields == null) {
            throw new IllegalArgumentException("신청 정보를 입력하세요.");
        }

        // 양식마다 검사와 상세 저장이 다르다
        return isLeave ? submitLeave(user, approverIds, fields) : submitEquipment(user, approverIds, fields);
    }

    // 휴가 신청서: 날짜 계산·연차·겹침을 검사하고 휴가 상세·날짜를 저장한다
    private Long submitLeave(LoginUser user, List<Long> approverIds, ApprovalFieldsDTO fields) {

        // 휴가 입력값 검사
        if(!StringUtils.hasText(fields.getReason())) throw new IllegalArgumentException("사유를 입력하세요.");
        if(fields.getReason().length() > 1000) throw new IllegalArgumentException("사유는 1000자까지 입력할 수 있습니다.");

        if(!StringUtils.hasText(fields.getFromDate()) || !StringUtils.hasText(fields.getToDate())) throw new IllegalArgumentException("휴가 기간이 올바르지 않습니다.");

        LocalDate from, to;
        try {
            from = LocalDate.parse(fields.getFromDate());
            to = LocalDate.parse(fields.getToDate());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("휴가 기간이 올바르지 않습니다.");
        }

        List<LeaveDayDTO> leaveDays = aService.calculateLeaveDays(user.getCompanyId(), fields.getLeaveType(), from, to);

        if(leaveDays.isEmpty()) throw new IllegalArgumentException("휴가로 쓸 날이 없습니다.");

        BigDecimal total = BigDecimal.ZERO;

        for(LeaveDayDTO day : leaveDays) {
            total = total.add(day.getDeductDays());
        }

        // 연차확인
        BigDecimal remain = aService.getRemainingLeaveDays(user.getCompanyId(), user.getEmployeeId());
        BigDecimal pending = dao.sumPendingLeaveDays(user.getCompanyId(), user.getEmployeeId());
        BigDecimal available = remain.subtract(pending);
        List<LeaveDayDTO> pendingDays = dao.selectPendingLeaveDays(user.getCompanyId(), user.getEmployeeId(), from.toString(), to.toString());


        if(available.compareTo(total) < 0) throw new IllegalArgumentException("남은 연차가 부족합니다. " + "(남은 연차 " + available.stripTrailingZeros().toPlainString() + "일)");

        if(aService.hasApprovedLeaveOverlap(user.getCompanyId(), user.getEmployeeId(), leaveDays)) throw new IllegalArgumentException("이미 승인된 휴가와 겹칩니다.");

        for(LeaveDayDTO day : leaveDays) {
            for(LeaveDayDTO p : pendingDays) {
                if(overlaps(day,p)) throw new IllegalArgumentException("결재 중인 휴가와 겹칩니다.");
            }
        }


        // DB 저장
        Long documentId = saveDocument(user, "DOC_LEAVE", "휴가 신청서 - " + from + " ~ " + to, fields.getReason(), approverIds);

        ApprovalLeaveDTO leave = new ApprovalLeaveDTO();
        leave.setCompanyId(user.getCompanyId());
        leave.setApprovalDocumentId(documentId);
        leave.setReason(fields.getReason());
        leave.setRequestedDays(total);
        dao.insertLeaveRequest(leave);

        for (LeaveDayDTO day :leaveDays) {
            dao.insertLeaveDay(user.getCompanyId(), documentId, day);
        }

        return documentId;
    }

    // 장비 지급 신청서: 장비 종류·수량·수령 지점·사용 목적을 검사하고 장비 상세를 저장한다.
    // 지급 티켓은 최종 승인 때 만든다(계획서 3장 B)
    private Long submitEquipment(LoginUser user, List<Long> approverIds, ApprovalFieldsDTO fields) {

        AssetType assetType = EQUIPMENT_KINDS.stream()
                .filter(t -> t.name().equals(fields.getAssetType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("장비 종류가 올바르지 않습니다."));

        if(!Integer.valueOf(EQUIPMENT_QUANTITY).equals(fields.getQuantity())) throw new IllegalArgumentException("장비는 한 번에 1개만 신청할 수 있습니다.");

        Long branchId = parseBranchId(user.getCompanyId(), fields.getBranchId());

        // 티켓도 같은 방식(글자 수)으로 1~1,000자를 검사한다
        String purpose = fields.getPurpose();
        if(!StringUtils.hasText(purpose)) throw new IllegalArgumentException("사용 목적을 입력하세요.");
        if(purpose.codePointCount(0, purpose.length()) > PURPOSE_MAX) throw new IllegalArgumentException("사용 목적은 1000자까지 입력할 수 있습니다.");


        // DB 저장
        Long documentId = saveDocument(user, "DOC_EQUIPMENT", "장비 지급 신청서 - " + assetType.getLabel(), purpose, approverIds);

        ApprovalEquipmentDTO equipment = new ApprovalEquipmentDTO();
        equipment.setApprovalDocumentId(documentId);
        equipment.setCompanyId(user.getCompanyId());
        equipment.setBranchId(branchId);
        equipment.setAssetType(assetType.name());
        equipment.setQuantity(EQUIPMENT_QUANTITY);
        equipment.setPurpose(purpose);
        dao.insertEquipmentRequest(equipment);

        return documentId;
    }

    // 수령 지점: 숫자여야 하고, 우리 업체의 사용 중인 지점이어야 한다(다른 업체 지점 번호를 보내도 막는다)
    private Long parseBranchId(Long companyId, String value) {
        Long branchId;
        try {
            branchId = Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("수령 지점을 골라 주세요.");
        }

        boolean usable = bService.getBranches(companyId).stream().anyMatch(b -> branchId.equals(b.getBranchId()));
        if(!usable) throw new IllegalArgumentException("수령 지점을 다시 골라 주세요.");

        return branchId;
    }

    // 두 양식이 같이 쓰는 저장: 문서(결재 중) → 결재선(고른 순서대로 1·2단계) → 제출 이력. 새 문서 번호를 돌려준다
    private Long saveDocument(LoginUser user, String documentType, String title, String content, List<Long> approverIds) {
        ApprovalDocumentDTO document = new ApprovalDocumentDTO();
        document.setCompanyId(user.getCompanyId());
        document.setAuthorEmployeeId(user.getEmployeeId());
        document.setStatusCode("IN_PROGRESS"); // enum 들어오면 수정
        document.setDocumentType(documentType);
        document.setTitle(title);
        document.setContent(content);

        dao.insertDocument(document);

        Long documentId = document.getApprovalDocumentId();

        for(int i=0; i< approverIds.size(); i++) {
            ApprovalStepDTO stepDto = new ApprovalStepDTO();
            stepDto.setStepNo(i+1);
            stepDto.setApprovalDocumentId(documentId);
            stepDto.setCompanyId(user.getCompanyId());
            stepDto.setApproverEmployeeId(approverIds.get(i));

            dao.insertStep(stepDto);
        }

        ApprovalHistoryDTO history = new ApprovalHistoryDTO();
        history.setActionCode("APR_SUBMIT"); // enum 수정
        history.setAfterStatus("IN_PROGRESS");
        history.setActorAccountId(user.getUserAccountId());
        history.setApprovalDocumentId(documentId);
        history.setCompanyId(user.getCompanyId());
        dao.insertHistory(history);

        nService.notifyApprovalRequest(user.getCompanyId(), approverIds.get(0), documentId, title);


        return documentId;
    }
}
