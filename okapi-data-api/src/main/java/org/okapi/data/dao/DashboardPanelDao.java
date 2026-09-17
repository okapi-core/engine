/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import java.util.Optional;
import org.okapi.data.model.DashboardPanel;

public interface DashboardPanelDao {
  void save(String orgId, String dashboardId, String rowId, String versionId, DashboardPanel panel);

  void delete(String orgId, String dashboardId, String rowId, String versionId, String panelId);

  Optional<DashboardPanel> get(
      String orgId, String dashboardId, String rowId, String versionId, String panelId);

  List<DashboardPanel> getAll(String orgId, String dashboardId, String rowId, String versionId);
}
