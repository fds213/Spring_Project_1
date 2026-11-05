package com.ieum.gw.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dao.PasswordResetDAO;
import com.ieum.gw.dto.PasswordResetCodeDTO;
import com.ieum.gw.dto.PasswordResetTargetDTO;

import lombok.RequiredArgsConstructor;

/**
 * 비밀번호 찾기(ACC-07)의 DB 트랜잭션. 메일은 트랜잭션 밖이어야 해서 순서는 PasswordResetService가 정하고 저장만 여기로 뗐다.
 * 발급·확인·변경 모두 계정 행을 잠근다: 동시에 눌러도 살아 있는 코드는 하나이고 제한도 뚫리지 않는다.
 * 코드와 resetToken은 SHA-256 해시로만 저장한다.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetRecordService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();
    static final int CODE_MINUTES = 10;          // 코드·resetToken 사용 시간 (기능명세서 1.8)
    static final int RESEND_SECONDS = 60;        // 1분에 한 번
    static final int MAX_SENDS_PER_DAY = 5;      // 하루 5번
    static final int MAX_FAILURES = 5;           // 5번 틀리면 그 코드는 무효
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final PasswordResetDAO dao;
    private final EmployeeService employeeService;

    /**
     * 새 코드를 만들고 이전 코드를 무효로 한다.
     *
     * @return 보낼 코드(plainCode 포함). 계정이 없거나 1분·하루 제한이면 null (화면에는 같은 안내가 나간다)
     */
    @Transactional
    public PasswordResetCodeDTO issue(String companyCode, String employeeNo) {
        PasswordResetTargetDTO target = findTarget(companyCode, employeeNo);
        if (target == null) {
            return null;
        }
        dao.lockAccount(target.getCompanyId(), target.getUserAccountId());

        LocalDateTime now = now();
        LocalDateTime last = dao.getLastIssuedAt(target.getCompanyId(), target.getUserAccountId());
        if (last != null && now.isBefore(last.plusSeconds(RESEND_SECONDS))) {
            return null;
        }
        if (dao.getSentCountToday(target.getCompanyId(), target.getEmail(), now.toLocalDate()) >= MAX_SENDS_PER_DAY) {
            return null;
        }

        dao.revokeLive(target.getCompanyId(), target.getUserAccountId(), now);

        String plain = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordResetCodeDTO code = new PasswordResetCodeDTO();
        code.setCompanyId(target.getCompanyId());
        code.setUserAccountId(target.getUserAccountId());
        code.setEmail(target.getEmail());
        code.setTokenHash(TrialVerifyRecordService.sha256(plain));
        code.setIssuedAt(now);
        code.setExpiresAt(now.plusMinutes(CODE_MINUTES));
        dao.insertCode(code);
        dao.recordSend(target.getCompanyId(), target.getEmail(), now.toLocalDate(), now);

        code.setPlainCode(plain);
        return code;
    }

    /**
     * 코드를 확인한다. 맞으면 같은 행의 해시를 resetToken 해시로 바꾼다.
     * 틀린 횟수·무효 처리는 예외를 던져도 남아야 해서 ResponseStatusException으로는 롤백하지 않는다.
     *
     * @return resetToken (새 비밀번호와 함께 보낼 값)
     * @throws ResponseStatusException 400 틀림(남은 횟수 안내), 410 코드 없음·10분 지남·5번 틀림
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public String verify(String companyCode, String employeeNo, String input) {
        if (input == null || !input.trim().matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "6자리 숫자 인증 코드를 넣어 주세요");
        }
        PasswordResetTargetDTO target = findTarget(companyCode, employeeNo);
        if (target == null) {
            // 계정이 없어도 '틀림'과 같은 답 (계정이 있는지 드러나지 않게)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않습니다");
        }
        dao.lockAccount(target.getCompanyId(), target.getUserAccountId());

        LocalDateTime now = now();
        PasswordResetCodeDTO live = dao.findLiveCode(target.getCompanyId(), target.getUserAccountId());
        if (live == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "쓸 수 있는 인증 코드가 없습니다. 다시 보내기를 눌러 주세요");
        }
        if (!now.isBefore(live.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "인증 코드의 사용 시간(" + CODE_MINUTES + "분)이 지났습니다. 다시 보내기를 눌러 주세요");
        }
        if (!TrialVerifyRecordService.sha256(input.trim()).equals(live.getTokenHash())) {
            dao.addFailure(live.getEmailVerificationId(), live.getCompanyId(), MAX_FAILURES, now);
            int left = MAX_FAILURES - (live.getFailedCount() + 1);
            if (left <= 0) {
                throw new ResponseStatusException(HttpStatus.GONE,
                        MAX_FAILURES + "번 틀려 이 코드는 쓸 수 없습니다. 다시 보내기를 눌러 주세요");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않습니다 (" + left + "번 남음)");
        }

        String token = newToken();
        int changed = dao.markVerified(live.getEmailVerificationId(), live.getCompanyId(),
                TrialVerifyRecordService.sha256(token), now.plusMinutes(CODE_MINUTES), now);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.GONE, "쓸 수 있는 인증 코드가 없습니다. 다시 보내기를 눌러 주세요");
        }
        return token;
    }

    /**
     * resetToken으로 새 비밀번호를 저장하고 로그인 잠금을 푼다.
     *
     * @return 로그인 칸에 채울 업체 코드·사번
     * @throws ResponseStatusException 410 표 없음·만료·이미 씀, 409 사용 중 계정 아님
     * @throws IllegalArgumentException 400 비밀번호 8자 미만·너무 김
     */
    @Transactional
    public PasswordResetTargetDTO reset(String resetToken, String newPassword) {
        PasswordResetCodeDTO found = resetToken == null || resetToken.isEmpty() ? null
                : dao.findByResetToken(TrialVerifyRecordService.sha256(resetToken));
        if (found == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "인증한 지 오래되었거나 이미 쓴 요청입니다. 처음부터 다시 해 주세요");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 8자 이상으로 정해 주세요");
        }
        String hash = employeeService.hashPassword(newPassword); // 72바이트 검사 포함
        dao.lockAccount(found.getCompanyId(), found.getUserAccountId());

        LocalDateTime now = now();
        if (dao.consume(found.getEmailVerificationId(), found.getCompanyId(), now) == 0) {
            throw new ResponseStatusException(HttpStatus.GONE, "인증한 지 오래되었거나 이미 쓴 요청입니다. 처음부터 다시 해 주세요");
        }
        if (dao.updatePassword(found.getCompanyId(), found.getUserAccountId(), hash, now) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "사용 중인 계정이 아닙니다. 업체 관리자에게 문의해 주세요");
        }
        return dao.findTargetByAccount(found.getCompanyId(), found.getUserAccountId());
    }

    /** 지금 시각 (테스트에서 바꿔 끼울 수 있게 메서드로) */
    LocalDateTime now() {
        return LocalDateTime.now(SEOUL);
    }

    /** 업체 코드는 소문자·앞뒤 빈칸 제거, 사번은 앞뒤 빈칸 제거 후 찾기. 빈 값이면 null */
    private PasswordResetTargetDTO findTarget(String companyCode, String employeeNo) {
        if (companyCode == null || employeeNo == null || companyCode.isBlank() || employeeNo.isBlank()) {
            return null;
        }
        return dao.findTarget(companyCode.trim().toLowerCase(), employeeNo.trim());
    }

    /** resetToken: 32바이트 무작위 값 (URL에 써도 되는 Base64) */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}