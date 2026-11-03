package com.ieum.gw.ticket.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.ieum.gw.ticket.dao.TicketDAO;
import com.ieum.gw.ticket.dto.EquipmentTicketDTO;

class TicketServiceTest {
    static class FakeDAO extends TicketDAO {
        int result = 1;
        int calls;
        RuntimeException error;
        EquipmentTicketDTO existing;

        FakeDAO() { super(null); }

        @Override
        public int insertEquipmentTicket(Long companyId, Long requesterEmployeeId, Long approvalDocumentId,
                                         Long branchId, String purpose) {
            assertEquals(7L, companyId);
            assertEquals(9L, requesterEmployeeId);
            assertEquals(11L, approvalDocumentId);
            assertEquals(13L, branchId);
            assertEquals("개발 업무", purpose);
            calls++;
            if (error != null) { throw error; }
            return result;
        }

        @Override
        public EquipmentTicketDTO selectEquipmentTicket(Long companyId, Long approvalDocumentId) {
            assertEquals(7L, companyId);
            assertEquals(11L, approvalDocumentId);
            return existing;
        }
    }

    private void create(TicketService service) {
        service.createEquipmentTicket(7L, 9L, 11L, 13L, "AT_LAPTOP", "개발 업무");
    }

    @Test
    @DisplayName("장비 승인 연동 계약의 업체·요청자·결재 문서·지점·목적을 저장한다")
    void insertsEquipmentTicket() {
        FakeDAO dao = new FakeDAO();
        create(new TicketService(dao));
        assertEquals(1, dao.calls);
    }

    @Test
    @DisplayName("동일 결재의 중복 생성은 같은 내용일 때 기존 티켓을 유지한다")
    void acceptsMatchingDuplicate() {
        FakeDAO dao = new FakeDAO();
        dao.result = 0;
        dao.existing = new EquipmentTicketDTO();
        dao.existing.setRequesterEmployeeId(9L);
        dao.existing.setBranchId(13L);
        dao.existing.setContent("개발 업무");
        assertDoesNotThrow(() -> create(new TicketService(dao)));
    }

    @Test
    @DisplayName("중복 건이 다른 업체에 있거나 요청자·지점·목적이 다르면 실패한다")
    void rejectsMismatchedDuplicate() {
        FakeDAO dao = new FakeDAO();
        dao.result = 0;
        assertThrows(IllegalStateException.class, () -> create(new TicketService(dao)));
        dao.existing = new EquipmentTicketDTO();
        dao.existing.setRequesterEmployeeId(99L);
        dao.existing.setBranchId(13L);
        dao.existing.setContent("개발 업무");
        assertThrows(IllegalStateException.class, () -> create(new TicketService(dao)));
        dao.existing.setRequesterEmployeeId(9L);
        dao.existing.setBranchId(99L);
        assertThrows(IllegalStateException.class, () -> create(new TicketService(dao)));
        dao.existing.setBranchId(13L);
        dao.existing.setContent("다른 목적");
        assertThrows(IllegalStateException.class, () -> create(new TicketService(dao)));
    }

