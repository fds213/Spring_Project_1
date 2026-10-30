package com.ieum.gw.location.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 지점(BRANCH 한 줄 중 고를 때 쓰는 칸)
@Getter
@Setter
@ToString
public class BranchDTO {
    private Long branchId;     // branch_id
    private String branchName; // branch_name
}
