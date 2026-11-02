package com.ieum.gw.employee.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** USER_ACCOUNT 한 행 */
@Getter
@Setter
@ToString
public class AccountDTO {
    private Long userAccountId;   // user_account_id (INSERT 전에는 null)
    private Long companyId;       // company_id
    private Long employeeId;      // employee_id
    private String email;         // email (소문자)
    @ToString.Exclude
    private String passwordHash;  // password_hash (BCrypt)
    private String roleCode;      // role_code   ROLE_ADMIN ...
    private String statusCode;    // status_code ACTIVE ...
}