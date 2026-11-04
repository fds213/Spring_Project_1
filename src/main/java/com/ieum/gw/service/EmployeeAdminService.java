package com.ieum.gw.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dto.CompanyUsageDTO;
import com.ieum.gw.dto.EmployeeAdminRowDTO;
import com.ieum.gw.dto.LoginUser;

import lombok.RequiredArgsConstructor;

/** 사원 관리 (CFG-02). 업체 관리자·인사 담당. 업체는 로그인 정보에서만 꺼낸다 */
@Service
@RequiredArgsConstructor
public class EmployeeAdminService {

    private static final Set<String> MANAGERS = Set.of("ROLE_ADMIN", "ROLE_HR");

    private final EmployeeDAO dao;
    private final EmployeeService employeeService;          // 사용 중 계정 수 (결제·가입 승인과 같은 기준)
    private final SubscriptionService subscriptionService;  // 구매 인원·만료

    /** 사원 관리 목록 (GET /employee/admin/api/employees) */
    public Map<String, Object> getList(LoginUser user) {
        // ① 권한
        if (!MANAGERS.contains(user.getRole())) {
            throw new AccessDeniedException("사원 관리는 업체 관리자와 인사 담당만 할 수 있습니다");
        }
        Long companyId = user.getCompanyId();
        boolean admin = "ROLE_ADMIN".equals(user.getRole());

        // ② 자리: 가입 승인 현황판과 같은 계산
        int used = employeeService.countAccountsInUse(companyId);
        CompanyUsageDTO usage = subscriptionService.getCompanyUsage(companyId);
        Map<String, Object> caps = null;
        boolean full = false;
        boolean readOnly = false;
        if (usage != null) {
            int limit = subscriptionService.getSeatLimit(companyId);
            full = used >= limit;
            readOnly = usage.isExpired();
            caps = new LinkedHashMap<>();
            caps.put("active", limit);
            caps.put("paid", !usage.isTrial());
        }

        // ③ 한 사람씩 화면 모양으로
        List<Map<String, Object>> items = new ArrayList<>();
        int off = 0;
        int seat = 0;
        for (EmployeeAdminRowDTO r : dao.listAdminRows(companyId)) {
            boolean active = "ACTIVE".equals(r.getStatusCode());
            boolean protectedOne = "ROLE_ADMIN".equals(r.getRoleCode())
                    || r.getEmployeeId().equals(user.getEmployeeId());   // 관리자·본인은 끌 수 없다
            if (active) seat++; else off++;

            List<String> actions = new ArrayList<>();
            if (active && !protectedOne) actions.add("off");
            if ("RETIRED".equals(r.getStatusCode())) actions.add("on");
            if (admin && active && r.getDepartmentName() != null) actions.add("head");

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getEmployeeName());
            m.put("empNo", r.getEmployeeNo());
            m.put("dept", r.getDepartmentName());
            m.put("deptType", r.getDepartmentType());
            m.put("head", r.isHead());
            m.put("rank", r.getGradeName());
            m.put("role", r.getRoleCode());
            m.put("status", r.getStatusCode());
            m.put("work", r.getEmploymentStatus());
            m.put("empType", r.getEmploymentType());
            m.put("gender", r.getGenderCode());
            m.put("at", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
            m.put("actions", actions);
            m.put("canRetire", !protectedOne);
            items.add(m);
        }

        // ④ 조립
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("perms", Map.of("admin", admin, "pay", admin)); // shortcut: 결제 버튼은 관리자만, 구독 권한(SUB-01)이 생기면 바꾼다
        result.put("caps", caps);
        result.put("readOnly", readOnly);
        result.put("seats", Map.of("used", used, "full", full));
        result.put("counts", Map.of("total", items.size(), "off", off, "seat", seat));
        result.put("items", items);
        return result;
    }
}