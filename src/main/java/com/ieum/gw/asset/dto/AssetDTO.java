package com.ieum.gw.asset.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

/** 현재 지급된 자산. 제조 일련번호와 업체 자산번호를 구분한다. */
@Getter
@Setter
public class AssetDTO {
    private Long assetId;
    private String assetNo;
    private String assetName;
    private String assetType;
    private String serialNo;
    private Long branchId;
    private Long itTicketId;
    @JsonSerialize(using = AssetDateTimeSerializer.class)
    private LocalDateTime issuedAt;
}
