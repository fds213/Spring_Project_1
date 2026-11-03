package com.ieum.gw.ticket.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;

class TicketReadServiceTest {
    @Test
    @DisplayName("티켓 번호가 없으면 댓글·이력 조회를 시작하지 않는다")
    void rejectsMissingId() {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(7L);
        emp.setEmployeeId(9L);
        LoginUser user = new LoginUser(emp);
        TicketReadService service = new TicketReadService(null);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.getMyComments(user, null)).getRawStatusCode());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.getMyHistory(user, null)).getRawStatusCode());
    }

    @Test
    @DisplayName("실제 Mapper의 티켓·댓글·이력 조회는 모두 업체·요청자·티켓 ID를 바인딩한다")
    void mapperScopesEveryReadToOwner() throws Exception {
        Configuration config = new Configuration();
        try (InputStream in = getClass().getResourceAsStream("/mapper/TicketReadMapper.xml")) {
            assertNotNull(in);
            new XMLMapperBuilder(in, config, "mapper/TicketReadMapper.xml", config.getSqlFragments()).parse();
        }
        for (String id : List.of("countMyTicket", "selectMyComments", "selectMyHistory")) {
            var bound = config.getMappedStatement("com.ieum.gw.ticket.mapper.TicketReadMapper." + id)
                    .getBoundSql(Map.of("companyId", 7L, "employeeId", 9L, "itTicketId", 13L));
            String sql = bound.getSql().replaceAll("\\s+", " ").toLowerCase();
            assertTrue(sql.contains("where t.company_id = ? and t.requester_employee_id = ? and t.it_ticket_id = ?"));
            assertEquals(List.of("companyId", "employeeId", "itTicketId"), bound.getParameterMappings().stream()
                    .map(mapping -> mapping.getProperty()).collect(Collectors.toList()));
            if (id.equals("selectMyComments")) {
                assertTrue(sql.contains("t.company_id = c.company_id and t.it_ticket_id = c.it_ticket_id"));
                assertTrue(sql.contains("order by c.created_at, c.ticket_comment_id"));
            } else if (id.equals("selectMyHistory")) {
                assertTrue(sql.contains("t.company_id = h.company_id and t.it_ticket_id = h.it_ticket_id"));
                assertTrue(sql.contains("order by h.created_at, h.ticket_history_id"));
            }
        }
    }
}
