package io.github.ccweixiao.datastoria.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.dto.DiscoveredModelResponse;

/**
 * Best-effort enrichment of discovered model metadata from public authoritative catalogs
 * (OpenRouter's model API, then models.dev). Providers' own {@code /models} endpoints usually
 * return bare ids; the catalogs fill context window, max output, reasoning/image support and
 * free-model flags.
 *
 * <p>Failure semantics are strictly non-fatal: any network error, timeout or malformed payload
 * aborts the enrichment and the original discovery result is returned unchanged. The whole process
 * is bounded by a fixed 120-second budget. Values already known from the provider are never
 * overwritten — only missing fields are filled, and booleans are only upgraded to {@code true}
 * (never downgraded).
 */
@Component
public class ModelCatalogEnricher {

  private static final Logger log = LoggerFactory.getLogger(ModelCatalogEnricher.class);

  private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/models";
  private static final String MODELS_DEV_URL = "https://models.dev/api.json";
  private static final Duration PROCESS_BUDGET = Duration.ofSeconds(120);
  private static final Duration PER_SOURCE_TIMEOUT = Duration.ofSeconds(45);
  private static final Duration CACHE_TTL = Duration.ofMinutes(10);

  /** The catalogs are several MB of JSON; WebClient's default 256KB codec limit rejects them. */
  private static final int MAX_CATALOG_BYTES = 16 * 1024 * 1024;

  /** Normalized catalog entry merged from all sources. */
  record CatalogEntry(
      Integer contextWindowTokens,
      Integer maxOutputTokens,
      boolean supportsReasoning,
      boolean supportsImageInput,
      boolean free) {}

  private static final CatalogEntry EMPTY_ENTRY = new CatalogEntry(null, null, false, false, false);

  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper mapper;
  private final boolean enabled;
  private volatile Map<String, CatalogEntry> cachedCatalog;
  private volatile Instant cachedAt;

  public ModelCatalogEnricher(
      WebClient.Builder webClientBuilder,
      ObjectMapper mapper,
      @Value("${datastoria.model.enrichment.enabled:true}") boolean enabled) {
    this.webClientBuilder = webClientBuilder;
    this.mapper = mapper;
    this.enabled = enabled;
  }

  /**
   * Enriches the discovery result in place semantics: returns a new list with catalog data filled
   * into missing fields. Never throws — on any failure the input list is returned unchanged.
   */
  public List<DiscoveredModelResponse> enrich(
      String providerKey, List<DiscoveredModelResponse> models) {
    if (!enabled || models == null || models.isEmpty()) {
      if (!enabled) {
        log.info("Model catalog enrichment disabled; using provider metadata as-is");
      }
      return models;
    }
    try {
      Map<String, CatalogEntry> catalog = catalog();
      if (catalog.isEmpty()) {
        log.info(
            "Model catalog enrichment found no catalog entries; using provider metadata as-is");
        return models;
      }
      long matched =
          models.stream()
              .filter(m -> m.modelKey() != null && catalog.containsKey(normalize(m.modelKey())))
              .count();
      log.info(
          "Model catalog enrichment matched {}/{} models against {} public catalog entries",
          matched,
          models.size(),
          catalog.size());
      return applyCatalog(models, catalog);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log.warn("Model catalog enrichment interrupted; returning provider metadata as-is");
      return models;
    } catch (Exception error) {
      // Non-fatal by contract: offline servers, firewalls and malformed payloads all land here.
      log.warn(
          "Model catalog enrichment unavailable ({}); returning provider metadata as-is",
          error.getClass().getSimpleName());
      return models;
    }
  }

  /** Fetches (and caches) the merged catalog within the fixed process budget. */
  Map<String, CatalogEntry> catalog() throws InterruptedException {
    Map<String, CatalogEntry> cached = cachedCatalog;
    if (cached != null && cachedAt != null && cachedAt.plus(CACHE_TTL).isAfter(Instant.now())) {
      return cached;
    }
    Map<String, CatalogEntry> merged = new HashMap<>();
    Instant deadline = Instant.now().plus(PROCESS_BUDGET);
    mergeSource(merged, fetchJson(OPENROUTER_URL), this::mergeOpenRouter, deadline);
    mergeSource(merged, fetchJson(MODELS_DEV_URL), this::mergeModelsDev, deadline);
    if (!merged.isEmpty()) {
      // Never cache a failed fetch: a transient outage must not poison the TTL window.
      cachedCatalog = merged;
      cachedAt = Instant.now();
    }
    return merged;
  }

