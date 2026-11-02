package com.ieum.gw.chat.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;

@Getter
@Setter
@ToString
public class ChatMessageDTO {
    private Long chatMessageId;
    private Long companyId;
    private Long chatRoomId;
    private Long senderEmployeeId;
    private String messageType;
    private String messageText;
    private String clientMessageKey;
    private Timestamp createdAt;
}
