package com.ieum.gw.dao;

import java.util.List;
import org.springframework.stereotype.Repository;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.dto.AssetAssignmentDTO;
import com.ieum.gw.mapper.AssetMapper;
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
