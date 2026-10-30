package com.ieum.gw.chat.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ChatMessageDTO {
    private Long chatMessageId;
    private Long companyId;
    private Long chatRoomId;
    private Long senderEmployeeNum;
    private String messageType;
    private String messageText;
    private String created_at;
}
