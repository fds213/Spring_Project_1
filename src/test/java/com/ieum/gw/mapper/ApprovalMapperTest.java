package com.ieum.gw.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovalMapperTest {
    private Configuration configuration() throws Exception {
        Configuration config = new Configuration();
        try (InputStream in = getClass().getResourceAsStream("/mapper/ApprovalMapper.xml")) {
            assertNotNull(in);
            new XMLMapperBuilder(in, config, "mapper/ApprovalMapper.xml", config.getSqlFragments()).parse();
        }
        return config;
    }

    @Test
    @DisplayName("결재 조회 SQL은 enum의 DB 코드값을 문자열 파라미터로 바인딩한다")
    void bindsEnumCodes() throws Exception {
        Configuration config = configuration();

        Map<String, String> pending = Map.of("docLeave", "DOC_LEAVE", "docInProgress", "IN_PROGRESS");
        Map<String, String> turn = Map.of("docInProgress", "IN_PROGRESS",
                "stepWaiting", "WAITING", "stepApproved", "APPROVED");
        Map<String, String> box = Map.of("docDraft", "DRAFT", "docInProgress", "IN_PROGRESS",
                "stepWaiting", "WAITING", "stepApproved", "APPROVED");
        Map<String, Map<String, String>> queries = Map.of(
                "sumPendingLeaveDays", pending,
                "selectPendingLeaveDays", pending,
                "selectApprovedEquipment", Map.of("docEquipment", "DOC_EQUIPMENT", "docApproved", "APPROVED"),
                "selectBoxDocuments", box,
                "selectBoxSteps", box,
                "selectBoxHistory", box,
                "countWaitingForMe", turn);

        for (var query : queries.entrySet()) {
            BoundSql sql = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper." + query.getKey())
                    .getBoundSql(Map.of("companyId", 7L, "employeeId", 9L, "approvalDocumentId", 11L,
                            "fromDate", "2026-11-10", "toDate", "2026-11-11"));
            for (var code : query.getValue().entrySet()) {
                assertEquals(code.getValue(), sql.getAdditionalParameter(code.getKey()), query.getKey());
                assertTrue(sql.getParameterMappings().stream()
                        .anyMatch(p -> code.getKey().equals(p.getProperty())), query.getKey());
                assertFalse(sql.getSql().contains("'" + code.getValue() + "'"), query.getKey());
            }
        }
    }

    @Test
    @DisplayName("승인 UPDATE는 실제 Mapper 인자명으로 업체·문서·결재자·순번·선택 의견과 상태를 바인딩한다")
    void bindsApprovalUpdates() throws Exception {
        Configuration config = configuration();
        var method = ApprovalMapper.class.getMethod("approveStep", Long.class, Long.class,
                Integer.class, long.class, String.class);
        for (String opinion : new String[] {null, "승인합니다"}) {
            Object parameters = new ParamNameResolver(config, method)
                    .getNamedParams(new Object[] {1L, 100L, 1, 10L, opinion});
            BoundSql sql = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper.approveStep")
                    .getBoundSql(parameters);
            assertEquals(Arrays.asList("APPROVED", opinion, 1L, 10L, 100L, 1, "WAITING"),
                    boundValues(config, sql, parameters));
            assertTrue(sql.getSql().contains("processed_at = CAST(SYSTIMESTAMP AT TIME ZONE 'Asia/Seoul' AS TIMESTAMP)"));
            assertTrue(sql.getSql().contains("AND status_code = ?"));
        }

        var documentMethod = ApprovalMapper.class.getMethod("approveDocument", Long.class, long.class);
        Object parameters = new ParamNameResolver(config, documentMethod).getNamedParams(new Object[] {1L, 10L});
        BoundSql sql = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper.approveDocument")
                .getBoundSql(parameters);
        assertEquals(List.of("APPROVED", 1L, 10L, "IN_PROGRESS"), boundValues(config, sql, parameters));
        assertTrue(sql.getSql().contains("finished_at = CAST(SYSTIMESTAMP AT TIME ZONE 'Asia/Seoul' AS TIMESTAMP)"));
        assertTrue(sql.getSql().contains("AND status_code = ?"));
    }

    private List<Object> boundValues(Configuration config, BoundSql sql, Object parameters) {
        List<Object> values = new ArrayList<>();
        for (var mapping : sql.getParameterMappings()) {
            String property = mapping.getProperty();
            values.add(sql.hasAdditionalParameter(property) ? sql.getAdditionalParameter(property)
                    : config.newMetaObject(parameters).getValue(property));
        }
        return values;
    }
}
