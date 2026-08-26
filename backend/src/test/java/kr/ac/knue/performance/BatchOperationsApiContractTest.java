package kr.ac.knue.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class BatchOperationsApiContractTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void batch_definition_create_update_list_persists_and_audits_reason() throws Exception {
        String session = loginAsAdmin();
        long historyBefore = countChangeHistory("batch_definition", "BATCH-TDD-DEF");

        mockMvc.perform(post("/api/batch-definitions")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-TDD-DEF\",\"batchType\":\"EVALUATION_DATA\",\"scheduleCycle\":\"DAILY 04:00\",\"predecessorBatchId\":\"BATCH-EVAL-DATA\",\"successorBatchId\":null,\"executionParameters\":{\"year\":\"2026\"},\"maxExecutionSeconds\":1200,\"ownerUserId\":\"U-ADMIN\",\"useYn\":\"Y\",\"reason\":\"배치 정의 등록\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.batchId").value("BATCH-TDD-DEF"))
            .andExpect(jsonPath("$.data.executionParameters.year").value("2026"));
        assertThat(countChangeHistory("batch_definition", "BATCH-TDD-DEF")).isGreaterThan(historyBefore);

        mockMvc.perform(patch("/api/batch-definitions/BATCH-TDD-DEF")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-TDD-DEF\",\"batchType\":\"EVALUATION_DATA\",\"scheduleCycle\":\"WEEKLY MON 04:00\",\"predecessorBatchId\":\"BATCH-EVAL-DATA\",\"successorBatchId\":null,\"executionParameters\":{\"year\":\"2027\"},\"maxExecutionSeconds\":1800,\"ownerUserId\":\"U-ADMIN\",\"useYn\":\"Y\",\"reason\":\"배치 정의 수정\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scheduleCycle").value("WEEKLY MON 04:00"));

        mockMvc.perform(get("/api/batch-definitions?filter=BATCH-TDD-DEF")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].batchId").value("BATCH-TDD-DEF"))
            .andExpect(jsonPath("$.data.items[0].ownerUserId").value("U-ADMIN"));
    }

    @Test
    void batch_definition_validation_and_non_admin_authorization_block_writes() throws Exception {
        String teacherSession = login("teacher", "teacher");
        mockMvc.perform(post("/api/batch-definitions")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", teacherSession))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-R01-BLOCKED\",\"reason\":\"권한 테스트\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(countRows("batch_definition", "batch_id", "BATCH-R01-BLOCKED")).isZero();

        String adminSession = loginAsAdmin();
        mockMvc.perform(post("/api/batch-definitions")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", adminSession))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchType\":\"EVALUATION_DATA\",\"scheduleCycle\":\"DAILY\",\"maxExecutionSeconds\":30,\"ownerUserId\":\"U-ADMIN\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.meta.batchId").exists())
            .andExpect(jsonPath("$.error.meta.reason").exists());
    }

    @Test
    void batch_execution_manual_stop_rerun_and_result_are_persistent() throws Exception {
        String session = loginAsAdmin();

        MvcResult manual = mockMvc.perform(post("/api/batch-executions/manual-runs")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"BATCH-EVAL-DATA\",\"executionParameters\":{\"year\":\"2026\"},\"reason\":\"수동실행\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operationType").value("MANUAL_RUN"))
            .andExpect(jsonPath("$.data.executionStatus").value("RUNNING"))
            .andReturn();
        String executionId = JsonTestSupport.read(manual, "$.data.executionId");

        mockMvc.perform(get("/api/batch-executions?filter=" + executionId)
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].executionId").value(executionId));

        mockMvc.perform(patch("/api/batch-executions/NO-SUCH-EXEC/stop")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"대상 없음\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        mockMvc.perform(patch("/api/batch-executions/" + executionId + "/stop")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"중지 요청\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.executionStatus").value("STOP_REQUESTED"));

        mockMvc.perform(post("/api/batch-executions/" + executionId + "/reruns")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"executionParameters\":{\"retry\":true},\"reason\":\"재실행\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operationType").value("RERUN"))
            .andExpect(jsonPath("$.data.originalExecutionId").value(executionId));

        mockMvc.perform(get("/api/batch-results/" + executionId)
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.executionId").value(executionId))
            .andExpect(jsonPath("$.data.processedCount", greaterThanOrEqualTo(0)));
    }

    @Test
    void batch_reprocess_targets_run_and_detail_validate_failed_target_boundary() throws Exception {
        String session = loginAsAdmin();

        mockMvc.perform(get("/api/batch-reprocess-targets?filter=EXEC-SEED-FAILED")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].targetId").value("RPT-EXEC-SEED-FAILED"));

        MvcResult created = mockMvc.perform(post("/api/batch-reprocess-runs")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalExecutionId\":\"EXEC-SEED-FAILED\",\"targetId\":\"RPT-EXEC-SEED-FAILED\",\"reason\":\"실패건 재처리\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.originalExecutionId").value("EXEC-SEED-FAILED"))
            .andExpect(jsonPath("$.data.resultStatus").value("REQUESTED"))
            .andReturn();
        String reprocessExecutionId = JsonTestSupport.read(created, "$.data.reprocessExecutionId");

        mockMvc.perform(get("/api/batch-reprocess-runs/" + reprocessExecutionId)
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reprocessExecutionId").value(reprocessExecutionId));

        mockMvc.perform(post("/api/batch-reprocess-runs")
                .cookie(new jakarta.servlet.http.Cookie("AIOPS_SESSION", session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalExecutionId\":\"EXEC-SEED-FAILED\",\"targetId\":\"NO-SUCH-TARGET\",\"reason\":\"경계 위반\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.message", containsString("재처리 대상")));
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
            default -> throw new IllegalArgumentException("unsupported row count target");
        };
        return count == null ? 0L : count;
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
