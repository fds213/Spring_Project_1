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
}
