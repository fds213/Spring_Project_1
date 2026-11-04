package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.CompanyUsageDTO;
import com.ieum.gw.dto.EmployeeHeadTargetDTO;
import com.ieum.gw.dto.LoginUser;

/** 계정 비활성화·다시 사용: 권한, 입력, 보호 계정, 부서장 칸, 자리, 퇴사자, 만료 (가짜 DAO·Service, DB 없음) */
class EmployeeAdminStatusTest {

	static final Long COMPANY = 11L;

	/** 사원 몇 명과 계정 상태표·부서장 칸을 들고 있는 가짜 EmployeeDAO. 상태 변경은 실제 SQL처럼 조건이 맞을 때만 1행 */
	static class FakeEmployeeDao extends EmployeeDAO {
		Map<String, EmployeeHeadTargetDTO> people = new HashMap<>();
		Map<Long, String> status = new HashMap<>();
		Long deptHead = null;
		int lockCalls = 0;

		FakeEmployeeDao() {
			super(null);
		}

		@Override
		public EmployeeHeadTargetDTO getHeadTarget(Long companyId, String employeeNo) {
			return COMPANY.equals(companyId) ? people.get(employeeNo) : null;
		}

		@Override
		public int retireAccount(Long companyId, Long employeeId) {
			if (!"ACTIVE".equals(status.get(employeeId))) {
				return 0;
			}
			status.put(employeeId, "RETIRED");
			return 1;
		}

		@Override
		public int reactivateAccount(Long companyId, Long employeeId) {
			if (!"RETIRED".equals(status.get(employeeId))) {
				return 0;
			}
			status.put(employeeId, "ACTIVE");
			return 1;
		}

		@Override
		public int clearHeadOf(Long companyId, Long employeeId) {
			if (!employeeId.equals(deptHead)) {
				return 0;
			}
			deptHead = null;
			return 1;
		}

		@Override
		public Integer lockLastSerial(Long companyId) {
			lockCalls++;
			return 5;
		}
	}

	/** 사용 중 계정 수를 정해 준다 */
	static class FakeEmployeeService extends EmployeeService {
		int inUse = 1;

		@Override
		public int countAccountsInUse(Long companyId) {
			return inUse;
		}
	}

	/** 이용 상태와 한도. usage가 null이면 이용 기간이 없는 업체(한도 없음) */
	static class FakeSubscriptionService extends SubscriptionService {
		CompanyUsageDTO usage = null;
		int limit = 10;

		FakeSubscriptionService() {
			super(null, null);
		}

		@Override
		public CompanyUsageDTO getCompanyUsage(Long companyId) {
			return usage;
		}

		@Override
		public int getSeatLimit(Long companyId) {
			return limit;
		}
	}

	private FakeEmployeeDao dao;
	private FakeEmployeeService employees;
	private FakeSubscriptionService subscription;
	private EmployeeAdminService service;
	private LoginUser admin;
	private LoginUser hr;

	@BeforeEach
	void setUp() {
		dao = new FakeEmployeeDao();
		add("20260001", 101L, "관리자", "ROLE_ADMIN", "ACTIVE", "ST_SERVING");
		add("20260002", 502L, "김개발", "ROLE_EMP", "ACTIVE", "ST_SERVING");
		add("20260003", 503L, "박휴직", "ROLE_EMP", "RETIRED", "ST_SERVING");
		add("20260004", 504L, "최퇴사", "ROLE_EMP", "RETIRED", "ST_RETIRED");
		add("20260005", 505L, "이인사", "ROLE_HR", "ACTIVE", "ST_SERVING");

		employees = new FakeEmployeeService();
		subscription = new FakeSubscriptionService();
		service = new EmployeeAdminService(dao, employees, subscription);
		admin = user(101L, "ROLE_ADMIN");
		hr = user(505L, "ROLE_HR");
	}

	private void add(String no, Long id, String name, String role, String status, String work) {
		EmployeeHeadTargetDTO t = new EmployeeHeadTargetDTO();
		t.setEmployeeId(id);
		t.setEmployeeName(name);
		t.setRoleCode(role);
		t.setStatusCode(status);
		t.setEmploymentStatus(work);
		dao.people.put(no, t);
		dao.status.put(id, status);
	}

	private static LoginUser user(Long employeeId, String role) {
		AuthEmpDTO emp = new AuthEmpDTO();
		emp.setCompanyId(COMPANY);
		emp.setUserAccountId(employeeId);
		emp.setEmployeeId(employeeId);
		emp.setRole(role);
		return new LoginUser(emp);
	}

