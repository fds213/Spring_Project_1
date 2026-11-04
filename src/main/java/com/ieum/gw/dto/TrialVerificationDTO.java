package com.ieum.gw.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 인증 링크 1건(EMAIL_VERIFICATION). 값은 해시로만 저장한다(AGENTS.md '권한·보안')
@Getter
@Setter
@ToString
public class TrialVerificationDTO {
    private Long emailVerificationId;
    private Long companyId;
    private Long userAccountId;
    private String email;
    private String tokenHash;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;    // 인증에 썼다
    private LocalDateTime revokedAt;     // 다시 보내서 무효가 됐다
}
