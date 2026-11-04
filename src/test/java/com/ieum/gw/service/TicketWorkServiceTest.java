package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.*;
import static com.ieum.gw.code.TicketStatus.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.service.AssetService;
import com.ieum.gw.service.AssetLookupService;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.EmployeeService;
import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.dto.BranchDetailDTO;
import com.ieum.gw.service.BranchService;
import com.ieum.gw.service.NotificationService;
import com.ieum.gw.dao.TicketWorkDAO;
import com.ieum.gw.dto.TicketCommentDTO;
import com.ieum.gw.dto.TicketHistoryDTO;
import com.ieum.gw.dto.TicketRequestDTO;
import com.ieum.gw.dto.TicketWorkDTO;

/** 실제 Service와 트랜잭션 프록시를 검증한다. Oracle 실행 검증과 구분한다. */
class TicketWorkServiceTest {
    static class Rows extends TicketWorkDAO {
        TicketWorkDTO row;
        int updates, histories, comments, affected = 1;
        boolean failHistory;
        Map<String, Object> lastChange;
        String commentBody;
        List<TicketCommentDTO> commentRows = new ArrayList<>();
        List<TicketHistoryDTO> historyRows = new ArrayList<>();
        Rows() { super(null); }
        @Override public TicketWorkDTO lock(Long companyId, Long id) {
            return row != null && companyId.equals(row.getCompanyId()) && id.equals(row.getId()) ? row : null;
        }
        @Override public List<TicketWorkDTO> selectMine(Long companyId, Long employeeId) {
            return row != null && companyId.equals(row.getCompanyId()) && employeeId.equals(row.getRequesterId()) ? List.of(row) : List.of();
        }
        @Override public List<TicketWorkDTO> selectCompany(Long companyId) {
            return row != null && companyId.equals(row.getCompanyId()) ? List.of(row) : List.of();
        }
        @Override public TicketWorkDTO selectOne(Long companyId, Long id) { return lock(companyId,id); }
        @Override public int insert(TicketWorkDTO value) { row = value; row.setId(99L); return 1; }
        @Override public int update(Map<String, Object> change) { updates++; lastChange = new HashMap<>(change); return affected; }
        @Override public int insertHistory(Map<String, Object> change) {
            if (failHistory) { throw new IllegalStateException("이력 저장 실패"); }
            histories++; return 1;
        }
        @Override public int insertComment(Long companyId, Long id, Long employeeId, String body) {
            assertEquals(1L, companyId); assertEquals(10L, id); assertEquals(2L, employeeId);
            commentBody = body; comments++; return 1;
        }
        @Override public List<TicketCommentDTO> comments(Long companyId, Long id) { return commentRows; }
        @Override public List<TicketHistoryDTO> history(Long companyId, Long id) { return historyRows; }
    }
    static class People extends EmployeeService {
        boolean active = true, role = true;
        List<Integer> batches = new ArrayList<>();
        @Override public boolean isActiveEmployee(Long company, Long employee) { return company == 1L && active; }
        @Override public boolean hasAnyRole(Long company, Long employee, String... roles) {
            assertArrayEquals(new String[]{"ROLE_IT", "ROLE_ADMIN"}, roles); return role;
        }
        @Override public Map<Long, String> getEmployeeNames(Long company, List<Long> ids) {
            assertEquals(1L, company); assertFalse(ids.contains(null)); batches.add(ids.size());
            Map<Long, String> names = new HashMap<>(); ids.forEach(id -> names.put(id, "직원" + id)); return names;
        }
        @Override public Map<Long, com.ieum.gw.dto.EmployeeOptionDTO> getEmployeeNamesWithDept(Long company, List<Long> ids) {
            assertEquals(1L, company);
            Map<Long, com.ieum.gw.dto.EmployeeOptionDTO> result = new HashMap<>();
            for (Long id : ids) {
                var person = new com.ieum.gw.dto.EmployeeOptionDTO();
                person.setEmployeeId(id);
                person.setEmployeeName("직원" + id);
                person.setDepartmentName("IT지원팀");
                result.put(id, person);
            }
            return result;
        }
        @Override public Map<Long, String> getAccountNames(Long company, List<Long> ids) {
            Map<Long, String> names = new HashMap<>(); ids.forEach(id -> names.put(id, "계정" + id)); return names;
        }
        @Override public List<com.ieum.gw.dto.EmployeeOptionDTO> listAssignableEmployees(Long company, String... roles) {
            assertArrayEquals(new String[]{"ROLE_IT","ROLE_ADMIN"},roles);
            var p=new com.ieum.gw.dto.EmployeeOptionDTO();
            p.setEmployeeId(3L); p.setEmployeeName("담당자"); p.setDepartmentName("IT지원팀");
            return List.of(p);
        }
    }
    static class Notices extends NotificationService {
        int assigned, resolved;
        boolean fail;
        Notices() { super(null); }
        @Override public void notifyTicketAssigned(Long c, Long e, Long t, String title) {
            if (fail) { throw new IllegalStateException("알림 실패"); } assigned++;
        }
        @Override public void notifyTicketResolved(Long c, Long e, Long t, String title) {
            assertEquals(2L, e); if (fail) { throw new IllegalStateException("알림 실패"); } resolved++;
        }
    }
    Rows rows;
    People people;
    Notices notices;
    TicketWorkService service;
    AssetDTO asset;
    com.ieum.gw.dto.ApprovedEquipmentDTO approvedEquipment;
    boolean stockConflict;
    int equipmentIssues;

