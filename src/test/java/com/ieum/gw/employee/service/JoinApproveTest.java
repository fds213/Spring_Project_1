package com.ieum.gw.employee.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.employee.dao.EmployeeDAO;
import com.ieum.gw.employee.dao.JoinDAO;
import com.ieum.gw.employee.dto.EmployeeDTO;
import com.ieum.gw.employee.dto.JoinRequestViewDTO;
import com.ieum.gw.subscription.dto.CompanyUsageDTO;
import com.ieum.gw.subscription.service.SubscriptionService;

/** 가입 승인·조회·취소: 두 번 승인 막기, 업체 코드가 다르면 막기 (가짜 DAO, DB 없음) */
class JoinApproveTest {

	static final Long COMPANY = 11L;
	static final String CODE = "ieum0001";
	static final Long REQ = 1001L;

	/** 신청서 한 장만 들고 있는 가짜 JoinDAO. 상태를 바꾸는 SQL처럼 조건이 맞을 때만 바꾸고 바뀐 행 수를 돌려준다 */
	static class FakeJoinDao extends JoinDAO {
		JoinRequestViewDTO row;
		String accountStatus = "REQUESTED";
		int activateCalls = 0;
		int cancelCalls = 0;

		FakeJoinDao() {
			super(null);
		}

		@Override
		public String getHeadDeptName(Long companyId, Long employeeId) {
			return null; // 테스트 사용자는 관리자라 부서장 여부는 상관없다
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
		public int countActiveAccounts(Long companyId) {
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

	/** 사번 발급·사원 만들기를 몇 번 불렀는지만 센다 */
	static class FakeEmployeeService extends EmployeeService {
		int issueCalls = 0;
		int createCalls = 0;

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

	/** 이용 기간이 없는 업체처럼(한도 없음) 동작 → 좌석 검사는 이 테스트 범위 밖 */
	static class FakeSubscriptionService extends SubscriptionService {
		FakeSubscriptionService() {
			super(null, null);
		}

		@Override
		public CompanyUsageDTO getCompanyUsage(Long companyId) {
			return null;
		}
	}

	private FakeJoinDao dao;
	private FakeEmployeeService employees;
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
		service = new JoinService(dao, new FakeSubscriptionService(), employees, new EmployeeDAO(null));

		AuthEmpDTO emp = new AuthEmpDTO();
		emp.setCompanyId(COMPANY);
		emp.setUserAccountId(1L);
		emp.setEmployeeId(101L);
		emp.setRole("ROLE_ADMIN");
		admin = new LoginUser(emp);
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
}