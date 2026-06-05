package org.bsl.portal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthEntryPoint restAuthEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthEntryPoint restAuthEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthEntryPoint = restAuthEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        /*
                         * CORS preflight.
                         */
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        /*
                         * Login phải public.
                         */
                        .requestMatchers(
                                "/api/users/login",
                                "/users/login",
                                "/api/auth/login",
                                "/login"
                        ).permitAll()

                        /*
                         * Static frontend resources.
                         */
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/assets/**",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()

                        /*
                         * Swagger/OpenAPI.
                         */
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        /*
                         * Health check.
                         */
                        .requestMatchers(
                                "/actuator/**",
                                "/actuator/health",
                                "/health",
                                "/info"
                        ).permitAll()

                        /*
                         * WebSocket / SockJS handshake.
                         * SockJS gọi /ws/info không gửi Bearer token.
                         */
                        .requestMatchers("/ws/**").permitAll()

                        /*
                         * File/upload public để xem/tải file từ trang thông tin.
                         * Nếu muốn khóa file, cần sửa thêm JwtAuthenticationFilter.
                         */
                        .requestMatchers(HttpMethod.GET, "/files/**", "/uploads/**").permitAll()

                        /*
                         * User management vẫn nên khóa, kể cả GET.
                         * Tránh lộ danh sách/thông tin user.
                         */
                        .requestMatchers("/api/users/**").authenticated()

                        /*
                         * GET API thông tin được public.
                         * Ví dụ:
                         * - GET /api/notices/search
                         * - GET /api/departments/search
                         * - GET /api/app-links/search
                         * - GET /api/forms/search
                         * - GET /api/departments
                         */
                        .requestMatchers("/api/users/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()

                        /*
                         * Các hành động thêm/sửa/xóa/duyệt phải có Bearer token.
                         */
                        .requestMatchers(HttpMethod.POST, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/**").authenticated()

                        /*
                         * Các request còn lại mặc định yêu cầu đăng nhập.
                         */
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:3001",
                "http://127.0.0.1:3001",
                "http://127.0.0.1:8081",
                "http://10.232.100.68:3001",
                "http://10.232.100.68:8081",
                "https://10.232.100.68:3001",
                "https://10.232.100.68:8081",
                "https://10.232.132.48:3001",
                "https://10.232.132.48:8081",
                "https://homepage.youngone.com.vn",
                "https://homepage.youngone.com.vn:3001",
                "https://homepage.youngone.com.vn:8081"
        ));

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
