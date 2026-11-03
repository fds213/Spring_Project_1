package com.ieum.gw.employee.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.employee.dto.AccountDTO;
import com.ieum.gw.employee.dto.EmpSummaryDTO;
import com.ieum.gw.employee.dto.EmployeeDTO;
import com.ieum.gw.employee.dto.EmployeeOptionDTO;
import com.ieum.gw.employee.dto.IdNameDTO;
import com.ieum.gw.employee.mapper.EmployeeMapper;

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

    // ── 연동 확인 ──

    /** 같은 업체 재직자(재직 + 계정 사용 중)면 1, 아니면 0 */
    public int countActiveEmployee(Long companyId, Long employeeId) {
        return mapper.countActiveEmployee(companyId, employeeId);
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
	public List<EmployeeOptionDTO> listAssignableEmployees(Long companyId) {
		return mapper.listAssignableEmployees(companyId);
	}
}