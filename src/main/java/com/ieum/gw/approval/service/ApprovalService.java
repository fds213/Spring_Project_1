package com.ieum.gw.approval.service;

import com.ieum.gw.approval.dto.ApprovalFieldsDTO;
import com.ieum.gw.approval.dto.ApprovalSubmitDTO;
import com.ieum.gw.approval.dto.ApproverDTO;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.employee.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final EmployeeService eService;

    private List<Long> parseLine(List<String> line) {

        if(line == null || line.isEmpty()) throw new IllegalArgumentException("1단계 결재자를 골라주세요.");

        return line.stream().filter(StringUtils::hasText).map(Long::valueOf).collect(Collectors.toList());
    }

    public Map<String, Object> getFormOptions(Long companyId, Long employeeId) {
        List<ApproverDTO> approvers = eService.listActiveEmp(companyId, employeeId).stream()
                .map(e -> new ApproverDTO(e.getEmployeeId(), e.getEmployeeName(), e.getDepartmentName(), e.getGradeName()))
                .collect(Collectors.toList());

        Map<String, Object> options = new HashMap<>();
        options.put("approvers", approvers);
        options.put("branches", List.of());   // 장비 양식 단계에서 채운다
        options.put("equipKinds", List.of()); // 장비 양식 단계에서 채운다

        return options;
    }


    /**
     * 내가 지금 처리할 차례인 결재 문서 수(헤더 배지 'approval'). 결재함 '내 순번 대기' 목록과 같은 조건으로 센다.
     */
    public int countWaitingForMe(Long companyId, Long employeeId) {
        // TODO(서장호): 구현. 구현 전에는 0을 돌려준다.
        return 0;
    }

    @Transactional
    public Long submit(LoginUser user, ApprovalSubmitDTO dto) {

        // 양식 확인
        if(!"DOC_LEAVE".equals(dto.getDocumentType()) /* && !"DOC_EQUIPMENT".equals(dto.getDocumentType()) */ ) {
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


        // 휴가 입력값 검사
        ApprovalFieldsDTO fields = dto.getFields();
        if(fields == null) {
            throw new IllegalArgumentException("휴가 정보를 입력하세요.");
        }

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
















        return null;
    }
}
