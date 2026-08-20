package kr.ac.knue.performance.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kr.ac.knue.performance.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommonService {
    private static final List<String> ROLE_CODES = List.of("R01","R02","R03","R04","R05","R06","R07","R08","R09");
    private final CommonMapper mapper;

    public CommonService(CommonMapper mapper) {
        this.mapper = mapper;
    }

    public String login(String loginId, String password) {
        require(loginId, "loginId");
        require(password, "password");
        String hash = sha256(password);
        Map<String, Object> user = mapper.usersForAuth().stream()
            .filter(row -> loginId.equals(row.get("loginId")))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIAL", "credential 오류입니다."));
        if (!Boolean.TRUE.equals(user.get("systemEnabled")) || !hash.equals(user.get("passwordHash"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIAL", "credential 오류입니다.");
        }
        String sessionId = UUID.randomUUID().toString();
        mapper.createSession(sessionId, user.get("userId").toString());
        return sessionId;
    }

    public Map<String, Object> authenticate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다.");
        }
        Map<String, Object> session = mapper.session(sessionId);
        if (session == null || !"ACTIVE".equals(session.get("status"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다.");
        }
        Object roles = session.get("roleCodes");
        if (roles == null || !roles.toString().contains("R09")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다.");
        }
        Map<String, Object> authenticated = new LinkedHashMap<>(session);
        authenticated.put("roleCodes", roles.toString().isBlank() ? List.of() : List.of(roles.toString().split(",")));
        return authenticated;
    }

    public void logout(String sessionId) {
        authenticate(sessionId);
        mapper.expireSession(sessionId);
    }

    public Map<String, Object> me(String sessionId) {
        return authenticate(sessionId);
    }

    public List<Map<String, Object>> navigation(String sessionId) {
        Map<String, Object> session = authenticate(sessionId);
        String roleText = Objects.toString(session.get("roleCodes"), "");
        List<String> roles = ROLE_CODES.stream().filter(roleText::contains).toList();
        return tree(mapper.navigation(roles.toArray(String[]::new)), "menuId", "parentMenuId", "children");
    }

    public Object list(String type, Map<String, String> q) {
        String filter = blank(q.get("filter"));
        return switch (type) {
            case "users" -> mapper.listUsers(filter);
            case "organizations" -> mapper.listOrganizations(filter);
            case "organizationTree" -> tree(mapper.organizationTreeRows(), "orgCode", "parentOrgCode", "children");
            case "roles" -> mapper.listRoles();
            case "userRoles" -> mapper.listUserRoles(filter);
            case "menuPermissions" -> mapper.listMenuPermissions(blank(q.get("targetType")), blank(q.get("targetId")));
            case "menuTree" -> tree(mapper.menus(), "menuId", "parentMenuId", "children");
            case "menus" -> mapper.menus();
            case "codeGroups" -> mapper.listCodeGroups(filter);
            case "codeDetails" -> mapper.listCodeDetails(blank(q.get("groupId")), filter);
            default -> new ArrayList<>();
        };
    }

    @Transactional
    public void updateUserAccess(String userId, Map<String, Object> body, String approver) {
        require(userId, "userId");
        String yn = text(body, "systemEnabled");
        if (!List.of("Y", "N").contains(yn)) validation("systemEnabled", "Y 또는 N만 허용됩니다.");
        int count = mapper.updateUser(userId, "Y".equals(yn), "Y".equals(yn) ? "ACTIVE" : "DISABLED");
        if (count == 0) notFound("userId");
        mapper.deleteManualRoles(userId);
        List<?> roles = body.get("roleCodes") instanceof List<?> list ? list : new ArrayList<>();
        for (Object role : roles) {
            String roleCode = Objects.toString(role, "");
            if (!ROLE_CODES.contains(roleCode)) validation("roleCodes", "R01~R09만 허용됩니다.");
            mapper.insertManualRole(UUID.randomUUID().toString(), userId, roleCode, approver);
        }
    }

    @Transactional
    public void updateOrganizationRelationship(String orgCode, Map<String, Object> body) {
        require(orgCode, "orgCode");
        String start = text(body, "effectiveStartDate");
        String end = optionalText(body, "effectiveEndDate");
        if (end != null && LocalDate.parse(start).isAfter(LocalDate.parse(end))) validation("effectiveEndDate", "종료일은 시작일 이후여야 합니다.");
        int count = mapper.updateOrganizationRelation(orgCode, optionalText(body, "parentOrgCode"), start, end, end == null ? "ACTIVE" : "ENDED");
        if (count == 0) notFound("orgCode");
    }

    @Transactional
    public void updateRole(String roleCode, Map<String, Object> body) {
        if (!ROLE_CODES.contains(roleCode)) validation("roleCode", "R01~R09만 허용됩니다.");
        if (body.containsKey("roleCode") && !roleCode.equals(body.get("roleCode"))) validation("roleCode", "role_code는 변경할 수 없습니다.");
        int count = mapper.updateRole(roleCode, text(body,"roleName"), text(body,"purpose"), optionalText(body,"grantCriteria"), optionalText(body,"dataScopeDefault"), yn(body,"useYn"));
        if (count == 0) notFound("roleCode");
    }

    @Transactional
    public void replaceUserRoles(String userId, Map<String, Object> body) {
        require(userId, "userId");
        List<?> assignments = (List<?>) body.get("assignments");
        if (assignments == null || assignments.isEmpty()) validation("assignments", "하나 이상의 역할이 필요합니다.");
        mapper.deleteRoles(userId);
        for (Object item : assignments) {
            Map<?, ?> a = (Map<?, ?>) item;
            String roleCode = Objects.toString(a.get("roleCode"), "");
            if (!ROLE_CODES.contains(roleCode)) validation("roleCode", "R01~R09만 허용됩니다.");
            String start = Objects.toString(a.get("effectiveStartDate"), "");
            String end = a.get("effectiveEndDate") == null ? null : Objects.toString(a.get("effectiveEndDate"));
            if (start.isBlank()) validation("effectiveStartDate", "필수입니다.");
            if (end != null && LocalDate.parse(start).isAfter(LocalDate.parse(end))) validation("effectiveEndDate", "종료일은 시작일 이후여야 합니다.");
            String assignmentType = a.containsKey("assignmentType") ? Objects.toString(a.get("assignmentType"), "MANUAL") : "MANUAL";
            String status = a.containsKey("status") ? Objects.toString(a.get("status"), "ACTIVE") : "ACTIVE";
            String approver = a.get("approverUserId") == null ? null : Objects.toString(a.get("approverUserId"));
            mapper.insertRoleAssignment(UUID.randomUUID().toString(), userId, roleCode, assignmentType, start, end, approver, status);
        }
    }

    @Transactional
    public void saveMenuPermissions(Map<String, Object> body) {
        String targetType = text(body,"targetType");
        if (!List.of("ROLE","ORGANIZATION","USER").contains(targetType)) validation("targetType", "ROLE, ORGANIZATION, USER만 허용됩니다.");
        String targetId = text(body,"targetId");
        List<?> permissions = (List<?>) body.get("permissions");
        if (permissions == null) validation("permissions", "필수입니다.");
        for (Object item : permissions) {
            Map<?, ?> p = (Map<?, ?>) item;
            String menuId = Objects.toString(p.get("menuId"), "");
            boolean allow = "Y".equals(Objects.toString(p.get("allowAccess"), "N"));
            if (mapper.updatePermission(targetType, targetId, menuId, allow) == 0) {
                mapper.insertPermission(UUID.randomUUID().toString(), targetType, targetId, menuId, allow);
            }
        }
    }

    public void updateMenuStructure(String menuId, Map<String, Object> body) {
        require(menuId, "menuId");
        int level = Integer.parseInt(Objects.toString(body.getOrDefault("menuLevel", "3")));
        if (level < 1 || level > 3) validation("menuLevel", "1~3만 허용됩니다.");
        if (mapper.updateMenuStructure(menuId, optionalText(body,"parentMenuId"), level) == 0) notFound("menuId");
    }

    public void reorderMenus(Map<String, Object> body) {
        List<?> orders = (List<?>) body.get("orders");
        if (orders == null || orders.isEmpty()) validation("orders", "필수입니다.");
        for (Object item : orders) {
            Map<?, ?> o = (Map<?, ?>) item;
            mapper.updateMenuOrder(Objects.toString(o.get("menuId"), ""), Integer.parseInt(Objects.toString(o.get("sortOrder"), "0")));
        }
    }

    public void createMenu(Map<String, Object> body) { mapper.insertMenu(menuPayload(body, UUID.randomUUID().toString())); }
    public void updateMenu(String menuId, Map<String, Object> body) { Map<String,Object> p = menuPayload(body, menuId); if (mapper.updateMenu(p)==0) notFound("menuId"); }
    public void createCodeGroup(Map<String, Object> body) { mapper.insertCodeGroup(codeGroupPayload(body, text(body,"groupId"))); }
    public void updateCodeGroup(String groupId, Map<String, Object> body) { if (mapper.updateCodeGroup(codeGroupPayload(body, groupId))==0) notFound("groupId"); }
    public void createCodeDetail(Map<String, Object> body) { mapper.insertCodeDetail(codeDetailPayload(body, UUID.randomUUID().toString())); }
    public void updateCodeDetail(String detailId, Map<String, Object> body) { if (mapper.updateCodeDetail(codeDetailPayload(body, detailId))==0) notFound("detailId"); }

    private Map<String,Object> menuPayload(Map<String,Object> b, String id) {
        require(text(b,"menuName"), "menuName"); require(text(b,"screenId"), "screenId"); require(text(b,"url"), "url"); require(text(b,"businessCategory"), "businessCategory");
        Map<String,Object> m = new LinkedHashMap<>(b); m.put("menuId", id); m.putIfAbsent("parentMenuId", null); m.putIfAbsent("menuLevel", 3); m.putIfAbsent("sortOrder", 99); m.putIfAbsent("icon", ""); m.putIfAbsent("description", ""); m.put("useYn", yn(b,"useYn")); return m;
    }
    private Map<String,Object> codeGroupPayload(Map<String,Object> b, String id) { require(id,"groupId"); require(text(b,"groupName"),"groupName"); Map<String,Object> m=new LinkedHashMap<>(b); m.put("groupId",id); m.putIfAbsent("description",""); m.putIfAbsent("managingDepartment",""); m.put("useYn",yn(b,"useYn")); return m; }
    private Map<String,Object> codeDetailPayload(Map<String,Object> b, String id) { require(text(b,"groupId"),"groupId"); require(text(b,"codeValue"),"codeValue"); require(text(b,"codeName"),"codeName"); Map<String,Object> m=new LinkedHashMap<>(b); m.put("detailId",id); m.putIfAbsent("parentCodeValue",null); m.putIfAbsent("sortOrder",0); m.putIfAbsent("extraAttributes","{}"); m.put("useYn",yn(b,"useYn")); return m; }

    private static List<Map<String, Object>> tree(List<Map<String, Object>> rows, String idKey, String parentKey, String childrenKey) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> row : rows) { Map<String,Object> copy=new LinkedHashMap<>(row); copy.put(childrenKey,new ArrayList<>()); byId.put(Objects.toString(row.get(idKey),""),copy); }
        for (Map<String, Object> row : byId.values()) { String parent=Objects.toString(row.get(parentKey),""); if (!parent.isBlank() && byId.containsKey(parent)) ((List<Map<String,Object>>) byId.get(parent).get(childrenKey)).add(row); else roots.add(row); }
        return roots;
    }
    private static String text(Map<String, Object> body, String key) { String value = Objects.toString(body.get(key), "").trim(); require(value, key); return value; }
    private static String optionalText(Map<String, Object> body, String key) { String value = Objects.toString(body.get(key), "").trim(); return value.isBlank() ? null : value; }
    private static boolean yn(Map<String, Object> body, String key) { String value=Objects.toString(body.getOrDefault(key,"Y")); if (!List.of("Y","N").contains(value)) validation(key,"Y 또는 N만 허용됩니다."); return "Y".equals(value); }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s; }
    private static void require(String value, String field) { if (value == null || value.isBlank()) validation(field, "필수입니다."); }
    private static void validation(String field, String message) { throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.", Map.of(field, message)); }
    private static void notFound(String field) { throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_FOUND", "대상을 찾을 수 없습니다.", Map.of(field, "not found")); }
    public static String sha256(String value) { try { MessageDigest md=MessageDigest.getInstance("SHA-256"); byte[] bytes=md.digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); for(byte b:bytes) sb.append(String.format("%02x", b)); return sb.toString(); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
