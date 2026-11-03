package com.ieum.gw.site.service;

import com.ieum.gw.site.dao.InquiryDAO;
import com.ieum.gw.site.dto.InquiryResultDTO;
import com.ieum.gw.site.dto.InquirySubmitDTO;
import com.ieum.gw.site.dto.PublicInquiryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

/** 도입 문의 접수(WEB-02). 로그인 없이 부르므로 업체와 묶지 않는다 */
@Service
@RequiredArgsConstructor
public class InquiryService {

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

    /**
     * 문의를 저장한다. 같은 이메일의 접수 횟수는 제한하지 않는다(WEB-02).
     * 봇 막기 칸에 값이 있으면 봇이 눈치채지 못하게 성공처럼 답하되 저장하지 않고, 입력도 확인하지 않는다.
     *
     * @return 접수번호와 정리한 이메일. 저장하지 않았으면 둘 다 null
     * @throws InquiryFormException 입력이 규칙에 맞지 않을 때
     */
    public InquiryResultDTO submit(InquirySubmitDTO form) {
        InquiryResultDTO result = new InquiryResultDTO();
        if (form != null && !trim(form.getWebsite()).isEmpty()) {
            return result;
        }

        PublicInquiryDTO inquiry = normalize(form);
        inquiryDAO.insert(inquiry);

        // TODO(WEB-02): 접수 메일(본문에 문의 내용). 메일 기능이 생기면 저장 뒤 트랜잭션 밖에서 보낸다. 발송이 실패해도 문의는 남는다
        result.setNo(inquiry.getPublicInquiryId());
        result.setEmail(inquiry.getEmail());
        return result;
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
