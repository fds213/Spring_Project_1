package com.ieum.gw.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.dao.SubscriptionDAO;
import com.ieum.gw.dao.TrialVerifyDAO;
import com.ieum.gw.dto.TrialVerificationDTO;
import com.ieum.gw.dto.TrialVerifyIssuedDTO;
import com.ieum.gw.dto.TrialVerifyTargetDTO;

import lombok.RequiredArgsConstructor;

/**
 * 이메일 인증(ACC-06)의 DB 트랜잭션들. 메일 발송은 트랜잭션 밖이어야 해서 순서는 TrialVerifyService가 정하고 저장만 여기로 뗐다.
 * 발급과 인증 모두 업체 행을 잠근다: 동시에 다시 보내도 살아 있는 링크는 하나이고 제한도 뚫리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TrialVerifyRecordService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    static final int LINK_HOURS = 24;
    static final int RESEND_SECONDS = 60;
    static final int MAX_RESENDS_PER_DAY = 5;

    private final TrialVerifyDAO dao;
    private final SubscriptionDAO subscriptionDAO;
    private final EmployeeService employeeService;

    /**
     * 새 인증 링크를 만들고 이전 링크를 무효로 한다.
     *
     * @param resend 다시 보내기면 true: 1분에 한 번·하루 5번을 지킨다. 신청 직후의 첫 메일은 false
     * @return 이미 인증을 마친 업체면 null
     * @throws TrialVerifyException 429 제한에 걸렸을 때, 400 업체를 찾을 수 없을 때
     */
    @Transactional
    public TrialVerifyIssuedDTO issue(Long companyId, boolean resend) {
        subscriptionDAO.lockCompany(companyId);
        TrialVerifyTargetDTO target = dao.getTarget(companyId);
        if (target == null) {
            throw new TrialVerifyException(400, "인증 메일을 보낼 업체를 찾을 수 없습니다.");
        }
        if (target.getEmailVerifiedAt() != null) {
            return null;
        }

        LocalDateTime now = now();
        if (resend) {
            LocalDateTime last = dao.getLastIssuedAt(companyId);
            if (last != null && now.isBefore(last.plusSeconds(RESEND_SECONDS))) {
                throw new TrialVerifyException(429, "인증 메일은 1분에 한 번 보낼 수 있습니다. 잠시 뒤 다시 눌러 주세요.");
            }
            if (dao.getResendCount(companyId, target.getEmail(), now.toLocalDate()) >= MAX_RESENDS_PER_DAY) {
                throw new TrialVerifyException(429, "인증 메일은 하루 " + MAX_RESENDS_PER_DAY + "번까지 다시 보낼 수 있습니다. 내일 다시 시도해 주세요.");
            }
        }

        dao.revokeLive(companyId, now);

        String token = newToken();
        TrialVerificationDTO verification = new TrialVerificationDTO();
        verification.setCompanyId(companyId);
        verification.setUserAccountId(target.getUserAccountId());
        verification.setEmail(target.getEmail());
        verification.setTokenHash(sha256(token));
        verification.setIssuedAt(now);
        verification.setExpiresAt(now.plusHours(LINK_HOURS));
        dao.insertVerification(verification);

        dao.recordIssue(companyId, target.getEmail(), now.toLocalDate(), resend ? 1 : 0, now);
        return new TrialVerifyIssuedDTO(target, verification.getEmailVerificationId(), token);
    }

    /**
     * 링크의 값으로 이메일을 인증한다. 체험 만료일은 건드리지 않는다(ACC-02).
     *
     * @return 이번에 인증했으면 true, 이미 인증을 마친 업체면 false
     * @throws TrialVerifyException 400 없는 링크·다시 보내 무효가 된 링크, 410 24시간이 지난 링크
     */
    @Transactional
    public boolean verify(String token) {
        TrialVerificationDTO found = token == null || token.isEmpty() ? null : dao.findByTokenHash(sha256(token));
        if (found == null) {
            throw new TrialVerifyException(400, "인증 링크가 올바르지 않습니다. 메일의 링크를 다시 열어 주세요.");
        }
        Long companyId = found.getCompanyId();
        subscriptionDAO.lockCompany(companyId);

        LocalDateTime now = now();
        // 메일 보안 검사가 먼저 눌렀거나 두 번 누른 경우. 무효가 된 옛 링크여도 이미 인증했다고 답하는 편이 낫다
        TrialVerifyTargetDTO target = dao.getTarget(companyId);
        if (target != null && target.getEmailVerifiedAt() != null) {
            return false;
        }
        if (found.getRevokedAt() != null) {
            throw new TrialVerifyException(400, "더 새로 보낸 인증 메일이 있어 이 링크는 쓸 수 없습니다. 가장 최근 메일의 링크를 열어 주세요.");
        }
        if (!now.isBefore(found.getExpiresAt())) {
            throw new TrialVerifyException(410, "인증 링크의 사용 기간(24시간)이 지났습니다. 로그인 화면에서 로그인하면 새 인증 메일을 받을 수 있습니다.");
        }
        // 잠금 앞에서 읽은 뒤 그사이 무효가 됐을 수 있어 조건부로 바꾸고 행 수를 본다
        if (dao.consume(found.getEmailVerificationId(), companyId, now) == 0) {
            throw new TrialVerifyException(400, "더 새로 보낸 인증 메일이 있어 이 링크는 쓸 수 없습니다. 가장 최근 메일의 링크를 열어 주세요.");
        }
        subscriptionDAO.markEmailVerified(companyId, now);
        employeeService.markAccountEmailVerified(companyId, found.getUserAccountId(), now);
        return true;
    }

    LocalDateTime now() {
        return LocalDateTime.now(SEOUL);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 링크 값 → SHA-256 16진수 64자 (TOKEN_HASH에 저장·검색) */
    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }
}
