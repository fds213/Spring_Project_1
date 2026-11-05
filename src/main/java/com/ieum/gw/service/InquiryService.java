package com.ieum.gw.service;

import com.ieum.gw.code.MailType;
import com.ieum.gw.dao.InquiryDAO;
import com.ieum.gw.dto.InquiryResultDTO;
import com.ieum.gw.dto.InquirySubmitDTO;
import com.ieum.gw.dto.MailMessageDTO;
import com.ieum.gw.dto.PublicInquiryDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 도입 문의 접수(WEB-02). 로그인 없이 부르므로 업체와 묶지 않는다.
 * 트랜잭션이 없다: 저장은 한 문장이라 돌아오면 커밋이 끝났고, 접수 메일은 그 뒤에 보낸다.
 */
@Service
@RequiredArgsConstructor
public class InquiryService {

    private static final Logger logger = LoggerFactory.getLogger(InquiryService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String RECEIPT_SUBJECT = "[이음] 문의가 접수되었습니다"; // 입력값을 넣지 않는다

    /**
     * 같은 주소로 보내는 접수 메일의 하루 횟수. 문의는 제한 없이 저장하고 메일만 건너뛴다.
     * 공개 폼이라 남의 주소를 적어 우리 계정으로 글을 보내거나 Gmail 하루 발송 한도를 다 쓰는 것을 막는다.
     */
    static final int MAX_RECEIPTS_PER_DAY = 5;

    // 화면(site/inquiry.jsp)의 maxlength와 같고 모두 DB 칸보다 짧다. 내용은 CLOB이라 여기서 막아야 길이가 정해진다
    static final int MAX_COMPANY_NAME = 50;
    static final int MAX_NAME = 20;
    static final int MAX_EMAIL = 100;
    static final int MAX_TITLE = 50;
    static final int MAX_CONTENT = 1000;

    // 화면 JS와 같은 규칙. 이메일은 체험 신청(SiteService)과도 같다
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^01[016789]-\\d{3,4}-\\d{4}$");

    private final InquiryDAO inquiryDAO;
    private final MailService mailService;

    /**
     * 문의를 저장한다. 같은 이메일의 접수 횟수는 제한하지 않는다(WEB-02).
     * 봇 막기 칸에 값이 있으면 봇이 눈치채지 못하게 성공처럼 답하되 저장하지 않고, 입력도 확인하지 않는다.
     * 저장 뒤 접수 메일을 보낸다. 메일이 어떻게 되든 문의는 남는다.
     *
     * @return 접수번호, 정리한 이메일, 접수 메일을 넘겼는지. 저장하지 않았으면 접수번호·이메일은 null
     * @throws InquiryFormException 입력이 규칙에 맞지 않을 때
     */
    public InquiryResultDTO submit(InquirySubmitDTO form) {
        InquiryResultDTO result = new InquiryResultDTO();
        if (form != null && !trim(form.getWebsite()).isEmpty()) {
            return result;
        }

        PublicInquiryDTO inquiry = normalize(form);
        inquiryDAO.insert(inquiry);

        result.setNo(inquiry.getPublicInquiryId());
        result.setEmail(inquiry.getEmail());
        result.setMailSent(sendReceipt(inquiry));
        return result;
    }

    // 어떤 이유로 실패해도 예외를 내보내지 않는다: 문의는 이미 저장됐다
    private boolean sendReceipt(PublicInquiryDTO inquiry) {
        try {
            LocalDate today = LocalDate.now(SEOUL);
            int recorded = mailService.countForRecipient(MailType.MT_INQUIRY_RECEIPT, inquiry.getEmail(), today);
            if (recorded >= MAX_RECEIPTS_PER_DAY) {
                logger.info("접수 메일 하루 제한으로 건너뜀: no={}", inquiry.getPublicInquiryId());
                return false;
            }
            MailMessageDTO mail = new MailMessageDTO();
            mail.setMailType(MailType.MT_INQUIRY_RECEIPT);
            mail.setRecipientEmail(inquiry.getEmail());
            mail.setSubject(RECEIPT_SUBJECT);
            mail.setBody(receiptBody(inquiry));
            mail.setDedupeKey(receiptKey(inquiry.getEmail(), today, recorded + 1));
            mail.setPublicInquiryId(inquiry.getPublicInquiryId());
            return mailService.send(mail).isOk();
        } catch (RuntimeException e) {
            logger.error("접수 메일 실패: no={}", inquiry.getPublicInquiryId(), e);
            return false;
        }
    }

    /**
     * 주소·날짜·그날 몇 번째인지로 만든 키. 고유 제약이 하루 횟수를 지킨다: 같은 주소로 동시에 접수해 둘 다 같은 번째를 고르면
     * 한쪽만 기록되고 다른 쪽은 메일 없이 끝나, 동시 요청으로도 제한을 넘지 못한다. 주소는 길어서(키는 100자) 해시로 넣는다.
     */
    static String receiptKey(String email, LocalDate day, int nth) {
        return "inquiry-receipt:" + TrialVerifyRecordService.sha256(email) + ":" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + ":" + nth;
    }

    // 일반 텍스트 메일이다. 문의 내용은 요구사항(WEB-02)이라 넣고, 회사명·휴대전화처럼 필요 없는 입력값은 넣지 않는다
    static String receiptBody(PublicInquiryDTO inquiry) {
        return String.join("\n",
                "안녕하세요. 이음에 문의해 주셔서 감사합니다.",
                "",
                "아래 내용으로 문의를 접수했습니다. 담당자가 확인한 뒤 이 주소로 답변 드립니다.",
                "",
                "접수번호: " + inquiry.getPublicInquiryId(),
                "제목: " + inquiry.getTitle(),
                "내용:",
                inquiry.getContent(),
                "",
                "직접 문의하지 않으셨다면 이 메일을 무시해 주세요.");
    }

    /** 입력을 확인하고 저장할 행을 만든다: 앞뒤 공백을 빼고, 이메일은 소문자로, 비운 휴대전화는 null로 */
    static PublicInquiryDTO normalize(InquirySubmitDTO form) {
        if (form == null) {
            throw new InquiryFormException("content", "문의 내용이 비어 있습니다.");
        }
        PublicInquiryDTO inquiry = new PublicInquiryDTO();
        inquiry.setCompanyName(required("companyName", "회사명", form.getCompanyName(), MAX_COMPANY_NAME));
        inquiry.setRequesterName(required("name", "이름", form.getName(), MAX_NAME));

        String email = trim(form.getEmail()).toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            throw new InquiryFormException("email", "이메일을 입력해 주세요.");
        }
        if (email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            throw new InquiryFormException("email", "이메일 형식이 맞지 않습니다.");
        }
        inquiry.setEmail(email);

        String phone = trim(form.getPhone());
        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
            throw new InquiryFormException("phone", "휴대전화는 010-0000-0000 형식으로 입력해 주세요.");
        }
        inquiry.setPhone(phone.isEmpty() ? null : phone);

        inquiry.setTitle(required("title", "문의 제목", form.getTitle(), MAX_TITLE));
        inquiry.setContent(required("content", "문의 내용", form.getContent(), MAX_CONTENT));
        return inquiry;
    }

    // label은 받침으로 끝나는 말만 넘긴다(회사명·이름·문의 제목·문의 내용)
    private static String required(String field, String label, String value, int max) {
        String text = trim(value);
        if (text.isEmpty()) {
            throw new InquiryFormException(field, label + "을 입력해 주세요.");
        }
        if (text.length() > max) {
            throw new InquiryFormException(field, label + "은 " + max + "자 이하로 입력해 주세요.");
        }
        return text;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
