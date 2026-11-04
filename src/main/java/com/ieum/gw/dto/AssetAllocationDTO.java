package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 자산 행 잠금 및 지급 이력 저장용. 요청자가 아닌 Service가 업체·처리 계정을 채운다. */
@Getter
@Setter
public class AssetAllocationDTO {
    private Long companyId;
    private Long assetId;
    private Long version;
    private String assetName;
    private String assetNo;
    private String serialNo;
    private String assetType;
    private String status;
    private Long branchId;
    private Long assignmentId;
    private Long employeeId;
    private Long ticketId;
    private Long actorAccountId;
    private Long targetBranchId;
    private String targetStatus;
    private String note;
}
