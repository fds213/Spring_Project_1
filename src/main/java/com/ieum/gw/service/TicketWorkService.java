package com.ieum.gw.service;

import static com.ieum.gw.code.TicketStatus.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.service.AssetService;
import com.ieum.gw.service.AssetLookupService;
import com.ieum.gw.code.TicketStatus;
import com.ieum.gw.code.TicketType;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.ApprovedEquipmentDTO;
import com.ieum.gw.dto.EmployeeOptionDTO;
import com.ieum.gw.service.EmployeeService;
import com.ieum.gw.dto.BranchDetailDTO;
import com.ieum.gw.service.BranchService;
import com.ieum.gw.service.NotificationService;
import com.ieum.gw.dao.TicketWorkDAO;
import com.ieum.gw.dto.TicketCommentDTO;
import com.ieum.gw.dto.TicketHistoryDTO;
import com.ieum.gw.dto.TicketRequestDTO;
import com.ieum.gw.dto.TicketWorkDTO;
import lombok.RequiredArgsConstructor;

/** 접수 선택 목록(form-options)과 분리한 티켓 업무. 상태·이력·DB 알림은 한 트랜잭션이다. */
@Service
@RequiredArgsConstructor
public class TicketWorkService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private final TicketWorkDAO dao;
    private final AssetService assetService;
    private final AssetLookupService assetLookupService;
    private final BranchService branchService;
    private final EmployeeService employeeService;
    private final NotificationService notificationService;
    private final ApprovalService approvalService;
    private final AssetAllocationService assetAllocationService;

    @Transactional
    public Long create(LoginUser user, TicketRequestDTO form) {
        requireLogin(user);
        requireForm(form);
        TicketType type;
        try { type = TicketType.from(form.getType()); }
        catch (IllegalArgumentException e) { throw bad("티켓 구분이 올바르지 않습니다."); }
        if (type != TicketType.TK_INCIDENT && type != TicketType.TK_QUESTION) {
            throw bad("장비 지급은 장비 신청 결재를 통해 접수해 주세요.");
        }
        String title = text(form.getTitle(), 60, "제목");
        String body = text(form.getBody(), 1000, "내용");
        requireId(form.getBranchId());
        if (branchService.getBranches(user.getCompanyId()).stream()
                .noneMatch(b -> form.getBranchId().equals(b.getBranchId()))) {
            throw bad("사용 중인 같은 업체의 지점을 선택해 주세요.");
        }
        if (form.getAssetId() != null) {
            requireId(form.getAssetId());
            if (assetService.getMyAssets(user).stream().noneMatch(a -> form.getAssetId().equals(a.getAssetId()))) {
                throw bad("본인에게 현재 지급된 자산만 선택할 수 있습니다.");
            }
        }
        TicketWorkDTO row = new TicketWorkDTO();
        row.setCompanyId(user.getCompanyId());
        row.setRequesterId(user.getEmployeeId());
        row.setBranchId(form.getBranchId());
        row.setAssetId(form.getAssetId());
        row.setType(type.name());
        row.setStatus(RECEIVED.name());
        row.setTitle(title);
        row.setBody(body);
        if (dao.insert(row) != 1 || row.getId() == null) { throw new IllegalStateException("티켓 저장 결과를 확인할 수 없습니다."); }
        return row.getId();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMine(LoginUser user) {
        requireLogin(user);
        List<TicketWorkDTO> rows = dao.selectMine(user.getCompanyId(), user.getEmployeeId());
        if (rows.isEmpty()) { return List.of(); }
        Map<Long, BranchDetailDTO> branches = branchService.getBranchDetails(user.getCompanyId()).stream()
                .collect(Collectors.toMap(BranchDetailDTO::getBranchId, Function.identity()));
        Map<Long, AssetDTO> assets = assetLookupService.getSummaries(user.getCompanyId(),
                rows.stream().map(TicketWorkDTO::getAssetId).collect(Collectors.toList()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (TicketWorkDTO row : rows) {
            result.add(item(user, row, branches, assets));
        }
        return result;
    }

    private Map<String, Object> item(LoginUser user, TicketWorkDTO row, Map<Long, BranchDetailDTO> branches, Map<Long, AssetDTO> assets) {
            // 본인 목록 SQL로 접근 범위를 확정한 뒤에만 댓글·이력과 표시용 이름을 읽는다.
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("type", row.getType());
            item.put("title", row.getTitle());
            item.put("body", row.getBody());
            BranchDetailDTO branch = branches.get(row.getBranchId());
            item.put("branchName", branch == null ? null : branch.getBranchName());
            item.put("status", row.getStatus());
            item.put("at", time(row.getAt()));
            AssetDTO asset = assets.get(row.getAssetId());
            Map<String, Object> label = null;
            if (asset != null) {
                label = new LinkedHashMap<>();
                label.put("id", asset.getAssetId());
                label.put("name", asset.getAssetName());
                label.put("serial", asset.getSerialNo());
            }
            item.put("asset", label);
            List<TicketCommentDTO> comments = dao.comments(user.getCompanyId(), row.getId());
            List<TicketHistoryDTO> history = dao.history(user.getCompanyId(), row.getId());
            List<Long> employeeIds = new ArrayList<>();
            comments.forEach(c -> employeeIds.add(c.getAuthorEmployeeId()));
            history.forEach(h -> { employeeIds.add(h.getBeforeAssigneeId()); employeeIds.add(h.getAfterAssigneeId()); });
            Map<Long, String> employeeNames = names(user.getCompanyId(), employeeIds, false);
            Map<Long, String> accountNames = names(user.getCompanyId(),
                    history.stream().map(TicketHistoryDTO::getActorAccountId).collect(Collectors.toList()), true);
            item.put("comments", comments.stream().map(c -> Map.of(
                    "at", time(c.getCreatedAt()), "name", name(employeeNames, c.getAuthorEmployeeId()), "body", c.getContent()))
                    .collect(Collectors.toList()));
            item.put("history", history.stream().map(h -> Map.of(
                    "at", time(h.getCreatedAt()), "name", name(accountNames, h.getActorAccountId()),
                    "text", historyText(h, employeeNames))).collect(Collectors.toList()));
            item.put("canComment", !terminal(row));
            item.put("canClose", RESOLVED.name().equals(row.getStatus()));
            // 본인이 접수한 장애·문의 중 접수 상태인 건만 취소할 수 있다.
            item.put("canCancel", user.getEmployeeId().equals(row.getRequesterId())
            						&& RECEIVED.name().equals(row.getStatus())
            						&& (TicketType.TK_INCIDENT.name().equals(row.getType())
            							|| TicketType.TK_QUESTION.name().equals(row.getType())));
            item.put("version", row.getVersion());
            return item;
    }

    /** IT·관리자는 같은 업체 목록을 읽되 변경은 각 처리 메서드에서 다시 제한한다. */
    @Transactional(readOnly = true)
    public Map<String, Object> listAdmin(LoginUser user, String filter) {
        requireOperator(user);
        String wanted = filter == null || filter.isBlank() ? "UNASSIGNED" : filter;
        List<String> filters = List.of("UNASSIGNED", "MINE", "IN_PROGRESS", "RESOLVED", "ALL");
        if (!filters.contains(wanted)) { throw bad("목록 조건이 올바르지 않습니다."); }
        List<TicketWorkDTO> rows = dao.selectCompany(user.getCompanyId());
        Map<Long, EmployeeOptionDTO> names = people(user.getCompanyId(), rows.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getRequesterId(), r.getAssigneeId())).collect(Collectors.toList()));
        Map<Long, BranchDetailDTO> branches = branchService.getBranchDetails(user.getCompanyId()).stream()
                .collect(Collectors.toMap(BranchDetailDTO::getBranchId, Function.identity()));
        Map<String, Long> counts = new LinkedHashMap<>();
        filters.forEach(f -> counts.put(f, rows.stream().filter(r -> matches(r, user, f)).count()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (TicketWorkDTO row : rows) {
            if (!matches(row, user, wanted)) { continue; }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId()); item.put("type", row.getType()); item.put("title", row.getTitle());
            item.put("status", row.getStatus()); item.put("at", time(row.getAt()));
            item.put("requester", person(row.getRequesterId(), names));
            item.put("assignee", row.getAssigneeId() == null ? null : person(row.getAssigneeId(), names));
            BranchDetailDTO branch = branches.get(row.getBranchId());
            item.put("branchName", branch == null ? null : branch.getBranchName());
            items.add(item);
        }
        return Map.of("counts", counts, "items", items);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detailAdmin(LoginUser user, Long id) {
        requireOperator(user); requireId(id);
        TicketWorkDTO row = dao.selectOne(user.getCompanyId(), id);
        if (row == null) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "티켓을 찾을 수 없습니다."); }
        ApprovedEquipmentDTO equipment = TicketType.TK_EQUIPMENT.name().equals(row.getType())
                ? approvedEquipment(user, row) : null;
        Map<Long, BranchDetailDTO> branches = branchService.getBranchDetails(user.getCompanyId()).stream()
                .collect(Collectors.toMap(BranchDetailDTO::getBranchId, Function.identity()));
        Map<Long, AssetDTO> assets = assetLookupService.getSummaries(user.getCompanyId(), java.util.Arrays.asList(row.getAssetId()));
        Map<String, Object> result = item(user, row, branches, assets);
        Map<Long, EmployeeOptionDTO> names = people(user.getCompanyId(), java.util.Arrays.asList(row.getRequesterId(), row.getAssigneeId()));
        result.put("requester", person(row.getRequesterId(), names));
        result.put("assignee", row.getAssigneeId() == null ? null : person(row.getAssigneeId(), names));
        BranchDetailDTO branch = branches.get(row.getBranchId());
        Map<String, Object> location = null;
        if (branch != null) {
            location = new LinkedHashMap<>();
            location.put("name", branch.getBranchName()); location.put("address", branch.getAddress()); location.put("detail", branch.getDetailAddress());
        }
        result.put("branch", location);
        boolean handler = admin(user) || user.getEmployeeId().equals(row.getAssigneeId());
        result.put("actions", Map.of(
            "assignSelf", RECEIVED.name().equals(row.getStatus()) && row.getAssigneeId() == null,
            "assign", admin(user) && !terminal(row),
            "start", handler && ASSIGNED.name().equals(row.getStatus()),
            "resolve", handler && IN_PROGRESS.name().equals(row.getStatus()),
            "reject", equipment == null && RECEIVED.name().equals(row.getStatus()),
            "comment", handler && !terminal(row)));
        List<Map<String, Object>> assignees = new ArrayList<>();
        if (admin(user) && !terminal(row)) {
            employeeService.listAssignableEmployees(user.getCompanyId(), "ROLE_IT", "ROLE_ADMIN").forEach(p -> {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("employeeId", p.getEmployeeId()); option.put("name", p.getEmployeeName()); option.put("dept", p.getDepartmentName());
                assignees.add(option);
            });
        }
        result.put("assignees", assignees);
        result.put("stock", equipment != null && handler && IN_PROGRESS.name().equals(row.getStatus())
                ? assetAllocationService.getStock(user, equipment.getAssetType()).stream()
                        .map(TicketWorkService::assetOption).collect(Collectors.toList()) : List.of());
        result.put("equipmentKind", equipment == null ? null : equipment.getAssetType());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myEquipment(LoginUser user) {
        requireLogin(user);
        Map<Long, String> branches = branchService.getBranchDetails(user.getCompanyId()).stream()
                .collect(Collectors.toMap(BranchDetailDTO::getBranchId, BranchDetailDTO::getBranchName));
        List<Map<String, Object>> items = new ArrayList<>();
        for (AssetDTO asset : assetService.getMyAssets(user)) {
            Map<String, Object> item = assetOption(asset);
            item.put("branchName", branches.get(asset.getBranchId()));
            item.put("assignedAt", time(asset.getIssuedAt()));
            item.put("ticketId", asset.getItTicketId());
            items.add(item);
        }
        List<Map<String, Object>> waiting = new ArrayList<>();
        for (TicketWorkDTO row : dao.selectMine(user.getCompanyId(), user.getEmployeeId())) {
            if (!TicketType.TK_EQUIPMENT.name().equals(row.getType()) || row.getAssetId() != null
                    || !List.of(RECEIVED.name(), ASSIGNED.name(), IN_PROGRESS.name()).contains(row.getStatus())) {
                continue;
            }
            ApprovedEquipmentDTO equipment = approvedEquipment(user, row);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("kind", equipment.getAssetType());
            item.put("qty", equipment.getQuantity());
            item.put("branchName", branches.get(row.getBranchId()));
            item.put("status", row.getStatus());
            waiting.add(item);
        }
        return Map.of("items", items, "waiting", waiting);
    }

    private ApprovedEquipmentDTO approvedEquipment(LoginUser user, TicketWorkDTO row) {
        ApprovedEquipmentDTO equipment = approvalService.getApprovedEquipmentInfo(
                user.getCompanyId(), row.getEquipmentDocumentId());
        if (equipment == null || !Integer.valueOf(1).equals(equipment.getQuantity())) {
            throw conflict("승인된 장비 신청 정보를 확인해 주세요.");
        }
        return equipment;
    }

    private static Map<String, Object> assetOption(AssetDTO asset) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("id", asset.getAssetId());
        option.put("name", asset.getAssetName());
        option.put("serial", asset.getSerialNo());
        return option;
    }

    private static boolean matches(TicketWorkDTO row, LoginUser user, String filter) {
        switch (filter) {
            case "UNASSIGNED": return RECEIVED.name().equals(row.getStatus()) && row.getAssigneeId() == null;
            case "MINE": return user.getEmployeeId().equals(row.getAssigneeId()) && !terminal(row);
            case "IN_PROGRESS": return IN_PROGRESS.name().equals(row.getStatus());
            case "RESOLVED": return RESOLVED.name().equals(row.getStatus());
            default: return true;
        }
    }

    private Map<Long, EmployeeOptionDTO> people(Long companyId, List<Long> source) {
        List<Long> ids = source.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, EmployeeOptionDTO> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i += 1000) {
            result.putAll(employeeService.getEmployeeNamesWithDept(companyId,
                    ids.subList(i, Math.min(i + 1000, ids.size()))));
        }
        return result;
    }

    private static Map<String, Object> person(Long employeeId, Map<Long, EmployeeOptionDTO> people) {
        EmployeeOptionDTO employee = people.get(employeeId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", employeeId);
        result.put("name", employee == null ? null : employee.getEmployeeName());
        result.put("dept", employee == null ? null : employee.getDepartmentName());
        return result;
    }

    @Transactional
    public void comment(LoginUser user, Long id, TicketRequestDTO form, boolean adminRoute) {
        requireLogin(user);
        requireForm(form);
        String body = text(form.getBody(), 300, "댓글");
        TicketWorkDTO row = locked(user, id);
        if (adminRoute) {
            requireOperator(user);
            requireHandler(user, row);
        } else { requireOwner(user, row); }
        if (terminal(row)) { throw conflict("종료·취소·반려된 티켓에는 댓글을 남길 수 없습니다."); }
        if (dao.insertComment(user.getCompanyId(), id, user.getEmployeeId(), body) != 1) {
            throw new IllegalStateException("댓글 저장 결과를 확인할 수 없습니다.");
        }
    }

    @Transactional
    public void close(LoginUser user, Long id, TicketRequestDTO form) {
        requireLogin(user);
        TicketWorkDTO row = locked(user, id);
        requireOwner(user, row);
        requireVersion(row, form);
        requireState(row, RESOLVED);
        change(user, row, CLOSED, row.getAssigneeId(), "요청자 확인", false);
    }
    
    /** 요청자가 직접 접수한 장애·문의 티켓을 접수 상태에서 취소한다. */
    @Transactional
    public void cancel(LoginUser user, Long id, TicketRequestDTO form) {
    	requireLogin(user);
    	
    	TicketWorkDTO row = locked(user, id);
    	requireOwner(user, row);
    	requireVersion(row, form);
    	requireState(row, RECEIVED);
    	
    	boolean cancellableType = TicketType.TK_INCIDENT.name().equals(row.getType()) || TicketType.TK_QUESTION.name().equals(row.getType());
    	
    	if (!cancellableType) {
			throw conflict("직접 접수한 장애·문의 티켓만 취소할 수 있습니다.");
		}
    	
    	change(user, row, CANCELED, row.getAssigneeId(), "요청자 취소", false);
    }
    
    @Transactional
    public void assign(LoginUser user, Long id, TicketRequestDTO form) {
        requireOperator(user);
        requireForm(form);
        TicketWorkDTO row = locked(user, id);
        if (!admin(user) && (!RECEIVED.name().equals(row.getStatus()) || row.getAssigneeId() != null
                || (form.getAssigneeId() != null && !user.getEmployeeId().equals(form.getAssigneeId())))) {
            throw forbidden("IT 담당자는 미배정 티켓을 본인에게만 배정할 수 있습니다.");
        }
        requireVersion(row, form);
        if (terminal(row)) { throw conflict("종료·취소·반려된 티켓은 배정할 수 없습니다."); }
        Long target = form.getAssigneeId() == null ? user.getEmployeeId() : form.getAssigneeId();
        requireId(target);
        if (!employeeService.isActiveEmployee(user.getCompanyId(), target)
                || !employeeService.hasAnyRole(user.getCompanyId(), target, "ROLE_IT", "ROLE_ADMIN")) {
            throw bad("같은 업체의 사용 중인 IT 담당자 또는 관리자만 배정할 수 있습니다.");
        }
        if (target.equals(row.getAssigneeId())) { return; }
        change(user, row, RECEIVED.name().equals(row.getStatus()) ? ASSIGNED : TicketStatus.from(row.getStatus()),
                target, "담당자 배정", true);
        notificationService.notifyTicketAssigned(user.getCompanyId(), target, id, row.getTitle());
    }

    @Transactional
    public void start(LoginUser user, Long id, TicketRequestDTO form) {
        requireOperator(user);
        TicketWorkDTO row = locked(user, id);
        requireHandler(user, row);
        requireVersion(row, form);
        requireState(row, ASSIGNED);
        change(user, row, IN_PROGRESS, row.getAssigneeId(), "처리 시작", false);
    }

    @Transactional
    public void resolve(LoginUser user, Long id, TicketRequestDTO form) {
        requireOperator(user);
        TicketWorkDTO row = locked(user, id);
        requireHandler(user, row);
        requireVersion(row, form);
        requireState(row, IN_PROGRESS);
        String result = text(form.getResult(), 500, "해결 결과");
        if (TicketType.TK_EQUIPMENT.name().equals(row.getType())) {
            ApprovedEquipmentDTO equipment = approvedEquipment(user, row);
            requireId(form.getAssetId());
            assetAllocationService.issueForTicket(user, row.getRequesterId(), id, form.getAssetId(),
                    equipment.getAssetType(), row.getBranchId());
            row.setAssetId(form.getAssetId());
        } else if (form.getAssetId() != null) {
            throw bad("일반 장애·문의 완료에는 지급 자산을 지정할 수 없습니다.");
        }
        change(user, row, RESOLVED, row.getAssigneeId(), result, false);
        notificationService.notifyTicketResolved(user.getCompanyId(), row.getRequesterId(), id, row.getTitle());
    }

    @Transactional
    public void reject(LoginUser user, Long id, TicketRequestDTO form) {
        requireOperator(user);
        TicketWorkDTO row = locked(user, id);
        requireVersion(row, form);
        requireState(row, RECEIVED);
        if (TicketType.TK_EQUIPMENT.name().equals(row.getType())) { throw conflict("장비 지급 티켓은 반려할 수 없습니다."); }
        change(user, row, REJECTED, row.getAssigneeId(), text(form.getReason(), 300, "반려 사유"), false);
    }

    private void change(LoginUser user, TicketWorkDTO row, TicketStatus after, Long assignee, String reason, boolean assignment) {
        Map<String, Object> values = new HashMap<>();
        values.put("companyId", user.getCompanyId());
        values.put("id", row.getId());
        values.put("version", row.getVersion());
        values.put("assetId", row.getAssetId());
        values.put("actorAccountId", user.getUserAccountId());
        values.put("beforeStatus", row.getStatus());
        values.put("afterStatus", after.name());
        values.put("beforeAssigneeId", row.getAssigneeId());
        values.put("afterAssigneeId", assignee);
        values.put("reason", reason);
        values.put("assignment", assignment);
        values.put("resolved", after == RESOLVED);
        values.put("closed", after == CLOSED);
        if (dao.update(values) != 1) { throw conflict("다른 사람이 먼저 변경했습니다. 새로 불러와 주세요."); }
        if (dao.insertHistory(values) != 1) { throw new IllegalStateException("티켓 이력을 저장하지 못했습니다."); }
    }

    private Map<Long, String> names(Long companyId, List<Long> source, boolean accounts) {
        List<Long> ids = source.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> names = new HashMap<>();
        for (int i = 0; i < ids.size(); i += 1000) {
            List<Long> batch = new ArrayList<>(ids.subList(i, Math.min(ids.size(), i + 1000)));
            names.putAll(accounts ? employeeService.getAccountNames(companyId, batch) : employeeService.getEmployeeNames(companyId, batch));
        }
        return names;
    }

    private static String historyText(TicketHistoryDTO h, Map<Long, String> employees) {
        String status = (h.getBeforeStatus() == null ? "" : TicketStatus.from(h.getBeforeStatus()).getLabel() + " → ")
                + TicketStatus.from(h.getAfterStatus()).getLabel();
        if (!Objects.equals(h.getBeforeAssigneeId(), h.getAfterAssigneeId())) {
            status += " · 담당자 " + name(employees, h.getBeforeAssigneeId()) + " → " + name(employees, h.getAfterAssigneeId());
        }
        return status + (h.getReason() == null || h.getReason().isBlank() ? "" : " · " + h.getReason());
    }

    private static String name(Map<Long, String> names, Long id) { return id == null ? "미배정" : names.getOrDefault(id, "이름 정보 없음"); }
    private static String time(LocalDateTime value) { return value == null ? "" : TIME.format(value); }
    private TicketWorkDTO locked(LoginUser user, Long id) {
        requireId(id);
        TicketWorkDTO row = dao.lock(user.getCompanyId(), id);
        if (row == null) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "티켓을 찾을 수 없습니다."); }
        return row;
    }
    private static void requireOwner(LoginUser user, TicketWorkDTO row) {
        if (!user.getEmployeeId().equals(row.getRequesterId())) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "티켓을 찾을 수 없습니다."); }
    }
    private static void requireHandler(LoginUser user, TicketWorkDTO row) {
        if (!admin(user) && !user.getEmployeeId().equals(row.getAssigneeId())) { throw forbidden("현재 담당자만 처리할 수 있습니다."); }
    }
    private static boolean admin(LoginUser user) { return "ROLE_ADMIN".equals(user.getRole()); }
    private static boolean terminal(TicketWorkDTO row) { return List.of(CLOSED.name(), CANCELED.name(), REJECTED.name()).contains(row.getStatus()); }
    private static void requireState(TicketWorkDTO row, TicketStatus expected) {
        if (!expected.name().equals(row.getStatus())) { throw conflict("현재 상태에서는 처리할 수 없습니다. 새로 불러와 주세요."); }
    }
    private static void requireVersion(TicketWorkDTO row, TicketRequestDTO form) {
        requireForm(form);
        if (form.getVersion() == null || form.getVersion() < 0) { throw bad("변경 버전이 필요합니다."); }
        if (!form.getVersion().equals(row.getVersion())) { throw conflict("다른 사람이 먼저 변경했습니다. 새로 불러와 주세요."); }
    }
    private static void requireForm(TicketRequestDTO form) { if (form == null) { throw bad("요청 내용이 필요합니다."); } }
    private static void requireId(Long id) { if (id == null || id <= 0) { throw bad("번호가 올바르지 않습니다."); } }
    private static String text(String raw, int max, String label) {
        if (raw == null || raw.isBlank() || raw.strip().length() > max) { throw bad(label + "은 1~" + max + "자로 입력해 주세요."); }
        return raw.strip();
    }
    private static void requireLogin(LoginUser user) {
        if (user == null || user.getCompanyId() == null || user.getCompanyId() <= 0 || user.getEmployeeId() == null
                || user.getEmployeeId() <= 0 || user.getUserAccountId() == null || user.getUserAccountId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }
    private static void requireOperator(LoginUser user) {
        requireLogin(user);
        if (!admin(user) && !"ROLE_IT".equals(user.getRole())) { throw forbidden("IT 담당자 또는 관리자만 사용할 수 있습니다."); }
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException forbidden(String message) { return new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
}
