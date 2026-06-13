/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.model.DashboardVersion;
import org.okapi.data.pg.entity.DashboardVersionEntity;
import org.okapi.data.pg.repository.DashboardVersionRepository;

public final class DashboardVersionDaoPg implements DashboardVersionDao {
  private final DashboardVersionRepository repository;

  public DashboardVersionDaoPg(DashboardVersionRepository repository) {
    this.repository = repository;
  }

  public void save(DashboardVersion version) {
    repository.saveAndFlush(
        new DashboardVersionEntity(
            id(version.getOrgId(), version.getDashboardId(), version.getVersionId()),
            version.getStatus(),
            version.getCreatedAt(),
            version.getCreatedBy(),
            version.getSpecHash(),
            version.getNote()));
  }

  public Optional<DashboardVersion> get(String org, String dashboard, String version) {
    return repository.findById(id(org, dashboard, version)).map(this::toDto);
  }

  public List<DashboardVersion> list(String org, String dashboard) {
    return repository.findAllByIdOrgIdAndIdDashboardIdOrderByIdVersionId(org, dashboard).stream()
        .map(this::toDto)
        .toList();
  }

  private DashboardVersionEntity.Id id(String org, String dashboard, String version) {
    return new DashboardVersionEntity.Id(org, dashboard, version);
  }

  private DashboardVersion toDto(DashboardVersionEntity entity) {
    return DashboardVersion.builder()
        .orgId(entity.getId().getOrgId())
        .dashboardId(entity.getId().getDashboardId())
        .versionId(entity.getId().getVersionId())
        .status(entity.getStatus())
        .createdAt(entity.getCreatedAt())
        .createdBy(entity.getCreatedBy())
        .specHash(entity.getSpecHash())
        .note(entity.getNote())
        .build();
  }
}
