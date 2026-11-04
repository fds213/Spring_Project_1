package com.ieum.gw.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.dao.AssetLookupDAO;
import lombok.RequiredArgsConstructor;

/** 티켓 등 이미 접근 권한을 검사한 업무의 자산 이름 표시용. 현재 지급 여부와 무관하게 과거 연결도 읽는다. */
@Service
@RequiredArgsConstructor
public class AssetLookupService {
    private final AssetLookupDAO dao;

    @Transactional(readOnly = true)
    public Map<Long, AssetDTO> getSummaries(Long companyId, List<Long> assetIds) {
        if (companyId == null || companyId <= 0) { throw new IllegalArgumentException("업체 정보가 필요합니다."); }
        Map<Long, AssetDTO> result = new LinkedHashMap<>();
        if (assetIds == null) { return result; }
        List<Long> ids = assetIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        for (int i = 0; i < ids.size(); i += 1000) {
            for (AssetDTO row : dao.selectSummaries(companyId, new ArrayList<>(ids.subList(i, Math.min(i + 1000, ids.size()))))) {
                result.put(row.getAssetId(), row);
            }
        }
        return result;
    }
}
