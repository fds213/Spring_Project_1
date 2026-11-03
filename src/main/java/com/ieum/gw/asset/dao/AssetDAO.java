package com.ieum.gw.asset.dao;

import java.util.List;
import org.springframework.stereotype.Repository;
import com.ieum.gw.asset.dto.AssetDTO;
import com.ieum.gw.asset.dto.AssetAssignmentDTO;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AssetDAO {
    private final AssetMapper mapper;

    public List<AssetDTO> selectMyAssets(Long companyId, Long employeeId) {
        return mapper.selectMyAssets(companyId, employeeId);
    }

    public int countMyAssets(Long companyId, Long employeeId) {
        return mapper.countMyAssets(companyId, employeeId);
    }

    public List<AssetAssignmentDTO> selectMyAssignments(Long companyId, Long employeeId, Long assetId) {
        return mapper.selectMyAssignments(companyId, employeeId, assetId);
    }
}
