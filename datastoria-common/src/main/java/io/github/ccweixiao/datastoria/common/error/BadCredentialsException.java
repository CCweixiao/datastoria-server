package io.github.ccweixiao.datastoria.common.error;

/**
 * Thrown when username/password login fails (unknown user, wrong password, or disabled account).
 * Mapped by {@link io.github.ccweixiao.datastoria.controller.GlobalExceptionHandler} to HTTP 401.
 */
public class BadCredentialsException extends RuntimeException {

  public BadCredentialsException() {
    super("Invalid username or password");
  }
}
