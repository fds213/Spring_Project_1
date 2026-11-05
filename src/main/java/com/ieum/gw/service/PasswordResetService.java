package com.ieum.gw.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.code.MailType;
import com.ieum.gw.dto.MailMessageDTO;
import com.ieum.gw.dto.PasswordResetCodeDTO;
import com.ieum.gw.dto.PasswordResetTargetDTO;

import lombok.RequiredArgsConstructor;

/**
 * 비밀번호 찾기(ACC-07)의 순서. 트랜잭션이 없다: 저장(PasswordResetRecordService)이 커밋된 뒤에 메일을 보낸다.
 * 코드 보내기는 계정이 있든 없든, 제한에 걸리든 메일이 실패하든 같은 답을 준다(계정이 있는지 드러나지 않게).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String SUBJECT = "[이음] 비밀번호 찾기 인증 코드";

    private final PasswordResetRecordService recordService;
    private final MailService mailService;
    private final MailSettings settings;

    /**
     * 인증 코드를 보낸다. 보낼 계정이 없거나 제한에 걸려도 예외 없이 끝난다.
     *
     * @throws ResponseStatusException 409 메일을 끈 환경 (계정과 관계없는 전체 설정)
     */
    public void sendCode(String companyCode, String employeeNo) {
        if (!settings.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "지금은 인증 메일을 보내지 않아 비밀번호를 찾을 수 없습니다. 업체 관리자에게 문의해 주세요");
        }
        PasswordResetCodeDTO code = recordService.issue(companyCode, employeeNo);
        if (code == null) {
            return;
        }
        try {
            if (!mailService.send(mail(code)).isOk()) {
                logger.warn("비밀번호 찾기 메일 실패: emailVerificationId={}", code.getEmailVerificationId());
            }
        } catch (RuntimeException e) {
            logger.error("비밀번호 찾기 메일 실패: emailVerificationId={}", code.getEmailVerificationId(), e);
        }
    }

    /** 코드 확인 → resetToken (예외는 PasswordResetRecordService.verify 참고) */
    public String verify(String companyCode, String employeeNo, String code) {
        return recordService.verify(companyCode, employeeNo, code);
    }

    /** 새 비밀번호 저장 → 로그인 칸에 채울 업체 코드·사번 (예외는 PasswordResetRecordService.reset 참고) */
    public PasswordResetTargetDTO reset(String resetToken, String newPassword) {
        return recordService.reset(resetToken, newPassword);
    }

    /** 메일 한 통. 본문에는 코드와 유효 시간만 넣는다 */
    private MailMessageDTO mail(PasswordResetCodeDTO code) {
        MailMessageDTO mail = new MailMessageDTO();
        mail.setMailType(MailType.MT_PASSWORD_RESET);
        mail.setRecipientEmail(code.getEmail());
        mail.setSubject(SUBJECT);
        mail.setBody(String.join("\n",
                "이음 비밀번호 찾기 인증 코드입니다.",
                "",
                "인증 코드: " + code.getPlainCode(),
                "",
                PasswordResetRecordService.CODE_MINUTES + "분 안에 로그인 화면의 비밀번호 찾기에 넣어 주세요.",
                "인증 코드를 다시 받으면 이전 코드는 쓸 수 없습니다.",
                "",
                "직접 요청하지 않으셨다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다."));
        mail.setDedupeKey("password-reset:" + code.getEmailVerificationId());
        mail.setCompanyId(code.getCompanyId());
        mail.setUserAccountId(code.getUserAccountId());
        mail.setEmailVerificationId(code.getEmailVerificationId());
        return mail;
    }
}