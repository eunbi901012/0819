package kr.ac.knue.performance;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommonController {
    private final CommonService service;

    public CommonController(CommonService service) {
        this.service = service;
    }

    @GetMapping("/api/health")
    ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(service.health());
    }

    @PostMapping("/api/auth/login")
    ApiResponse<CurrentUser> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        CurrentUser user = service.login(request);
        response.addHeader("Set-Cookie", ResponseCookie.from("AIOPS_SESSION", LastSessionHolder.take())
            .path("/").httpOnly(true).sameSite("Lax").maxAge(8 * 60 * 60).build().toString());
        return ApiResponse.ok(user);
    }

    @PostMapping("/api/auth/logout")
    ApiResponse<Map<String, Object>> logout(@CookieValue(name = "AIOPS_SESSION", required = false) String sessionId, @RequestBody(required = false) GenericRequest request) {
        return ApiResponse.ok(service.logout(sessionId, request));
    }

    @GetMapping("/api/auth/me")
    ApiResponse<CurrentUser> me(@CookieValue(name = "AIOPS_SESSION", required = false) String sessionId) {
        return ApiResponse.ok(service.currentUser(sessionId));
    }

    @GetMapping("/api/navigation/menus")
    ApiResponse<PageResult> navigation() {
        return ApiResponse.ok(service.navigation());
    }

    @GetMapping("/api/users")
    ApiResponse<PageResult> users(@RequestParam(required = false) String filter, @RequestParam(required = false) String roleCode, @RequestParam(required = false) Boolean systemEnabled, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.users(filter, roleCode, systemEnabled, page, size));
    }

    @PatchMapping("/api/users/{userId}/usage")
    ApiResponse<Map<String, Object>> updateUserUsage(@PathVariable String userId, @RequestBody UserUsageRequest request) {
        return ApiResponse.ok(service.updateUserUsage(userId, request));
    }

    @PatchMapping("/api/users/{userId}/business-roles")
    ApiResponse<Map<String, Object>> updateBusinessRoles(@PathVariable String userId, @RequestBody BusinessRoleRequest request) {
        return ApiResponse.ok(service.updateBusinessRoles(userId, request));
    }

    @GetMapping("/api/organizations")
    ApiResponse<PageResult> organizations(@RequestParam(required = false) String filter, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.organizations(filter, page, size));
    }

    @GetMapping("/api/organizations/tree")
    ApiResponse<PageResult> organizationTree() {
        return ApiResponse.ok(service.organizationTree());
    }

    @PostMapping("/api/organization-relations")
    ApiResponse<Map<String, Object>> organizationRelation(@RequestBody OrganizationRelationRequest request) {
        return ApiResponse.ok(service.updateOrganizationRelation(request));
    }

    @GetMapping("/api/roles")
    ApiResponse<PageResult> roles(@RequestParam(required = false) String filter) {
        return ApiResponse.ok(service.roles(filter));
    }

    @PostMapping("/api/roles")
    ApiResponse<Map<String, Object>> createRole(@RequestBody RoleRequest request) {
        return ApiResponse.ok(service.saveRole(null, request));
    }

    @PatchMapping("/api/roles/{roleCode}")
    ApiResponse<Map<String, Object>> updateRole(@PathVariable String roleCode, @RequestBody RoleRequest request) {
        return ApiResponse.ok(service.saveRole(roleCode, request));
    }

    @GetMapping("/api/user-role-assignments")
    ApiResponse<PageResult> userRoles(@RequestParam(required = false) String filter, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.userRoles(filter, page, size));
    }

    @PostMapping("/api/user-role-assignments")
    ApiResponse<Map<String, Object>> createUserRole(@RequestBody UserRoleAssignmentRequest request) {
        return ApiResponse.ok(service.createUserRole(request));
    }

    @PatchMapping("/api/user-role-assignments/{assignmentId}")
    ApiResponse<Map<String, Object>> updateUserRole(@PathVariable String assignmentId, @RequestBody UserRoleAssignmentRequest request) {
        return ApiResponse.ok(service.updateUserRole(assignmentId, request));
    }

    @DeleteMapping("/api/user-role-assignments/{assignmentId}")
    ApiResponse<Map<String, Object>> revokeUserRole(@PathVariable String assignmentId, @RequestBody(required = false) RevokeRoleRequest request) {
        return ApiResponse.ok(service.revokeUserRole(assignmentId, request));
    }

    @GetMapping("/api/menu-permissions")
    ApiResponse<PageResult> menuPermissions(@RequestParam(required = false) String targetType, @RequestParam(required = false) String targetId) {
        return ApiResponse.ok(service.menuPermissions(targetType, targetId));
    }

    @PutMapping("/api/menu-permissions")
    ApiResponse<PageResult> saveMenuPermissions(@RequestBody MenuPermissionRequest request) {
        return ApiResponse.ok(service.saveMenuPermissions(request));
    }

    @GetMapping("/api/menus/tree")
    ApiResponse<PageResult> menuTree() {
        return ApiResponse.ok(service.menuTree());
    }

    @PatchMapping("/api/menus/{menuId}/parent")
    ApiResponse<Map<String, Object>> updateMenuParent(@PathVariable String menuId, @RequestBody MenuParentRequest request) {
        return ApiResponse.ok(service.updateMenuParent(menuId, request));
    }

    @PatchMapping("/api/menus/reorder")
    ApiResponse<PageResult> reorderMenus(@RequestBody MenuReorderRequest request) {
        return ApiResponse.ok(service.reorderMenus(request));
    }

    @GetMapping("/api/menus")
    ApiResponse<PageResult> menus(@RequestParam(required = false) String filter) {
        return ApiResponse.ok(service.menus(filter));
    }

    @PostMapping("/api/menus")
    ApiResponse<Map<String, Object>> createMenu(@RequestBody MenuRequest request) {
        return ApiResponse.ok(service.saveMenu(null, request));
    }

    @PatchMapping("/api/menus/{menuId}")
    ApiResponse<Map<String, Object>> updateMenu(@PathVariable String menuId, @RequestBody MenuRequest request) {
        return ApiResponse.ok(service.saveMenu(menuId, request));
    }

    @GetMapping("/api/code-groups")
    ApiResponse<PageResult> codeGroups(@RequestParam(required = false) String filter) {
        return ApiResponse.ok(service.codeGroups(filter));
    }

    @PostMapping("/api/code-groups")
    ApiResponse<Map<String, Object>> createCodeGroup(@RequestBody CodeGroupRequest request) {
        return ApiResponse.ok(service.saveCodeGroup(null, request));
    }

    @PatchMapping("/api/code-groups/{groupId}")
    ApiResponse<Map<String, Object>> updateCodeGroup(@PathVariable String groupId, @RequestBody CodeGroupRequest request) {
        return ApiResponse.ok(service.saveCodeGroup(groupId, request));
    }

    @GetMapping("/api/code-groups/{groupId}/codes")
    ApiResponse<PageResult> detailCodes(@PathVariable String groupId, @RequestParam(required = false) String filter) {
        return ApiResponse.ok(service.detailCodes(groupId, filter));
    }

    @PostMapping("/api/code-groups/{groupId}/codes")
    ApiResponse<Map<String, Object>> createDetailCode(@PathVariable String groupId, @RequestBody DetailCodeRequest request) {
        return ApiResponse.ok(service.saveDetailCode(groupId, null, request));
    }

    @PatchMapping("/api/code-groups/{groupId}/codes/{codeValue}")
    ApiResponse<Map<String, Object>> updateDetailCode(@PathVariable String groupId, @PathVariable String codeValue, @RequestBody DetailCodeRequest request) {
        return ApiResponse.ok(service.saveDetailCode(groupId, codeValue, request));
    }

    @GetMapping("/api/batch-definitions")
    ApiResponse<PageResult> batchDefinitions(@RequestParam(required = false) String filter, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.batchDefinitions(filter, page, size));
    }

    @PostMapping("/api/batch-definitions")
    ApiResponse<Map<String, Object>> createBatchDefinition(@RequestBody BatchDefinitionRequest request) {
        return ApiResponse.ok(service.saveBatchDefinition(null, request));
    }

    @PatchMapping("/api/batch-definitions/{batchId}")
    ApiResponse<Map<String, Object>> updateBatchDefinition(@PathVariable String batchId, @RequestBody BatchDefinitionRequest request) {
        return ApiResponse.ok(service.saveBatchDefinition(batchId, request));
    }

    @GetMapping("/api/batch-executions")
    ApiResponse<PageResult> batchExecutions(@RequestParam(required = false) String filter, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.batchExecutions(filter, page, size));
    }

    @PostMapping("/api/batch-executions/manual-runs")
    ApiResponse<Map<String, Object>> createBatchManualRun(@RequestBody BatchManualRunRequest request) {
        return ApiResponse.ok(service.createBatchManualRun(request));
    }

    @PatchMapping("/api/batch-executions/{executionId}/stop")
    ApiResponse<Map<String, Object>> updateBatchExecutionStop(@PathVariable String executionId, @RequestBody BatchStopRequest request) {
        return ApiResponse.ok(service.stopBatchExecution(executionId, request));
    }

    @PostMapping("/api/batch-executions/{executionId}/reruns")
    ApiResponse<Map<String, Object>> createBatchExecutionRerun(@PathVariable String executionId, @RequestBody BatchRerunRequest request) {
        return ApiResponse.ok(service.rerunBatchExecution(executionId, request));
    }

    @GetMapping("/api/batch-results/{executionId}")
    ApiResponse<Map<String, Object>> batchExecutionResult(@PathVariable String executionId) {
        return ApiResponse.ok(service.batchExecutionResult(executionId));
    }

    @GetMapping("/api/batch-reprocess-targets")
    ApiResponse<PageResult> batchReprocessTargets(@RequestParam(required = false) String filter, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.batchReprocessTargets(filter, page, size));
    }

    @PostMapping("/api/batch-reprocess-runs")
    ApiResponse<Map<String, Object>> createBatchReprocessRun(@RequestBody BatchReprocessRunRequest request) {
        return ApiResponse.ok(service.createBatchReprocessRun(request));
    }

    @GetMapping("/api/batch-reprocess-runs/{reprocessExecutionId}")
    ApiResponse<Map<String, Object>> batchReprocessRun(@PathVariable String reprocessExecutionId) {
        return ApiResponse.ok(service.batchReprocessRun(reprocessExecutionId));
    }

}
