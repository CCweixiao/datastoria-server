package io.github.ccweixiao.datastoria.common.error;

/**
 * Thrown when a create/update violates a uniqueness constraint (e.g. username already taken). Maps
 * to HTTP 409.
 */
public class ConflictException extends RuntimeException {

  private final ApiErrorCode code;

  public ConflictException(ApiErrorCode code) {
    super(code.message(java.util.Locale.ENGLISH));
    this.code = code;
  }

  public ApiErrorCode code() {
    return code;
  }
}
