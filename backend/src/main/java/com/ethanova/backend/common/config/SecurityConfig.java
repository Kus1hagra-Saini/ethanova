package com.ethanova.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Review 1.
 *
 * <p>All endpoints are intentionally unauthenticated. This is a deliberate,
 * temporary choice: the review milestone focuses on the data platform, not
 * access control. Proper authentication (JWT) will replace this configuration
 * in Phase 2 without changing any controller code.
 *
 * <p>The {@code spring-boot-starter-security} dependency is kept on the
 * classpath so the security integration point stays stable — introducing it
 * later would require re-wiring filter chains and re-testing every endpoint.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless REST API — no server-side sessions, so CSRF tokens are unnecessary.
                .csrf(csrf -> csrf.disable())

                // Explicitly disallow HTTP session creation. Every request is self-contained.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // All REST endpoints — open for Review 1.
                        .requestMatchers("/api/**").permitAll()
                        // Actuator endpoints (health, info, metrics) — required for ops visibility.
                        .requestMatchers("/actuator/**").permitAll()
                        // Everything else (error dispatch, future Swagger paths added in 3.3) — open.
                        .anyRequest().permitAll()
                )

                // Remove default authentication mechanisms so no 401 challenge fires
                // and no browser credential prompt appears.
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable());

        return http.build();
    }
}