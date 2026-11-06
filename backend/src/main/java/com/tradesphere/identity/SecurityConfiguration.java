package com.tradesphere.identity;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Keep Spring's session-backed CSRF protection and session-fixation protection enabled.
        return http
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/readiness", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/session").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/selling-price").hasAuthority("prices:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/*/selling-price").hasAuthority("prices:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers", "/api/v1/customers/**").hasAuthority("customers:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers").hasAuthority("customers:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/customers/*").hasAuthority("customers:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").hasAuthority("catalog:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products").hasAuthority("catalog:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/*").hasAuthority("catalog:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/warehouses", "/api/v1/warehouses/**", "/api/v1/inventory", "/api/v1/inventory/**").hasAuthority("inventory:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/warehouses", "/api/v1/inventory/receipts").hasAuthority("inventory:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/warehouses/*").hasAuthority("inventory:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/**").hasAuthority("orders:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders", "/api/v1/orders/*/confirm", "/api/v1/orders/*/process", "/api/v1/orders/*/cancel", "/api/v1/orders/*/fulfill").hasAuthority("orders:write")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                problem(response, 401, "Authentication required", "Sign in to continue."))
                        .accessDeniedHandler((request, response, exception) ->
                                problem(response, 403, "Access denied", "You do not have permission, or your security token expired.")))
                .formLogin(login -> login.loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, exception) ->
                                problem(response, 401, "Sign-in failed", "The username or password is incorrect.")))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .build();
    }

    private static void problem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        // All values below are server-owned constants, not reflected request input.
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status
                + ",\"title\":\"" + title + "\",\"detail\":\"" + detail + "\"}");
    }
}
