package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.code.MailType;
import com.ieum.gw.dto.MailMessageDTO;
import com.ieum.gw.dto.MailResultDTO;
import com.ieum.gw.dto.TrialVerifyIssuedDTO;
import com.ieum.gw.dto.TrialVerifyTargetDTO;

class TrialVerifyServiceTest {

    private static final Long COMPANY = 42L;

    static class FakeRecord extends TrialVerifyRecordService {
        RuntimeException fail;
        boolean verifiedAlready;
        int issues;

        FakeRecord() {
            super(null, null, null);
        }

        @Override
        public TrialVerifyIssuedDTO issue(Long companyId, boolean resend) {
            issues++;
            if (fail != null) {
                throw fail;
            }
            if (verifiedAlready) {
                return null;
            }
            TrialVerifyTargetDTO target = new TrialVerifyTargetDTO();
            target.setCompanyId(companyId);
            target.setCompanyCode("ieum4827");
            target.setEmail("kim@gana.com");
            target.setUserAccountId(8L);
            target.setEmployeeNo("20260001");
            return new TrialVerifyIssuedDTO(target, 1000L, "무작위값");
        }
    }

    static class FakeMail extends MailService {
        MailMessageDTO last;
        MailResultDTO result = MailResultDTO.sent();

        FakeMail() {
            super(null, null, new MailSettings(), null);
        }

        @Override
        public MailResultDTO send(MailMessageDTO mail) {
            last = mail;
            return result;
        }
    }

    private static MailSettings settings(boolean enabled) {
        return new MailSettings() {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public String getBaseUrl() {
                return "https://ieum.test";
            }
        };
    }

    private final FakeRecord record = new FakeRecord();
    private final FakeMail mail = new FakeMail();
    private final TrialVerifyService service = new TrialVerifyService(record, mail, settings(true));

    @Test
    @DisplayName("첫 메일: 링크 값을 # 뒤에 넣고 업체 코드와 관리자 사번을 적어 신청 이메일로 보낸다")
    void firstMail() {
        assertTrue(service.sendFirst(COMPANY));

        assertEquals(MailType.MT_TRIAL_VERIFY, mail.last.getMailType());
        assertEquals("kim@gana.com", mail.last.getRecipientEmail());
        assertEquals("trial-verify:1000", mail.last.getDedupeKey());
        assertEquals(COMPANY, mail.last.getCompanyId());
        assertEquals(1000L, mail.last.getEmailVerificationId());
        assertTrue(mail.last.getBody().contains("https://ieum.test/trial/verify#무작위값"));
        assertTrue(mail.last.getBody().contains("ieum4827"));
        assertTrue(mail.last.getBody().contains("20260001"));
    }

    @Test
    @DisplayName("첫 메일이 어떻게 실패해도 신청은 끝났으므로 예외를 내보내지 않고 false만 돌려준다")
    void firstMailNeverThrows() {
        mail.result = MailResultDTO.failed("메일 서버 연결 실패");
        assertFalse(service.sendFirst(COMPANY));

        record.fail = new IllegalStateException("DB 끊김");
        assertFalse(service.sendFirst(COMPANY));
    }

    @Test
    @DisplayName("메일을 끈 환경: 첫 메일은 링크도 만들지 않고, 다시 보내기는 409로 알린다")
    void mailOff() {
        TrialVerifyService off = new TrialVerifyService(record, mail, settings(false));

        assertFalse(off.sendFirst(COMPANY));
        assertEquals(0, record.issues);
        assertNull(mail.last);
        assertEquals(409, assertThrows(TrialVerifyException.class, () -> off.resend(COMPANY)).getStatus());
    }

    @Test
    @DisplayName("다시 보내기: 보내지 못하면 502, 이미 인증한 업체면 메일 없이 false")
    void resend() {
        assertTrue(service.resend(COMPANY));

        mail.result = MailResultDTO.failed("메일 서버 연결 실패");
        assertEquals(502, assertThrows(TrialVerifyException.class, () -> service.resend(COMPANY)).getStatus());

        mail.last = null;
        record.verifiedAlready = true;
        assertFalse(service.resend(COMPANY));
        assertNull(mail.last);
    }
}
