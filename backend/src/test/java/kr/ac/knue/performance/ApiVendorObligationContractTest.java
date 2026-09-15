package kr.ac.knue.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
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
class ApiVendorObligationContractTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void auth_logout_expires_session_and_writes_change_history() throws Exception {
        String session = loginAsAdmin();
        long before = countChangeHistory("session", session);

        mockMvc.perform(post("/api/auth/logout")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"vendor obligation logout\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.loggedOut").value(true));

        assertThat(countRows("session", "session_id", session, "status", "EXPIRED")).isEqualTo(1L);
        assertThat(countChangeHistory("session", session)).isGreaterThan(before);
    }

    @Test
    void batch_definition_create_requires_business_fields_persists_row_and_change_history() throws Exception {
        cleanupBatchDefinition("BATCH-VOB-CREATE");
        String session = loginAsAdmin();

        mockMvc.perform(post("/api/batch-definitions")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-VOB-CREATE\",\"batchType\":\"EVALUATION_DATA\",\"scheduleCycle\":\"DAILY 05:00\",\"predecessorBatchId\":\"BATCH-EVAL-DATA\",\"successorBatchId\":null,\"executionParameters\":{\"portal\":\"faculty\"},\"maxExecutionSeconds\":600,\"ownerUserId\":\"U-ADMIN\",\"useYn\":\"Y\",\"reason\":\"vendor create\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batchId").value("BATCH-VOB-CREATE"))
            .andExpect(jsonPath("$.data.executionParameters.portal").value("faculty"));

        assertThat(countRows("batch_definition", "batch_id", "BATCH-VOB-CREATE")).isEqualTo(1L);
        assertThat(countChangeHistory("batch_definition", "BATCH-VOB-CREATE")).isGreaterThan(0L);
    }

    @Test
    void batch_definition_create_rejects_missing_business_reason_without_table_insert() throws Exception {
        cleanupBatchDefinition("BATCH-VOB-INVALID");
        String session = loginAsAdmin();

        mockMvc.perform(post("/api/batch-definitions")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-VOB-INVALID\",\"batchType\":\"EVALUATION_DATA\",\"scheduleCycle\":\"DAILY\",\"maxExecutionSeconds\":600,\"ownerUserId\":\"U-ADMIN\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.reason").exists());

        assertThat(countRows("batch_definition", "batch_id", "BATCH-VOB-INVALID")).isZero();
    }

    @Test
    void batch_definition_patch_requires_auth_and_updates_table_state_and_change_history() throws Exception {
        upsertBatchDefinition("BATCH-VOB-PATCH", "DAILY 03:00");
        String session = loginAsAdmin();
        long before = countChangeHistory("batch_definition", "BATCH-VOB-PATCH");

        mockMvc.perform(patch("/api/batch-definitions/BATCH-VOB-PATCH")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-VOB-PATCH\",\"batchType\":\"EVALUATION_DATA\",\"scheduleCycle\":\"DAILY 06:00\",\"predecessorBatchId\":\"BATCH-EVAL-DATA\",\"successorBatchId\":null,\"executionParameters\":{\"portal\":\"admin\"},\"maxExecutionSeconds\":900,\"ownerUserId\":\"U-ADMIN\",\"useYn\":\"Y\",\"reason\":\"vendor patch\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scheduleCycle").value("DAILY 06:00"))
            .andExpect(jsonPath("$.data.executionParameters.portal").value("admin"));

        assertThat(scheduleCycleForBatchDefinition("BATCH-VOB-PATCH")).isEqualTo("DAILY 06:00");
        assertThat(countChangeHistory("batch_definition", "BATCH-VOB-PATCH")).isGreaterThan(before);
    }

    @Test
    void batch_definition_patch_rejects_unauthorized_and_business_validation() throws Exception {
        upsertBatchDefinition("BATCH-VOB-AUTH", "DAILY 03:00");

        mockMvc.perform(patch("/api/batch-definitions/BATCH-VOB-AUTH")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-VOB-AUTH\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        String session = loginAsAdmin();
        mockMvc.perform(patch("/api/batch-definitions/BATCH-VOB-AUTH")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"DIFFERENT\",\"batchType\":\"EVALUATION_DATA\",\"scheduleCycle\":\"DAILY\",\"maxExecutionSeconds\":100,\"ownerUserId\":\"U-ADMIN\",\"reason\":\"immutable\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.batchId").exists());
    }

    @Test
    void batch_manual_run_requires_auth_validation_persists_execution_result_and_history() throws Exception {
        String session = loginAsAdmin();
        long historyBefore = countChangeHistory("batch_execution");

        MvcResult result = mockMvc.perform(post("/api/batch-executions/manual-runs")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-EVAL-DATA\",\"executionParameters\":{\"scope\":\"portal\"},\"reason\":\"manual obligation\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operationType").value("MANUAL_RUN"))
            .andExpect(jsonPath("$.data.executionStatus").value("RUNNING"))
            .andExpect(jsonPath("$.data.executionId", startsWith("EXEC-")))
            .andReturn();
        String executionId = JsonTestSupport.read(result, "$.data.executionId");

        assertThat(countRows("batch_execution", "execution_id", executionId)).isEqualTo(1L);
        assertThat(countRows("batch_execution_result", "execution_id", executionId)).isEqualTo(1L);
        assertThat(countChangeHistory("batch_execution")).isGreaterThan(historyBefore);
    }

    @Test
    void batch_manual_run_rejects_missing_reason_and_unknown_batch() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(post("/api/batch-executions/manual-runs")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-EVAL-DATA\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.reason").exists());

        mockMvc.perform(post("/api/batch-executions/manual-runs")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-NO-SUCH\",\"reason\":\"unknown\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void batch_stop_requires_auth_validation_changes_state_and_writes_history() throws Exception {
        String session = loginAsAdmin();
        resetSeedExecutionStatus("EXEC-SEED-FAILED", "RUNNING");
        long before = countChangeHistory("batch_execution", "EXEC-SEED-FAILED");

        mockMvc.perform(patch("/api/batch-executions/EXEC-SEED-FAILED/stop")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"stop obligation\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.executionStatus").value("STOP_REQUESTED"));

        assertThat(executionStatusForBatchExecution("EXEC-SEED-FAILED")).isEqualTo("STOP_REQUESTED");
        assertThat(countChangeHistory("batch_execution", "EXEC-SEED-FAILED")).isGreaterThan(before);
    }

    @Test
    void batch_stop_rejects_missing_reason() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(patch("/api/batch-executions/EXEC-SEED-FAILED/stop")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.reason").exists());
    }

    @Test
    void batch_rerun_requires_auth_validation_persists_new_execution_result_and_history() throws Exception {
        String session = loginAsAdmin();
        long historyBefore = countChangeHistory("batch_execution");

        MvcResult result = mockMvc.perform(post("/api/batch-executions/EXEC-SEED-FAILED/reruns")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"executionParameters\":{\"rerun\":true},\"reason\":\"rerun obligation\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operationType").value("RERUN"))
            .andExpect(jsonPath("$.data.executionStatus").value("REQUESTED"))
            .andExpect(jsonPath("$.data.originalExecutionId").value("EXEC-SEED-FAILED"))
            .andReturn();
        String rerunId = JsonTestSupport.read(result, "$.data.executionId");

        assertThat(countRows("batch_execution", "execution_id", rerunId)).isEqualTo(1L);
        assertThat(countRows("batch_execution_result", "execution_id", rerunId)).isEqualTo(1L);
        assertThat(countChangeHistory("batch_execution")).isGreaterThan(historyBefore);
    }

    @Test
    void batch_rerun_rejects_missing_reason_and_unknown_source() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(post("/api/batch-executions/EXEC-SEED-FAILED/reruns")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.reason").exists());

        mockMvc.perform(post("/api/batch-executions/EXEC-NO-SUCH/reruns")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"unknown source\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void batch_result_detail_reads_persistent_execution_result_by_literal_path() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(get("/api/batch-results/EXEC-SEED-FAILED")
                .cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.executionId").value("EXEC-SEED-FAILED"))
            .andExpect(jsonPath("$.data.processedCount", greaterThanOrEqualTo(0)));
    }

    @Test
    void batch_reprocess_run_requires_business_validation_creates_request_id_and_reads_detail() throws Exception {
        cleanupReprocessExecution("REPROC-VOB-DETAIL");
        jdbcTemplate.update("INSERT INTO batch_reprocess_execution (reprocess_execution_id, original_execution_id, target_id, reason, result_status, operator_user_id) VALUES (?, 'EXEC-SEED-FAILED', 'RPT-EXEC-SEED-FAILED', 'detail seed', 'REQUESTED', 'U-ADMIN')", "REPROC-VOB-DETAIL");
        String session = loginAsAdmin();

        mockMvc.perform(get("/api/batch-reprocess-runs/REPROC-VOB-DETAIL")
                .cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reprocessExecutionId").value("REPROC-VOB-DETAIL"))
            .andExpect(jsonPath("$.data.resultStatus").value("REQUESTED"));

        mockMvc.perform(post("/api/batch-reprocess-runs")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalExecutionId\":\"EXEC-SEED-FAILED\",\"targetId\":\"RPT-EXEC-SEED-FAILED\",\"reason\":\"reprocess obligation\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reprocessExecutionId", startsWith("REPROC-")))
            .andExpect(jsonPath("$.data.resultStatus").value("REQUESTED"));
    }

    @Test
    void batch_reprocess_run_rejects_mismatched_source_and_missing_reason() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(post("/api/batch-reprocess-runs")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalExecutionId\":\"EXEC-SEED-FAILED\",\"targetId\":\"RPT-EXEC-SEED-FAILED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.reason").exists());

        mockMvc.perform(post("/api/batch-reprocess-runs")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalExecutionId\":\"EXEC-OTHER\",\"targetId\":\"RPT-EXEC-SEED-FAILED\",\"reason\":\"mismatch\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.targetId").value("mismatch"));
    }

    @Test
    void code_group_create_update_and_detail_code_create_update_write_change_history() throws Exception {
        cleanupCodeGroup("VOB_STATUS");
        String session = loginAsAdmin();

        mockMvc.perform(post("/api/code-groups")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":\"VOB_STATUS\",\"groupName\":\"벤더상태\",\"description\":\"상태\",\"managementDepartment\":\"교수지원과\",\"useYn\":\"Y\",\"reason\":\"group create\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.groupId").value("VOB_STATUS"));
        assertThat(countChangeHistory("code_group", "VOB_STATUS")).isGreaterThan(0L);

        long groupBefore = countChangeHistory("code_group", "VOB_STATUS");
        mockMvc.perform(patch("/api/code-groups/VOB_STATUS")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":\"VOB_STATUS\",\"groupName\":\"벤더상태수정\",\"description\":\"상태\",\"managementDepartment\":\"교수지원과\",\"useYn\":\"Y\",\"reason\":\"group update\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.groupName").value("벤더상태수정"));
        assertThat(countChangeHistory("code_group", "VOB_STATUS")).isGreaterThan(groupBefore);

        mockMvc.perform(post("/api/code-groups/VOB_STATUS/codes")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeValue\":\"OPEN\",\"codeName\":\"진행\",\"sortOrder\":1,\"extraAttributes\":{\"color\":\"blue\"},\"useYn\":\"Y\",\"reason\":\"code create\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.codeValue").value("OPEN"));
        assertThat(countChangeHistory("detail_code", "VOB_STATUS:OPEN")).isGreaterThan(0L);

        long detailBefore = countChangeHistory("detail_code", "VOB_STATUS:OPEN");
        mockMvc.perform(patch("/api/code-groups/VOB_STATUS/codes/OPEN")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeValue\":\"OPEN\",\"codeName\":\"진행중\",\"sortOrder\":2,\"extraAttributes\":{\"color\":\"green\"},\"useYn\":\"Y\",\"reason\":\"code update\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.codeName").value("진행중"));
        assertThat(countChangeHistory("detail_code", "VOB_STATUS:OPEN")).isGreaterThan(detailBefore);
    }

    @Test
    void code_group_update_rejects_immutable_path_mismatch() throws Exception {
        cleanupCodeGroup("VOB_IMMUTABLE");
        jdbcTemplate.update("INSERT INTO code_group (group_id, group_name, use_yn) VALUES ('VOB_IMMUTABLE', '불변', 'Y')");
        String session = loginAsAdmin();

        mockMvc.perform(patch("/api/code-groups/VOB_IMMUTABLE")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":\"OTHER\",\"groupName\":\"불변\",\"useYn\":\"Y\",\"reason\":\"immutable\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.groupId").exists());
    }

    @Test
    void menu_create_update_parent_reorder_and_permission_put_write_change_history() throws Exception {
        cleanupMenu("MENU-VOB");
        String session = loginAsAdmin();

        mockMvc.perform(post("/api/menus")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuId\":\"MENU-VOB\",\"parentMenuId\":\"MENU-MANAGE\",\"menuLevel\":\"LEAF\",\"menuName\":\"벤더 메뉴\",\"displayOrder\":77,\"screenId\":\"SCR-VOB\",\"url\":\"/admin/vendor\",\"icon\":\"dot\",\"businessCategory\":\"시스템\",\"description\":\"벤더\",\"useYn\":\"Y\",\"reason\":\"menu create\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuId").value("MENU-VOB"));
        assertThat(countChangeHistory("menu", "MENU-VOB")).isGreaterThan(0L);

        long updateBefore = countChangeHistory("menu", "MENU-VOB");
        mockMvc.perform(patch("/api/menus/MENU-VOB")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuId\":\"MENU-VOB\",\"parentMenuId\":\"MENU-MANAGE\",\"menuLevel\":\"LEAF\",\"menuName\":\"벤더 메뉴 수정\",\"displayOrder\":76,\"screenId\":\"SCR-VOB\",\"url\":\"/admin/vendor\",\"icon\":\"dot\",\"businessCategory\":\"시스템\",\"description\":\"벤더\",\"useYn\":\"Y\",\"reason\":\"menu update\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuName").value("벤더 메뉴 수정"));
        assertThat(countChangeHistory("menu", "MENU-VOB")).isGreaterThan(updateBefore);

        long parentBefore = countChangeHistory("menu", "MENU-VOB");
        mockMvc.perform(patch("/api/menus/MENU-VOB/parent")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentMenuId\":\"MENU-USER-ORG\",\"displayOrder\":10,\"reason\":\"parent update\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.parentMenuId").value("MENU-USER-ORG"));
        assertThat(countChangeHistory("menu", "MENU-VOB")).isGreaterThan(parentBefore);

        long reorderBefore = countChangeHistory("menu", "reorder");
        mockMvc.perform(patch("/api/menus/reorder")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"menuId\":\"MENU-VOB\",\"displayOrder\":9}],\"reason\":\"menu reorder\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].menuId").exists());
        assertThat(countChangeHistory("menu", "reorder")).isGreaterThan(reorderBefore);

        long permissionBefore = countChangeHistory("menu_permission", "ROLE:R09");
        mockMvc.perform(put("/api/menu-permissions")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"ROLE\",\"targetId\":\"R09\",\"permissions\":[{\"menuId\":\"MENU-VOB\",\"allowed\":true}],\"reason\":\"permission update\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].targetType").value("ROLE"));
        assertThat(countChangeHistory("menu_permission", "ROLE:R09")).isGreaterThan(permissionBefore);
    }

    @Test
    void menu_update_and_permission_put_reject_validation_errors() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(patch("/api/menus/MENU-USER")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuId\":\"OTHER\",\"menuName\":\"오류\",\"displayOrder\":1,\"reason\":\"immutable\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.menuId").exists());

        mockMvc.perform(patch("/api/menus/MENU-USER/parent")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentMenuId\":\"MENU-MANAGE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.displayOrder").exists());

        mockMvc.perform(patch("/api/menus/reorder")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.items").exists());

        mockMvc.perform(put("/api/menu-permissions")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"BAD\",\"targetId\":\"R09\",\"permissions\":[],\"reason\":\"invalid\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.targetType").exists());
    }

    @Test
    void organization_relation_role_and_user_role_writes_record_change_history() throws Exception {
        String session = loginAsAdmin();
        cleanupRole("R10");
        prepareActiveUserRoleAssignment("URA-VOB-PATCH", "U-001", "R03");
        prepareActiveUserRoleAssignment("URA-VOB-DELETE", "U-001", "R04");

        long organizationBefore = countChangeHistory("organization", "DEPT-CS");
        mockMvc.perform(post("/api/organization-relations")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationCode\":\"DEPT-CS\",\"parentOrganizationCode\":\"COL-EDU\",\"effectiveStartDate\":\"2026-03-01\",\"effectiveEndDate\":\"2026-12-31\",\"reason\":\"organization relation\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.organizationCode").value("DEPT-CS"));
        assertThat(countChangeHistory("organization", "DEPT-CS")).isGreaterThan(organizationBefore);

        mockMvc.perform(post("/api/roles")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"R10\",\"roleName\":\"벤더역할\",\"purpose\":\"벤더 검증\",\"assignmentCriteria\":\"수동\",\"defaultDataScope\":\"ALL\",\"useYn\":\"Y\",\"reason\":\"role create\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleCode").value("R10"));
        assertThat(countChangeHistory("role", "R10")).isGreaterThan(0L);

        long roleBefore = countChangeHistory("role", "R10");
        mockMvc.perform(patch("/api/roles/R10")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"R10\",\"roleName\":\"벤더역할수정\",\"purpose\":\"벤더 검증\",\"assignmentCriteria\":\"수동\",\"defaultDataScope\":\"ALL\",\"useYn\":\"Y\",\"reason\":\"role update\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleName").value("벤더역할수정"));
        assertThat(countChangeHistory("role", "R10")).isGreaterThan(roleBefore);

        mockMvc.perform(post("/api/user-role-assignments")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"roleCode\":\"R02\",\"assignmentType\":\"MANUAL\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\",\"approverUserId\":\"U-ADMIN\",\"reason\":\"assignment create\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignmentType").value("MANUAL"));
        assertThat(countChangeHistory("user_role_assignment")).isGreaterThan(0L);

        long userRoleUpdateBefore = countChangeHistory("user_role_assignment", "URA-VOB-PATCH");
        mockMvc.perform(patch("/api/user-role-assignments/URA-VOB-PATCH")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"roleCode\":\"R03\",\"assignmentType\":\"MANUAL\",\"validFrom\":\"2026-02-01\",\"validTo\":\"2026-12-31\",\"approverUserId\":\"U-ADMIN\",\"reason\":\"assignment update\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.validFrom").value("2026-02-01"));
        assertThat(countChangeHistory("user_role_assignment", "URA-VOB-PATCH")).isGreaterThan(userRoleUpdateBefore);

        long userRoleDeleteBefore = countChangeHistory("user_role_assignment", "URA-VOB-DELETE");
        mockMvc.perform(delete("/api/user-role-assignments/URA-VOB-DELETE")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"assignment delete\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REVOKED"));
        assertThat(countChangeHistory("user_role_assignment", "URA-VOB-DELETE")).isGreaterThan(userRoleDeleteBefore);
    }

    @Test
    void user_usage_and_business_roles_update_local_state_and_change_history() throws Exception {
        String session = loginAsAdmin();

        long usageBefore = countChangeHistory("user_account", "U-001");
        mockMvc.perform(patch("/api/users/U-001/usage")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systemEnabled\":false,\"reason\":\"usage obligation\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.systemEnabled").value(false));
        assertThat(countChangeHistory("user_account", "U-001")).isGreaterThan(usageBefore);

        long rolesBefore = countChangeHistory("user_role_assignment", "U-001");
        mockMvc.perform(patch("/api/users/U-001/business-roles")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCodes\":[\"R01\",\"R02\"],\"reason\":\"business role obligation\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("U-001"));
        assertThat(countChangeHistory("user_role_assignment", "U-001")).isGreaterThan(rolesBefore);

        mockMvc.perform(patch("/api/users/U-001/usage")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systemEnabled\":true,\"reason\":\"usage restore\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.systemEnabled").value(true));
    }

    @Test
    void organization_relation_role_and_user_role_validation_errors_are_enforced() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(post("/api/organization-relations")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationCode\":\"DEPT-CS\",\"effectiveStartDate\":\"2026-03-01\",\"effectiveEndDate\":\"2026-02-01\",\"reason\":\"invalid\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.effectiveEndDate").exists());

        mockMvc.perform(post("/api/roles")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"R11\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.roleName").exists());

        mockMvc.perform(patch("/api/roles/R09")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"R01\",\"roleName\":\"관리자\",\"purpose\":\"관리\",\"reason\":\"immutable\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.roleCode").exists());

        mockMvc.perform(post("/api/user-role-assignments")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"roleCode\":\"R01\",\"assignmentType\":\"BAD\",\"validFrom\":\"2026-01-01\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.assignmentType").exists());

        mockMvc.perform(delete("/api/user-role-assignments/URA-NO-SUCH")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"not found\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void missing_vendor_obligations_are_backed_by_literal_paths_request_history_and_source_state_assertions() throws Exception {
        cleanupBatchDefinition("BATCH-VOB-STRICT");
        cleanupBusinessAssignee("BA-VOB-STRICT");
        cleanupPositionAssignment("PA-VOB-STRICT");
        cleanupDataScopeRule("DS-VOB-STRICT");
        String session = loginAsAdmin();
        long sourceUserRowsBefore = countAllRows("user_account");
        long menuPermissionRowsBefore = countMenuPermissionRows("ROLE", "R09");
        long unauthorizedReprocessRowsBefore = countAllRows("batch_reprocess_execution");

        mockMvc.perform(post("/api/batch-reprocess-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalExecutionId\":\"EXEC-SEED-FAILED\",\"targetId\":\"RPT-EXEC-SEED-FAILED\",\"reason\":\"unauthorized strict\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        assertThat(countAllRows("batch_reprocess_execution")).isEqualTo(unauthorizedReprocessRowsBefore);

        mockMvc.perform(post("/api/batch-definitions")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-VOB-STRICT\",\"batchType\":\"EVALUATION_DATA\",\"scheduleCycle\":\"DAILY 05:30\",\"predecessorBatchId\":\"BATCH-EVAL-DATA\",\"successorBatchId\":null,\"executionParameters\":{\"portal\":\"strict\"},\"maxExecutionSeconds\":600,\"ownerUserId\":\"U-ADMIN\",\"useYn\":\"Y\",\"reason\":\"strict create\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batchId").value("BATCH-VOB-STRICT"));
        assertThat(countRows("batch_definition", "batch_id", "BATCH-VOB-STRICT")).isEqualTo(1L);
        assertLatestHistoryHasRequestIdActorAndReason("batch_definition", "BATCH-VOB-STRICT", "strict create");

        mockMvc.perform(patch("/api/batch-definitions/BATCH-VOB-STRICT")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-VOB-STRICT\",\"batchType\":\"EVALUATION_DATA\",\"scheduleCycle\":\"DAILY 06:30\",\"predecessorBatchId\":\"BATCH-EVAL-DATA\",\"successorBatchId\":null,\"executionParameters\":{\"portal\":\"strict-updated\"},\"maxExecutionSeconds\":900,\"ownerUserId\":\"U-ADMIN\",\"useYn\":\"Y\",\"reason\":\"strict patch\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scheduleCycle").value("DAILY 06:30"));
        assertThat(scheduleCycleForBatchDefinition("BATCH-VOB-STRICT")).isEqualTo("DAILY 06:30");
        assertLatestHistoryHasRequestIdActorAndReason("batch_definition", "BATCH-VOB-STRICT", "strict patch");

        MvcResult manual = mockMvc.perform(post("/api/batch-executions/manual-runs")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-EVAL-DATA\",\"executionParameters\":{\"scope\":\"strict\"},\"reason\":\"strict manual\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operationType").value("MANUAL_RUN"))
            .andExpect(jsonPath("$.data.executionStatus").value("RUNNING"))
            .andReturn();
        String manualExecutionId = JsonTestSupport.read(manual, "$.data.executionId");
        assertThat(countRows("batch_execution", "execution_id", manualExecutionId)).isEqualTo(1L);
        assertThat(countRows("batch_execution_result", "execution_id", manualExecutionId)).isEqualTo(1L);
        assertLatestHistoryHasRequestIdActorAndReason("batch_execution", manualExecutionId, "strict manual");

        resetSeedExecutionStatus("EXEC-SEED-FAILED", "RUNNING");
        mockMvc.perform(patch("/api/batch-executions/EXEC-SEED-FAILED/stop")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"strict stop\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.executionStatus").value("STOP_REQUESTED"));
        assertThat(executionStatusForBatchExecution("EXEC-SEED-FAILED")).isEqualTo("STOP_REQUESTED");
        assertLatestHistoryHasRequestIdActorAndReason("batch_execution", "EXEC-SEED-FAILED", "strict stop");

        MvcResult rerun = mockMvc.perform(post("/api/batch-executions/EXEC-SEED-FAILED/reruns")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"executionParameters\":{\"strict\":true},\"reason\":\"strict rerun\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operationType").value("RERUN"))
            .andExpect(jsonPath("$.data.originalExecutionId").value("EXEC-SEED-FAILED"))
            .andReturn();
        String rerunId = JsonTestSupport.read(rerun, "$.data.executionId");
        assertThat(countRows("batch_execution", "execution_id", rerunId)).isEqualTo(1L);
        assertThat(countRows("batch_execution_result", "execution_id", rerunId)).isEqualTo(1L);
        assertLatestHistoryHasRequestIdActorAndReason("batch_execution", rerunId, "strict rerun");

        MvcResult reprocess = mockMvc.perform(post("/api/batch-reprocess-runs")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalExecutionId\":\"EXEC-SEED-FAILED\",\"targetId\":\"RPT-EXEC-SEED-FAILED\",\"reason\":\"strict reprocess\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resultStatus").value("REQUESTED"))
            .andReturn();
        String reprocessId = JsonTestSupport.read(reprocess, "$.data.reprocessExecutionId");
        assertThat(countRows("batch_reprocess_execution", "reprocess_execution_id", reprocessId)).isEqualTo(1L);
        assertLatestHistoryHasRequestIdActorAndReason("batch_reprocess_execution", reprocessId, "strict reprocess");

        mockMvc.perform(post("/api/business-assignees")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":\"BA-VOB-STRICT\",\"businessOrganizationCode\":\"COL-EDU\",\"assigneeUserId\":\"U-001\",\"businessAreaCode\":\"EVALUATION\",\"dataScope\":\"COL-EDU\",\"processingPermission\":true,\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\",\"reason\":\"strict business assignee\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.assigneeId").value("BA-VOB-STRICT"));
        assertThat(countRows("business_assignee", "assignee_id", "BA-VOB-STRICT")).isEqualTo(1L);
        assertLatestHistoryHasRequestIdActorAndReason("business_assignee", "BA-VOB-STRICT", "strict business assignee");

        mockMvc.perform(post("/api/position-assignments")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentId\":\"PA-VOB-STRICT\",\"positionCode\":\"DEPT_CHAIR\",\"positionName\":\"학과장\",\"userId\":\"U-002\",\"organizationCode\":\"DEPT-CS\",\"validFrom\":\"2026-03-01\",\"validTo\":\"2026-12-31\",\"reason\":\"strict position assignment\",\"name\":\"원천변경금지\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.assignmentId").value("PA-VOB-STRICT"));
        assertThat(countRows("position_assignment", "assignment_id", "PA-VOB-STRICT")).isEqualTo(1L);
        assertLatestHistoryHasRequestIdActorAndReason("position_assignment", "PA-VOB-STRICT", "strict position assignment");
        assertThat(countAllRows("user_account")).isEqualTo(sourceUserRowsBefore);

        mockMvc.perform(put("/api/data-scope-rules/DS-VOB-STRICT")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleId\":\"DS-VOB-STRICT\",\"roleCode\":\"R03\",\"dataScopeType\":\"COLLEGE\",\"organizationCode\":\"COL-EDU\",\"businessAreaCode\":\"EVALUATION\",\"useYn\":\"Y\",\"reason\":\"strict data scope\",\"menuId\":\"MENU-USER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ruleId").value("DS-VOB-STRICT"));
        assertThat(countRows("data_scope_rule", "rule_id", "DS-VOB-STRICT")).isEqualTo(1L);
        assertLatestHistoryHasRequestIdActorAndReason("data_scope_rule", "DS-VOB-STRICT", "strict data scope");
        assertThat(countMenuPermissionRows("ROLE", "R09")).isEqualTo(menuPermissionRowsBefore);

        mockMvc.perform(post("/api/data-scope-evaluations")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"roleCode\":\"R07\",\"organizationCode\":\"COL-EDU\",\"businessAreaCode\":\"EVALUATION\",\"baseDate\":\"2026-06-01\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.allowed").value(true))
            .andExpect(jsonPath("$.data.appliedConditions.organizationCode").value("COL-EDU"))
            .andExpect(jsonPath("$.data.appliedConditions.businessAreaCode").value("EVALUATION"));

        mockMvc.perform(post("/api/data-scope-evaluations")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"U-001\",\"organizationCode\":\"DEPT-CS\",\"baseDate\":\"2026-06-01\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.roleCode").exists());
        assertThat(countAllRows("user_account")).isEqualTo(sourceUserRowsBefore);
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

    private long countChangeHistory(String entityName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM change_history WHERE entity_name = ?", Long.class, entityName);
        return count == null ? 0L : count;
    }

    private long countChangeHistory(String entityName, String entityId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM change_history WHERE entity_name = ? AND entity_id = ?", Long.class, entityName, entityId);
        return count == null ? 0L : count;
    }

    private long countRows(String table, String idColumn, String id) {
        Long count = switch (table + ":" + idColumn) {
            case "batch_definition:batch_id" -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM batch_definition WHERE batch_id = ?",
                Long.class,
                id
            );
            case "batch_execution:execution_id" -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM batch_execution WHERE execution_id = ?",
                Long.class,
                id
            );
            case "batch_execution_result:execution_id" -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM batch_execution_result WHERE execution_id = ?",
                Long.class,
                id
            );
            case "batch_reprocess_execution:reprocess_execution_id" -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM batch_reprocess_execution WHERE reprocess_execution_id = ?",
                Long.class,
                id
            );
            case "business_assignee:assignee_id" -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM business_assignee WHERE assignee_id = ?",
                Long.class,
                id
            );
            case "position_assignment:assignment_id" -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM position_assignment WHERE assignment_id = ?",
                Long.class,
                id
            );
            case "data_scope_rule:rule_id" -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_scope_rule WHERE rule_id = ?",
                Long.class,
                id
            );
            default -> throw new IllegalArgumentException("unsupported row count target");
        };
        return count == null ? 0L : count;
    }

    private long countAllRows(String table) {
        Long count = switch (table) {
            case "user_account" -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account", Long.class);
            case "batch_reprocess_execution" -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM batch_reprocess_execution", Long.class);
            default -> throw new IllegalArgumentException("unsupported row count target");
        };
        return count == null ? 0L : count;
    }

    private long countMenuPermissionRows(String targetType, String targetId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM menu_permission WHERE target_type = ? AND target_id = ?",
            Long.class,
            targetType,
            targetId
        );
        return count == null ? 0L : count;
    }

    private void assertLatestHistoryHasRequestIdActorAndReason(String entityName, String entityId, String reason) {
        Map<String, Object> history = jdbcTemplate.queryForMap(
            "SELECT history_id, actor_user_id, reason, after_value FROM change_history WHERE entity_name = ? AND entity_id = ? ORDER BY changed_at DESC LIMIT 1",
            entityName,
            entityId
        );
        assertThat(historyValue(history, "history_id")).isNotBlank();
        assertThat(historyValue(history, "actor_user_id")).isEqualTo("U-ADMIN");
        assertThat(historyValue(history, "reason")).isEqualTo(reason);
        assertThat(historyValue(history, "after_value")).isNotBlank();
    }

    private String historyValue(Map<String, Object> history, String key) {
        Object value = history.get(key);
        if (value == null) value = history.get(key.toUpperCase());
        return value == null ? "" : String.valueOf(value);
    }

    private long countRows(String table, String idColumn, String id, String statusColumn, String status) {
        Long count = switch (table + ":" + idColumn + ":" + statusColumn) {
            case "session:session_id:status" -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM session WHERE session_id = ? AND status = ?",
                Long.class,
                id,
                status
            );
            default -> throw new IllegalArgumentException("unsupported row count target");
        };
        return count == null ? 0L : count;
    }

    private String scheduleCycleForBatchDefinition(String batchId) {
        return jdbcTemplate.queryForObject(
            "SELECT schedule_cycle FROM batch_definition WHERE batch_id = ?",
            String.class,
            batchId
        );
    }

    private String executionStatusForBatchExecution(String executionId) {
        return jdbcTemplate.queryForObject(
            "SELECT execution_status FROM batch_execution WHERE execution_id = ?",
            String.class,
            executionId
        );
    }

    private void cleanupBatchDefinition(String batchId) {
        jdbcTemplate.update("DELETE FROM batch_definition WHERE batch_id = ?", batchId);
    }

    private void upsertBatchDefinition(String batchId, String scheduleCycle) {
        cleanupBatchDefinition(batchId);
        jdbcTemplate.update("INSERT INTO batch_definition (batch_id, batch_type, schedule_cycle, predecessor_batch_id, successor_batch_id, execution_parameters, max_execution_seconds, owner_user_id, use_yn) VALUES (?, 'EVALUATION_DATA', ?, 'BATCH-EVAL-DATA', NULL, '{}', 600, 'U-ADMIN', 'Y')", batchId, scheduleCycle);
    }

    private void resetSeedExecutionStatus(String executionId, String status) {
        jdbcTemplate.update("UPDATE batch_execution SET execution_status = ? WHERE execution_id = ?", status, executionId);
    }

    private void cleanupReprocessExecution(String reprocessExecutionId) {
        jdbcTemplate.update("DELETE FROM batch_reprocess_execution WHERE reprocess_execution_id = ?", reprocessExecutionId);
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

    private void cleanupCodeGroup(String groupId) {
        jdbcTemplate.update("DELETE FROM detail_code WHERE group_id = ?", groupId);
        jdbcTemplate.update("DELETE FROM code_group WHERE group_id = ?", groupId);
    }

    private void cleanupMenu(String menuId) {
        jdbcTemplate.update("DELETE FROM menu_permission WHERE menu_id = ?", menuId);
        jdbcTemplate.update("DELETE FROM menu WHERE menu_id = ?", menuId);
    }

    private void cleanupRole(String roleCode) {
        jdbcTemplate.update("DELETE FROM user_role_assignment WHERE role_code = ?", roleCode);
        jdbcTemplate.update("DELETE FROM role WHERE role_code = ?", roleCode);
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
}
