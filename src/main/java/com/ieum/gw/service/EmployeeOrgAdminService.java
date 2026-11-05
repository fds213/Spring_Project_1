package com.ieum.gw.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.code.EmployeeOrgKind;
import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dto.EmployeeLoginInfoDTO;
import com.ieum.gw.dto.EmployeeOrgItemDTO;
import com.ieum.gw.dto.LoginUser;

import lombok.RequiredArgsConstructor;

/** 부서·직급 관리 (CFG-01). 업체 관리자만. 업체는 로그인 정보에서만 꺼낸다 */
@Service
@RequiredArgsConstructor
public class EmployeeOrgAdminService {

    private static final Set<String> DEPT_TYPES = Set.of("DEPT_IT", "DEPT_HR", "DEPT_OPS");
    private static final String BAD_CHARS = "/\\;%"; // 이름이 주소에 들어가서 못 쓰는 글자
    private static final int NAME_MAX = 20;

    private final EmployeeDAO dao;
    private final EmployeeService employeeService; // 이름을 바꾼 뒤 내 헤더 문구

    /** 목록 (GET /employee/admin/api/{depts|ranks}) */
    public Map<String, Object> list(LoginUser user, EmployeeOrgKind kind) {
        requireAdmin(user);
        List<Map<String, Object>> items = new ArrayList<>();
        for (EmployeeOrgItemDTO o : dao.listOrgItems(user.getCompanyId(), kind)) {
            Map<String, Object> members = new LinkedHashMap<>();
            members.put("inUse", o.getInUse());
            members.put("retired", o.getRetired());
            members.put("requests", o.getRequests());

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", o.getName());
            m.put("members", members);
            if (kind.isDept()) {
                m.put("type", o.getType());
                m.put("head", o.getHeadName());
            }
            items.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("max", kind.getMax());
        result.put("items", items);
        return result;
    }

    /** 추가 (POST /employee/admin/api/{kind}). 예전에 지운 같은 이름이 있으면 되살린다 */
    @Transactional
    public Map<String, Object> add(LoginUser user, EmployeeOrgKind kind, String rawName) {
        requireAdmin(user);
        String name = cleanName(kind, rawName);
        Long companyId = user.getCompanyId();

        List<EmployeeOrgItemDTO> items = dao.lockOrgItems(companyId, kind);
        if (items.size() >= kind.getMax()) {
            throw new IllegalStateException("최대 " + kind.getMax() + "개까지 만들 수 있습니다");
        }
        requireNoSameName(items, name, null);

        // 지운 행도 남아 있어 같은 이름으로 새로 넣으면 (업체, 이름) 고유 제약에 걸린다 → 되살린다
        Long deletedId = dao.findDeletedOrgId(companyId, kind, name);
        if (deletedId != null) {
            dao.reviveOrg(companyId, kind, deletedId, items.size());
        } else {
            dao.insertOrg(companyId, kind, name, items.size());
        }
        return Map.of("name", name);
    }

    /** 이름 바꾸기 (POST /employee/admin/api/{kind}/{name}). 소속 직원은 번호로 이어져 그대로 따라온다 */
    @Transactional
    public Map<String, Object> rename(LoginUser user, EmployeeOrgKind kind, String from, String rawTo) {
        requireAdmin(user);
        String to = cleanName(kind, rawTo);
        Long companyId = user.getCompanyId();

        List<EmployeeOrgItemDTO> items = dao.lockOrgItems(companyId, kind);
        EmployeeOrgItemDTO target = find(items, kind, from);
        if (!to.equals(from)) {
            requireNoSameName(items, to, target.getId());
            try {
                dao.renameOrg(companyId, kind, target.getId(), to);
            } catch (DuplicateKeyException e) {
                // 지운 행과 이름이 같을 때 (사용 중 목록에는 없어 위 검사를 지나온다)
                throw new IllegalStateException("예전에 삭제한 같은 이름이 있습니다. 그 이름으로 추가하면 되살아납니다");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("mySub", mySub(user, kind, to));
        return result;
    }

    /** 삭제 = 사용 안 함 (POST /employee/admin/api/{kind}/{name}/delete). 사용 중 계정·가입 요청이 있으면 거절 */
    @Transactional
    public Map<String, Object> delete(LoginUser user, EmployeeOrgKind kind, String name) {
        requireAdmin(user);
        Long companyId = user.getCompanyId();

        List<EmployeeOrgItemDTO> items = dao.lockOrgItems(companyId, kind);
        EmployeeOrgItemDTO target = find(items, kind, name);
        if (items.size() <= 1) {
            throw new IllegalStateException("하나 이상 남겨야 합니다");
        }
        // 인원은 잠근 뒤에 다시 센다 (화면을 연 사이 누가 들어왔을 수 있다)
        for (EmployeeOrgItemDTO o : dao.listOrgItems(companyId, kind)) {
            if (o.getId().equals(target.getId()) && (o.getInUse() > 0 || o.getRequests() > 0)) {
                throw new IllegalStateException("사용 중 계정 " + o.getInUse() + "명, 가입 요청 " + o.getRequests()
                        + "건이 있어 지울 수 없습니다");
            }
        }
        dao.deleteOrg(companyId, kind, target.getId());
        return Map.of("name", name);
    }

    /** 순서 바꾸기 (POST /employee/admin/api/{kind}/{name}/move). 옆과 바꾼 뒤 전체 순서를 0부터 다시 매긴다 */
    @Transactional
    public Map<String, Object> move(LoginUser user, EmployeeOrgKind kind, String name, Integer dir) {
        requireAdmin(user);
        if (dir == null || (dir != -1 && dir != 1)) {
            throw new IllegalArgumentException("dir는 -1 또는 1로 보내 주세요");
        }
        Long companyId = user.getCompanyId();

        List<EmployeeOrgItemDTO> items = new ArrayList<>(dao.lockOrgItems(companyId, kind));
        EmployeeOrgItemDTO target = find(items, kind, name);
        int from = items.indexOf(target);
        int to = from + dir;
        if (to < 0 || to >= items.size()) {
            throw new IllegalStateException("더 옮길 수 없습니다");
        }
        Collections.swap(items, from, to);
        for (int i = 0; i < items.size(); i++) {
            dao.updateOrgSortOrder(companyId, kind, items.get(i).getId(), i);
        }
        return Map.of("ok", true);
    }

    /** 부서 형태 바꾸기 (POST /employee/admin/api/depts/{name}/type). 빈 값은 일반 */
    @Transactional
    public Map<String, Object> setType(LoginUser user, String name, String type) {
        requireAdmin(user);
        String code = (type == null || type.isEmpty()) ? null : type;
        if (code != null && !DEPT_TYPES.contains(code)) {
            throw new IllegalArgumentException("부서 형태를 다시 골라 주세요");
        }
        Long companyId = user.getCompanyId();

        EmployeeOrgItemDTO target = find(dao.lockOrgItems(companyId, EmployeeOrgKind.DEPT), EmployeeOrgKind.DEPT, name);
        dao.updateDeptType(companyId, target.getId(), code);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("type", code);
        return result;
    }

    /** 업체 관리자만 (Security도 관리 화면 주소를 관리자만 통과시킨다) */
    private void requireAdmin(LoginUser user) {
        if (!"ROLE_ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("부서·직급 관리는 업체 관리자만 할 수 있습니다");
        }
    }

    /** 이름 검사: 앞뒤 공백 제거, 1~20자, 주소에 못 쓰는 글자 금지, 직급 '관리자' 금지 */
    private String cleanName(EmployeeOrgKind kind, String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(kind.getWord() + " 이름을 입력해 주세요");
        }
        if (name.length() > NAME_MAX) {
            throw new IllegalArgumentException("이름은 " + NAME_MAX + "자까지 쓸 수 있습니다");
        }
        for (char c : BAD_CHARS.toCharArray()) {
            if (name.indexOf(c) >= 0) {
                throw new IllegalArgumentException("이름에 / \\ ; % 는 쓸 수 없습니다");
            }
        }
        if (!kind.isDept() && norm(name).equals("관리자")) {
            throw new IllegalArgumentException("'관리자'는 최초 관리자 표시에 쓰여 직급 이름으로 쓸 수 없습니다");
        }
        return name;
    }

    /** 공백·대소문자를 무시하고 비교할 이름 */
    private static String norm(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 같은 종류 안에서 이름이 겹치면 409 (exceptId는 이름을 바꾸는 자기 자신) */
    private void requireNoSameName(List<EmployeeOrgItemDTO> items, String name, Long exceptId) {
        for (EmployeeOrgItemDTO o : items) {
            if (!o.getId().equals(exceptId) && norm(o.getName()).equals(norm(name))) {
                throw new IllegalStateException("이미 있는 이름입니다");
            }
        }
    }

    /** 이름으로 찾기. 없으면 404 (다른 탭에서 지웠을 수 있다) */
    private EmployeeOrgItemDTO find(List<EmployeeOrgItemDTO> items, EmployeeOrgKind kind, String name) {
        return items.stream()
                .filter(o -> o.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "없는 " + kind.getWord() + "입니다. 목록을 새로 고쳐 주세요"));
    }

    /** 이름을 바꾼 부서·직급이 내 것이면 헤더에 보일 새 "부서 · 직급", 아니면 null */
    private String mySub(LoginUser user, EmployeeOrgKind kind, String to) {
        EmployeeLoginInfoDTO me = employeeService.getLoginInfo(user.getCompanyId(), user.getEmployeeId());
        if (me == null) {
            return null;
        }
        String mine = kind.isDept() ? me.getDeptName() : me.getRankName();
        if (!to.equals(mine)) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (me.getDeptName() != null) {
            parts.add(me.getDeptName());
        }
        if (me.getRankName() != null) {
            parts.add(me.getRankName());
        }
        return String.join(" · ", parts);
    }
}