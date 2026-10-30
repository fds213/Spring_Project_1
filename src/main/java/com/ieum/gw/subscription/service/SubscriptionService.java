package com.ieum.gw.subscription.service;

import com.ieum.gw.subscription.dto.CompanyUsageDTO;
import org.springframework.stereotype.Service;

/**
 * 구독이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 */
@Service
public class SubscriptionService {

    /**
     * 업체 이용 상태. 결제·만료로 로그인 중에도 바뀌므로 세션에 담지 말고 요청마다 한 번 읽어
     * 헤더의 companyBilling(layout.tag)·만료 읽기 전용 검사·남은 일수 팝업에 같이 쓴다(AGENTS.md 72·124줄).
     */
    public CompanyUsageDTO getCompanyUsage(Long companyId) {
        // TODO(최준원): 구현. 구현 전에는 예외를 던진다.
        throw new UnsupportedOperationException("구현 전: 최준원");
    }

    /**
     * 사용 중(ACTIVE) 계정을 둘 수 있는 최대 수(관리자 포함). 체험은 10, 유료는 구매 인원(요구분석서 3장 단순화 ②).
     * 직원 가입 승인 때 부르는 쪽이 업체 행을 잠근 뒤 사용 중 계정 수와 비교한다(AGENTS.md 181줄).
     */
    public int getSeatLimit(Long companyId) {
        // TODO(최준원): 구현. 구현 전에는 예외를 던진다.
        throw new UnsupportedOperationException("구현 전: 최준원");
    }
}
