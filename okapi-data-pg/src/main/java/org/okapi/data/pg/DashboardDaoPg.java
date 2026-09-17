/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dashboardvars.DashVars;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.data.model.Dashboard;
import org.okapi.data.model.ResourceOrder;
import org.okapi.data.model.Tags;
import org.okapi.data.pg.entity.DashboardEntity;
import org.okapi.data.pg.repository.DashboardRepository;

public final class DashboardDaoPg implements DashboardDao {
  private final DashboardRepository repository;
  private final Gson gson;

  public DashboardDaoPg(DashboardRepository repository, Gson gson) {
    this.repository = repository;
    this.gson = gson;
  }

  public Optional<Dashboard> get(String orgId, String id) {
    return repository.findById(new DashboardEntity.Id(orgId, id)).map(this::toDto);
  }

  public Dashboard save(Dashboard dashboard) {
    if (dashboard == null) throw new NullPointerException("dashboard");
    repository.saveAndFlush(
        new DashboardEntity(
            new DashboardEntity.Id(dashboard.getOrgId(), dashboard.getDashboardId()),
            dashboard.getCreator(),
            dashboard.getLastEditor(),
            dashboard.getCreated(),
            dashboard.getUpdatedTime(),
            dashboard.getTitle(),
            dashboard.getDesc(),
            json(dashboard.getTags()),
            json(dashboard.getRowOrder()),
            dashboard.getActiveVersion(),
            json(dashboard.getDashVars()),
            dashboard.getVersion()));
    return dashboard;
  }

  public void delete(String id) throws ResourceNotFoundException {
    var entity =
        repository
            .findFirstByIdDashboardId(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Dashboard with id " + id + " not found"));
    repository.delete(entity);
  }

  public List<Dashboard> getAll(String orgId) {
    return repository.findAllByIdOrgIdOrderByIdDashboardId(orgId).stream()
        .map(this::toDto)
        .toList();
  }

  private Dashboard toDto(DashboardEntity entity) {
    return Dashboard.builder()
        .orgId(entity.getId().getOrgId())
        .dashboardId(entity.getId().getDashboardId())
        .creator(entity.getCreator())
        .lastEditor(entity.getLastEditor())
        .created(entity.getCreated())
        .updatedTime(entity.getUpdatedTime())
        .title(entity.getTitle())
        .desc(entity.getDesc())
        .tags(fromJson(entity.getTags(), Tags.class))
        .rowOrder(fromJson(entity.getRowOrder(), ResourceOrder.class))
        .activeVersion(entity.getActiveVersion())
        .dashVars(fromJson(entity.getDashVars(), DashVars.class))
        .version(entity.getVersion())
        .build();
  }

  private String json(Object value) {
    return value == null ? null : gson.toJson(value);
  }

  private <T> T fromJson(String value, Class<T> type) {
    return value == null ? null : gson.fromJson(value, type);
  }
}
