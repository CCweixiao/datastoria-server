package io.github.ccweixiao.datastoria.boot.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

/**
 * Serves the statically exported Next.js frontend (Next.js {@code output: "export"}) from the
 * configured {@code spring.web.resources.static-locations}.
 *
 * <p>The exported build contains {@code route.html} files plus plain assets. The built-in WebFlux
 * resource handler already serves exact file paths; this router adds the pieces it cannot: clean
 * URL resolution ({@code /login} &rarr; {@code login.html}), a 404 fallback page, and a permanent
 * redirect from the legacy {@code /session/{id}} deep links to the query-parameter form.
 *
 * <p>The router is only registered when {@code spring.web.resources.static-locations} is set
 * explicitly (the deployment launcher passes {@code file:app/frontend/}), so local development and
 * tests that rely on Spring's classpath defaults are unaffected. Requests under {@code /api} and
 * {@code /actuator} never match, and a page route only matches when an exported page actually
 * exists, so controller mappings always win.
 */
@Configuration
public class ExportedWebAppConfig {

  private static final MediaType HTML_UTF8 = MediaType.parseMediaType("text/html;charset=UTF-8");

  private final ResourceLoader resourceLoader;
  private final List<String> locations;

  public ExportedWebAppConfig(
      ResourceLoader resourceLoader,
      @Value("${spring.web.resources.static-locations:}") String staticLocations) {
    this.resourceLoader = resourceLoader;
    this.locations =
        Arrays.stream(staticLocations.split(","))
            .map(String::trim)
            .filter(location -> !location.isEmpty())
            .toList();
  }

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  RouterFunction<ServerResponse> exportedWebAppRouter() {
    if (locations.isEmpty()) {
      return null;
    }
    return RouterFunctions.route()
        .GET(
            "/session/{sessionId}",
            request -> true,
            request -> {
              String sessionId = request.pathVariable("sessionId");
              String code = request.queryParam("code").map(c -> "&code=" + c).orElse("");
              return ServerResponse.permanentRedirect(
                      URI.create("/session?sessionId=" + sessionId + code))
                  .build();
            })
        .GET(
            "/{*path}",
            this::isExportedPageRequest,
            request -> {
              Optional<Resource> page = resolve(request.path());
              if (page.isPresent()) {
                return ServerResponse.ok().contentType(HTML_UTF8).bodyValue(page.get());
              }
              return notFoundPage();
            })
        // Any other extension-less navigation path renders the exported 404 page (controllers
        // all live under /api, so this can never shadow them).
        .GET("/{*path}", this::isNavigationRequest, request -> notFoundPage())
        .build();
  }

  /** Matches extension-less navigation paths outside the API namespaces. */
  private boolean isExportedPageRequest(ServerRequest request) {
    return isNavigationRequest(request) && resolve(request.path()).isPresent();
  }

  private boolean isNavigationRequest(ServerRequest request) {
    String path = request.path();
    if (path.startsWith("/api/") || path.startsWith("/actuator/") || path.contains("..")) {
      return false;
    }
    String fileName = path.substring(path.lastIndexOf('/') + 1);
    if (fileName.contains(".")) {
      // Plain assets (/_next/..., *.png, *.xml) are served by the built-in resource handler.
      return false;
    }
    return true;
  }

  /** Resolves a clean URL to {@code path.html}, {@code path/index.html} or {@code index.html}. */
  private Optional<Resource> resolve(String path) {
    String normalized = path.equals("/") ? "" : path;
    return firstExisting(
        normalized + ".html",
        normalized + "/index.html",
        normalized.isEmpty() ? "index.html" : null);
  }

  private Optional<Resource> firstExisting(String... candidates) {
    for (String candidate : candidates) {
      if (candidate == null) {
        continue;
      }
      for (String location : locations) {
        Resource resource = resourceLoader.getResource(location + candidate);
        if (resource.exists() && resource.isReadable()) {
          return Optional.of(resource);
        }
      }
    }
    return Optional.empty();
  }

  private Mono<ServerResponse> notFoundPage() {
    Optional<Resource> notFound = firstExisting("404.html");
    if (notFound.isPresent()) {
      return ServerResponse.status(HttpStatus.NOT_FOUND)
          .contentType(HTML_UTF8)
          .bodyValue(notFound.get());
    }
    return ServerResponse.notFound().build();
  }
}
