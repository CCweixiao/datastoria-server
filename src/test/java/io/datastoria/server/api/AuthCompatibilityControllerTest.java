package io.datastoria.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class AuthCompatibilityControllerTest {

  @Test
  void listsConfiguredProvidersAndRedirectsToSpringAuthorization() {
    var environment =
        new MockEnvironment()
            .withProperty(
                "spring.security.oauth2.client.registration.github.client-id", "test-client");
    var controller = new AuthCompatibilityController(environment);

    assertThat(controller.providers()).containsOnlyKeys("github");
    assertThat(controller.providers().get("github").toString()).contains("GitHub");
    var response = controller.signIn("github");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation().toString())
        .isEqualTo("/oauth2/authorization/github");
    assertThat(controller.signIn("google").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void returnsNextAuthCompatibleSessionWithoutExposingTokens() {
    var principal =
        new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
            Map.of(
                "sub", "user-123",
                "email", "user@example.com",
                "name", "Example User",
                "picture", "https://example.com/avatar.png",
                "access_token", "must-not-leak"),
            "sub");
    var authentication =
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "github");
    var controller = new AuthCompatibilityController(new MockEnvironment());

    Map<String, Object> session = controller.session(authentication).block();

    assertThat(session).isNotNull();
    assertThat(session.toString())
        .contains("user@example.com", "Example User")
        .doesNotContain("must-not-leak");
    assertThat(session).containsKey("expires");
  }
}
