/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.Optional;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.model.OrgSummary;
import org.okapi.data.model.Organization;
import org.okapi.data.pg.entity.OrganizationEntity;
import org.okapi.data.pg.repository.OrganizationRepository;

public final class OrgDaoPg implements OrgDao {
  private final OrganizationRepository repository;

  public OrgDaoPg(OrganizationRepository repository) {
    this.repository = repository;
  }

  public Optional<Organization> findById(String id) {
    return repository.findById(id).map(this::toDto);
  }

  public Optional<OrgSummary> getSummary(String id) {
    return repository.getSummary(id).map(this::toSummary);
  }

  public void save(Organization organization) {
    repository.upsert(
        organization.getOrgId(),
        organization.getOrgName(),
        organization.getOrgCreator(),
        organization.getCreated());
  }

  @Override
  public boolean createIfNotExists(Organization organization) {
    return repository.insertIfAbsent(
            organization.getOrgId(),
            organization.getOrgName(),
            organization.getOrgCreator(),
            organization.getCreated())
        == 1;
  }

  private Organization toDto(OrganizationEntity entity) {
    return Organization.builder()
        .orgId(entity.getOrgId())
        .orgName(entity.getOrgName())
        .orgCreator(entity.getOrgCreator())
        .created(entity.getCreated())
        .build();
  }

  private OrgSummary toSummary(OrganizationRepository.OrgSummaryProjection projection) {
    return OrgSummary.builder()
        .orgId(projection.getOrgId())
        .orgName(projection.getOrgName())
        .totalMembers(projection.getTotalMembers())
        .build();
  }
}
