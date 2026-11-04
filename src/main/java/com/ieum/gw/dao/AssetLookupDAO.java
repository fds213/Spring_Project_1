package com.ieum.gw.dao;

import java.util.List;
import org.springframework.stereotype.Repository;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.mapper.AssetLookupMapper;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AssetLookupDAO {
    private final AssetLookupMapper mapper;
    public List<AssetDTO> selectSummaries(Long companyId, List<Long> ids) { return mapper.selectSummaries(companyId, ids); }
}
