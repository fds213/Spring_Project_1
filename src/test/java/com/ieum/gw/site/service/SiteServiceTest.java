package com.ieum.gw.site.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.employee.dto.AdminAccountDTO;
import com.ieum.gw.employee.service.EmployeeService;
import com.ieum.gw.site.dto.TrialApplyDTO;
import com.ieum.gw.site.dto.TrialResultDTO;
import com.ieum.gw.subscription.dto.TrialCompanyDTO;
import com.ieum.gw.subscription.service.SubscriptionService;

class SiteServiceTest {

    private static TrialApplyDTO form(String companyName, Number seats, String email, String adminName, String password) {
        TrialApplyDTO f = new TrialApplyDTO();
        f.setCompanyName(companyName);
        f.setSeats(seats == null ? null : new BigDecimal(seats.toString()));
        f.setEmail(email);
        f.setAdminName(adminName);
        f.setPassword(password);
        return f;
    }

    private static TrialApplyDTO valid() {
        return form("가나상사", 5, "kim@gana.com", "김가나", "password1");
    }

    private static String rejectedField(TrialApplyDTO f) {
        return assertThrows(TrialFormException.class, () -> SiteService.normalize(f)).getField();
    }

    /** 받은 값을 적어 두고 정해 둔 업체를 돌려주는 가짜 구독 */
    static class FakeSubscription extends SubscriptionService {
        String companyName;
        String trialEmail;
        int seats;

        FakeSubscription() {
            super(null, null);
        }

        @Override
        public TrialCompanyDTO createTrialCompany(String companyName, String trialEmail, int seats) {
            this.companyName = companyName;
            this.trialEmail = trialEmail;
            this.seats = seats;
            TrialCompanyDTO c = new TrialCompanyDTO();
            c.setCompanyId(42L);
            c.setCompanyCode("ieum4827");
            c.setStartsAt(LocalDateTime.parse("2026-10-30T15:20"));
            c.setEndsAt(LocalDateTime.parse("2026-11-13T00:00"));
            return c;
        }
    }

    /** 받은 값을 적어 두고 사번 20260001을 돌려주는 가짜 사원 */
    static class FakeEmployee extends EmployeeService {
        Long companyId;
        String adminName;
        String email;
        String rawPassword;

        @Override
        public AdminAccountDTO createCompanyAdmin(Long companyId, String adminName, String email, String rawPassword) {
            this.companyId = companyId;
            this.adminName = adminName;
            this.email = email;
            this.rawPassword = rawPassword;
            AdminAccountDTO a = new AdminAccountDTO();
            a.setEmployeeId(7L);
            a.setUserAccountId(8L);
            a.setEmployeeNo("20260001");
            return a;
        }
    }

    @Test
    @DisplayName("입력 정리: 이름은 앞뒤 공백을 빼고 이메일은 소문자로, 비밀번호는 그대로 둔다")
    void normalizes() {
        TrialApplyDTO in = SiteService.normalize(form("  가나상사 ", 10, "  Kim@Gana.COM ", " 김가나 ", " pass word "));

        assertEquals("가나상사", in.getCompanyName());
        assertEquals(BigDecimal.TEN, in.getSeats());
        assertEquals("kim@gana.com", in.getEmail());
        assertEquals("김가나", in.getAdminName());
        assertEquals(" pass word ", in.getPassword());
    }

    @Test
    @DisplayName("이용 인원은 관리자 포함 1~10명 정수만 받는다: 10.9를 10으로 잘라 받지 않는다")
    void rejectsSeatsOutOfRange() {
        assertEquals("seats", rejectedField(form("가나상사", 0, "kim@gana.com", "김가나", "password1")));
        assertEquals("seats", rejectedField(form("가나상사", 11, "kim@gana.com", "김가나", "password1")));
        assertEquals("seats", rejectedField(form("가나상사", null, "kim@gana.com", "김가나", "password1")));
        assertEquals("seats", rejectedField(form("가나상사", new BigDecimal("10.9"), "kim@gana.com", "김가나", "password1")));
        assertEquals("seats", rejectedField(form("가나상사", new BigDecimal("0.5"), "kim@gana.com", "김가나", "password1")));
        assertEquals(BigDecimal.TEN, SiteService.normalize(form("가나상사", new BigDecimal("10.0"), "kim@gana.com", "김가나", "password1")).getSeats());
    }

