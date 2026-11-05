package com.ieum.gw.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ieum.gw.code.MailType;
import com.ieum.gw.dto.MailMessageDTO;
import com.ieum.gw.dto.MailResultDTO;
import com.ieum.gw.dto.TrialVerifyIssuedDTO;
import com.ieum.gw.dto.TrialVerifyTargetDTO;

import lombok.RequiredArgsConstructor;

/**
 * 체험 신청 이메일 인증(ACC-06)의 순서. 트랜잭션이 없다: 저장(TrialVerifyRecordService)이 커밋된 뒤에 메일을 보낸다.
 * 메일 본문에는 업체명 같은 신청 입력값을 넣지 않는다. 남의 주소로 신청해 아무 글이나 보내는 데 쓰이지 않게.
 */
@Service
@RequiredArgsConstructor
public class TrialVerifyService {

    /** 다시 보내기를 할 수 있는 업체(체험을 신청했거나 비밀번호를 맞힌 세션). 요청 값으로 업체를 받지 않는다 */
    public static final String SESSION_COMPANY_ID = "trialVerifyCompanyId";

    private static final Logger logger = LoggerFactory.getLogger(TrialVerifyService.class);
    private static final String SUBJECT = "[이음] 이메일 인증을 완료해 주세요";

    private final TrialVerifyRecordService recordService;
    private final MailService mailService;
    private final MailSettings settings;

    public boolean isRequired() {
        return settings.isVerificationRequired();
    }

    /**
     * 신청 직후의 첫 메일. 어떤 이유로 실패해도 예외를 내보내지 않는다: 신청은 이미 끝났고 다시 보내기가 남아 있다.
     *
     * @return 메일 서버에 넘겼으면 true. 메일을 끈 환경이면 링크를 만들지 않고 false
     */
    public boolean sendFirst(Long companyId) {
        if (!settings.isEnabled()) {
            return false;
        }
        try {
            TrialVerifyIssuedDTO issued = recordService.issue(companyId, false);
            return issued != null && send(issued).isOk();
        } catch (RuntimeException e) {
            logger.error("첫 인증 메일 실패: companyId={}", companyId, e);
            return false;
        }
    }

    /**
     * 다시 보내기. 이전 링크는 무효가 된다.
     *
     * @return 메일 서버에 넘겼으면 true, 이미 인증을 마친 업체면 false
     * @throws TrialVerifyException 409 메일을 끈 환경, 429 제한, 502 메일을 보내지 못했을 때
     */
    public boolean resend(Long companyId) {
        if (!settings.isEnabled()) {
            throw new TrialVerifyException(409, "지금은 인증 메일을 보내지 않습니다. 인증 없이 로그인할 수 있습니다.");
        }
        TrialVerifyIssuedDTO issued = recordService.issue(companyId, true);
        if (issued == null) {
            return false;
        }
        if (!send(issued).isOk()) {
            throw new TrialVerifyException(502, "인증 메일을 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
        }
        return true;
    }

    /** @return 이번에 인증했으면 true, 이미 인증을 마친 업체면 false */
    public boolean verify(String token) {
        return recordService.verify(token);
    }

    private MailResultDTO send(TrialVerifyIssuedDTO issued) {
        TrialVerifyTargetDTO target = issued.getTarget();
        MailMessageDTO mail = new MailMessageDTO();
        mail.setMailType(MailType.MT_TRIAL_VERIFY);
        mail.setRecipientEmail(target.getEmail());
        mail.setSubject(SUBJECT);
        mail.setBody(body(target, issued.getToken()));
        mail.setDedupeKey("trial-verify:" + issued.getEmailVerificationId());
        mail.setCompanyId(target.getCompanyId());
        mail.setUserAccountId(target.getUserAccountId());
        mail.setEmailVerificationId(issued.getEmailVerificationId());
        return mailService.send(mail);
    }

    // 링크의 값을 # 뒤에 둔다. # 뒤는 서버로 가지 않아 접속 로그와 메일 보안 검사의 요청에 남지 않는다.
    // 화면(site/verify.js)이 읽어 [인증하기]를 누를 때 POST 본문으로 보낸다
    String body(TrialVerifyTargetDTO target, String token) {
        return String.join("\n",
                "안녕하세요. 이음 무료 체험을 신청해 주셔서 감사합니다.",
                "",
                "아래 링크를 열어 [인증하기]를 누르면 로그인할 수 있습니다.",
                settings.getBaseUrl() + "/trial/verify#" + token,
                "",
                "링크는 " + TrialVerifyRecordService.LINK_HOURS + "시간 동안 쓸 수 있고, 인증 메일을 다시 받으면 이전 링크는 쓸 수 없습니다.",
                "",
                "로그인할 때 쓰는 정보입니다.",
                "- 업체 코드: " + target.getCompanyCode(),
                "- 관리자 사번: " + target.getEmployeeNo(),
                "",
                "직접 신청하지 않으셨다면 이 메일을 무시해 주세요.");
    }
}
