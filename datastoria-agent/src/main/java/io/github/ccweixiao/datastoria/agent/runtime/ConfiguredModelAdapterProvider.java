package io.github.ccweixiao.datastoria.agent.runtime;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import io.agentscope.extensions.model.gemini.GeminiChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.github.ccweixiao.datastoria.common.domain.Model;
import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderRepository;
import io.github.ccweixiao.datastoria.service.OAuthCredentialService;
import io.github.ccweixiao.datastoria.service.SecretService;

/**
 * Production model adapter for OpenAI-compatible, Anthropic, Gemini, Codex, and Copilot endpoints.
 *
 * <p>The API key is decrypted only while constructing the provider model and is never accepted
 * from, or returned to, the browser. A model-level secret overrides the provider-level secret.
 */
@Component
public final class ConfiguredModelAdapterProvider implements ModelAdapterProvider {

  private final ModelProviderRepository providerRepository;
  private final SecretService secretService;
  private final OAuthCredentialService oauthCredentialService;
  private final ObjectMapper mapper;

  public ConfiguredModelAdapterProvider(
      ModelProviderRepository providerRepository,
      SecretService secretService,
      OAuthCredentialService oauthCredentialService,
      ObjectMapper mapper) {
    this.providerRepository = providerRepository;
    this.secretService = secretService;
    this.oauthCredentialService = oauthCredentialService;
    this.mapper = mapper;
  }

  @Override
  public ModelAdapter adapterFor(Model modelConfig) {
    return adapterFor(modelConfig, null);
  }

  @Override
  public ModelAdapter adapterFor(Model modelConfig, Identity identity) {
    ModelProvider provider =
        providerRepository
            .findById(modelConfig.providerId(), modelConfig.tenantId())
            .filter(ModelProvider::enabled)
            .orElseThrow(() -> new NotFoundException("ModelProvider", modelConfig.providerId()));
    boolean githubCopilot = "github-copilot".equalsIgnoreCase(provider.providerKey());
    boolean openAiCodex = "openai-codex".equalsIgnoreCase(provider.providerKey());
    boolean anthropic = isAnthropic(provider.providerKey());
    boolean gemini = isGemini(provider.providerKey());
    if (!githubCopilot && !openAiCodex && !anthropic && !gemini && !isOpenAiCompatible(provider)) {
      throw new IllegalArgumentException("Unsupported provider type");
    }
    // Advertised to AgentScope so compaction thresholds can scale with the real window.
    Integer contextWindow = contextWindowTokens(modelConfig);
    if (openAiCodex) {
      if (identity == null) {
        throw new IllegalStateException("Authenticated identity is required for OAuth provider");
      }
      String accessToken = oauthCredentialService.accessToken("codex", identity);
      var providerModel = new CodexResponsesChatModel(modelConfig.modelKey(), accessToken, mapper);
      if (contextWindow != null) {
        providerModel.withContextWindowSize(contextWindow);
      }
      return ignored -> providerModel;
    }
    String apiKey;
    if (githubCopilot) {
      if (identity == null) {
        throw new IllegalStateException("Authenticated identity is required for OAuth provider");
      }
      apiKey = oauthCredentialService.accessToken("github", identity);
    } else {
      String secretId =
          modelConfig.secretId() != null && !modelConfig.secretId().isBlank()
              ? modelConfig.secretId()
              : provider.secretId();
      if (secretId == null || secretId.isBlank()) {
        throw new IllegalStateException("Provider credential is not configured");
      }
      apiKey = secretService.decrypt(secretId, modelConfig.tenantId());
    }
    if (anthropic) {
      AnthropicChatModel.Builder builder =
          AnthropicChatModel.builder().apiKey(apiKey).modelName(modelConfig.modelKey()).stream(
              true);
      String baseUrl = resolvedBaseUrl(provider);
      if (baseUrl != null) {
        builder.baseUrl(baseUrl);
      }
      if (contextWindow != null) {
        builder.contextWindowSize(contextWindow);
      }
      var providerModel = builder.build();
      return ignored -> providerModel;
    }
    if (gemini) {
      GeminiChatModel.Builder builder =
          GeminiChatModel.builder()
              .apiKey(apiKey)
              .modelName(modelConfig.modelKey())
              .streamEnabled(true);
      String baseUrl = resolvedBaseUrl(provider);
      if (baseUrl != null) {
        builder.baseUrl(baseUrl);
      }
      if (contextWindow != null) {
        builder.contextWindowSize(contextWindow);
      }
      var providerModel = builder.build();
      return ignored -> providerModel;
    }
    OpenAIChatModel.Builder builder =
        OpenAIChatModel.builder().apiKey(apiKey).modelName(modelConfig.modelKey()).stream(true);
    if (githubCopilot) {
      builder
          .baseUrl("https://api.githubcopilot.com")
          .endpointPath("/chat/completions")
          .httpTransport(GitHubCopilotHttpTransport.create());
    }
    String baseUrl = resolvedBaseUrl(provider);
    if (baseUrl != null) {
      builder.baseUrl(baseUrl);
    }
    if (contextWindow != null) {
      builder.contextWindowSize(contextWindow);
    }
    var providerModel = builder.build();
    return ignored -> providerModel;
  }

