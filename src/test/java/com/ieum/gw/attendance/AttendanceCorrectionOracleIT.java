package com.ieum.gw.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.ieum.gw.dao.AttendanceCorrectionDAO;
import com.ieum.gw.dao.AttendanceDAO;
import com.ieum.gw.dao.NotificationDAO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.mapper.AttendanceCorrectionMapper;
import com.ieum.gw.mapper.AttendanceMapper;
import com.ieum.gw.mapper.NotificationMapper;
import com.ieum.gw.service.AttendanceCorrectionService;
import com.ieum.gw.service.AttendanceException;
import com.ieum.gw.service.NotificationService;

/**
 * 근태 정정 SQL을 실제 Oracle에서 돌린다. 팀 DB가 아니라 이 PC에 임시로 띄운 빈 DB(db/ 스크립트로 만든 것)에만 붙는다:
 * IEUM_LOCAL_ORACLE_URL(localhost 주소만)·USER·PASSWORD 를 줬을 때만 실행하고, 자료를 실제로 커밋한다(쓰고 버리는 DB).
 * 예: docker 의 gvenzl/oracle-xe:21-slim-faststart → .github/scripts/apply-db-scripts.sh 로 스크립트 적용 → 이 테스트
 */
@EnabledIfEnvironmentVariable(named = "IEUM_LOCAL_ORACLE_URL", matches = "jdbc:oracle:thin:@//localhost:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceCorrectionOracleIT {

    private JdbcTemplate jdbc;
    private AttendanceCorrectionService service;
    private LocalDate today;
    private LoginUser admin;  // 강아지(20260001): 업체 관리자, 경영지원팀 부서장
    private LoginUser head;   // 홍길동(20260002): 개발팀 부서장
    private LoginUser member; // 아무개(20260003): 이 테스트에서 개발팀으로 옮긴다

    /** 임시 DB에 붙고, 개발팀에 부서원을 하나 둔다 */
    @BeforeAll
    void connect() throws Exception {
        DataSource ds = new DriverManagerDataSource(System.getenv("IEUM_LOCAL_ORACLE_URL"),
                System.getenv("IEUM_LOCAL_ORACLE_USER"), System.getenv("IEUM_LOCAL_ORACLE_PASSWORD"));
        jdbc = new JdbcTemplate(ds);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfigLocation(new ClassPathResource("config/mybatis-config.xml"));
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        AttendanceDAO attendanceDao = new AttendanceDAO(session.getMapper(AttendanceMapper.class));
        ProxyFactory proxy = new ProxyFactory(new AttendanceCorrectionService(
                new AttendanceCorrectionDAO(session.getMapper(AttendanceCorrectionMapper.class)), attendanceDao,
                new NotificationService(new NotificationDAO(session.getMapper(NotificationMapper.class)))));
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (AttendanceCorrectionService) proxy.getProxy();
        today = LocalDate.parse(attendanceDao.selectToday());

        admin = user("20260001");
        head = user("20260002");
        member = user("20260003");
        jdbc.update("UPDATE employee SET department_id = (SELECT department_id FROM department WHERE company_id = ? AND department_name = '개발팀') "
                + "WHERE company_id = ? AND employee_id = ?", member.getCompanyId(), member.getCompanyId(), member.getEmployeeId());
    }

    /** ieum0001 의 사번으로 로그인 사용자를 만든다 */
    private LoginUser user(String employeeNo) {
        return jdbc.queryForObject("SELECT e.company_id, e.employee_id, a.user_account_id, a.role_code FROM employee e "
                + "JOIN company c ON c.company_id = e.company_id "
                + "JOIN user_account a ON a.company_id = e.company_id AND a.employee_id = e.employee_id "
                + "WHERE c.company_code = 'ieum0001' AND e.employee_no = ?", (rs, n) -> {
                    AuthEmpDTO a = new AuthEmpDTO();
                    a.setCompanyId(rs.getLong("company_id"));
                    a.setEmployeeId(rs.getLong("employee_id"));
                    a.setUserAccountId(rs.getLong("user_account_id"));
                    a.setRole(rs.getString("role_code"));
                    return new LoginUser(a);
                }, employeeNo);
    }

    /** 오늘에서 며칠 전 날짜에 출근만 있는 근태(퇴근 누락)를 넣고 그 날짜를 돌려준다 */
    private String noOut(LoginUser u, int daysAgo, String in) {
        String d = today.minusDays(daysAgo).toString();
        jdbc.update("INSERT INTO attendance (company_id, employee_id, work_date, check_in_at) "
                + "VALUES (?, ?, TO_DATE(?, 'YYYY-MM-DD'), TO_TIMESTAMP(? || ' ' || ?, 'YYYY-MM-DD HH24:MI'))",
                u.getCompanyId(), u.getEmployeeId(), d, d, in);
        return d;
    }

    /** 그날 근태의 퇴근 시각 (HH24:MI, 없으면 null) */
    private String checkOut(LoginUser u, String d) {
        return jdbc.queryForObject("SELECT TO_CHAR(check_out_at, 'HH24:MI') FROM attendance WHERE company_id = ? AND employee_id = ? "
                + "AND work_date = TO_DATE(?, 'YYYY-MM-DD')", String.class, u.getCompanyId(), u.getEmployeeId(), d);
    }

    /** 정정 요청 행의 칸 하나 */
    private String col(Long id, String column) {
        return jdbc.queryForObject("SELECT TO_CHAR(" + column + ") FROM attendance_correction WHERE attendance_correction_id = ?", String.class, id);
    }

    /** 처리할 목록의 요청 번호들 */
    @SuppressWarnings("unchecked")
    private List<Long> inboxIds(LoginUser u) {
        return ((List<Map<String, Object>>) service.inbox(u).get("items")).stream()
                .map(m -> ((Number) m.get("id")).longValue()).collect(Collectors.toList());
    }

    /** 이 정정 요청으로 남은 알림: "종류:받는 사원 번호" (남은 순서) */
    private List<String> notis(Long id) {
        return jdbc.queryForList("SELECT n.notification_type || ':' || a.employee_id FROM notification n "
                + "JOIN user_account a ON a.company_id = n.company_id AND a.user_account_id = n.receiver_account_id "
                + "WHERE n.attendance_correction_id = ? ORDER BY n.notification_id", String.class, id);
    }

    /** 거절된 요청의 상태 코드 */
    private static int status(Runnable r) {
        return assertThrows(AttendanceException.class, r::run).getStatus();
    }

    @Test
    @DisplayName("요청 → 부서장 승인: 원래 출근을 근태에서 복사하고, 승인하면 그날 퇴근이 채워진다(CK_82_*·CK_36_02 통과)")
    void requestAndApprove() {
        String d = noOut(member, 3, "09:07");
        Long id = ((Number) service.request(member, d, null, "18:25", "단말기 고장").get("id")).longValue();
        assertEquals("ACR_REQUESTED", col(id, "status_code"));
        assertNotNull(col(id, "origin_check_in_at"));
        assertNull(col(id, "origin_check_out_at"));
        assertTrue(inboxIds(head).contains(id));
        assertTrue(!inboxIds(admin).contains(id)); // 부서장이 있으면 업체 관리자 몫이 아니다

        assertEquals(403, status(() -> service.approve(admin, id)));
        assertEquals(403, status(() -> service.approve(member, id)));
        service.approve(head, id);
        assertEquals("18:25", checkOut(member, d));
        assertEquals("ACR_APPROVED", col(id, "status_code"));
        assertEquals(String.valueOf(head.getEmployeeId()), col(id, "approver_employee_id"));
        assertNotNull(col(id, "decided_at"));
        // 알림(FK_69_07·CK_69_01): 요청은 부서장에게, 승인은 요청한 직원에게
        assertEquals(List.of("NT_ATT_CORRECTION_REQUEST:" + head.getEmployeeId(), "NT_ATT_CORRECTION_DONE:" + member.getEmployeeId()), notis(id));
        assertEquals(409, status(() -> service.reject(head, id, "늦은 반려")));
        assertEquals(409, status(() -> service.request(member, d, null, "19:00", "이미 퇴근이 있는 날")));
    }

    @Test
    @DisplayName("같은 날 대기 하나만(UX_82_01), 취소 뒤 다시 요청, 반려는 사유를 남기고 근태는 그대로")
    void duplicateCancelReject() {
        String d = noOut(member, 4, "08:58");
        Long first = ((Number) service.request(member, d, null, "18:00", "첫 요청").get("id")).longValue();
        assertEquals(409, status(() -> service.request(member, d, null, "18:10", "두 번째")));
        assertEquals(404, status(() -> service.cancel(head, first)));
        service.cancel(member, first);
        assertEquals("ACR_CANCELED", col(first, "status_code"));

        Long second = ((Number) service.request(member, d, null, "18:10", "다시 요청").get("id")).longValue();
        assertEquals(400, status(() -> service.reject(head, second, " ")));
        service.reject(head, second, "그날 일찍 나간 것으로 압니다");
        assertEquals("ACR_REJECTED", col(second, "status_code"));
        assertEquals(List.of("NT_ATT_CORRECTION_REQUEST:" + head.getEmployeeId()), notis(first)); // 취소는 알림 없음
        assertEquals(List.of("NT_ATT_CORRECTION_REQUEST:" + head.getEmployeeId(), "NT_ATT_CORRECTION_DONE:" + member.getEmployeeId()), notis(second));
        assertEquals("그날 일찍 나간 것으로 압니다", col(second, "reject_reason"));
        assertNull(checkOut(member, d));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mine = (List<Map<String, Object>>) service.myCorrections(member, d.substring(0, 7)).get("items");
        assertTrue(mine.stream().anyMatch(m -> "REJECTED".equals(m.get("status")) && "그날 일찍 나간 것으로 압니다".equals(m.get("rejectReason"))));
    }

    @Test
    @DisplayName("부서장 본인 요청은 업체 관리자가, 부서장이 휴직이면 그 부서원 요청도 업체 관리자가 처리한다")
    void adminDecides() {
        String hd = noOut(head, 5, "09:00");
        Long own = ((Number) service.request(head, hd, null, "18:00", "부서장 본인").get("id")).longValue();
        assertTrue(inboxIds(admin).contains(own));
        assertEquals(403, status(() -> service.approve(head, own)));
        assertTrue(notis(own).contains("NT_ATT_CORRECTION_REQUEST:" + admin.getEmployeeId())); // 부서장 본인 요청은 업체 관리자에게
        assertTrue(!notis(own).contains("NT_ATT_CORRECTION_REQUEST:" + head.getEmployeeId()));
        service.approve(admin, own);
        assertEquals("18:00", checkOut(head, hd));

        String md = noOut(member, 6, "09:00");
        Long id = ((Number) service.request(member, md, null, "18:40", "부서장 휴직 중").get("id")).longValue();
        jdbc.update("UPDATE employee SET employment_status = 'ST_LEAVE' WHERE company_id = ? AND employee_id = ?", head.getCompanyId(), head.getEmployeeId());
        try {
            assertTrue(inboxIds(admin).contains(id));
            assertEquals(403, status(() -> service.approve(head, id)));
            service.approve(admin, id);
            assertEquals("18:40", checkOut(member, md));
        } finally {
            jdbc.update("UPDATE employee SET employment_status = 'ST_SERVING' WHERE company_id = ? AND employee_id = ?", head.getCompanyId(), head.getEmployeeId());
        }
    }

    @Test
    @DisplayName("오늘은 409, 7일 지난 날은 409, 출근보다 이른 퇴근은 400 (실제 근태 행 기준)")
    void rejectedBeforeInsert() {
        assertEquals(409, status(() -> service.request(member, today.toString(), null, "18:00", "오늘")));
        assertEquals(409, status(() -> service.request(member, today.minusDays(9).toString(), "09:00", "18:00", "기한 지남")));
        String d = noOut(member, 7, "10:30");
        assertEquals(400, status(() -> service.request(member, d, null, "10:00", "출근보다 이름")));
    }

    @Test
    @DisplayName("결근 정정: 기록 없는 근무일에 출근·퇴근을 요청하고(CK_82_07), 승인하면 그날 근태가 새로 생긴다")
    void absenceRequestAndApprove() {
        // 1~7일 전 중 이 테스트들이 근태를 넣지 않는 평일 (부서원은 3·4·6·7일 전을 쓴다)
        String d = null;
        for (int i = 1; i <= 7 && d == null; i++) {
            LocalDate x = today.minusDays(i);
            boolean weekday = x.getDayOfWeek().getValue() <= 5;
            if (weekday && !List.of(3, 4, 6, 7).contains(i)) d = x.toString();
        }
        assertNotNull(d);
        String day = d;
        assertEquals(400, status(() -> service.request(member, day, null, "18:00", "출근 시각 없음")));
        Long id = ((Number) service.request(member, day, "09:10", "18:20", "외근 뒤 바로 퇴근").get("id")).longValue();
        assertNull(col(id, "attendance_id"));
        assertNotNull(col(id, "req_check_in_at"));
        service.approve(head, id);
        assertEquals("18:20", checkOut(member, day));
        assertEquals("09:10", jdbc.queryForObject("SELECT TO_CHAR(check_in_at, 'HH24:MI') FROM attendance WHERE company_id = ? AND employee_id = ? "
                + "AND work_date = TO_DATE(?, 'YYYY-MM-DD')", String.class, member.getCompanyId(), member.getEmployeeId(), day));
        assertEquals("ACR_APPROVED", col(id, "status_code"));
    }
}
