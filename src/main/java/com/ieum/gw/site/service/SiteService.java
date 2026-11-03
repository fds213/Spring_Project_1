package com.ieum.gw.site.service;

import com.ieum.gw.employee.dto.AdminAccountDTO;
import com.ieum.gw.employee.service.EmployeeService;
import com.ieum.gw.site.dto.TrialApplyDTO;
import com.ieum.gw.site.dto.TrialResultDTO;
import com.ieum.gw.subscription.dto.TrialCompanyDTO;
import com.ieum.gw.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 상품 홈페이지의 처리. 체험 신청은 흐름만 맡고, 업체는 subscription, 관리자 계정·사번은 employee의 Service를 부른다
 * (AGENTS.md '패키지·이름 규칙').
 */
@Service
@RequiredArgsConstructor
public class SiteService {

    // DB 칸 길이(COMPANY_NAME 200, EMPLOYEE_NAME 100, TRIAL_EMAIL 254)와 화면 안내(비밀번호 8자 이상)에 맞춘다
    static final int MAX_COMPANY_NAME = 200;
    static final int MAX_ADMIN_NAME = 100;
    static final int MAX_EMAIL = 254;
    static final int MIN_PASSWORD = 8;
    // BCrypt는 72바이트 뒤를 버려서 그 뒤를 바꿔도 로그인된다. 한글은 한 글자가 3바이트라 24자까지다
    static final int MAX_PASSWORD_BYTES = 72;
    static final BigDecimal MAX_TRIAL_SEATS = BigDecimal.TEN;

    // 영문 이메일만 받는다. DB 검사 제약(TRIAL_EMAIL = LOWER(TRIM(TRIAL_EMAIL)))과 Java 소문자 변환이 어긋나지 않게
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private final SubscriptionService subscriptionService;
    private final EmployeeService employeeService;

    /**
     * 무료 체험 신청(ACC-01·02·03): 업체·업체 코드·14일 체험 기간과 업체 관리자 계정을 한 트랜잭션으로 만든다.
     * 하나라도 실패하면 모두 되돌린다. 인증 메일(ACC-06)은 핵심 뒤 추가라 아직 보내지 않는다(붙이면 커밋 뒤에 보낸다).
     *
     * @throws TrialFormException 입력이 규칙에 맞지 않을 때
     * @throws com.ieum.gw.subscription.service.TrialEmailUsedException       이 이메일로 만든 업체가 이미 있을 때
     * @throws com.ieum.gw.subscription.service.CompanyCodeExhaustedException 업체 코드를 더 만들 수 없을 때
     */
    @Transactional
    public TrialResultDTO applyTrial(TrialApplyDTO form) {
        TrialApplyDTO in = normalize(form);

        int seats = in.getSeats().intValueExact(); // normalize가 1~10 정수만 남긴다

        TrialCompanyDTO company = subscriptionService.createTrialCompany(in.getCompanyName(), in.getEmail(), seats);
        AdminAccountDTO admin = employeeService.createCompanyAdmin(
                company.getCompanyId(), in.getAdminName(), in.getEmail(), in.getPassword());

        TrialResultDTO result = new TrialResultDTO();
        result.setCompanyName(in.getCompanyName());
        result.setCompanyCode(company.getCompanyCode());
        result.setEmployeeNo(admin.getEmployeeNo());
        result.setEmail(in.getEmail());
        result.setSeats(seats);
        result.setTrialStartDate(company.getStartsAt().toLocalDate().toString());
        result.setTrialLastDate(company.getEndsAt().toLocalDate().minusDays(1).toString()); // 만료가 0시라 그 전날
        return result;
    }

    /** 입력을 확인하고 정리한 새 객체를 돌려준다: 이름은 앞뒤 공백을 빼고, 이메일은 소문자로. 비밀번호는 그대로 둔다 */
    static TrialApplyDTO normalize(TrialApplyDTO form) {
        if (form == null) {
            throw new TrialFormException("companyName", "신청 내용이 비어 있습니다.");
        }
        String companyName = trim(form.getCompanyName());
        if (companyName.isEmpty()) {
            throw new TrialFormException("companyName", "업체명을 입력해 주세요.");
        }
        if (companyName.length() > MAX_COMPANY_NAME) {
            throw new TrialFormException("companyName", "업체명은 " + MAX_COMPANY_NAME + "자 이하로 입력해 주세요.");
        }

        BigDecimal seats = form.getSeats();
        if (seats == null || seats.stripTrailingZeros().scale() > 0
                || seats.compareTo(BigDecimal.ONE) < 0 || seats.compareTo(MAX_TRIAL_SEATS) > 0) {
            throw new TrialFormException("seats", "이용 인원은 관리자를 포함해 1~" + MAX_TRIAL_SEATS + "명입니다.");
        }

        String email = trim(form.getEmail()).toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            throw new TrialFormException("email", "이메일을 입력해 주세요.");
        }
        if (email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            throw new TrialFormException("email", "이메일 형식이 맞지 않습니다.");
        }

        String adminName = trim(form.getAdminName());
        if (adminName.isEmpty()) {
            throw new TrialFormException("adminName", "관리자 이름을 입력해 주세요.");
        }
        if (adminName.length() > MAX_ADMIN_NAME) {
            throw new TrialFormException("adminName", "관리자 이름은 " + MAX_ADMIN_NAME + "자 이하로 입력해 주세요.");
        }

        String password = form.getPassword();
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new TrialFormException("password", "비밀번호는 " + MIN_PASSWORD + "자 이상이어야 합니다.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new TrialFormException("password", "비밀번호가 너무 깁니다. 영문·숫자는 72자, 한글은 24자까지 쓸 수 있습니다.");
        }

        TrialApplyDTO in = new TrialApplyDTO();
        in.setCompanyName(companyName);
        in.setSeats(BigDecimal.valueOf(seats.intValueExact())); // 10.0 → 10
        in.setEmail(email);
        in.setAdminName(adminName);
        in.setPassword(password);
        return in;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
