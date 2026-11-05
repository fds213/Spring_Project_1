package com.ieum.gw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

// 방금 만든 인증 링크. 원래 값은 메일에 넣는 동안만 메모리에 있고 어디에도 저장하지 않는다
@Getter
@AllArgsConstructor
@ToString
public class TrialVerifyIssuedDTO {
    private final TrialVerifyTargetDTO target;
    private final Long emailVerificationId;

    @ToString.Exclude
    private final String token;
}
