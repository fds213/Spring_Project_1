package com.ieum.gw.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.ieum.gw.dao.NotificationDAO;
import com.ieum.gw.dto.NotificationPageDTO;
import com.ieum.gw.dto.NotificationSaveDTO;
import com.ieum.gw.dto.NotificationType;
import com.ieum.gw.dto.NotificationUnreadCountDTO;

import lombok.RequiredArgsConstructor;

/**
 * 알림이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 * 알림은 DB에만 저장하고 화면이 15초마다 조회한다(실시간 푸시는 추가 기능, 계획서 7장). 그래서 업무 트랜잭션 안에서 부른다.
 * 저장 메서드에는 @Transactional을 따로 붙이지 않는다: 부른 쪽의 트랜잭션에 그대로 들어가, 업무가 취소되면 알림도 같이 취소된다.
 * 받는 사람은 사원 ID로 받고 계정 ID로 바꿔 저장하는 일은 알림이 한다. 그 사원의 계정이 없으면 받을 곳이 없으니 로그만 남기고 업무는 이어 간다.
 * 결재 알림은 같은 사람에게 같은 문서·같은 종류로 두 번 들어와도 하나만 저장한다. 티켓 알림은 재배정·재요청으로
 * 같은 인자가 다시 올 수 있어 부를 때마다 저장한다(같은 처리가 두 번 되는 것은 티켓의 version 검사가 막는다).
 * 근태 정정 알림은 결재처럼 같은 사람에게 같은 요청·같은 종류로 하나만 저장한다(요청은 한 번만 대기에 들어가고, 처리는 한 번뿐이다).
 * 그 밖의 저장 오류(없는 문서·티켓·정정 요청 번호 등)는 그대로 던져 업무 트랜잭션을 취소한다(계획서 5장).
 * 근태 정정 알림은 DB 패치(NOTIFICATION.ATTENDANCE_CORRECTION_ID 칸과 CK_69_01 교체, 코드 2개)를 돌린 DB에서만 저장된다.
 * 패치 전에 부르면 오류가 나 정정 요청·처리까지 취소되니, 부르는 코드는 패치가 들어간 뒤에 붙인다.
 * 이음 문의 답변 알림도 같다(11-09_01 패치: NOTIFICATION.SUPPORT_INQUIRY_ID 칸과 CK_69_01 교체, 코드 1개. 팀 DB는 11/10 적용).
 * 답변 하나에 알림 하나를 저장한다(같은 답변으로 두 번 와도 하나). 답변을 고칠 때는 보내지 않는다.
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

    /**
     * 근태 정정 요청(NT_ATT_CORRECTION_REQUEST). 정정 요청을 받을 때 처리자(요청자 부서장, 없거나 본인이면 업체 관리자)에게 부른다.
     * 처리자가 여럿이면 사람마다 부른다. workDate는 YYYY-MM-DD.
     */
    public void notifyAttendanceCorrectionRequest(Long companyId, Long receiverEmployeeId, Long attendanceCorrectionId,
                                                  String requesterName, String workDate) {
        NotificationType type = NotificationType.NT_ATT_CORRECTION_REQUEST;
        saveAttendanceCorrection(type, companyId, receiverEmployeeId, attendanceCorrectionId,
                type.messageFor(requesterName, workDate));
    }

    /**
     * 근태 정정 처리(NT_ATT_CORRECTION_DONE). 승인하거나 반려할 때 요청한 직원에게 부른다. 취소는 본인이 하니 부르지 않는다.
     * approved가 true면 승인, false면 반려 문구로 저장한다. workDate는 YYYY-MM-DD.
     */
    public void notifyAttendanceCorrectionDone(Long companyId, Long receiverEmployeeId, Long attendanceCorrectionId,
                                               String workDate, boolean approved) {
        NotificationType type = NotificationType.NT_ATT_CORRECTION_DONE;
        String result = approved ? "승인되었습니다." : "반려되었습니다. 근태 화면에서 반려 사유를 확인해 주세요.";
        saveAttendanceCorrection(type, companyId, receiverEmployeeId, attendanceCorrectionId,
                type.messageFor(workDate, result));
    }

    /**
     * 이음 문의 답변(NT_SUPPORT_REPLY). 운영자가 답변을 달 때 문의한 직원에게 부른다.
     * companyId는 문의한 업체다(운영자의 업체가 아니다). 같은 답변 번호로 두 번 불러도 하나만 저장한다.
     */
    public void notifySupportReply(Long companyId, Long receiverEmployeeId, Long supportInquiryId,
                                   Long supportReplyId, String inquiryTitle) {
        NotificationType type = NotificationType.NT_SUPPORT_REPLY;
        NotificationSaveDTO dto = newRow(type, companyId, type.messageFor(inquiryTitle));
        dto.setSupportInquiryId(supportInquiryId);
        dto.setDedupeKey(type.name() + ":" + supportInquiryId + ":" + supportReplyId);
        save(dto, receiverEmployeeId);
    }

    /** 본인의 안 읽은 이음 문의 답변 알림 수와 가장 큰 알림 번호를 문의 번호별로. 안 읽은 것이 없는 문의는 빠진다 */
    public Map<Long, NotificationUnreadCountDTO> countUnreadSupportReplies(Long companyId, Long userAccountId) {
        Map<Long, NotificationUnreadCountDTO> counts = new HashMap<>();
        for (NotificationUnreadCountDTO row : notificationDAO.countUnreadSupportReplies(companyId, userAccountId)) {
            counts.put(row.getTargetId(), row);
        }
        return counts;
    }

    /**
     * 한 문의에 온 본인 답변 알림을 upToId 번호까지 읽음으로 표시한다('내가 보낸 문의'에서 그 문의를 펼쳤을 때).
     * upToId는 화면이 받은 목록의 가장 큰 알림 번호라, 목록을 받은 뒤 온 답변의 알림은 안 읽은 채로 남는다.
     * 남의 문의 번호나 이미 읽은 알림이면 아무것도 바꾸지 않는다(업체·받는 계정 조건). 바뀐 수를 돌려준다.
     */
    public int markSupportRepliesRead(Long companyId, Long userAccountId, Long supportInquiryId, Long upToId) {
        return notificationDAO.markSupportRepliesRead(companyId, userAccountId, supportInquiryId, upToId);
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
        NotificationSaveDTO dto = newRow(type, companyId, type.messageFor(documentTitle));
        dto.setApprovalDocumentId(approvalDocumentId);
        dto.setDedupeKey(type.name() + ":" + approvalDocumentId);
        save(dto, receiverEmployeeId);
    }

    private void saveTicket(NotificationType type, Long companyId, Long receiverEmployeeId,
                            Long itTicketId, String ticketTitle) {
        NotificationSaveDTO dto = newRow(type, companyId, type.messageFor(ticketTitle));
        dto.setItTicketId(itTicketId);
        dto.setDedupeKey(type.name() + ":" + itTicketId + ":" + UUID.randomUUID()); // 부를 때마다 새 행
        save(dto, receiverEmployeeId);
    }

    /** 근태 정정 알림 1행을 정정 요청 번호와 함께 저장한다. 같은 사람·요청·종류는 하나만 */
    private void saveAttendanceCorrection(NotificationType type, Long companyId, Long receiverEmployeeId,
                                          Long attendanceCorrectionId, String message) {
        NotificationSaveDTO dto = newRow(type, companyId, message);
        dto.setAttendanceCorrectionId(attendanceCorrectionId);
        dto.setDedupeKey(type.name() + ":" + attendanceCorrectionId);
        save(dto, receiverEmployeeId);
    }

    /** 종류·제목·문구를 채운 저장용 행을 만든다 */
    private NotificationSaveDTO newRow(NotificationType type, Long companyId, String message) {
        NotificationSaveDTO dto = new NotificationSaveDTO();
        dto.setCompanyId(companyId);
        dto.setNotificationType(type.name());
        dto.setTitle(type.getTitle());
        dto.setMessage(message);
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
