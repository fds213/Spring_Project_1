package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 마커 추가·수정 때 화면이 보내는 값 (본문 JSON: { title, address, detailAddress, lat, lng, description })
// 업체·지도 번호는 받지 않는다. 서버가 로그인 정보로 정한다 (AGENTS.md)
@Getter
@Setter
@ToString
public class MapMarkerSaveDTO {

    private String title;
    private String address;
    private String detailAddress;
    private Double lat;
    private Double lng;
    private String description;
}
