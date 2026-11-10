package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import com.ieum.gw.dao.NotificationDAO;
import com.ieum.gw.dto.NotificationDTO;
import com.ieum.gw.dto.NotificationPageDTO;
import com.ieum.gw.dto.NotificationSaveDTO;
import com.ieum.gw.dto.NotificationUnreadCountDTO;

/**
 * DB 없이 확인한다: DAO를 가짜로 바꿔 고유 제약(UQ_69_01)과 업체·계정 조건을 흉내 낸다.
 * SQL과 트랜잭션은 이 테스트 밖이다(PR 설명의 Oracle 확인).
 */
class NotificationServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long OTHER_COMPANY = 2L;
    private static final Long EMP_KIM = 100L;
    private static final Long EMP_LEE = 200L;
    private static final Long EMP_NO_ACCOUNT = 300L;
    private static final Long ACCOUNT_KIM = 10L;
    private static final Long ACCOUNT_LEE = 20L;
    private static final Long DOC = 7000L;
    private static final Long TICKET = 8000L;
    private static final Long CORRECTION = 9000L;
    private static final Long INQUIRY = 600L;

    /** 저장한 행을 기억하고, 받는 계정·중복 방지 키가 겹치면 고유 제약처럼 예외를 던지는 가짜 DAO */
    static class FakeDAO extends NotificationDAO {
        final Map<String, Long> accounts = new HashMap<>();
        final List<NotificationSaveDTO> rows = new ArrayList<>();
        final List<Long> readIds = new ArrayList<>();
        RuntimeException insertError;
        int lastOffset = -1;

        FakeDAO() {
            super(null);
            accounts.put(COMPANY + ":" + EMP_KIM, ACCOUNT_KIM);
            accounts.put(COMPANY + ":" + EMP_LEE, ACCOUNT_LEE);
        }

        @Override
        public Long findAccountId(Long companyId, Long employeeId) {
            return accounts.get(companyId + ":" + employeeId);
        }

        @Override
        public int insert(NotificationSaveDTO dto) {
            if (insertError != null) {
                throw insertError;
            }
            boolean duplicate = rows.stream().anyMatch(r -> r.getReceiverAccountId().equals(dto.getReceiverAccountId())
                    && r.getDedupeKey().equals(dto.getDedupeKey()));
            if (duplicate) {
                throw new DuplicateKeyException("ORA-00001");
            }
            rows.add(dto);
            return 1;
        }

        /** 알림 번호는 rows 순번(0부터)으로 친다 */
        private boolean owned(Long companyId, Long accountId, Long id) {
            if (id < 0 || id >= rows.size()) {
                return false;
            }
            NotificationSaveDTO row = rows.get(id.intValue());
            return row.getCompanyId().equals(companyId) && row.getReceiverAccountId().equals(accountId);
        }

        @Override
        public List<NotificationDTO> list(Long companyId, Long accountId, int offset, int size) {
            lastOffset = offset;
            return rows.stream()
                    .filter(r -> r.getCompanyId().equals(companyId) && r.getReceiverAccountId().equals(accountId))
                    .skip(offset)
                    .limit(size)
                    .map(r -> {
                        NotificationDTO dto = new NotificationDTO();
                        dto.setMessage(r.getMessage());
                        return dto;
                    })
                    .collect(Collectors.toList());
        }

        @Override
        public int count(Long companyId, Long accountId) {
            return (int) rows.stream()
                    .filter(r -> r.getCompanyId().equals(companyId) && r.getReceiverAccountId().equals(accountId))
                    .count();
        }

        @Override
        public int countUnread(Long companyId, Long accountId) {
            int unread = 0;
            for (long id = 0; id < rows.size(); id++) {
                if (owned(companyId, accountId, id) && !readIds.contains(id)) {
                    unread++;
                }
            }
            return unread;
        }

        @Override
        public int markRead(Long companyId, Long accountId, Long id) {
            if (!owned(companyId, accountId, id) || readIds.contains(id)) {
                return 0;
            }
            readIds.add(id);
            return 1;
        }

        @Override
        public boolean isOwned(Long companyId, Long accountId, Long id) {
            return owned(companyId, accountId, id);
        }

        /** 이음 문의 답변 알림 중 안 읽은 것을 문의별로 센다(SQL의 GROUP BY 흉내) */
        @Override
        public List<NotificationUnreadCountDTO> countUnreadSupportReplies(Long companyId, Long accountId) {
            Map<Long, NotificationUnreadCountDTO> counts = new HashMap<>();
            for (long id = 0; id < rows.size(); id++) {
                NotificationSaveDTO row = rows.get((int) id);
                if (owned(companyId, accountId, id) && !readIds.contains(id) && "NT_SUPPORT_REPLY".equals(row.getNotificationType())) {
                    NotificationUnreadCountDTO dto = counts.computeIfAbsent(row.getSupportInquiryId(), k -> {
                        NotificationUnreadCountDTO d = new NotificationUnreadCountDTO();
                        d.setTargetId(k);
                        return d;
                    });
                    dto.setUnread(dto.getUnread() + 1);
                    dto.setLastId(id); // 순번이 알림 번호다
                }
            }
            return new ArrayList<>(counts.values());
        }

        @Override
        public int markSupportRepliesRead(Long companyId, Long accountId, Long supportInquiryId, Long upToId) {
            int changed = 0;
            for (long id = 0; id <= upToId && id < rows.size(); id++) {
                NotificationSaveDTO row = rows.get((int) id);
                if (owned(companyId, accountId, id) && !readIds.contains(id)
                        && "NT_SUPPORT_REPLY".equals(row.getNotificationType())
                        && supportInquiryId.equals(row.getSupportInquiryId())) {
                    readIds.add(id);
                    changed++;
                }
            }
            return changed;
        }
    }

    private final FakeDAO dao = new FakeDAO();
    private final NotificationService service = new NotificationService(dao);

    @Test
    @DisplayName("결재 요청 알림은 사원 ID를 계정 ID로 바꿔 문서 번호와 함께 저장한다")
    void approvalRequestSaved() {
        service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC, "휴가 신청서");

        assertEquals(1, dao.rows.size());
        NotificationSaveDTO row = dao.rows.get(0);
        assertEquals(COMPANY, row.getCompanyId());
        assertEquals(ACCOUNT_KIM, row.getReceiverAccountId());
        assertEquals("NT_APPROVAL_REQUEST", row.getNotificationType());
        assertEquals("결재 요청", row.getTitle());
        assertTrue(row.getMessage().contains("휴가 신청서"));
        assertEquals(DOC, row.getApprovalDocumentId());
        assertNull(row.getItTicketId());
        assertNull(row.getAttendanceCorrectionId());
    }

    @Test
    @DisplayName("결재 완료·반려는 각자의 종류로 저장한다")
    void approvalTypes() {
        service.notifyApprovalDone(COMPANY, EMP_KIM, DOC, "휴가 신청서");
        service.notifyApprovalRejected(COMPANY, EMP_KIM, DOC, "휴가 신청서");

        assertEquals("NT_APPROVAL_DONE", dao.rows.get(0).getNotificationType());
        assertEquals("NT_APPROVAL_REJECT", dao.rows.get(1).getNotificationType());
    }

    @Test
    @DisplayName("같은 사람에게 같은 문서·같은 종류의 결재 알림을 두 번 보내도 예외 없이 하나만 저장한다")
    void approvalDeduped() {
        service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC, "휴가 신청서");
        service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC, "휴가 신청서");

        assertEquals(1, dao.rows.size());
    }

    @Test
    @DisplayName("같은 문서라도 받는 사람이나 종류나 문서가 다르면 각각 저장한다")
    void approvalNotOverDeduped() {
        service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC, "휴가 신청서");
        service.notifyApprovalRequest(COMPANY, EMP_LEE, DOC, "휴가 신청서");
        service.notifyApprovalDone(COMPANY, EMP_KIM, DOC, "휴가 신청서");
        service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC + 1, "장비 지급 신청서");

        assertEquals(4, dao.rows.size());
    }

    @Test
    @DisplayName("티켓 알림은 같은 인자로 두 번 불러도 부를 때마다 저장한다")
    void ticketNotDeduped() {
        service.notifyTicketAssigned(COMPANY, EMP_KIM, TICKET, "모니터 고장");
        service.notifyTicketAssigned(COMPANY, EMP_KIM, TICKET, "모니터 고장");

        assertEquals(2, dao.rows.size());
        assertNotEquals(dao.rows.get(0).getDedupeKey(), dao.rows.get(1).getDedupeKey());
        assertEquals("NT_TICKET_ASSIGNED", dao.rows.get(0).getNotificationType());
        assertEquals(TICKET, dao.rows.get(0).getItTicketId());
        assertNull(dao.rows.get(0).getApprovalDocumentId());
        assertNull(dao.rows.get(0).getAttendanceCorrectionId());
    }

    @Test
    @DisplayName("티켓 처리 완료 알림은 요청자 계정으로 저장하고 중복 방지 키가 칸 길이(100자)를 넘지 않는다")
    void ticketResolvedSaved() {
        service.notifyTicketResolved(COMPANY, EMP_LEE, Long.MAX_VALUE, "모니터 고장");

        NotificationSaveDTO row = dao.rows.get(0);
        assertEquals("NT_TICKET_RESOLVED", row.getNotificationType());
        assertEquals(ACCOUNT_LEE, row.getReceiverAccountId());
        assertTrue(row.getDedupeKey().length() <= 100);
    }

    @Test
    @DisplayName("근태 정정 요청 알림은 처리자 계정으로 정정 요청 번호와 함께 저장하고 요청자 이름·근무일을 문구에 넣는다")
    void attendanceCorrectionRequestSaved() {
        service.notifyAttendanceCorrectionRequest(COMPANY, EMP_KIM, CORRECTION, "고양이", "2026-11-04");

        assertEquals(1, dao.rows.size());
        NotificationSaveDTO row = dao.rows.get(0);
        assertEquals(ACCOUNT_KIM, row.getReceiverAccountId());
        assertEquals("NT_ATT_CORRECTION_REQUEST", row.getNotificationType());
        assertEquals("근태 정정 요청", row.getTitle());
        assertEquals("고양이님이 2026-11-04 근태 정정을 요청했습니다.", row.getMessage());
        assertEquals(CORRECTION, row.getAttendanceCorrectionId());
        assertNull(row.getApprovalDocumentId());
        assertNull(row.getItTicketId());
    }

    @Test
    @DisplayName("근태 정정 요청 알림은 같은 처리자에게 두 번 와도 하나만, 처리자가 여럿이면 사람마다 저장한다")
    void attendanceCorrectionRequestDeduped() {
        service.notifyAttendanceCorrectionRequest(COMPANY, EMP_KIM, CORRECTION, "고양이", "2026-11-04");
        service.notifyAttendanceCorrectionRequest(COMPANY, EMP_KIM, CORRECTION, "고양이", "2026-11-04");
        service.notifyAttendanceCorrectionRequest(COMPANY, EMP_LEE, CORRECTION, "고양이", "2026-11-04");
        service.notifyAttendanceCorrectionRequest(COMPANY, EMP_KIM, CORRECTION + 1, "고양이", "2026-11-05");

        assertEquals(3, dao.rows.size());
    }

    @Test
    @DisplayName("근태 정정 처리 알림은 승인과 반려 문구가 다르고, 같은 요청의 처리 알림은 하나만 저장한다")
    void attendanceCorrectionDoneSaved() {
        service.notifyAttendanceCorrectionDone(COMPANY, EMP_KIM, CORRECTION, "2026-11-04", true);
        service.notifyAttendanceCorrectionDone(COMPANY, EMP_KIM, CORRECTION, "2026-11-04", false);
        service.notifyAttendanceCorrectionDone(COMPANY, EMP_KIM, CORRECTION + 1, "2026-11-05", false);

        assertEquals(2, dao.rows.size());
        NotificationSaveDTO approved = dao.rows.get(0);
        assertEquals("NT_ATT_CORRECTION_DONE", approved.getNotificationType());
        assertEquals("근태 정정 처리", approved.getTitle());
        assertEquals("2026-11-04 근태 정정 요청이 승인되었습니다.", approved.getMessage());
        assertEquals(CORRECTION, approved.getAttendanceCorrectionId());
        assertNull(approved.getApprovalDocumentId());
        assertNull(approved.getItTicketId());
        assertEquals("2026-11-05 근태 정정 요청이 반려되었습니다. 근태 화면에서 반려 사유를 확인해 주세요.",
                dao.rows.get(1).getMessage());
    }

    @Test
    @DisplayName("근태 정정 알림의 중복 방지 키는 칸 길이(100자)를 넘지 않는다")
    void attendanceCorrectionKeyLength() {
        service.notifyAttendanceCorrectionRequest(COMPANY, EMP_KIM, Long.MAX_VALUE, "고양이", "2026-11-04");
        service.notifyAttendanceCorrectionDone(COMPANY, EMP_KIM, Long.MAX_VALUE, "2026-11-04", true);

        dao.rows.forEach(r -> assertTrue(r.getDedupeKey().length() <= 100));
    }

    @Test
    @DisplayName("근태 정정 알림도 중복이 아닌 저장 오류(패치 전 DB의 CK_69_01 등)는 그대로 던진다")
    void attendanceCorrectionErrorPropagates() {
        dao.insertError = new DataIntegrityViolationException("ORA-02290");

        assertThrows(DataIntegrityViolationException.class,
                () -> service.notifyAttendanceCorrectionRequest(COMPANY, EMP_KIM, CORRECTION, "고양이", "2026-11-04"));
    }

    @Test
    @DisplayName("이음 문의 답변 알림은 문의한 직원의 계정으로 문의 번호와 함께 저장하고 문의 제목을 문구에 넣는다")
    void supportReplySaved() {
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 1L, "결재함 오류");

        assertEquals(1, dao.rows.size());
        NotificationSaveDTO row = dao.rows.get(0);
        assertEquals(COMPANY, row.getCompanyId());
        assertEquals(ACCOUNT_KIM, row.getReceiverAccountId());
        assertEquals("NT_SUPPORT_REPLY", row.getNotificationType());
        assertEquals("이음 문의 답변", row.getTitle());
        assertEquals("'결재함 오류' 문의에 이음 운영팀이 답변했습니다.", row.getMessage());
        assertEquals(INQUIRY, row.getSupportInquiryId());
        assertNull(row.getApprovalDocumentId());
        assertNull(row.getItTicketId());
        assertNull(row.getAttendanceCorrectionId());
    }

    @Test
    @DisplayName("이음 문의 답변 알림은 답변마다 저장하고, 같은 답변으로 두 번 오면 하나만 저장한다")
    void supportReplyPerReply() {
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 1L, "결재함 오류");
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 1L, "결재함 오류");
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 2L, "결재함 오류");

        assertEquals(2, dao.rows.size());
    }

    @Test
    @DisplayName("이음 문의 답변 알림의 중복 방지 키는 칸 길이(100자)를 넘지 않는다")
    void supportReplyKeyLength() {
        service.notifySupportReply(COMPANY, EMP_KIM, Long.MAX_VALUE, Long.MAX_VALUE, "결재함 오류");

        assertTrue(dao.rows.get(0).getDedupeKey().length() <= 100);
    }

    @Test
    @DisplayName("이음 문의 답변 알림도 중복이 아닌 저장 오류는 그대로 던진다")
    void supportReplyErrorPropagates() {
        dao.insertError = new DataIntegrityViolationException("ORA-02291");

        assertThrows(DataIntegrityViolationException.class,
                () -> service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 1L, "결재함 오류"));
    }

    @Test
    @DisplayName("안 읽은 답변 알림 수는 본인 것만 문의별로 세고, 다른 종류의 알림은 세지 않는다")
    void countUnreadSupportRepliesOwnOnly() {
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 1L, "a");
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 2L, "a");
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY + 1, 3L, "b");
        service.notifySupportReply(COMPANY, EMP_LEE, INQUIRY, 4L, "a");
        service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC, "c");

        Map<Long, NotificationUnreadCountDTO> counts = service.countUnreadSupportReplies(COMPANY, ACCOUNT_KIM);

        assertEquals(2, counts.size());
        assertEquals(2, counts.get(INQUIRY).getUnread());
        assertEquals(1L, counts.get(INQUIRY).getLastId()); // 0·1번이 김의 INQUIRY 알림
        assertEquals(1, counts.get(INQUIRY + 1).getUnread());
        assertTrue(service.countUnreadSupportReplies(OTHER_COMPANY, ACCOUNT_KIM).isEmpty());
    }

    @Test
    @DisplayName("문의를 펼치면 그 문의의 본인 답변 알림만 읽음이 되고 헤더 배지 수도 줄어든다")
    void markSupportRepliesReadOnlyThatInquiry() {
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 1L, "a");
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 2L, "a");
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY + 1, 3L, "b");
        service.notifySupportReply(COMPANY, EMP_LEE, INQUIRY, 4L, "a");

        Long upTo = service.countUnreadSupportReplies(COMPANY, ACCOUNT_KIM).get(INQUIRY).getLastId();
        assertEquals(2, service.markSupportRepliesRead(COMPANY, ACCOUNT_KIM, INQUIRY, upTo));
        assertEquals(0, service.markSupportRepliesRead(COMPANY, ACCOUNT_KIM, INQUIRY, upTo)); // 두 번 눌러도 된다

        assertEquals(Map.of(INQUIRY + 1, 1), service.countUnreadSupportReplies(COMPANY, ACCOUNT_KIM).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getUnread())));
        assertEquals(1, service.countUnread(COMPANY, ACCOUNT_KIM));
        assertEquals(1, service.countUnread(COMPANY, ACCOUNT_LEE));
    }

    @Test
    @DisplayName("목록을 받은 뒤 온 답변의 알림은 그때 받은 번호까지만 읽음으로 바꿔서 안 읽은 채로 남는다")
    void markSupportRepliesReadKeepsLaterReply() {
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 1L, "a");
        Long upTo = service.countUnreadSupportReplies(COMPANY, ACCOUNT_KIM).get(INQUIRY).getLastId(); // 화면이 받은 목록
        service.notifySupportReply(COMPANY, EMP_KIM, INQUIRY, 2L, "a"); // 그 뒤 운영자가 답변을 더 단다

        assertEquals(1, service.markSupportRepliesRead(COMPANY, ACCOUNT_KIM, INQUIRY, upTo));

        assertEquals(1, service.countUnreadSupportReplies(COMPANY, ACCOUNT_KIM).get(INQUIRY).getUnread());
    }

    @Test
    @DisplayName("받는 사원의 계정이 없으면 저장하지 않고 업무도 막지 않는다")
    void noAccountSkipped() {
        service.notifyApprovalDone(COMPANY, EMP_NO_ACCOUNT, DOC, "휴가 신청서");

        assertTrue(dao.rows.isEmpty());
    }

    @Test
    @DisplayName("다른 업체의 사원 번호로는 그 사원의 계정을 찾지 않는다")
    void otherCompanyEmployeeSkipped() {
        service.notifyApprovalDone(OTHER_COMPANY, EMP_KIM, DOC, "휴가 신청서");

        assertTrue(dao.rows.isEmpty());
    }

    @Test
    @DisplayName("중복이 아닌 저장 오류는 그대로 던져 업무 트랜잭션을 취소하게 한다")
    void otherErrorPropagates() {
        dao.insertError = new DataIntegrityViolationException("ORA-02291");

        assertThrows(DataIntegrityViolationException.class,
                () -> service.notifyApprovalDone(COMPANY, EMP_KIM, DOC, "휴가 신청서"));
    }

    @Test
    @DisplayName("안 읽은 수는 본인 알림만 세고, 읽으면 줄어든다")
    void countUnreadOwnOnly() {
        service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC, "a");
        service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC + 1, "b");
        service.notifyApprovalRequest(COMPANY, EMP_LEE, DOC, "a");

        assertEquals(2, service.countUnread(COMPANY, ACCOUNT_KIM));
        assertEquals(1, service.countUnread(COMPANY, ACCOUNT_LEE));
        assertEquals(0, service.countUnread(OTHER_COMPANY, ACCOUNT_KIM));

        service.markRead(COMPANY, ACCOUNT_KIM, 0L);

        assertEquals(1, service.countUnread(COMPANY, ACCOUNT_KIM));
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽어도 성공이다")
    void markReadTwice() {
        service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC, "a");

        service.markRead(COMPANY, ACCOUNT_KIM, 0L);
        service.markRead(COMPANY, ACCOUNT_KIM, 0L);

        assertEquals(1, dao.readIds.size());
    }

    @Test
    @DisplayName("남의 알림·다른 업체의 알림·없는 알림은 읽음 처리를 거절하고 아무것도 바꾸지 않는다")
    void markReadRejected() {
        service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC, "a");

        assertThrows(NotificationNotFoundException.class, () -> service.markRead(COMPANY, ACCOUNT_LEE, 0L));
        assertThrows(NotificationNotFoundException.class, () -> service.markRead(OTHER_COMPANY, ACCOUNT_KIM, 0L));
        assertThrows(NotificationNotFoundException.class, () -> service.markRead(COMPANY, ACCOUNT_KIM, 99L));
        assertTrue(dao.readIds.isEmpty());
    }

    @Test
    @DisplayName("목록은 페이지를 1 ~ 마지막 사이로 맞추고 알림이 없어도 1페이지를 준다")
    void listPaging() {
        NotificationPageDTO empty = service.list(COMPANY, ACCOUNT_KIM, 5);
        assertEquals(1, empty.getPage());
        assertEquals(1, empty.getPages());
        assertEquals(0, empty.getTotal());
        assertNotNull(empty.getItems());
        assertEquals(0, dao.lastOffset);

        long total = NotificationService.PAGE_SIZE + 1;
        for (long i = 0; i < total; i++) {
            service.notifyApprovalRequest(COMPANY, EMP_KIM, DOC + i, "문서");
        }

        NotificationPageDTO last = service.list(COMPANY, ACCOUNT_KIM, Integer.MAX_VALUE);
        assertEquals(2, last.getPage());
        assertEquals(2, last.getPages());
        assertEquals(total, last.getTotal());
        assertEquals(1, last.getItems().size());
        assertEquals(NotificationService.PAGE_SIZE, dao.lastOffset);

        NotificationPageDTO first = service.list(COMPANY, ACCOUNT_KIM, -3);
        assertEquals(1, first.getPage());
        assertEquals(NotificationService.PAGE_SIZE, first.getItems().size());
        assertEquals(0, dao.lastOffset);
    }
}
