package com.ieum.gw.chat.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ChatMessageDTO {
    private Long msgId;
    private Long roomId;
    private String sender;
    private String msg_type;
    private String content;
}
