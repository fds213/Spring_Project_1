package com.ieum.gw.ticket.service;

import org.springframework.stereotype.Service;

/**
 * 티켓이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 */
@Service
public class TicketService {

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
     */
    public void createEquipmentTicket(Long companyId, Long requesterEmployeeId, Long approvalDocumentId,
                                      Long branchId, String assetType, String purpose) {
        // TODO(정상욱): 구현. 구현 전에는 아무것도 하지 않는다(티켓만 생기지 않는다).
    }
}
