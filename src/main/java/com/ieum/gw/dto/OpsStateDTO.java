package com.ieum.gw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/** 업체 이용 상태. 화면은 status로 색을 고르고 label을 그대로 보인다 */
@Getter
@ToString
@AllArgsConstructor
public class OpsStateDTO {
    private final String status; // TRIAL / PAID / EXPIRED
    private final String label;  // 체험 중, 유료, 체험 만료, 유료 만료
}
