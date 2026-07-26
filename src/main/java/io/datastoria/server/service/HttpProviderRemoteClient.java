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

/** Provider model-discovery client for OpenAI-compatible, Anthropic, and Gemini catalogs. */
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
    boolean gemini = isGemini(providerKey);
    String modelsUrl = modelsUrl(baseUrl, gemini);
    WebClient.RequestHeadersSpec<?> request =
        webClientBuilder
            .build()
            .get()
            .uri(modelsUrl)
            .headers(headers -> applyCredential(headers, providerKey, credential));
    try {
      JsonNode body = request.retrieve().bodyToMono(JsonNode.class).block(TIMEOUT);
      JsonNode data = body == null ? null : body.get(gemini ? "models" : "data");
      if (data == null || !data.isArray()) {
        throw new ProviderOperationException(
            "PROVIDER_INVALID_RESPONSE", 502, "Provider returned an invalid model list");
      }
      return java.util.stream.StreamSupport.stream(data.spliterator(), false)
          .filter(node -> !gemini || supportsGenerateContent(node))
          .filter(node -> !ProviderModelMetadata.id(node).isBlank())
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
    } else if (isGemini(providerKey)) {
      headers.set("x-goog-api-key", credential);
    } else {
      headers.setBearerAuth(credential);
    }
  }

  private static String modelsUrl(String baseUrl, boolean gemini) {
    if (baseUrl.endsWith("/models")) {
      return gemini ? baseUrl + "?pageSize=1000" : baseUrl;
    }
    if (gemini) {
      return (baseUrl.endsWith("/v1beta") ? baseUrl + "/models" : baseUrl + "/v1beta/models")
          + "?pageSize=1000";
    }
    return baseUrl.endsWith("/v1") || baseUrl.endsWith("/v4")
        ? baseUrl + "/models"
        : baseUrl + "/v1/models";
  }

  private static boolean supportsGenerateContent(JsonNode node) {
    JsonNode methods = node.path("supportedGenerationMethods");
    if (!methods.isArray()) {
      return true;
    }
    for (JsonNode method : methods) {
      if ("generateContent".equals(method.asText())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isGemini(String providerKey) {
    return "google".equals(providerKey)
        || "gemini".equals(providerKey)
        || "google-generative-ai".equals(providerKey);
  }

  private static String resolveBaseUrl(ModelProvider provider) {
    if (provider.baseUrl() != null && !provider.baseUrl().isBlank()) {
      return provider.baseUrl().replaceAll("/+$", "");
    }
    return switch (provider.providerKey().toLowerCase()) {
      case "openai" -> "https://api.openai.com";
      case "anthropic" -> "https://api.anthropic.com";
      case "google",
          "gemini",
          "google-generative-ai" -> "https://generativelanguage.googleapis.com/v1beta";
      case "openrouter" -> "https://openrouter.ai/api";
      case "groq" -> "https://api.groq.com/openai";
      case "cerebras" -> "https://api.cerebras.ai";
      case "nebius" -> "https://api.tokenfactory.nebius.com";
      case "deepseek" -> "https://api.deepseek.com";
      case "zhipu", "glm" -> "https://open.bigmodel.cn/api/coding/paas/v4";
      case "kimi", "moonshot" -> "https://api.moonshot.cn";
      case "minimax" -> "https://api.minimax.io";
      case "dashscope",
          "bailian",
          "qwen",
          "aliyun-bailian" -> "https://dashscope.aliyuncs.com/compatible-mode";
      case "xai" -> "https://api.x.ai";
      default -> throw new ProviderOperationException(
          "PROVIDER_NOT_SUPPORTED", 501, "Provider requires an explicit baseUrl");
    };
  }
}
