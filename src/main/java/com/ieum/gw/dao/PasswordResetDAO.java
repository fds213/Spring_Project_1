package com.ieum.gw.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.PasswordResetCodeDTO;
import com.ieum.gw.dto.PasswordResetTargetDTO;
import com.ieum.gw.mapper.PasswordResetMapper;

import lombok.RequiredArgsConstructor;

/** 비밀번호 찾기(ACC-07). 인증 목적 AUTH_PASSWORD_RESET은 XML 한 곳에 있다 */
@Repository
@RequiredArgsConstructor
public class PasswordResetDAO {

    private final PasswordResetMapper mapper;

    /** 업체 코드 + 사번의 사용 중 계정 (운영자 제외). 없으면 null */
    public PasswordResetTargetDTO findTarget(String companyCode, String employeeNo) {
        return mapper.findTarget(companyCode, employeeNo);
    }

    /** 계정 ID로 업체 코드·사번 (변경 뒤 로그인 칸 채우기용) */
    public PasswordResetTargetDTO findTargetByAccount(Long companyId, Long userAccountId) {
        return mapper.findTargetByAccount(companyId, userAccountId);
    }

    /** 이 계정 행 잠금 (발급·확인·변경을 한 줄로 세움) */
    public Long lockAccount(Long companyId, Long userAccountId) {
        return mapper.lockAccount(companyId, userAccountId);
    }

    /** 이 계정에 마지막으로 코드를 보낸 시각. 처음이면 null */
    public LocalDateTime getLastIssuedAt(Long companyId, Long userAccountId) {
        return mapper.getLastIssuedAt(companyId, userAccountId);
    }

    /** 오늘 이 이메일로 보낸 수 (없으면 0) */
    public int getSentCountToday(Long companyId, String email, LocalDate today) {
        Integer count = mapper.getSentCountToday(companyId, email, today);
        return count == null ? 0 : count;
    }

    /** 이 계정의 살아 있는 코드·표를 모두 무효로 */
    public int revokeLive(Long companyId, Long userAccountId, LocalDateTime now) {
        return mapper.revokeLive(companyId, userAccountId, now);
    }

    /** 코드 1건 저장 (해시만). 성공하면 code.emailVerificationId에 새 번호 */
    public int insertCode(PasswordResetCodeDTO code) {
        return mapper.insertCode(code);
    }

    /** 오늘 보낸 수 +1 */
    public int recordSend(Long companyId, String email, LocalDate today, LocalDateTime now) {
        return mapper.recordSend(companyId, email, today, now);
    }

    /** 확인을 기다리는 최신 코드. 없으면 null */
    public PasswordResetCodeDTO findLiveCode(Long companyId, Long userAccountId) {
        return mapper.findLiveCode(companyId, userAccountId);
    }

    /** 틀린 횟수 +1, maxFailures번째면 무효 */
    public int addFailure(Long id, Long companyId, int maxFailures, LocalDateTime now) {
        return mapper.addFailure(id, companyId, maxFailures, now);
    }

    /** 코드 맞힘: resetToken 해시로 바꾸고 새 만료 시각. 그사이 무효·만료면 0 */
    public int markVerified(Long id, Long companyId, String tokenHash, LocalDateTime expiresAt, LocalDateTime now) {
        return mapper.markVerified(id, companyId, tokenHash, expiresAt, now);
    }

    /** resetToken 해시로 확인을 마친 인증 찾기. 없으면 null */
    public PasswordResetCodeDTO findByResetToken(String tokenHash) {
        return mapper.findByResetToken(tokenHash);
    }

    /** resetToken 사용 처리. 그사이 쓰였거나 무효·만료면 0 */
    public int consume(Long id, Long companyId, LocalDateTime now) {
        return mapper.consume(id, companyId, now);
    }

    /** 새 비밀번호 해시 저장 + 로그인 실패 잠금 해제. 사용 중 계정이 아니면 0 */
    public int updatePassword(Long companyId, Long userAccountId, String passwordHash, LocalDateTime now) {
        return mapper.updatePassword(companyId, userAccountId, passwordHash, now);
    }
}