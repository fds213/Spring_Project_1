package com.ieum.gw.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dao.JoinDAO;
import com.ieum.gw.dto.AccountDTO;
import com.ieum.gw.dto.EmployeeDTO;
import com.ieum.gw.dto.JoinCompanyDTO;
import com.ieum.gw.dto.JoinOpenFormDTO;
import com.ieum.gw.dto.JoinOpeningDTO;
import com.ieum.gw.dto.JoinRequestDTO;
import com.ieum.gw.dto.JoinRequestFormDTO;
import com.ieum.gw.dto.JoinRequestViewDTO;
import com.ieum.gw.dto.CompanyUsageDTO;

import lombok.RequiredArgsConstructor;

/**
 * 직원 가입 (ACC-04·05, 단순화 ①②). 가입 열기·닫기, 가입 요청, 승인·반려. 업체 관리자는 업체 전체, 부서장은 자기 부서만
 * 다룬다. 업체·사람은 로그인 정보에서만 꺼낸다.
 */
@Service
@RequiredArgsConstructor
public class JoinService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<Integer> OPEN_MINUTES = Set.of(10, 30, 60, 180, 1440);
    // SiteService와 같은 이메일 규칙 (영문만, DB 소문자 제약과 맞춤)
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private final JoinDAO dao;
    private final SubscriptionService subscriptionService; // 인원 한도·이용 상태
    private final EmployeeService employeeService; // 비밀번호 해시, 사번 발급 (같은 모듈)
    private final EmployeeDAO employeeDAO; // 계정 저장 (insertAccount)

    /** 가입 승인 화면에 보낼 내용 (GET /employee/api/join) */
    public Map<String, Object> getBoard(LoginUser user) {
        Long companyId = user.getCompanyId();

        // ① 누구인가: 관리자 or 부서장. 둘 다 아니면 이 화면을 볼 수 없다
        boolean admin = isAdmin(user);
        String headDept = requireApprover(user, admin);

        // ② 열린 부서: 관리자는 전부, 부서장은 자기 부서만
        List<Map<String, Object>> openings = new ArrayList<>();
        for (JoinOpeningDTO o : dao.listOpenings(companyId)) {
            if (!admin && !o.getDepartmentName().equals(headDept)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scope", o.getDepartmentName());
            row.put("all", false);
            row.put("until", o.getJoinOpenUntil().toString());
            row.put("byName", o.getOpenedByName());
            row.put("mine", user.getUserAccountId().equals(o.getJoinOpenedBy()));
            openings.add(row);
        }

        // ③ 인원: 사용 중 / 한도. 이용 기간이 없는 업체(운영 업체)는 한도 없음
        int used = employeeService.countAccountsInUse(companyId);
        CompanyUsageDTO usage = subscriptionService.getCompanyUsage(companyId);
        Map<String, Object> caps = null;
        Integer room = null;
        boolean canApprove = true;
        boolean readOnly = false;
        if (usage != null) {
            int limit = subscriptionService.getSeatLimit(companyId);
            room = Math.max(0, limit - used);
            canApprove = room > 0;
            readOnly = usage.isExpired();
            caps = new LinkedHashMap<>();
            caps.put("active", limit);
            caps.put("paid", !usage.isTrial());
        }
        Map<String, Object> seats = new LinkedHashMap<>();
        seats.put("used", used);
        seats.put("room", room);

        // ④ 다음 사번 미리보기 (실제 발급은 승인 때 issueEmployeeNo)
        Integer last = dao.peekLastSerial(companyId);
        String nextEmpNo = last == null ? null : String.format("%d%04d", LocalDate.now(SEOUL).getYear(), last + 1);

        // ⑤ 조립
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("perms", Map.of("admin", admin));
        board.put("myDept", headDept);
        board.put("companyCode", dao.getCompanyCode(companyId));
        board.put("depts", admin ? dao.listDeptNames(companyId) : List.of(headDept));
        board.put("openings", openings);
        board.put("seats", seats);
        board.put("caps", caps);
        board.put("nextEmpNo", nextEmpNo);
        board.put("canApprove", canApprove);
        board.put("readOnly", readOnly);
        board.put("requests", waitingRequests(companyId, admin, headDept));
        return board;
    }

    // ---------- 가입 열기·끄기 ----------

    /** 가입 열기 (POST /employee/api/join/openings). 관리자는 고른 부서들, 부서장은 자기 부서 */
    @Transactional
    public Map<String, Object> openJoin(LoginUser user, JoinOpenFormDTO form) {
        Long companyId = user.getCompanyId();
        boolean admin = isAdmin(user);
        String headDept = requireApprover(user, admin);

        // 시간: 화면 선택지만 받는다
        Integer minutes = form.getMinutes();
        if (minutes == null || !OPEN_MINUTES.contains(minutes)) {
            throw new IllegalArgumentException("열어 둘 시간을 다시 골라 주세요");
        }

        // 열 부서: 관리자는 보낸 목록, 부서장은 자기 부서 하나 (보낸 목록은 무시)
        List<String> depts = admin ? form.getDepts() : List.of(headDept);
        if (depts == null || depts.isEmpty()) {
            throw new IllegalArgumentException("열 부서를 골라 주세요");
        }

        LocalDateTime until = LocalDateTime.now(SEOUL).plusMinutes(minutes);
        for (String dept : depts) {
            int rows = dao.openDept(companyId, dept, until, user.getUserAccountId());
            if (rows == 0) {
                throw new IllegalArgumentException("없는 부서입니다: " + dept);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("depts", depts);
        result.put("until", until.toString());
        return result;
    }

    /** 한 부서 끄기 (POST /employee/api/join/openings/{dept}/delete). 부서장은 자기 부서만 */
    @Transactional
    public void closeJoin(LoginUser user, String dept) {
        boolean admin = isAdmin(user);
        String headDept = requireApprover(user, admin);
        if (!admin && !dept.equals(headDept)) {
            throw new AccessDeniedException("자기 부서만 끌 수 있습니다");
        }
        dao.closeDept(user.getCompanyId(), dept);
    }

    /** 모두 끄기 (POST /employee/api/join/openings/delete). 관리자만 */
    @Transactional
    public void closeAllJoin(LoginUser user) {
        if (!isAdmin(user)) {
            throw new AccessDeniedException("모두 끄기는 업체 관리자만 할 수 있습니다");
        }
        dao.closeAll(user.getCompanyId());
    }

    // ---------- 계정 만들기 (로그인 없음) ----------

    /**
     * 업체 코드 확인 (GET /join/api/companies/{code}). 가입이 열린 부서와 직급 목록. 없는 업체·만료된 업체·열린
     * 부서 없음은 구분하지 않고 null (화면에 같은 모습으로 보이게)
     */
    public Map<String, Object> checkCompany(String companyCode) {
        // ① 코드 정리: 공백 빼고 소문자로
        if (companyCode == null) {
            return null;
        }
        String code = companyCode.trim().toLowerCase();

        // ②~④ 가입을 받는 업체인가 (없음·만료·인증 전은 acceptingCompany가 null)
        JoinCompanyDTO company = acceptingCompany(code);
        if (company == null) {
            return null;
        }
        Long companyId = company.getCompanyId();

        // ⑤ 열린 부서 이름만 모으기
        List<String> depts = new ArrayList<>();
        for (JoinOpeningDTO o : dao.listOpenings(companyId)) {
            depts.add(o.getDepartmentName());
        }
        if (depts.isEmpty()) {
            return null;
        }

        // ⑥ 조립
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyName", company.getCompanyName());
        result.put("depts", depts);
        result.put("ranks", dao.listGradeNames(companyId));
        return result;
    }

    /** 가입 승인 요청 (POST /join/api/companies/{code}/requests, 로그인 없음) */
    @Transactional
    public Map<String, Object> requestJoin(String companyCode, JoinRequestFormDTO form) {
        // ① 업체가 가입을 받나
        String code = companyCode == null ? "" : companyCode.trim().toLowerCase();
        JoinCompanyDTO company = acceptingCompany(code);
        if (company == null) {
            throw new IllegalArgumentException("가입을 받을 수 없는 업체 코드입니다");
        }
        Long companyId = company.getCompanyId();

        // ② 입력 확인
        String name = form.getName() == null ? "" : form.getName().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException("이름을 확인해 주세요");
        }
        String email = form.getEmail() == null ? "" : form.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("이메일 형식이 맞지 않습니다");
        }
        String password = form.getPassword();
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("비밀번호는 8자 이상으로 정해 주세요");
        }

        // ③ 부서(지금 열려 있나)·직급
        Long deptId = dao.findOpenDeptId(companyId, form.getDept());
        if (deptId == null) {
            throw new IllegalArgumentException("가입이 닫혔거나 없는 부서입니다");
        }
        Long gradeId = dao.findGradeId(companyId, form.getRank());
        if (gradeId == null) {
            throw new IllegalArgumentException("없는 직급입니다");
        }

        // ④ 이메일 중복. 비활성 계정이면 새로 가입하지 말고 관리자에게 다시 사용을 요청 (ACC-04)
        if (dao.countRetiredEmail(companyId, email) > 0) {
            throw new IllegalStateException("비활성화된 계정의 이메일입니다. 업체 관리자에게 다시 사용을 요청해 주세요");
        }
        if (dao.countLiveEmail(companyId, email) > 0) {
            throw new IllegalStateException("이미 승인 대기 중이거나 계정이 있는 이메일입니다");
        }

        // ⑤ 계정 저장: 사원 없음, ROLE_EMP, REQUESTED (로그인 안 됨)
        AccountDTO acc = new AccountDTO();
        acc.setCompanyId(companyId);
        acc.setEmail(email);
        acc.setPasswordHash(employeeService.hashPassword(password)); // 72바이트 검사 포함
        acc.setRoleCode("ROLE_EMP");
        acc.setStatusCode("REQUESTED");
        try {
            employeeDAO.insertAccount(acc); // acc.userAccountId 채워짐
        } catch (DuplicateKeyException e) {
            // 같은 이메일로 동시에 신청하면 ④를 함께 통과할 수 있음 → 고유 제약에서 걸린 것도 같은 안내로
            throw new IllegalStateException("이미 승인 대기 중이거나 계정이 있는 이메일입니다");
        }

        // ⑥ 요청 저장. 신청자에게는 키를, DB에는 키의 해시만
        String key = UUID.randomUUID().toString();
        JoinRequestDTO req = new JoinRequestDTO();
        req.setCompanyId(companyId);
        req.setUserAccountId(acc.getUserAccountId());
        req.setRequestName(name);
        req.setDepartmentId(deptId);
        req.setJobGradeId(gradeId);
        req.setRequestKeyHash(sha256(key));
        dao.insertRequest(req);

        // ⑦ 저장한 요청을 보기 모양으로 (번호 대신 키로 찾기)
        return getMyRequest(code, key);
    }

    /** 가입 승인 (POST /employee/api/join/requests/{reqId}/approve) */
    @Transactional
    public Map<String, Object> approveRequest(LoginUser user, Long requestId) {
        Long companyId = user.getCompanyId();

        // ① 권한: 관리자 or 부서장
        boolean admin = isAdmin(user);
        String headDept = requireApprover(user, admin);

        // ② 신청서 잡기 (FOR UPDATE) → 없으면 400, 대기 중 아니면 409
        JoinRequestViewDTO req = dao.lockRequest(companyId, requestId);
        if (req == null) {
            throw new JoinNotFoundException("없는 가입 요청입니다");
        }
        if (!"REQUESTED".equals(req.getRequestStatus())) {
            throw new IllegalStateException("이미 처리된 가입 요청입니다");
        }
        // 부서장은 자기 부서 신청만
        if (!admin && !req.getDepartmentName().equals(headDept)) {
            throw new AccessDeniedException("자기 부서 가입 요청만 처리할 수 있습니다");
        }

        // ③ 사번 발급 (번호표 기계 잠금 → 같은 업체 승인은 한 줄로 섬)
        String employeeNo;
        try {
            employeeNo = employeeService.issueEmployeeNo(companyId);
        } catch (IllegalStateException e) {
            // 원래 문구에 companyId 같은 내부 정보가 있어서 화면에는 일반 안내만
            throw new IllegalStateException("사번을 발급할 수 없습니다. 운영 담당자에게 문의해 주세요");
        }

        // ④ 좌석 확인 (운영 업체는 한도 없음)
        CompanyUsageDTO usage = subscriptionService.getCompanyUsage(companyId);
        if (usage != null) {
            int limit = subscriptionService.getSeatLimit(companyId);
            if (employeeService.countAccountsInUse(companyId) >= limit) {
                throw new IllegalStateException("인원이 가득 차서 승인할 수 없습니다");
            }
        }

        // ⑤ 사원 만들기 + 첫 연차
        EmployeeDTO emp = employeeService.createJoinedEmployee(companyId, employeeNo, req.getRequestName(),
                req.getDepartmentId(), req.getJobGradeId());

        // ⑥ 계정 연결·사용 시작 → 0행이면 막기
        int rows = dao.activateAccount(companyId, req.getUserAccountId(), emp.getEmployeeId());
        if (rows == 0) {
            throw new IllegalStateException("이미 처리된 가입 요청입니다");
        }

        // ⑦ 신청서 승인 완료 (처리한 사람 = 로그인 계정)
        dao.approveRequest(companyId, requestId, user.getUserAccountId());

        // ⑧ 화면에 줄 값
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", req.getRequestName());
        result.put("empNo", employeeNo);
        return result;
    }

    /** 가입 반려 (POST /employee/api/join/requests/{reqId}/reject) */
    @Transactional
    public Map<String, Object> rejectRequest(LoginUser user, Long requestId) {
        Long companyId = user.getCompanyId();

        // ① 권한: 관리자 or 부서장
        boolean admin = isAdmin(user);
        String headDept = requireApprover(user, admin);

        // ② 신청서 잡기 → 없으면 400, 대기 중 아니면 409, 부서장은 자기 부서만
        JoinRequestViewDTO req = dao.lockRequest(companyId, requestId);
        if (req == null) {
            throw new JoinNotFoundException("없는 가입 요청입니다");
        }
        if (!"REQUESTED".equals(req.getRequestStatus())) {
            throw new IllegalStateException("이미 처리된 가입 요청입니다");
        }
        if (!admin && !req.getDepartmentName().equals(headDept)) {
            throw new AccessDeniedException("자기 부서 가입 요청만 처리할 수 있습니다");
        }

        // ③ 신청서 반려 → 0행이면 409
        int rows = dao.rejectRequest(companyId, requestId, user.getUserAccountId());
        if (rows == 0) {
            throw new IllegalStateException("이미 처리된 가입 요청입니다");
        }

        // ④ 계정도 반려 (같은 이메일로 다시 신청할 수 있게)
        dao.rejectAccount(companyId, req.getUserAccountId());

        // ⑤ 화면에 줄 값: { name }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", req.getRequestName());
        return result;
    }

    /** 내 신청 취소 (POST /join/api/companies/{code}/requests/{key}/delete, 로그인 없음) */
    @Transactional
    public void cancelMyRequest(String companyCode, String requestKey) {
        // ① 코드 정리
        if (companyCode == null || requestKey == null) {
            throw new IllegalStateException("취소할 수 있는 신청이 없습니다");
        }
        String code = companyCode.trim().toLowerCase();

        // ② 키로 내 신청 찾기 → 없거나 대기 중이 아니면 막기
        JoinRequestViewDTO view = dao.getRequestView(code, sha256(requestKey));
        if (view == null || !"REQUESTED".equals(view.getRequestStatus())) {
            throw new IllegalStateException("취소할 수 있는 신청이 없습니다");
        }

        // ③ 요청 취소 (실제 번호는 찾은 행에서) → 바뀐 행이 0이면 막기
        int rows = dao.cancelRequest(view.getAccountRequestId(), view.getCompanyId());
        if (rows == 0) {
            throw new IllegalStateException("이미 처리된 신청입니다");
        }

        // ④ 계정도 취소
        dao.cancelAccount(view.getUserAccountId(), view.getCompanyId());
    }

    /** 내 신청 보기 (GET /join/api/companies/{code}/requests/{key}, 로그인 없음) */
    public Map<String, Object> getMyRequest(String companyCode, String requestKey) {
        if (companyCode == null || requestKey == null) {
            return null;
        }
        String code = companyCode.trim().toLowerCase();

        JoinRequestViewDTO request = dao.getRequestView(code, sha256(requestKey));
        if (request == null || "CANCELED".equals(request.getRequestStatus())) {
            return null;
        }

        String status = request.getRequestStatus(); // DB 상태 꺼내 두기
        String state; // 화면에 줄 상태
        String reason = null; // 끝난 이유

        if ("REQUESTED".equals(status)) {
            state = "WAITING";
        } else if ("APPROVED".equals(status)) {
            if ("ACTIVE".equals(request.getAccountStatus())) {
                state = "APPROVED";              // 승인됐고 계정도 사용 중
            } else {
                state = "ENDED";                 // 승인됐지만 지금은 계정이 꺼짐
                reason = "OFF";
            }
        } else {
            state = "ENDED";
            reason = "REJECTED";
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("reqId", requestKey);
        view.put("state", state);
        view.put("reason", reason);
        view.put("companyCode", request.getCompanyCode());
        view.put("companyName", request.getCompanyName());
        view.put("name", request.getRequestName());
        view.put("dept", request.getDepartmentName());
        view.put("rank", request.getGradeName());
        view.put("email", request.getEmail());
        view.put("depts", List.of(request.getDepartmentName()));
        view.put("ranks", request.getGradeName() == null ? List.of() : List.of(request.getGradeName()));
        view.put("empNo", request.getEmployeeNo());
        return view;
    }

    private JoinCompanyDTO acceptingCompany(String code) {
        JoinCompanyDTO company = dao.findCompanyByCode(code);
        if (company == null) {
            return null;
        }
        Long companyId = company.getCompanyId();

        // ③ 이용 상태: 기간이 없거나(운영 업체) 끝났으면 받지 않음
        CompanyUsageDTO usage = subscriptionService.getCompanyUsage(companyId);
        if (usage == null || usage.isExpired()) {
            return null;
        }

        // ④ 이메일 인증 전 업체 거절 (준원님 요청: 인증 메일이 아직 없어 주석)
        // TODO(ACC-06): 인증 메일이 붙으면 주석을 푼다
        // if (!usage.isEmailVerified()) {
        // return null;
        // }
        return company;
    }

    // ---------- 권한 ----------

    private boolean isAdmin(LoginUser user) {
        return "ROLE_ADMIN".equals(user.getRole());
    }

    /** 관리자나 부서장인지 확인하고, 부서장이면 그 부서 이름을 돌려준다(관리자는 null일 수 있음). 둘 다 아니면 403 */
    private String requireApprover(LoginUser user, boolean admin) {
        String headDept = dao.getHeadDeptName(user.getCompanyId(), user.getEmployeeId());
        if (!admin && headDept == null) {
            throw new AccessDeniedException("가입 승인은 업체 관리자와 부서장만 할 수 있습니다");
        }
        return headDept;
    }

    /** 현황판용 대기 목록. 관리자는 전부, 부서장은 자기 부서만 */
    private List<Map<String, Object>> waitingRequests(Long companyId, boolean admin, String headDept) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JoinRequestViewDTO r : dao.listWaitingRequests(companyId)) {
            if (!admin && !r.getDepartmentName().equals(headDept)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reqId", r.getAccountRequestId());
            m.put("name", r.getRequestName());
            m.put("dept", r.getDepartmentName());
            m.put("rank", r.getGradeName());
            m.put("email", r.getEmail());
            m.put("requestAt", r.getRequestedAt() == null ? null : r.getRequestedAt().toString());
            list.add(m);
        }
        return list;
    }

    /** 요청 키 → SHA-256 16진수 64자 (REQUEST_KEY_HASH에 저장·검색) */
    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("해시를 만들 수 없습니다", e);
        }
    }
}