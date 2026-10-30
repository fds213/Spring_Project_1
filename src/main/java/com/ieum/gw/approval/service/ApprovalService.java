package com.ieum.gw.approval.service;

import org.springframework.stereotype.Service;

/**
 * 결재가 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 */
@Service
public class ApprovalService {

    /**
     * 내가 지금 처리할 차례인 결재 문서 수(헤더 배지 'approval'). 결재함 '내 순번 대기' 목록과 같은 조건으로 센다.
     */
    public int countWaitingForMe(Long companyId, Long employeeId) {
        // TODO(서장호): 구현. 구현 전에는 0을 돌려준다.
        return 0;
    }
}
