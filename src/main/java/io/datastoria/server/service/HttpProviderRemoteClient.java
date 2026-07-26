package io.datastoria.server.service;

import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.api.error.ProviderOperationException;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.dto.DiscoveredModelResponse;

/**
 * Minimal provider model-discovery client. It supports OpenAI-compatible {@code /v1/models}
 * providers and Anthropic without introducing provider SDK dependencies.
 */
@Component
public class HttpProviderRemoteClient implements ProviderRemoteClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final WebClient.Builder webClientBuilder;

  public HttpProviderRemoteClient(WebClient.Builder webClientBuilder) {
    this.webClientBuilder = webClientBuilder;
  }

  @Override
  public boolean supports(String providerKey) {
    return providerKey != null && !providerKey.isBlank();
  }

  @Override
  public List<DiscoveredModelResponse> discoverModels(ModelProvider provider, String credential) {
    if (!supports(provider.providerKey())) {
      throw new ProviderOperationException(
          "PROVIDER_NOT_SUPPORTED", 501, "Provider does not support model discovery");
    }
    String providerKey = provider.providerKey().toLowerCase();
    String baseUrl = resolveBaseUrl(provider);
    String modelsUrl =
        baseUrl.endsWith("/v1") || baseUrl.endsWith("/v4")
            ? baseUrl + "/models"
            : baseUrl + "/v1/models";
    WebClient.RequestHeadersSpec<?> request =
        webClientBuilder
            .build()
            .get()
            .uri(modelsUrl)
            .headers(headers -> applyCredential(headers, providerKey, credential));
    try {
      JsonNode body = request.retrieve().bodyToMono(JsonNode.class).block(TIMEOUT);
      JsonNode data = body == null ? null : body.get("data");
      if (data == null || !data.isArray()) {
        throw new ProviderOperationException(
            "PROVIDER_INVALID_RESPONSE", 502, "Provider returned an invalid model list");
      }
      return java.util.stream.StreamSupport.stream(data.spliterator(), false)
          .filter(node -> !node.path("id").asText("").isBlank())
          .map(node -> ProviderModelMetadata.from(provider.providerKey(), node))
          .toList();
    } catch (ProviderOperationException ex) {
      throw ex;
    } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden ex) {
      throw new ProviderOperationException(
          "PROVIDER_AUTH_FAILED", 502, "Provider rejected the configured credential");
    } catch (WebClientResponseException ex) {
      throw new ProviderOperationException(
          "PROVIDER_UPSTREAM_ERROR",
          502,
          "Provider request failed with status " + ex.getStatusCode());
    } catch (RuntimeException ex) {
      if (ex.getCause() instanceof java.util.concurrent.TimeoutException) {
        throw new ProviderOperationException("PROVIDER_TIMEOUT", 504, "Provider request timed out");
      }
      throw new ProviderOperationException(
          "PROVIDER_UNAVAILABLE", 502, "Provider could not be reached");
    }
  }

  private static void applyCredential(HttpHeaders headers, String providerKey, String credential) {
    if ("anthropic".equals(providerKey)) {
      headers.set("x-api-key", credential);
      headers.set("anthropic-version", "2023-06-01");
    } else {
      headers.setBearerAuth(credential);
    }
  }

  private static String resolveBaseUrl(ModelProvider provider) {
    if (provider.baseUrl() != null && !provider.baseUrl().isBlank()) {
      return provider.baseUrl().replaceAll("/+$", "");
    }
    return switch (provider.providerKey().toLowerCase()) {
      case "openai" -> "https://api.openai.com";
      case "anthropic" -> "https://api.anthropic.com";
      case "openrouter" -> "https://openrouter.ai/api";
      case "deepseek" -> "https://api.deepseek.com";
      default -> throw new ProviderOperationException(
          "PROVIDER_NOT_SUPPORTED", 501, "Provider requires an explicit baseUrl");
    };
  }
}
