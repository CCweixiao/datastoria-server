package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

/**
 * Compiles a CREATE DATABASE work order into a single ON CLUSTER statement with the Atomic engine.
 * The executor's {@code database-exists} precondition guards against recreating an existing
 * database (idempotent BLOCK), so users can provision a database through the same approval flow
 * that the create-table precondition points to when the target database is missing.
 */
@Component
public class CreateDatabaseDescriptor extends AbstractDdlDescriptor {

  @Override
  public String generatorKey() {
    return "create_database";
  }

  @Override
  public void validateRules(JsonNode rules) {
    super.validateRules(rules);
    requireRule(rules, "requireCluster");
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    String database = rawIdentifier(requiredText(intent, "database"));
    String cluster = rawIdentifier(requiredText(intent, "cluster"));
    String sql =
        "CREATE DATABASE "
            + identifier(database)
            + " ON CLUSTER "
            + identifier(cluster)
            + " ENGINE = Atomic";
    return new CompiledDdlPlan(
        List.of(
            new CompiledDdlStatement(
                1,
                DdlOperationKind.CREATE_DATABASE,
                sql,
                List.of(database),
                "LOW",
                List.of(),
                "PRECONDITION")),
        List.of("createDatabase", "engine=Atomic", "requireCluster"));
  }
}
