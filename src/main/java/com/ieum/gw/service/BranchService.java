package com.ieum.gw.service;

import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.dto.BranchDetailDTO;
import com.ieum.gw.dao.BranchDAO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 지점이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 * 지도도 location 모듈이지만 담당이 달라 지점은 이 클래스로 나눈다.
 */
@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchDAO dao;

    /**
     * 사용 중(USE_YN = 'Y')인 지점 목록(이름순). 장비 신청의 수령 지점, 티켓의 접수 지점을 고를 때 쓴다.
     */
    @Transactional(readOnly = true)
    public List<BranchDTO> getBranches(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new IllegalArgumentException("업체 정보가 필요합니다.");
        }
        return dao.selectActiveBranches(companyId);
    }

    /**
     * 기존 자산·티켓의 이름과 주소를 표시할 때 호출한다. 사용 중지한 지점도 포함한다.
     * 입력 업체 ID는 호출한 모듈이 인증 정보에서 가져오며, SQL에서도 같은 업체만 읽는다.
     * 새 접수·장비 신청 선택 목록에는 기존 getBranches를 사용한다.
     */
    @Transactional(readOnly = true)
    public List<BranchDetailDTO> getBranchDetails(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new IllegalArgumentException("업체 정보가 필요합니다.");
        }
        return dao.selectBranchDetails(companyId);
    }
}
