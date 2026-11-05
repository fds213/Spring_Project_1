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
import com.ieum.gw.dto.AssetAdminAssignmentDTO;

/** 실제 Mapper XML의 업체 분리와 이력 보존 조건을 확인한다. Oracle에서 SQL을 실행하지 않는다. */
class AssetAdminAssignmentsMapperTest {
    @Test
    @DisplayName("관리자 이력 SQL은 업체·자산을 바인딩하고 모든 이름 연결을 같은 업체 LEFT JOIN으로 조회한다")
    void scopesAllJoinsAndPreservesPastAssignments() throws Exception {
        Configuration config = new Configuration();
        try (InputStream in = getClass().getResourceAsStream("/mapper/AssetAllocationMapper.xml")) {
            assertNotNull(in);
            new XMLMapperBuilder(in, config, "mapper/AssetAllocationMapper.xml", config.getSqlFragments()).parse();
        }
        var method = AssetAllocationMapper.class.getMethod("selectAssignments", Long.class, Long.class);
        Object parameters = new ParamNameResolver(config, method).getNamedParams(new Object[] {7L, 13L});
        var statement = config.getMappedStatement("com.ieum.gw.mapper.AssetAllocationMapper.selectAssignments");
        var bound = statement.getBoundSql(parameters);
        String sql = bound.getSql().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        assertEquals(List.class, method.getReturnType());
        assertEquals(AssetAdminAssignmentDTO.class, statement.getResultMaps().get(0).getType());
        assertEquals(List.of("companyId", "assetId"), bound.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty()).collect(Collectors.toList()));
        assertEquals(7L, config.newMetaObject(parameters).getValue("companyId"));
        assertEquals(13L, config.newMetaObject(parameters).getValue("assetId"));
        for (String join : List.of(
                "left join employee receiver on receiver.company_id = aa.company_id and receiver.employee_id = aa.employee_id",
                "left join user_account issue_account on issue_account.company_id = aa.company_id and issue_account.user_account_id = aa.issued_by",
                "left join employee issuer on issuer.company_id = issue_account.company_id and issuer.employee_id = issue_account.employee_id",
                "left join user_account return_account on return_account.company_id = aa.company_id and return_account.user_account_id = aa.returned_by",
                "left join employee returner on returner.company_id = return_account.company_id and returner.employee_id = return_account.employee_id")) {
            assertTrue(sql.contains(join), join);
        }
        assertTrue(sql.contains("receiver.employee_name as employeename"));
        assertTrue(sql.contains("issuer.employee_name as issuedbyname"));
        assertTrue(sql.contains("returner.employee_name as returnedbyname"));
        assertTrue(sql.contains("where aa.company_id = ? and aa.asset_id = ?"));
        assertTrue(sql.endsWith("order by aa.issued_at desc, aa.asset_assignment_id desc"));
        assertFalse(sql.contains("employment_status"));
        assertFalse(sql.contains("status_code"));
        assertFalse(sql.contains("returned_at is null"));
        assertFalse(sql.contains("returned_at is not null"));
    }
}