  /**
   * Reads the model's advertised context window from its capabilities JSON (the settings page
   * stores {@code contextWindowTokens}; provider discovery uses the same key). Null when absent —
   * the harness then falls back to the configured default window.
   */
  private Integer contextWindowTokens(Model modelConfig) {
    String json = modelConfig.capabilitiesJson();
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      JsonNode node = mapper.readTree(json);
      int value =
          node.path("contextWindowTokens")
              .asInt(
                  node.path("context_window_tokens")
                      .asInt(
                          node.path("contextWindow").asInt(node.path("context_length").asInt(0))));
      return value > 0 ? value : null;
    } catch (JsonProcessingException error) {
      return null;
    }
  }

  private static boolean isAnthropic(String providerKey) {
    return providerKey != null
        && ("anthropic".equalsIgnoreCase(providerKey) || "claude".equalsIgnoreCase(providerKey));
  }

  private static boolean isGemini(String providerKey) {
    return providerKey != null
        && ("google".equalsIgnoreCase(providerKey)
            || "gemini".equalsIgnoreCase(providerKey)
            || "google-generative-ai".equalsIgnoreCase(providerKey));
  }

  private static boolean isOpenAiCompatible(ModelProvider provider) {
    String key = provider.providerKey();
    if (key == null || "oauth".equalsIgnoreCase(provider.authType())) {
      return false;
    }
    String normalized = key.toLowerCase(java.util.Locale.ROOT);
    return normalized.equals("openai")
        || normalized.equals("openai-compatible")
        || normalized.equals("openrouter")
        || normalized.equals("groq")
        || normalized.equals("cerebras")
        || normalized.equals("nebius")
        || normalized.equals("deepseek")
        || normalized.equals("kimi")
        || normalized.equals("moonshot")
        || normalized.equals("zhipu")
        || normalized.equals("glm")
        || normalized.equals("minimax")
        || normalized.equals("dashscope")
        || normalized.equals("bailian")
        || normalized.equals("qwen")
        || normalized.equals("aliyun-bailian")
        || normalized.equals("custom")
        || normalized.equals("xai")
        || (!normalized.equals("anthropic")
            && provider.baseUrl() != null
            && !provider.baseUrl().isBlank());
  }

  private static String resolvedBaseUrl(ModelProvider provider) {
    if (provider.baseUrl() != null && !provider.baseUrl().isBlank()) {
      return provider.baseUrl();
    }
    return switch (provider.providerKey().toLowerCase(java.util.Locale.ROOT)) {
      case "openai", "anthropic", "claude", "google", "gemini", "google-generative-ai" -> null;
      case "openrouter" -> "https://openrouter.ai/api/v1";
      case "groq" -> "https://api.groq.com/openai/v1";
      case "cerebras" -> "https://api.cerebras.ai/v1";
      case "nebius" -> "https://api.tokenfactory.nebius.com/v1";
      case "deepseek" -> "https://api.deepseek.com";
      case "zhipu", "glm" -> "https://open.bigmodel.cn/api/coding/paas/v4";
      case "kimi", "moonshot" -> "https://api.moonshot.cn/v1";
      case "minimax" -> "https://api.minimax.io/v1";
      case "dashscope",
          "bailian",
          "qwen",
          "aliyun-bailian" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
      case "xai" -> "https://api.x.ai/v1";
      default -> null;
    };
  }
}
