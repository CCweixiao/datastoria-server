package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.sun.net.httpserver.HttpServer;

import io.github.ccweixiao.datastoria.common.domain.ModelProvider;

class HttpProviderRemoteClientTest {

  @Test
  void discoversNativeGeminiModelsWithoutPuttingCredentialInUrl() throws Exception {
    AtomicReference<String> requestUri = new AtomicReference<>();
    AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1beta/models",
        exchange -> {
          requestUri.set(exchange.getRequestURI().toString());
          apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
          byte[] body =
              """
              {
                "models": [
                  {
                    "name": "models/gemini-test",
                    "displayName": "Gemini Test",
                    "inputTokenLimit": 1000000,
                    "outputTokenLimit": 64000,
                    "supportedGenerationMethods": ["generateContent"]
                  },
                  {
                    "name": "models/text-embedding-test",
                    "supportedGenerationMethods": ["embedContent"]
                  }
                ]
              }
              """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
      ModelProvider provider = provider("google", baseUrl);

      var models =
          new HttpProviderRemoteClient(WebClient.builder())
              .discoverModels(provider, "server-only-test-key");

      assertThat(requestUri.get()).isEqualTo("/v1beta/models?pageSize=1000");
      assertThat(requestUri.get()).doesNotContain("server-only-test-key");
      assertThat(apiKeyHeader.get()).isEqualTo("server-only-test-key");
      assertThat(models)
          .singleElement()
          .satisfies(
              model -> {
                assertThat(model.modelKey()).isEqualTo("gemini-test");
                assertThat(model.contextWindowTokens()).isEqualTo(1_000_000);
              });
    } finally {
      server.stop(0);
    }
  }

  private static ModelProvider provider(String key, String baseUrl) {
    Instant now = Instant.now();
    return new ModelProvider(
        "provider-1",
        "tenant-1",
        null,
        key,
        "Provider",
        baseUrl,
        "api_key",
        true,
        "{}",
        "secret-1",
        0,
        "admin",
        "admin",
        now,
        now,
        null);
  }
}
