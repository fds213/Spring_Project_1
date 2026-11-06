package com.ieum.gw.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.code.EmployeeOrgKind;
import com.ieum.gw.dto.AccountDTO;
import com.ieum.gw.dto.EmpSummaryDTO;
import com.ieum.gw.dto.EmployeeAdminRowDTO;
import com.ieum.gw.dto.EmployeeDTO;
import com.ieum.gw.dto.EmployeeHeadTargetDTO;
import com.ieum.gw.dto.EmployeeLoginInfoDTO;
import com.ieum.gw.dto.EmployeeOptionDTO;
import com.ieum.gw.dto.EmployeeOrgItemDTO;
import com.ieum.gw.dto.IdNameDTO;
import com.ieum.gw.dto.OrgDeptDTO;
import com.ieum.gw.dto.OrgMemberDTO;
import com.ieum.gw.mapper.EmployeeMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EmployeeDAO {

    private final EmployeeMapper mapper;

    /** 재직자 목록 (채팅·결재용) */
    public List<EmpSummaryDTO> listActiveEmp(Long companyId, Long excludeEmpId) {
        return mapper.listActiveEmp(companyId, excludeEmpId);
    }

    // ── 사번 발급 ──

    /** 새 업체의 사번 카운터 행 (마지막 번호 0) */
    public int insertNoCounter(Long companyId) {
        return mapper.insertNoCounter(companyId);
    }

    /** 카운터 잠그고 마지막 번호. 행이 없으면 null */
    public Integer lockLastSerial(Long companyId) {
        return mapper.lockLastSerial(companyId);
    }

    /** 올린 번호 저장 */
    public int updateLastSerial(Long companyId, int lastSerial) {
        return mapper.updateLastSerial(companyId, lastSerial);
    }

    // ── 사원·계정 만들기 ──

    /** 사원 1행. 성공하면 dto.employeeId에 새 번호 */
    public int insertEmployee(EmployeeDTO dto) {
        return mapper.insertEmployee(dto);
    }

    /** 계정 1행. 성공하면 dto.userAccountId에 새 번호 */
    public int insertAccount(AccountDTO dto) {
        return mapper.insertAccount(dto);
    }
    
    /** 비밀번호 변경: 본인 계정의 지금 비밀번호 해시 (사용 중 계정만, 없으면 null) */
    public String findPasswordHash(Long companyId, Long userAccountId) {
        return mapper.findPasswordHash(companyId, userAccountId);
    }

    /** 비밀번호 바꾸기: 확인한 해시가 그대로일 때만. 바뀐 행 수 */
    public int updatePassword(Long companyId, Long userAccountId, String oldHash, String newHash) {
        return mapper.updatePassword(companyId, userAccountId, oldHash, newHash);
    }

    /** 계정 이메일 인증 시각. 이미 인증한 계정이면 0 */
    public int markAccountEmailVerified(Long companyId, Long userAccountId, java.time.LocalDateTime verifiedAt) {
        return mapper.markAccountEmailVerified(companyId, userAccountId, verifiedAt);
    }

    // ── 연동 확인 ──

    /** 같은 업체 재직자(재직 + 계정 사용 중)면 1, 아니면 0 */
    public int countActiveEmployee(Long companyId, Long employeeId) {
        return mapper.countActiveEmployee(companyId, employeeId);
    }

    /** 사용 중인 재직자의 지금 역할. 사용 중이 아니면 null */
    public String getActiveRole(Long companyId, Long employeeId) {
        return mapper.getActiveRole(companyId, employeeId);
    }

    /** 역할이 목록 중 하나면 1, 아니면 0 */
    public int countRole(Long companyId, Long employeeId, List<String> roleCodes) {
        return mapper.countRole(companyId, employeeId, roleCodes);
    }
    
 // ── 다른 모듈용 직원 조회 (같은 업체만) ──
    
    /** 직원의 근무 지점 ID. 없으면 null */
	public Long getWorkBranchId(Long companyId, Long employeeId) {
		return mapper.getWorkBranchId(companyId, employeeId);
	}

    /** 직원 ID → 이름 (퇴사자 포함) */
	public List<IdNameDTO> listEmployeeNames(Long companyId, List<Long> ids) {
		return mapper.listEmployeeNames(companyId, ids);
	}

    /** 계정 ID → 직원 이름 (사원이 연결된 계정만) */
	public List<IdNameDTO> listAccountNames(Long companyId, List<Long> ids) {
		return mapper.listAccountNames(companyId, ids);
	}

    /** 담당자로 고를 수 있는 재직자 */
	public List<EmployeeOptionDTO> listAssignableEmployees(Long companyId, List<String> roleCodes) {
		return mapper.listAssignableEmployees(companyId, roleCodes);
	}
	
    /** 사용 중(ACTIVE) 계정 수 */
    public int countAccountsInUse(Long companyId) {
        return mapper.countAccountsInUse(companyId);
    }
    
    /** 직원 ID → 이름·부서명 (퇴사자 포함) */
    public List<EmployeeOptionDTO> listEmployeeNamesWithDept(Long companyId, List<Long> ids) {
        return mapper.listEmployeeNamesWithDept(companyId, ids);
    }
    
    /** 사원 관리 목록 (사원증 있는 사람만) */
    public List<EmployeeAdminRowDTO> listAdminRows(Long companyId) {
        return mapper.listAdminRows(companyId);
    }
    
    /** 조직도 부서 카드: 사용 중 부서 (관리 화면 순서) */
    public List<OrgDeptDTO> listOrgDepartments(Long companyId) {
        return mapper.listOrgDepartments(companyId);
    }

    /** 조직도 사람: 재직 + 계정 사용 중 (부서장 → 직급 높은 순 → 이름) */
    public List<OrgMemberDTO> listOrgMembers(Long companyId) {
        return mapper.listOrgMembers(companyId);
    }
    
    /** 부서장 지정 대상. 같은 업체에 그 사번이 없으면 null */
    public EmployeeHeadTargetDTO getHeadTarget(Long companyId, String employeeNo) {
        return mapper.getHeadTarget(companyId, employeeNo);
    }

    /** 역할 바꾸기 (직원·IT 담당·인사 담당끼리만). 업체 관리자 계정이면 0 */
    public int updateRole(Long companyId, Long employeeId, String roleCode) {
        return mapper.updateRole(companyId, employeeId, roleCode);
    }

    /** 그 부서의 부서장을 이 사람으로 */
    public int setDeptHead(Long companyId, Long departmentId, Long employeeId) {
        return mapper.setDeptHead(companyId, departmentId, employeeId);
    }

    /** 지금 부서장이 이 사람일 때만 비운다. 바뀐 행 수 */
    public int clearDeptHead(Long companyId, Long departmentId, Long employeeId) {
        return mapper.clearDeptHead(companyId, departmentId, employeeId);
    }
    
    /** 화면 공통 값 (헤더·사이드바). 사원 행이 없으면 null */
    public EmployeeLoginInfoDTO getLoginInfo(Long companyId, Long employeeId) {
        return mapper.getLoginInfo(companyId, employeeId);
    }
    
    /** 계정 비활성화(ACTIVE → RETIRED). 바뀐 행 수 */
    public int retireAccount(Long companyId, Long employeeId) {
        return mapper.retireAccount(companyId, employeeId);
    }

    /** 계정 다시 사용(RETIRED → ACTIVE). 바뀐 행 수 */
    public int reactivateAccount(Long companyId, Long employeeId) {
        return mapper.reactivateAccount(companyId, employeeId);
    }

    /** 이 사람이 부서장인 부서의 부서장 칸 비우기 */
    public int clearHeadOf(Long companyId, Long employeeId) {
        return mapper.clearHeadOf(companyId, employeeId);
    }
    
    // ── 부서·직급 관리 (CFG-01) ──
    /** 부서·직급을 바꾸기 전에 업체 행 잠금 (동시 추가 직렬화) */
    public Long lockCompanyForOrg(Long companyId) {
        return mapper.lockCompanyForOrg(companyId);
    }

    /** 목록 (사용 중인 것만, 순서대로, 인원·요청 수 포함) */
    public List<EmployeeOrgItemDTO> listOrgItems(Long companyId, EmployeeOrgKind kind) {
        return mapper.listOrgItems(companyId, kind);
    }

    /** 쓰기 전에 그 업체의 목록 행을 잠그고 순서대로 */
    public List<EmployeeOrgItemDTO> lockOrgItems(Long companyId, EmployeeOrgKind kind) {
        return mapper.lockOrgItems(companyId, kind);
    }

    /** 예전에 지운 같은 이름의 번호. 없으면 null */
    public Long findDeletedOrgId(Long companyId, EmployeeOrgKind kind, String name) {
        return mapper.findDeletedOrgId(companyId, kind, name);
    }

    /** 새로 추가 */
    public int insertOrg(Long companyId, EmployeeOrgKind kind, String name, int sortOrder) {
        return mapper.insertOrg(companyId, kind, name, sortOrder);
    }

    /** 지운 것을 되살리기 */
    public int reviveOrg(Long companyId, EmployeeOrgKind kind, Long id, int sortOrder) {
        return mapper.reviveOrg(companyId, kind, id, sortOrder);
    }

    /** 이름 바꾸기 */
    public int renameOrg(Long companyId, EmployeeOrgKind kind, Long id, String name) {
        return mapper.renameOrg(companyId, kind, id, name);
    }

    /** 순서 번호 저장 */
    public int updateOrgSortOrder(Long companyId, EmployeeOrgKind kind, Long id, int sortOrder) {
        return mapper.updateOrgSortOrder(companyId, kind, id, sortOrder);
    }

    /** 삭제(사용 안 함). 부서면 가입 활성화·부서장도 비움 */
    public int deleteOrg(Long companyId, EmployeeOrgKind kind, Long id) {
        return mapper.deleteOrg(companyId, kind, id);
    }

    /** 부서 형태 바꾸기 (null = 일반) */
    public int updateDeptType(Long companyId, Long id, String type) {
        return mapper.updateDeptType(companyId, id, type);
    }
    
    /** 인사 정보 저장 (고용 형태·재직 상태·성별, 퇴사일) */
    public int updateHrInfo(Long companyId, Long employeeId, String empType, String work, String gender) {
        return mapper.updateHrInfo(companyId, employeeId, empType, work, gender);
    }
}