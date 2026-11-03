package com.ieum.gw.ticket.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

/** 티켓 댓글 조회. 사원 이름은 사원 모듈의 조회 계약을 통해 연결한다. */
@Getter
@Setter
public class TicketCommentDTO {
    private Long ticketCommentId;
    private Long authorEmployeeId;
    private String content;
    @JsonSerialize(using = TicketDateTimeSerializer.class)
    private LocalDateTime createdAt;
}
