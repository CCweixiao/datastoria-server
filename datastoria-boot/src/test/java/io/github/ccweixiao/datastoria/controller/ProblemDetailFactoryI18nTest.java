package io.github.ccweixiao.datastoria.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;

class ProblemDetailFactoryI18nTest {

  private final ProblemDetailFactory factory = new ProblemDetailFactory();

  @Test
  void rendersEnglishProblemDetailsByDefault() {
    var problem = factory.forError(ApiErrorCode.REVISION_CONFLICT, Locale.ENGLISH);

    assertThat(problem.getProperties()).containsEntry("code", "REVISION_CONFLICT");
    assertThat(problem.getProperties()).containsEntry("locale", "en");
    assertThat(problem.getProperties())
        .containsEntry(
            "message",
            "The resource was modified by another writer. Fetch the latest revision and retry.");
  }

  @Test
  void rendersChineseProblemDetailsForChineseLocale() {
    var problem = factory.forError(ApiErrorCode.REVISION_CONFLICT, Locale.SIMPLIFIED_CHINESE);

    assertThat(problem.getTitle()).isEqualTo("版本冲突");
    assertThat(problem.getProperties()).containsEntry("locale", "zh-CN");
    assertThat(problem.getProperties()).containsEntry("message", "资源已被其他操作修改，请获取最新版本后重试。");
  }
}
