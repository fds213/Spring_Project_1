package com.ieum.gw.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.CompanyUsageDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.SubscriptionService;

/** 만료 읽기 전용 검사: 만료 업체의 쓰기는 409, 조회·허용된 쓰기·정상 업체는 통과 (가짜 구독 Service, DB 없음) */
class ReadOnlyInterceptorTest {

	/** 업체 이용 상태를 마음대로 정해 주는 가짜. usage가 null이면 이용 기간이 없는 운영 업체 */
	static class FakeSubscriptionService extends SubscriptionService {
		CompanyUsageDTO usage;

		FakeSubscriptionService() {
			super(null, null);
		}

		@Override
		public CompanyUsageDTO getCompanyUsage(Long companyId) {
			return usage;
		}
	}

	private FakeSubscriptionService subscription;
	private ReadOnlyInterceptor interceptor;
	private MockHttpServletResponse response;

	@BeforeEach
	void setUp() {
		subscription = new FakeSubscriptionService();
		interceptor = new ReadOnlyInterceptor(subscription);
	}

	/** 로그인 정보는 스레드에 남아서 다른 테스트로 새어 나가지 않게 매번 지운다 */
	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private void login(String role) {
		AuthEmpDTO emp = new AuthEmpDTO();
		emp.setCompanyId(11L);
		emp.setUserAccountId(101L);
		emp.setEmployeeId(101L);
		emp.setRole(role);
		LoginUser user = new LoginUser(emp);
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
	}

	private void expired(boolean expired) {
		CompanyUsageDTO usage = new CompanyUsageDTO();
		usage.setExpired(expired);
		subscription.usage = usage;
	}

	/** 실제 서버처럼 앞에 /gw 가 붙은 주소로 보낸다 */
	private boolean send(String method, String path) throws IOException {
		MockHttpServletRequest request = new MockHttpServletRequest(method, "/gw" + path);
		request.setContextPath("/gw");
		response = new MockHttpServletResponse();
		return interceptor.preHandle(request, response, null);
	}

	@Test
	@DisplayName("만료 업체가 화면을 거치지 않고 쓰기 요청을 보내도 409로 거절하고 안내 문구를 돌려준다")
	void expiredWriteBlocked() throws IOException {
		login("ROLE_ADMIN");
		expired(true);

		assertFalse(send("POST", "/employee/api/join/requests/1001/approve"));
		assertEquals(409, response.getStatus());
		assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("읽기 전용"));
	}

	@Test
	@DisplayName("만료 업체라도 조회(GET)는 그대로 된다")
	void expiredReadAllowed() throws IOException {
		login("ROLE_EMP");
		expired(true);

		assertTrue(send("GET", "/approval/api/docs"));
	}

	@Test
	@DisplayName("만료 업체라도 본인 비밀번호 변경은 되고, 결제는 업체 관리자만 된다")
	void expiredAllowedWrites() throws IOException {
		expired(true);

		login("ROLE_EMP");
		assertTrue(send("POST", "/employee/api/password"));
		assertFalse(send("POST", "/subscription/api/orders"));
		assertEquals(409, response.getStatus());

		login("ROLE_ADMIN");
		assertTrue(send("POST", "/subscription/api/orders"));
		assertTrue(send("POST", "/subscription/api/orders/confirm"));
	}

	@Test
	@DisplayName("만료되지 않은 업체와 이용 기간이 없는 운영 업체의 쓰기는 막지 않는다")
	void notExpiredAllowed() throws IOException {
		login("ROLE_EMP");

		expired(false);
		assertTrue(send("POST", "/approval/api/docs"));

		subscription.usage = null;
		assertTrue(send("POST", "/approval/api/docs"));
	}

	@Test
	@DisplayName("로그인하지 않은 요청(가입 신청 등)은 검문소가 막지 않는다")
	void anonymousAllowed() throws IOException {
		expired(true);

		assertTrue(send("POST", "/join/api/companies/ieum0001/requests"));
	}
	
	@Test
	@DisplayName("만료 업체에서도 업체 관리자의 직원 상태 변경 주소는 통과하고(off만 Service가 허용), 인사 담당과 다른 사원 관리 주소는 409다")
	void expiredStaffStatus() throws IOException {
		expired(true);

		login("ROLE_ADMIN");
		assertTrue(send("POST", "/employee/admin/api/employees/20260002/status"));
		assertFalse(send("POST", "/employee/admin/api/employees/20260002/head"));
		assertEquals(409, response.getStatus());

		login("ROLE_HR");
		assertFalse(send("POST", "/employee/admin/api/employees/20260002/status"));
		assertEquals(409, response.getStatus());
	}
}