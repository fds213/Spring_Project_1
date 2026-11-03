package com.ieum.gw.employee.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.employee.dto.AccountDTO;
import com.ieum.gw.employee.dto.EmpSummaryDTO;
import com.ieum.gw.employee.dto.EmployeeDTO;
import com.ieum.gw.employee.dto.EmployeeOptionDTO;
import com.ieum.gw.employee.dto.IdNameDTO;

@Mapper
public interface EmployeeMapper {

    // 재직자 목록 (채팅·결재용)
    List<EmpSummaryDTO> listActiveEmp(Long companyId, Long excludeEmpId);

    // ── 사번 발급 ──
    int insertNoCounter(@Param("companyId") Long companyId);
    Integer lockLastSerial(@Param("companyId") Long companyId);
    int updateLastSerial(@Param("companyId") Long companyId, @Param("lastSerial") int lastSerial);

    // ── 사원·계정 만들기 ──
    int insertEmployee(EmployeeDTO dto);
    int insertAccount(AccountDTO dto);

    // ── 연동 확인 ──
    int countActiveEmployee(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);
    int countRole(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                  @Param("roleCodes") List<String> roleCodes);
    
	Long getWorkBranchId(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

	List<IdNameDTO> listEmployeeNames(@Param("companyId") Long companyId, @Param("ids") List<Long> ids);

	List<IdNameDTO> listAccountNames(@Param("companyId") Long companyId, @Param("ids") List<Long> ids);

	List<EmployeeOptionDTO> listAssignableEmployees(@Param("companyId") Long companyId,
			@Param("roleCodes") List<String> roleCodes);
	
	int countAccountsInUse(@Param("companyId") Long companyId);
	
	List<EmployeeOptionDTO> listEmployeeNamesWithDept(@Param("companyId") Long companyId, @Param("ids") List<Long> ids);
}