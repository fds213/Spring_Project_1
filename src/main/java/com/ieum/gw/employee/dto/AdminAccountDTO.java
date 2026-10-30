package com.ieum.gw.employee.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 체험 신청 때 만든 업체 관리자(EmployeeService.createCompanyAdmin의 결과)
@Getter
@Setter
@ToString
public class AdminAccountDTO {
    private Long employeeId;    // employee_id
    private Long userAccountId; // user_account_id
    private String employeeNo;  // employee_no (신청 완료 화면에 보여 준다)
}
