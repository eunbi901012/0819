package kr.ac.knue.performance;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> meta;

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> meta) {
        super(message);
        this.status = status;
        this.code = code;
        this.meta = meta;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> meta() {
        return meta;
    }
}
