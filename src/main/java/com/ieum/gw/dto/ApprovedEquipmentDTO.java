package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 결재 완료된 장비 신청의 지급 정보 (티켓의 지급 대기 목록·관리자 상세가 쓴다).
// 문서 내용·결재 첨부는 넘기지 않고 지급에 필요한 값만 담는다
@Getter
@Setter
@ToString
public class ApprovedEquipmentDTO {
    private String assetType;  // AT_LAPTOP 등 장비 종류 코드
    private Integer quantity;
    private String purpose;
}
