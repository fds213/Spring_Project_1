package com.ieum.gw.subscription.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.subscription.dao.SubscriptionDAO;
import com.ieum.gw.subscription.dto.CompanyBillingDTO;
import com.ieum.gw.subscription.dto.ServicePeriodDTO;
import com.ieum.gw.subscription.service.SubscriptionService;

/** 언제 이용 상태를 읽는지 확인한다. 값 계산은 ServicePeriodsTest, 헤더에 실제로 보이는지는 Tomcat에서 본다 */
class SubscriptionHeaderAdviceTest {

    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,*/*;q=0.8";

    static class FakeService extends SubscriptionService {
        final List<Long> asked = new ArrayList<>();
        final CompanyBillingDTO billing = new CompanyBillingDTO();
        RuntimeException error;

        FakeService() {
            super(null, null);
        }

        @Override
        public CompanyBillingDTO getCompanyBilling(Long companyId) {
            asked.add(companyId);
            if (error != null) {
                throw error;
            }
            return billing;
        }
    }

    private final FakeService service = new FakeService();
    private final SubscriptionHeaderAdvice advice = new SubscriptionHeaderAdvice(service);

    private static MockHttpServletRequest request(String accept) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (accept != null) {
            request.addHeader("Accept", accept);
        }
        return request;
    }

    private static void loginAs(long companyId) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setRole("ROLE_EMP");
        emp.setCompanyId(companyId);
        LoginUser user = new LoginUser(emp);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    // 가짜 DAO의 이용 기간으로 실제 SubscriptionService가 값을 만든다
    private static SubscriptionHeaderAdvice adviceWith(ServicePeriodDTO... periods) {
        SubscriptionDAO dao = new SubscriptionDAO(null) {
            @Override
            public List<ServicePeriodDTO> listPeriods(Long companyId) {
                return List.of(periods);
            }
        };
        return new SubscriptionHeaderAdvice(new SubscriptionService(dao, null));
    }

    private static ServicePeriodDTO period(boolean trial, int startsDaysAgo, int endsDaysAgo) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        ServicePeriodDTO p = new ServicePeriodDTO();
        p.setTrial(trial);
        p.setStartsAt(now.minusDays(startsDaysAgo));
        p.setEndsAt(now.minusDays(endsDaysAgo));
        return p;
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("로그인한 사람이 화면을 열면 그 사람 업체의 이용 상태를 읽는다")
    void screenRequest() {
        loginAs(7L);

        assertSame(service.billing, advice.companyBilling(request(BROWSER_ACCEPT)));
        assertEquals(List.of(7L), service.asked);
    }

    @Test
    @DisplayName("JSON 요청(배지 15초 폴링 등)에는 DB를 읽지 않는다")
    void jsonRequestSkipsQuery() {
        loginAs(7L);

        assertNull(advice.companyBilling(request("application/json")));
        assertNull(advice.companyBilling(request("*/*")));
        assertNull(advice.companyBilling(request(null)));
        assertEquals(List.of(), service.asked);
    }

    @Test
    @DisplayName("로그인 전 화면(소개·체험 신청·로그인)에는 읽지 않는다")
    void anonymousSkipsQuery() {
        assertNull(advice.companyBilling(request(BROWSER_ACCEPT)));

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertNull(advice.companyBilling(request(BROWSER_ACCEPT)));
        assertEquals(List.of(), service.asked);
    }

    @Test
    @DisplayName("조회가 실패해도 화면은 그대로 연다(띠만 안 보인다)")
    void failureStillOpensScreen() {
        loginAs(7L);
        service.error = new IllegalStateException("DB 오류");

        assertNull(advice.companyBilling(request(BROWSER_ACCEPT)));
    }

    @Test
    @DisplayName("유료가 끝난 업체: 만료이면서 유료라 화면이 체험 만료와 가를 수 있다")
    void paidExpiredScreen() {
        loginAs(7L);
        SubscriptionHeaderAdvice paidExpired = adviceWith(period(true, 60, 46), period(false, 50, 20));

        CompanyBillingDTO billing = paidExpired.companyBilling(request(BROWSER_ACCEPT));

        assertEquals("EXPIRED", billing.getStatus());
        assertTrue(billing.isPaid());
    }

    @Test
    @DisplayName("체험이 끝난 업체: 만료이고 유료가 아니다")
    void trialExpiredScreen() {
        loginAs(7L);

        CompanyBillingDTO billing = adviceWith(period(true, 60, 46)).companyBilling(request(BROWSER_ACCEPT));

        assertEquals("EXPIRED", billing.getStatus());
        assertFalse(billing.isPaid());
    }

    @Test
    @DisplayName("체험 중과 유료 중: 상태와 유료 여부가 그대로 간다")
    void usingScreen() {
        loginAs(7L);

        CompanyBillingDTO trial = adviceWith(period(true, 3, -11)).companyBilling(request(BROWSER_ACCEPT));
        assertEquals("TRIAL", trial.getStatus());
        assertFalse(trial.isPaid());

        CompanyBillingDTO paid = adviceWith(period(true, 3, -11), period(false, 1, -29)).companyBilling(request(BROWSER_ACCEPT));
        assertEquals("PAID", paid.getStatus());
        assertTrue(paid.isPaid());
    }
}
