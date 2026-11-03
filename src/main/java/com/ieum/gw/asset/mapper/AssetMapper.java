package com.ieum.gw.asset.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.asset.dto.AssetDTO;
import com.ieum.gw.asset.dto.AssetAssignmentDTO;

@Mapper
public interface AssetMapper {
    List<AssetDTO> selectMyAssets(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);
    int countMyAssets(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);
    List<AssetAssignmentDTO> selectMyAssignments(@Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId, @Param("assetId") Long assetId);
}
