package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ieum.gw.dao.EmployeeDAO;

class EmployeePasswordTest {

	static final Long COMPANY = 1L;
	static final Long ACCOUNT = 10L;

	/** 계정 하나의 비밀번호 해시만 들고 있는 가짜 DAO. UPDATE는 진짜 SQL처럼 확인한 해시가 그대로일 때만 바꾼다 */
	static class FakeDao extends EmployeeDAO {
		String hash;              // null이면 사용 중 계정이 아님
		String changedMeanwhile;  // 값이 있으면 UPDATE 직전에 다른 창이 이 해시로 바꾼 것으로 본다
		int updates = 0;

		FakeDao() {
			super(null);
		}

		@Override
		public String findPasswordHash(Long companyId, Long userAccountId) {
			return COMPANY.equals(companyId) && ACCOUNT.equals(userAccountId) ? hash : null;
		}

		@Override
		public int updatePassword(Long companyId, Long userAccountId, String oldHash, String newHash) {
			if (changedMeanwhile != null) {
				hash = changedMeanwhile;
			}
			if (hash == null || !hash.equals(oldHash)) {
				return 0;
			}
			hash = newHash;
			updates++;
			return 1;
		}
	}

	BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // 테스트라 강도를 낮춰 빠르게
	FakeDao dao;
	EmployeeService service;

	@BeforeEach
	void setUp() {
		dao = new FakeDao();
		dao.hash = encoder.encode("old-pass-1");
		service = new EmployeeService();
		ReflectionTestUtils.setField(service, "dao", dao);
		ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
	}

	@Test
	@DisplayName("현재 비밀번호가 맞으면 새 비밀번호로 바뀌고, 이전 비밀번호는 더 이상 맞지 않는다")
	void changes() {
		service.changePassword(COMPANY, ACCOUNT, "old-pass-1", "new-pass-1");

		assertEquals(1, dao.updates);
		assertTrue(encoder.matches("new-pass-1", dao.hash));
		assertFalse(encoder.matches("old-pass-1", dao.hash));
	}

	@Test
	@DisplayName("현재 비밀번호가 틀리면 400이고 저장하지 않는다")
	void wrongCurrent() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.changePassword(COMPANY, ACCOUNT, "wrong-pass", "new-pass-1"));

		assertEquals("현재 비밀번호가 맞지 않습니다", e.getMessage());
		assertEquals(0, dao.updates);
	}

	@Test
	@DisplayName("새 비밀번호가 8자 미만이면 400")
	void tooShort() {
		assertThrows(IllegalArgumentException.class,
				() -> service.changePassword(COMPANY, ACCOUNT, "old-pass-1", "1234567"));
		assertEquals(0, dao.updates);
	}

	@Test
	@DisplayName("새 비밀번호가 지금과 같으면 400")
	void sameAsNow() {
		assertThrows(IllegalArgumentException.class,
				() -> service.changePassword(COMPANY, ACCOUNT, "old-pass-1", "old-pass-1"));
		assertEquals(0, dao.updates);
	}

	@Test
	@DisplayName("사용 중 계정이 아니면(해시를 못 읽음) 409")
	void notActive() {
		dao.hash = null;

		assertThrows(IllegalStateException.class,
				() -> service.changePassword(COMPANY, ACCOUNT, "old-pass-1", "new-pass-1"));
	}

	@Test
	@DisplayName("확인한 뒤 그사이 다른 창에서 비밀번호가 바뀌었으면 409이고 덮어쓰지 않는다")
	void changedMeanwhile() {
		String other = encoder.encode("other-tab-1");
		dao.changedMeanwhile = other;

		assertThrows(IllegalStateException.class,
				() -> service.changePassword(COMPANY, ACCOUNT, "old-pass-1", "new-pass-1"));
		assertEquals(other, dao.hash); // 다른 창이 바꾼 값이 그대로 남음
	}
}