package com.ieum.gw.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 티켓 업무 처리용 DB 행. 화면 응답은 Service에서 확정 규격으로 변환한다. */
@Getter
@Setter
public class TicketWorkDTO {
    private Long id;
    private Long companyId;
    private Long requesterId;
    private Long assigneeId;
    private Long branchId;
    private Long assetId;
    private Long equipmentDocumentId;
    private String type;
    private String status;
    private String title;
    private String body;
    private Long version;
    private LocalDateTime at;
}
