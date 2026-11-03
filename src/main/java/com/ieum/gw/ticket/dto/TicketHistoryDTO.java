package com.ieum.gw.ticket.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

/** 이미 기록된 상태·담당자 변경을 조회한다. 상태를 새로 정의하거나 변경하지 않는다. */
@Getter
@Setter
public class TicketHistoryDTO {
    private Long ticketHistoryId;
    private Long actorAccountId;
    private String beforeStatus;
    private String afterStatus;
    private Long beforeAssigneeId;
    private Long afterAssigneeId;
    private String reason;
    @JsonSerialize(using = TicketDateTimeSerializer.class)
    private LocalDateTime createdAt;
}
