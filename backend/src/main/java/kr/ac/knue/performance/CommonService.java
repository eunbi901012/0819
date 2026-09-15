package kr.ac.knue.performance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommonService {
    private final CommonMapper mapper;
    private final ObjectMapper objectMapper;

    public CommonService(CommonMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "faculty-performance-common");
    }

    @Transactional
    public CurrentUser login(LoginRequest request) {
        if (request == null || blank(request.loginId()) || blank(request.password())) {
            throw bad("VALIDATION_ERROR", "로그인 정보를 입력하세요.", Map.of("loginId", "required", "password", "required"));
        }
        Map<String, Object> user = mapper.authenticate(request.loginId(), request.password());
        if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "계정 또는 비밀번호를 확인하세요.", Map.of());
        String sessionId = UUID.randomUUID().toString();
        mapper.createSession(sessionId, str(user.get("userId")));
        LastSessionHolder.set(sessionId);
        RequestContext.setActor(str(user.get("userId")));
        history("session", sessionId, null, Map.of("userId", str(user.get("userId")), "status", "ACTIVE"), "로그인");
        return currentUser(user);
    }

    public CurrentUser currentUser(String sessionId) {
        Map<String, Object> user = requireSession(sessionId);
        return currentUser(user);
    }

    @Transactional
    public Map<String, Object> logout(String sessionId, GenericRequest request) {
        requireSession(sessionId);
        Map<String, Object> before = mapper.byId("session", "session_id", sessionId);
        mapper.expireSession(sessionId);
        Map<String, Object> after = mapper.byId("session", "session_id", sessionId);
        history("session", sessionId, before, after, request == null ? null : request.reason());
        return Map.of("loggedOut", true);
    }

    public Map<String, Object> requireSession(String sessionId) {
        if (blank(sessionId)) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 세션이 필요합니다.", Map.of());
        Map<String, Object> user = mapper.sessionUser(sessionId);
        if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 세션이 만료되었습니다.", Map.of());
        List<String> roles = mapper.rolesForUser(str(user.get("userId")));
        if (!roles.contains("R09")) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "R09 시스템관리자 권한이 필요합니다.", Map.of("requiredRole", "R09"));
        RequestContext.setActor(str(user.get("userId")));
        return user;
    }

    public PageResult page(List<Map<String, Object>> items, int page, int size) {
        return new PageResult(items, page, size, items.size());
    }

    public PageResult users(String filter, String roleCode, Boolean systemEnabled, int page, int size) {
        return page(normalizeUsers(mapper.users(filter, roleCode, systemEnabled, size, page * size)), page, size);
    }

    public PageResult organizations(String filter, int page, int size) {
        return page(mapper.organizations(filter, size, page * size), page, size);
    }

    public PageResult roles(String filter) {
        return page(mapper.roles(filter), 0, 100);
    }

    public PageResult userRoles(String filter, int page, int size) {
        return page(mapper.userRoles(filter, size, page * size), page, size);
    }

    public PageResult menuPermissions(String targetType, String targetId) {
        return page(mapper.menuPermissions(targetType, targetId), 0, 100);
    }

    public PageResult navigation() {
        List<Map<String, Object>> leaves = mapper.menus(null).stream()
            .filter(row -> "LEAF".equals(str(row.get("menuLevel"))) && row.get("url") != null)
            .filter(row -> mapper.menuPermissions("ROLE", "R09").stream().anyMatch(p -> str(p.get("menuId")).equals(str(row.get("menuId"))) && Boolean.TRUE.equals(p.get("allowed"))))
            .toList();
        return page(leaves, 0, leaves.size());
    }

    public PageResult menus(String filter) {
        return page(mapper.menus(filter), 0, 200);
    }

    public PageResult menuTree() {
        return page(tree(mapper.menus(null), null), 0, 200);
    }

    public PageResult organizationTree() {
        return page(tree(mapper.organizations(null, 500, 0), null), 0, 500);
    }

    public PageResult codeGroups(String filter) {
        return page(mapper.codeGroups(filter), 0, 100);
    }

    public PageResult detailCodes(String groupId, String filter) {
        return page(mapper.detailCodes(groupId, filter), 0, 100);
    }

    @Transactional
    public Map<String, Object> updateUserUsage(String userId, UserUsageRequest request) {
        if (request == null || request.systemEnabled() == null) throw bad("VALIDATION_ERROR", "사용여부를 입력하세요.", Map.of("systemEnabled", "required"));
        Map<String, Object> before = mapper.byId("user_account", "user_id", userId);
        if (before == null) throw notFound("사용자를 찾을 수 없습니다.");
        mapper.updateUserUsage(userId, request.systemEnabled());
        history("user_account", userId, before, mapper.byId("user_account", "user_id", userId), request.reason());
        return firstUser(userId);
    }

    @Transactional
    public Map<String, Object> updateBusinessRoles(String userId, BusinessRoleRequest request) {
        if (request == null || request.roleCodes() == null || request.roleCodes().isEmpty()) throw bad("VALIDATION_ERROR", "역할을 선택하세요.", Map.of("roleCodes", "required"));
        List<String> currentRoles = mapper.rolesForUser(userId);
        for (String roleCode : request.roleCodes()) {
            if (currentRoles.contains(roleCode)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("assignmentId", "URA-" + userId + "-" + roleCode);
            row.put("userId", userId);
            row.put("roleCode", roleCode);
            row.put("assignmentType", "MANUAL");
            row.put("validFrom", LocalDate.now().toString());
            row.put("validTo", null);
            row.put("approverUserId", RequestContext.actor());
            tryInsertUserRole(row);
        }
        history("user_role_assignment", userId, null, Map.of("roleCodes", request.roleCodes()), request.reason());
        return firstUser(userId);
    }

    @Transactional
    public Map<String, Object> updateOrganizationRelation(OrganizationRelationRequest request) {
        if (request == null || blank(request.organizationCode()) || blank(request.effectiveStartDate())) throw bad("VALIDATION_ERROR", "조직코드와 적용 시작일은 필수입니다.", Map.of("organizationCode", "required", "effectiveStartDate", "required"));
        if (!blank(request.effectiveEndDate()) && LocalDate.parse(request.effectiveEndDate()).isBefore(LocalDate.parse(request.effectiveStartDate()))) throw bad("VALIDATION_ERROR", "종료일은 시작일보다 빠를 수 없습니다.", Map.of("effectiveEndDate", "invalidPeriod"));
        Map<String, Object> before = mapper.byId("organization", "organization_code", request.organizationCode());
        if (before == null) throw notFound("조직을 찾을 수 없습니다.");
        mapper.updateOrganizationRelation(request.organizationCode(), request.parentOrganizationCode(), request.effectiveStartDate(), request.effectiveEndDate());
        Map<String, Object> after = mapper.byId("organization", "organization_code", request.organizationCode());
        history("organization", request.organizationCode(), before, after, request.reason());
        return mapper.organizations(request.organizationCode(), 1, 0).get(0);
    }

    @Transactional
    public Map<String, Object> saveRole(String pathRoleCode, RoleRequest request) {
        if (request == null || blank(request.roleCode()) || blank(request.roleName()) || blank(request.purpose())) throw bad("VALIDATION_ERROR", "역할 필수값을 입력하세요.", Map.of("roleCode", "required", "roleName", "required", "purpose", "required"));
        if (pathRoleCode != null && !pathRoleCode.equals(request.roleCode())) throw bad("VALIDATION_ERROR", "역할코드는 변경할 수 없습니다.", Map.of("roleCode", "immutable"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roleCode", request.roleCode()); row.put("roleName", request.roleName()); row.put("purpose", request.purpose()); row.put("assignmentCriteria", request.assignmentCriteria()); row.put("defaultDataScope", request.defaultDataScope()); row.put("useYn", yn(request.useYn()));
        upsertRole(row);
        history("role", request.roleCode(), null, row, request.reason());
        return mapper.roles(request.roleCode()).get(0);
    }

    @Transactional
    public Map<String, Object> createUserRole(UserRoleAssignmentRequest request) {
        validateUserRole(request);
        Map<String, Object> row = userRoleRow(UUID.randomUUID().toString(), request);
        mapper.insertUserRole(row);
        history("user_role_assignment", str(row.get("assignmentId")), null, row, request.reason());
        return mapper.userRoles(str(row.get("assignmentId")), 1, 0).get(0);
    }

    @Transactional
    public Map<String, Object> updateUserRole(String assignmentId, UserRoleAssignmentRequest request) {
        validateUserRole(request);
        Map<String, Object> before = mapper.byId("user_role_assignment", "assignment_id", assignmentId);
        if (before == null) throw notFound("사용자 역할을 찾을 수 없습니다.");
        Map<String, Object> row = userRoleRow(assignmentId, request);
        mapper.updateUserRole(row);
        history("user_role_assignment", assignmentId, before, row, request.reason());
        return mapper.userRoles(assignmentId, 1, 0).get(0);
    }

    @Transactional
    public Map<String, Object> revokeUserRole(String assignmentId, RevokeRoleRequest request) {
        Map<String, Object> before = mapper.byId("user_role_assignment", "assignment_id", assignmentId);
        if (before == null) throw notFound("사용자 역할을 찾을 수 없습니다.");
        if ("REVOKED".equals(str(before.get("status")))) throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "이미 회수된 역할입니다.", Map.of("assignmentId", assignmentId));
        mapper.revokeUserRole(assignmentId);
        Map<String, Object> after = mapper.byId("user_role_assignment", "assignment_id", assignmentId);
        history("user_role_assignment", assignmentId, before, after, request == null ? null : request.reason());
        return mapper.userRoles(assignmentId, 1, 0).get(0);
    }

    @Transactional
    public PageResult saveMenuPermissions(MenuPermissionRequest request) {
        if (request == null || !List.of("ROLE", "ORG", "USER").contains(request.targetType())) throw bad("VALIDATION_ERROR", "대상구분이 올바르지 않습니다.", Map.of("targetType", "ROLE|ORG|USER"));
        if (blank(request.targetId()) || request.permissions() == null) throw bad("VALIDATION_ERROR", "대상과 권한 항목을 입력하세요.", Map.of("targetId", "required", "permissions", "required"));
        for (MenuPermissionItem item : request.permissions()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("permissionId", "PERM-" + request.targetType() + "-" + request.targetId() + "-" + item.menuId());
            row.put("targetType", request.targetType()); row.put("targetId", request.targetId()); row.put("menuId", item.menuId()); row.put("allowed", item.allowed());
            upsertMenuPermission(row);
        }
        history("menu_permission", request.targetType() + ":" + request.targetId(), null, Map.of("count", request.permissions().size()), request.reason());
        return menuPermissions(request.targetType(), request.targetId());
    }

    @Transactional
    public Map<String, Object> updateMenuParent(String menuId, MenuParentRequest request) {
        if (request == null || request.displayOrder() == null) throw bad("VALIDATION_ERROR", "표시순서를 입력하세요.", Map.of("displayOrder", "required"));
        mapper.updateMenuParent(menuId, request.parentMenuId(), request.displayOrder());
        history("menu", menuId, null, request, request.reason());
        return mapper.menus(menuId).stream().filter(m -> menuId.equals(str(m.get("menuId")))).findFirst().orElseThrow(() -> notFound("메뉴를 찾을 수 없습니다."));
    }

    @Transactional
    public PageResult reorderMenus(MenuReorderRequest request) {
        if (request == null || request.items() == null) throw bad("VALIDATION_ERROR", "정렬 항목을 입력하세요.", Map.of("items", "required"));
        request.items().forEach(item -> mapper.updateMenuOrder(item.menuId(), item.displayOrder()));
        history("menu", "reorder", null, request, request.reason());
        return menus(null);
    }

    @Transactional
    public Map<String, Object> saveMenu(String pathMenuId, MenuRequest request) {
        if (request == null || blank(request.menuId()) || blank(request.menuName()) || request.displayOrder() == null) throw bad("VALIDATION_ERROR", "메뉴 필수값을 입력하세요.", Map.of("menuId", "required", "menuName", "required", "displayOrder", "required"));
        if (pathMenuId != null && !pathMenuId.equals(request.menuId())) throw bad("VALIDATION_ERROR", "메뉴ID는 변경할 수 없습니다.", Map.of("menuId", "immutable"));
        Map<String, Object> row = row("menuId", request.menuId(), "parentMenuId", request.parentMenuId(), "menuLevel", request.menuLevel(), "menuName", request.menuName(), "displayOrder", request.displayOrder(), "screenId", request.screenId(), "url", request.url(), "icon", request.icon(), "businessCategory", request.businessCategory(), "description", request.description(), "useYn", yn(request.useYn()));
        upsertMenu(row);
        history("menu", request.menuId(), null, row, request.reason());
        return mapper.menus(request.menuId()).stream().filter(m -> request.menuId().equals(str(m.get("menuId")))).findFirst().orElse(row);
    }

    @Transactional
    public Map<String, Object> saveCodeGroup(String pathGroupId, CodeGroupRequest request) {
        if (request == null || blank(request.groupId()) || blank(request.groupName())) throw bad("VALIDATION_ERROR", "코드그룹 필수값을 입력하세요.", Map.of("groupId", "required", "groupName", "required"));
        if (pathGroupId != null && !pathGroupId.equals(request.groupId())) throw bad("VALIDATION_ERROR", "그룹ID는 변경할 수 없습니다.", Map.of("groupId", "immutable"));
        Map<String, Object> row = row("groupId", request.groupId(), "groupName", request.groupName(), "description", request.description(), "managementDepartment", request.managementDepartment(), "useYn", yn(request.useYn()));
        upsertCodeGroup(row);
        history("code_group", request.groupId(), null, row, request.reason());
        return mapper.codeGroups(request.groupId()).get(0);
    }

    @Transactional
    public Map<String, Object> saveDetailCode(String groupId, String pathCodeValue, DetailCodeRequest request) {
        if (request == null || blank(request.codeValue()) || blank(request.codeName()) || request.sortOrder() == null || request.sortOrder() < 0) throw bad("VALIDATION_ERROR", "상세코드 필수값을 확인하세요.", Map.of("sortOrder", "nonNegativeRequired", "codeValue", "required", "codeName", "required"));
        if (pathCodeValue != null && !pathCodeValue.equals(request.codeValue())) throw bad("VALIDATION_ERROR", "코드값은 변경할 수 없습니다.", Map.of("codeValue", "immutable"));
        Map<String, Object> row = row("groupId", groupId, "codeValue", request.codeValue(), "codeName", request.codeName(), "parentCodeValue", request.parentCodeValue(), "sortOrder", request.sortOrder(), "extraAttributes", request.extraAttributes() == null ? "{}" : request.extraAttributes().toString(), "validFrom", request.validFrom(), "validTo", request.validTo(), "useYn", yn(request.useYn()));
        upsertDetailCode(row);
        history("detail_code", groupId + ":" + request.codeValue(), null, row, request.reason());
        return mapper.detailCodes(groupId, request.codeValue()).get(0);
    }

    public PageResult batchDefinitions(String filter, int page, int size) {
        List<Map<String, Object>> items = mapper.batchDefinitions(filter, size, page * size);
        items.forEach(row -> parseJsonField(row, "executionParameters"));
        return page(items, page, size);
    }

    @Transactional
    public Map<String, Object> saveBatchDefinition(String pathBatchId, BatchDefinitionRequest request) {
        if (request == null || blank(request.batchId()) || blank(request.batchType()) || blank(request.scheduleCycle()) || request.maxExecutionSeconds() == null || request.maxExecutionSeconds() <= 0 || blank(request.ownerUserId()) || blank(request.reason())) {
            throw bad("VALIDATION_ERROR", "배치 정의 필수값과 사유를 입력하세요.", Map.of("batchId", "required", "batchType", "required", "scheduleCycle", "required", "maxExecutionSeconds", "positiveRequired", "ownerUserId", "required", "reason", "required"));
        }
        if (pathBatchId != null && !pathBatchId.equals(request.batchId())) throw bad("VALIDATION_ERROR", "배치ID는 변경할 수 없습니다.", Map.of("batchId", "immutable"));
        if (mapper.byId("user_account", "user_id", request.ownerUserId()) == null) throw notFound("담당자를 찾을 수 없습니다.");
        if (!blank(request.predecessorBatchId()) && mapper.byId("batch_definition", "batch_id", request.predecessorBatchId()) == null) throw notFound("선행 배치를 찾을 수 없습니다.");
        if (!blank(request.successorBatchId()) && mapper.byId("batch_definition", "batch_id", request.successorBatchId()) == null) throw notFound("후행 배치를 찾을 수 없습니다.");
        Map<String, Object> before = mapper.byId("batch_definition", "batch_id", request.batchId());
        Map<String, Object> row = row("batchId", request.batchId(), "batchType", request.batchType(), "scheduleCycle", request.scheduleCycle(), "predecessorBatchId", request.predecessorBatchId(), "successorBatchId", request.successorBatchId(), "executionParameters", json(request.executionParameters()), "maxExecutionSeconds", request.maxExecutionSeconds(), "ownerUserId", request.ownerUserId(), "useYn", yn(request.useYn()));
        if (mapper.updateBatchDefinition(row) == 0) mapper.insertBatchDefinition(row);
        history("batch_definition", request.batchId(), before, row, request.reason());
        return oneBatchDefinition(request.batchId());
    }

    public PageResult batchExecutions(String filter, int page, int size) {
        List<Map<String, Object>> items = mapper.batchExecutions(filter, size, page * size);
        items.forEach(row -> parseJsonField(row, "executionParameters"));
        return page(items, page, size);
    }

    @Transactional
    public Map<String, Object> createBatchManualRun(BatchManualRunRequest request) {
        if (request == null || blank(request.batchId()) || blank(request.reason())) throw bad("VALIDATION_ERROR", "배치ID와 사유를 입력하세요.", Map.of("batchId", "required", "reason", "required"));
        if (mapper.byId("batch_definition", "batch_id", request.batchId()) == null) throw notFound("배치 정의를 찾을 수 없습니다.");
        String executionId = "EXEC-" + UUID.randomUUID();
        Map<String, Object> row = row("executionId", executionId, "batchId", request.batchId(), "operationType", "MANUAL_RUN", "executionParameters", json(request.executionParameters()), "reason", request.reason(), "operatorUserId", RequestContext.actor(), "executionStatus", "RUNNING", "originalExecutionId", null);
        mapper.insertBatchExecution(row);
        mapper.insertBatchExecutionResult(row("executionId", executionId, "logFilePath", "/var/log/batch/" + executionId + ".log"));
        history("batch_execution", executionId, null, row, request.reason());
        return oneBatchExecution(executionId);
    }

    @Transactional
    public Map<String, Object> stopBatchExecution(String executionId, BatchStopRequest request) {
        if (request == null || blank(request.reason())) throw bad("VALIDATION_ERROR", "중지 사유를 입력하세요.", Map.of("reason", "required"));
        Map<String, Object> before = mapper.byId("batch_execution", "execution_id", executionId);
        if (before == null) throw notFound("배치 실행을 찾을 수 없습니다.");
        mapper.stopBatchExecution(executionId, request.reason());
        Map<String, Object> after = mapper.byId("batch_execution", "execution_id", executionId);
        history("batch_execution", executionId, before, after, request.reason());
        return oneBatchExecution(executionId);
    }

    @Transactional
    public Map<String, Object> rerunBatchExecution(String executionId, BatchRerunRequest request) {
        if (request == null || blank(request.reason())) throw bad("VALIDATION_ERROR", "재실행 사유를 입력하세요.", Map.of("reason", "required"));
        Map<String, Object> original = mapper.byId("batch_execution", "execution_id", executionId);
        if (original == null) throw notFound("원 배치 실행을 찾을 수 없습니다.");
        String rerunId = "EXEC-" + UUID.randomUUID();
        Map<String, Object> row = row("executionId", rerunId, "batchId", str(original.get("batch_id")), "operationType", "RERUN", "executionParameters", json(request.executionParameters()), "reason", request.reason(), "operatorUserId", RequestContext.actor(), "executionStatus", "REQUESTED", "originalExecutionId", executionId);
        mapper.insertBatchExecution(row);
        mapper.insertBatchExecutionResult(row("executionId", rerunId, "logFilePath", "/var/log/batch/" + rerunId + ".log"));
        history("batch_execution", rerunId, null, row, request.reason());
        return oneBatchExecution(rerunId);
    }

    public Map<String, Object> batchExecutionResult(String executionId) {
        Map<String, Object> result = mapper.batchExecutionResult(executionId);
        if (result == null) throw notFound("배치 결과를 찾을 수 없습니다.");
        return result;
    }

    public PageResult batchReprocessTargets(String filter, int page, int size) {
        return page(mapper.batchReprocessTargets(filter, size, page * size), page, size);
    }

    @Transactional
    public Map<String, Object> createBatchReprocessRun(BatchReprocessRunRequest request) {
        if (request == null || blank(request.originalExecutionId()) || blank(request.targetId()) || blank(request.reason())) throw bad("VALIDATION_ERROR", "원실행ID, 재처리 대상, 사유를 입력하세요.", Map.of("originalExecutionId", "required", "targetId", "required", "reason", "required"));
        Map<String, Object> target = mapper.byId("batch_reprocess_target", "target_id", request.targetId());
        if (target == null) throw notFound("재처리 대상을 찾을 수 없습니다.");
        if (!request.originalExecutionId().equals(str(target.get("original_execution_id")))) throw bad("VALIDATION_ERROR", "재처리 대상과 원실행ID가 일치하지 않습니다.", Map.of("targetId", "mismatch"));
        if (!List.of("FAILED", "READY").contains(str(target.get("failure_status")))) throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "실패 상태의 재처리 대상만 실행할 수 있습니다.", Map.of("targetId", request.targetId()));
        String reprocessExecutionId = "REPROC-" + UUID.randomUUID();
        Map<String, Object> row = row("reprocessExecutionId", reprocessExecutionId, "originalExecutionId", request.originalExecutionId(), "targetId", request.targetId(), "reason", request.reason(), "resultStatus", "REQUESTED", "operatorUserId", RequestContext.actor());
        mapper.insertBatchReprocessRun(row);
        history("batch_reprocess_execution", reprocessExecutionId, null, row, request.reason());
        return batchReprocessRun(reprocessExecutionId);
    }

    public Map<String, Object> batchReprocessRun(String reprocessExecutionId) {
        Map<String, Object> result = mapper.batchReprocessRun(reprocessExecutionId);
        if (result == null) throw notFound("재처리 결과를 찾을 수 없습니다.");
        return result;
    }

    public PageResult positions(String baseDate, int page, int size) {
        requireDate(baseDate, "baseDate");
        return page(mapper.positions(baseDate, size, page * size), page, size);
    }

    @Transactional
    public Map<String, Object> createPositionAssignment(PositionAssignmentRequest request) {
        validatePositionAssignment(request);
        String assignmentId = blank(request.assignmentId()) ? "PA-" + UUID.randomUUID() : request.assignmentId();
        Map<String, Object> row = row("assignmentId", assignmentId, "positionCode", request.positionCode(), "positionName", request.positionName(), "userId", request.userId(), "organizationCode", request.organizationCode(), "validFrom", request.validFrom(), "validTo", request.validTo());
        mapper.insertPositionAssignment(row);
        history("position_assignment", assignmentId, null, row, request.reason());
        return mapper.positions(request.validFrom(), 1, 0).stream().filter(item -> assignmentId.equals(str(item.get("assignmentId")))).findFirst().orElse(row);
    }

    public PageResult businessAssignees(int page, int size) {
        return page(mapper.businessAssignees(size, page * size), page, size);
    }

    @Transactional
    public Map<String, Object> createBusinessAssignee(BusinessAssigneeRequest request) {
        validateBusinessAssignee(request);
        String assigneeId = blank(request.assigneeId()) ? "BA-" + UUID.randomUUID() : request.assigneeId();
        Map<String, Object> row = row("assigneeId", assigneeId, "businessOrganizationCode", request.businessOrganizationCode(), "assigneeUserId", request.assigneeUserId(), "businessAreaCode", request.businessAreaCode(), "dataScope", request.dataScope(), "processingPermission", request.processingPermission(), "validFrom", request.validFrom(), "validTo", request.validTo());
        mapper.insertBusinessAssignee(row);
        history("business_assignee", assigneeId, null, row, request.reason());
        return mapper.businessAssignees(100, 0).stream().filter(item -> assigneeId.equals(str(item.get("assigneeId")))).findFirst().orElse(row);
    }

    public PageResult dataScopeRules(int page, int size) {
        return page(mapper.dataScopeRules(size, page * size), page, size);
    }

    @Transactional
    public Map<String, Object> saveDataScopeRule(String ruleId, DataScopeRuleRequest request) {
        validateDataScopeRule(ruleId, request);
        Map<String, Object> before = mapper.byId("data_scope_rule", "rule_id", ruleId);
        Map<String, Object> row = row("ruleId", ruleId, "roleCode", request.roleCode(), "dataScopeType", request.dataScopeType(), "organizationCode", request.organizationCode(), "businessAreaCode", request.businessAreaCode(), "useYn", yn(request.useYn()));
        if (mapper.updateDataScopeRule(row) == 0) mapper.insertDataScopeRule(row);
        history("data_scope_rule", ruleId, before, row, request.reason());
        return mapper.dataScopeRules(200, 0).stream().filter(item -> ruleId.equals(str(item.get("ruleId")))).findFirst().orElse(row);
    }

    public Map<String, Object> evaluateDataScope(DataScopeEvaluationRequest request) {
        if (request == null || blank(request.roleCode())) throw bad("VALIDATION_ERROR", "역할코드를 입력하세요.", Map.of("roleCode", "required"));
        String baseDate = blank(request.baseDate()) ? LocalDate.now().toString() : request.baseDate();
        requireDate(baseDate, "baseDate");
        List<Map<String, Object>> rules = mapper.activeDataScopeRules(request.roleCode());
        Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("roleCode", request.roleCode());
        applied.put("userId", request.userId());
        applied.put("baseDate", baseDate);
        applied.put("organizationCode", request.organizationCode());
        applied.put("businessAreaCode", request.businessAreaCode());
        boolean allowed = rules.stream().anyMatch(rule -> scopeAllows(rule, request, baseDate, applied));
        return row("allowed", allowed, "appliedConditions", applied, "filteredOrganizationCode", allowed ? request.organizationCode() : null);
    }

    private CurrentUser currentUser(Map<String, Object> user) {
        return new CurrentUser(str(user.get("userId")), str(user.get("loginId")), str(user.get("name")), mapper.rolesForUser(str(user.get("userId"))));
    }

    private Map<String, Object> oneBatchDefinition(String batchId) {
        Map<String, Object> found = mapper.batchDefinitions(batchId, 1, 0).stream().findFirst().orElseThrow(() -> notFound("배치 정의를 찾을 수 없습니다."));
        parseJsonField(found, "executionParameters");
        return found;
    }

    private Map<String, Object> oneBatchExecution(String executionId) {
        Map<String, Object> found = mapper.batchExecutions(executionId, 100, 0).stream()
            .filter(row -> executionId.equals(str(row.get("executionId"))))
            .findFirst()
            .orElseThrow(() -> notFound("배치 실행을 찾을 수 없습니다."));
        parseJsonField(found, "executionParameters");
        return found;
    }

    private Map<String, Object> firstUser(String userId) {
        return normalizeUsers(mapper.users(userId, null, null, 1, 0)).get(0);
    }

    private List<Map<String, Object>> normalizeUsers(List<Map<String, Object>> users) {
        users.forEach(row -> row.put("roleCodes", str(row.get("roleCodes")).isBlank() ? List.of() : List.of(str(row.get("roleCodes")).split(","))));
        return users;
    }

    private void validateUserRole(UserRoleAssignmentRequest request) {
        if (request == null || blank(request.userId()) || blank(request.roleCode()) || !List.of("POSITION_BASED", "MANUAL").contains(request.assignmentType()) || blank(request.validFrom())) throw bad("VALIDATION_ERROR", "사용자 역할 필수값을 확인하세요.", Map.of("userId", "required", "roleCode", "required", "assignmentType", "POSITION_BASED|MANUAL", "validFrom", "required"));
    }

    private Map<String, Object> userRoleRow(String assignmentId, UserRoleAssignmentRequest request) {
        return row("assignmentId", assignmentId, "userId", request.userId(), "roleCode", request.roleCode(), "assignmentType", request.assignmentType(), "validFrom", request.validFrom(), "validTo", request.validTo(), "approverUserId", request.approverUserId());
    }

    private void tryInsertUserRole(Map<String, Object> row) {
        try {
            mapper.insertUserRole(row);
        } catch (Exception ignored) {
            mapper.updateUserRole(row);
        }
    }

    private void upsertRole(Map<String, Object> row) {
        if (mapper.updateRole(row) == 0) mapper.insertRole(row);
    }

    private void upsertMenuPermission(Map<String, Object> row) {
        if (mapper.updateMenuPermission(row) == 0) mapper.insertMenuPermission(row);
    }

    private void upsertMenu(Map<String, Object> row) {
        if (mapper.updateMenu(row) == 0) mapper.insertMenu(row);
    }

    private void upsertCodeGroup(Map<String, Object> row) {
        if (mapper.updateCodeGroup(row) == 0) mapper.insertCodeGroup(row);
    }

    private void upsertDetailCode(Map<String, Object> row) {
        if (mapper.updateDetailCode(row) == 0) mapper.insertDetailCode(row);
    }

    private void validatePositionAssignment(PositionAssignmentRequest request) {
        if (request == null || blank(request.positionCode()) || blank(request.userId()) || blank(request.organizationCode()) || blank(request.validFrom())) {
            throw bad("VALIDATION_ERROR", "보직코드, 대상 사용자, 소속조직, 유효 시작일은 필수입니다.", Map.of("positionCode", "required", "userId", "required", "organizationCode", "required", "validFrom", "required"));
        }
        requireDate(request.validFrom(), "validFrom");
        if (!blank(request.validTo()) && LocalDate.parse(request.validTo()).isBefore(LocalDate.parse(request.validFrom()))) throw bad("VALIDATION_ERROR", "유효 종료일은 시작일보다 빠를 수 없습니다.", Map.of("validTo", "invalidPeriod"));
        if (mapper.byId("user_account", "user_id", request.userId()) == null) throw notFound("보직 대상 사용자를 찾을 수 없습니다.");
        if (mapper.byId("organization", "organization_code", request.organizationCode()) == null) throw notFound("보직 소속조직을 찾을 수 없습니다.");
    }

    private void validateBusinessAssignee(BusinessAssigneeRequest request) {
        if (request == null || blank(request.businessOrganizationCode()) || blank(request.assigneeUserId()) || blank(request.businessAreaCode()) || blank(request.dataScope()) || request.processingPermission() == null || blank(request.validFrom())) {
            throw bad("VALIDATION_ERROR", "업무담당자 필수값을 입력하세요.", Map.of("businessOrganizationCode", "required", "assigneeUserId", "required", "businessAreaCode", "required", "dataScope", "required", "processingPermission", "required", "validFrom", "required"));
        }
        requireDate(request.validFrom(), "validFrom");
        if (!blank(request.validTo()) && LocalDate.parse(request.validTo()).isBefore(LocalDate.parse(request.validFrom()))) throw bad("VALIDATION_ERROR", "지정 종료일은 시작일보다 빠를 수 없습니다.", Map.of("validTo", "invalidPeriod"));
        if (mapper.byId("user_account", "user_id", request.assigneeUserId()) == null) throw notFound("업무담당자를 찾을 수 없습니다.");
        if (mapper.byId("organization", "organization_code", request.businessOrganizationCode()) == null) throw notFound("업무조직을 찾을 수 없습니다.");
    }

    private void validateDataScopeRule(String ruleId, DataScopeRuleRequest request) {
        if (request == null || blank(ruleId) || blank(request.roleCode()) || blank(request.dataScopeType())) throw bad("VALIDATION_ERROR", "데이터 범위 규칙 필수값을 입력하세요.", Map.of("ruleId", "required", "roleCode", "required", "dataScopeType", "required"));
        if (request.ruleId() != null && !ruleId.equals(request.ruleId())) throw bad("VALIDATION_ERROR", "규칙ID는 경로 값과 일치해야 합니다.", Map.of("ruleId", "pathMismatch"));
        if (!List.of("SELF", "DEPARTMENT", "COLLEGE", "BUSINESS_AREA", "ALL").contains(request.dataScopeType())) throw bad("VALIDATION_ERROR", "데이터 범위 유형이 올바르지 않습니다.", Map.of("dataScopeType", "SELF|DEPARTMENT|COLLEGE|BUSINESS_AREA|ALL"));
        if (mapper.byId("role", "role_code", request.roleCode()) == null) throw notFound("역할을 찾을 수 없습니다.");
        if (!blank(request.organizationCode()) && mapper.byId("organization", "organization_code", request.organizationCode()) == null) throw notFound("조직을 찾을 수 없습니다.");
    }

    private boolean scopeAllows(Map<String, Object> rule, DataScopeEvaluationRequest request, String baseDate, Map<String, Object> applied) {
        String scope = str(rule.get("dataScopeType"));
        if ("ALL".equals(scope)) return true;
        if ("SELF".equals(scope)) return !blank(request.userId()) && request.userId().equals(RequestContext.actor());
        if ("DEPARTMENT".equals(scope) || "COLLEGE".equals(scope)) {
            String ruleOrganization = str(rule.get("organizationCode"));
            return !blank(ruleOrganization) && ruleOrganization.equals(request.organizationCode());
        }
        if ("BUSINESS_AREA".equals(scope)) {
            List<Map<String, Object>> assignees = mapper.activeBusinessAssignees(request.userId(), request.businessAreaCode(), baseDate);
            boolean matched = assignees.stream().anyMatch(row -> str(row.get("businessOrganizationCode")).equals(request.organizationCode()) || str(row.get("dataScope")).equals(request.organizationCode()));
            if (matched) applied.put("businessAreaCode", request.businessAreaCode());
            return matched;
        }
        return false;
    }

    private void requireDate(String value, String field) {
        try {
            LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw bad("VALIDATION_ERROR", "날짜 형식을 확인하세요.", Map.of(field, "yyyy-MM-dd"));
        }
    }

    private List<Map<String, Object>> tree(List<Map<String, Object>> rows, String parent) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : rows) {
            String parentValue = source.containsKey("parentMenuId") ? str(source.get("parentMenuId")) : str(source.get("parentOrganizationCode"));
            boolean isRoot = parent == null ? parentValue.isBlank() : parent.equals(parentValue);
            if (isRoot) {
                Map<String, Object> node = new LinkedHashMap<>(source);
                String id = source.containsKey("menuId") ? str(source.get("menuId")) : str(source.get("organizationCode"));
                node.put("children", tree(rows, id));
                result.add(node);
            }
        }
        return result;
    }

    private void history(String entity, String entityId, Object before, Object after, String reason) {
        try {
            mapper.history(UUID.randomUUID().toString(), entity, entityId, before == null ? null : objectMapper.writeValueAsString(before), after == null ? null : objectMapper.writeValueAsString(after), reason, RequestContext.actor());
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZATION_ERROR", "변경 이력 직렬화에 실패했습니다.", Map.of());
        }
    }

    private String json(Object value) {
        if (value == null) return "{}";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "JSON 형식을 확인하세요.", Map.of("executionParameters", "invalidJson"));
        }
    }

    private void parseJsonField(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null || value instanceof Map<?, ?>) return;
        try {
            row.put(key, objectMapper.readValue(String.valueOf(value), Map.class));
        } catch (JsonProcessingException e) {
            row.put(key, Map.of());
        }
    }

    private ApiException bad(String code, String message, Map<String, Object> meta) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, meta);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message, Map.of());
    }

    private String yn(String value) {
        return blank(value) ? "Y" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) row.put(String.valueOf(values[i]), values[i + 1]);
        return row;
    }
}
