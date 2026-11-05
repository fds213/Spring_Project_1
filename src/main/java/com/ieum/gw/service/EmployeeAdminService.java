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
    private static final Set<String> ACTIONS = Set.of("on", "off");
    private static final Set<String> EMP_TYPES = Set.of("EMP_REGULAR", "EMP_CONTRACT", "EMP_DISPATCH", "EMP_INTERN");
    private static final Set<String> WORKS = Set.of("ST_SERVING", "ST_RETIRED"); // 휴직은 이번 범위에서 쓰지 않는다(공통 코드 N)
    private static final Set<String> GENDERS = Set.of("GD_MALE", "GD_FEMALE", "GD_OTHER");
    
    private final EmployeeDAO dao;
    private final EmployeeService employeeService;          // 사용 중 계정 수 (결제·가입 승인과 같은 기준)
    private final SubscriptionService subscriptionService;  // 구매 인원·만료
    private final ApprovalService approvalService;          // 비활성화·퇴사 때 결재 차례 자동 반려

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
    
    /** 계정 비활성화·다시 사용 (POST /employee/admin/api/employees/{empNo}/status). 업체 관리자·인사 담당 */
    @Transactional
    public Map<String, Object> setStatus(LoginUser user, String empNo, String action) {
        // ① 권한
        if (!MANAGERS.contains(user.getRole())) {
            throw new AccessDeniedException("사원 관리는 업체 관리자와 인사 담당만 할 수 있습니다");
        }
        // ② 입력: 모르는 값이면 아무것도 바꾸지 않는다
        if (action == null || !ACTIONS.contains(action)) {
            throw new IllegalArgumentException("action은 on 또는 off로 보내 주세요");
        }
        Long companyId = user.getCompanyId();
        boolean admin = "ROLE_ADMIN".equals(user.getRole());
        boolean off = "off".equals(action);

        // ③ 만료 중에는 업체 관리자의 비활성화만 된다 (요구분석서 3.11: 자리를 줄이는 쪽이라 허용)
        CompanyUsageDTO usage = subscriptionService.getCompanyUsage(companyId);
        if (usage != null && usage.isExpired() && !(admin && off)) {
            throw new IllegalStateException("이용 기간이 끝나 읽기 전용입니다. 업체 관리자의 비활성화만 할 수 있습니다");
        }

        // ④ 대상 (다른 업체 사번이면 찾지 못한다)
        EmployeeHeadTargetDTO t = dao.getHeadTarget(companyId, empNo);
        if (t == null) {
            throw new IllegalArgumentException("없는 사원입니다");
        }

        int released = 0;
        if (off) {
            // ⑤ 업체 관리자 계정과 본인은 끌 수 없다 (목록의 protectedOne과 같은 기준)
            if ("ROLE_ADMIN".equals(t.getRoleCode()) || t.getEmployeeId().equals(user.getEmployeeId())) {
                throw new IllegalStateException("업체 관리자와 본인 계정은 비활성화할 수 없습니다");
            }
            // ⑥ 사용 중일 때만 끈다
            if (dao.retireAccount(companyId, t.getEmployeeId()) == 0) {
                throw new IllegalStateException("이미 비활성 계정입니다. 화면을 새로 고쳐 주세요");
            }
            // ⑦ 부서장 칸 비우기, 결재 차례 자동 반려
            released = afterTurnedOff(companyId, t.getEmployeeId());
        	} else {
            // 본인 계정은 스스로 켤 수 없다 (#127 리뷰: 꺼진 인사 담당이 기존 세션으로 자기를 되살리던 것)
            if (t.getEmployeeId().equals(user.getEmployeeId())) {
                throw new IllegalStateException("본인 계정은 다시 사용으로 바꿀 수 없습니다");
            }
            // ⑤ 퇴사자는 인사 정보에서 재직 상태를 먼저 바꿔야 한다
            if ("ST_RETIRED".equals(t.getEmploymentStatus())) {
                throw new IllegalStateException("퇴사 처리된 사원입니다. 인사 정보에서 재직 상태를 먼저 바꿔 주세요");
            }
            // ⑥ 자리: 가입 승인과 같은 자물쇠(사번 카운터)를 잡고 센다. 동시에 눌러도 한도를 넘지 않는다
            if (usage != null) {
                dao.lockLastSerial(companyId);
                if (employeeService.countAccountsInUse(companyId) >= subscriptionService.getSeatLimit(companyId)) {
                    throw new IllegalStateException("빈 자리가 없습니다. 구매 인원을 늘리거나 다른 계정을 비활성화해 주세요");
                }
            }
            // 0행: 이미 켜졌거나, 기다리는 사이 다른 사람이 퇴사로 저장했다 (SQL이 재직 상태를 다시 본다)
            if (dao.reactivateAccount(companyId, t.getEmployeeId()) == 0) {
                throw new IllegalStateException("이미 사용 중이거나 퇴사 처리된 계정입니다. 화면을 새로 고쳐 주세요");
            }
        }

        // 화면 토스트용
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", t.getEmployeeName());
        result.put("released", released);
        return result;
    }
    
    /** 인사 정보 저장 (POST /employee/admin/api/employees/{empNo}/hr). 퇴사로 바꾸면 계정도 끈다 */
    @Transactional
    public Map<String, Object> setHrInfo(LoginUser user, String empNo, String empType, String work, String gender) {
        // ① 권한
        if (!MANAGERS.contains(user.getRole())) {
            throw new AccessDeniedException("사원 관리는 업체 관리자와 인사 담당만 할 수 있습니다");
        }
        // ② 값 검사 (성별은 선택: 빈 값이면 null)
        String genderCode = (gender == null || gender.isEmpty()) ? null : gender;
        if (empType == null || !EMP_TYPES.contains(empType)) {
            throw new IllegalArgumentException("고용 형태를 다시 골라 주세요");
        }
        if (work == null || !WORKS.contains(work)) {
            throw new IllegalArgumentException("재직 상태를 다시 골라 주세요");
        }
        if (genderCode != null && !GENDERS.contains(genderCode)) {
            throw new IllegalArgumentException("성별을 다시 골라 주세요");
        }
        Long companyId = user.getCompanyId();

        // ③ 대상
        EmployeeHeadTargetDTO t = dao.getHeadTarget(companyId, empNo);
        if (t == null) {
            throw new IllegalArgumentException("없는 사원입니다");
        }
        // ④ 업체 관리자와 본인은 퇴사로 바꿀 수 없다 (목록의 canRetire와 같은 기준)
        boolean retiring = "ST_RETIRED".equals(work) && !"ST_RETIRED".equals(t.getEmploymentStatus());
        if (retiring && ("ROLE_ADMIN".equals(t.getRoleCode()) || t.getEmployeeId().equals(user.getEmployeeId()))) {
            throw new IllegalStateException("업체 관리자와 본인은 퇴사로 바꿀 수 없습니다");
        }

        // ⑤ 저장
        dao.updateHrInfo(companyId, t.getEmployeeId(), empType, work, genderCode);

        // ⑥ 이번에 퇴사가 됐으면 계정도 끄고 정리한다 (재직으로 돌려도 계정은 다시 사용으로 따로 켠다: 자리 확인)
        boolean turnedOff = false;
        int released = 0;
        if (retiring) {
            turnedOff = dao.retireAccount(companyId, t.getEmployeeId()) == 1;
            released = afterTurnedOff(companyId, t.getEmployeeId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", t.getEmployeeName());
        result.put("turnedOff", turnedOff);
        result.put("released", released);
        return result;
    }

    /** 계정이 꺼진 뒤 정리: 부서장 칸 비우기, 이 사람이 결재할 차례인 문서 자동 반려. 반려한 건수 */
    private int afterTurnedOff(Long companyId, Long employeeId) {
        dao.clearHeadOf(companyId, employeeId);
        return approvalService.rejectWaitingForRetired(companyId, employeeId);
    }
}