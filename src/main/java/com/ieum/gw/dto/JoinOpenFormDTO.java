package com.ieum.gw.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 가입 열기 요청 (화면 JSON) */
@Getter
@Setter
@ToString
public class JoinOpenFormDTO {
    private List<String> depts;  // 열 부서 이름들 (관리자만 보냄, 부서장은 비어 있음)
    private Integer minutes;     // 열어 둘 시간 (10, 30, 60, 180, 1440)
}