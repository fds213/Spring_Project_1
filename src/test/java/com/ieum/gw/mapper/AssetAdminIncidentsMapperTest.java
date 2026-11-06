package com.ieum.gw.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.code.TicketType;
import com.ieum.gw.dto.AssetAdminIncidentDTO;

/** XML과 Mapper의 연결 및 조회 조건을 정적으로 검증한다. Oracle은 사용하지 않는다. */
class AssetAdminIncidentsMapperTest {
    @Test
    @DisplayName("장애 SQL은 업체별 이름 JOIN과 자산·종류 조건을 적용하고 상태·재직 필터 없이 최신순으로 조회한다")
    void verifiesTenantScopeAndHistoryConditions() throws Exception {
        Configuration config = new Configuration();
        try (InputStream in = getClass().getResourceAsStream("/mapper/AssetAllocationMapper.xml")) {
            assertNotNull(in);
            new XMLMapperBuilder(in, config, "mapper/AssetAllocationMapper.xml", config.getSqlFragments()).parse();
        }
        var method = AssetAllocationMapper.class.getMethod("selectIncidents", Long.class, Long.class, String.class);
        Object parameters = new ParamNameResolver(config, method)
                .getNamedParams(new Object[] {7L, 13L, TicketType.TK_INCIDENT.name()});
        var statement = config.getMappedStatement("com.ieum.gw.mapper.AssetAllocationMapper.selectIncidents");
        var bound = statement.getBoundSql(parameters);
        String sql = bound.getSql().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        assertEquals(AssetAdminIncidentDTO.class, statement.getResultMaps().get(0).getType());
        assertEquals(List.of("companyId", "assetId", "ticketType"), bound.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty()).collect(Collectors.toList()));
        assertEquals(7L, config.newMetaObject(parameters).getValue("companyId"));
        assertEquals(13L, config.newMetaObject(parameters).getValue("assetId"));
        assertEquals(TicketType.TK_INCIDENT.name(), config.newMetaObject(parameters).getValue("ticketType"));
        assertTrue(sql.contains("left join employee rt on rt.company_id = it.company_id and rt.employee_id = it.requester_employee_id"));
        assertTrue(sql.contains("left join employee asn on asn.company_id = it.company_id and asn.employee_id = it.assignee_employee_id"));
        assertTrue(sql.contains("where it.company_id = ? and it.asset_id = ? and it.ticket_type = ?"));
        assertTrue(sql.endsWith("order by it.created_at desc, it.it_ticket_id desc"));
        assertFalse(sql.contains("employment_status"));
        String conditions = sql.substring(sql.indexOf(" from "));
        assertFalse(conditions.contains("status_code"));
        assertFalse(conditions.contains("is not null"));
    }
}
