package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.dto.DiscoveredModelResponse;
import io.github.ccweixiao.datastoria.service.ModelCatalogEnricher.CatalogEntry;

class ModelCatalogEnricherTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void openRouterCatalogIsMergedUnderFullAndSuffixKeys() {
    Map<String, CatalogEntry> merged = new HashMap<>();
    new ModelCatalogEnricher(WebClient.builder(), mapper, true)
        .mergeOpenRouter(
            merged,
            parse(
                """
                {"data":[{
                  "id":"openai/gpt-4o",
                  "context_length":128000,
                  "architecture":{"input_modalities":["text","image"]},
                  "supported_parameters":["tools","reasoning"],
                  "top_provider":{"max_completion_tokens":16384},
                  "pricing":{"prompt":"0.0000025","completion":"0.00001"}
                },{
                  "id":"meta/llama-3.3-70b",
                  "context_length":131072,
                  "architecture":{"input_modalities":["text"]},
                  "supported_parameters":["tools"],
                  "pricing":{"prompt":"0","completion":"0"}
                }]}
                """));

    CatalogEntry gpt = merged.get("gpt4o");
    assertThat(gpt).isNotNull();
    assertThat(gpt.contextWindowTokens()).isEqualTo(128_000);
    assertThat(gpt.maxOutputTokens()).isEqualTo(16_384);
    assertThat(gpt.supportsReasoning()).isTrue();
    assertThat(gpt.supportsImageInput()).isTrue();
    assertThat(gpt.free()).isFalse();
    assertThat(merged.get("openaigpt4o").free()).isFalse();

    CatalogEntry llama = merged.get("llama3.370b");
    assertThat(llama).isNotNull();
    assertThat(llama.free()).isTrue();
    assertThat(llama.supportsReasoning()).isFalse();
  }

  @Test
  void modelsDevCatalogUsesProviderModelIdsAndLimits() {
    Map<String, CatalogEntry> merged = new HashMap<>();
    new ModelCatalogEnricher(WebClient.builder(), mapper, true)
        .mergeModelsDev(
            merged,
            parse(
                """
                {"deepseek":{"models":{"deepseek-chat":{
                  "id":"deepseek-chat",
                  "limit":{"context":65536,"output":8192},
                  "abilities":{"reasoning":false,"image_input":false},
                  "pricing":{"prompt":0,"completion":0}
                }}}}
                """));

    CatalogEntry chat = merged.get("deepseekchat");
    assertThat(chat).isNotNull();
    assertThat(chat.contextWindowTokens()).isEqualTo(65_536);
    assertThat(chat.maxOutputTokens()).isEqualTo(8_192);
    assertThat(chat.free()).isTrue();
  }

  @Test
  void enrichmentFillsOnlyMissingValuesAndNeverDowngrades() {
    Map<String, CatalogEntry> catalog =
        Map.of(
            "gpt4o",
            new CatalogEntry(128_000, 16_384, true, true, false),
            "deepseekchat",
            new CatalogEntry(65_536, 8_192, true, false, true));

    List<DiscoveredModelResponse> enriched =
        ModelCatalogEnricher.applyCatalog(
            List.of(
                // Known values win over the catalog; booleans upgrade only.
                new DiscoveredModelResponse(
                    "gpt-4o", "GPT", "openai", "balanced", false, false, false, 32_000, 4_096),
                // Everything missing gets filled.
                new DiscoveredModelResponse("deepseek-chat", "DS", "deepseek")),
            catalog);

    assertThat(enriched.get(0).contextWindowTokens()).isEqualTo(32_000);
    assertThat(enriched.get(0).maxOutputTokens()).isEqualTo(4_096);
    assertThat(enriched.get(0).supportsReasoning()).isTrue();
    assertThat(enriched.get(0).isFree()).isFalse();

    assertThat(enriched.get(1).contextWindowTokens()).isEqualTo(65_536);
    assertThat(enriched.get(1).maxOutputTokens()).isEqualTo(8_192);
    assertThat(enriched.get(1).supportsReasoning()).isTrue();
    assertThat(enriched.get(1).supportsImageInput()).isFalse();
    assertThat(enriched.get(1).isFree()).isTrue();
  }

  @Test
  void networkFailureAbortsEnrichmentWithoutException() {
    ModelCatalogEnricher broken =
        new ModelCatalogEnricher(WebClient.builder(), mapper, true) {
          @Override
          Map<String, CatalogEntry> catalog() {
            throw new IllegalStateException("connect timed out");
          }
        };
    List<DiscoveredModelResponse> models =
        List.of(new DiscoveredModelResponse("gpt-4o", "GPT", "openai"));

    List<DiscoveredModelResponse> result = broken.enrich("openai", models);

    assertThat(result).isSameAs(models);
  }

  @Test
  void disabledEnricherIsANoOp() {
    ModelCatalogEnricher disabled = new ModelCatalogEnricher(WebClient.builder(), mapper, false);
    List<DiscoveredModelResponse> models =
        List.of(new DiscoveredModelResponse("gpt-4o", "GPT", "openai"));

    assertThat(disabled.enrich("openai", models)).isSameAs(models);
  }

  private JsonNode parse(String json) {
    try {
      return mapper.readTree(json);
    } catch (Exception error) {
      throw new IllegalArgumentException(error);
    }
  }
}
