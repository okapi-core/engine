/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.model.DashboardRow;
import org.okapi.data.model.ResourceOrder;
import org.okapi.data.pg.entity.DashboardRowEntity;
import org.okapi.data.pg.repository.DashboardRowRepository;

public final class DashboardRowDaoPg implements DashboardRowDao {
  private final DashboardRowRepository repository;
  private final Gson gson;

  public DashboardRowDaoPg(DashboardRowRepository repository, Gson gson) {
    this.repository = repository;
    this.gson = gson;
  }

  public Optional<DashboardRow> get(String org, String dashboard, String version, String row) {
    return repository.findById(id(org, dashboard, version, row)).map(this::toDto);
  }

  public void save(String org, String dashboard, String version, DashboardRow row) {
    repository.saveAndFlush(
        new DashboardRowEntity(
            id(org, dashboard, version, row.getRowId()),
            row.getNote(),
            row.getTitle(),
            row.getPanelOrder() == null ? null : gson.toJson(row.getPanelOrder())));
  }

  public void delete(String org, String dashboard, String version, String row) {
    repository.deleteById(id(org, dashboard, version, row));
  }

  public List<DashboardRow> getAll(String org, String dashboard, String version) {
    return repository
        .findAllByIdOrgIdAndIdDashboardIdAndIdVersionIdOrderByIdRowId(org, dashboard, version)
        .stream()
        .map(this::toDto)
        .toList();
  }

  private DashboardRowEntity.Id id(String org, String dashboard, String version, String row) {
    return new DashboardRowEntity.Id(org, dashboard, version, row);
  }

  private DashboardRow toDto(DashboardRowEntity entity) {
    return DashboardRow.builder()
        .rowId(entity.getId().getRowId())
        .note(entity.getNote())
        .title(entity.getTitle())
        .panelOrder(
            entity.getPanelOrder() == null
                ? null
                : gson.fromJson(entity.getPanelOrder(), ResourceOrder.class))
        .build();
  }
}
