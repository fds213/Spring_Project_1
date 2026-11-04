package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.dao.SubscriptionDAO;
import com.ieum.gw.dao.TrialVerifyDAO;
import com.ieum.gw.dto.TrialVerificationDTO;
import com.ieum.gw.dto.TrialVerifyIssuedDTO;
import com.ieum.gw.dto.TrialVerifyTargetDTO;

class TrialVerifyRecordServiceTest {

    private static final Long COMPANY = 42L;
    private static final Long ACCOUNT = 8L;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 4, 10, 0);

    /** 표 대신 메모리에 두는 가짜 저장소. 조건부 UPDATE도 실제 SQL과 같은 조건으로 판단한다 */
    static class FakeDAO extends TrialVerifyDAO {
        final List<TrialVerificationDTO> rows = new ArrayList<>();
        final Map<LocalDate, Integer> resends = new HashMap<>();
        TrialVerifyTargetDTO target = new TrialVerifyTargetDTO();

        FakeDAO() {
            super(null);
            target.setCompanyId(COMPANY);
            target.setCompanyCode("ieum4827");
            target.setEmail("kim@gana.com");
            target.setUserAccountId(ACCOUNT);
            target.setEmployeeNo("20260001");
        }

        @Override
        public TrialVerifyTargetDTO getTarget(Long companyId) {
            return COMPANY.equals(companyId) ? target : null;
        }

        @Override
        public LocalDateTime getLastIssuedAt(Long companyId) {
            return rows.stream().map(TrialVerificationDTO::getIssuedAt).max(LocalDateTime::compareTo).orElse(null);
        }

        @Override
        public int getResendCount(Long companyId, String email, LocalDate today) {
            return resends.getOrDefault(today, 0);
        }

        @Override
        public int revokeLive(Long companyId, LocalDateTime now) {
            rows.stream().filter(r -> r.getConsumedAt() == null && r.getRevokedAt() == null).forEach(r -> r.setRevokedAt(now));
            return 1;
        }

        @Override
        public int insertVerification(TrialVerificationDTO v) {
            v.setEmailVerificationId(1000L + rows.size());
            rows.add(v);
            return 1;
        }

        @Override
        public int recordIssue(Long companyId, String email, LocalDate today, int count, LocalDateTime now) {
            resends.merge(today, count, Integer::sum);
            return 1;
        }

        @Override
        public TrialVerificationDTO findByTokenHash(String tokenHash) {
            return rows.stream().filter(r -> r.getTokenHash().equals(tokenHash)).findFirst().orElse(null);
        }

        @Override
        public int consume(Long id, Long companyId, LocalDateTime now) {
            for (TrialVerificationDTO r : rows) {
                if (r.getEmailVerificationId().equals(id) && r.getConsumedAt() == null && r.getRevokedAt() == null
                        && r.getExpiresAt().isAfter(now)) {
                    r.setConsumedAt(now);
                    return 1;
                }
            }
            return 0;
        }
    }

    static class FakeSubscription extends SubscriptionDAO {
        final FakeDAO dao;
        int locks;
        LocalDateTime verifiedAt;

        FakeSubscription(FakeDAO dao) {
            super(null);
            this.dao = dao;
        }

        @Override
        public void lockCompany(Long companyId) {
            locks++;
        }

        @Override
        public int markEmailVerified(Long companyId, LocalDateTime at) {
            verifiedAt = at;
            dao.target.setEmailVerifiedAt(at);
            return 1;
        }
    }

    static class FakeEmployee extends EmployeeService {
        Long account;

        @Override
        public void markAccountEmailVerified(Long companyId, Long userAccountId, LocalDateTime verifiedAt) {
            account = userAccountId;
        }
    }

    private final FakeDAO dao = new FakeDAO();
    private final FakeSubscription subscription = new FakeSubscription(dao);
    private final FakeEmployee employee = new FakeEmployee();
    private LocalDateTime clock = T0;
    private final TrialVerifyRecordService service = new TrialVerifyRecordService(dao, subscription, employee) {
        @Override
        LocalDateTime now() {
            return clock;
        }
    };

    private int statusOf(Runnable action) {
        return assertThrows(TrialVerifyException.class, action::run).getStatus();
    }

    @Test
    @DisplayName("링크 값은 해시로만 저장하고 24시간 뒤 만료로 적는다. 동시 요청을 막으려고 업체 행을 잠근다")
    void issuesHashedToken() {
        TrialVerifyIssuedDTO issued = service.issue(COMPANY, false);

        TrialVerificationDTO row = dao.rows.get(0);
        assertNotEquals(issued.getToken(), row.getTokenHash());
        assertEquals(TrialVerifyRecordService.sha256(issued.getToken()), row.getTokenHash());
        assertEquals(T0.plusHours(24), row.getExpiresAt());
        assertEquals(ACCOUNT, row.getUserAccountId());
        assertEquals("kim@gana.com", row.getEmail());
        assertEquals(1, subscription.locks);
        assertTrue(issued.getToken().matches("[A-Za-z0-9_-]{43}"), "주소에 그대로 넣을 수 있는 글자만 쓴다");
    }

    @Test
    @DisplayName("다시 보내면 이전 링크는 쓸 수 없고 새 링크만 된다")
    void resendRevokesPrevious() {
        String first = service.issue(COMPANY, false).getToken();
        clock = T0.plusMinutes(1);
        String second = service.issue(COMPANY, true).getToken();

        assertEquals(400, statusOf(() -> service.verify(first)));
        assertNull(subscription.verifiedAt);

        assertTrue(service.verify(second));
        assertEquals(clock, subscription.verifiedAt);
        assertEquals(ACCOUNT, employee.account);
    }

    @Test
    @DisplayName("다시 보내기는 1분에 한 번: 59초 뒤는 거절, 60초 뒤는 된다")
    void oneResendPerMinute() {
        service.issue(COMPANY, false);

        clock = T0.plusSeconds(59);
        assertEquals(429, statusOf(() -> service.issue(COMPANY, true)));
        assertEquals(1, dao.rows.size(), "거절되면 새 링크가 생기지 않는다");
        assertNull(dao.rows.get(0).getRevokedAt(), "거절되면 이전 링크도 그대로 쓸 수 있다");

        clock = T0.plusSeconds(60);
        assertNotNull(service.issue(COMPANY, true));
    }

    @Test
    @DisplayName("다시 보내기는 하루 5번: 첫 메일은 세지 않고, 여섯 번째를 거절하며, 다음 날 다시 된다")
    void fiveResendsPerDay() {
        service.issue(COMPANY, false);
        for (int i = 1; i <= 5; i++) {
            clock = T0.plusMinutes(i);
            assertNotNull(service.issue(COMPANY, true), i + "번째 다시 보내기");
        }

        clock = T0.plusMinutes(6);
        assertEquals(429, statusOf(() -> service.issue(COMPANY, true)));

        clock = T0.plusDays(1);
        assertNotNull(service.issue(COMPANY, true));
    }

    @Test
    @DisplayName("1분 제한은 날짜가 바뀌어도 이어진다")
    void minuteLimitAcrossMidnight() {
        clock = LocalDateTime.of(2026, 11, 4, 23, 59, 50);
        service.issue(COMPANY, false);

        clock = LocalDateTime.of(2026, 11, 5, 0, 0, 10);
        assertEquals(429, statusOf(() -> service.issue(COMPANY, true)));
    }

    @Test
    @DisplayName("24시간이 지난 링크는 거절한다: 23시간 59분은 되고 24시간은 안 된다")
    void linkExpiresAfter24Hours() {
        String token = service.issue(COMPANY, false).getToken();

        clock = T0.plusHours(24);
        assertEquals(410, statusOf(() -> service.verify(token)));
        assertNull(subscription.verifiedAt);

        clock = T0.plusHours(24).minusMinutes(1);
        assertTrue(service.verify(token));
    }

    @Test
    @DisplayName("없는 값이나 빈 값은 거절한다")
    void unknownToken() {
        assertEquals(400, statusOf(() -> service.verify("없는값")));
        assertEquals(400, statusOf(() -> service.verify("")));
        assertEquals(400, statusOf(() -> service.verify(null)));
    }

    @Test
    @DisplayName("두 번 눌러도 처음 시각이 남고, 인증을 마친 업체의 옛 링크는 '이미 인증'으로 답한다")
    void secondClickIsAlreadyVerified() {
        String first = service.issue(COMPANY, false).getToken();
        clock = T0.plusMinutes(1);
        String second = service.issue(COMPANY, true).getToken();

        assertTrue(service.verify(second));
        LocalDateTime verifiedAt = subscription.verifiedAt;

        clock = T0.plusDays(3);
        assertFalse(service.verify(second));
        assertFalse(service.verify(first));
        assertEquals(verifiedAt, subscription.verifiedAt);
    }

    @Test
    @DisplayName("이미 인증을 마친 업체에는 새 링크를 만들지 않는다")
    void noIssueAfterVerified() {
        dao.target.setEmailVerifiedAt(T0);

        assertNull(service.issue(COMPANY, true));
        assertTrue(dao.rows.isEmpty());
    }

    @Test
    @DisplayName("없는 업체면 거절한다")
    void unknownCompany() {
        assertEquals(400, statusOf(() -> service.issue(999L, true)));
    }
}
