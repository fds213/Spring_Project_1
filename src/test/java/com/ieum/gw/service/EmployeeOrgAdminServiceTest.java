package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.code.EmployeeOrgKind;
import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.EmployeeLoginInfoDTO;
import com.ieum.gw.dto.EmployeeOrgItemDTO;
import com.ieum.gw.dto.LoginUser;

/** 부서·직급 관리: 이름 규칙, 개수, 삭제 조건, 되살리기, 순서, 권한 (가짜 DAO, DB 없음) */
class EmployeeOrgAdminServiceTest {

	static final Long COMPANY = 11L;
	static final EmployeeOrgKind DEPT = EmployeeOrgKind.DEPT;

	/** 부서(또는 직급) 한 행 */
	static class Row {
		Long id;
		String name;
		boolean active = true;
		int sort;
		int inUse;
		int requests;
	}

	/** 행 목록만 들고 있는 가짜 EmployeeDAO. 실제 SQL처럼 사용 중인 행만, 순서대로 돌려준다 */
	static class FakeEmployeeDao extends EmployeeDAO {
		List<Row> rows = new ArrayList<>();
		long nextId = 1;

		FakeEmployeeDao() {
			super(null);
		}

		/** 테스트 준비용: 사용 중인 행 하나 */
		Row put(String name, int inUse, int requests) {
			Row r = new Row();
			r.id = nextId++;
			r.name = name;
			r.sort = rows.size();
			r.inUse = inUse;
			r.requests = requests;
			rows.add(r);
			return r;
		}

		/** 이름으로 행 찾기 (사용 여부 상관없이) */
		Row row(String name) {
			return rows.stream().filter(r -> r.name.equals(name)).findFirst().orElse(null);
		}

		private List<EmployeeOrgItemDTO> active() {
			return rows.stream().filter(r -> r.active).sorted(Comparator.comparingInt((Row r) -> r.sort)).map(r -> {
				EmployeeOrgItemDTO o = new EmployeeOrgItemDTO();
				o.setId(r.id);
				o.setName(r.name);
				o.setInUse(r.inUse);
				o.setRequests(r.requests);
				return o;
			}).collect(Collectors.toList());
		}

		@Override
		public List<EmployeeOrgItemDTO> listOrgItems(Long companyId, EmployeeOrgKind kind) {
			return active();
		}

		@Override
		public List<EmployeeOrgItemDTO> lockOrgItems(Long companyId, EmployeeOrgKind kind) {
			return active();
		}

		@Override
		public Long findDeletedOrgId(Long companyId, EmployeeOrgKind kind, String name) {
			Row r = row(name);
			return r != null && !r.active ? r.id : null;
		}

		@Override
		public int insertOrg(Long companyId, EmployeeOrgKind kind, String name, int sortOrder) {
			put(name, 0, 0).sort = sortOrder;
			return 1;
		}

		@Override
		public int reviveOrg(Long companyId, EmployeeOrgKind kind, Long id, int sortOrder) {
			Row r = byId(id);
			r.active = true;
			r.sort = sortOrder;
			return 1;
		}

		@Override
		public int renameOrg(Long companyId, EmployeeOrgKind kind, Long id, String name) {
			byId(id).name = name;
			return 1;
		}

		@Override
		public int updateOrgSortOrder(Long companyId, EmployeeOrgKind kind, Long id, int sortOrder) {
			byId(id).sort = sortOrder;
			return 1;
		}

		@Override
		public int deleteOrg(Long companyId, EmployeeOrgKind kind, Long id) {
			byId(id).active = false;
			return 1;
		}

		private Row byId(Long id) {
			return rows.stream().filter(r -> r.id.equals(id)).findFirst().orElseThrow();
		}
	}

	/** 이름을 바꿀 때 내 헤더 문구를 찾는다. 테스트에서는 내 부서·직급이 없다고 본다 */
	static class FakeEmployeeService extends EmployeeService {
		@Override
		public EmployeeLoginInfoDTO getLoginInfo(Long companyId, Long employeeId) {
			return null;
		}
	}

	private FakeEmployeeDao dao;
	private EmployeeOrgAdminService service;
	private LoginUser admin;

