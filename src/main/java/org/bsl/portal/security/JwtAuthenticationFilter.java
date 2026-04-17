package org.bsl.portal.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        logger.debug("JwtFilter processing → {} {}", method, path);

        // Extract token từ header Authorization
        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            logger.debug("Bearer token found → length: {}", token.length());
        }

        // Nếu có token → validate & set Authentication
        if (token != null && !token.isBlank()) {
            try {
                if (jwtUtil.validateToken(token)) {
                    String userEmail = jwtUtil.getEmailFromToken(token);
                    logger.info("Valid token → User: {} | Path: {}", userEmail, path);

                    // Tạo Authentication (có thể mở rộng authorities sau)
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userEmail, null, Collections.emptyList());

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    logger.warn("Invalid token → Path: {}", path);
                }
            } catch (Exception e) {
                logger.error("Token validation error → Path: {} | Message: {}", path, e.getMessage());
            }
        }

        // Luôn tiếp tục filter chain
        // - Nếu public endpoint (permitAll) → request đi tiếp dù không có auth
        // - Nếu protected → AuthorizationFilter sau sẽ kiểm tra và trả 401 nếu cần
        filterChain.doFilter(request, response);
    }

    private boolean isStaticResource(String path) {
        // Giữ lại nếu bạn có static files cần bypass sớm (nhưng thường không cần vì permitAll đã xử lý)
        return path.startsWith("/uploads/") ||
                path.matches("/(assets|static|public|css|js|images)/.*") ||
                path.matches(".*\\.(css|js|png|jpg|jpeg|gif|webp|ico|svg|woff2?|ttf|eot|pdf|zip|gz|json|xml|txt|html)$") ||
                "/favicon.ico".equals(path) ||
                "/".equals(path) ||
                "/index.html".equals(path);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.matches("^/error.*|/actuator.*|/health$");
    }

    // Nếu bạn vẫn muốn trả JSON error đẹp khi 401 (tùy chọn)
    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        error.put("status", status.value());
        error.put("timestamp", System.currentTimeMillis());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(error);
        response.getWriter().write(json);
        response.getWriter().flush();
    }
}