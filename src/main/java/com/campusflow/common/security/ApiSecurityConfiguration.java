package com.campusflow.common.security;

import com.campusflow.common.api.ApiResponse;
import com.campusflow.common.api.ErrorCode;
import com.campusflow.common.config.DatabaseWebModule;
import com.campusflow.modules.user.service.UserService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiSecurityConfiguration {

    @Bean
    @Profile(DatabaseWebModule.PROFILE)
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Profile(DatabaseWebModule.PROFILE)
    SecurityFilterChain authenticatedApi(HttpSecurity http, JwtTokens tokens, UserService users,
                                         JsonMapper mapper, Environment environment) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, failure) -> {
            if (failure instanceof AuthenticationServiceException) {
                writeError(response, mapper, ErrorCode.INTERNAL_ERROR);
            } else {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                writeError(response, mapper, ErrorCode.UNAUTHORIZED);
            }
        };
        AccessDeniedHandler forbidden = (request, response, failure) ->
                writeError(response, mapper, ErrorCode.FORBIDDEN);
        return stateless(http)
                .authorizeHttpRequests(authorize -> {
                    if (environment.matchesProfiles("demo")) {
                        authorize.requestMatchers("/api/v1/demo/health").permitAll();
                    }
                    authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/health", "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/activities/*/enrollments",
                                "/api/v1/activities/*/enrollments/me", "/api/v1/enrollments/me",
                                "/api/v1/enrollments/me/*").hasRole("STUDENT")
                        .anyRequest().authenticated();
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(server -> server
                        .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden)
                        .jwt(jwt -> jwt.decoder(tokens.decoder())
                                .jwtAuthenticationConverter(token -> authenticate(token, users)))
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <T extends BearerTokenAuthenticationFilter> T postProcess(T filter) {
                                filter.setAuthenticationFailureHandler(unauthorized::commence);
                                return filter;
                            }
                        }))
                .build();
    }

    @Bean
    @Profile("!(" + DatabaseWebModule.PROFILE + ")")
    SecurityFilterChain healthOnly(HttpSecurity http) throws Exception {
        return stateless(http).authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll()).build();
    }

    private static HttpSecurity stateless(HttpSecurity http) {
        return http.csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    private static UsernamePasswordAuthenticationToken authenticate(Jwt jwt, UserService users) {
        try {
            var user = users.authenticatedProfile(Long.parseLong(jwt.getSubject()),
                            jwt.getClaims().get(JwtTokens.ACCOUNT_VERSION_CLAIM))
                    .filter(profile -> !profile.roles().isEmpty())
                    .orElseThrow(() -> new InvalidBearerTokenException("Account is unavailable"));
            var authorities = user.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .toList();
            return UsernamePasswordAuthenticationToken.authenticated(
                    new CampusFlowPrincipal(user.id(), user.username()), null, authorities);
        } catch (DataAccessException error) {
            throw new AuthenticationServiceException("Authentication store unavailable", error);
        }
    }

    private static void writeError(HttpServletResponse response, JsonMapper mapper, ErrorCode code)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), ApiResponse.failure(code));
    }
}
