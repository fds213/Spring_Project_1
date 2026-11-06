package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.EmployeeHeadTargetDTO;
import com.ieum.gw.dto.LoginUser;

/** 역할 바꾸기: 관리자만, 직원·IT·인사끼리만, 관리자·본인 거절, 같은 업체만, 동시 변경 (가짜 DAO, DB 없음) */
class EmployeeAdminRoleTest {

	static final Long COMPANY = 11L;
	static final Long ME = 101L;

	/** 사번별 사원과 역할을 들고 있는 가짜 EmployeeDAO. updateRole은 실제 SQL처럼 직원·IT·인사일 때만 1행 */
	static class FakeEmployeeDao extends EmployeeDAO {
		static final Set<String> CHANGEABLE = Set.of("ROLE_EMP", "ROLE_IT", "ROLE_HR");
		Map<String, EmployeeHeadTargetDTO> people = new HashMap<>();
		Map<Long, String> roles = new HashMap<>();   // employeeId → 지금 DB의 역할
		int updates = 0;

		FakeEmployeeDao() {
			super(null);
		}

		/** 실제 SQL처럼 업체가 맞을 때만 찾는다 */
		@Override
		public EmployeeHeadTargetDTO getHeadTarget(Long companyId, String employeeNo) {
			return COMPANY.equals(companyId) ? people.get(employeeNo) : null;
		}

		@Override
		public int updateRole(Long companyId, Long employeeId, String roleCode) {
			if (!COMPANY.equals(companyId) || !CHANGEABLE.contains(roles.get(employeeId))) {
				return 0;
			}
			roles.put(employeeId, roleCode);
			updates++;
			return 1;
		}

		/** 사원 한 명 넣기 (찾을 때 보이는 역할과 DB 역할을 같게) */
		void add(String empNo, Long id, String name, String role) {
			EmployeeHeadTargetDTO t = new EmployeeHeadTargetDTO();
			t.setEmployeeId(id);
			t.setEmployeeName(name);
			t.setRoleCode(role);
			t.setStatusCode("ACTIVE");
			people.put(empNo, t);
			roles.put(id, role);
		}
	}

	private FakeEmployeeDao dao;
	private EmployeeAdminService service;
	private LoginUser admin;

	@BeforeEach
	void setUp() {
		dao = new FakeEmployeeDao();
		dao.add("20260001", ME, "관리자", "ROLE_ADMIN");
		dao.add("20260002", 502L, "김직원", "ROLE_EMP");
		dao.add("20260003", 503L, "이아이티", "ROLE_IT");
		service = new EmployeeAdminService(dao, null, null, null); // setRole은 DAO만 쓴다
		admin = user(COMPANY, "ROLE_ADMIN");
	}

	private static LoginUser user(Long companyId, String role) {
		AuthEmpDTO emp = new AuthEmpDTO();
		emp.setCompanyId(companyId);
		emp.setUserAccountId(ME);
		emp.setEmployeeId(ME);
		emp.setRole(role);
		return new LoginUser(emp);
	}

	@Test
	@DisplayName("업체 관리자가 직원을 IT 담당으로 바꾸면 역할이 바뀌고 이름·역할을 돌려준다")
	void change() {
		Map<String, Object> r = service.setRole(admin, "20260002", "ROLE_IT");

		assertEquals("ROLE_IT", dao.roles.get(502L));
		assertEquals("김직원", r.get("name"));
		assertEquals("ROLE_IT", r.get("role"));
	}

	@Test
	@DisplayName("인사 담당·IT 담당·직원이 부르면 403이고 역할은 바뀌지 않는다 (Security는 인사 담당을 통과시킴)")
	void notAdmin() {
		for (String role : List.of("ROLE_HR", "ROLE_IT", "ROLE_EMP")) {
			assertThrows(AccessDeniedException.class,
					() -> service.setRole(user(COMPANY, role), "20260002", "ROLE_HR"));
		}
		assertEquals("ROLE_EMP", dao.roles.get(502L));
		assertEquals(0, dao.updates);
	}

	@Test
	@DisplayName("직원·IT 담당·인사 담당이 아닌 역할(업체 관리자·운영자·빈 값)은 400")
	void onlyGrantableRoles() {
		for (String role : new String[] { "ROLE_ADMIN", "ROLE_OPS", null }) {
			assertThrows(IllegalArgumentException.class, () -> service.setRole(admin, "20260002", role));
		}
		assertEquals(0, dao.updates);
	}

	@Test
	@DisplayName("대상이 업체 관리자이거나 본인이면 409")
	void adminOrSelf() {
		assertThrows(IllegalStateException.class, () -> service.setRole(admin, "20260001", "ROLE_EMP"));

		dao.add("20260009", ME, "본인", "ROLE_EMP"); // 본인 검사가 역할 검사와 따로 동작하는지
		assertThrows(IllegalStateException.class, () -> service.setRole(admin, "20260009", "ROLE_IT"));
		assertEquals(0, dao.updates);
	}

	@Test
	@DisplayName("다른 업체 사번은 찾지 못해 400")
	void otherCompany() {
		LoginUser otherAdmin = user(99L, "ROLE_ADMIN");

		assertThrows(IllegalArgumentException.class, () -> service.setRole(otherAdmin, "20260002", "ROLE_IT"));
		assertEquals("ROLE_EMP", dao.roles.get(502L));
	}

	@Test
	@DisplayName("확인한 뒤 그사이 업체 관리자가 된 계정이면 UPDATE가 0행이라 409")
	void becameAdminMeanwhile() {
		dao.roles.put(503L, "ROLE_ADMIN"); // 찾을 때는 IT 담당, DB는 이미 관리자

		assertThrows(IllegalStateException.class, () -> service.setRole(admin, "20260003", "ROLE_EMP"));
		assertEquals("ROLE_ADMIN", dao.roles.get(503L));
	}

	@Test
	@DisplayName("사용 중이 아닌(비활성) 계정은 409이고 역할은 바뀌지 않는다 (#175 리뷰)")
	void notActive() {
		dao.add("20260004", 504L, "최퇴사", "ROLE_EMP");
		dao.people.get("20260004").setStatusCode("RETIRED");

		assertThrows(IllegalStateException.class, () -> service.setRole(admin, "20260004", "ROLE_IT"));
		assertEquals("ROLE_EMP", dao.roles.get(504L));
	}
}