package com.ieum.gw.employee.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.employee.dto.JoinCompanyDTO;
import com.ieum.gw.employee.dto.JoinOpeningDTO;
import com.ieum.gw.employee.dto.JoinRequestDTO;
import com.ieum.gw.employee.dto.JoinRequestViewDTO;

@Mapper
public interface JoinMapper {

	String getCompanyCode(@Param("companyId") Long companyId);

	String getHeadDeptName(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

	List<String> listDeptNames(@Param("companyId") Long companyId);

	List<JoinOpeningDTO> listOpenings(@Param("companyId") Long companyId);

	int countActiveAccounts(@Param("companyId") Long companyId);

	Integer peekLastSerial(@Param("companyId") Long companyId);

	int openDept(@Param("companyId") Long companyId, @Param("deptName") String deptName,
			@Param("until") LocalDateTime until, @Param("openedBy") Long openedBy);

	int closeDept(@Param("companyId") Long companyId, @Param("deptName") String deptName);

	int closeAll(@Param("companyId") Long companyId);

	JoinCompanyDTO findCompanyByCode(@Param("companyCode") String companyCode);

	List<String> listGradeNames(@Param("companyId") Long companyId);

	Long findOpenDeptId(@Param("companyId") Long companyId, @Param("deptName") String deptName);

	Long findGradeId(@Param("companyId") Long companyId, @Param("gradeName") String gradeName);

	int countLiveEmail(@Param("companyId") Long companyId, @Param("email") String email);

	int insertRequest(JoinRequestDTO dto);
	
	JoinRequestViewDTO getRequestView(@Param("companyCode") String companyCode,@Param("requestId") Long requestId);
	
	int cancelRequest(@Param("requestId") Long requestId,@Param("companyId") Long companyId);
	
	int cancelAccount(@Param("userAccountId") Long userAccountId, @Param("companyId") Long companyId);
	
}