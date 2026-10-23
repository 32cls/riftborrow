package org.acme.security;

import java.util.Set;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Cross-Site Request Forgery protection for a cross-origin SPA.
 *
 * <p>Because the session cookie is {@code SameSite=None} (the SPA and the API are on
 * different domains), the browser sends it on cross-site requests from <em>any</em> origin.
 * A browser only blocks the attacker from <em>reading</em> the response of a forged request,
 * not the request from landing and mutating state.
 *
 * <p>Requiring a custom header on every state-changing request forces a CORS preflight,
 * which is denied for origins not in the allowlist, so forged requests never fire. The
 * header check below enforces the same rule server-side as defense in depth.
 */
@Provider
public class CsrfFilter implements ContainerRequestFilter {

    public static final String HEADER = "X-Requested-With";
    public static final String EXPECTED_VALUE = "riftborrow";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (MUTATING_METHODS.contains(ctx.getMethod())
                && !EXPECTED_VALUE.equalsIgnoreCase(ctx.getHeaderString(HEADER))) {
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"Missing or invalid " + HEADER + " header\"}")
                    .type("application/json")
                    .build());
        }
    }
}
