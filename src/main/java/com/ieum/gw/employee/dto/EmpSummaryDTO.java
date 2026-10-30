package com.ieum.gw.employee.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class EmpSummaryDTO {
    private Long companyId; // company_id
    private Long employeeId; // employee_id
    private String employeeName; // employee_name
    private Long departmentId; // department_id
    private Long jobGradeId; // job_grade_id
}
