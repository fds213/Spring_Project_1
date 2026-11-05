package com.ieum.gw.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 비밀번호 찾기 인증 1건 (EMAIL_VERIFICATION, 목적 AUTH_PASSWORD_RESET). 코드·표는 해시로만 저장한다 */
@Getter
@Setter
@ToString(exclude = "plainCode")
public class PasswordResetCodeDTO {

	private Long emailVerificationId;
	private Long companyId;
	private Long userAccountId;
	private String email;
	private String tokenHash;            // 확인 전: 6자리 코드의 해시, 확인 뒤: resetToken의 해시
	private LocalDateTime issuedAt;
	private LocalDateTime expiresAt;
	private LocalDateTime verifiedAt;    // 코드를 맞힌 시각 (이때부터 resetToken 단계)
	private int failedCount;

	private String plainCode;            // 메일에 넣을 평문 코드. DB에 저장하지 않는다
}