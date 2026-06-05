package org.bsl.portal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

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

        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            try {
                if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
                    String userEmail = jwtUtil.getEmailFromToken(token);
                    String role = jwtUtil.getRoleFromToken(token);

                    String authority = "ROLE_USER";

                    if (StringUtils.hasText(role)) {
                        String normalizedRole = role.trim().toUpperCase();
                        authority = normalizedRole.startsWith("ROLE_")
                                ? normalizedRole
                                : "ROLE_" + normalizedRole;
                    }

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userEmail,
                                    null,
                                    List.of(new SimpleGrantedAuthority(authority))
                            );

                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    logger.debug("Valid token → User: {} | Role: {} | Path: {}", userEmail, authority, path);
                } else {
                    logger.warn("Invalid token → Path: {}", path);
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                logger.error("Token validation error → Path: {} | Message: {}", path, e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isStaticResource(String path) {
        return path.startsWith("/files/")
                || path.startsWith("/uploads/")
                || path.matches("/(assets|static|public|css|js|images)/.*")
                || path.matches(".*\\.(css|js|png|jpg|jpeg|gif|webp|ico|svg|woff2?|ttf|eot|pdf|zip|gz|json|xml|txt|html)$")
                || "/favicon.ico".equals(path)
                || "/".equals(path)
                || "/index.html".equals(path);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        /*
         * Không skip /api/notices hoặc /api/notices/search.
         * Search có thể public trong SecurityConfig, nhưng nếu frontend gửi token
         * thì filter vẫn cần chạy để set SecurityContext.
         *
         * Riêng /ws/** phải skip vì SockJS gọi /ws/info không gửi Bearer token.
         */
        return path.matches("^/error.*|/actuator.*|/health$")
                || path.equals("/ws")
                || path.startsWith("/ws/")
                || isStaticResource(path);
    }
}
