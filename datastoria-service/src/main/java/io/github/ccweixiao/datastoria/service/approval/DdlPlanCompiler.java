package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;

@Service
public class DdlPlanCompiler {

  private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
      new com.fasterxml.jackson.databind.ObjectMapper();
  private final Map<String, DdlWorkOrderTypeDescriptor> descriptors;

  public DdlPlanCompiler(List<DdlWorkOrderTypeDescriptor> descriptors) {
    this.descriptors =
        descriptors.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    DdlWorkOrderTypeDescriptor::generatorKey, Function.identity()));
  }

  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    DdlWorkOrderTypeDescriptor descriptor = descriptors.get(definition.generatorKey());
    if (descriptor == null) {
      throw PlainTextException.badRequest(ApiErrorCode.APPROVAL_WORK_ORDER_TYPE_UNSUPPORTED);
    }
    try {
      descriptor.validateRules(JSON.readTree(definition.generationRuleJson()));
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    CompiledDdlPlan plan = descriptor.compile(intent, definition, schema);
    if (plan.statements().isEmpty()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_OPERATION_UNSUPPORTED);
    }
    for (int index = 0; index < plan.statements().size(); index++) {
      if (plan.statements().get(index).ordinal() != index + 1) {
        throw new IllegalStateException("DDL descriptor produced non-contiguous ordinals");
      }
    }
    return plan;
  }
}
