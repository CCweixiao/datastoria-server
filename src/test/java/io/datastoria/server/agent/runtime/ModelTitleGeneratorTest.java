package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.agent.domain.RunContext;
import io.datastoria.server.agent.testing.FakeModelAdapter;
import io.datastoria.server.agent.testing.FakeStreamModel;

class ModelTitleGeneratorTest {

  @Test
  void generatesStructuredTitleWithServerSideModel() {
    FakeStreamModel model =
        FakeStreamModel.builder()
            .text("{\"title\":\"Investigate Slow Merge Queries\"}")
            .finish(12, 5)
            .build();
    RunContext context =
        new RunContext(
            "title-run",
            "tenant",
            "user",
            "session",
            "message",
            "request",
            "agent",
            "model",
            Instant.EPOCH);

    String title =
        new ModelTitleGenerator(new ObjectMapper())
            .generate(
                new FakeModelAdapter(model),
                context,
                "Please investigate why merge queries are slow")
            .block();

    assertThat(title).isEqualTo("Investigate Slow Merge Queries");
    assertThat(model.lastMessages()).hasSize(2);
    assertThat(model.lastMessages().get(0).getTextContent()).contains("short chat session titles");
    assertThat(model.lastToolCount()).isZero();
  }

  @Test
  void trimsPlainProviderOutputToWireLimit() {
    FakeStreamModel model = FakeStreamModel.builder().text("A".repeat(80)).finish(1, 1).build();

    String title =
        new ModelTitleGenerator(new ObjectMapper())
            .generate(
                new FakeModelAdapter(model),
                new RunContext(
                    "title-run",
                    "tenant",
                    "user",
                    "session",
                    "message",
                    null,
                    "agent",
                    "model",
                    Instant.EPOCH),
                "input")
            .block();

    assertThat(title).hasSize(64);
  }
}
