package io.datastoria.server.repository;

import java.util.Optional;

import io.datastoria.server.domain.OAuthCredential;

public interface OAuthCredentialRepository {

  Optional<OAuthCredential> findByOwner(String tenantId, String userId, String providerKey);

  OAuthCredential save(OAuthCredential credential);

  OAuthCredential update(OAuthCredential credential, long expectedRevision);
}
