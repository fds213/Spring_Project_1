package com.ieum.gw.location.service;

import com.ieum.gw.location.dto.BranchDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 지점이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 * 지도도 location 모듈이지만 담당이 달라 지점은 이 클래스로 나눈다.
 */
@Service
public class BranchService {

    /**
     * 사용 중(USE_YN = 'Y')인 지점 목록(이름순). 장비 신청의 수령 지점, 티켓의 접수 지점을 고를 때 쓴다.
     */
    public List<BranchDTO> getBranches(Long companyId) {
        // TODO(정상욱): 구현. 구현 전에는 빈 목록을 돌려준다.
        return List.of();
    }
}
