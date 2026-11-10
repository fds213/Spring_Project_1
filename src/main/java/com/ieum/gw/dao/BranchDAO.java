package com.ieum.gw.dao;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.dto.BranchDetailDTO;
import com.ieum.gw.mapper.BranchMapper;
import lombok.RequiredArgsConstructor;

/** 지점 SQL 호출을 Service와 분리한다. */
@Repository
@RequiredArgsConstructor
public class BranchDAO {
    private final BranchMapper mapper;

    public List<BranchDTO> selectActiveBranches(Long companyId) {
        return mapper.selectActiveBranches(companyId);
    }

    public List<BranchDetailDTO> selectBranchDetails(Long companyId) {
        return mapper.selectBranchDetails(companyId);
    }
    
    /** 지점 등록을 Mapper에 위임한다. */
    public int insertBranch(Long companyId, String branchName, String address, String detailAddress) {
    	return mapper.insertBranch(companyId, branchName, address, detailAddress);
    }
    
    /** 지점 정보 수정을 Mapper에 위임한다. */
    public int updateBranch(Long companyId, Long branchId, String branchName, String address, String detailAddress) {
    	return mapper.updateBranch(companyId, branchId, branchName, address, detailAddress);
    }
    
    /** 지점 행을 잠그고 지점이 없으면 null을 반환한다. */
    public Long lockBranch(Long companyId, Long branchId) {
        return mapper.lockBranch(companyId, branchId);
    }

    /** 지정된 지점의 현재 사용 상태를 조회한다. */
    public String selectBranchUseYn(Long companyId, Long branchId) {
        return mapper.selectBranchUseYn(companyId, branchId);
    }

    /** 지정된 수령 지점의 미지급 장비 티켓 수를 조회한다. */
    public int countPendingEquipmentTickets(Long companyId, Long branchId) {
        return mapper.countPendingEquipmentTickets(companyId, branchId);
    }

    /** 지정된 지점을 사용 중지하고 변경된 행 수를 반환한다. */
    public int disableBranch(Long companyId, Long branchId) {
        return mapper.disableBranch(companyId, branchId);
    }
}
