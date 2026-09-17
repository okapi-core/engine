/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.model.DashboardPanel;
import org.okapi.data.model.PanelQueryConfig;
import org.okapi.data.pg.entity.DashboardPanelEntity;
import org.okapi.data.pg.repository.DashboardPanelRepository;

public final class DashboardPanelDaoPg implements DashboardPanelDao {
  private final DashboardPanelRepository repository;
  private final Gson gson;

  public DashboardPanelDaoPg(DashboardPanelRepository repository, Gson gson) {
    this.repository = repository;
    this.gson = gson;
  }

  public Optional<DashboardPanel> get(
      String org, String dashboard, String row, String version, String panel) {
    return repository.findById(id(org, dashboard, row, version, panel)).map(this::toDto);
  }

  public void save(String org, String dashboard, String row, String version, DashboardPanel panel) {
    repository.saveAndFlush(
        new DashboardPanelEntity(
            id(org, dashboard, row, version, panel.getPanelId()),
            panel.getNote(),
            panel.getTitle(),
            panel.getQueryConfig() == null ? null : gson.toJson(panel.getQueryConfig())));
  }

  public void delete(String org, String dashboard, String row, String version, String panel) {
    repository.deleteById(id(org, dashboard, row, version, panel));
  }

  public List<DashboardPanel> getAll(String org, String dashboard, String row, String version) {
    return repository
        .findAllByIdOrgIdAndIdDashboardIdAndIdVersionIdAndIdRowIdOrderByIdPanelId(
            org, dashboard, version, row)
        .stream()
        .map(this::toDto)
        .toList();
  }

  private DashboardPanelEntity.Id id(
      String org, String dashboard, String row, String version, String panel) {
    return new DashboardPanelEntity.Id(org, dashboard, version, row, panel);
  }

  private DashboardPanel toDto(DashboardPanelEntity entity) {
    return DashboardPanel.builder()
        .panelId(entity.getId().getPanelId())
        .note(entity.getNote())
        .title(entity.getTitle())
        .queryConfig(
            entity.getQueryConfig() == null
                ? null
                : gson.fromJson(entity.getQueryConfig(), PanelQueryConfig.class))
        .build();
  }
}
