package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 장비 신청 상세 저장용 (EQUIPMENT_REQUEST)
@Getter
@Setter
@ToString
public class ApprovalEquipmentDTO {
    private Long approvalDocumentId;
    private Long companyId;
    private Long branchId;
    private String assetType;
    private Integer quantity;
    private String purpose;
}
