package com.ieum.gw.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.JoinCompanyDTO;
import com.ieum.gw.dto.JoinOpeningDTO;
import com.ieum.gw.dto.JoinRequestDTO;
import com.ieum.gw.dto.JoinRequestViewDTO;

@Mapper
public interface JoinMapper {

	String getCompanyCode(@Param("companyId") Long companyId);

	String getHeadDeptName(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

	List<String> listDeptNames(@Param("companyId") Long companyId);

	List<JoinOpeningDTO> listOpenings(@Param("companyId") Long companyId);

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

	int countRetiredEmail(@Param("companyId") Long companyId, @Param("email") String email);
	
	int insertRequest(JoinRequestDTO dto);

	JoinRequestViewDTO getRequestView(@Param("companyCode") String companyCode, @Param("keyHash") String keyHash);

	int cancelRequest(@Param("requestId") Long requestId, @Param("companyId") Long companyId);

	int cancelAccount(@Param("userAccountId") Long userAccountId, @Param("companyId") Long companyId);

	List<JoinRequestViewDTO> listWaitingRequests(@Param("companyId") Long companyId);

	JoinRequestViewDTO lockRequest(@Param("companyId") Long companyId, @Param("requestId") Long requestId);

	int activateAccount(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId,
			@Param("employeeId") Long employeeId);

	int approveRequest(@Param("companyId") Long companyId, @Param("requestId") Long requestId,
			@Param("reviewerId") Long reviewerId);
	
	int rejectRequest(@Param("companyId") Long companyId, @Param("requestId") Long requestId,
			@Param("reviewerId") Long reviewerId);

	int rejectAccount(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId);
}