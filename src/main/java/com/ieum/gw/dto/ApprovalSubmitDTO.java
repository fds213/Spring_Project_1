package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class ApprovalSubmitDTO {
    private String documentType;
    private Long draftId; // 임시저장 번호
    private List<String> line;
    private ApprovalFieldsDTO fields;
}
