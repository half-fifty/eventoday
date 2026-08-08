package com.min.edu.common.config;

import java.util.List;

import com.min.edu.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.min.edu.auth.handler.OAuth2AuthenticationFailureHandler;
import com.min.edu.auth.service.CustomOAuth2UserService;
import com.min.edu.common.security.jwt.JwtAuthenticationFilter;
import com.min.edu.common.security.handler.RestAccessDeniedHandler;
import com.min.edu.common.security.handler.RestAuthenticationEntryPoint;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2AuthenticationSuccessHandler authenticationSuccessHandler,
            OAuth2AuthenticationFailureHandler authenticationFailureHandler,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/reissue",
                                "/auth/logout",
                                "/auth/business/verify",
                                "/auth/business/signup",
                                "/auth/business/login"
                        ).permitAll()
                        .requestMatchers("/auth/me").authenticated()
                        .requestMatchers("/notifications/**").authenticated()
                        // PUBLIC 파일(공개 평면도 이미지 등)은 비로그인 사용자도 내려받을 수 있어야 한다.
                        // 접근 가능 여부(PUBLIC/PRIVATE) 판단은 FileService에서 계속 수행한다.
                        .requestMatchers(HttpMethod.GET, "/v1/files/*/download").permitAll()
                        .requestMatchers("/v1/files/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/booth-recruitments/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/events/*/booth-recruitment/management")
                            .authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/events/*/booth-recruitment",
                                "/events/*/booth-recruitment/completion",
                                "/events/*/booth-recruitment/closure")
                            .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/events/*/booth-recruitment").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/events/*/booth-recruitment").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events/*/booths/public").permitAll()
                        .requestMatchers("/events/*/booths/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events/*/venue-maps/public").permitAll()
                        .requestMatchers("/events/*/venue-maps/**").authenticated()
                        .requestMatchers("/events/*/exchange-code-requests").authenticated()
                        .requestMatchers("/exchange-code-requests/**").authenticated()
                        .requestMatchers("/admin/exchange-code-requests").authenticated()
                        .requestMatchers("/admin/exchange-code-requests/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events/*/exchange-codes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/members/me/exchange-codes").authenticated()
                        .requestMatchers(HttpMethod.POST, "/exchange-codes/validation").authenticated()
                        .requestMatchers(HttpMethod.POST, "/exchange-codes/redemption").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService))
                        .successHandler(authenticationSuccessHandler)
                        .failureHandler(authenticationFailureHandler))
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-url}") String frontendUrl) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
