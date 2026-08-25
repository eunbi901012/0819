package kr.ac.knue.performance;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final CommonService service;

    public AuthInterceptor(CommonService service) {
        this.service = service;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod()) || request.getRequestURI().equals("/api/health") || request.getRequestURI().equals("/api/auth/login") || request.getRequestURI().startsWith("/v3/api-docs") || request.getRequestURI().startsWith("/swagger-ui")) {
            return true;
        }
        service.requireSession(sessionId(request));
        return true;
    }

    private String sessionId(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("AIOPS_SESSION".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
