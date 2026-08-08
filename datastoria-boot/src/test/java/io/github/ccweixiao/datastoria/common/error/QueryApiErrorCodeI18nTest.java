package io.github.ccweixiao.datastoria.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class QueryApiErrorCodeI18nTest {

  @Test
  void queryPolicyErrorsHaveDistinctEnglishAndChineseCopy() {
    for (ApiErrorCode code :
        List.of(ApiErrorCode.QUERY_WRITE_PERMISSION_DENIED, ApiErrorCode.QUERY_UNSAFE_SQL)) {
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
