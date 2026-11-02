package com.ieum.gw.notification.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 알림 목록 한 페이지(화면 JS). 게시판 목록과 같은 이름을 쓴다 */
@Getter
@Setter
@ToString
public class NotificationPageDTO {
    private int page;  // 지금 페이지(1부터)
    private int pages; // 전체 페이지 수(알림이 없어도 1)
    private int total; // 전체 알림 수
    private List<NotificationDTO> items;
}
