package kr.ac.knue.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScopeManagementApiContractTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void position_assignment_create_persists_and_base_date_list_returns_only_effective_rows() throws Exception {
        cleanupPositionAssignment("PA-CONTRACT");
        String session = loginAsAdmin();
        long historyBefore = countChangeHistory("position_assignment", "PA-CONTRACT");
        long userBefore = countRows("user_account", "user_id", "U-002");
        long orgBefore = countRows("organization", "organization_code", "DEPT-CS");

        mockMvc.perform(post("/api/position-assignments")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentId\":\"PA-CONTRACT\",\"positionCode\":\"DEPT_CHAIR\",\"positionName\":\"학과장\",\"userId\":\"U-002\",\"organizationCode\":\"DEPT-CS\",\"validFrom\":\"2026-03-01\",\"validTo\":\"2026-12-31\",\"reason\":\"보직 계약 저장\",\"name\":\"원천변경금지\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.assignmentId").value("PA-CONTRACT"))
            .andExpect(jsonPath("$.data.positionCode").value("DEPT_CHAIR"));

        mockMvc.perform(get("/api/positions?baseDate=2026-06-01")
                .cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[*].assignmentId", hasItem("PA-CONTRACT")));

        mockMvc.perform(get("/api/positions?baseDate=2027-01-01")
                .cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.total").value(0));

        assertThat(countRows("position_assignment", "assignment_id", "PA-CONTRACT")).isEqualTo(1L);
        assertThat(countChangeHistory("position_assignment", "PA-CONTRACT")).isGreaterThan(historyBefore);
        assertThat(countRows("user_account", "user_id", "U-002")).isEqualTo(userBefore);
        assertThat(countRows("organization", "organization_code", "DEPT-CS")).isEqualTo(orgBefore);
    }

    @Test
    void position_assignment_rejects_invalid_period_and_unauthorized_session_without_insert() throws Exception {
        cleanupPositionAssignment("PA-INVALID");
        String session = loginAsAdmin();

        mockMvc.perform(post("/api/position-assignments")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentId\":\"PA-INVALID\",\"positionCode\":\"DEPT_CHAIR\",\"userId\":\"U-002\",\"organizationCode\":\"DEPT-CS\",\"validFrom\":\"2026-12-31\",\"validTo\":\"2026-01-01\",\"reason\":\"invalid\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.validTo").exists());

        String r01Session = createSessionForUser("TEST-R01-PA", "U-001");
        mockMvc.perform(post("/api/position-assignments")
                .cookie(sessionCookie(r01Session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentId\":\"PA-INVALID\",\"positionCode\":\"DEPT_CHAIR\",\"userId\":\"U-002\",\"organizationCode\":\"DEPT-CS\",\"validFrom\":\"2026-03-01\",\"reason\":\"no auth\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(countRows("position_assignment", "assignment_id", "PA-INVALID")).isZero();
    }

    @Test
    void business_assignee_create_persists_and_scope_evaluation_applies_period_and_business_area() throws Exception {
        cleanupBusinessAssignee("BA-CONTRACT");
        String session = loginAsAdmin();
        long historyBefore = countChangeHistory("business_assignee", "BA-CONTRACT");

        mockMvc.perform(post("/api/business-assignees")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":\"BA-CONTRACT\",\"businessOrganizationCode\":\"COL-EDU\",\"assigneeUserId\":\"U-001\",\"businessAreaCode\":\"EVALUATION\",\"dataScope\":\"COL-EDU\",\"processingPermission\":true,\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\",\"reason\":\"업무담당 계약 저장\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.assigneeId").value("BA-CONTRACT"));

        mockMvc.perform(get("/api/business-assignees")
                .cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[*].assigneeId", hasItem("BA-CONTRACT")));

        mockMvc.perform(post("/api/data-scope-evaluations")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"roleCode\":\"R07\",\"organizationCode\":\"COL-EDU\",\"businessAreaCode\":\"EVALUATION\",\"baseDate\":\"2026-06-01\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.allowed").value(true))
            .andExpect(jsonPath("$.data.appliedConditions.businessAreaCode").value("EVALUATION"));

        mockMvc.perform(post("/api/data-scope-evaluations")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"roleCode\":\"R07\",\"organizationCode\":\"COL-EDU\",\"businessAreaCode\":\"ADMISSION\",\"baseDate\":\"2026-06-01\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.allowed").value(false));

        assertThat(countRows("business_assignee", "assignee_id", "BA-CONTRACT")).isEqualTo(1L);
        assertThat(countChangeHistory("business_assignee", "BA-CONTRACT")).isGreaterThan(historyBefore);
    }

    @Test
    void business_assignee_rejects_missing_business_area_without_insert() throws Exception {
        cleanupBusinessAssignee("BA-INVALID");
        String session = loginAsAdmin();

        mockMvc.perform(post("/api/business-assignees")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":\"BA-INVALID\",\"businessOrganizationCode\":\"COL-EDU\",\"assigneeUserId\":\"U-001\",\"validFrom\":\"2026-01-01\",\"reason\":\"invalid\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.businessAreaCode").exists());

        String r01Session = createSessionForUser("TEST-R01-BA", "U-001");
        mockMvc.perform(post("/api/business-assignees")
                .cookie(sessionCookie(r01Session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":\"BA-INVALID\",\"businessOrganizationCode\":\"COL-EDU\",\"assigneeUserId\":\"U-001\",\"businessAreaCode\":\"EVALUATION\",\"dataScope\":\"COL-EDU\",\"processingPermission\":true,\"validFrom\":\"2026-01-01\",\"reason\":\"forbidden\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(countRows("business_assignee", "assignee_id", "BA-INVALID")).isZero();
    }

    @Test
    void data_scope_rule_save_persists_rejects_invalid_type_and_does_not_change_menu_permission() throws Exception {
        cleanupDataScopeRule("DS-CONTRACT");
        String session = loginAsAdmin();
        long permissionBefore = countRows("menu_permission", "target_type", "ROLE", "target_id", "R09");

        mockMvc.perform(put("/api/data-scope-rules/DS-CONTRACT")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleId\":\"DS-CONTRACT\",\"roleCode\":\"R03\",\"dataScopeType\":\"COLLEGE\",\"organizationCode\":\"COL-EDU\",\"businessAreaCode\":\"EVALUATION\",\"useYn\":\"Y\",\"reason\":\"데이터 범위 저장\",\"menuId\":\"MENU-USER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ruleId").value("DS-CONTRACT"));

        mockMvc.perform(get("/api/data-scope-rules")
                .cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[*].ruleId", hasItem("DS-CONTRACT")));

        mockMvc.perform(put("/api/data-scope-rules/DS-INVALID")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleId\":\"DS-INVALID\",\"roleCode\":\"R03\",\"dataScopeType\":\"INVALID\",\"reason\":\"invalid\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.dataScopeType").exists());

        String r01Session = createSessionForUser("TEST-R01-DS", "U-001");
        mockMvc.perform(put("/api/data-scope-rules/DS-INVALID")
                .cookie(sessionCookie(r01Session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleId\":\"DS-INVALID\",\"roleCode\":\"R03\",\"dataScopeType\":\"ALL\",\"reason\":\"forbidden\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(countRows("data_scope_rule", "rule_id", "DS-CONTRACT")).isEqualTo(1L);
        assertThat(countRows("data_scope_rule", "rule_id", "DS-INVALID")).isZero();
        assertThat(countChangeHistory("data_scope_rule", "DS-CONTRACT")).isGreaterThan(0L);
        assertThat(countRows("menu_permission", "target_type", "ROLE", "target_id", "R09")).isEqualTo(permissionBefore);
    }

    @Test
    void data_scope_evaluation_validates_role_and_applies_out_of_scope_filter_without_source_mutation() throws Exception {
        String session = loginAsAdmin();
        long userBefore = countRows("user_account");

        mockMvc.perform(post("/api/data-scope-evaluations")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"organizationCode\":\"DEPT-CS\",\"baseDate\":\"2026-06-01\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.roleCode").exists());

        mockMvc.perform(post("/api/data-scope-evaluations")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"roleCode\":\"R01\",\"organizationCode\":\"COL-EDU\",\"businessAreaCode\":\"EVALUATION\",\"baseDate\":\"2026-06-01\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.allowed").value(false))
            .andExpect(jsonPath("$.data.appliedConditions.userId").value("U-001"));

        assertThat(countRows("user_account")).isEqualTo(userBefore);
    }

    private Cookie sessionCookie(String session) {
        return new Cookie("AIOPS_SESSION", session);
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"admin\",\"password\":\"admin\"}"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("AIOPS_SESSION"))
            .andReturn();
        return result.getResponse().getCookie("AIOPS_SESSION").getValue();
    }

    private String createSessionForUser(String sessionId, String userId) {
        jdbcTemplate.update("DELETE FROM session WHERE session_id = ?", sessionId);
        jdbcTemplate.update("INSERT INTO session (session_id, user_id, expires_at, status) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '1' HOUR, 'ACTIVE')", sessionId, userId);
        return sessionId;
    }

    private long countChangeHistory(String entityName, String entityId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM change_history WHERE entity_name = ? AND entity_id = ?", Long.class, entityName, entityId);
        return count == null ? 0L : count;
    }

    private long countRows(String table) {
        Long count = switch (table) {
            case "user_account" -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account", Long.class);
            default -> throw new IllegalArgumentException("unsupported row count target");
        };
        return count == null ? 0L : count;
    }

    private long countRows(String table, String idColumn, String id) {
        Long count = switch (table + ":" + idColumn) {
            case "position_assignment:assignment_id" -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM position_assignment WHERE assignment_id = ?", Long.class, id);
            case "business_assignee:assignee_id" -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_assignee WHERE assignee_id = ?", Long.class, id);
            case "data_scope_rule:rule_id" -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM data_scope_rule WHERE rule_id = ?", Long.class, id);
            case "user_account:user_id" -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE user_id = ?", Long.class, id);
            case "organization:organization_code" -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM organization WHERE organization_code = ?", Long.class, id);
            default -> throw new IllegalArgumentException("unsupported row count target");
        };
        return count == null ? 0L : count;
    }

    private long countRows(String table, String firstColumn, String firstValue, String secondColumn, String secondValue) {
        Long count = switch (table + ":" + firstColumn + ":" + secondColumn) {
            case "menu_permission:target_type:target_id" -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM menu_permission WHERE target_type = ? AND target_id = ?", Long.class, firstValue, secondValue);
            default -> throw new IllegalArgumentException("unsupported row count target");
        };
        return count == null ? 0L : count;
    }

    private void cleanupPositionAssignment(String assignmentId) {
        jdbcTemplate.update("DELETE FROM position_assignment WHERE assignment_id = ?", assignmentId);
    }

    private void cleanupBusinessAssignee(String assigneeId) {
        jdbcTemplate.update("DELETE FROM business_assignee WHERE assignee_id = ?", assigneeId);
    }

    private void cleanupDataScopeRule(String ruleId) {
        jdbcTemplate.update("DELETE FROM data_scope_rule WHERE rule_id = ?", ruleId);
    }
}
