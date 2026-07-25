package io.datastoria.server.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.ModelProviderRepository;
import io.datastoria.server.repository.ModelRepository;

/**
 * Creates the editable built-in model catalog for a tenant on first use.
 *
 * <p>The browser no longer owns a static model list. Built-ins are materialized as ordinary
 * provider/model rows so administrators can update, disable, discover, or replace them through the
 * Spring admin APIs.
 */
@Service
public class ModelCatalogProvisioner {

  private static final String ACTOR = "system:model-catalog";

  private static final List<ProviderSeed> PROVIDERS =
      List.of(
          new ProviderSeed("openai", "OpenAI", "https://api.openai.com/v1"),
          new ProviderSeed("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
          new ProviderSeed("deepseek", "DeepSeek", "https://api.deepseek.com"));

  private static final List<ModelSeed> MODELS =
      List.of(
          new ModelSeed(
              "openai",
              "gpt-5.4",
              "GPT-5.4",
              "OpenAI model for coding and professional work.",
              true,
              true,
              List.of("low", "medium", "high", "xhigh")),
          new ModelSeed(
              "openai",
              "gpt-5",
              "GPT-5",
              "OpenAI frontier reasoning model.",
              true,
              true,
              List.of("minimal", "low", "medium", "high")),
          new ModelSeed(
              "openrouter",
              "qwen/qwen3-coder:free",
              "Qwen3 Coder (free)",
              "OpenRouter-hosted coding model.",
              false,
              false,
              List.of()),
          new ModelSeed(
              "deepseek",
              "deepseek-chat",
              "DeepSeek Chat",
              "DeepSeek general-purpose chat and coding model.",
              false,
              false,
              List.of()));

  private final ModelProviderRepository providerRepository;
  private final ModelRepository modelRepository;

  public ModelCatalogProvisioner(
      ModelProviderRepository providerRepository, ModelRepository modelRepository) {
    this.providerRepository = providerRepository;
    this.modelRepository = modelRepository;
  }

  /**
   * Idempotently materializes missing built-ins. Existing rows always win, so upgrades never
   * overwrite administrator changes.
   */
  public synchronized void provision(Identity identity) {
    String tenantId = identity.tenantId();
    Map<String, ModelProvider> providers =
        providerRepository.findAll(tenantId).stream()
            .collect(
                Collectors.toMap(
                    provider -> normalize(provider.providerKey()),
                    Function.identity(),
                    (left, right) -> left));

    for (ProviderSeed seed : PROVIDERS) {
      providers.computeIfAbsent(
          seed.key(),
          ignored ->
              providerRepository.save(
                  new ModelProvider(
                      Ulid.next(),
                      tenantId,
                      seed.key(),
                      seed.displayName(),
                      seed.baseUrl(),
                      "api_key",
                      true,
                      "{}",
                      null,
                      0,
                      ACTOR,
                      ACTOR,
                      null,
                      null,
                      null)));
    }

    Map<String, Model> models =
        modelRepository.findAll(tenantId).stream()
            .collect(
                Collectors.toMap(
                    model -> model.providerId() + "\u0000" + model.modelKey(),
                    Function.identity(),
                    (left, right) -> left));
    for (ModelSeed seed : MODELS) {
      ModelProvider provider = providers.get(seed.providerKey());
      String key = provider.id() + "\u0000" + seed.modelKey();
      if (models.containsKey(key)) {
        continue;
      }
      String capabilities =
          """
          {"supportedEndpoints":["chat"],"autoSelectable":true,\
          "supportsImageInput":%s,"supportsTemperature":true,\
          "supportsReasoning":%s,"reasoningLevels":%s}
          """
              .formatted(seed.imageInput(), seed.reasoning(), jsonArray(seed.reasoningLevels()))
              .replace("\n", "")
              .trim();
      Model saved =
          modelRepository.save(
              new Model(
                  Ulid.next(),
                  tenantId,
                  provider.id(),
                  seed.modelKey(),
                  seed.displayName(),
                  seed.description(),
                  "system",
                  true,
                  seed.modelKey().endsWith(":free"),
                  capabilities,
                  "{}",
                  null,
                  0,
                  null,
                  null,
                  null));
      models.put(key, saved);
    }
  }

  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  private static String jsonArray(List<String> values) {
    return values.stream()
        .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
        .collect(Collectors.joining(",", "[", "]"));
  }

  private record ProviderSeed(String key, String displayName, String baseUrl) {}

  private record ModelSeed(
      String providerKey,
      String modelKey,
      String displayName,
      String description,
      boolean imageInput,
      boolean reasoning,
      List<String> reasoningLevels) {}
}
