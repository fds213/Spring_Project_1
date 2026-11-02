package com.ieum.gw.employee.service;

import java.time.LocalDate;
import java.time.LocalDateTime;                                         
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;                                                  

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;       

import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.employee.dao.JoinDAO;
import com.ieum.gw.employee.dto.JoinOpenFormDTO;                       
import com.ieum.gw.employee.dto.JoinOpeningDTO;
import com.ieum.gw.subscription.dto.CompanyUsageDTO;
import com.ieum.gw.subscription.service.SubscriptionService;

import lombok.RequiredArgsConstructor;

/**
 * 직원 가입 (ACC-04·05, 단순화 ①②). 가입 열기·닫기, 가입 요청, 승인·반려.
 * 업체 관리자는 업체 전체, 부서장은 자기 부서만 다룬다. 업체·사람은 로그인 정보에서만 꺼낸다.
 */
@Service
@RequiredArgsConstructor
public class JoinService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<Integer> OPEN_MINUTES = Set.of(10, 30, 60, 180, 1440); 

    private final JoinDAO dao;
    private final SubscriptionService subscriptionService; // 인원 한도·이용 상태 (준원님)

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
        int used = dao.countActiveAccounts(companyId);
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
        String nextEmpNo = last == null ? null
                : String.format("%d%04d", LocalDate.now(SEOUL).getYear(), last + 1);

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
        board.put("requests", List.of());   // 가입 요청 목록은 ②-2에서 채운다
        return board;
    }

    //  ---------- 가입 열기·끄기 ----------

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

    //  ---------- 권한 ----------

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
}