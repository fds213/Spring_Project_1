package com.ieum.gw.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dto.CompanyUsageDTO;
import com.ieum.gw.dto.EmployeeAdminRowDTO;
import com.ieum.gw.dto.EmployeeHeadTargetDTO;
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
            // 만료 중에는 업체 관리자의 비활성화만 된다
            if (active && !protectedOne && (admin || !readOnly)) actions.add("off");
            if (!readOnly && "RETIRED".equals(r.getStatusCode())) actions.add("on");
            if (!readOnly && admin && active && r.getDepartmentName() != null) actions.add("head");
            
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
    
    /** 부서장 지정·해제 (POST /employee/admin/api/employees/{empNo}/head). 업체 관리자만 */
    @Transactional
    public Map<String, Object> setHead(LoginUser user, String empNo, Boolean head) {        
        // ① 권한: 인사 담당도 이 주소까지는 들어오지만(Security), 부서장 지정은 관리자만
    	if (!"ROLE_ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("부서장 지정은 업체 관리자만 할 수 있습니다");
        }
        if (head == null) {
            throw new IllegalArgumentException("부서장 지정 여부(head)를 보내 주세요");
        }
        
        Long companyId = user.getCompanyId();

        // ② 대상 찾기 (다른 업체 사번이면 찾지 못한다)
        EmployeeHeadTargetDTO t = dao.getHeadTarget(companyId, empNo);
        if (t == null) {
            throw new IllegalArgumentException("없는 사원입니다");
        }
        if (t.getDepartmentId() == null) {
            throw new IllegalStateException("부서가 정해지지 않은 사원입니다");
        }

        // ③·④ 지정은 사용 중 계정만, 그 부서의 부서장 칸을 덮어쓴다
        if (head) {
            if (!"ACTIVE".equals(t.getStatusCode())) {
                throw new IllegalStateException("사용 중인 계정만 부서장으로 지정할 수 있습니다");
            }
            dao.setDeptHead(companyId, t.getDepartmentId(), t.getEmployeeId());
        // 해제는 지금 부서장이 이 사람일 때만 (0행이면 그사이 다른 사람으로 바뀌었다)
        } else if (dao.clearDeptHead(companyId, t.getDepartmentId(), t.getEmployeeId()) == 0) {
            throw new IllegalStateException("이미 부서장이 아닙니다. 화면을 새로 고쳐 주세요");
        }

        // ⑤ 화면 토스트용
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("head", head);
        result.put("name", t.getEmployeeName());
        result.put("dept", t.getDepartmentName());
        return result;
    }
}