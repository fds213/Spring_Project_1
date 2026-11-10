package com.ieum.gw.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Proxy;
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

import com.ieum.gw.dao.ApprovalDAO;
import com.ieum.gw.dto.ApprovalDocumentDTO;

class ApprovalMapperTest {
    @Test
    @DisplayName("결재 문서 조회는 업체·문서 조건으로 해당 문서만 잠그고 최대 5초 대기한다")
    void documentLockHasWaitLimit() throws Exception {
        Configuration config = configuration();
        BoundSql sql = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper.lockDocument")
                .getBoundSql(Map.of("companyId", 1L, "documentId", 10L));
        assertTrue(sql.getSql().replaceAll("\\s+", " ")
                .contains("FOR UPDATE OF approval_document_id WAIT 5"));
        assertEquals(List.of(1L, 10L), boundValues(config, sql, Map.of("companyId", 1L, "documentId", 10L)));
    }
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

    @Test
    @DisplayName("반려 UPDATE는 사유·결재자·순번과 WAITING·IN_PROGRESS 조건을 바인딩한다")
    void bindsRejectionUpdates() throws Exception {
        Configuration config = configuration();
        var method = ApprovalMapper.class.getMethod("rejectStep", Long.class, Long.class,
                Integer.class, long.class, String.class);
        Object parameters = new ParamNameResolver(config, method)
                .getNamedParams(new Object[] {1L, 100L, 1, 10L, "일정 조정 필요"});
        BoundSql sql = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper.rejectStep")
                .getBoundSql(parameters);
        assertEquals(List.of("REJECTED", "일정 조정 필요", 1L, 10L, 100L, 1, "WAITING"),
                boundValues(config, sql, parameters));
        assertTrue(sql.getSql().contains("processed_at = CAST(SYSTIMESTAMP AT TIME ZONE 'Asia/Seoul' AS TIMESTAMP)"));
        assertTrue(sql.getSql().contains("AND status_code = ?"));

        var documentMethod = ApprovalMapper.class.getMethod("rejectDocument", Long.class, long.class);
        parameters = new ParamNameResolver(config, documentMethod).getNamedParams(new Object[] {1L, 10L});
        sql = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper.rejectDocument").getBoundSql(parameters);
        assertEquals(List.of("REJECTED", 1L, 10L, "IN_PROGRESS"), boundValues(config, sql, parameters));
        assertTrue(sql.getSql().contains("finished_at = CAST(SYSTIMESTAMP AT TIME ZONE 'Asia/Seoul' AS TIMESTAMP)"));
        assertTrue(sql.getSql().contains("AND status_code = ?"));
    }

    @Test
    @DisplayName("반려 DAO는 업체·결재자·순번·문서·사유를 빠짐없이 Mapper에 전달한다")
    void rejectionDaoForwardsAllArguments() {
        List<Object> received = new ArrayList<>();
        ApprovalMapper mapper = (ApprovalMapper) Proxy.newProxyInstance(
                ApprovalMapper.class.getClassLoader(), new Class<?>[] {ApprovalMapper.class},
                (proxy, method, args) -> {
                    assertEquals("rejectStep", method.getName());
                    received.addAll(Arrays.asList(args));
                    return 1;
                });
        assertEquals(1, new ApprovalDAO(mapper).rejectStep(1L, 100L, 2, 10L, "일정 조정 필요"));
        assertEquals(List.of(1L, 100L, 2, 10L, "일정 조정 필요"), received);
    }

