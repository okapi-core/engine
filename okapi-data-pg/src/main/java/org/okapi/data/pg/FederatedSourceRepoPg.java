/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.FederatedSourceRepo;
import org.okapi.data.model.FederatedSource;
import org.okapi.data.pg.entity.FederatedSourceEntity;
import org.okapi.data.pg.repository.FederatedSourceRepository;

public final class FederatedSourceRepoPg implements FederatedSourceRepo {
  private final FederatedSourceRepository repository;

  public FederatedSourceRepoPg(FederatedSourceRepository repository) {
    this.repository = repository;
  }

  public Optional<FederatedSource> getSource(String tenant, String source) {
    return repository.findById(id(tenant, source)).map(this::toDto);
  }

  public List<FederatedSource> getAllSources(String tenant) {
    return repository.findAllByIdOrgIdOrderByIdSourceName(tenant).stream()
        .map(this::toDto)
        .toList();
  }

  public void createSource(FederatedSource source) {
    repository.saveAndFlush(
        new FederatedSourceEntity(
            id(source.getOrgId(), source.getSourceName()),
            source.getSourceType(),
            source.getRegistrationToken(),
            source.getCreated()));
  }

  public void deleteSource(String tenant, String source) {
    repository.deleteById(id(tenant, source));
  }

  private FederatedSourceEntity.Id id(String orgId, String sourceName) {
    return new FederatedSourceEntity.Id(orgId, sourceName);
  }

  private FederatedSource toDto(FederatedSourceEntity entity) {
    return FederatedSource.builder()
        .orgId(entity.getId().getOrgId())
        .sourceName(entity.getId().getSourceName())
        .sourceType(entity.getSourceType())
        .registrationToken(entity.getRegistrationToken())
        .created(entity.getCreated())
        .build();
  }
}
