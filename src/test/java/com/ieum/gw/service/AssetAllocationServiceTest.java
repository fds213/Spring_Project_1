package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.dao.AssetAllocationDAO;
import com.ieum.gw.dto.AssetAllocationDTO;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.service.BranchService;

class AssetAllocationServiceTest {
    static class Rows extends AssetAllocationDAO {
        AssetAllocationDTO row;
        List<Long> open=List.of();
        int changed, assigned, history, returned;
        int affected=1;
        boolean failHistory;
        Rows() { super(null); }
        @Override public AssetAllocationDTO lock(Long company,Long asset) {
            return company==1L && asset==10L ? row : null;
        }
        @Override public List<Long> openAssignments(Long company,Long asset) { return open; }
        @Override public int issue(AssetAllocationDTO r) { changed++; return affected; }
        @Override public int insertAssignment(AssetAllocationDTO r) { assigned++; r.setAssignmentId(100L); return 1; }
        @Override public int insertIssueHistory(AssetAllocationDTO r) { history++; return 1; }
        @Override public int returnAssignment(AssetAllocationDTO r) { returned++; return affected; }
        @Override public int release(AssetAllocationDTO r) { changed++; return affected; }
        @Override public int insertReturnHistory(AssetAllocationDTO r) {
            if(failHistory)throw new IllegalStateException("회수 이력 실패"); history++; return 1;
        }
        @Override public List<AssetDTO> stock(Long company,String type,String status) {
            assertEquals(1L,company); assertEquals("AT_LAPTOP",type); assertEquals("AS_AVAILABLE",status); return List.of();
        }
    }
    Rows rows;
    AssetAllocationService service;
    LoginUser operator;
    @BeforeEach void setup() {
        rows=new Rows();
        rows.row=new AssetAllocationDTO(); rows.row.setCompanyId(1L); rows.row.setAssetId(10L);
        rows.row.setVersion(100L); rows.row.setAssetType("AT_LAPTOP"); rows.row.setStatus("AS_AVAILABLE"); rows.row.setBranchId(8L);
        BranchService branches=new BranchService(null) {
            @Override public List<BranchDTO> getBranches(Long company) {
                BranchDTO b=new BranchDTO(); b.setBranchId(9L); return List.of(b);
            }
        };
        service=new AssetAllocationService(rows,branches,new EmployeeService() {
            @Override public boolean isActiveEmployee(Long company, Long employee) {
                return company == 1L && employee == 2L;
            }
        });
        AuthEmpDTO auth=new AuthEmpDTO(); auth.setCompanyId(1L); auth.setEmployeeId(3L);
        auth.setUserAccountId(30L); auth.setRole("ROLE_IT"); operator=new LoginUser(auth);
    }
    @Test
    @DisplayName("관리 지급은 version 누락·불일치와 타 업체 사원을 쓰기 전에 거절한다")
    void checksAdminAssignment() {
        status(400, () -> service.assign(operator, 10L, 2L, null));
        status(409, () -> service.assign(operator, 10L, 2L, 99L));
        status(400, () -> service.assign(operator, 10L, 3L, 100L));
        assertEquals(0, rows.changed);
        service.assign(operator, 10L, 2L, 100L);
        assertEquals(1, rows.changed);
        assertNull(rows.row.getTicketId());
    }

