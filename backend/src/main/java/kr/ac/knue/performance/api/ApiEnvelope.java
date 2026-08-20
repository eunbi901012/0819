package kr.ac.knue.performance.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiEnvelope {
    private ApiEnvelope() {
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("meta", Map.of("timestamp", Instant.now().toString()));
        body.put("data", data);
        return body;
    }

    public static Map<String, Object> error(String code, String message, Map<String, String> fieldErrors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("meta", Map.of("timestamp", Instant.now().toString()));
        body.put("error", Map.of("code", code, "message", message, "fieldErrors", fieldErrors));
        return body;
    }
}
