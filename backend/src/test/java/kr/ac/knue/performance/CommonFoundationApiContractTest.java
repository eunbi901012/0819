package kr.ac.knue.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommonFoundationApiContractTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void openapi_contract_fixture_is_available_on_classpath() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").exists()).isTrue();
    }

    @Test
    void health_endpoint_returns_service_contract_body() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("UP"))
            .andExpect(jsonPath("$.data.service").value("faculty-performance-common"));
    }

    @Test
    void admin_can_login_read_current_user_and_logout_with_session_cookie() throws Exception {
        long loginHistoryBefore = countChangeHistory("session");
        String session = loginAsAdmin();
        assertThat(countChangeHistory("session")).isGreaterThan(loginHistoryBefore);

        mockMvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.loginId").value("admin"))
            .andExpect(jsonPath("$.data.roles[0]").value("R09"));

        long logoutHistoryBefore = countChangeHistory("session", session);
        mockMvc.perform(post("/api/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"테스트 로그아웃\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        assertThat(countChangeHistory("session", session)).isGreaterThan(logoutHistoryBefore);

        mockMvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void protected_management_api_requires_session_and_blocks_non_admin_role() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        String teacherSession = login("teacher", "teacher");
        mockMvc.perform(get("/api/users").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", teacherSession)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void user_management_filters_show_korus_readonly_fields_and_update_local_usage_and_roles() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(get("/api/users?filter=김교수").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].staffNo").value("2024001"))
            .andExpect(jsonPath("$.data.items[0].position").value("교수"))
            .andExpect(jsonPath("$.data.items[0].retirementDate").exists())
            .andExpect(jsonPath("$.data.items[0].lastSyncedAt").exists());

        long usageHistoryBefore = countChangeHistory("user_account", "U-001");
        mockMvc.perform(patch("/api/users/U-001/usage")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systemEnabled\":false,\"reason\":\"테스트 비활성화\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.systemEnabled").value(false))
            .andExpect(jsonPath("$.data.name").value("김교수"));
        assertThat(countChangeHistory("user_account", "U-001")).isGreaterThan(usageHistoryBefore);

        long roleHistoryBefore = countChangeHistory("user_role_assignment", "U-001");
        mockMvc.perform(patch("/api/users/U-001/business-roles")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCodes\":[\"R01\",\"R02\"],\"reason\":\"역할 테스트\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleCodes", hasSize(2)));
        assertThat(countChangeHistory("user_role_assignment", "U-001")).isGreaterThan(roleHistoryBefore);

        mockMvc.perform(patch("/api/users/U-001/usage")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systemEnabled\":null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.systemEnabled").exists());

        mockMvc.perform(patch("/api/users/U-001/usage")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systemEnabled\":true,\"reason\":\"테스트 원복\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.systemEnabled").value(true));
    }

    @Test
    void organization_tree_relation_and_period_validation_are_persistent() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(get("/api/organizations?filter=컴퓨터").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].organizationCode").value("DEPT-CS"));

        mockMvc.perform(get("/api/organizations/tree").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].children").isArray());

        mockMvc.perform(post("/api/organization-relations")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationCode\":\"DEPT-CS\",\"parentOrganizationCode\":\"COL-EDU\",\"effectiveStartDate\":\"2026-03-01\",\"effectiveEndDate\":\"2026-02-01\",\"reason\":\"기간 오류\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.effectiveEndDate").exists());

        long organizationHistoryBefore = countChangeHistory("organization", "DEPT-CS");
        mockMvc.perform(post("/api/organization-relations")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationCode\":\"DEPT-CS\",\"parentOrganizationCode\":\"COL-EDU\",\"effectiveStartDate\":\"2026-03-01\",\"effectiveEndDate\":\"2026-12-31\",\"reason\":\"조직 개편\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.parentOrganizationCode").value("COL-EDU"));
        assertThat(countChangeHistory("organization", "DEPT-CS")).isGreaterThan(organizationHistoryBefore);
    }

    @Test
    void roles_keep_role_code_immutable_and_support_criteria_updates() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(get("/api/roles").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(9)))
            .andExpect(jsonPath("$.data.items[8].roleCode").value("R09"));

        long createRoleHistoryBefore = countChangeHistory("role", "R10");
        mockMvc.perform(post("/api/roles")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"R10\",\"roleName\":\"계약테스트역할\",\"purpose\":\"계약 테스트\",\"assignmentCriteria\":\"테스트 지정\",\"defaultDataScope\":\"ALL\",\"useYn\":\"Y\",\"reason\":\"역할 등록\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleCode").value("R10"))
            .andExpect(jsonPath("$.data.roleName").value("계약테스트역할"));
        assertThat(countChangeHistory("role", "R10")).isGreaterThan(createRoleHistoryBefore);

        long updateRoleHistoryBefore = countChangeHistory("role", "R08");
        mockMvc.perform(patch("/api/roles/R08")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"R08\",\"roleName\":\"점수산출 감사자\",\"purpose\":\"산출 과정과 근거를 조회\",\"assignmentCriteria\":\"감사자 지정\",\"defaultDataScope\":\"AUDIT\",\"useYn\":\"Y\",\"reason\":\"역할 수정\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.defaultDataScope").value("AUDIT"));
        assertThat(countChangeHistory("role", "R08")).isGreaterThan(updateRoleHistoryBefore);

        mockMvc.perform(patch("/api/roles/R09")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"R01\",\"roleName\":\"시스템관리자\",\"purpose\":\"관리\",\"assignmentCriteria\":\"임명\",\"defaultDataScope\":\"ALL\",\"useYn\":\"Y\",\"reason\":\"코드 불변\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.roleCode").exists());
    }

    @Test
    void user_role_assignment_grant_update_revoke_and_double_revoke_conflict() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(get("/api/user-role-assignments?filter=U-001")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].userId").value("U-001"));

        long createHistoryBefore = countChangeHistory("user_role_assignment");
        mockMvc.perform(post("/api/user-role-assignments")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"roleCode\":\"R02\",\"assignmentType\":\"MANUAL\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\",\"approverUserId\":\"U-ADMIN\",\"reason\":\"부여\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignmentType").value("MANUAL"));
        assertThat(countChangeHistory("user_role_assignment")).isGreaterThan(createHistoryBefore);

        prepareActiveUserRoleAssignment("URA-CONTRACT-PATCH", "U-001", "R03");
        long updateHistoryBefore = countChangeHistory("user_role_assignment", "URA-CONTRACT-PATCH");
        mockMvc.perform(patch("/api/user-role-assignments/URA-CONTRACT-PATCH")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"roleCode\":\"R03\",\"assignmentType\":\"MANUAL\",\"validFrom\":\"2026-02-01\",\"validTo\":\"2026-12-31\",\"approverUserId\":\"U-ADMIN\",\"reason\":\"변경\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.validFrom").value("2026-02-01"));
        assertThat(countChangeHistory("user_role_assignment", "URA-CONTRACT-PATCH")).isGreaterThan(updateHistoryBefore);

        prepareActiveUserRoleAssignment("URA-CONTRACT-DELETE", "U-001", "R04");
        long deleteHistoryBefore = countChangeHistory("user_role_assignment", "URA-CONTRACT-DELETE");
        mockMvc.perform(delete("/api/user-role-assignments/URA-CONTRACT-DELETE")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"회수\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REVOKED"));
        assertThat(countChangeHistory("user_role_assignment", "URA-CONTRACT-DELETE")).isGreaterThan(deleteHistoryBefore);

        mockMvc.perform(delete("/api/user-role-assignments/URA-CONTRACT-DELETE")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"재회수\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void menu_permissions_navigation_menu_tree_and_menu_info_are_managed() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(get("/api/menu-permissions?targetType=ROLE&targetId=R09")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].targetType").value("ROLE"));

        mockMvc.perform(put("/api/menu-permissions")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"BAD\",\"targetId\":\"R09\",\"permissions\":[],\"reason\":\"검증\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.targetType").exists());

        long permissionHistoryBefore = countChangeHistory("menu_permission", "ROLE:R09");
        mockMvc.perform(put("/api/menu-permissions")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"ROLE\",\"targetId\":\"R09\",\"permissions\":[{\"menuId\":\"MENU-USER\",\"allowed\":true}],\"reason\":\"권한 저장\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].targetType").value("ROLE"));
        assertThat(countChangeHistory("menu_permission", "ROLE:R09")).isGreaterThan(permissionHistoryBefore);

        mockMvc.perform(get("/api/navigation/menus").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(9)));

        mockMvc.perform(get("/api/menus").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].menuId").exists());

        mockMvc.perform(get("/api/menus/tree").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].menuName").value("시스템 관리"));

        long parentHistoryBefore = countChangeHistory("menu", "MENU-USER");
        mockMvc.perform(patch("/api/menus/MENU-USER/parent")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentMenuId\":\"MENU-USER-ORG\",\"displayOrder\":1,\"reason\":\"구조 변경\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.parentMenuId").value("MENU-USER-ORG"));
        assertThat(countChangeHistory("menu", "MENU-USER")).isGreaterThan(parentHistoryBefore);

        long reorderHistoryBefore = countChangeHistory("menu", "reorder");
        mockMvc.perform(patch("/api/menus/reorder")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"menuId\":\"MENU-USER\",\"displayOrder\":2}],\"reason\":\"순서 변경\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].menuId").exists());
        assertThat(countChangeHistory("menu", "reorder")).isGreaterThan(reorderHistoryBefore);

        long createMenuHistoryBefore = countChangeHistory("menu", "MENU-TST");
        mockMvc.perform(post("/api/menus")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuId\":\"MENU-TST\",\"parentMenuId\":\"MENU-MANAGE\",\"menuLevel\":\"LEAF\",\"menuName\":\"테스트 메뉴\",\"displayOrder\":99,\"screenId\":\"SCR-TST\",\"url\":\"/admin/test\",\"icon\":\"dot\",\"businessCategory\":\"시스템\",\"description\":\"테스트\",\"useYn\":\"Y\",\"reason\":\"등록\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.screenId").value("SCR-TST"));
        assertThat(countChangeHistory("menu", "MENU-TST")).isGreaterThan(createMenuHistoryBefore);

        long updateMenuHistoryBefore = countChangeHistory("menu", "MENU-TST");
        mockMvc.perform(patch("/api/menus/MENU-TST")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuId\":\"MENU-TST\",\"parentMenuId\":\"MENU-MANAGE\",\"menuLevel\":\"LEAF\",\"menuName\":\"테스트 메뉴 수정\",\"displayOrder\":98,\"screenId\":\"SCR-TST\",\"url\":\"/admin/test\",\"icon\":\"dot\",\"businessCategory\":\"시스템\",\"description\":\"테스트\",\"useYn\":\"Y\",\"reason\":\"수정\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuName").value("테스트 메뉴 수정"));
        assertThat(countChangeHistory("menu", "MENU-TST")).isGreaterThan(updateMenuHistoryBefore);
    }

    @Test
    void code_group_and_detail_code_support_list_create_update_and_validation() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(get("/api/code-groups").cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].groupId").exists());

        long createGroupHistoryBefore = countChangeHistory("code_group", "EVAL_STATUS");
        mockMvc.perform(post("/api/code-groups")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":\"EVAL_STATUS\",\"groupName\":\"평가상태\",\"description\":\"상태\",\"managementDepartment\":\"교수지원과\",\"useYn\":\"Y\",\"reason\":\"등록\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.groupName").value("평가상태"));
        assertThat(countChangeHistory("code_group", "EVAL_STATUS")).isGreaterThan(createGroupHistoryBefore);

        long updateGroupHistoryBefore = countChangeHistory("code_group", "EVAL_STATUS");
        mockMvc.perform(patch("/api/code-groups/EVAL_STATUS")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":\"EVAL_STATUS\",\"groupName\":\"평가상태 수정\",\"description\":\"상태\",\"managementDepartment\":\"교수지원과\",\"useYn\":\"Y\",\"reason\":\"수정\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.groupName").value("평가상태 수정"));
        assertThat(countChangeHistory("code_group", "EVAL_STATUS")).isGreaterThan(updateGroupHistoryBefore);

        mockMvc.perform(post("/api/code-groups/EVAL_STATUS/codes")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeValue\":\"OPEN\",\"codeName\":\"진행\",\"sortOrder\":-1,\"extraAttributes\":{\"color\":\"blue\"},\"useYn\":\"Y\",\"reason\":\"검증\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.sortOrder").exists());

        long createCodeHistoryBefore = countChangeHistory("detail_code", "EVAL_STATUS:OPEN");
        mockMvc.perform(post("/api/code-groups/EVAL_STATUS/codes")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeValue\":\"OPEN\",\"codeName\":\"진행\",\"parentCodeValue\":null,\"sortOrder\":1,\"extraAttributes\":{\"color\":\"blue\"},\"useYn\":\"Y\",\"reason\":\"등록\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.codeValue").value("OPEN"));
        assertThat(countChangeHistory("detail_code", "EVAL_STATUS:OPEN")).isGreaterThan(createCodeHistoryBefore);

        long updateCodeHistoryBefore = countChangeHistory("detail_code", "EVAL_STATUS:OPEN");
        mockMvc.perform(patch("/api/code-groups/EVAL_STATUS/codes/OPEN")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeValue\":\"OPEN\",\"codeName\":\"진행중\",\"parentCodeValue\":null,\"sortOrder\":2,\"extraAttributes\":{\"color\":\"green\"},\"useYn\":\"Y\",\"reason\":\"상세코드 수정\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.codeName").value("진행중"));
        assertThat(countChangeHistory("detail_code", "EVAL_STATUS:OPEN")).isGreaterThan(updateCodeHistoryBefore);

        mockMvc.perform(get("/api/code-groups/EVAL_STATUS/codes")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].codeName", containsString("진행")));
    }

    private long countChangeHistory(String entityName) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM change_history WHERE entity_name = ?",
            Long.class,
            entityName
        );
        return count == null ? 0L : count;
    }

    private long countChangeHistory(String entityName, String entityId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM change_history WHERE entity_name = ? AND entity_id = ?",
            Long.class,
            entityName,
            entityId
        );
        return count == null ? 0L : count;
    }

    private void prepareActiveUserRoleAssignment(String assignmentId, String userId, String roleCode) {
        jdbcTemplate.update("DELETE FROM user_role_assignment WHERE assignment_id = ?", assignmentId);
        jdbcTemplate.update(
            "INSERT INTO user_role_assignment (assignment_id, user_id, role_code, assignment_type, valid_from, valid_to, approver_user_id, status) VALUES (?, ?, ?, 'MANUAL', DATE '2026-01-01', DATE '2026-12-31', 'U-ADMIN', 'ACTIVE')",
            assignmentId,
            userId,
            roleCode
        );
    }

    private String loginAsAdmin() throws Exception {
        return login("admin", "admin");
    }

    private String login(String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("AIOPS_SESSION"))
            .andReturn();
        return result.getResponse().getCookie("AIOPS_SESSION").getValue();
    }
}