    @Test
    @DisplayName("잘못된 ID·장비 종류·사용 목적은 저장 전에 거절한다")
    void validatesInput() {
        TicketService service = new TicketService(null);
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertThrows(IllegalArgumentException.class, () -> service.createEquipmentTicket(id, 9L, 11L, 13L, "AT_LAPTOP", "목적"));
            assertThrows(IllegalArgumentException.class, () -> service.createEquipmentTicket(7L, id, 11L, 13L, "AT_LAPTOP", "목적"));
            assertThrows(IllegalArgumentException.class, () -> service.createEquipmentTicket(7L, 9L, id, 13L, "AT_LAPTOP", "목적"));
            assertThrows(IllegalArgumentException.class, () -> service.createEquipmentTicket(7L, 9L, 11L, id, "AT_LAPTOP", "목적"));
        }
        for (String purpose : new String[] {null, "", "   ", "가".repeat(1001)}) {
            assertThrows(IllegalArgumentException.class, () -> service.createEquipmentTicket(7L, 9L, 11L, 13L, "AT_LAPTOP", purpose));
        }
        for (String type : new String[] {null, "", "UNKNOWN"}) {
            assertThrows(IllegalArgumentException.class, () -> service.createEquipmentTicket(7L, 9L, 11L, 13L, type, "목적"));
        }
    }

    @Test
    @DisplayName("원본 문서 중복 외의 DB 오류는 숨기지 않고 호출자에게 전파한다")
    void propagatesDatabaseError() {
        FakeDAO dao = new FakeDAO();
        dao.error = new IllegalStateException("저장 실패");
        assertSame(dao.error, assertThrows(IllegalStateException.class, () -> create(new TicketService(dao))));
    }

    @Test
    @DisplayName("티켓 SQL은 원본 결재 문서 고유 제약만 건너뛰고 업체 조건으로 중복 건을 읽는다")
    void parsesRealMapper() throws Exception {
        Configuration config = new Configuration();
        try (InputStream in = getClass().getResourceAsStream("/mapper/TicketMapper.xml")) {
            assertNotNull(in);
            new XMLMapperBuilder(in, config, "mapper/TicketMapper.xml", config.getSqlFragments()).parse();
        }
        String prefix = "com.ieum.gw.ticket.mapper.TicketMapper.";
        String insert = config.getMappedStatement(prefix + "insertEquipmentTicket")
                .getBoundSql(Map.of()).getSql().replaceAll("\\s+", " ");
        assertTrue(insert.contains("IGNORE_ROW_ON_DUPKEY_INDEX(IT_TICKET (EQUIPMENT_DOCUMENT_ID))"));
        assertTrue(insert.contains("'TK_EQUIPMENT'"));
        var select = config.getMappedStatement(prefix + "selectEquipmentTicket")
                .getBoundSql(Map.of("companyId", 7L, "approvalDocumentId", 11L));
        assertTrue(select.getSql().replaceAll("\\s+", " ").contains("WHERE company_id = ? AND equipment_document_id = ?"));
        assertEquals("companyId", select.getParameterMappings().get(0).getProperty());
    }

    // 실제 DB 없이 Spring 프록시의 트랜잭션 참여·오류 전파만 검사한다.
    static class FakeTransactions extends AbstractPlatformTransactionManager {
        final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        int begins;
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() { return active.get(); }
        @Override protected boolean isExistingTransaction(Object tx) { return Boolean.TRUE.equals(tx); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { active.set(true); begins++; }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
        @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { }
        @Override protected void doCleanupAfterCompletion(Object tx) { active.remove(); }
    }

    private TicketService proxy(FakeDAO dao, FakeTransactions transactions) {
        ProxyFactory factory = new ProxyFactory(new TicketService(dao));
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (TicketService) factory.getProxy();
    }

    @Test
    @DisplayName("결재 트랜잭션 없이 지급 티켓만 따로 생성하는 호출은 거절한다")
    void requiresApprovalTransaction() {
        FakeDAO dao = new FakeDAO();
        assertThrows(IllegalTransactionStateException.class, () -> create(proxy(dao, new FakeTransactions())));
        assertEquals(0, dao.calls);
    }

    @Test
    @DisplayName("결재 트랜잭션에 참여해 별도 트랜잭션을 시작하지 않는다")
    void joinsApprovalTransaction() {
        FakeTransactions transactions = new FakeTransactions();
        TicketService service = proxy(new FakeDAO(), transactions);
        new TransactionTemplate(transactions).execute(status -> { create(service); return null; });
        assertEquals(1, transactions.begins);
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
    }

    @Test
    @DisplayName("티켓 저장이 실패하면 호출한 결재 트랜잭션도 롤백 경로로 진행한다")
    void rollsBackApprovalOnFailure() {
        FakeDAO dao = new FakeDAO();
        dao.error = new IllegalStateException("저장 실패");
        FakeTransactions transactions = new FakeTransactions();
        TicketService service = proxy(dao, transactions);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions)
                .execute(status -> { create(service); return null; }));
        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
    }
}
