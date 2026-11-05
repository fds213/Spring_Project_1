package com.ieum.gw.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.AssetAdminAssignmentDTO;
import com.ieum.gw.dto.AssetAllocationDTO;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.dto.AssetListDTO;

@Mapper
public interface AssetAllocationMapper {
	/** 같은 업체 자산의 지급·회수 이력과 관련 직원 이름을 조회한다. */
	List<AssetAdminAssignmentDTO> selectAssignments(@Param("companyId") Long companyId, 
													@Param("assetId") Long assetId);
	
	/** 같은 업체의 전체 상태 자산을 조회한다. */
	List<AssetListDTO> selectList(@Param("companyId") Long companyId);
	
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
