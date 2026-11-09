package com.ieum.gw.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketWorkMapperTest {
    @Test @DisplayName("실제 XML은 업체·요청자 조회와 업체·상태·version 조건 갱신을 바인딩한다")
    void binding() throws Exception {
        Configuration config=new Configuration();
        try(var in=getClass().getResourceAsStream("/mapper/TicketWorkMapper.xml")) {
            new XMLMapperBuilder(in,config,"mapper/TicketWorkMapper.xml",config.getSqlFragments()).parse();
        }
        Map<String,Object> p=new HashMap<>();
        p.put("companyId",1L); p.put("employeeId",2L); p.put("id",10L); p.put("version",0L);
        p.put("assignment",true); p.put("resolved",false); p.put("closed",false);
        String ns=TicketWorkMapper.class.getName()+".";
        String own=config.getMappedStatement(ns+"selectMine").getBoundSql(p).getSql();
        assertTrue(own.contains("company_id = ?")); assertTrue(own.contains("requester_employee_id = ?"));
        String update=config.getMappedStatement(ns+"update").getBoundSql(p).getSql();
        assertTrue(update.contains("version = version + 1"));
        assertTrue(update.contains("version = ? AND status_code = ?"));
        assertTrue(update.contains("company_id = ? AND it_ticket_id = ?"));
        assertTrue(config.getMappedStatement(ns+"lock").getBoundSql(p).getSql().contains("FOR UPDATE"));
        assertTrue(config.hasStatement(ns+"insert!selectKey"));
        for(String id:new String[]{"comments","history"}) {
            String sql=config.getMappedStatement(ns+id).getBoundSql(p).getSql();
            assertTrue(sql.contains("company_id = ? AND it_ticket_id = ?"));
        }
    }

    @Test
    @DisplayName("관리자 검색의 건수와 목록 SQL은 업체·제목 조건을 공유하고 선택한 탭만 페이징한다")
    void adminSearchBinding() throws Exception {
        Configuration config = new Configuration();
        String resource = "mapper/TicketWorkMapper.xml";
        try (var input = getClass().getResourceAsStream("/" + resource)) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }

        String namespace = TicketWorkMapper.class.getName() + ".";
        Map<String, Object> parameters = new HashMap<>(Map.of(
                "companyId", 1L, "employeeId", 3L, "keyword", "노트북_%", "offset", 20L, "size", 20));
        var count = config.getMappedStatement(namespace + "countAdminTicketTabs").getBoundSql(parameters);
        String countSql = count.getSql().replaceAll("\\s+", " ").strip();
        assertTrue(countSql.startsWith("WITH admin_tickets AS ("));
        assertTrue(countSql.contains("WHERE company_id = ? AND INSTR(LOWER(title), LOWER(?)) > 0"));
        assertFalse(countSql.contains("노트북_%"));
        assertEquals("RECEIVED", count.getAdditionalParameter("received"));
        assertEquals("CLOSED", count.getAdditionalParameter("closed"));
        String sharedQuery = countSql.substring(0, countSql.indexOf("SELECT COUNT("));

        var statement = config.getMappedStatement(namespace + "selectAdminTicketPage");
        Map<String, String> tabConditions = Map.of(
                "UNASSIGNED", "WHERE is_unassigned = 1 ",
                "MINE", "WHERE is_mine = 1 ",
                "IN_PROGRESS", "WHERE is_in_progress = 1 ",
                "RESOLVED", "WHERE is_resolved = 1 ",
                "ALL", "");
        for (var tab : tabConditions.entrySet()) {
            parameters.put("filter", tab.getKey());
            String pageSql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ").strip();
            assertTrue(pageSql.startsWith(sharedQuery));
            assertTrue(pageSql.endsWith("FROM admin_tickets " + tab.getValue()
                    + "ORDER BY at DESC, id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        }

        parameters.put("keyword", "");
        assertFalse(statement.getBoundSql(parameters).getSql().contains("INSTR"));
    }
}
