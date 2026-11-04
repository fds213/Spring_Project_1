package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dao.JoinDAO;
import com.ieum.gw.dto.EmployeeDTO;
import com.ieum.gw.dto.JoinRequestViewDTO;
import com.ieum.gw.dto.CompanyUsageDTO;

/** 가입 승인·조회·취소: 두 번 승인 막기, 업체 코드가 다르면 막기 (가짜 DAO, DB 없음) */
class JoinApproveTest {

	static final Long COMPANY = 11L;
	static final String CODE = "ieum0001";
	static final Long REQ = 1001L;

	/** 신청서 한 장만 들고 있는 가짜 JoinDAO. 상태를 바꾸는 SQL처럼 조건이 맞을 때만 바꾸고 바뀐 행 수를 돌려준다 */
	static class FakeJoinDao extends JoinDAO {
		JoinRequestViewDTO row;
		String accountStatus = "REQUESTED";
		String headDept = null; // 로그인한 사람이 부서장인 부서 (관리자 테스트는 null)
		int activateCalls = 0;
		int cancelCalls = 0;
		int rejectAccountCalls = 0;

		FakeJoinDao() {
			super(null);
		}

		@Override
		public String getHeadDeptName(Long companyId, Long employeeId) {
			return headDept;
		}

		@Override
		public JoinRequestViewDTO lockRequest(Long companyId, Long requestId) {
			return COMPANY.equals(companyId) && REQ.equals(requestId) ? row : null;
		}

		@Override
		public int activateAccount(Long companyId, Long userAccountId, Long employeeId) {
			activateCalls++;
			if (!"REQUESTED".equals(accountStatus)) {
				return 0;
			}
			accountStatus = "ACTIVE";
			return 1;
		}

		@Override
		public int approveRequest(Long companyId, Long requestId, Long reviewerId) {
			if (!"REQUESTED".equals(row.getRequestStatus())) {
				return 0;
			}
			row.setRequestStatus("APPROVED");
			return 1;
		}

		@Override
		public int rejectRequest(Long companyId, Long requestId, Long reviewerId) {
			if (!"REQUESTED".equals(row.getRequestStatus())) {
				return 0;
			}
			row.setRequestStatus("REJECTED");
			return 1;
		}

		@Override
		public int rejectAccount(Long companyId, Long userAccountId) {
			rejectAccountCalls++;
			if (!"REQUESTED".equals(accountStatus)) {
				return 0;
			}
			accountStatus = "REJECTED";
			return 1;
		}

		/** 업체 코드가 맞을 때만 신청을 찾는다 (실제 SQL: c.company_code = #{companyCode} AND r.request_key_hash = #{keyHash}) */
		@Override
		public JoinRequestViewDTO getRequestView(String companyCode, String keyHash) {
			return CODE.equals(companyCode) && keyHash != null ? row : null;
		}

		@Override
		public int cancelRequest(Long requestId, Long companyId) {
			cancelCalls++;
			if (!"REQUESTED".equals(row.getRequestStatus())) {
				return 0;
			}
			row.setRequestStatus("CANCELED");
			return 1;
		}

		@Override
		public int cancelAccount(Long userAccountId, Long companyId) {
			return 1;
		}
	}

	/** 사번 발급·사원 만들기를 몇 번 불렀는지 세고, 사용 중 계정 수를 정해 준다 */
	static class FakeEmployeeService extends EmployeeService {
		int issueCalls = 0;
		int createCalls = 0;
		int inUse = 1;

		@Override
		public int countAccountsInUse(Long companyId) {
			return inUse;
		}

		@Override
		String issueEmployeeNo(Long companyId) {
			issueCalls++;
			return "2026000" + issueCalls;
		}

		@Override
		EmployeeDTO createJoinedEmployee(Long companyId, String employeeNo, String name, Long departmentId,
				Long jobGradeId) {
			createCalls++;
			EmployeeDTO emp = new EmployeeDTO();
			emp.setEmployeeId(500L + createCalls);
			emp.setEmployeeNo(employeeNo);
			return emp;
		}
	}

	/** 좌석 한도. null이면 이용 기간이 없는 업체(한도 없음) */
	static class FakeSubscriptionService extends SubscriptionService {
		Integer limit = null;

		FakeSubscriptionService() {
			super(null, null);
		}

		@Override
		public CompanyUsageDTO getCompanyUsage(Long companyId) {
			return limit == null ? null : new CompanyUsageDTO();
		}

		@Override
		public int getSeatLimit(Long companyId) {
			return limit;
		}
	}

