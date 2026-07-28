package io.github.ccweixiao.datastoria.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class MentionContextFormatterTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void carriesStructuredMentionContextAcrossUserTurnsOnServer() throws Exception {
    MentionContextFormatter formatter = new MentionContextFormatter();
    String first =
        formatter.apply(
            "inspect this",
            mapper.readTree(
                """
                {"mentionMetadata":{"version":1,"mentions":[
                  {"kind":"table","name":"system.query_log","engine":"MergeTree"}
                ]}}
                """));
    String second = formatter.apply("what columns does it have?", null);

    assertThat(first)
        .isEqualTo(
            """
            inspect this

            [system-added context]
            Mentioned tables:
            - system.query_log (engine: MergeTree)""");
    assertThat(second)
        .isEqualTo(
            """
            what columns does it have?

            [system-added context]
            Mentioned tables:
            - system.query_log (engine: MergeTree)""");
  }

  @Test
  void ignoresMalformedOrOversizedClientMetadata() throws Exception {
    MentionContextFormatter formatter = new MentionContextFormatter();
    String oversized = "x".repeat(501);

    assertThat(
            formatter.apply(
                "hello",
                mapper.readTree(
                    """
                    {"mentionMetadata":{"version":1,"mentions":[
                      {"kind":"table","name":%s,"engine":"MergeTree"}
                    ]}}
                    """
                        .formatted(mapper.writeValueAsString(oversized)))))
        .isEqualTo("hello");
  }
}
