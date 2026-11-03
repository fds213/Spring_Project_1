package com.ieum.gw.location.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.location.dto.BranchDTO;
import com.ieum.gw.location.dto.BranchDetailDTO;

@Mapper
public interface BranchMapper {
    List<BranchDTO> selectActiveBranches(@Param("companyId") Long companyId);
    List<BranchDetailDTO> selectBranchDetails(@Param("companyId") Long companyId);
}
