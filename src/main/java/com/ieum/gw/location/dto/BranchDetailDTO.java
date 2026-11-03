package com.ieum.gw.location.dto;

import lombok.Getter;
import lombok.Setter;

/** 기존 자산·티켓의 지점 표시용이다. 새 선택 목록의 BranchDTO와 구분한다. */
@Getter
@Setter
public class BranchDetailDTO {
    private Long branchId;
    private String branchName;
    private String address;
    private String detailAddress;
    private String useYn;
}
