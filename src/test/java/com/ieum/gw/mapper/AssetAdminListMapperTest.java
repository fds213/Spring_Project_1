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
    @DisplayName("자산 목록은 업체별 미회수 지급 건과 직원만 LEFT JOIN하고 전체 상태·정렬을 유지한다")
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
        assertTrue(sql.contains("a.status_code as status"));
        assertTrue(sql.contains("e.employee_name as employeename"));
        assertTrue(sql.contains("aas.issued_at as issuedat"));
        assertTrue(sql.contains("left join asset_assignment aas on aas.company_id = a.company_id "
                + "and aas.asset_id = a.asset_id and aas.returned_at is null"));
        assertTrue(sql.contains("left join employee e on e.company_id = aas.company_id "
                + "and e.employee_id = aas.employee_id"));
        String where = sql.substring(sql.indexOf(" where "));
        assertTrue(where.startsWith(" where a.company_id = ?"));
        assertTrue(where.endsWith("order by a.asset_no, a.asset_id"));
        assertFalse(where.contains("returned_at"), "미지급 자산도 유지하도록 지급 필터는 JOIN에 둔다.");
        assertFalse(where.contains("status_code"));
        assertFalse(sql.contains("employment_status"));
    }
}