	@BeforeEach
	void setUp() {
		dao = new FakeEmployeeDao();
		service = new EmployeeOrgAdminService(dao, new FakeEmployeeService());
		admin = user("ROLE_ADMIN");
	}

	private static LoginUser user(String role) {
		AuthEmpDTO emp = new AuthEmpDTO();
		emp.setCompanyId(COMPANY);
		emp.setUserAccountId(101L);
		emp.setEmployeeId(101L);
		emp.setRole(role);
		return new LoginUser(emp);
	}

	@Test
	@DisplayName("이름 규칙: 공백·대소문자만 다른 이름은 409, 빈 이름·주소에 못 쓰는 글자·21자는 400, 직급 '관리자'는 400")
	void nameRules() {
		service.add(admin, DEPT, " 개발팀 ");
		assertTrue(dao.row("개발팀").active); // 앞뒤 공백은 지운다

		assertThrows(IllegalStateException.class, () -> service.add(admin, DEPT, "개발 팀"));
		assertThrows(IllegalArgumentException.class, () -> service.add(admin, DEPT, "  "));
		assertThrows(IllegalArgumentException.class, () -> service.add(admin, DEPT, "a/b"));
		assertThrows(IllegalArgumentException.class, () -> service.add(admin, DEPT, "가".repeat(21)));
		assertThrows(IllegalArgumentException.class, () -> service.add(admin, EmployeeOrgKind.RANK, "관 리 자"));
		assertEquals(1, dao.rows.size());
	}

	@Test
	@DisplayName("부서는 30개까지 만들 수 있다(넘으면 409)")
	void maxCount() {
		for (int i = 0; i < 30; i++) {
			dao.put("부서" + i, 0, 0);
		}
		assertThrows(IllegalStateException.class, () -> service.add(admin, DEPT, "새부서"));
	}

	@Test
	@DisplayName("사용 중 계정이나 가입 요청이 있으면 못 지우고, 마지막 하나도 못 지운다. 비어 있으면 지운다")
	void deleteRules() {
		dao.put("개발팀", 1, 0);
		dao.put("영업팀", 0, 1);
		dao.put("기획팀", 0, 0);

		assertThrows(IllegalStateException.class, () -> service.delete(admin, DEPT, "개발팀"));
		assertThrows(IllegalStateException.class, () -> service.delete(admin, DEPT, "영업팀"));
		service.delete(admin, DEPT, "기획팀");
		assertFalse(dao.row("기획팀").active);

		dao.row("개발팀").active = false; // 하나만 남은 상황
		assertThrows(IllegalStateException.class, () -> service.delete(admin, DEPT, "영업팀"));
	}

	@Test
	@DisplayName("지운 이름을 다시 추가하면 새 행을 만들지 않고 되살린다(업체·이름 고유 제약)")
	void reviveSameName() {
		dao.put("개발팀", 0, 0);
		Row sales = dao.put("영업팀", 0, 0);
		service.delete(admin, DEPT, "영업팀");

		service.add(admin, DEPT, "영업팀");

		assertEquals(2, dao.rows.size());
		assertTrue(sales.active);
	}

	@Test
	@DisplayName("순서를 바꾸면 옆과 자리를 바꾸고 전체를 0부터 다시 매긴다(처음 순서 값이 모두 같아도 된다)")
	void moveRenumbers() {
		dao.put("가", 0, 0).sort = 0;
		dao.put("나", 0, 0).sort = 0;
		dao.put("다", 0, 0).sort = 0;

		service.move(admin, DEPT, "다", -1);

		assertEquals(0, dao.row("가").sort);
		assertEquals(1, dao.row("다").sort);
		assertEquals(2, dao.row("나").sort);
		assertThrows(IllegalStateException.class, () -> service.move(admin, DEPT, "가", -1));
	}

	@Test
	@DisplayName("없는 이름은 404(다른 탭에서 지운 경우), 업체 관리자가 아니면 403")
	void notFoundAndAdmin() {
		dao.put("개발팀", 0, 0);

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> service.rename(admin, DEPT, "없는팀", "새이름"));
		assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
		assertThrows(AccessDeniedException.class, () -> service.list(user("ROLE_HR"), DEPT));
	}
}