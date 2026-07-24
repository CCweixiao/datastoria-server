package io.datastoria.server.api.error;

/**
 * Thrown when a client request body contains a forbidden secret (e.g. {@code apiKey}). Maps to HTTP
 * 400 with code {@code CLIENT_SECRET_NOT_ALLOWED}.
 */
public class ClientSecretNotAllowedException extends RuntimeException {

  private final String fieldName;

  public ClientSecretNotAllowedException(String fieldName) {
    super("Client secret field is not allowed: " + fieldName);
    this.fieldName = fieldName;
  }

  public String fieldName() {
    return fieldName;
  }
}
