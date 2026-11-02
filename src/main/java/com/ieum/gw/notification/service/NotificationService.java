package com.ieum.gw.notification.service;

import org.springframework.stereotype.Service;

/**
 * 알림이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 * 알림은 DB에만 저장하고 화면이 15초마다 조회한다(실시간 푸시는 추가 기능, 계획서 7장). 그래서 업무 트랜잭션 안에서 부른다.
 * 받는 사람은 사원 ID로 받고 계정 ID로 바꿔 저장하는 일은 알림이 한다.
 * 결재 알림은 같은 사람에게 같은 문서·같은 종류로 두 번 들어와도 하나만 저장한다. 티켓 알림은 재배정·재요청으로
 * 같은 인자가 다시 올 수 있어 부를 때마다 저장한다(같은 처리가 두 번 되는 것은 티켓의 version 검사가 막는다).
 */
@Service
public class NotificationService {

    /** 결재 요청(NT_APPROVAL_REQUEST). 상신 때 1단계 결재자에게, 1단계 승인 뒤 2단계 결재자에게 부른다. */
    public void notifyApprovalRequest(Long companyId, Long receiverEmployeeId, Long approvalDocumentId, String documentTitle) {
        // TODO(최준원): 구현. 구현 전에는 아무것도 하지 않는다.
    }

    /** 결재 완료(NT_APPROVAL_DONE). 최종 승인 때 기안자에게 부른다. */
    public void notifyApprovalDone(Long companyId, Long receiverEmployeeId, Long approvalDocumentId, String documentTitle) {
        // TODO(최준원): 구현. 구현 전에는 아무것도 하지 않는다.
    }

    /** 결재 반려(NT_APPROVAL_REJECT). 반려 때 기안자에게 부른다. */
    public void notifyApprovalRejected(Long companyId, Long receiverEmployeeId, Long approvalDocumentId, String documentTitle) {
        // TODO(최준원): 구현. 구현 전에는 아무것도 하지 않는다.
    }

    /** 티켓 배정(NT_TICKET_ASSIGNED). 배정 때 담당자에게 부른다. */
    public void notifyTicketAssigned(Long companyId, Long receiverEmployeeId, Long itTicketId, String ticketTitle) {
        // TODO(최준원): 구현. 구현 전에는 아무것도 하지 않는다.
    }

    /** 티켓 처리 완료(NT_TICKET_RESOLVED). 처리 완료 때 요청자에게(수령·조치 확인 요청) 부른다. */
    public void notifyTicketResolved(Long companyId, Long receiverEmployeeId, Long itTicketId, String ticketTitle) {
        // TODO(최준원): 구현. 구현 전에는 아무것도 하지 않는다.
    }

    /** 안 읽은 알림 수(헤더 배지 'notification'). */
    public int countUnread(Long companyId, Long userAccountId) {
        // TODO(최준원): 구현. 구현 전에는 0을 돌려준다.
        return 0;
    }
}
