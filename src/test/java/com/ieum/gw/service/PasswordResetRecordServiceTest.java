package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dao.PasswordResetDAO;
import com.ieum.gw.dto.PasswordResetCodeDTO;
import com.ieum.gw.dto.PasswordResetTargetDTO;

class PasswordResetRecordServiceTest {

	static final String COMPANY = "ieum0001";
	static final String EMP = "20260003";

	/** 계정 하나와 EMAIL_VERIFICATION 행들을 메모리에 둔 가짜 DAO. UPDATE 조건은 XML과 같게 흉내 낸다 */
	static class FakeDao extends PasswordResetDAO {
		static class Row {
			PasswordResetCodeDTO c;
			boolean revoked;
			boolean consumed;
		}

		PasswordResetTargetDTO target = new PasswordResetTargetDTO();
		List<Row> rows = new ArrayList<>();
		int sentToday = 0;
		String passwordHash;

		FakeDao() {
			super(null);
			target.setCompanyId(1L);
			target.setCompanyCode(COMPANY);
			target.setUserAccountId(10L);
			target.setEmployeeNo(EMP);
			target.setEmail("it@ieum0001.test");
		}

		/** DB에서 읽은 값처럼 복사본을 준다 (나중에 행이 바뀌어도 읽은 값은 그대로) */
		static PasswordResetCodeDTO copy(PasswordResetCodeDTO c) {
			PasswordResetCodeDTO r = new PasswordResetCodeDTO();
			r.setEmailVerificationId(c.getEmailVerificationId());
			r.setCompanyId(c.getCompanyId());
			r.setUserAccountId(c.getUserAccountId());
			r.setEmail(c.getEmail());
			r.setTokenHash(c.getTokenHash());
			r.setIssuedAt(c.getIssuedAt());
			r.setExpiresAt(c.getExpiresAt());
			r.setVerifiedAt(c.getVerifiedAt());
			r.setFailedCount(c.getFailedCount());
			return r;
		}

		Row row(Long id) {
			return rows.get(id.intValue() - 1);
		}

		@Override
		public PasswordResetTargetDTO findTarget(String companyCode, String employeeNo) {
			return COMPANY.equals(companyCode) && EMP.equals(employeeNo) ? target : null;
		}

		@Override
		public PasswordResetTargetDTO findTargetByAccount(Long companyId, Long userAccountId) {
			return target;
		}

		@Override
		public Long lockAccount(Long companyId, Long userAccountId) {
			return userAccountId;
		}

		@Override
		public LocalDateTime getLastIssuedAt(Long companyId, Long userAccountId) {
			return rows.isEmpty() ? null : rows.get(rows.size() - 1).c.getIssuedAt();
		}

		@Override
		public int getSentCountToday(Long companyId, String email, LocalDate today) {
			return sentToday;
		}

		@Override
		public int revokeLive(Long companyId, Long userAccountId, LocalDateTime now) {
			int n = 0;
			for (Row r : rows) {
				if (!r.consumed && !r.revoked) {
					r.revoked = true;
					n++;
				}
			}
			return n;
		}

		@Override
		public int insertCode(PasswordResetCodeDTO code) {
			code.setEmailVerificationId((long) rows.size() + 1);
			Row r = new Row();
			r.c = copy(code);
			rows.add(r);
			return 1;
		}

		@Override
		public int recordSend(Long companyId, String email, LocalDate today, LocalDateTime now) {
			sentToday++;
			return 1;
		}

		@Override
		public PasswordResetCodeDTO findLiveCode(Long companyId, Long userAccountId) {
			for (int i = rows.size() - 1; i >= 0; i--) {
				Row r = rows.get(i);
				if (r.c.getVerifiedAt() == null && !r.consumed && !r.revoked) {
					return copy(r.c);
				}
			}
			return null;
		}

