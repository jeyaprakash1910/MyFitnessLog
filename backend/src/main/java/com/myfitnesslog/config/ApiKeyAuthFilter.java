package com.myfitnesslog.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates a request by a shared {@code X-API-Key} header.
 *
 * <p>This is a deliberately minimal boundary for V1: it authenticates the
 * <em>application</em>, not a user. One client (Android) holds one secret. It
 * exists so a public Render URL is not an open door to read or delete personal
 * workout history — see the item-#2 auth ADR / internal note.
 *
 * <p>Behaviour is driven entirely by whether a key is configured:
 * <ul>
 *   <li><b>No key configured</b> (blank {@code app.api-key} — the local dev and
 *       test default): the filter authenticates <em>every</em> request. Auth is
 *       effectively off, so development and the existing test suite run unchanged.
 *   <li><b>Key configured</b> (production, {@code APP_API_KEY} set): only a request
 *       carrying the exact key is authenticated. Everything else is left
 *       unauthenticated and rejected with 401 by the filter chain (except the
 *       explicitly permitted health and preflight requests).
 * </ul>
 *
 * <p>The upgrade path to real per-user auth (JWT / Supabase Auth) is to replace
 * this one filter; the rest of the chain is unaffected.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final String configuredKey;
    private final boolean enabled;

    public ApiKeyAuthFilter(String configuredKey) {
        this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
        this.enabled = !this.configuredKey.isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        if (!enabled) {
            // Auth disabled (no key configured): allow all.
            authenticate("dev-no-auth");
        } else {
            String provided = request.getHeader(HEADER);
            if (provided != null && constantTimeEquals(configuredKey, provided.trim())) {
                authenticate("api-client");
            }
            // Otherwise: leave the context unauthenticated; the authorization
            // rules reject it with 401.
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String principal) {
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, AuthorityUtils.createAuthorityList("ROLE_API"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** Length-constant comparison so a wrong key cannot be timed byte by byte. */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes();
        byte[] y = b.getBytes();
        if (x.length != y.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < x.length; i++) {
            r |= x[i] ^ y[i];
        }
        return r == 0;
    }
}
