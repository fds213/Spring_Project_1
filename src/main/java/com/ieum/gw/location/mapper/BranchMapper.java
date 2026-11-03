package com.ieum.gw.location.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.location.dto.BranchDTO;

@Mapper
public interface BranchMapper {
    List<BranchDTO> selectActiveBranches(@Param("companyId") Long companyId);
}
