package com.ieum.gw.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** EMPLOYEE 한 행 */
@Getter
@Setter
@ToString
public class EmployeeDTO {
    private Long employeeId;          // employee_id (INSERT 전에는 null)
    private Long companyId;           // company_id
    private String employeeNo;        // employee_no  "20260001"
    private String employeeName;      // employee_name
    private Long departmentId;        // department_id (관리자는 처음엔 없음)
    private Long jobGradeId;          // job_grade_id
    private LocalDate hireDate;       // hire_date (날짜만)
    private String employmentStatus;  // employment_status  ST_SERVING
}