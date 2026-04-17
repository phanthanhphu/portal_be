package org.bsl.portal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS trước tiên
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // API JWT thường disable CSRF
                .csrf(csrf -> csrf.disable())

                // Không dùng session cho API JWT → STATELESS là chuẩn nhất
                .sessionManagement(session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        // Nếu bạn vẫn muốn giới hạn 1 session/user thì có thể bật lại sau, nhưng với JWT thường không cần
                        // .maximumSessions(1)
                        // .maxSessionsPreventsLogin(false)
                )

                // Quy tắc phân quyền
                .authorizeHttpRequests(auth -> auth
                        // Các endpoint công khai - không cần token
                        .requestMatchers(
                                // Đăng nhập & đăng ký
                                "/users/login",
                                "/api/auth/login",
                                "/login",
                                "/api/users/add",
                                "/users/add",
                                "/users/**",

                                // Swagger & OpenAPI
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**",

                                // Tài nguyên tĩnh (nếu có frontend nhúng)
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/assets/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",

                                // Actuator & health check
                                "/actuator/**",
                                "/actuator/health",
                                "/health",
                                "/info",

                                // Một số endpoint public khác (nếu có)
                                "/users/logout",                // thường logout chỉ xóa client-side token
                                "/users/change-password",       // nếu cho phép không cần token (ít an toàn)
                                "/users/get-swagger-token",      // tùy dự án
                                "/api/**",
                                "/files/**",
                                "/uploads/**"
                        ).permitAll()

                        // Tất cả các request còn lại PHẢI có token hợp lệ
                        .anyRequest().authenticated()
                )

                // Thêm JWT filter trước UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://10.232.132.65:3000",
                "http://10.232.106.178:3000",
                "http://10.232.100.68:3000",
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://127.0.0.1:3000"
                // Nếu deploy production → thêm domain thật, ví dụ: "https://your-frontend.com"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // cache preflight 1 giờ

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
        return new BCryptPasswordEncoder(12); // tăng strength lên 12 là hợp lý 2025+
    }
}