package io.datastoria.server.api.error;

/** Thrown when a resource cannot be found for the given tenant. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String resourceType, String id) {
    super(resourceType + " not found: " + id);
  }

  public NotFoundException(String message) {
    super(message);
  }
}
