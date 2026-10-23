package org.acme.security;

import java.util.Locale;

import io.quarkus.oidc.JavaScriptRequestChecker;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Classifies requests as "JavaScript" for the OIDC code authentication mechanism
 * (io.quarkus.oidc.runtime.CodeAuthenticationMechanism), which consults this bean
 * before deciding between a 302 redirect to the provider and a challenge response.
 *
 * <p>The SPA and the API live on different origins, and the SPA authenticates its
 * fetch() calls with the cross-site session cookie. When such a call is unauthenticated
 * the SPA must be able to <em>detect</em> the failure (response status) and then do a
 * top-level navigation to /login — a 302 redirect to Discord would be useless inside a
 * fetch(). So:
 *
 * <ul>
 *   <li>a request is "JavaScript" if it carries an Origin header (cross-origin fetch/XHR
 *       always sends one; top-level browser navigations never do), or if X-Requested-With
 *       is one of the values Quarkus already recognizes or our CSRF header value;</li>
 *   <li>unauthenticated JavaScript requests get the challenge returned by
 *       {@link #getChallenge(RoutingContext)} — a 401 — because
 *       quarkus.oidc.authentication.java-script-auto-redirect=false;</li>
 *   <li>browser navigations are never "JavaScript", so they keep auto-redirecting to the
 *       provider's authorize URL (this is how /login works).</li>
 * </ul>
 *
 * <p>Note: {@link ChallengeData} lives in a *.runtime package; the reference is
 * unavoidable because it is part of this public interface's contract (default method
 * signature). It is only reachable from application code here, so it stays in the native
 * image.
 */
@ApplicationScoped
public class SpaJavaScriptRequestChecker implements JavaScriptRequestChecker {

    private static final String ORIGIN = "Origin";
    private static final String X_REQUESTED_WITH = "X-Requested-With";

    /** Our CSRF header value (see CsrfFilter); its presence marks a browser JS call. */
    private static final String RIFTBORROW = "riftborrow";

    @Override
    public boolean isJavaScriptRequest(RoutingContext routingContext) {
        // Cross-origin fetch()/XHR always send Origin; top-level navigations never do.
        if (routingContext.request().getHeader(ORIGIN) != null) {
            return true;
        }
        String xrw = routingContext.request().getHeader(X_REQUESTED_WITH);
        if (xrw == null) {
            return false;
        }
        xrw = xrw.toLowerCase(Locale.ROOT);
        return xrw.equals("javascript") || xrw.equals("xmlhttprequest") || xrw.equals(RIFTBORROW);
    }

    /**
     * Challenge sent to unauthenticated JavaScript requests. Quarkus's built-in default
     * (used when this method returns null) is status 499; the SPA and any client handling
     * standard auth failures expect a 401, so we provide it explicitly.
     */
    @Override
    public ChallengeData getChallenge(RoutingContext routingContext) {
        return new ChallengeData(401, "WWW-Authenticate", "OIDC");
    }
}