  private String fetchJson(String url) {
    try {
      return webClientBuilder
          .clone()
          .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_CATALOG_BYTES))
          .build()
          .get()
          .uri(url)
          .retrieve()
          .bodyToMono(String.class)
          .timeout(PER_SOURCE_TIMEOUT)
          .block(PER_SOURCE_TIMEOUT);
    } catch (RuntimeException error) {
      // A single unavailable source must not kill the other one; log the reason so operators
      // can tell timeouts, size limits and DNS failures apart.
      log.warn("Model catalog source {} unavailable: {}", url, error.toString());
      return null;
    }
  }

  private JsonNode parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return mapper.readTree(raw);
    } catch (Exception error) {
      return null;
    }
  }

  @FunctionalInterface
  private interface SourceMerger {
    void merge(Map<String, CatalogEntry> merged, JsonNode body);
  }

  private void mergeSource(
      Map<String, CatalogEntry> merged, String raw, SourceMerger merger, Instant deadline) {
    JsonNode body = parse(raw);
    if (body == null || Instant.now().isAfter(deadline)) {
      return;
    }
    merger.merge(merged, body);
  }

  /** OpenRouter: flat {@code data[]} with {@code context_length}, modalities, pricing. */
  void mergeOpenRouter(Map<String, CatalogEntry> merged, JsonNode body) {
    for (JsonNode node : body.path("data")) {
      if (!node.isObject()) {
        continue;
      }
      String id = node.path("id").asText("");
      if (id.isBlank()) {
        continue;
      }
      JsonNode architecture = node.path("architecture");
      boolean image = hasModality(architecture.path("input_modalities"), "image");
      boolean reasoning = false;
      for (JsonNode parameter : node.path("supported_parameters")) {
        if ("reasoning".equalsIgnoreCase(parameter.asText())) {
          reasoning = true;
          break;
        }
      }
      JsonNode topProvider = node.path("top_provider");
      Integer output =
          positive(
              topProvider.path("max_completion_tokens").canConvertToInt()
                  ? topProvider.path("max_completion_tokens").asInt()
                  : node.path("default_max_completion_tokens").asInt(0));
      boolean free =
          isZeroPrice(node.path("pricing").path("prompt"))
              && isZeroPrice(node.path("pricing").path("completion"));
      upsert(
          merged,
          id,
          positive(node.path("context_length").asInt(0)),
          output,
          reasoning,
          image,
          free);
    }
  }

  /** models.dev: providers keyed by slug, each with a {@code models} object (id → metadata). */
  void mergeModelsDev(Map<String, CatalogEntry> merged, JsonNode body) {
    body.forEach(
        provider -> {
          JsonNode models = provider.path("models");
          models
              .fields()
              .forEachRemaining(
                  field -> {
                    JsonNode model = field.getValue();
                    JsonNode limit = model.path("limit");
                    JsonNode abilities = model.path("abilities");
                    JsonNode pricing = model.path("pricing");
                    boolean free =
                        isZeroPrice(pricing.path("prompt"))
                            && isZeroPrice(pricing.path("completion"));
                    String id = model.path("id").asText(field.getKey());
                    upsert(
                        merged,
                        id,
                        positive(limit.path("context").asInt(0)),
                        positive(limit.path("output").asInt(0)),
                        abilities.path("reasoning").asBoolean(false),
                        abilities.path("image_input").asBoolean(false),
                        free);
                  });
        });
  }

  private static boolean hasModality(JsonNode modalities, String expected) {
    for (JsonNode modality : modalities) {
      if (expected.equalsIgnoreCase(modality.asText())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isZeroPrice(JsonNode price) {
    if (price == null || price.isNull()) {
      return false;
    }
    return price.isNumber() ? price.asDouble() == 0d : price.asText("x").matches("0(\\.0+)?");
  }

  private static Integer positive(int value) {
    return value > 0 ? value : null;
  }

  /**
   * Registers the entry under both the full id ("openai/gpt-4o") and the bare suffix ("gpt-4o"),
   * normalized. Existing richer values win (true over false, non-null over null).
   */
  private static void upsert(
      Map<String, CatalogEntry> merged,
      String id,
      Integer context,
      Integer output,
      boolean reasoning,
      boolean image,
      boolean free) {
    if (id == null || id.isBlank()) {
      return;
    }
    String full = normalize(id);
    String suffix = id.contains("/") ? normalize(id.substring(id.lastIndexOf('/') + 1)) : full;
    CatalogEntry entry = new CatalogEntry(context, output, reasoning, image, free);
    for (String key : new String[] {full, suffix}) {
      merged.merge(
          key,
          entry,
          (existing, incoming) ->
              new CatalogEntry(
                  existing.contextWindowTokens() != null
                      ? existing.contextWindowTokens()
                      : incoming.contextWindowTokens(),
                  existing.maxOutputTokens() != null
                      ? existing.maxOutputTokens()
                      : incoming.maxOutputTokens(),
                  existing.supportsReasoning() || incoming.supportsReasoning(),
                  existing.supportsImageInput() || incoming.supportsImageInput(),
                  existing.free() || incoming.free()));
    }
  }

  /** Pure application of catalog values: fill-missing-only, booleans never downgraded. */
  static List<DiscoveredModelResponse> applyCatalog(
      List<DiscoveredModelResponse> models, Map<String, CatalogEntry> catalog) {
    List<DiscoveredModelResponse> enriched = new ArrayList<>(models.size());
    for (DiscoveredModelResponse model : models) {
      CatalogEntry entry =
          model.modelKey() == null
              ? EMPTY_ENTRY
              : catalog.getOrDefault(normalize(model.modelKey()), EMPTY_ENTRY);
      if (entry == null) {
        enriched.add(model);
        continue;
      }
      enriched.add(
          new DiscoveredModelResponse(
              model.modelKey(),
              model.displayName(),
              model.providerKey(),
              model.tier(),
              Boolean.TRUE.equals(model.supportsReasoning()) || entry.supportsReasoning(),
              Boolean.TRUE.equals(model.supportsImageInput()) || entry.supportsImageInput(),
              Boolean.TRUE.equals(model.isFree()) || entry.free(),
              model.contextWindowTokens() != null
                  ? model.contextWindowTokens()
                  : entry.contextWindowTokens(),
              model.maxOutputTokens() != null ? model.maxOutputTokens() : entry.maxOutputTokens()));
    }
    return enriched;
  }

  private static String normalize(String id) {
    return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
  }
}
