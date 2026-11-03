package com.ieum.gw.location.dao;

import java.util.List;
import org.springframework.stereotype.Repository;
import com.ieum.gw.location.dto.BranchDTO;
import com.ieum.gw.location.dto.BranchDetailDTO;
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
}
