package com.ieum.gw.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;

@Getter
@Setter
@ToString
public class ChatMessageDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chatMessageId;
    private String senderName;
    private Long companyId;
    private Long chatRoomId;
    private Long senderEmployeeId;
    private String messageType;
    private String messageText;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long attachmentId;
    private String clientMessageKey;
    private Timestamp createdAt;
}
