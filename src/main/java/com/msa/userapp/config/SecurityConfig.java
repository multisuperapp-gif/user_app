package com.msa.userapp.config;

import com.msa.userapp.security.BearerAuthenticationFilter;
import com.msa.userapp.security.AuthenticatedApiRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final BearerAuthenticationFilter bearerAuthenticationFilter;
    private final AuthenticatedApiRateLimitFilter authenticatedApiRateLimitFilter;

    public SecurityConfig(
            BearerAuthenticationFilter bearerAuthenticationFilter,
            AuthenticatedApiRateLimitFilter authenticatedApiRateLimitFilter
    ) {
        this.bearerAuthenticationFilter = bearerAuthenticationFilter;
        this.authenticatedApiRateLimitFilter = authenticatedApiRateLimitFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) -> response.sendError(401))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/profile/photo/view").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(authenticatedApiRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(bearerAuthenticationFilter, AuthenticatedApiRateLimitFilter.class)
                .build();
    }
}
