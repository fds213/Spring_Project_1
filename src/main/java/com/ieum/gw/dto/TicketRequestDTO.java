package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 인증 업체·작성자는 받지 않는다. 접수와 상태 변경의 입력값만 담는다. */
@Getter
@Setter
public class TicketRequestDTO {
    private String type;
    private String title;
    private String body;
    private Long branchId;
    private Long assetId;
    private Long assigneeId;
    private Long version;
    private String result;
    private String reason;
    
    private Long attachmentId;
}