    public static LoginUser user(Long company, Long employee, String role) {
        AuthEmpDTO auth = new AuthEmpDTO();
        auth.setCompanyId(company); auth.setEmployeeId(employee);
        auth.setUserAccountId(employee + 100); auth.setRole(role); auth.setStatus("ACTIVE");
        return new LoginUser(auth);
    }

    @BeforeEach void setup() {
        rows = new Rows(); people = new People(); notices = new Notices();
        asset = new AssetDTO(); asset.setAssetId(50L); asset.setAssetName("노트북"); asset.setBranchId(8L);
        AssetService assets = new AssetService(null) {
            @Override public List<AssetDTO> getMyAssets(LoginUser user) { return List.of(asset); }
        };
        AssetLookupService lookup = new AssetLookupService(null) {
            @Override public Map<Long, AssetDTO> getSummaries(Long c, List<Long> ids) { return Map.of(50L, asset); }
        };
        BranchService branches = new BranchService(null) {
            @Override public List<BranchDTO> getBranches(Long c) {
                BranchDTO b = new BranchDTO(); b.setBranchId(8L); return List.of(b);
            }
            @Override public List<BranchDetailDTO> getBranchDetails(Long c) {
                BranchDetailDTO b = new BranchDetailDTO(); b.setBranchId(8L); b.setBranchName("본사"); return List.of(b);
            }
        };
        service = new TicketWorkService(rows, assets, lookup, branches, people, notices,
                new ApprovalService(null, null, null, null, null, null, null) {
                    @Override public com.ieum.gw.dto.ApprovedEquipmentDTO getApprovedEquipmentInfo(Long companyId, Long documentId) {
                        if (approvedEquipment != null) {
                            assertEquals(1L, companyId);
                            assertEquals(70L, documentId);
                        }
                        return approvedEquipment;
                    }
                }, new AssetAllocationService(null, null, null) {
                    @Override public Long issueForTicket(LoginUser operator, Long receiverId, Long ticketId,
                                                         Long assetId, String approvedType, Long branchId) {
                        assertEquals(1L, operator.getCompanyId());
                        assertEquals(2L, receiverId);
                        assertEquals(10L, ticketId);
                        assertEquals(50L, assetId);
                        assertEquals("AT_LAPTOP", approvedType);
                        assertEquals(8L, branchId);
                        if (stockConflict) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 지급된 자산입니다.");
                        }
                        equipmentIssues++;
                        return 100L;
                    }
                });
        rows.row = ticket(RECEIVED.name());
    }
    private TicketWorkDTO ticket(String status) {
        TicketWorkDTO t = new TicketWorkDTO();
        t.setId(10L); t.setCompanyId(1L); t.setRequesterId(2L); t.setBranchId(8L); t.setAssetId(50L);
        t.setType("TK_INCIDENT"); t.setStatus(status); t.setTitle("장애"); t.setBody("내용"); t.setVersion(0L);
        t.setAt(LocalDateTime.of(2026,11,3,10,0,0));
        return t;
    }

    private TicketRequestDTO equipmentResolution() {
        rows.row.setType("TK_EQUIPMENT");
        rows.row.setStatus(IN_PROGRESS.name());
        rows.row.setAssigneeId(3L);
        rows.row.setAssetId(null);
        rows.row.setEquipmentDocumentId(70L);
        approvedEquipment = new com.ieum.gw.dto.ApprovedEquipmentDTO();
        approvedEquipment.setAssetType("AT_LAPTOP");
        approvedEquipment.setQuantity(1);
        TicketRequestDTO request = form();
        request.setAssetId(50L);
        return request;
    }

    @Test
    @DisplayName("승인 장비 지급 완료 시 티켓에 지급 자산과 완료 상태를 기록한다")
    void resolveApprovedEquipment() {
        service.resolve(user(1L, 3L, "ROLE_IT"), 10L, equipmentResolution());
        assertEquals(1, equipmentIssues);
        assertEquals(50L, rows.lastChange.get("assetId"));
        assertEquals(RESOLVED.name(), rows.lastChange.get("afterStatus"));
        assertEquals(1, rows.histories);
        assertEquals(1, notices.resolved);
    }

    @Test
    @DisplayName("지급 재고 충돌 시 티켓 상태·이력·알림을 변경하지 않는다")
    void stockConflictLeavesTicketInProgress() {
        TicketRequestDTO request = equipmentResolution();
        stockConflict = true;
        status(HttpStatus.CONFLICT, () -> service.resolve(user(1L, 3L, "ROLE_IT"), 10L, request));
        assertEquals(IN_PROGRESS.name(), rows.row.getStatus());
        assertNull(rows.row.getAssetId());
        assertEquals(0, rows.updates);
        assertEquals(0, rows.histories);
        assertEquals(0, notices.resolved);
    }
    private TicketRequestDTO form() {
        TicketRequestDTO f = new TicketRequestDTO(); f.setVersion(0L); f.setResult("해결했습니다"); f.setReason("반려 사유");
        f.setBody("댓글"); f.setType("TK_INCIDENT"); f.setTitle(" 장애 "); f.setBranchId(8L);
        return f;
    }
    private void status(HttpStatus expected, Runnable work) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, work::run).getStatus());
    }

    @Test @DisplayName("접수는 인증 업체·요청자를 저장하고 숫자 ID를 반환한다")
    void create() {
        TicketRequestDTO f = form(); f.setAssetId(50L);
        assertEquals(99L, service.create(user(1L,2L,"ROLE_EMP"), f));
        assertEquals(1L, rows.row.getCompanyId()); assertEquals(2L, rows.row.getRequesterId());
        assertEquals("장애", rows.row.getTitle()); assertEquals(RECEIVED.name(), rows.row.getStatus());
        assertEquals(0, rows.histories);
    }
    @Test @DisplayName("장비 지급 직접 접수와 없는 지점·남의 자산·빈 본문은 거절한다")
    void invalidCreate() {
        TicketRequestDTO f = form(); f.setType("TK_EQUIPMENT");
        status(HttpStatus.BAD_REQUEST, () -> service.create(user(1L,2L,"ROLE_EMP"),f));
        f.setType("TK_INCIDENT"); f.setBranchId(9L);
        status(HttpStatus.BAD_REQUEST, () -> service.create(user(1L,2L,"ROLE_EMP"),f));
        f.setBranchId(8L); f.setAssetId(999L);
        status(HttpStatus.BAD_REQUEST, () -> service.create(user(1L,2L,"ROLE_EMP"),f));
        f.setAssetId(null); f.setBody(" ");
        status(HttpStatus.BAD_REQUEST, () -> service.create(user(1L,2L,"ROLE_EMP"),f));
    }
    @Test @DisplayName("IT 담당자 자기 배정은 버전 조건 갱신·이력·알림을 기록한다")
    void assignSelf() {
        service.assign(user(1L,3L,"ROLE_IT"),10L,form());
        assertEquals(ASSIGNED.name(), rows.lastChange.get("afterStatus"));
        assertEquals(3L, rows.lastChange.get("afterAssigneeId"));
        assertEquals(103L, rows.lastChange.get("actorAccountId"));
        assertEquals(1, rows.histories); assertEquals(1, notices.assigned);
    }
    @Test @DisplayName("일반 직원 배정과 IT 담당자의 타인 지정·재배정은 거절한다")
    void denyAssign() {
        status(HttpStatus.FORBIDDEN, () -> service.assign(user(1L,2L,"ROLE_EMP"),10L,form()));
        TicketRequestDTO f = form(); f.setAssigneeId(4L);
        status(HttpStatus.FORBIDDEN, () -> service.assign(user(1L,3L,"ROLE_IT"),10L,f));
        rows.row.setAssigneeId(3L); rows.row.setStatus(ASSIGNED.name());
        status(HttpStatus.FORBIDDEN, () -> service.assign(user(1L,3L,"ROLE_IT"),10L,form()));
        assertEquals(0, rows.updates);
    }
    @Test @DisplayName("관리자 재배정은 처리 중 상태를 유지하고 이전·새 담당자를 기록한다")
    void adminReassign() {
        rows.row.setStatus(IN_PROGRESS.name()); rows.row.setAssigneeId(3L);
        TicketRequestDTO f = form(); f.setAssigneeId(4L);
        service.assign(user(1L,5L,"ROLE_ADMIN"),10L,f);
        assertEquals(IN_PROGRESS.name(),rows.lastChange.get("afterStatus"));
        assertEquals(3L,rows.lastChange.get("beforeAssigneeId")); assertEquals(4L,rows.lastChange.get("afterAssigneeId"));
    }
    @Test @DisplayName("배정 저장 직전에 계정 활성 여부와 역할을 다시 확인한다")
    void invalidTarget() {
        people.active=false;
        status(HttpStatus.BAD_REQUEST, () -> service.assign(user(1L,3L,"ROLE_IT"),10L,form()));
        people.active=true; people.role=false;
        status(HttpStatus.BAD_REQUEST, () -> service.assign(user(1L,3L,"ROLE_IT"),10L,form()));
        assertEquals(0, rows.updates);
    }
    @Test @DisplayName("오래된 버전과 조건부 갱신 0건은 409이며 이력·알림을 남기지 않는다")
    void conflicts() {
        rows.row.setVersion(1L);
        status(HttpStatus.CONFLICT, () -> service.assign(user(1L,3L,"ROLE_IT"),10L,form()));
        rows.row.setVersion(0L); rows.affected=0;
        status(HttpStatus.CONFLICT, () -> service.assign(user(1L,3L,"ROLE_IT"),10L,form()));
        assertEquals(0,rows.histories); assertEquals(0,notices.assigned);
    }
    @Test @DisplayName("현재 담당자만 배정 상태에서 처리를 시작하고 접수에서 완료로 건너뛰지 못한다")
    void start() {
        rows.row.setAssigneeId(3L); rows.row.setStatus(ASSIGNED.name());
        status(HttpStatus.FORBIDDEN, () -> service.start(user(1L,4L,"ROLE_IT"),10L,form()));
        service.start(user(1L,3L,"ROLE_IT"),10L,form());
        assertEquals(IN_PROGRESS.name(), rows.lastChange.get("afterStatus"));
        rows.row.setStatus(RECEIVED.name());
        status(HttpStatus.CONFLICT, () -> service.resolve(user(1L,3L,"ROLE_IT"),10L,form()));
    }
    @Test @DisplayName("해결 결과가 있어야 완료하고 요청자에게 완료 알림을 보낸다")
    void resolve() {
        rows.row.setAssigneeId(3L); rows.row.setStatus(IN_PROGRESS.name());
        TicketRequestDTO f=form(); f.setResult(" ");
        status(HttpStatus.BAD_REQUEST, () -> service.resolve(user(1L,3L,"ROLE_IT"),10L,f));
        f.setResult("조치 완료"); service.resolve(user(1L,3L,"ROLE_IT"),10L,f);
        assertEquals(RESOLVED.name(),rows.lastChange.get("afterStatus"));
        assertEquals("조치 완료",rows.lastChange.get("reason")); assertEquals(1,notices.resolved);
    }
    @Test @DisplayName("장비 지급 티켓은 반려하거나 지급 연동 없이 완료할 수 없다")
    void equipmentGuard() {
        rows.row.setType("TK_EQUIPMENT");
        status(HttpStatus.CONFLICT, () -> service.reject(user(1L,3L,"ROLE_IT"),10L,form()));
        rows.row.setStatus(IN_PROGRESS.name()); rows.row.setAssigneeId(3L);
        status(HttpStatus.CONFLICT, () -> service.resolve(user(1L,3L,"ROLE_IT"),10L,form()));
        assertEquals(0,rows.updates);
    }
    @Test @DisplayName("반려는 접수 상태의 일반 티켓에 사유를 남겨 수행한다")
    void reject() {
        service.reject(user(1L,3L,"ROLE_IT"),10L,form());
        assertEquals(REJECTED.name(),rows.lastChange.get("afterStatus"));
        rows.row.setStatus(ASSIGNED.name());
        status(HttpStatus.CONFLICT, () -> service.reject(user(1L,3L,"ROLE_IT"),10L,form()));
    }
    @Test @DisplayName("처리 완료 티켓은 요청자만 확인 종료하고 다른 업체·요청자는 404다")
    void close() {
        rows.row.setStatus(RESOLVED.name());
        status(HttpStatus.NOT_FOUND, () -> service.close(user(1L,8L,"ROLE_ADMIN"),10L,form()));
        status(HttpStatus.NOT_FOUND, () -> service.close(user(9L,2L,"ROLE_EMP"),10L,form()));
        service.close(user(1L,2L,"ROLE_EMP"),10L,form());
        assertEquals(CLOSED.name(),rows.lastChange.get("afterStatus"));
    }
    @Test @DisplayName("댓글 작성자는 인증 정보이며 종료 뒤 댓글과 담당자 아닌 IT의 댓글은 막는다")
    void comment() {
        service.comment(user(1L,2L,"ROLE_EMP"),10L,form(),false);
        assertEquals("댓글",rows.commentBody);
        status(HttpStatus.FORBIDDEN, () -> service.comment(user(1L,3L,"ROLE_IT"),10L,form(),true));
        rows.row.setStatus(CLOSED.name());
        status(HttpStatus.CONFLICT, () -> service.comment(user(1L,2L,"ROLE_EMP"),10L,form(),false));
    }
    @Test @DisplayName("누락 버전·음수 버전·비로그인 요청은 쓰기 전에 거절한다")
    void malformed() {
        TicketRequestDTO f=form(); f.setVersion(null);
        status(HttpStatus.BAD_REQUEST, () -> service.assign(user(1L,3L,"ROLE_IT"),10L,f));
        f.setVersion(-1L);
        status(HttpStatus.BAD_REQUEST, () -> service.assign(user(1L,3L,"ROLE_IT"),10L,f));
        status(HttpStatus.UNAUTHORIZED, () -> service.create(null,form()));
        assertEquals(0,rows.updates);
    }
    @Test @DisplayName("내 티켓 응답은 고정 초 날짜·댓글·계정 이름·담당자 변경 문장·종료 권한을 포함한다")
    void list() {
        rows.row.setStatus(RESOLVED.name());
        TicketCommentDTO c=new TicketCommentDTO(); c.setAuthorEmployeeId(4L); c.setContent("댓글");
        c.setCreatedAt(rows.row.getAt()); rows.commentRows.add(c);
        TicketHistoryDTO h=new TicketHistoryDTO(); h.setBeforeStatus(RECEIVED.name()); h.setAfterStatus(ASSIGNED.name());
        h.setActorAccountId(103L); h.setAfterAssigneeId(3L); h.setCreatedAt(rows.row.getAt()); rows.historyRows.add(h);
        Map<String,Object> item=service.listMine(user(1L,2L,"ROLE_EMP")).get(0);
        assertEquals("2026-11-03T10:00:00",item.get("at")); assertEquals(true,item.get("canClose"));
        assertEquals("본사",item.get("branchName"));
        assertTrue(item.get("comments").toString().contains("직원4"));
        assertTrue(item.get("history").toString().contains("계정103"));
        assertTrue(item.get("history").toString().contains("미배정 → 직원3"));
        assertTrue(service.listMine(user(9L,2L,"ROLE_EMP")).isEmpty());
    }
    @Test @DisplayName("이름 조회는 null·중복 ID를 제거하고 1000개 단위로 나눈다")
    void batches() {
        for(long id=1;id<=1001;id++) {
            TicketCommentDTO c=new TicketCommentDTO(); c.setAuthorEmployeeId(id); c.setContent("댓글"); rows.commentRows.add(c);
        }
        service.listMine(user(1L,2L,"ROLE_EMP"));
        assertEquals(List.of(1000,1),people.batches);
    }

    @Test @DisplayName("관리자 목록 카드 건수와 항목은 같은 업체·필터 조건을 사용한다")
    void adminList() {
        var result=service.listAdmin(user(1L,3L,"ROLE_IT"),"UNASSIGNED");
        assertEquals(1L,((Map<?,?>)result.get("counts")).get("UNASSIGNED"));
        assertEquals(1,((List<?>)result.get("items")).size());
        rows.row.setAssigneeId(3L); rows.row.setStatus(IN_PROGRESS.name());
        result=service.listAdmin(user(1L,3L,"ROLE_IT"),"MINE");
        assertEquals(1L,((Map<?,?>)result.get("counts")).get("MINE"));
        assertEquals(0L,((Map<?,?>)result.get("counts")).get("UNASSIGNED"));
        status(HttpStatus.BAD_REQUEST,()->service.listAdmin(user(1L,3L,"ROLE_IT"),"UNKNOWN"));
        status(HttpStatus.FORBIDDEN,()->service.listAdmin(user(1L,2L,"ROLE_EMP"),"ALL"));
        assertTrue(((List<?>)service.listAdmin(user(9L,3L,"ROLE_IT"),"ALL").get("items")).isEmpty());
    }
    @Test @DisplayName("관리자 상세는 지점·권한 버튼과 이름·부서로 바꾼 담당자 후보를 반환한다")
    void adminDetail() {
        var result=service.detailAdmin(user(1L,5L,"ROLE_ADMIN"),10L);
        var action=(Map<?,?>)result.get("actions");
        assertEquals(true,action.get("assign")); assertEquals(true,action.get("assignSelf"));
        assertEquals(false,action.get("resolve"));
        var option=(Map<?,?>)((List<?>)result.get("assignees")).get(0);
        assertEquals("담당자",option.get("name")); assertEquals("IT지원팀",option.get("dept"));
        rows.row.setAssigneeId(3L); rows.row.setStatus(IN_PROGRESS.name());
        action=(Map<?,?>)service.detailAdmin(user(1L,4L,"ROLE_IT"),10L).get("actions");
        assertEquals(false,action.get("resolve")); assertEquals(false,action.get("comment"));
        action=(Map<?,?>)service.detailAdmin(user(1L,3L,"ROLE_IT"),10L).get("actions");
        assertEquals(true,action.get("resolve")); assertEquals(true,action.get("comment"));
        status(HttpStatus.NOT_FOUND,()->service.detailAdmin(user(9L,3L,"ROLE_IT"),10L));
        rows.row.setType("TK_EQUIPMENT");
        status(HttpStatus.CONFLICT,()->service.detailAdmin(user(1L,3L,"ROLE_IT"),10L));
    }

    static class Transactions extends AbstractPlatformTransactionManager {
        boolean rolledBack, committed;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { committed=true; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack=true; }
    }
    private TicketWorkService proxy(Transactions tx) {
        ProxyFactory proxy=new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        return (TicketWorkService)proxy.getProxy();
    }
    @Test @DisplayName("이력 저장 실패는 실제 트랜잭션 프록시의 롤백으로 전달된다")
    void historyRollback() {
        rows.failHistory=true; Transactions tx=new Transactions();
        assertThrows(IllegalStateException.class,()->proxy(tx).assign(user(1L,3L,"ROLE_IT"),10L,form()));
        assertTrue(tx.rolledBack); assertFalse(tx.committed); assertEquals(0,notices.assigned);
    }
    @Test @DisplayName("알림 저장 실패도 티켓 트랜잭션의 롤백으로 전달된다")
    void noticeRollback() {
        notices.fail=true; Transactions tx=new Transactions();
        assertThrows(IllegalStateException.class,()->proxy(tx).assign(user(1L,3L,"ROLE_IT"),10L,form()));
        assertTrue(tx.rolledBack); assertFalse(tx.committed);
    }
}
