/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.FederatedSourceRepo;
import org.okapi.data.model.FederatedSource;

public final class FederatedSourceRepoPg implements FederatedSourceRepo {
  private final JdbcRecordStore store;

  public FederatedSourceRepoPg(JdbcRecordStore store) {
    this.store = store;
  }

  public Optional<FederatedSource> getSource(String tenant, String source) {
    return store.get("federated-source", key(tenant, source), FederatedSource.class);
  }

  public List<FederatedSource> getAllSources(String tenant) {
    return store.list("federated-source", tenant, null, FederatedSource.class);
  }

  public void createSource(FederatedSource source) {
    store.put(
        "federated-source",
        key(source.getOrgId(), source.getSourceName()),
        source.getOrgId(),
        null,
        null,
        null,
        source);
  }

  public void deleteSource(String tenant, String source) {
    store.delete("federated-source", key(tenant, source));
  }
}