		@Override
		public int addFailure(Long id, Long companyId, int maxFailures, LocalDateTime now) {
			Row r = row(id);
			if (r.c.getVerifiedAt() != null || r.revoked) {
				return 0;
			}
			r.c.setFailedCount(r.c.getFailedCount() + 1);
			r.revoked = r.c.getFailedCount() >= maxFailures;
			return 1;
		}

		@Override
		public int markVerified(Long id, Long companyId, String tokenHash, LocalDateTime expiresAt, LocalDateTime now) {
			Row r = row(id);
			if (r.c.getVerifiedAt() != null || r.consumed || r.revoked || !r.c.getExpiresAt().isAfter(now)) {
				return 0;
			}
			r.c.setTokenHash(tokenHash);
			r.c.setVerifiedAt(now);
			r.c.setExpiresAt(expiresAt);
			return 1;
		}

		@Override
		public PasswordResetCodeDTO findByResetToken(String tokenHash) {
			for (Row r : rows) {
				if (tokenHash.equals(r.c.getTokenHash()) && r.c.getVerifiedAt() != null && !r.consumed && !r.revoked) {
					return copy(r.c);
				}
			}
			return null;
		}

		@Override
		public int consume(Long id, Long companyId, LocalDateTime now) {
			Row r = row(id);
			if (r.c.getVerifiedAt() == null || r.consumed || r.revoked || !r.c.getExpiresAt().isAfter(now)) {
				return 0;
			}
			r.consumed = true;
			return 1;
		}

		@Override
		public int updatePassword(Long companyId, Long userAccountId, String hash, LocalDateTime now) {
			passwordHash = hash;
			return 1;
		}
	}

	BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
	FakeDao dao;
	LocalDateTime clock = LocalDateTime.of(2026, 11, 5, 14, 0);
	PasswordResetRecordService service;

	@BeforeEach
	void setUp() {
		dao = new FakeDao();
		EmployeeService employeeService = new EmployeeService();
		ReflectionTestUtils.setField(employeeService, "passwordEncoder", encoder);
		service = new PasswordResetRecordService(dao, employeeService) {
			@Override
			LocalDateTime now() {
				return clock;
			}
		};
	}

	static HttpStatus status(Runnable r) {
		return assertThrows(ResponseStatusException.class, r::run).getStatus();
	}

	// ---------- 코드 보내기 ----------

	@Test
	@DisplayName("사용 중 계정이면 6자리 코드를 만들고 DB에는 해시만 남긴다")
	void issueStoresHashOnly() {
		PasswordResetCodeDTO code = service.issue(" IEUM0001 ", EMP);

		assertNotNull(code);
		assertTrue(code.getPlainCode().matches("\\d{6}"));
		assertEquals(TrialVerifyRecordService.sha256(code.getPlainCode()), dao.rows.get(0).c.getTokenHash());
		assertNotEquals(code.getPlainCode(), dao.rows.get(0).c.getTokenHash());
		assertEquals(clock.plusMinutes(10), dao.rows.get(0).c.getExpiresAt());
	}

	@Test
	@DisplayName("없는 업체 코드·사번이면 아무것도 저장하지 않는다 (화면에는 같은 안내)")
	void issueUnknown() {
		assertNull(service.issue(COMPANY, "29999999"));
		assertTrue(dao.rows.isEmpty());
		assertEquals(0, dao.sentToday);
	}

	@Test
	@DisplayName("1분 안에 다시 보내면 만들지 않고, 1분 뒤에는 새 코드를 만들며 이전 코드는 무효가 된다")
	void resendAfterOneMinute() {
		service.issue(COMPANY, EMP);
		clock = clock.plusSeconds(59);
		assertNull(service.issue(COMPANY, EMP));

		clock = clock.plusSeconds(1);
		assertNotNull(service.issue(COMPANY, EMP));
		assertTrue(dao.rows.get(0).revoked);
		assertFalse(dao.rows.get(1).revoked);
	}

