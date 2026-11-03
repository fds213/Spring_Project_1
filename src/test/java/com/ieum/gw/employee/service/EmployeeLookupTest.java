package com.ieum.gw.employee.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ieum.gw.employee.dao.EmployeeDAO;
import com.ieum.gw.employee.dto.EmployeeOptionDTO;
import com.ieum.gw.employee.dto.IdNameDTO;

class EmployeeLookupTest {

	/** 진짜 DB 대신 "몇 번, 무슨 값으로 불렸나"만 적어 두는 가짜 DAO */
	static class FakeDao extends EmployeeDAO {
		int calls = 0;
		Long lastCompanyId;
		List<String> lastRoles;

		FakeDao() {
			super(null); // 진짜 Mapper는 안 씀
		}

		@Override
		public List<IdNameDTO> listEmployeeNames(Long companyId, List<Long> ids) {
			calls++;
			lastCompanyId = companyId;
			List<IdNameDTO> rows = new ArrayList<>();
			for (Long id : ids) {
				IdNameDTO r = new IdNameDTO();
				r.setId(id);
				r.setName("직원" + id);
				rows.add(r);
			}
			return rows;
		}

		@Override
		public List<IdNameDTO> listAccountNames(Long companyId, List<Long> ids) {
			calls++;
			lastCompanyId = companyId;
			return new ArrayList<>();
		}

		@Override
		public List<EmployeeOptionDTO> listAssignableEmployees(Long companyId, List<String> roleCodes) {
			calls++;
			lastCompanyId = companyId;
			lastRoles = roleCodes;
			return new ArrayList<>();
		}
	}

	private EmployeeService service;
	private FakeDao fake;

	@BeforeEach
	void setUp() {
		service = new EmployeeService();
		fake = new FakeDao();
		ReflectionTestUtils.setField(service, "dao", fake); // @Autowired 필드에 가짜 넣기
	}

	@Test
	@DisplayName("직원 ID가 빈 목록이면 DB를 부르지 않고, 결과에 get(null)을 해도 오류가 없다")
	void emptyEmployeeIds() {
		Map<Long, String> names = service.getEmployeeNames(11L, List.of());

		assertEquals(0, fake.calls);
		assertTrue(names.isEmpty());
		assertNull(names.get(null)); // Map.of()였다면 여기서 NPE
	}

	@Test
	@DisplayName("계정 ID가 null이어도 DB를 부르지 않고 get(null)이 안전하다")
	void nullAccountIds() {
		Map<Long, String> names = service.getAccountNames(11L, null);

		assertEquals(0, fake.calls);
		assertNull(names.get(null));
	}

	@Test
	@DisplayName("직원 이름 조회는 받은 업체 ID를 그대로 넘기고 ID → 이름 Map으로 돌려준다")
	void passCompanyId() {
		Map<Long, String> names = service.getEmployeeNames(11L, List.of(101L, 105L));

		assertEquals(1, fake.calls);
		assertEquals(11L, fake.lastCompanyId);
		assertEquals("직원101", names.get(101L));
		assertEquals("직원105", names.get(105L));
	}

	@Test
	@DisplayName("담당자 목록은 역할을 안 주면 빈 목록, 주면 업체 ID와 역할을 그대로 넘긴다")
	void assignableRoles() {
		assertTrue(service.listAssignableEmployees(11L).isEmpty());
		assertEquals(0, fake.calls);

		service.listAssignableEmployees(11L, "ROLE_IT", "ROLE_ADMIN");
		assertEquals(1, fake.calls);
		assertEquals(11L, fake.lastCompanyId);
		assertEquals(List.of("ROLE_IT", "ROLE_ADMIN"), fake.lastRoles);
	}
}