	private FakeJoinDao dao;
	private FakeEmployeeService employees;
	private FakeSubscriptionService subscription;
	private JoinService service;
	private LoginUser admin;

	@BeforeEach
	void setUp() {
		dao = new FakeJoinDao();
		JoinRequestViewDTO row = new JoinRequestViewDTO();
		row.setAccountRequestId(REQ);
		row.setCompanyId(COMPANY);
		row.setUserAccountId(141L);
		row.setRequestStatus("REQUESTED");
		row.setRequestName("테스트");
		row.setDepartmentName("개발팀");
		dao.row = row;

		employees = new FakeEmployeeService();
		subscription = new FakeSubscriptionService();
		service = new JoinService(dao, subscription, employees, new EmployeeDAO(null));
		admin = user(101L, "ROLE_ADMIN");
	}

	private static LoginUser user(Long employeeId, String role) {
		AuthEmpDTO emp = new AuthEmpDTO();
		emp.setCompanyId(COMPANY);
		emp.setUserAccountId(employeeId);
		emp.setEmployeeId(employeeId);
		emp.setRole(role);
		return new LoginUser(emp);
	}

	@Test
	@DisplayName("같은 가입 요청을 두 번 승인하면 두 번째는 409이고, 사번·사원·계정 연결은 한 번만 일어난다")
	void approveTwice() {
		Map<String, Object> first = service.approveRequest(admin, REQ);
		assertEquals("20260001", first.get("empNo"));

		assertThrows(IllegalStateException.class, () -> service.approveRequest(admin, REQ));

		assertEquals(1, employees.issueCalls);
		assertEquals(1, employees.createCalls);
		assertEquals(1, dao.activateCalls);
		assertEquals("APPROVED", dao.row.getRequestStatus());
	}

	@Test
	@DisplayName("다른 업체 코드로는 내 신청을 볼 수도 취소할 수도 없다")
	void otherCompanyCode() {
		String key = "1f2560d6-4ad5-4294-891b-0c7766fbc371";

		assertNull(service.getMyRequest("ieum9999", key));
		assertThrows(IllegalStateException.class, () -> service.cancelMyRequest("ieum9999", key));
		assertEquals(0, dao.cancelCalls);
		assertEquals("REQUESTED", dao.row.getRequestStatus());

		// 같은 키라도 맞는 업체 코드면 보인다 (대문자·공백도 정리해서 찾는다)
		assertNotNull(service.getMyRequest(" IEUM0001 ", key));
	}

	@Test
	@DisplayName("자리가 다 찼으면 승인은 409이고, 사원 생성과 계정 연결은 하지 않는다")
	void seatFull() {
		subscription.limit = 5;
		employees.inUse = 5;

		assertThrows(IllegalStateException.class, () -> service.approveRequest(admin, REQ));

		assertEquals(0, employees.createCalls);
		assertEquals(0, dao.activateCalls);
		assertEquals("REQUESTED", dao.row.getRequestStatus());

		// 한 자리가 비면 승인된다
		employees.inUse = 4;
		assertEquals("테스트", service.approveRequest(admin, REQ).get("name"));
	}

	@Test
	@DisplayName("부서장은 다른 부서의 가입 요청을 승인·반려할 수 없다(403)")
	void headOtherDept() {
		dao.headDept = "IT지원팀"; // 신청은 개발팀
		LoginUser head = user(103L, "ROLE_EMP");

		assertThrows(AccessDeniedException.class, () -> service.approveRequest(head, REQ));
		assertThrows(AccessDeniedException.class, () -> service.rejectRequest(head, REQ));

		assertEquals(0, employees.issueCalls);
		assertEquals(0, dao.rejectAccountCalls);
		assertEquals("REQUESTED", dao.row.getRequestStatus());
	}

	@Test
	@DisplayName("같은 요청을 두 번 반려하면 두 번째는 409이고, 계정 반려는 한 번만 일어난다")
	void rejectTwice() {
		assertEquals("테스트", service.rejectRequest(admin, REQ).get("name"));

		assertThrows(IllegalStateException.class, () -> service.rejectRequest(admin, REQ));

		assertEquals(1, dao.rejectAccountCalls);
		assertEquals("REJECTED", dao.row.getRequestStatus());
		assertEquals("REJECTED", dao.accountStatus);
	}

	@Test
	@DisplayName("없는 가입 요청은 404로 거절한다")
	void notFound() {
		assertThrows(JoinNotFoundException.class, () -> service.approveRequest(admin, 9999L));
		assertThrows(JoinNotFoundException.class, () -> service.rejectRequest(admin, 9999L));
	}
}