package io.datastoria.server.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.domain.AgentDefinition;
import io.datastoria.server.domain.AgentRevision;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.dto.AgentDefinitionResponse;
import io.datastoria.server.dto.AgentRevisionResponse;
import io.datastoria.server.dto.CreateAgentRequest;
import io.datastoria.server.dto.CreateAgentRevisionRequest;
import io.datastoria.server.dto.UpdateAgentRequest;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.AgentDefinitionRepository;
import io.datastoria.server.repository.AgentRevisionRepository;
import io.datastoria.server.repository.ModelRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Application service for agent definition and revision management. */
@Service
public class AgentDefinitionService {

  private final AgentDefinitionRepository defRepo;
  private final AgentRevisionRepository revRepo;
  private final ModelRepository modelRepo;
  private final Scheduler jdbcScheduler;

  public AgentDefinitionService(
      AgentDefinitionRepository defRepo,
      AgentRevisionRepository revRepo,
      ModelRepository modelRepo,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.defRepo = defRepo;
    this.revRepo = revRepo;
    this.modelRepo = modelRepo;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<AgentDefinitionResponse>> findAll(Identity identity) {
    return Mono.fromCallable(
            () ->
                defRepo.findAll(identity.tenantId()).stream()
                    .map(
                        d -> {
                          List<AgentRevision> revs =
                              revRepo.findByAgentId(d.id(), identity.tenantId());
                          return AgentDefinitionResponse.from(d, revs);
                        })
                    .toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<AgentDefinitionResponse> findById(String id, Identity identity) {
    return Mono.fromCallable(
            () -> {
              AgentDefinition d =
                  defRepo
                      .findById(id, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Agent", id));
              return AgentDefinitionResponse.from(
                  d, revRepo.findByAgentId(d.id(), identity.tenantId()));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<AgentDefinitionResponse> create(CreateAgentRequest req, Identity identity) {
    return Mono.fromCallable(
            () -> {
              AgentDefinition def =
                  new AgentDefinition(
                      Ulid.next(),
                      identity.tenantId(),
                      req.agentKey(),
                      req.name(),
                      req.description(),
                      "draft",
                      null,
                      0,
                      identity.userId(),
                      identity.userId(),
                      null,
                      null,
                      null);
              return AgentDefinitionResponse.from(defRepo.save(def), List.of());
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<AgentRevisionResponse> createRevision(
      String agentId, CreateAgentRevisionRequest req, Identity identity) {
    return Mono.fromCallable(
            () -> {
              AgentDefinition def =
                  defRepo
                      .findById(agentId, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Agent", agentId));
              if (req.modelId() != null) {
                modelRepo
                    .findById(req.modelId(), identity.tenantId())
                    .orElseThrow(() -> new NotFoundException("Model", req.modelId()));
              }
              int nextVersion =
                  revRepo.findByAgentId(agentId, identity.tenantId()).stream()
                          .mapToInt(AgentRevision::version)
                          .max()
                          .orElse(0)
                      + 1;
              String checksum = sha256(req.systemPrompt());
              AgentRevision rev =
                  new AgentRevision(
                      Ulid.next(),
                      agentId,
                      nextVersion,
                      req.modelId(),
                      req.systemPrompt(),
                      checksum,
                      req.runtimeConfigJson(),
                      req.toolPolicyJson(),
                      req.skillPolicyJson(),
                      identity.userId(),
                      Instant.now());
              return AgentRevisionResponse.from(revRepo.save(rev));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<AgentDefinitionResponse> update(
      String agentId, UpdateAgentRequest req, Long ifMatch, Identity identity) {
    return Mono.fromCallable(
            () -> {
              AgentDefinition existing =
                  defRepo
                      .findById(agentId, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Agent", agentId));
              AgentDefinition updated =
                  new AgentDefinition(
                      existing.id(),
                      existing.tenantId(),
                      existing.agentKey(),
                      req.name(),
                      req.description(),
                      existing.status(),
                      existing.publishedRevisionId(),
                      existing.revision(),
                      existing.createdBy(),
                      identity.userId(),
                      existing.createdAt(),
                      existing.updatedAt(),
                      null);
              AgentDefinition saved =
                  defRepo.update(updated, ifMatch != null ? ifMatch : existing.revision());
              return AgentDefinitionResponse.from(
                  saved, revRepo.findByAgentId(agentId, identity.tenantId()));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> delete(String agentId, Long ifMatch, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> {
              AgentDefinition existing =
                  defRepo
                      .findById(agentId, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Agent", agentId));
              defRepo.softDelete(
                  agentId, identity.tenantId(), ifMatch != null ? ifMatch : existing.revision());
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<AgentDefinitionResponse> publish(
      String agentId, String revisionId, Long ifMatch, Identity identity) {
    return Mono.fromCallable(
            () -> {
              AgentDefinition def =
                  defRepo
                      .findById(agentId, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Agent", agentId));
              // Verify revision belongs to this agent
              revRepo
                  .findById(revisionId, identity.tenantId())
                  .filter(r -> r.agentId().equals(agentId))
                  .orElseThrow(() -> new NotFoundException("AgentRevision", revisionId));
              long expected = ifMatch != null ? ifMatch : def.revision();
              defRepo.publish(agentId, identity.tenantId(), revisionId, expected);
              return AgentDefinitionResponse.from(
                  defRepo.findById(agentId, identity.tenantId()).orElseThrow(),
                  revRepo.findByAgentId(agentId, identity.tenantId()));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<AgentDefinitionResponse> disable(String agentId, Long ifMatch, Identity identity) {
    return Mono.fromCallable(
            () -> {
              AgentDefinition def =
                  defRepo
                      .findById(agentId, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Agent", agentId));
              long expected = ifMatch != null ? ifMatch : def.revision();
              defRepo.disable(agentId, identity.tenantId(), expected);
              return AgentDefinitionResponse.from(
                  defRepo.findById(agentId, identity.tenantId()).orElseThrow(),
                  revRepo.findByAgentId(agentId, identity.tenantId()));
            })
        .subscribeOn(jdbcScheduler);
  }

  private static String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
