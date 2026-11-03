package com.ieum.gw.notification.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.ieum.gw.notification.dao.NotificationDAO;
import com.ieum.gw.notification.dto.NotificationPageDTO;
import com.ieum.gw.notification.dto.NotificationSaveDTO;
import com.ieum.gw.notification.dto.NotificationType;

import lombok.RequiredArgsConstructor;

/**
 * 알림이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 * 알림은 DB에만 저장하고 화면이 15초마다 조회한다(실시간 푸시는 추가 기능, 계획서 7장). 그래서 업무 트랜잭션 안에서 부른다.
 * 저장 메서드에는 @Transactional을 따로 붙이지 않는다: 부른 쪽의 트랜잭션에 그대로 들어가, 업무가 취소되면 알림도 같이 취소된다.
 * 받는 사람은 사원 ID로 받고 계정 ID로 바꿔 저장하는 일은 알림이 한다. 그 사원의 계정이 없으면 받을 곳이 없으니 로그만 남기고 업무는 이어 간다.
 * 결재 알림은 같은 사람에게 같은 문서·같은 종류로 두 번 들어와도 하나만 저장한다. 티켓 알림은 재배정·재요청으로
 * 같은 인자가 다시 올 수 있어 부를 때마다 저장한다(같은 처리가 두 번 되는 것은 티켓의 version 검사가 막는다).
 * 그 밖의 저장 오류(없는 문서·티켓 번호 등)는 그대로 던져 업무 트랜잭션을 취소한다(계획서 5장).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    static final int PAGE_SIZE = 20;

    private final NotificationDAO notificationDAO;

    /** 결재 요청(NT_APPROVAL_REQUEST). 상신 때 1단계 결재자에게, 1단계 승인 뒤 2단계 결재자에게 부른다. */
    public void notifyApprovalRequest(Long companyId, Long receiverEmployeeId, Long approvalDocumentId, String documentTitle) {
        saveApproval(NotificationType.NT_APPROVAL_REQUEST, companyId, receiverEmployeeId, approvalDocumentId, documentTitle);
    }

    /** 결재 완료(NT_APPROVAL_DONE). 최종 승인 때 기안자에게 부른다. */
    public void notifyApprovalDone(Long companyId, Long receiverEmployeeId, Long approvalDocumentId, String documentTitle) {
        saveApproval(NotificationType.NT_APPROVAL_DONE, companyId, receiverEmployeeId, approvalDocumentId, documentTitle);
    }

    /** 결재 반려(NT_APPROVAL_REJECT). 반려 때 기안자에게 부른다. */
    public void notifyApprovalRejected(Long companyId, Long receiverEmployeeId, Long approvalDocumentId, String documentTitle) {
        saveApproval(NotificationType.NT_APPROVAL_REJECT, companyId, receiverEmployeeId, approvalDocumentId, documentTitle);
    }

    /** 티켓 배정(NT_TICKET_ASSIGNED). 배정 때 담당자에게 부른다. */
    public void notifyTicketAssigned(Long companyId, Long receiverEmployeeId, Long itTicketId, String ticketTitle) {
        saveTicket(NotificationType.NT_TICKET_ASSIGNED, companyId, receiverEmployeeId, itTicketId, ticketTitle);
    }

    /** 티켓 처리 완료(NT_TICKET_RESOLVED). 처리 완료 때 요청자에게(수령·조치 확인 요청) 부른다. */
    public void notifyTicketResolved(Long companyId, Long receiverEmployeeId, Long itTicketId, String ticketTitle) {
        saveTicket(NotificationType.NT_TICKET_RESOLVED, companyId, receiverEmployeeId, itTicketId, ticketTitle);
    }

    /** 안 읽은 알림 수(헤더 배지 'notification'). */
    public int countUnread(Long companyId, Long userAccountId) {
        return notificationDAO.countUnread(companyId, userAccountId);
    }

    /** 본인 알림 한 페이지(최신 순). page는 1 ~ 마지막 페이지 사이로 맞춘다. */
    public NotificationPageDTO list(Long companyId, Long userAccountId, int page) {
        int total = notificationDAO.count(companyId, userAccountId);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(1, page), pages);

        NotificationPageDTO result = new NotificationPageDTO();
        result.setPage(current);
        result.setPages(pages);
        result.setTotal(total);
        result.setItems(notificationDAO.list(companyId, userAccountId, (current - 1) * PAGE_SIZE, PAGE_SIZE));
        return result;
    }

    /**
     * 본인 알림을 읽음으로 표시한다. 이미 읽은 알림은 성공으로 치고 처음 읽은 시각을 그대로 둔다(두 번 눌러도 된다).
     * 없는 알림이나 남의 알림이면 NotificationNotFoundException.
     */
    public void markRead(Long companyId, Long userAccountId, Long notificationId) {
        if (notificationDAO.markRead(companyId, userAccountId, notificationId) == 0
                && !notificationDAO.isOwned(companyId, userAccountId, notificationId)) {
            throw new NotificationNotFoundException();
        }
    }

    private void saveApproval(NotificationType type, Long companyId, Long receiverEmployeeId,
                              Long approvalDocumentId, String documentTitle) {
        NotificationSaveDTO dto = newRow(type, companyId, documentTitle);
        dto.setApprovalDocumentId(approvalDocumentId);
        dto.setDedupeKey(type.name() + ":" + approvalDocumentId);
        save(dto, receiverEmployeeId);
    }

    private void saveTicket(NotificationType type, Long companyId, Long receiverEmployeeId,
                            Long itTicketId, String ticketTitle) {
        NotificationSaveDTO dto = newRow(type, companyId, ticketTitle);
        dto.setItTicketId(itTicketId);
        dto.setDedupeKey(type.name() + ":" + itTicketId + ":" + UUID.randomUUID()); // 부를 때마다 새 행
        save(dto, receiverEmployeeId);
    }

    private NotificationSaveDTO newRow(NotificationType type, Long companyId, String targetTitle) {
        NotificationSaveDTO dto = new NotificationSaveDTO();
        dto.setCompanyId(companyId);
        dto.setNotificationType(type.name());
        dto.setTitle(type.getTitle());
        dto.setMessage(type.messageFor(targetTitle));
        return dto;
    }

    private void save(NotificationSaveDTO dto, Long receiverEmployeeId) {
        Long accountId = notificationDAO.findAccountId(dto.getCompanyId(), receiverEmployeeId);
        if (accountId == null) {
            logger.warn("알림을 저장하지 않음(받는 계정 없음): companyId={}, employeeId={}, type={}",
                    dto.getCompanyId(), receiverEmployeeId, dto.getNotificationType());
            return;
        }
        dto.setReceiverAccountId(accountId);
        try {
            notificationDAO.insert(dto);
        } catch (DuplicateKeyException e) {
            // 같은 결재 알림이 이미 있다. Oracle은 이 문장만 되돌리므로 부른 쪽의 트랜잭션은 그대로 이어진다
            logger.debug("이미 있는 알림: key={}, accountId={}", dto.getDedupeKey(), accountId);
        }
    }
}
