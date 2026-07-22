package com.myfitnesslog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS policy for browser clients.
 *
 * <p>The web client (Milestone 10) is served from the Vite dev server on a
 * different origin than this API, so without an explicit policy every request
 * fails at the preflight and no web page can read anything at all.
 *
 * <p>The policy is deliberately the narrowest one that works:
 *
 * <ul>
 *   <li><b>GET only.</b> The web client is read-only. Android does not use CORS
 *       at all — it is not a browser — so permitting the write verbs here would
 *       widen the surface for no caller.
 *   <li><b>No credentials.</b> V1 has no authentication and sends no cookies.
 *       Allowing credentials would also forbid a wildcard origin later and is
 *       the setting most often paired with an over-broad origin list.
 *   <li><b>Explicit origins, configured not hardcoded.</b> {@code app.cors.allowed-origins}
 *       defaults to the Vite dev server; a deployment sets the real web origin
 *       without a code change. An empty list disables CORS entirely.
 * </ul>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfig(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
