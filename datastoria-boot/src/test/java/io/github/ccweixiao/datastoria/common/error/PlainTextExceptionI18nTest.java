package io.github.ccweixiao.datastoria.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class PlainTextExceptionI18nTest {

  @Test
  void rendersStableCodeAndEnglishCompatibilityBody() {
    var exception = PlainTextException.badRequest(ApiErrorCode.INVALID_JSON);

    assertThat(exception.code()).isEqualTo(ApiErrorCode.INVALID_JSON);
    assertThat(exception.body(Locale.ENGLISH)).isEqualTo("Invalid JSON in request body");
  }

  @Test
  void rendersChineseBodyForChineseLocale() {
    var exception = PlainTextException.badRequest(ApiErrorCode.INVALID_JSON);

    assertThat(exception.body(Locale.SIMPLIFIED_CHINESE)).isEqualTo("请求正文中的 JSON 无效");
  }
}