	@Test
	@DisplayName("오늘 5번 보냈으면 더 만들지 않는다")
	void dailyLimit() {
		dao.sentToday = 5;
		assertNull(service.issue(COMPANY, EMP));
	}

	// ---------- 코드 확인 ----------

	@Test
	@DisplayName("맞는 코드면 resetToken을 주고, 같은 코드로 두 번은 확인할 수 없다")
	void verifyOk() {
		String plain = service.issue(COMPANY, EMP).getPlainCode();

		String token = service.verify(COMPANY, EMP, plain);

		assertNotNull(token);
		assertEquals(TrialVerifyRecordService.sha256(token), dao.rows.get(0).c.getTokenHash());
		assertEquals(HttpStatus.GONE, status(() -> service.verify(COMPANY, EMP, plain)));
	}

	@Test
	@DisplayName("틀리면 400과 남은 횟수, 5번째에 410이 되고 그 뒤에는 맞는 코드도 쓸 수 없다")
	void verifyFiveFailures() {
		String plain = service.issue(COMPANY, EMP).getPlainCode();
		String wrong = plain.equals("000000") ? "111111" : "000000";

		ResponseStatusException first = assertThrows(ResponseStatusException.class, () -> service.verify(COMPANY, EMP, wrong));
		assertEquals(HttpStatus.BAD_REQUEST, first.getStatus());
		assertTrue(first.getReason().contains("4번 남음"));
		for (int i = 0; i < 3; i++) {
			assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.verify(COMPANY, EMP, wrong)));
		}
		assertEquals(HttpStatus.GONE, status(() -> service.verify(COMPANY, EMP, wrong)));
		assertEquals(HttpStatus.GONE, status(() -> service.verify(COMPANY, EMP, plain)));
	}

	@Test
	@DisplayName("코드를 받고 10분이 지나면 410")
	void verifyExpired() {
		String plain = service.issue(COMPANY, EMP).getPlainCode();
		clock = clock.plusMinutes(10);

		assertEquals(HttpStatus.GONE, status(() -> service.verify(COMPANY, EMP, plain)));
	}

	@Test
	@DisplayName("없는 계정에 코드를 넣으면 틀렸을 때와 같은 400")
	void verifyUnknownAccount() {
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.verify(COMPANY, "29999999", "123456")));
	}

	// ---------- 새 비밀번호 ----------

	@Test
	@DisplayName("resetToken으로 새 비밀번호를 저장하고 업체 코드·사번을 돌려주며, 같은 표로 두 번은 바꿀 수 없다")
	void resetOk() {
		String token = service.verify(COMPANY, EMP, service.issue(COMPANY, EMP).getPlainCode());

		PasswordResetTargetDTO done = service.reset(token, "new-pass-1");

		assertEquals(COMPANY, done.getCompanyCode());
		assertEquals(EMP, done.getEmployeeNo());
		assertTrue(encoder.matches("new-pass-1", dao.passwordHash));
		assertEquals(HttpStatus.GONE, status(() -> service.reset(token, "new-pass-2")));
	}

	@Test
	@DisplayName("새 비밀번호가 8자 미만이면 400이고, 표는 그대로라 고쳐서 다시 하면 된다")
	void resetTooShortKeepsToken() {
		String token = service.verify(COMPANY, EMP, service.issue(COMPANY, EMP).getPlainCode());

		assertThrows(IllegalArgumentException.class, () -> service.reset(token, "1234567"));
		assertNull(dao.passwordHash);

		service.reset(token, "new-pass-1");
		assertNotNull(dao.passwordHash);
	}

	@Test
	@DisplayName("코드를 맞히고 10분이 지나면 새 비밀번호를 저장하지 않고 410")
	void resetExpired() {
		String token = service.verify(COMPANY, EMP, service.issue(COMPANY, EMP).getPlainCode());
		clock = clock.plusMinutes(10);

		assertEquals(HttpStatus.GONE, status(() -> service.reset(token, "new-pass-1")));
		assertNull(dao.passwordHash);
	}
}