    @Test
    @DisplayName("휴가 반영 조회는 업체·문서로 한정하고 저장된 날짜 ID·구분·차감 일수를 날짜순으로 읽는다")
    void selectsSavedLeaveDays() throws Exception {
        Configuration config = configuration();
        var method = ApprovalMapper.class.getMethod("selectLeaveDays", Long.class, long.class);
        Object parameters = new ParamNameResolver(config, method).getNamedParams(new Object[] {1L, 10L});
        var statement = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper.selectLeaveDays");
        BoundSql sql = statement.getBoundSql(parameters);
        assertEquals(List.of(1L, 10L), boundValues(config, sql, parameters));
        String query = sql.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(query.contains("leave_request_day_id, leave_date, leave_type, deduct_days"));
        assertTrue(query.contains("WHERE company_id = ? AND approval_document_id = ?"));
        assertTrue(query.contains("ORDER BY leave_date ASC"));
        assertEquals(com.ieum.gw.dto.LeaveDayDTO.class, statement.getResultMaps().get(0).getType());
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

    @Test
    @DisplayName("장비 반영 조회는 업체·문서로 한정하고 티켓 생성에 필요한 수령 지점·종류·목적을 읽는다")
    void selectsEquipmentRequest() throws Exception {
        Configuration config = configuration();
        var method = ApprovalMapper.class.getMethod("selectEquipmentRequest", Long.class, long.class);
        Object parameters = new ParamNameResolver(config, method).getNamedParams(new Object[] {1L, 10L});
        var statement = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper.selectEquipmentRequest");
        BoundSql sql = statement.getBoundSql(parameters);
        assertEquals(List.of(1L, 10L), boundValues(config, sql, parameters));
        String query = sql.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(query.contains("branch_id, asset_type, quantity, purpose"));
        assertTrue(query.contains("WHERE company_id = ? AND approval_document_id = ?"));
        assertEquals(com.ieum.gw.dto.ApprovalEquipmentDTO.class, statement.getResultMaps().get(0).getType());
    }
    @Test
    @DisplayName("임시저장 쓰기는 업체·문서·기안자·DRAFT 조건이 모두 걸려 남의 문서나 제출한 문서를 바꾸지 못한다")
    void draftWritesGuardCompanyAuthorAndStatus() throws Exception {
        Configuration config = configuration();
        ApprovalDocumentDTO doc = new ApprovalDocumentDTO();
        doc.setCompanyId(1L);
        doc.setApprovalDocumentId(10L);
        doc.setAuthorEmployeeId(100L);
        doc.setDocumentType("DOC_LEAVE");
        doc.setStatusCode("IN_PROGRESS");

        for (String id : List.of("updateDraft", "submitDraft")) {
            BoundSql sql = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper." + id).getBoundSql(doc);
            List<Object> values = boundValues(config, sql, doc);
            assertEquals(List.of(1L, 10L, 100L, "DRAFT"), values.subList(values.size() - 4, values.size()), id);
            assertTrue(sql.getSql().replaceAll("\\s+", " ")
                    .contains("WHERE company_id = ? AND approval_document_id = ? AND author_employee_id = ? AND status_code = ?"), id);
        }

        var method = ApprovalMapper.class.getMethod("deleteDraft", Long.class, Long.class, long.class);
        Object params = new ParamNameResolver(config, method).getNamedParams(new Object[] {1L, 100L, 10L});
        BoundSql sql = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper.deleteDraft").getBoundSql(params);
        assertEquals(List.of(1L, 10L, 100L, "DRAFT"), boundValues(config, sql, params));
    }

    @Test
    @DisplayName("임시저장 목록은 내 업체·내가 쓴 DRAFT만 최근 저장 순으로 읽는다")
    void draftListIsAuthorOnly() throws Exception {
        Configuration config = configuration();
        var method = ApprovalMapper.class.getMethod("selectDraftDocuments", Long.class, Long.class);
        Object params = new ParamNameResolver(config, method).getNamedParams(new Object[] {1L, 100L});
        BoundSql sql = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper.selectDraftDocuments").getBoundSql(params);
        assertEquals(List.of(1L, 100L, "DRAFT"), boundValues(config, sql, params));
        assertTrue(sql.getSql().replaceAll("\\s+", " ").contains("ORDER BY updated_at DESC"));
    }

    @Test
    @DisplayName("자동 반려 후보는 업체·현재 순번으로 조회하며 문서 종류 제한 없이 ID순으로 반환한다")
    void retirementCandidatesUseCurrentTurn() throws Exception {
        Configuration config = configuration();
        var method = ApprovalMapper.class.getMethod("selectWaitingDocumentIds", Long.class, Long.class);
        Object params = new ParamNameResolver(config, method).getNamedParams(new Object[] {1L, 100L});
        var statement = config.getMappedStatement("com.ieum.gw.mapper.ApprovalMapper.selectWaitingDocumentIds");
        BoundSql sql = statement.getBoundSql(params);
        assertEquals(List.of(1L, "IN_PROGRESS", 100L, "WAITING", "APPROVED"), boundValues(config, sql, params));
        String query = sql.getSql().replaceAll("\\s+", " ");
        assertTrue(query.contains("ps.company_id = ms.company_id"));
        assertTrue(query.contains("ps.step_no < ms.step_no"));
        assertTrue(query.contains("ORDER BY ad.approval_document_id ASC"));
        assertFalse(query.contains("document_type"));
        assertEquals(Long.class, statement.getResultMaps().get(0).getType());
    }

}
