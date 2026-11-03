package com.ieum.gw.employee.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.attendance.service.AttendanceService;
import com.ieum.gw.employee.dao.EmployeeDAO;
import com.ieum.gw.employee.dto.AccountDTO;
import com.ieum.gw.employee.dto.AdminAccountDTO;
import com.ieum.gw.employee.dto.EmpSummaryDTO;
import com.ieum.gw.employee.dto.EmployeeDTO;

/**
 * 사원이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 */
@Service
public class EmployeeService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_SERIAL = 9999;          // 사번 뒤 4자리 (CK_14_01)
    private static final int MAX_PASSWORD_BYTES = 72;    // BCrypt가 보는 최대 길이

    @Autowired
    private EmployeeDAO dao;
    @Autowired
    private PasswordEncoder passwordEncoder;             // security-context.xml의 bCryptPasswordEncoder
    @Autowired
    private AttendanceService attendanceService;

    // 현재 활성화 상태인 직원 목록 ( excludeEmpId가 NULL이 아니면 해당 사번 제외한 나머지 반환)
    public List<EmpSummaryDTO> listActiveEmp(Long companyId, Long excludeEmpId) {
        return dao.listActiveEmp(companyId, excludeEmpId);
    }

    /**
     * 같은 업체의 재직자인지 본다. 재직자는 사원이 재직(ST_SERVING)이고 계정이 사용 중(ACTIVE)인 사람이다.
     * 결재가 결재자를 고를 때와 결재할 때 부른다.
     */
    public boolean isActiveEmployee(Long companyId, Long employeeId) {
        if (companyId == null || employeeId == null) {
            return false;
        }
        return dao.countActiveEmployee(companyId, employeeId) > 0;
    }

    /**
     * 같은 업체 사원의 역할이 roleCodes 중 하나인지 본다. 다른 사람의 역할을 볼 때 쓴다
     * (예: 티켓을 배정받을 사람이 ROLE_IT·ROLE_ADMIN인지). 로그인한 본인의 역할은 LoginUser에서 꺼낸다.
     *
     * @param roleCodes ROLE_EMP·ROLE_IT·ROLE_HR·ROLE_ADMIN 중 하나 이상
     */
    public boolean hasAnyRole(Long companyId, Long employeeId, String... roleCodes) {
        if (companyId == null || employeeId == null || roleCodes == null || roleCodes.length == 0) {
            return false;
        }
        return dao.countRole(companyId, employeeId, Arrays.asList(roleCodes)) > 0;
    }

    /**
     * 체험 신청 때 업체 관리자(ROLE_ADMIN) 사원과 계정을 만든다. 체험 신청 트랜잭션 안에서 부른다(AGENTS.md 52줄).
     * 인증 메일을 붙이기 전이라 바로 로그인할 수 있게(ACTIVE) 만든다(요구분석서 3장 범위 표 아래).
     * 사번 발급(업체별 다음 번호, AGENTS.md 179줄)과 첫 연차(AttendanceService.grantInitialLeave)도 이 안에서 한다.
     *
     * @param rawPassword 평문 비밀번호. 여기서 BCrypt로 해시하고 로그에 남기지 않는다
     * @return 만든 사원 ID·계정 ID·사번
     */
    @Transactional
    public AdminAccountDTO createCompanyAdmin(Long companyId, String adminName, String email, String rawPassword) {
        // ① 새 업체라 사번 카운터 행이 없음 → 만들기 (마지막 번호 0)
        dao.insertNoCounter(companyId);

        // ② 첫 사번 (예: 20260001)
        String employeeNo = issueEmployeeNo(companyId);
        LocalDate today = LocalDate.now(SEOUL);

        // ③ 사원
        EmployeeDTO emp = new EmployeeDTO();
        emp.setCompanyId(companyId);
        emp.setEmployeeNo(employeeNo);
        emp.setEmployeeName(adminName);
        emp.setHireDate(today);
        emp.setEmploymentStatus("ST_SERVING");
        dao.insertEmployee(emp);                         // emp.employeeId 채워짐

        // ④ 계정
        AccountDTO acc = new AccountDTO();
        acc.setCompanyId(companyId);
        acc.setEmployeeId(emp.getEmployeeId());
        acc.setEmail(email.trim().toLowerCase());        // CK_07_01: 소문자·공백 없음
        acc.setPasswordHash(hashPassword(rawPassword));
        acc.setRoleCode("ROLE_ADMIN");
        acc.setStatusCode("ACTIVE");
        dao.insertAccount(acc);                          // acc.userAccountId 채워짐

        // ⑤ 첫 연차 (근태 담당 메서드, 같은 트랜잭션)
        attendanceService.grantInitialLeave(companyId, emp.getEmployeeId(), today);

        // ⑥ 결과
        AdminAccountDTO result = new AdminAccountDTO();
        result.setEmployeeId(emp.getEmployeeId());
        result.setUserAccountId(acc.getUserAccountId());
        result.setEmployeeNo(employeeNo);
        return result;
    }

    /**
     * 업체의 다음 사번을 발급한다. 카운터 행을 잠그고(FOR UPDATE) 번호를 하나 올린다.
     * 호출한 트랜잭션이 끝날 때까지 같은 업체의 다른 발급은 기다리므로 번호가 겹치지 않는다.
     * 직원 가입 승인에서도 쓴다.
     *
     * @return 발급 연도 4자리 + 일련번호 4자리 (예: 20260001)
     */
    String issueEmployeeNo(Long companyId) {
        Integer last = dao.lockLastSerial(companyId);
        if (last == null) {
            throw new IllegalStateException("사번 카운터가 없는 업체입니다: companyId=" + companyId);
        }
        int next = last + 1;
        if (next > MAX_SERIAL) {
            throw new IllegalStateException("사번을 더 발급할 수 없습니다: companyId=" + companyId);
        }
        dao.updateLastSerial(companyId, next);
        return String.format("%d%04d", LocalDate.now(SEOUL).getYear(), next);
    }

    /** 평문 비밀번호를 BCrypt로. 72바이트를 넘으면 BCrypt가 뒤를 무시하므로 거절한다 */
    String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("비밀번호를 입력해 주세요");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("비밀번호가 너무 깁니다");
        }
        return passwordEncoder.encode(rawPassword);
    }
}