package com.ieum.gw.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.TrialVerificationDTO;
import com.ieum.gw.dto.TrialVerifyTargetDTO;
import com.ieum.gw.mapper.TrialVerifyMapper;

import lombok.RequiredArgsConstructor;

/** 체험 신청 관리자의 이메일 인증(ACC-06). 인증 목적은 공통 코드 AUTH_ADMIN_EMAIL 하나다 */
@Repository
@RequiredArgsConstructor
public class TrialVerifyDAO {

    private static final String PURPOSE = "AUTH_ADMIN_EMAIL";

    private final TrialVerifyMapper mapper;

    /** 인증 메일을 받을 업체와 관리자. 없는 업체면 null */
    public TrialVerifyTargetDTO getTarget(Long companyId) {
        return mapper.getTarget(companyId);
    }

    /** 이 업체에 마지막으로 인증 메일을 만든 시각. 처음이면 null */
    public LocalDateTime getLastIssuedAt(Long companyId) {
        return mapper.getLastIssuedAt(companyId, PURPOSE);
    }

    /** 오늘 다시 보낸 횟수. 첫 메일은 세지 않는다. 오늘 보낸 적이 없으면 0 */
    public int getResendCount(Long companyId, String email, LocalDate today) {
        Integer count = mapper.getResendCount(companyId, email, PURPOSE, today);
        return count == null ? 0 : count;
    }

    /** 아직 쓰지 않은 이전 링크를 모두 무효로 한다 */
    public int revokeLive(Long companyId, LocalDateTime now) {
        return mapper.revokeLive(companyId, PURPOSE, now);
    }

    /** 새 링크 1행. 번호를 dto.emailVerificationId에 채운다 */
    public int insertVerification(TrialVerificationDTO verification) {
        return mapper.insertVerification(verification, PURPOSE);
    }

    /** 오늘 발송 칸을 올린다(없으면 만든다). resends: 다시 보내기면 1, 첫 메일이면 0 */
    public int recordIssue(Long companyId, String email, LocalDate today, int resends, LocalDateTime now) {
        return mapper.recordIssue(companyId, email, PURPOSE, today, resends, now);
    }

    /** 링크 값의 해시로 찾는다. 없으면 null */
    public TrialVerificationDTO findByTokenHash(String tokenHash) {
        return mapper.findByTokenHash(tokenHash, PURPOSE);
    }

    /** 링크를 인증에 쓴 것으로 표시한다. 쓸 수 없는 링크(사용·무효·만료)면 0 */
    public int consume(Long emailVerificationId, Long companyId, LocalDateTime now) {
        return mapper.consume(emailVerificationId, companyId, now);
    }
}
