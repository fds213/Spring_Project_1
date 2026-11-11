package com.ieum.gw.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dao.TicketDAO;
import com.ieum.gw.dto.EquipmentTicketDTO;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Objects;

/**
 * 티켓이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketDAO dao;
    private final BranchService branchService;

    /**
     * 장비 신청 최종 승인 때 IT 지급 티켓(TK_EQUIPMENT, RECEIVED) 1건을 만든다(계획서 흐름 B).
     * 결재 최종 승인 트랜잭션 안에서 부른다. EQUIPMENT_DOCUMENT_ID 고유 제약이 있어 같은 문서로 두 번 만들어지지 않는다.
     * 티켓에는 지급에 필요한 정보만 넣고 결재 첨부는 넘기지 않는다(계획서 5장 '결재').
     *
     * @param requesterEmployeeId 기안자
     * @param approvalDocumentId  장비 신청 결재 문서 ID
     * @param branchId            수령 지점
     * @param assetType           장비 종류 코드(AT_LAPTOP·AT_DESKTOP·AT_MONITOR·AT_OTHER)
     * @param purpose             사용 목적
     * @return 신규 생성했거나 정보 일치를 확인한 기존 지급 티켓 ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long createEquipmentTicket(Long companyId, Long requesterEmployeeId, Long approvalDocumentId,
                                      Long branchId, String assetType, String purpose) {
        requireId(companyId);
        requireId(requesterEmployeeId);
        requireId(approvalDocumentId);
        requireId(branchId);
        
        if (assetType == null || !List.of("AT_LAPTOP", "AT_DESKTOP", "AT_MONITOR", "AT_OTHER").contains(assetType)) {
            throw new IllegalArgumentException("장비 종류가 올바르지 않습니다.");
        }
        
        if (purpose == null || purpose.isBlank() || purpose.codePointCount(0, purpose.length()) > 1000) {
            throw new IllegalArgumentException("사용 목적은 1~1,000자로 입력해 주세요.");
        }
        
        // 회사·요청자·지점은 결재 Service가 검증한 값이며, 업체별 복합 FK로도 검사한다.
        // 원본 장비 문서 고유 제약에 해당하는 중복만 SQL에서 건너뛴다.
        boolean branchActive = branchService.lockActiveBranchForEquipmentTicket(companyId, branchId);
        int inserted = branchActive ? dao.insertEquipmentTicket(companyId, requesterEmployeeId, approvalDocumentId, branchId, purpose) : 0;
        
        if (inserted == 1 || inserted == 0) {
			EquipmentTicketDTO ticket = dao.selectEquipmentTicket(companyId, approvalDocumentId);
			
			if (!branchActive && ticket == null) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "수령 지점이 사용 중지되었거나 존재하지 않습니다.");
			}
			
			if (ticket != null 
					&& ticket.getItTicketId() != null
					&& ticket.getItTicketId() > 0
					&& Objects.equals(requesterEmployeeId, ticket.getRequesterEmployeeId())
					&& Objects.equals(branchId, ticket.getBranchId())
					&& Objects.equals(purpose, ticket.getContent())) {
				
				return ticket.getItTicketId();
			}
		}
        
        throw new IllegalStateException("같은 결재 문서의 지급 티켓 정보를 확인해 주세요.");
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("업체·사원·결재 문서·지점 정보가 필요합니다.");
        }
    }
}