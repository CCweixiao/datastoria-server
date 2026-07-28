package io.github.ccweixiao.datastoria.common.error;

/**
 * Thrown when an {@code If-Match} revision does not match the persisted value. Maps to HTTP 409.
 */
public class RevisionConflictException extends RuntimeException {

  private final String resourceType;
  private final String resourceId;
  private final long expectedRevision;
  private final long actualRevision;

  public RevisionConflictException(
      String resourceType, String resourceId, long expectedRevision, long actualRevision) {
    super(
        "Revision conflict for "
            + resourceType
            + " "
            + resourceId
            + ": expected "
            + expectedRevision
            + " but was "
            + actualRevision);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.expectedRevision = expectedRevision;
    this.actualRevision = actualRevision;
  }

  public String resourceType() {
    return resourceType;
  }

  public String resourceId() {
    return resourceId;
  }

  public long expectedRevision() {
    return expectedRevision;
  }

  public long actualRevision() {
    return actualRevision;
  }
}