	private void expired() {
		CompanyUsageDTO usage = new CompanyUsageDTO();
		usage.setExpired(true);
		subscription.usage = usage;
	}

	@Test
	@DisplayName("관리자와 인사 담당은 직원 계정을 끄고 다시 켤 수 있고, 같은 요청을 두 번 보내면 두 번째는 409다")
	void offAndOn() {
		assertEquals("김개발", service.setStatus(admin, "20260002", "off").get("name"));
		assertEquals("RETIRED", dao.status.get(502L));
		assertThrows(IllegalStateException.class, () -> service.setStatus(admin, "20260002", "off"));

		service.setStatus(hr, "20260002", "on");
		assertEquals("ACTIVE", dao.status.get(502L));
		assertThrows(IllegalStateException.class, () -> service.setStatus(hr, "20260002", "on"));
	}

	@Test
	@DisplayName("관리자·인사 담당이 아니면 403, action이 없거나 모르는 값이면 400이고 아무것도 바뀌지 않는다")
	void permissionAndInput() {
		assertThrows(AccessDeniedException.class, () -> service.setStatus(user(502L, "ROLE_EMP"), "20260002", "off"));
		assertThrows(IllegalArgumentException.class, () -> service.setStatus(admin, "20260002", null));
		assertThrows(IllegalArgumentException.class, () -> service.setStatus(admin, "20260002", "delete"));
		assertEquals("ACTIVE", dao.status.get(502L));
	}

	@Test
	@DisplayName("업체 관리자 계정과 본인 계정은 끌 수 없다(409)")
	void protectedAccounts() {
		assertThrows(IllegalStateException.class, () -> service.setStatus(hr, "20260001", "off"));
		assertThrows(IllegalStateException.class, () -> service.setStatus(hr, "20260005", "off"));
		assertEquals("ACTIVE", dao.status.get(101L));
		assertEquals("ACTIVE", dao.status.get(505L));
	}

	@Test
	@DisplayName("부서장을 끄면 부서장 칸도 비워진다")
	void offClearsHead() {
		dao.deptHead = 502L;

		service.setStatus(admin, "20260002", "off");
		assertNull(dao.deptHead);
	}

	@Test
	@DisplayName("빈 자리가 없으면 다시 사용은 409이고, 자리를 세기 전에 가입 승인과 같은 자물쇠를 잡는다")
	void seatFull() {
		subscription.usage = new CompanyUsageDTO(); // 이용 중, 만료 아님
		subscription.limit = 5;
		employees.inUse = 5;

		assertThrows(IllegalStateException.class, () -> service.setStatus(admin, "20260003", "on"));
		assertEquals("RETIRED", dao.status.get(503L));
		assertEquals(1, dao.lockCalls);

		employees.inUse = 4; // 한 자리가 비면 된다
		service.setStatus(admin, "20260003", "on");
		assertEquals("ACTIVE", dao.status.get(503L));
	}

	@Test
	@DisplayName("퇴사 처리된 사원은 다시 사용할 수 없다(409)")
	void retiredEmployee() {
		assertThrows(IllegalStateException.class, () -> service.setStatus(admin, "20260004", "on"));
		assertEquals("RETIRED", dao.status.get(504L));
	}

	@Test
	@DisplayName("만료 업체에서는 업체 관리자의 비활성화만 되고, 다시 사용과 인사 담당의 비활성화는 409다")
	void expiredCompany() {
		expired();

		assertThrows(IllegalStateException.class, () -> service.setStatus(admin, "20260003", "on"));
		assertThrows(IllegalStateException.class, () -> service.setStatus(hr, "20260002", "off"));
		assertEquals("ACTIVE", dao.status.get(502L));

		service.setStatus(admin, "20260002", "off");
		assertEquals("RETIRED", dao.status.get(502L));
	}

	@Test
	@DisplayName("다른 업체 사번은 없는 사원(400)이다")
	void otherCompany() {
		LoginUser otherAdmin = new LoginUser(new AuthEmpDTO() {
			{
				setCompanyId(99L);
				setEmployeeId(1L);
				setUserAccountId(1L);
				setRole("ROLE_ADMIN");
			}
		});
		assertThrows(IllegalArgumentException.class, () -> service.setStatus(otherAdmin, "20260002", "off"));
		assertEquals("ACTIVE", dao.status.get(502L));
	}
}