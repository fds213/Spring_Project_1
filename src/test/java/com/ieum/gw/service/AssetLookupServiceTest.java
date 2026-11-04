package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ieum.gw.dao.AssetLookupDAO;
import com.ieum.gw.dto.AssetDTO;

class AssetLookupServiceTest {
    @Test @DisplayName("자산 표시 ID는 null·중복을 제거하고 같은 업체에서 1000개씩 조회한다")
    void batches() {
        List<Integer> sizes=new ArrayList<>();
        AssetLookupService service=new AssetLookupService(new AssetLookupDAO(null) {
            @Override public List<AssetDTO> selectSummaries(Long companyId,List<Long> ids) {
                assertEquals(7L,companyId); sizes.add(ids.size());
                AssetDTO a=new AssetDTO(); a.setAssetId(ids.get(0)); return List.of(a);
            }
        });
        List<Long> ids=new ArrayList<>(); ids.add(null);
        for(long i=1;i<=1001;i++) { ids.add(i); ids.add(i); }
        assertEquals(2,service.getSummaries(7L,ids).size());
        assertEquals(List.of(1000,1),sizes);
        assertTrue(service.getSummaries(7L,List.of()).isEmpty());
        assertTrue(service.getSummaries(7L,null).isEmpty());
        assertEquals(2,sizes.size());
    }
}
