package com.ieum.gw.attendance.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.attendance.dto.ApprovalStepDTO;
import com.ieum.gw.attendance.dto.EmployeeCareerDTO;
import com.ieum.gw.attendance.dto.LeaveCycleDTO;
import com.ieum.gw.attendance.dto.LeaveRequestDTO;
import com.ieum.gw.attendance.dto.LeaveUsageDTO;

@Mapper
public interface LeaveMapper {

    EmployeeCareerDTO selectEmployeeCareer(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    LeaveCycleDTO selectCurrentCycle(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("today") String today);

    LeaveUsageDTO selectUsage(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                              @Param("startsOn") String startsOn, @Param("endsOn") String endsOn);

    List<LeaveRequestDTO> selectRequests(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                                         @Param("startsOn") String startsOn, @Param("endsOn") String endsOn);

    List<ApprovalStepDTO> selectSteps(@Param("companyId") Long companyId, @Param("documentIds") List<Long> documentIds);
}
