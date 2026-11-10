package com.ieum.gw.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.dto.BranchDetailDTO;
import com.ieum.gw.dto.BranchSaveRequestDTO;

@Mapper
public interface BranchMapper {
    List<BranchDTO> selectActiveBranches(@Param("companyId") Long companyId);
    
    List<BranchDetailDTO> selectBranchDetails(@Param("companyId") Long companyId);
    
    /** 지점을 저장하고 등록된 행 수를 반환한다. */
    int insertBranch(@Param("companyId") Long companyId, @Param("branchName") String branchName, @Param("address") String address, @Param("detailAddress") String detailAddress);
    
    /** 지점 정보를 수정하고 변경된 행 수를 반환한다. */
    int updateBranch(@Param("companyId") Long companyId, @Param("branchId") Long branchId, @Param("branchName") String branchName, @Param("address") String address, @Param("detailAddress") String detailAddress);
    
    /** 같은 업체의 지점 행을 잠그고 지점 ID를 반환한다. */
    Long lockBranch(@Param("companyId") Long companyId, @Param("branchId") Long branchId);
    
    /** 같은 업체의 지정된 지점이 사용 중인지 조회한다. */
    String selectBranchUseYn(@Param("companyId") Long companyId, @Param("branchId") Long branchId);
    
    /** 같은 업체·수령 지점에 남은 미지급 장비 티켓 수를 센다. */
    int countPendingEquipmentTickets(@Param("companyId") Long companyId, @Param("branchId") Long branchId);
    
    /** 사용 중인 지점을 중지하고 변경된 행 수를 반환한다. */
    int disableBranch(@Param("companyId") Long companyId, @Param("branchId") Long branchId);
}
