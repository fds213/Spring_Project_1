package com.ieum.gw.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 가입이 열린 부서 1개 (DEPARTMENT.JOIN_OPEN_UNTIL이 지금보다 뒤) */
@Getter
@Setter
@ToString
public class JoinOpeningDTO {
    private String departmentName;       // department_name  부서 이름
    private LocalDateTime joinOpenUntil; // join_open_until  이 시각까지 열림
    private Long joinOpenedBy;           // join_opened_by   연 사람의 계정 번호
    private String openedByName;         // 연 사람 이름 (사원 표에서 찾아옴)
}