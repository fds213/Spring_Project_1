package com.ieum.gw.employee.service;

import com.ieum.gw.employee.dao.EmployeeDAO;
import com.ieum.gw.employee.dto.EmpSummaryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeDAO dao;

    // 현재 활성화 상태인 직원 목록 ( excludeEmpId가 NULL이 아니면 해당 사번 제외한 나머지 반환)
    public List<EmpSummaryDTO> listActiveEmp(Long companyId, Long excludeEmpId) {
        return dao.listActiveEmp(companyId, excludeEmpId);
    }
}
