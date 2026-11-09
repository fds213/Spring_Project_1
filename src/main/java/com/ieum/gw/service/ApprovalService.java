package com.ieum.gw.service;

import com.ieum.gw.dao.ApprovalDAO;
import com.ieum.gw.dto.*;
import com.ieum.gw.dto.LeaveDayDTO;
import com.ieum.gw.code.AssetType;
import com.ieum.gw.code.ApprovalAction;
import com.ieum.gw.code.ApprovalDocumentStatus;
import com.ieum.gw.code.ApprovalDocumentType;
import com.ieum.gw.code.ApprovalStepStatus;
import com.ieum.gw.code.LeaveType;
import com.ieum.gw.dto.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

// Dear programmer:
// When I wrote this code, only god and I knew how it worked.
// Now, only god knows it!
//
// Therefore, if u are trying to optimize this routine and fails,
// please increase this counter as a warning for the next person:
//
// total_hours_wasted_here = 1


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
    private final ScheduleService sService;
    private final TicketService tService;

    private List<Long> parseLine(List<String> line) {

        if(line == null || line.isEmpty()) throw new IllegalArgumentException("1단계 결재자를 골라주세요.");

        return line.stream().filter(StringUtils::hasText).map(this::parseApproverId).collect(Collectors.toList());
    }

    private Long parseApproverId(String id) {
        try {
            return Long.valueOf(id.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("결재자를 다시 골라 주세요.");
        }
    }

    @NonNull
    private static LoginUser getActor(Long companyId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "로그인 정보를 확인할 수 없습니다.");
        }
        LoginUser actor = (LoginUser) authentication.getPrincipal();
        if (!Objects.equals(companyId, actor.getCompanyId())
                || !("ROLE_ADMIN".equals(actor.getRole()) || "ROLE_HR".equals(actor.getRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "사원 비활성화 권한이 없습니다.");
        }
        return actor;
    }

    @Nullable
    private static String validateOpinion(String opinion) {

        if (opinion != null && opinion.codePointCount(0, opinion.length()) > 1000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "결재 의견은 1000자까지 입력할 수 있습니다."
            );
        }
        return opinion;
    }

    @NonNull
    private static ApprovalStepDTO requireCurrentApprover(LoginUser user, List<ApprovalStepDTO> steps) {
    /*
        로그인한 사원의 결재선이 있는지 확인
        내 결재선 상태가 WAITING인지 확인
        내 앞 단계가 모두 APPROVED인지 확인
    */

        // 내 결재선이 있는지?
        ApprovalStepDTO approver = null;
        for(ApprovalStepDTO step : steps) {
            if(Objects.equals(user.getEmployeeId(), step.getApproverEmployeeId())) {
                approver = step;
                break;
            }
        }

        if(approver == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 문서의 결재자가 아닙니다.");
        if(!ApprovalStepStatus.WAITING.name().equals(approver.getStatusCode())) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 결재입니다.");
        if(approver.getStepNo()==2) {
            if(!ApprovalStepStatus.APPROVED.name().equals(steps.get(0).getStatusCode())) throw new ResponseStatusException(HttpStatus.CONFLICT, "이전 결재가 완료되지 않았습니다.");
        }
        return approver;
    }

    @NonNull
    private ApprovalDocumentDTO lockInDocument(LoginUser user, long documentId) {
        ApprovalDocumentDTO doc;
        try {
            doc = dao.lockDocument(user.getCompanyId(), documentId);
        } catch (CannotAcquireLockException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "다른 요청에서 이 문서를 처리 중입니다. 잠시 후 다시 시도해 주세요.", e);
        }

        // 문서가 없으면 404
        if(doc==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회된 문서가 없습니다.");
        // 진행 중인 문서가 아니면 409
        if(!ApprovalDocumentStatus.IN_PROGRESS.name().equals(doc.getStatusCode())) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리되었거나 결재할 수 없는 문서입니다.");
        return doc;
    }

    @NonNull
    private ApprovalDecisionResultDTO createResponse(LoginUser user, ApprovalDocumentDTO doc, ApprovalHistoryDTO history) {
        ApprovalDecisionResultDTO response = new ApprovalDecisionResultDTO();

        String authorName = eService.getEmployeeNames(
                user.getCompanyId(),
                List.of(doc.getAuthorEmployeeId())
        ).get(doc.getAuthorEmployeeId());

        response.setEmployeeName(authorName);
        response.setDocumentType(doc.getDocumentType());
        response.setStatusCode(history.getAfterStatus());
        return response;
    }

    private boolean overlaps(LeaveDayDTO day, LeaveDayDTO p) {
        return day.getLeaveDate().equals(p.getLeaveDate()) && (LeaveType.LV_FULL.name().equals(day.getLeaveType())|| LeaveType.LV_FULL.name().equals(p.getLeaveType()) || day.getLeaveType().equals(p.getLeaveType()));
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
    @Transactional(readOnly = true)
    public int countWaitingForMe(Long companyId, Long employeeId) {
        return dao.countWaitingForMe(companyId, employeeId);
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
        boolean isLeave = ApprovalDocumentType.DOC_LEAVE.name().equals(dto.getDocumentType());
        if(!isLeave && !ApprovalDocumentType.DOC_EQUIPMENT.name().equals(dto.getDocumentType())) {
            throw new IllegalArgumentException("지원하지 않는 양식");
        }
        if (dto.getOriginalDocumentId() != null) {
            ApprovalDocumentDTO original;
            try {
                original = dao.lockDocument(user.getCompanyId(), dto.getOriginalDocumentId());
            } catch (CannotAcquireLockException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 요청에서 이 문서를 처리 중입니다. 잠시 후 다시 시도해 주세요.", e);
            }
            if (original == null || !Objects.equals(original.getAuthorEmployeeId(), user.getEmployeeId())
                    || !ApprovalDocumentStatus.REJECTED.name().equals(original.getStatusCode())
                    || !dto.getDocumentType().equals(original.getDocumentType())) {
                throw new IllegalArgumentException("재기안할 수 없는 문서입니다.");
            }
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
        return isLeave ? submitLeave(user, approverIds, fields, dto.getOriginalDocumentId()) : submitEquipment(user, approverIds, fields, dto.getOriginalDocumentId());
    }

    // 휴가 신청서: 날짜 계산·연차·겹침을 검사하고 휴가 상세·날짜를 저장한다
    private Long submitLeave(LoginUser user, List<Long> approverIds, ApprovalFieldsDTO fields, Long originalDocumentId) {

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

        aService.lockLeaveForSubmit(user.getCompanyId(), user.getEmployeeId());

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
        Long documentId = saveDocument(user, ApprovalDocumentType.DOC_LEAVE.name(), "휴가 신청서 - " + from + " ~ " + to, fields.getReason(), approverIds, originalDocumentId);

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
    private Long submitEquipment(LoginUser user, List<Long> approverIds, ApprovalFieldsDTO fields, Long originalDocumentId) {

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
        Long documentId = saveDocument(user, ApprovalDocumentType.DOC_EQUIPMENT.name(), "장비 지급 신청서 - " + assetType.getLabel(), purpose, approverIds, originalDocumentId);

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
    private Long saveDocument(LoginUser user, String documentType, String title, String content, List<Long> approverIds, Long originalDocumentId) {
        ApprovalDocumentDTO document = new ApprovalDocumentDTO();
        document.setCompanyId(user.getCompanyId());
        document.setAuthorEmployeeId(user.getEmployeeId());
        document.setOriginalDocumentId(originalDocumentId);
        document.setStatusCode(ApprovalDocumentStatus.IN_PROGRESS.name());
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
        history.setActionCode(ApprovalAction.APR_SUBMIT.name());
        history.setAfterStatus(ApprovalDocumentStatus.IN_PROGRESS.name());
        history.setActorAccountId(user.getUserAccountId());
        history.setApprovalDocumentId(documentId);
        history.setCompanyId(user.getCompanyId());
        dao.insertHistory(history);

        nService.notifyApprovalRequest(user.getCompanyId(), approverIds.get(0), documentId, title);


        return documentId;
    }



    @Transactional
    public int rejectWaitingForRetired(Long companyId, Long employeeId) {
        LoginUser actor = getActor(companyId);
        if (companyId == null || employeeId == null || actor.getUserAccountId() == null) {
            throw new IllegalArgumentException("업체·사원·처리 계정 정보가 필요합니다.");
        }

        List<Long> docs = dao.selectWaitingDocumentIds(companyId, employeeId);
        int rejectedCount = 0;
        String opinion = "결재자 계정 비활성화로 자동 반려되었습니다.";
        for (Long documentId : docs) {
            ApprovalDocumentDTO doc;
            try {
                doc = dao.lockDocument(companyId, documentId);
            } catch (CannotAcquireLockException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "다른 요청에서 이 문서를 처리 중입니다. 잠시 후 다시 시도해 주세요.", e);
            }
            // 후보 조회 이후 다른 결재 요청이 먼저 처리했을 수 있다.
            if (doc == null || !ApprovalDocumentStatus.IN_PROGRESS.name().equals(doc.getStatusCode())) {
                continue;
            }
            List<ApprovalStepDTO> steps = dao.selectDocumentSteps(companyId, documentId);
            ApprovalStepDTO target = steps.stream()
                    .filter(step -> Objects.equals(employeeId, step.getApproverEmployeeId()))
                    .findFirst().orElse(null);
            if (target == null || !ApprovalStepStatus.WAITING.name().equals(target.getStatusCode())) {
                continue;
            }
            boolean previousPending = steps.stream().anyMatch(step -> step.getStepNo() < target.getStepNo()
                    && !ApprovalStepStatus.APPROVED.name().equals(step.getStatusCode()));
            if (previousPending) continue;

            if (dao.rejectStep(companyId, employeeId, target.getStepNo(), documentId, opinion) != 1
                    || dao.rejectDocument(companyId, documentId) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "결재를 처리하지 못했습니다. 다시 시도하세요.");
            }
            ApprovalHistoryDTO history = new ApprovalHistoryDTO();
            history.setCompanyId(companyId);
            history.setApprovalDocumentId(documentId);
            history.setActorAccountId(actor.getUserAccountId());
            history.setActionCode(ApprovalAction.APR_REJECT.name());
            history.setBeforeStatus(ApprovalDocumentStatus.IN_PROGRESS.name());
            history.setAfterStatus(ApprovalDocumentStatus.REJECTED.name());
            history.setOpinion(opinion);
            dao.insertHistory(history);
            nService.notifyApprovalRejected(companyId, doc.getAuthorEmployeeId(), documentId, doc.getTitle());
            rejectedCount++;
        }
        return rejectedCount;
    }



    /**
     * 결재함: 내가 기안했거나 결재선에 있는 문서를 탭(WAIT·PROGRESS·DONE·REJECTED·DRAFT)별로 나눠 돌려준다.
     * 임시저장은 이번 범위가 아니라 DRAFT는 항상 빈 목록이다.
     * <ul>
     *   <li>WAIT(내 순번 대기): 진행 중인 문서 중 지금 순번이 나인 문서
     *       (내 단계가 WAITING이고 내 앞 단계가 모두 APPROVED)</li>
     *   <li>PROGRESS(진행): 진행 중인 문서 중 지금 순번이 내가 아니고, 내가 올렸거나 이미 승인한 문서.
     *       1차가 승인해 순번이 넘어간 문서는 사라지지 않고 여기에 남는다</li>
     *   <li>DONE·REJECTED: 완료·반려 문서 중 내가 올렸거나 처리한 문서</li>
     *   <li>2차 결재자는 차례가 오기 전에는 문서가 보이지 않는다(SQL의 boxRelated가 걸러 준다)</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Map<String, List<ApprovalBoxDTO>> box(LoginUser user) {
        Long companyId = user.getCompanyId();
        Long employeeId = user.getEmployeeId();

        Map<Long, List<ApprovalBoxStepDTO>> steps = dao.selectBoxSteps(companyId, employeeId).stream()
                .collect(Collectors.groupingBy(ApprovalBoxStepDTO::getApprovalDocumentId));
        Map<Long, List<ApprovalBoxHistoryDTO>> history = dao.selectBoxHistory(companyId, employeeId).stream()
                .collect(Collectors.groupingBy(ApprovalBoxHistoryDTO::getApprovalDocumentId));

        Map<String, List<ApprovalBoxDTO>> box = new LinkedHashMap<>();
        for (String tab : List.of("WAIT", "PROGRESS", "DONE", "REJECTED", "DRAFT")) {
            box.put(tab, new ArrayList<>());
        }

        for (ApprovalBoxDTO doc : dao.selectBoxDocuments(companyId, employeeId)) {
            Long id = doc.getApprovalDocumentId();
            doc.setSteps(steps.getOrDefault(id, List.of()));
            doc.setHistory(history.getOrDefault(id, List.of()));
            doc.setCanDecide(doc.isMyTurn());
            if (ApprovalDocumentStatus.REJECTED.name().equals(doc.getStatusCode())) {
                doc.setCanRedraft(Objects.equals(employeeId, doc.getAuthorEmployeeId()));
                doc.getHistory().stream()
                        .filter(h -> ApprovalAction.APR_REJECT.name().equals(h.getActionCode()))
                        .reduce((first, last) -> last)
                        .ifPresent(h -> doc.setRejectOpinion(h.getOpinion()));
            }
            if (doc.getFields() != null && doc.getFields().getAssetType() != null) {
                doc.setAssetTypeName(AssetType.from(doc.getFields().getAssetType()).getLabel());
            }

            if (doc.isMyTurn()) box.get("WAIT").add(doc);
            else if (ApprovalDocumentStatus.IN_PROGRESS.name().equals(doc.getStatusCode())) box.get("PROGRESS").add(doc);
            else if (ApprovalDocumentStatus.APPROVED.name().equals(doc.getStatusCode())) box.get("DONE").add(doc);
            else if (ApprovalDocumentStatus.REJECTED.name().equals(doc.getStatusCode())) box.get("REJECTED").add(doc);
        }
        return box;
    }

    @Transactional
    public ApprovalDecisionResultDTO approve(LoginUser user, ApprovalDecisionDTO dto, long documentId) {

        Long ticketId = null;
        ApprovalDocumentDTO doc = lockInDocument(user, documentId);
        List<ApprovalStepDTO> steps = dao.selectDocumentSteps(user.getCompanyId(), documentId);
        ApprovalStepDTO approver = requireCurrentApprover(user, steps);

        // 결재 처리
        String opinion = validateOpinion(dto.getOpinion());

        int result = dao.approveStep(user.getCompanyId(), user.getEmployeeId(), approver.getStepNo(),documentId, opinion);
        if(result!=1) throw new ResponseStatusException(HttpStatus.CONFLICT, "결재를 처리하지 못했습니다. 다시 시도하세요.");

        // 다음 결재자에게 알림, 최종 결재자면 문서 상태 변경
        if (approver.getStepNo() == 1 && steps.size() == 2) {
            nService.notifyApprovalRequest(
                    user.getCompanyId(),
                    steps.get(1).getApproverEmployeeId(),
                    documentId,
                    doc.getTitle()
            );
        } else {
            // 마지막 결재자: 문서를 APPROVED로 변경
            result = dao.approveDocument(user.getCompanyId(), documentId);
            if(result!=1) throw new ResponseStatusException(HttpStatus.CONFLICT, "결재를 처리하지 못했습니다. 다시 시도하세요.");

            if (ApprovalDocumentType.DOC_LEAVE.name().equals(doc.getDocumentType())) {
                List<LeaveDayDTO> days =
                        dao.selectLeaveDays(user.getCompanyId(), documentId);

                aService.useLeave(
                        user.getCompanyId(),
                        doc.getAuthorEmployeeId(),
                        user.getUserAccountId(),
                        days
                );

                sService.addLeaveEvents(
                        user.getCompanyId(),
                        doc.getAuthorEmployeeId(),
                        days
                );
            } else if (ApprovalDocumentType.DOC_EQUIPMENT.name().equals(doc.getDocumentType())) {
                ApprovalEquipmentDTO equipment = dao.selectEquipmentRequest(user.getCompanyId(), documentId);
                if (equipment == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "장비 신청 정보를 찾을 수 없습니다.");
                }
                ticketId = tService.createEquipmentTicket(
                        user.getCompanyId(),
                        doc.getAuthorEmployeeId(),
                        documentId,
                        equipment.getBranchId(),
                        equipment.getAssetType(),
                        equipment.getPurpose()
                );
            }

            nService.notifyApprovalDone(user.getCompanyId(), doc.getAuthorEmployeeId(), documentId, doc.getTitle());
        }

        // 이력 저장
        ApprovalHistoryDTO history = new ApprovalHistoryDTO();
        history.setActionCode(ApprovalAction.APR_APPROVE.name());
        history.setBeforeStatus(doc.getStatusCode());
        history.setAfterStatus(
                approver.getStepNo() == 1 && steps.size() == 2
                        ? ApprovalDocumentStatus.IN_PROGRESS.name()
                        : ApprovalDocumentStatus.APPROVED.name()
        );
        history.setOpinion(opinion);
        history.setActorAccountId(user.getUserAccountId());
        history.setApprovalDocumentId(documentId);
        history.setCompanyId(user.getCompanyId());
        dao.insertHistory(history);

        ApprovalDecisionResultDTO response = createResponse(user, doc, history);
        response.setTicketId(ticketId == null ? null : ticketId.toString());
        return response;
    }




    @Transactional
    public ApprovalDecisionResultDTO reject(LoginUser user, ApprovalDecisionDTO dto, long documentId) {

        // 행 잠금 슛
        ApprovalDocumentDTO doc = lockInDocument(user, documentId);
        List<ApprovalStepDTO> steps = dao.selectDocumentSteps(user.getCompanyId(), documentId);
        ApprovalStepDTO approver = requireCurrentApprover(user, steps);

        String opinion = validateOpinion(dto.getOpinion());

        if (!StringUtils.hasText(opinion)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "반려 사유를 입력해 주세요."
            );
        }

        int result = dao.rejectStep(user.getCompanyId(), user.getEmployeeId(), approver.getStepNo(),documentId, opinion);
        if(result!=1) throw new ResponseStatusException(HttpStatus.CONFLICT, "결재를 처리하지 못했습니다. 다시 시도하세요.");


        // 반려는 조건없이 REJECTED
        result = dao.rejectDocument(user.getCompanyId(), documentId);
        if(result!=1) throw new ResponseStatusException(HttpStatus.CONFLICT, "결재를 처리하지 못했습니다. 다시 시도하세요.");

        nService.notifyApprovalRejected(user.getCompanyId(), doc.getAuthorEmployeeId(), documentId, doc.getTitle());

        // 이력 저장
        ApprovalHistoryDTO history = new ApprovalHistoryDTO();
        history.setActionCode(ApprovalAction.APR_REJECT.name());
        history.setBeforeStatus(doc.getStatusCode());
        history.setAfterStatus(ApprovalDocumentStatus.REJECTED.name());
        history.setOpinion(opinion);
        history.setActorAccountId(user.getUserAccountId());
        history.setApprovalDocumentId(documentId);
        history.setCompanyId(user.getCompanyId());
        dao.insertHistory(history);

        return createResponse(user, doc, history);
    }
}
