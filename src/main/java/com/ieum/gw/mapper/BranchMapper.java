package com.ieum.gw.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.dto.BranchDetailDTO;

@Mapper
public interface BranchMapper {
    List<BranchDTO> selectActiveBranches(@Param("companyId") Long companyId);
    List<BranchDetailDTO> selectBranchDetails(@Param("companyId") Long companyId);
}
