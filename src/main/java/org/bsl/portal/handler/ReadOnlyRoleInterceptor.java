package org.bsl.portal.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bsl.portal.model.User;
import org.bsl.portal.security.JwtUtil;
import org.bsl.portal.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

@Component
public class ReadOnlyRoleInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> ALLOWED_VIEW_MUTATIONS = Set.of(
            "/api/users/login",
            "/api/auth/login",
            "/api/users/logout",
            "/api/auth/logout",
            "/api/users/change-password"
    );

    private final JwtUtil jwtUtil;
    private final UserService userService;

    public ReadOnlyRoleInterceptor(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        String method = request.getMethod() != null ? request.getMethod().toUpperCase() : "";

        if (SAFE_METHODS.contains(method) || ALLOWED_VIEW_MUTATIONS.contains(request.getRequestURI())) {
            return true;
        }

        String token = extractBearerToken(request.getHeader("Authorization"));

        if (token == null || !jwtUtil.validateToken(token)) {
            return true;
        }

        String role = jwtUtil.getRoleFromToken(token);
        String email = jwtUtil.getEmailFromToken(token);

        if (email != null && !email.trim().isEmpty()) {
            User currentUser = userService.findByEmail(email.trim()).orElse(null);
            if (currentUser != null) {
                role = currentUser.getRole();
            }
        }

        if (!isViewRole(role)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"message\":\"View role is read-only. This action is not allowed.\"}"
        );
        return false;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean isViewRole(String role) {
        if (role == null) {
            return false;
        }

        String normalizedRole = role.trim().toUpperCase();
        return "VIEW".equals(normalizedRole) || "ROLE_VIEW".equals(normalizedRole);
    }
}
