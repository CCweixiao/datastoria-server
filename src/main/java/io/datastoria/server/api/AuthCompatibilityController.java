package io.datastoria.server.api;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/** NextAuth-compatible read endpoints backed by Spring Security OAuth2 login. */
@RestController
@RequestMapping("/api/auth")
public class AuthCompatibilityController {

  private static final Map<String, String> PROVIDER_NAMES =
      Map.of("google", "Google", "github", "GitHub", "microsoft", "Microsoft Entra ID");

  private final Environment environment;

  public AuthCompatibilityController(Environment environment) {
    this.environment = environment;
  }

  @GetMapping("/providers")
  public Map<String, Object> providers() {
    Map<String, Object> result = new LinkedHashMap<>();
    PROVIDER_NAMES.forEach(
        (id, name) -> {
          String clientId =
              environment.getProperty(
                  "spring.security.oauth2.client.registration." + id + ".client-id");
          if (clientId != null && !clientId.isBlank()) {
            result.put(
                id,
                Map.of(
                    "id",
                    id,
                    "name",
                    name,
                    "type",
                    "oauth",
                    "signinUrl",
                    "/api/auth/signin/" + id,
                    "callbackUrl",
                    "/login/oauth2/code/" + id));
          }
        });
    return result;
  }

  @GetMapping("/signin/{provider}")
  public ResponseEntity<Void> signIn(@PathVariable String provider) {
    if (!providers().containsKey(provider)) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create("/oauth2/authorization/" + provider))
        .build();
  }

  @GetMapping("/session")
  public Mono<Map<String, Object>> session(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return Mono.just(Map.of());
    }
    Map<String, Object> attributes =
        authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal
            ? principal.getAttributes()
            : Map.of();
    String email = value(attributes, "email", "preferred_username", "sub");
    String name = value(attributes, "name", "login", "preferred_username");
    String image = value(attributes, "picture", "avatar_url");
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", authentication.getName());
    putIfPresent(user, "name", name);
    putIfPresent(user, "email", email);
    putIfPresent(user, "image", image);
    return Mono.just(
        Map.of("user", user, "expires", Instant.now().plusSeconds(7 * 24 * 60 * 60).toString()));
  }

  /**
   * Development/non-prod fallback. In the prod profile Spring Security's logout filter intercepts
   * this same POST first and invalidates the authenticated WebSession.
   */
  @PostMapping("/signout")
  public ResponseEntity<Void> signOut() {
    return ResponseEntity.noContent().build();
  }

  private static String value(Map<String, Object> attributes, String... keys) {
    for (String key : keys) {
      Object value = attributes.get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return String.valueOf(value);
      }
    }
    return null;
  }

  private static void putIfPresent(Map<String, Object> target, String key, String value) {
    if (value != null) {
      target.put(key, value);
    }
  }
}
