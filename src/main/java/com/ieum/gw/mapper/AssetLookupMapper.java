package com.ieum.gw.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.dto.AssetDTO;

@Mapper
public interface AssetLookupMapper {
    List<AssetDTO> selectSummaries(@Param("companyId") Long companyId, @Param("ids") List<Long> ids);
}
