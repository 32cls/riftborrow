package org.acme.api;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * OIDC authorization-code-flow entry points for a cross-origin SPA.
 *
 * <p>The SPA (a different origin) cannot complete the code flow itself, so it bounces the
 * browser to {@code /login}. Quarkus keeps the OIDC client role: the code flow is started by
 * the built-in authentication mechanism, not by a service bean. There is no
 * {@code OidcLoginService}/{@code OidcLogoutService} to inject in this Quarkus version, so
 * instead:
 *
 * <ul>
 *   <li>{@code GET /login} is <em>public</em>: it validates the requested redirect target
 *       against the CORS origin allowlist, stashes it in an {@code HttpOnly} {@code return_to}
 *       cookie, and 303s to the protected {@code /auth/start} trigger. (It must be public
 *       because a protected endpoint's code never runs for an unauthenticated request — the
 *       mechanism intercepts first.)</li>
 *   <li>{@code GET /auth/start} is <em>protected</em>: an unauthenticated browser request is
 *       taken over by the code-flow mechanism, which redirects to the provider. An already
 *       authenticated request short-circuits straight to the SPA.</li>
 *   <li>{@code GET /auth/complete} is the provider <em>callback</em> (the configured
 *       {@code redirect-path}). With
 *       {@code quarkus.oidc.authentication.restore-path-after-redirect=false}, the app serves
 *       this path after the code exchange, so we send the browser back to the SPA.</li>
 *   <li>Logout is manual at {@code /logout}: Discord does not advertise an
 *       {@code end_session_endpoint}, so Quarkus's built-in RP-initiated logout
 *       ({@code quarkus.oidc.logout.*}) cannot be used — it hard-fails boot. Instead the
 *       local OIDC session cookies are cleared and the browser 303s to Discord's documented
 *       RP-logout endpoint, which ends the Discord session and bounces back to the SPA
 *       origin (that origin must be registered in the Discord app's redirect URLs).</li>
 * </ul>
 */
@Path("/")
public class AuthResource {

    private static final String RETURN_TO_COOKIE = "return_to";
    private static final int RETURN_TO_MAX_AGE_SECONDS = 600;

    /**
     * OIDC cookie names — the values of the constants in
     * {@code io.quarkus.oidc.runtime.OidcUtils} (quarkus-oidc 3.39.1). Hardcoded rather than
     * referenced: application code should not depend on Quarkus {@code *.runtime} packages,
     * which are excluded from the native image.
     */
    private static final String OIDC_SESSION_COOKIE = "q_session";
    private static final String OIDC_STATE_COOKIE = "q_auth";
    private static final String OIDC_ACCESS_TOKEN_COOKIE = "q_session_at";
    private static final String OIDC_REFRESH_TOKEN_COOKIE = "q_session_rt";

    /** Discord's documented RP-logout endpoint (Discord does not implement OIDC RP-initiated logout). */
    private static final String DISCORD_RP_LOGOUT = "https://discord.com/api/v10/auth/logout";

    @Inject
    @ConfigProperty(name = "app.spa.base")
    String spaBase;

    @Inject
    @ConfigProperty(name = "quarkus.http.cors.origins")
    String corsOrigins;

    @Inject
    @ConfigProperty(name = "quarkus.oidc.client-id")
    String clientId;

    /**
     * Public entry point the SPA bounces the browser to. Validates the requested deep link,
     * records it in an {@code HttpOnly} cookie, then hands off to the protected
     * {@code /auth/start} trigger to start (or skip) the OIDC flow.
     */
    @GET
    @Path("/login")
    public Response login(@QueryParam("redirect") String redirect) {
        Response.ResponseBuilder resp = Response.seeOther(URI.create("/auth/start"));
        String safe = validateRedirect(redirect);
        if (safe != null) {
            resp = resp.cookie(returnToCookie(safe, RETURN_TO_MAX_AGE_SECONDS));
        }
        return resp.build();
    }

    /**
     * Protected OIDC login trigger. For an unauthenticated browser request the code-flow
     * mechanism intercepts and redirects to the provider (the body never runs). For an
     * already-authenticated request we go straight to the SPA.
     */
    @GET
    @Path("/auth/start")
    @RolesAllowed("user")
    public Response start(@CookieParam(RETURN_TO_COOKIE) String returnTo) {
        return redirectToSpa(returnTo);
    }

    /**
     * Provider callback ({@code redirect-path}). After the code exchange the app serves this
     * path, so we send the browser back to the SPA, honoring the {@code return_to} deep link.
     */
    @GET
    @Path("/auth/complete")
    public Response complete(@CookieParam(RETURN_TO_COOKIE) String returnTo) {
        return redirectToSpa(returnTo);
    }

    /**
     * Manual logout: clears the local OIDC session cookies, then 303s the browser to Discord's
     * documented RP-logout endpoint so the Discord session ends too. The bounce target is the
     * SPA origin (a config value, never user input — no open-redirect risk), which must be
     * registered in the Discord app's redirect URLs.
     */
    @GET
    @Path("/logout")
    public Response logout() {
        String target = DISCORD_RP_LOGOUT
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(spaBase);
        return Response.seeOther(URI.create(target))
                .cookie(clearCookie(OIDC_SESSION_COOKIE))
                .cookie(clearCookie(OIDC_ACCESS_TOKEN_COOKIE))
                .cookie(clearCookie(OIDC_REFRESH_TOKEN_COOKIE))
                .cookie(clearCookie(OIDC_STATE_COOKIE))
                .cookie(clearCookie(RETURN_TO_COOKIE))
                .build();
    }

    private Response redirectToSpa(String returnTo) {
        String target = validateRedirect(returnTo);
        if (target == null) {
            target = spaBase;
        }
        return Response.seeOther(URI.create(target))
                .cookie(returnToCookie("", 0))
                .build();
    }

    private NewCookie returnToCookie(String value, int maxAgeSeconds) {
        // Secure + HttpOnly; the value is only ever read by the API (same-origin), so the
        // default SameSite (Lax) is sufficient and avoids the "None requires Secure" coupling.
        return new NewCookie(RETURN_TO_COOKIE, value, "/", null, null, maxAgeSeconds, true, true);
    }

    private static NewCookie clearCookie(String name) {
        // maxAge 0 expires the cookie; Secure + HttpOnly to match how the originals are set.
        return new NewCookie(name, "", "/", null, null, 0, true, true);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private List<String> allowedOrigins() {
        return Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Only allow redirecting back to an origin the SPA is allowed to use (same list as the
     * CORS origins), to prevent open-redirect abuse. Returns the validated URL or {@code null}.
     */
    private String validateRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return null;
        }
        try {
            URL url = new URL(redirect);
            String origin = url.getProtocol() + "://" + url.getHost()
                    + (url.getPort() == -1 ? "" : ":" + url.getPort());
            for (String allowed : allowedOrigins()) {
                if (allowed.equalsIgnoreCase(origin)) {
                    return redirect;
                }
            }
        } catch (MalformedURLException e) {
            return null;
        }
        return null;
    }
}
