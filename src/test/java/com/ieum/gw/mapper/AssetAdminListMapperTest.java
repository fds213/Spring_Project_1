package com.ieum.gw.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ieum.gw.dto.AssetListDTO;

/** 실제 Mapper XML의 바인딩·조회 범위를 확인한다. Oracle에서 SQL을 실행하지 않는다. */
class AssetAdminListMapperTest {
    @Test
    @DisplayName("자산 목록 SQL은 업체를 바인딩하고 상태 제한 없이 목록 DTO의 필드를 조회한다")
    void bindsCompanyAndSelectsAllStatuses() throws Exception {
        Configuration config = new Configuration();
        try (InputStream in = getClass().getResourceAsStream("/mapper/AssetAllocationMapper.xml")) {
            assertNotNull(in);
            new XMLMapperBuilder(in, config, "mapper/AssetAllocationMapper.xml", config.getSqlFragments()).parse();
        }
        var method = AssetAllocationMapper.class.getMethod("selectList", Long.class);
        Object parameters = new ParamNameResolver(config, method).getNamedParams(new Object[] {7L});
        var statement = config.getMappedStatement("com.ieum.gw.mapper.AssetAllocationMapper.selectList");
        var bound = statement.getBoundSql(parameters);
        String sql = bound.getSql().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        assertEquals(List.class, method.getReturnType());
        assertEquals(AssetListDTO.class, statement.getResultMaps().get(0).getType());
        assertEquals(1, bound.getParameterMappings().size());
        assertEquals("companyId", bound.getParameterMappings().get(0).getProperty());
        assertEquals(7L, config.newMetaObject(parameters).getValue("companyId"));
        assertEquals("select asset_id as id, asset_no as assetno, asset_name as name, serial_no as serial, "
                + "asset_type as kind, status_code as status, branch_id as branchid, version "
                + "from asset where company_id = ? order by asset_no, asset_id", sql);
    }
}
