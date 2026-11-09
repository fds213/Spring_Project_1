package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 직원이 보내는 장애 문의 입력. 업체·작성자는 받지 않는다(로그인 정보에서만 꺼낸다) */
@Getter
@Setter
public class SupportInquiryFormDTO {
    private String title;
    private String body;
}
