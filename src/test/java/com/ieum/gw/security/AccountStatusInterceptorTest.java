package com.ieum.gw.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.EmployeeService;

/** 계정 상태 검사: 꺼진 계정의 기존 세션은 끊고, 사용 중이거나 로그인하지 않은 요청은 통과 (가짜 직원 Service, DB 없음) */
class AccountStatusInterceptorTest {

	/** 지금도 사용 중인지, DB의 역할이 무엇인지 마음대로 정해 주는 가짜 */
	static class FakeEmployeeService extends EmployeeService {
		boolean active = true;
		String role = "ROLE_HR";   // login()의 세션 역할과 같게 시작

		@Override
		public String getActiveRole(Long companyId, Long employeeId) {
			return active ? role : null;
		}
	}

	private FakeEmployeeService employees;
	private AccountStatusInterceptor interceptor;
	private MockHttpSession session;
	private MockHttpServletResponse response;

	@BeforeEach
	void setUp() {
		employees = new FakeEmployeeService();
		interceptor = new AccountStatusInterceptor(employees);
		session = new MockHttpSession();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private void login() {
		AuthEmpDTO emp = new AuthEmpDTO();
		emp.setCompanyId(11L);
		emp.setUserAccountId(141L);
		emp.setEmployeeId(141L);
		emp.setRole("ROLE_HR");
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(emp), null, List.of()));
	}

	/** 실제 서버처럼 /gw가 붙은 주소로, 세션을 들고 보낸다. ajax면 jQuery가 붙이는 이름표를 단다 */
	private boolean send(String path, boolean ajax) throws IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/gw" + path);
		request.setContextPath("/gw");
		request.setSession(session);
		if (ajax) {
			request.addHeader("X-Requested-With", "XMLHttpRequest");
		}
		response = new MockHttpServletResponse();
		return interceptor.preHandle(request, response, null);
	}

	@Test
	@DisplayName("로그인하지 않은 요청과 사용 중인 계정의 요청은 통과하고 세션도 그대로다")
	void passes() throws IOException {
		employees.active = false;
		assertTrue(send("/login", false)); // 로그인 전에는 계정 상태를 보지 않는다

		login();
		employees.active = true;
		assertTrue(send("/home", false));
		assertFalse(session.isInvalid());
	}

	@Test
	@DisplayName("비활성화된 계정이 기존 세션으로 화면을 열면 세션을 끊고 사용 중지 안내가 있는 로그인 화면으로 보낸다")
	void inactivePage() throws IOException {
		login();
		employees.active = false;

		assertFalse(send("/home", false));
		assertEquals("/gw/login?disabled", response.getRedirectedUrl());
		assertTrue(session.isInvalid());
		assertNull(SecurityContextHolder.getContext().getAuthentication());
	}

	@Test
	@DisplayName("비활성화된 계정이 기존 세션으로 API(Ajax)를 부르면 401이고 세션을 끊는다")
	void inactiveAjax() throws IOException {
		login();
		employees.active = false;

		assertFalse(send("/employee/admin/api/employees/20260002/status", true));
		assertEquals(401, response.getStatus());
		assertTrue(session.isInvalid());
	}

	@Test
	@DisplayName("역할이 바뀐 계정이 기존 세션으로 화면을 열면 세션을 끊고 다시 로그인 안내(?changed)로 보낸다")
	void roleChangedPage() throws IOException {
		login();                       // 세션 역할 ROLE_HR
		employees.role = "ROLE_EMP";   // 관리자가 직원으로 내림

		assertFalse(send("/home", false));
		assertEquals("/gw/login?changed", response.getRedirectedUrl());
		assertTrue(session.isInvalid());
		assertNull(SecurityContextHolder.getContext().getAuthentication());
	}

	@Test
	@DisplayName("역할이 바뀐 계정이 옛 권한으로 API(Ajax)를 부르면 401이고 세션을 끊는다 (#175 리뷰: 인사 담당→직원 뒤 비활성화 요청)")
	void roleChangedAjax() throws IOException {
		login();
		employees.role = "ROLE_EMP";

		assertFalse(send("/employee/admin/api/employees/20260002/status", true));
		assertEquals(401, response.getStatus());
		assertTrue(session.isInvalid());
	}
}