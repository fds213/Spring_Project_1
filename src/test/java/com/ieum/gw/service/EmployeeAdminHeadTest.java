package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.EmployeeHeadTargetDTO;
import com.ieum.gw.dto.LoginUser;

/** 부서장 지정·해제: 관리자만, 같은 업체만, 상태 검사, 해제 0행, 입력 누락 (가짜 DAO, DB 없음) */
class EmployeeAdminHeadTest {

	static final Long COMPANY = 11L;
	static final Long DEPT = 7L;

	/** 사원 몇 명과 부서장 칸 하나만 들고 있는 가짜 EmployeeDAO. 해제는 실제 SQL처럼 지금 부서장일 때만 1행 */
	static class FakeEmployeeDao extends EmployeeDAO {
		Map<String, EmployeeHeadTargetDTO> people = new HashMap<>();
		Long deptHead = null;
		int setCalls = 0;
		int clearCalls = 0;

		FakeEmployeeDao() {
			super(null);
		}

		/** 실제 SQL처럼 업체가 맞을 때만 찾는다 */
		@Override
		public EmployeeHeadTargetDTO getHeadTarget(Long companyId, String employeeNo) {
			return COMPANY.equals(companyId) ? people.get(employeeNo) : null;
		}

		@Override
		public int setDeptHead(Long companyId, Long departmentId, Long employeeId) {
			setCalls++;
			deptHead = employeeId;
			return 1;
		}

		@Override
		public int clearDeptHead(Long companyId, Long departmentId, Long employeeId) {
			clearCalls++;
			if (!employeeId.equals(deptHead)) {
				return 0;
			}
			deptHead = null;
			return 1;
		}
	}

	private FakeEmployeeDao dao;
	private EmployeeAdminService service;
	private LoginUser admin;

	@BeforeEach
	void setUp() {
		dao = new FakeEmployeeDao();
		dao.people.put("20260002", person(502L, "김개발", DEPT, "ACTIVE"));
		dao.people.put("20260003", person(503L, "박퇴사", DEPT, "RETIRED"));
		dao.people.put("20260004", person(504L, "이무소속", null, "ACTIVE"));
		service = new EmployeeAdminService(dao, null, null, null); // setHead는 DAO만 쓴다
		admin = user(COMPANY, "ROLE_ADMIN");
	}

	private static EmployeeHeadTargetDTO person(Long id, String name, Long deptId, String status) {
		EmployeeHeadTargetDTO t = new EmployeeHeadTargetDTO();
		t.setEmployeeId(id);
		t.setEmployeeName(name);
		t.setDepartmentId(deptId);
		t.setDepartmentName(deptId == null ? null : "개발팀");
		t.setStatusCode(status);
		return t;
	}

	private static LoginUser user(Long companyId, String role) {
		AuthEmpDTO emp = new AuthEmpDTO();
		emp.setCompanyId(companyId);
		emp.setUserAccountId(101L);
		emp.setEmployeeId(101L);
		emp.setRole(role);
		return new LoginUser(emp);
	}

	@Test
	@DisplayName("업체 관리자가 지정하면 그 부서의 부서장이 되고, 해제하면 비워진다")
	void setAndClear() {
		Map<String, Object> r = service.setHead(admin, "20260002", true);
		assertEquals(true, r.get("head"));
		assertEquals("개발팀", r.get("dept"));
		assertEquals(502L, dao.deptHead);

		service.setHead(admin, "20260002", false);
		assertNull(dao.deptHead);
	}

	@Test
	@DisplayName("업체 관리자가 아니면(인사 담당·IT 담당·직원) 403이고 부서장은 바뀌지 않는다")
	void notAdmin() {
		for (String role : List.of("ROLE_HR", "ROLE_IT", "ROLE_EMP")) {
			assertThrows(AccessDeniedException.class, () -> service.setHead(user(COMPANY, role), "20260002", true));
		}
		assertEquals(0, dao.setCalls);
		assertNull(dao.deptHead);
	}

	@Test
	@DisplayName("다른 업체 관리자가 이 업체 사번을 보내면 없는 사원(400)이고 아무것도 바뀌지 않는다")
	void otherCompany() {
		assertThrows(IllegalArgumentException.class, () -> service.setHead(user(99L, "ROLE_ADMIN"), "20260002", true));
		assertEquals(0, dao.setCalls);
		assertNull(dao.deptHead);
	}

	@Test
	@DisplayName("부서가 없는 사원과 사용 중이 아닌 계정은 부서장으로 지정할 수 없다(409)")
	void cannotAssign() {
		assertThrows(IllegalStateException.class, () -> service.setHead(admin, "20260004", true));
		assertThrows(IllegalStateException.class, () -> service.setHead(admin, "20260003", true));
		assertEquals(0, dao.setCalls);
	}

	@Test
	@DisplayName("이미 부서장이 아닌 사람을 해제하면 409이고 지금 부서장은 그대로다")
	void clearNotHead() {
		dao.deptHead = 999L; // 그사이 다른 사람이 부서장이 됐다

		assertThrows(IllegalStateException.class, () -> service.setHead(admin, "20260002", false));
		assertEquals(999L, dao.deptHead);
	}

	@Test
	@DisplayName("head 값을 보내지 않으면 400이고 지정도 해제도 하지 않는다")
	void missingHead() {
		dao.deptHead = 502L;

		assertThrows(IllegalArgumentException.class, () -> service.setHead(admin, "20260002", null));
		assertEquals(0, dao.setCalls);
		assertEquals(0, dao.clearCalls);
		assertEquals(502L, dao.deptHead);
	}
}