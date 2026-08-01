package io.github.ccweixiao.datastoria.common.error;

/** Raised when a non-administrator attempts an administrator-only operation. */
public class AdminAccessRequiredException extends RuntimeException {

  public AdminAccessRequiredException() {
    super("Administrator access is required");
  }
}
