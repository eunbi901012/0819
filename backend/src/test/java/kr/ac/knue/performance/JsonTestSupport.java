package kr.ac.knue.performance;

import org.springframework.test.web.servlet.MvcResult;

final class JsonTestSupport {
    private JsonTestSupport() {
    }

    static String read(MvcResult result, String path) throws java.io.UnsupportedEncodingException {
        Object value = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        return String.valueOf(value);
    }
}
