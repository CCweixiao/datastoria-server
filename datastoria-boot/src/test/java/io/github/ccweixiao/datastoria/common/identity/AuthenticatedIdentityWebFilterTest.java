package io.github.ccweixiao.datastoria.common.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.server.WebFilterChain;

import reactor.test.StepVerifier;

class AuthenticatedIdentityWebFilterTest {

  @Test
  void derivesTenantUserAndRolesFromAuthenticatedPrincipalWithoutTrustingHeaders() {
    var principal =
        new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
            Map.of(
                "sub", "stable-subject",
                "email", "authenticated@example.com",
                "tenant_id", "tenant-from-provider",
                "roles", List.of("admin")),
            "sub");
    var authentication =
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "test");
    var exchange =
        MockServerWebExchange.builder(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/test")
                    .header("x-datastoria-user-email", "spoofed@example.com"))
            .principal(authentication)
            .build();
    AuthenticatedIdentityWebFilter filter = new AuthenticatedIdentityWebFilter("fallback-tenant");
    WebFilterChain chain =
        ignored ->
            IdentityContext.current()
                .doOnNext(
                    identity -> {
                      assertThat(identity.userId()).isEqualTo("authenticated@example.com");
                      assertThat(identity.tenantId()).isEqualTo("tenant-from-provider");
                      assertThat(identity.roles()).contains("ROLE_USER", "ROLE_ADMIN");
                    })
                .then();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
  }
}
