package com.ieum.gw.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.dto.AssetAllocationDTO;
import com.ieum.gw.dto.AssetDTO;

@Mapper
public interface AssetAllocationMapper {
    AssetAllocationDTO detail(@Param("companyId") Long companyId,@Param("assetId") Long assetId);
    AssetAllocationDTO lock(@Param("companyId") Long companyId,@Param("assetId") Long assetId);
    List<Long> openAssignments(@Param("companyId") Long companyId,@Param("assetId") Long assetId);
    int issue(AssetAllocationDTO row);
    int insertAssignment(AssetAllocationDTO row);
    int insertIssueHistory(AssetAllocationDTO row);
    int returnAssignment(AssetAllocationDTO row);
    int release(AssetAllocationDTO row);
    int insertReturnHistory(AssetAllocationDTO row);
    List<AssetDTO> stock(@Param("companyId") Long companyId,@Param("assetType") String assetType,@Param("status") String status);
}
