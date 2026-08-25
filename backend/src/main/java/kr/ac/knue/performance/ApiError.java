package kr.ac.knue.performance;

import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> meta) {
}
