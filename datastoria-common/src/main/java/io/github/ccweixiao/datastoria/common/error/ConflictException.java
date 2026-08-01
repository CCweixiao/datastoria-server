package io.github.ccweixiao.datastoria.common.error;

/**
 * Thrown when a create/update violates a uniqueness constraint (e.g. username already taken). Maps
 * to HTTP 409.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
