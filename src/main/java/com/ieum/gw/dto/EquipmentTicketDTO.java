package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 중복 생성 요청이 기존 지급 티켓과 일치하는지 확인할 때 쓴다. */
@Getter
@Setter
public class EquipmentTicketDTO {
    private Long itTicketId;
    private Long requesterEmployeeId;
    private Long branchId;
    private String content;
}
