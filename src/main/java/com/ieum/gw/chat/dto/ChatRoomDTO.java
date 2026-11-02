package com.ieum.gw.chat.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ChatRoomDTO {
    private Long chatRoomId;
    private Long companyId;
    private String roomType;
    private String roomName;
    private String displayName;
}
