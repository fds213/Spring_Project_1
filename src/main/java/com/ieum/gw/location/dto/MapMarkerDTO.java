package com.ieum.gw.location.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 공통 지도의 마커 하나 (MAP_MARKER 한 줄). map.js가 읽는 모양: { id, title, address, detailAddress, lat, lng, description }
@Getter
@Setter
@ToString
public class MapMarkerDTO {

    private Long id;               // 마커 번호 (MAP_MARKER_ID)
    private String title;          // 제목 (예: 본사, 판교 창고)
    private String address;        // 주소 (검색하거나 지도를 눌러 고른 주소)
    private String detailAddress;  // 상세주소 (예: 3층) - 선택
    private Double lat;            // 위도 (LATITUDE)
    private Double lng;            // 경도 (LONGITUDE)
    private String description;    // 설명 - 선택
    private String updatedAt;      // 마지막으로 고친 시각 (예: 2026-11-02T16:30:00)
}
