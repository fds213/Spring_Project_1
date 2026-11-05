package com.ieum.gw.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    int markAccountEmailVerified(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId,
            @Param("verifiedAt") java.time.LocalDateTime verifiedAt);

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

	List<EmployeeAdminRowDTO> listAdminRows(@Param("companyId") Long companyId);

	EmployeeHeadTargetDTO getHeadTarget(@Param("companyId") Long companyId, @Param("employeeNo") String employeeNo);

	int setDeptHead(@Param("companyId") Long companyId, @Param("departmentId") Long departmentId,
			@Param("employeeId") Long employeeId);

	int clearDeptHead(@Param("companyId") Long companyId, @Param("departmentId") Long departmentId,
			@Param("employeeId") Long employeeId);
	
	EmployeeLoginInfoDTO getLoginInfo(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

	int retireAccount(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

	int reactivateAccount(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

	int clearHeadOf(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

	// ── 부서·직급 관리 (CFG-01) ──

	/** 목록 (사용 중인 것만, 순서대로, 인원·요청 수 포함) */
	List<EmployeeOrgItemDTO> listOrgItems(@Param("companyId") Long companyId, @Param("kind") EmployeeOrgKind kind);

	/** 쓰기 전에 그 업체의 목록 행을 잠그고 순서대로 */
	List<EmployeeOrgItemDTO> lockOrgItems(@Param("companyId") Long companyId, @Param("kind") EmployeeOrgKind kind);

	/** 예전에 지운 같은 이름의 번호. 없으면 null */
	Long findDeletedOrgId(@Param("companyId") Long companyId, @Param("kind") EmployeeOrgKind kind, @Param("name") String name);

	/** 새로 추가 */
	int insertOrg(@Param("companyId") Long companyId, @Param("kind") EmployeeOrgKind kind, @Param("name") String name,
			@Param("sortOrder") int sortOrder);

	/** 지운 것을 되살리기 */
	int reviveOrg(@Param("companyId") Long companyId, @Param("kind") EmployeeOrgKind kind, @Param("id") Long id,
			@Param("sortOrder") int sortOrder);

	/** 이름 바꾸기 */
	int renameOrg(@Param("companyId") Long companyId, @Param("kind") EmployeeOrgKind kind, @Param("id") Long id,
			@Param("name") String name);

	/** 순서 번호 저장 */
	int updateOrgSortOrder(@Param("companyId") Long companyId, @Param("kind") EmployeeOrgKind kind, @Param("id") Long id,
			@Param("sortOrder") int sortOrder);

	/** 삭제(사용 안 함). 부서면 가입 활성화·부서장도 비움 */
	int deleteOrg(@Param("companyId") Long companyId, @Param("kind") EmployeeOrgKind kind, @Param("id") Long id);

	/** 부서 형태 바꾸기 (null = 일반) */
	int updateDeptType(@Param("companyId") Long companyId, @Param("id") Long id, @Param("type") String type);
	}