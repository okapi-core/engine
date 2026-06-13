/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardVarDao;
import org.okapi.data.model.DashboardVariable;

public final class DashboardVarDaoPg implements DashboardVarDao {
  private final JdbcRecordStore store;

  public DashboardVarDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  private String scope(String org, String dashboard, String version) {
    return key(org, dashboard, version);
  }

  public Optional<DashboardVariable> get(
      String org, String dashboard, String version, String name) {
    return store.get(
        "dashboard-variable", key(scope(org, dashboard, version), name), DashboardVariable.class);
  }

  public DashboardVariable save(
      String org, String dashboard, String version, DashboardVariable variable) {
    if (variable == null) throw new NullPointerException("variable");
    var scope = scope(org, dashboard, version);
    store.put(
        "dashboard-variable", key(scope, variable.getVarName()), scope, null, null, null, variable);
    return variable;
  }

  public void delete(String org, String dashboard, String version, String name) {
    store.delete("dashboard-variable", key(scope(org, dashboard, version), name));
  }

  public List<DashboardVariable> list(String org, String dashboard, String version) {
    return store.list(
        "dashboard-variable", scope(org, dashboard, version), null, DashboardVariable.class);
  }
}
