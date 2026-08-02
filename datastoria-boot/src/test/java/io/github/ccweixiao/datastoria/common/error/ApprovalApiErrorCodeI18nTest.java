package io.github.ccweixiao.datastoria.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class ApprovalApiErrorCodeI18nTest {

  @Test
  void approvalErrorsHaveDistinctEnglishAndChineseCopy() {
    List<ApiErrorCode> codes =
        List.of(
            ApiErrorCode.APPROVAL_WORK_ORDER_TYPE_UNSUPPORTED,
            ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT,
            ApiErrorCode.APPROVAL_CONTENT_CHANGED,
            ApiErrorCode.APPROVAL_INVALID_STATE,
            ApiErrorCode.APPROVAL_RESOURCE_CONFLICT,
            ApiErrorCode.APPROVAL_DEPENDENCY_NOT_SUPPORTED,
            ApiErrorCode.DDL_OPERATION_UNSUPPORTED,
            ApiErrorCode.DDL_RULE_VIOLATION,
            ApiErrorCode.DDL_REVALIDATION_REQUIRED,
            ApiErrorCode.APPROVAL_EXECUTION_FAILED);

    for (ApiErrorCode code : codes) {
      assertThat(code.title(Locale.ENGLISH)).isNotBlank();
      assertThat(code.message(Locale.ENGLISH)).isNotBlank();
      assertThat(code.title(Locale.SIMPLIFIED_CHINESE))
          .isNotBlank()
          .isNotEqualTo(code.title(Locale.ENGLISH));
      assertThat(code.message(Locale.SIMPLIFIED_CHINESE))
          .isNotBlank()
          .isNotEqualTo(code.message(Locale.ENGLISH));
    }
  }
}
