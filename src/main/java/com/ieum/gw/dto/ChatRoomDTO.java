package com.ieum.gw.dto;

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
    private int unreadCount;
    private Long createdByEmployeeId;
    private Long directEmployeeLow;
    private Long directEmployeeHigh;
    /** 마지막 메시지 종류(MSG_TEXT·MSG_IMAGE), 메시지가 없으면 null */
    private String lastMessageType;
    /** 마지막 메시지 글의 앞 100자 (방 목록 미리보기) */
    private String lastMessageText;
    private java.util.Date lastMessageAt;
    /** 마지막 메시지를 내가 보냈는지 */
    private boolean lastMessageMine;
}