    private Long issue(String type) { return service.issueForTicket(operator,2L,20L,10L,type,9L); }
    private void status(int expected,Runnable run) {
        assertEquals(expected,assertThrows(ResponseStatusException.class,run::run).getRawStatusCode());
    }
    @Test @DisplayName("승인 종류와 일치하는 재고 지급은 이동 지점·수령인·티켓·처리 계정과 이력을 저장한다")
    void issue() {
        assertEquals(100L,issue("AT_LAPTOP"));
        assertEquals(1,rows.changed); assertEquals(1,rows.assigned); assertEquals(1,rows.history);
        assertEquals(8L,rows.row.getBranchId()); assertEquals(9L,rows.row.getTargetBranchId());
        assertEquals(30L,rows.row.getActorAccountId()); assertEquals(2L,rows.row.getEmployeeId());
        assertEquals(20L,rows.row.getTicketId()); assertEquals("AS_IN_USE",rows.row.getTargetStatus());
        assertNotNull(rows.row.getNote());
    }
    @Test @DisplayName("이미 지급된 자산과 지급 이력이 남은 자산은 다시 지급하지 않는다")
    void alreadyIssued() {
        rows.row.setStatus("AS_IN_USE"); status(409,()->issue("AT_LAPTOP"));
        rows.row.setStatus("AS_AVAILABLE"); rows.open=List.of(100L); status(409,()->issue("AT_LAPTOP"));
        assertEquals(0,rows.changed);
    }
    @Test @DisplayName("다른 종류·잘못된 수령 지점과 다른 업체 자산 지급은 거절한다")
    void invalid() {
        status(400,()->issue("AT_MONITOR"));
        status(400,()->service.issueForTicket(operator,2L,20L,10L,"AT_LAPTOP",99L));
        status(404,()->service.issueForTicket(operator,2L,20L,999L,"AT_LAPTOP",9L));
        assertEquals(0,rows.assigned);
    }
    @Test @DisplayName("조건부 자산 갱신이 0건이면 지급 이력을 만들지 않고 409다")
    void conflict() {
        rows.affected=0; status(409,()->issue("AT_LAPTOP"));
        assertEquals(0,rows.assigned); assertEquals(0,rows.history);
    }
    @Test @DisplayName("현재 지급 건만 회수하고 사용 가능 상태·회수 계정·사유 이력을 남긴다")
    void returned() {
        rows.row.setStatus("AS_IN_USE"); rows.open=List.of(100L);
        service.returnAsset(operator,10L,100L," 회수 사유 ");
        assertEquals(1,rows.returned); assertEquals(1,rows.changed); assertEquals(1,rows.history);
        assertEquals("AS_AVAILABLE",rows.row.getTargetStatus()); assertEquals("회수 사유",rows.row.getNote());
    }
    @Test @DisplayName("중복 회수·이전 지급 건 회수·빈 회수 사유는 거절한다")
    void invalidReturn() {
        status(409,()->service.returnAsset(operator,10L,100L,"사유"));
        rows.row.setStatus("AS_IN_USE"); rows.open=List.of(101L); rows.row.setVersion(101L);
        status(409,()->service.returnAsset(operator,10L,100L,"사유"));
        status(400,()->service.returnAsset(operator,10L,101L," "));
        assertEquals(0,rows.returned);
    }
    @Test @DisplayName("재고 조회도 인증 역할과 승인 장비 종류를 검사한다")
    void stock() {
        assertTrue(service.getStock(operator,"AT_LAPTOP").isEmpty());
        status(401,()->service.getStock(null,"AT_LAPTOP"));
        status(400,()->service.getStock(operator,"UNKNOWN"));
        AuthEmpDTO a=new AuthEmpDTO(); a.setCompanyId(1L); a.setEmployeeId(2L); a.setUserAccountId(20L); a.setRole("ROLE_EMP");
        status(403,()->service.getStock(new LoginUser(a),"AT_LAPTOP"));
    }
    static class Transactions extends AbstractPlatformTransactionManager {
        boolean rolledBack;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx,TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack=true; }
    }
    @Test @DisplayName("티켓 트랜잭션이 없는 독립 자산 지급은 프록시가 거절한다")
    void mandatory() {
        ProxyFactory factory=new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(new Transactions(),new AnnotationTransactionAttributeSource()));
        AssetAllocationService proxy=(AssetAllocationService)factory.getProxy();
        assertThrows(IllegalTransactionStateException.class,()->proxy.issueForTicket(operator,2L,20L,10L,"AT_LAPTOP",9L));
        assertEquals(0,rows.changed);
    }
    @Test @DisplayName("회수 이력 실패는 자산·지급 이력 변경을 포함한 트랜잭션의 롤백으로 전달된다")
    void rollback() {
        rows.row.setStatus("AS_IN_USE"); rows.open=List.of(100L); rows.failHistory=true;
        Transactions tx=new Transactions();
        ProxyFactory factory=new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        AssetAllocationService proxy=(AssetAllocationService)factory.getProxy();
        assertThrows(IllegalStateException.class,()->proxy.returnAsset(operator,10L,100L,"사유"));
        assertTrue(tx.rolledBack);
    }
}
