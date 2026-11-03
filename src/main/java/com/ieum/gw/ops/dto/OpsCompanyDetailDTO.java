package com.ieum.gw.ops.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 업체 관리에서 펼친 한 업체 (ops/company.js). 업체의 업무 자료는 담지 않고 운영 기록만 담는다(OPS-01·02) */
@Getter
@Setter
public class OpsCompanyDetailDTO extends OpsCompanyDTO {
    private Long no;                      // 신청번호(COMPANY_ID). 업체당 신청이 1건이라 따로 저장하지 않는다
    private String email;
    private boolean emailVerified;
    private List<OpsEmployeeDTO> employees;
    private List<OpsPaymentDTO> payments; // 최신순
    private List<Object> inquiries;       // 회원 접수(OPS-04)는 이번 구현 범위에서 빠져 늘 빈 목록이다
}
