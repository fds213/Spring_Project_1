package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dto.OrgDeptDTO;
import com.ieum.gw.dto.OrgMemberDTO;
import com.ieum.gw.service.EmployeeService;

class EmployeeOrgChartTest {

	/** 정해 둔 부서·사람 목록을 그대로 돌려주는 가짜 DAO (SQL 정렬은 이미 끝난 것으로 본다) */
	static class FakeDao extends EmployeeDAO {
		List<OrgDeptDTO> depts = new ArrayList<>();
		List<OrgMemberDTO> members = new ArrayList<>();
		Long askedCompanyId;

		FakeDao() {
			super(null);
		}

		@Override
		public List<OrgDeptDTO> listOrgDepartments(Long companyId) {
			askedCompanyId = companyId;
			return depts;
		}

		@Override
		public List<OrgMemberDTO> listOrgMembers(Long companyId) {
			return members;
		}
	}

	FakeDao dao;
	EmployeeService service;

	@BeforeEach
	void setUp() {
		dao = new FakeDao();
		service = new EmployeeService();
		ReflectionTestUtils.setField(service, "dao", dao);
	}

	static OrgDeptDTO dept(Long id, String name) {
		OrgDeptDTO d = new OrgDeptDTO();
		d.setDepartmentId(id);
		d.setDepartmentName(name);
		return d;
	}

	static OrgMemberDTO member(Long deptId, Long empId, String name) {
		OrgMemberDTO m = new OrgMemberDTO();
		m.setDepartmentId(deptId);
		m.setEmployeeId(empId);
		m.setEmployeeName(name);
		return m;
	}

	static List<String> names(OrgDeptDTO d) {
		return d.getMembers().stream().map(OrgMemberDTO::getEmployeeName).collect(Collectors.toList());
	}

	@Test
	@DisplayName("부서 카드는 받은 순서대로, 사람이 없는 부서도 빈 목록으로 남는다")
	void keepsDeptOrderAndEmptyDept() {
		dao.depts.add(dept(1L, "개발팀"));
		dao.depts.add(dept(2L, "영업팀"));
		dao.depts.add(dept(3L, "인사팀"));
		dao.members.add(member(2L, 20L, "김부장"));

		List<OrgDeptDTO> chart = service.getOrgChart(7L);

		assertEquals(List.of("개발팀", "영업팀", "인사팀"),
				chart.stream().map(OrgDeptDTO::getDepartmentName).collect(Collectors.toList()));
		assertTrue(chart.get(0).getMembers().isEmpty());
		assertEquals(List.of("김부장"), names(chart.get(1)));
		assertTrue(chart.get(2).getMembers().isEmpty());
		assertEquals(7L, dao.askedCompanyId);
	}

	@Test
	@DisplayName("사람은 자기 부서 카드에 들어가고, 카드 안 순서는 받은 순서(부서장·직급·이름) 그대로다")
	void keepsMemberOrderInCard() {
		dao.depts.add(dept(1L, "개발팀"));
		dao.depts.add(dept(2L, "영업팀"));
		dao.members.add(member(2L, 20L, "김부장"));   // 영업팀 부서장
		dao.members.add(member(1L, 10L, "이팀장"));   // 개발팀 부서장
		dao.members.add(member(2L, 21L, "박대리"));
		dao.members.add(member(1L, 11L, "최사원"));

		List<OrgDeptDTO> chart = service.getOrgChart(7L);

		assertEquals(List.of("이팀장", "최사원"), names(chart.get(0)));
		assertEquals(List.of("김부장", "박대리"), names(chart.get(1)));
	}

	@Test
	@DisplayName("부서가 없거나 목록에 없는 부서의 사람은 맨 끝 '부서 미지정'(id null) 카드에 모인다")
	void unassignedGoLast() {
		dao.depts.add(dept(1L, "개발팀"));
		dao.members.add(member(null, 1L, "관리자"));
		dao.members.add(member(1L, 10L, "이팀장"));
		dao.members.add(member(99L, 30L, "지운부서사람"));

		List<OrgDeptDTO> chart = service.getOrgChart(7L);

		assertEquals(2, chart.size());
		OrgDeptDTO last = chart.get(1);
		assertNull(last.getDepartmentId());
		assertNull(last.getDepartmentName());
		assertEquals(List.of("관리자", "지운부서사람"), names(last));
	}

	@Test
	@DisplayName("모두 부서가 있으면 '부서 미지정' 카드는 생기지 않는다")
	void noUnassignedCard() {
		dao.depts.add(dept(1L, "개발팀"));
		dao.members.add(member(1L, 10L, "이팀장"));

		List<OrgDeptDTO> chart = service.getOrgChart(7L);

		assertEquals(1, chart.size());
	}
}