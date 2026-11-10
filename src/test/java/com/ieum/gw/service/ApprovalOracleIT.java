package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dao.*;
import com.ieum.gw.dto.*;
import com.ieum.gw.mapper.*;

/** 로컬 초기화 DB 전용. 실제 커밋한 시나리오 자료를 남기므로 명시적으로 켰을 때만 실행한다. */
@EnabledIfEnvironmentVariable(named = "IEUM_LOCAL_ORACLE_TEST", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApprovalOracleIT {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private ApprovalService approval;
    private LoginUser author;
    private LoginUser first;
    private LoginUser second;

    @BeforeAll
    void connectLocalOracle() throws Exception {
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(Paths.get("src/main/resources/config/db.properties"))) {
            p.load(in);
        }
        assertEquals("jdbc:log4jdbc:oracle:thin:@localhost:1522/FREEPDB1", p.getProperty("db.url"));
        assertEquals("ieum", p.getProperty("db.username").toLowerCase());
        DataSource dataSource = new DriverManagerDataSource(
                p.getProperty("db.url").replace("jdbc:log4jdbc:", "jdbc:"),
                p.getProperty("db.username"), p.getProperty("db.password"));
        jdbc = new JdbcTemplate(dataSource);
        assertEquals("FREEPDB1", jdbc.queryForObject("SELECT SYS_CONTEXT('USERENV','CON_NAME') FROM dual", String.class));
        transactions = new DataSourceTransactionManager(dataSource);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfigLocation(new ClassPathResource("config/mybatis-config.xml"));
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());

        AttendanceDAO attendanceDao = new AttendanceDAO(session.getMapper(AttendanceMapper.class));
        LeaveService leave = transactional(new LeaveService(new LeaveDAO(session.getMapper(LeaveMapper.class)), attendanceDao));
        HolidayService holidays = transactional(new HolidayService(attendanceDao));
        AttendanceService attendance = transactional(new AttendanceService(attendanceDao, leave, holidays));
        EmployeeService employees = new EmployeeService();
        ReflectionTestUtils.setField(employees, "dao", new EmployeeDAO(session.getMapper(EmployeeMapper.class)));
        ReflectionTestUtils.setField(employees, "passwordEncoder", new BCryptPasswordEncoder());
        ReflectionTestUtils.setField(employees, "attendanceService", attendance);
        BranchService branches = transactional(new BranchService(new BranchDAO(session.getMapper(BranchMapper.class))));
        approval = transactional(new ApprovalService(employees, attendance,
                new ApprovalDAO(session.getMapper(ApprovalMapper.class)),
                branches,
                new NotificationService(new NotificationDAO(session.getMapper(NotificationMapper.class))),
                transactional(new ScheduleService(new ScheduleDAO(session.getMapper(ScheduleMapper.class)), attendance)),

                transactional(new TicketService(new TicketDAO(session.getMapper(TicketMapper.class)))), null));

        author = user("20260002");
        first = user("20260001");
        second = user("20260004");
        holidays.addPreset(author.getCompanyId(), 2026);
    }

    @SuppressWarnings("unchecked")
    private <T> T transactional(T target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    private LoginUser user(String employeeNo) {
        return jdbc.queryForObject("SELECT e.company_id, e.employee_id, a.user_account_id, a.role_code "
                + "FROM employee e JOIN company c ON c.company_id=e.company_id "
                + "JOIN user_account a ON a.company_id=e.company_id AND a.employee_id=e.employee_id "
                + "WHERE c.company_code='ieum0001' AND e.employee_no=?", (rs, row) -> {
                    AuthEmpDTO auth = new AuthEmpDTO();
                    auth.setCompanyId(rs.getLong("company_id"));
                    auth.setEmployeeId(rs.getLong("employee_id"));
                    auth.setUserAccountId(rs.getLong("user_account_id"));
                    auth.setRole(rs.getString("role_code"));
                    return new LoginUser(auth);
                }, employeeNo);
    }

    private ApprovalSubmitDTO leave(String from, String to, LoginUser... approvers) {
        ApprovalSubmitDTO request = new ApprovalSubmitDTO();
        request.setDocumentType("DOC_LEAVE");
        request.setLine(java.util.Arrays.stream(approvers).map(u -> u.getEmployeeId().toString())
                .collect(java.util.stream.Collectors.toList()));
        ApprovalFieldsDTO fields = new ApprovalFieldsDTO();
        fields.setLeaveType("LV_FULL");
        fields.setFromDate(from);
        fields.setToDate(to);
        fields.setReason("로컬 Oracle 통합 검증");
        request.setFields(fields);
        return request;
    }

    private ApprovalDecisionDTO opinion(String text) {
        ApprovalDecisionDTO request = new ApprovalDecisionDTO();
        request.setOpinion(text);
        return request;
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String status(long documentId) {
        return jdbc.queryForObject("SELECT status_code FROM approval_document WHERE approval_document_id=?", String.class, documentId);
    }

    @Test @DisplayName("실제 Oracle에서 1차·최종 승인과 중복 요청을 처리하고 연차·일정·알림을 한 번 저장한다")
    void approvesLeave() {
        long id = approval.submit(author, leave("2026-11-12", "2026-11-12", first, second));
        assertEquals("IN_PROGRESS", approval.approve(first, opinion("1차 승인"), id).getStatusCode());
        assertEquals(0, count("SELECT COUNT(*) FROM calendar_event WHERE leave_request_day_id IN "
                + "(SELECT leave_request_day_id FROM leave_request_day WHERE approval_document_id=?)", id));
        assertEquals("APPROVED", approval.approve(second, opinion("최종 승인"), id).getStatusCode());
        assertEquals("APPROVED", status(id));
        assertEquals(1, count("SELECT COUNT(*) FROM leave_ledger WHERE leave_request_day_id IN "
                + "(SELECT leave_request_day_id FROM leave_request_day WHERE approval_document_id=?)", id));
        assertEquals(1, count("SELECT COUNT(*) FROM calendar_event WHERE leave_request_day_id IN "
                + "(SELECT leave_request_day_id FROM leave_request_day WHERE approval_document_id=?)", id));
        assertEquals(1, count("SELECT COUNT(*) FROM notification WHERE approval_document_id=? AND notification_type='NT_APPROVAL_DONE'", id));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> approval.approve(second, opinion("중복"), id)).getRawStatusCode());
    }

    @Test @DisplayName("실제 Oracle 반려는 필수 사유를 검사하고 반려 상태·의견·이력·알림을 저장한다")
    void rejectsLeave() {
        long id = approval.submit(author, leave("2026-11-13", "2026-11-13", first));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> approval.reject(first, opinion(" "), id)).getRawStatusCode());
        assertEquals("REJECTED", approval.reject(first, opinion("일정 변경 필요"), id).getStatusCode());
        assertEquals("REJECTED", status(id));
        assertEquals("일정 변경 필요", jdbc.queryForObject(
                "SELECT opinion FROM approval_step WHERE approval_document_id=?", String.class, id));
        assertEquals(1, count("SELECT COUNT(*) FROM approval_history WHERE approval_document_id=? AND action_code='APR_REJECT'", id));
        assertEquals(1, count("SELECT COUNT(*) FROM notification WHERE approval_document_id=? AND notification_type='NT_APPROVAL_REJECT'", id));
    }

    @Test @DisplayName("실제 Oracle 장비 최종 승인은 지급 티켓을 한 건 생성하고 중복 승인을 거절한다")
    void createsEquipmentTicket() {
        ApprovalSubmitDTO request = leave("2026-11-12", "2026-11-12", first);
        request.setDocumentType("DOC_EQUIPMENT");
        ApprovalFieldsDTO fields = new ApprovalFieldsDTO();
        fields.setAssetType("AT_LAPTOP");
        fields.setQuantity(1);
        fields.setPurpose("로컬 Oracle 장비 지급 검증");
        fields.setBranchId(jdbc.queryForObject("SELECT MIN(branch_id) FROM branch WHERE company_id=?", Long.class,
                author.getCompanyId()).toString());
        request.setFields(fields);
        long id = approval.submit(author, request);
        ApprovalDecisionResultDTO response = approval.approve(first, opinion(null), id);
        assertEquals("APPROVED", response.getStatusCode());
        assertEquals(1, count("SELECT COUNT(*) FROM it_ticket WHERE company_id=? AND equipment_document_id=?",
                author.getCompanyId(), id));
        Long ticketId = jdbc.queryForObject("SELECT it_ticket_id FROM it_ticket WHERE company_id=? AND equipment_document_id=?",
                Long.class, author.getCompanyId(), id);
        assertEquals(ticketId.toString(), response.getTicketId());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> approval.approve(first, opinion(null), id)).getRawStatusCode());
        assertEquals(1, count("SELECT COUNT(*) FROM it_ticket WHERE equipment_document_id=?", id));
    }

    // 첫 작업의 커밋을 의도적으로 늦추고, 다른 연결의 상신이 기다렸다가 최신 값으로 거절되는지 확인한다.
    private void rejectsAfterWaiting(Supplier<?> holder, Runnable competitor) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> waiting = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setTimeout(20);
        try {
            transaction.execute(status -> {
                holder.get();
                waiting.set(worker.submit(() -> { started.countDown(); competitor.run(); }));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> waiting.get().get(500, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return null;
            });
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> waiting.get().get(15, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof IllegalArgumentException, error.toString());
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    @Test @DisplayName("같은 직원의 같은 날짜 동시 상신은 실제 행 잠금에서 기다린 뒤 한 건만 저장한다")
    void concurrentOverlap() throws Exception {
        ApprovalSubmitDTO request = leave("2026-11-10", "2026-11-10", first);
        int before = count("SELECT COUNT(*) FROM approval_document WHERE author_employee_id=?", second.getEmployeeId());
        rejectsAfterWaiting(() -> approval.submit(second, request), () -> approval.submit(second, request));
        assertEquals(before + 1, count("SELECT COUNT(*) FROM approval_document WHERE author_employee_id=?", second.getEmployeeId()));
    }

    @Test @DisplayName("날짜가 다른 동시 상신도 대기 일수를 잠금 후 다시 읽어 15일 초과를 거절한다")
    void concurrentCapacity() throws Exception {
        ApprovalSubmitDTO eightDays = leave("2026-11-16", "2026-11-25", first);
        ApprovalSubmitDTO otherEightDays = leave("2026-11-26", "2026-12-07", first);
        int before = count("SELECT COUNT(*) FROM approval_document WHERE author_employee_id=?", second.getEmployeeId());
        rejectsAfterWaiting(() -> approval.submit(second, eightDays), () -> approval.submit(second, otherEightDays));
        assertEquals(before + 1, count("SELECT COUNT(*) FROM approval_document WHERE author_employee_id=?", second.getEmployeeId()));
    }

    @Test @DisplayName("최종 승인과 같은 날짜 상신이 겹치면 상신이 연차 잠금에서 기다린 뒤 승인 겹침을 거절한다")
    void concurrentApprovalAndSubmit() throws Exception {
        ApprovalSubmitDTO request = leave("2026-11-11", "2026-11-11", author);
        long id = approval.submit(first, request);
        rejectsAfterWaiting(() -> approval.approve(author, opinion(null), id), () -> approval.submit(first, request));
        assertEquals("APPROVED", status(id));
    }

    @Test @DisplayName("실제 Oracle 승인 트랜잭션 실패는 문서·결재선·연차·일정·완료 알림·승인 이력을 되돌린다")
    void oracleRollback() {
        long id = approval.submit(author, leave("2026-11-30", "2026-11-30", first));
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            approval.approve(first, opinion("롤백 검증"), id);
            throw new IllegalStateException("승인 트랜잭션 실패 검증");
        }));
        assertEquals("IN_PROGRESS", status(id));
        assertEquals("WAITING", jdbc.queryForObject("SELECT status_code FROM approval_step WHERE approval_document_id=?", String.class, id));
        assertEquals(0, count("SELECT COUNT(*) FROM leave_ledger WHERE leave_request_day_id IN "
                + "(SELECT leave_request_day_id FROM leave_request_day WHERE approval_document_id=?)", id));
        assertEquals(0, count("SELECT COUNT(*) FROM calendar_event WHERE leave_request_day_id IN "
                + "(SELECT leave_request_day_id FROM leave_request_day WHERE approval_document_id=?)", id));
        assertEquals(0, count("SELECT COUNT(*) FROM notification WHERE approval_document_id=? AND notification_type='NT_APPROVAL_DONE'", id));
        assertEquals(0, count("SELECT COUNT(*) FROM approval_history WHERE approval_document_id=? AND action_code='APR_APPROVE'", id));
    }
}
