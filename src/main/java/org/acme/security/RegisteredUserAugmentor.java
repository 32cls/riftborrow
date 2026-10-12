package org.acme.security;

import java.time.Duration;

import org.acme.domain.User;
import org.eclipse.microprofile.jwt.JsonWebToken;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.quarkus.oidc.UserInfo;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonString;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RegisteredUserAugmentor implements SecurityIdentityAugmentor {

    private final Cache<String, Long> registeredCache = Caffeine.newBuilder()
               .maximumSize(10_000)
               .expireAfterWrite(Duration.ofMinutes(10))
               .build();

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity,
                                         AuthenticationRequestContext ctx) {
        if (identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken jwt)) {
            return Uni.createFrom().item(identity);
        }
        return ctx.runBlocking(() -> {
            return addUserIdAttribute(identity, jwt);
        });
    }

    @Transactional
    public QuarkusSecurityIdentity addUserIdAttribute(SecurityIdentity identity, JsonWebToken jwt) {
        String key = String.format("%s|%s", jwt.getIssuer(), jwt.getSubject());
        Long userId = registeredCache.getIfPresent(key);
        UserInfo userInfo = identity.getAttribute("userinfo");
        String username = ((JsonString) userInfo.get("username")).getString();
        if (userId == null) {
            userId = User.getOrCreate(jwt.getIssuer(), jwt.getSubject(), username);
            registeredCache.put(key, userId);
        }
        return QuarkusSecurityIdentity.builder(identity)
                .addAttribute("userId", userId)
                .addRole("user")
                .build();
    }
}
