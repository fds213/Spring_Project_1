package com.ieum.gw.employee.mapper;

import com.ieum.gw.employee.dto.EmpSummaryDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EmployeeMapper {
    List<EmpSummaryDTO> listActiveEmp(Long companyId, Long excludeEmpId);
}
