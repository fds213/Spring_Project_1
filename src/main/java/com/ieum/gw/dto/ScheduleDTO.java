package com.ieum.gw.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 달력의 일정 하나 (CALENDAR_EVENT 한 줄). calendar.js가 읽는 모양: { id, scope, date, title, memo, canEdit, canDelete }
@Getter
@Setter
@ToString
public class ScheduleDTO {

    private Long id;            // 일정 번호 (CALENDAR_EVENT_ID)
    private String scope;       // 화면용 구분: PERSONAL(개인)·COMPANY(전사)·DEPT(부서). DB의 SCOPE_xxx를 SQL에서 바꿔 준다
    private String date;        // 날짜 (예: 2026-11-02)
    private String title;       // 제목
    private String memo;        // 메모 (CALENDAR_EVENT.CONTENT)
    private boolean canEdit;    // 수정 버튼을 보일지 (삭제와 같은 사람) — Service가 채운다
    private boolean canDelete;  // 삭제 버튼을 보일지 (개인·부서: 쓴 본인, 전사: 쓴 본인·업체 관리자) — Service가 채운다

    @JsonIgnore
    private Long ownerId;       // 쓴 사람 사원 ID (화면에는 보내지 않는다. 수정·삭제 권한 비교용)
    @JsonIgnore
    private String eventType;   // 일정 종류 (EV_LEAVE면 결재에서 온 휴가 일정이라 고치거나 지울 수 없다)
}
