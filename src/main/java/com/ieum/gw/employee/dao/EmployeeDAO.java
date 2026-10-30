package com.ieum.gw.employee.dao;

import com.ieum.gw.employee.dto.EmpSummaryDTO;
import com.ieum.gw.employee.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class EmployeeDAO {
    
    private final EmployeeMapper mapper;

    public List<EmpSummaryDTO> listActiveEmp(Long companyId, Long excludeEmpId) {
        return mapper.listActiveEmp(companyId, excludeEmpId);
    }
}
