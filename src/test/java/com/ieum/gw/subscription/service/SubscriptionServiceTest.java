package com.ieum.gw.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.ieum.gw.subscription.dao.SubscriptionDAO;
import com.ieum.gw.subscription.dto.CompanyDTO;
import com.ieum.gw.subscription.dto.CompanyUsageDTO;
import com.ieum.gw.subscription.dto.ServicePeriodDTO;
import com.ieum.gw.subscription.dto.TrialCompanyDTO;

/**
 * DB 없이 확인한다: DAO를 가짜로 바꿔 고유 제약(업체 코드·이메일)에 걸리는 상황을 만든다.
 * 실제 동시 신청에서 코드가 겹치지 않는지는 팀 DB의 고유 제약으로 확인해야 한다(이 테스트 밖).
 */
class SubscriptionServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 업체 코드 고유 제약과 (요청한) 이메일 고유 제약을 흉내 내는 가짜 DAO */
    static class FakeDAO extends SubscriptionDAO {
        final Set<String> usedCodes = new HashSet<>();
        final Set<String> usedEmails = new HashSet<>();
        final List<ServicePeriodDTO> periods = new ArrayList<>();
        String emailSavedByOtherRequest; // 다음 insertCompany 직전에 다른 신청이 이 이메일로 저장된다
        LocalDateTime emailVerifiedAt;
        int insertTries;
        long nextId = 10;

        FakeDAO() {
            super(null);
        }

        @Override
        public boolean existsTrialEmail(String trialEmail) {
            return usedEmails.contains(trialEmail);
        }

        @Override
        public int insertCompany(CompanyDTO dto) {
            insertTries++;
            dto.setCompanyId(nextId++); // selectKey처럼 시도마다 새 번호
            if (emailSavedByOtherRequest != null) {
                usedEmails.add(emailSavedByOtherRequest);
                emailSavedByOtherRequest = null;
            }
            if (usedEmails.contains(dto.getTrialEmail()) || usedCodes.contains(dto.getCompanyCode())) {
                throw new DuplicateKeyException("ORA-00001");
            }
            usedCodes.add(dto.getCompanyCode());
            usedEmails.add(dto.getTrialEmail());
            return 1;
        }

        @Override
        public int insertTrialPeriod(ServicePeriodDTO dto) {
            dto.setServicePeriodId(1000L + periods.size());
            periods.add(dto);
            return 1;
        }

        @Override
        public List<ServicePeriodDTO> listPeriods(Long companyId) {
            return periods.stream().filter(p -> p.getCompanyId().equals(companyId)).collect(Collectors.toList());
        }

        @Override
        public LocalDateTime getEmailVerifiedAt(Long companyId) {
            return emailVerifiedAt;
        }
    }

    /** 정해 둔 순서로 업체 코드를 내놓는다. 마지막 값은 계속 되풀이한다 */
    static CompanyCodeGenerator codes(String... sequence) {
        Iterator<String> it = Arrays.asList(sequence).iterator();
        return new CompanyCodeGenerator() {
            private String last;

            @Override
            public String next() {
                if (it.hasNext()) {
                    last = it.next();
                }
                return last;
            }
        };
    }

    private static ServicePeriodDTO period(long companyId, boolean trial, LocalDateTime startsAt, LocalDateTime endsAt, int seats) {
        ServicePeriodDTO p = new ServicePeriodDTO();
        p.setCompanyId(companyId);
        p.setTrial(trial);
        p.setStartsAt(startsAt);
        p.setEndsAt(endsAt);
        p.setPurchasedSeats(seats);
        return p;
    }

    @Test
    @DisplayName("뽑은 업체 코드가 이미 있으면 다시 뽑아 겹치지 않는 코드로 저장한다")
    void retriesOnDuplicateCode() {
        FakeDAO dao = new FakeDAO();
        dao.usedCodes.addAll(List.of("ieum0001", "ieum0002"));
        SubscriptionService service = new SubscriptionService(dao, codes("ieum0001", "ieum0002", "ieum0003"));

        TrialCompanyDTO result = service.createTrialCompany("가나상사", "kim@gana.com", 5);

        assertEquals("ieum0003", result.getCompanyCode());
        assertEquals(3, dao.insertTries);
        assertEquals(1, dao.periods.size());
        assertEquals(result.getCompanyId(), dao.periods.get(0).getCompanyId()); // 마지막(성공한) 번호로 기간을 만든다
    }

    @Test
    @DisplayName("업체 코드가 10번 모두 겹치면 멈추고 새 신청을 받을 수 없다고 안내한다")
    void stopsAfterTenCollisions() {
        FakeDAO dao = new FakeDAO();
        dao.usedCodes.add("ieum1234");
        SubscriptionService service = new SubscriptionService(dao, codes("ieum1234"));

        assertThrows(CompanyCodeExhaustedException.class, () -> service.createTrialCompany("가나상사", "kim@gana.com", 5));
        assertEquals(10, dao.insertTries);
        assertTrue(dao.periods.isEmpty());
    }

    @Test
    @DisplayName("이미 업체를 만든 이메일은 저장하기 전에 거절한다")
    void rejectsUsedEmailBeforeInsert() {
        FakeDAO dao = new FakeDAO();
        dao.usedEmails.add("kim@gana.com");
        SubscriptionService service = new SubscriptionService(dao, codes("ieum0001"));

        assertThrows(TrialEmailUsedException.class, () -> service.createTrialCompany("가나상사", "kim@gana.com", 5));
        assertEquals(0, dao.insertTries);
    }

    @Test
    @DisplayName("같은 이메일 신청이 거의 동시에 먼저 저장되면 코드를 다시 뽑지 않고 이메일로 거절한다")
    void rejectsEmailSavedConcurrently() {
        FakeDAO dao = new FakeDAO();
        dao.emailSavedByOtherRequest = "kim@gana.com";
        SubscriptionService service = new SubscriptionService(dao, codes("ieum0001", "ieum0002"));

        assertThrows(TrialEmailUsedException.class, () -> service.createTrialCompany("가나상사", "kim@gana.com", 5));
        assertEquals(1, dao.insertTries);
        assertTrue(dao.periods.isEmpty());
    }

    @Test
    @DisplayName("체험 기간은 신청 시각에 시작하고 신청일부터 14일째 날까지, 계약 인원은 신청 인원")
    void trialPeriodValues() {
        FakeDAO dao = new FakeDAO();
        SubscriptionService service = new SubscriptionService(dao, codes("ieum0001"));

        TrialCompanyDTO result = service.createTrialCompany("가나상사", "kim@gana.com", 5);

        ServicePeriodDTO saved = dao.periods.get(0);
        assertTrue(saved.isTrial());
        assertEquals(result.getStartsAt(), saved.getStartsAt());
        assertEquals(result.getStartsAt().toLocalDate().plusDays(14).atStartOfDay(), saved.getEndsAt());
        assertEquals(result.getEndsAt(), saved.getEndsAt());
        assertEquals(5, saved.getPurchasedSeats());
        assertEquals(result.getStartsAt().toLocalDate(), saved.getBillingAnchorDate());
    }

    @Test
    @DisplayName("방금 신청한 업체는 체험 중·인증 전이고, 인원 한도는 신청 인원과 관계없이 10명")
    void justAppliedCompany() {
        FakeDAO dao = new FakeDAO();
        SubscriptionService service = new SubscriptionService(dao, codes("ieum0001"));
        TrialCompanyDTO company = service.createTrialCompany("가나상사", "kim@gana.com", 3);

        CompanyUsageDTO usage = service.getCompanyUsage(company.getCompanyId());
        assertTrue(usage.isTrial());
        assertFalse(usage.isExpired());
        assertFalse(usage.isEmailVerified());
        assertEquals(company.getEndsAt(), usage.getEndsAt());
        assertEquals(10, service.getSeatLimit(company.getCompanyId()));
    }

    @Test
    @DisplayName("유료 이용 중인 업체의 인원 한도는 구매 인원")
    void paidSeatLimit() {
        FakeDAO dao = new FakeDAO();
        LocalDateTime now = LocalDateTime.now(SEOUL);
        dao.periods.add(period(20, true, now.minusDays(10), now.plusDays(4), 5));
        dao.periods.add(period(20, false, now.minusDays(2), now.plusDays(28), 25));
        SubscriptionService service = new SubscriptionService(dao, codes("ieum0001"));

        assertEquals(25, service.getSeatLimit(20L));
        assertFalse(service.getCompanyUsage(20L).isTrial());
    }

    @Test
    @DisplayName("이용 기간이 없는 업체(이음 운영 업체): 이용 상태는 null, 인원 한도는 예외")
    void companyWithoutPeriod() {
        SubscriptionService service = new SubscriptionService(new FakeDAO(), codes("ieum0001"));

        assertNull(service.getCompanyUsage(1L));
        assertThrows(IllegalStateException.class, () -> service.getSeatLimit(1L));
    }
}
