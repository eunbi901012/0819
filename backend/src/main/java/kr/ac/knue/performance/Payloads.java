package kr.ac.knue.performance;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

record PageResult(List<Map<String, Object>> items, int page, int size, int total) {
}

record CurrentUser(String userId, String loginId, String name, List<String> roles) {
}

record LoginRequest(String loginId, String password) {
}

record GenericRequest(String reason) {
}

record UserUsageRequest(Boolean systemEnabled, String reason) {
}

record BusinessRoleRequest(List<String> roleCodes, String reason) {
}

record OrganizationRelationRequest(String organizationCode, String parentOrganizationCode, String effectiveStartDate, String effectiveEndDate, String reason) {
}

record RoleRequest(String roleCode, String roleName, String purpose, String assignmentCriteria, String defaultDataScope, String useYn, String reason) {
}

record UserRoleAssignmentRequest(String userId, String roleCode, String assignmentType, String validFrom, String validTo, String approverUserId, String reason) {
}

record RevokeRoleRequest(String reason) {
}

record MenuPermissionItem(String menuId, Boolean allowed) {
}

record MenuPermissionRequest(String targetType, String targetId, List<MenuPermissionItem> permissions, String reason) {
}

record MenuParentRequest(String parentMenuId, Integer displayOrder, String reason) {
}

record MenuReorderItem(String menuId, Integer displayOrder) {
}

record MenuReorderRequest(List<MenuReorderItem> items, String reason) {
}

record MenuRequest(String menuId, String parentMenuId, String menuLevel, String menuName, Integer displayOrder, String screenId, String url, String icon, String businessCategory, String description, String useYn, String reason) {
}

record CodeGroupRequest(String groupId, String groupName, String description, String managementDepartment, String useYn, String reason) {
}

record DetailCodeRequest(String codeValue, String codeName, String parentCodeValue, Integer sortOrder, JsonNode extraAttributes, String validFrom, String validTo, String useYn, String reason) {
}
