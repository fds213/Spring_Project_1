package com.ieum.gw.dao;

import java.util.List;
import org.springframework.stereotype.Repository;
import com.ieum.gw.dto.AssetAllocationDTO;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.mapper.AssetAllocationMapper;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AssetAllocationDAO {
    private final AssetAllocationMapper mapper;
    public AssetAllocationDTO detail(Long companyId, Long assetId) { return mapper.detail(companyId, assetId); }
    public AssetAllocationDTO lock(Long companyId,Long assetId) { return mapper.lock(companyId,assetId); }
    public List<Long> openAssignments(Long companyId,Long assetId) { return mapper.openAssignments(companyId,assetId); }
    public int issue(AssetAllocationDTO row) { return mapper.issue(row); }
    public int insertAssignment(AssetAllocationDTO row) { return mapper.insertAssignment(row); }
    public int insertIssueHistory(AssetAllocationDTO row) { return mapper.insertIssueHistory(row); }
    public int returnAssignment(AssetAllocationDTO row) { return mapper.returnAssignment(row); }
    public int release(AssetAllocationDTO row) { return mapper.release(row); }
    public int insertReturnHistory(AssetAllocationDTO row) { return mapper.insertReturnHistory(row); }
    public List<AssetDTO> stock(Long companyId,String assetType,String status) { return mapper.stock(companyId,assetType,status); }
}