    @Test
    @DisplayName("비밀번호는 8자 이상, UTF-8 72바이트 이하(BCrypt가 뒤를 버리는 길이): 한글은 24자까지")
    void rejectsPasswordLength() {
        assertEquals("password", rejectedField(form("가나상사", 5, "kim@gana.com", "김가나", "pass123")));
        assertEquals("password", rejectedField(form("가나상사", 5, "kim@gana.com", "김가나", null)));
        assertEquals("password", rejectedField(form("가나상사", 5, "kim@gana.com", "김가나", "a".repeat(73))));
        assertEquals("password", rejectedField(form("가나상사", 5, "kim@gana.com", "김가나", "가".repeat(24) + "A")));
        assertEquals("a".repeat(72), SiteService.normalize(form("가나상사", 5, "kim@gana.com", "김가나", "a".repeat(72))).getPassword());
        assertEquals("가".repeat(24), SiteService.normalize(form("가나상사", 5, "kim@gana.com", "김가나", "가".repeat(24))).getPassword());
    }

    @Test
    @DisplayName("빈 칸·긴 이름·형식이 틀린 이메일은 그 칸을 알려 거절한다")
    void rejectsBlankLongAndMalformed() {
        assertEquals("companyName", rejectedField(form("   ", 5, "kim@gana.com", "김가나", "password1")));
        assertEquals("companyName", rejectedField(form("가".repeat(201), 5, "kim@gana.com", "김가나", "password1")));
        assertEquals("adminName", rejectedField(form("가나상사", 5, "kim@gana.com", null, "password1")));
        assertEquals("adminName", rejectedField(form("가나상사", 5, "kim@gana.com", "김".repeat(101), "password1")));
        assertEquals("email", rejectedField(form("가나상사", 5, "kim.gana.com", "김가나", "password1")));
        assertEquals("email", rejectedField(form("가나상사", 5, "kim @gana.com", "김가나", "password1")));
        assertEquals("email", rejectedField(form("가나상사", 5, "", "김가나", "password1")));
        assertEquals("companyName", rejectedField(null));
    }

    @Test
    @DisplayName("체험 신청: 정리한 값으로 업체를 만들고 그 업체 번호로 관리자를 만든 뒤 완료 화면 값을 돌려준다")
    void appliesTrial() {
        FakeSubscription subscription = new FakeSubscription();
        FakeEmployee employee = new FakeEmployee();
        SiteService service = new SiteService(subscription, employee);

        TrialResultDTO result = service.applyTrial(form(" 가나상사 ", 5, "Kim@Gana.com", "김가나", "password1"));

        assertEquals("가나상사", subscription.companyName);
        assertEquals("kim@gana.com", subscription.trialEmail);
        assertEquals(5, subscription.seats);
        assertEquals(42L, employee.companyId);
        assertEquals("김가나", employee.adminName);
        assertEquals("kim@gana.com", employee.email);
        assertEquals("password1", employee.rawPassword);

        assertEquals("ieum4827", result.getCompanyCode());
        assertEquals("20260001", result.getEmployeeNo());
        assertEquals("2026-10-30", result.getTrialStartDate());
        assertEquals("2026-11-12", result.getTrialLastDate());
    }

    // 실제로 업체·기간이 되돌려지는지는 DB가 있어야 볼 수 있다(CI에는 DB가 없음). 여기서는 예외가 삼켜지지 않는지와
    // 트랜잭션 표시가 빠지지 않았는지만 본다
    @Test
    @DisplayName("관리자 계정을 만들지 못하면 예외를 삼키지 않고 내보낸다(applyTrial에 @Transactional이 붙어 있어 되돌려진다)")
    void adminFailurePropagates() throws Exception {
        assertNotNull(SiteService.class.getMethod("applyTrial", TrialApplyDTO.class).getAnnotation(Transactional.class));

        EmployeeService notReady = new EmployeeService() {
            @Override
            public AdminAccountDTO createCompanyAdmin(Long companyId, String adminName, String email, String rawPassword) {
                throw new UnsupportedOperationException("구현 전: 김상준");
            }
        };
        SiteService service = new SiteService(new FakeSubscription(), notReady);

        assertThrows(UnsupportedOperationException.class, () -> service.applyTrial(valid()));
    }
}
