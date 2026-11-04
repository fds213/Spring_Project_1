package com.ieum.gw.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

/** 본인에게 지급된 건만 보여 준다. 이전·다음 수령인의 정보는 담지 않는다. */
@Getter
@Setter
public class AssetAssignmentDTO {
    private Long assetAssignmentId;
    private Long assetId;
    private Long itTicketId;
    @JsonSerialize(using = AssetDateTimeSerializer.class)
    private LocalDateTime issuedAt;
    @JsonSerialize(using = AssetDateTimeSerializer.class)
    private LocalDateTime returnedAt;
}
