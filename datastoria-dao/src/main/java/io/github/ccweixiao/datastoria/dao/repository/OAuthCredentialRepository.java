package io.github.ccweixiao.datastoria.dao.repository;

import java.util.Optional;

import io.github.ccweixiao.datastoria.common.domain.OAuthCredential;

public interface OAuthCredentialRepository {

  Optional<OAuthCredential> findByOwner(String tenantId, String userId, String providerKey);

  OAuthCredential save(OAuthCredential credential);

  OAuthCredential update(OAuthCredential credential, long expectedRevision);
}
