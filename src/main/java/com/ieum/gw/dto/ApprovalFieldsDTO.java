package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ApprovalFieldsDTO {
    // 휴가 신청서
    private String leaveType;
    private String fromDate;
    private String toDate;
    private String reason;

    // 장비 지급 신청서
    private String assetType;  // AT_LAPTOP 등 장비 종류 코드
    private Integer quantity;  // 화면이 숫자로 보낸다
    private String branchId;   // 화면 선택칸 값이라 문자열로 온다
    private String purpose;
}
