package com.myfitnesslog.config;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security policy for the API — the V1 authentication boundary.
 *
 * <p>Stateless REST secured by a single shared {@code X-API-Key} header
 * ({@link ApiKeyAuthFilter}). Sessions, CSRF, and form/basic login are all
 * disabled because there is no browser session and no user identity yet.
 *
 * <p>Open without a key:
 * <ul>
 *   <li>{@code GET /api/v1/health} — Render's liveness probe must never need a
 *       secret.
 *   <li>{@code OPTIONS /**} — CORS preflight carries no custom headers, so it
 *       cannot present the key; the real request that follows is still checked.
 * </ul>
 * Everything else requires the key <em>when one is configured</em>; see
 * {@link ApiKeyAuthFilter} for the key-absent (development) behaviour.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final String apiKey;

    public SecurityConfig(@Value("${app.api-key:}") String apiKey, Environment environment) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();

        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (this.apiKey.isEmpty()) {
            if (prod) {
                // Fail fast rather than deploy a publicly-reachable, unauthenticated API.
                throw new IllegalStateException(
                        "APP_API_KEY is required under the 'prod' profile but is missing or "
                        + "blank. Refusing to start: a production backend must not run with "
                        + "authentication disabled. Set the APP_API_KEY environment variable.");
            }
            log.warn("app.api-key is not set — API-KEY AUTHENTICATION IS DISABLED. "
                    + "This is expected for local development; a deployed backend MUST set "
                    + "APP_API_KEY (enforced under the 'prod' profile).");
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // CORS remains governed by CorsConfig (WebMvcConfigurer); enabling it
                // here lets the security chain honour that policy for preflight.
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .anyRequest().authenticated())
                // Unauthenticated -> 401 (not the default 403), so a missing/invalid
                // key reads correctly to a client.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(new ApiKeyAuthFilter(apiKey),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
