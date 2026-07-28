package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ProviderModelMetadataTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void preservesRichMetadataReturnedByProvider() throws Exception {
    var node =
        objectMapper.readTree(
            """
            {
              "id": "example-vision-pro",
              "display_name": "Example Vision Pro",
              "tier": "flagship",
              "supports_reasoning": true,
              "architecture": {"input_modalities": ["text", "image"]},
              "context_length": 262144,
              "max_output_tokens": 32768
            }
            """);

    var result = ProviderModelMetadata.from("custom", node);

    assertThat(result.displayName()).isEqualTo("Example Vision Pro");
    assertThat(result.tier()).isEqualTo("flagship");
    assertThat(result.supportsReasoning()).isTrue();
    assertThat(result.supportsImageInput()).isTrue();
    assertThat(result.contextWindowTokens()).isEqualTo(262144);
    assertThat(result.maxOutputTokens()).isEqualTo(32768);
  }

  @Test
  void enrichesKnownProviderFamiliesWithoutInventingUnknownLimits() throws Exception {
    var deepSeek =
        ProviderModelMetadata.from(
            "deepseek", objectMapper.readTree("{\"id\":\"deepseek-v4-pro\"}"));
    var miniMax =
        ProviderModelMetadata.from(
            "minimax", objectMapper.readTree("{\"id\":\"MiniMax-M2.7-highspeed\"}"));
    var unknown =
        ProviderModelMetadata.from("custom", objectMapper.readTree("{\"id\":\"private-model\"}"));

    assertThat(deepSeek.tier()).isEqualTo("flagship");
    assertThat(deepSeek.contextWindowTokens()).isEqualTo(1_000_000);
    assertThat(miniMax.tier()).isEqualTo("fast");
    assertThat(miniMax.contextWindowTokens()).isEqualTo(204_800);
    assertThat(unknown.contextWindowTokens()).isNull();
  }

  @Test
  void mapsNativeGeminiCatalogFields() throws Exception {
    var node =
        objectMapper.readTree(
            """
            {
              "name": "models/gemini-2.5-pro",
              "baseModelId": "gemini-2.5-pro",
              "displayName": "Gemini 2.5 Pro",
              "inputTokenLimit": 1048576,
              "outputTokenLimit": 65536,
              "supportedGenerationMethods": ["generateContent"]
            }
            """);

    var result = ProviderModelMetadata.from("google", node);

    assertThat(result.modelKey()).isEqualTo("gemini-2.5-pro");
    assertThat(result.displayName()).isEqualTo("Gemini 2.5 Pro");
    assertThat(result.contextWindowTokens()).isEqualTo(1_048_576);
    assertThat(result.maxOutputTokens()).isEqualTo(65_536);
  }
}
