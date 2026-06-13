/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.Optional;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.model.Organization;

public final class OrgDaoPg implements OrgDao {
  private final JdbcRecordStore store;

  public OrgDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  public Optional<Organization> findById(String id) {
    return store.get("organization", id, Organization.class);
  }

  public void save(Organization organization) {
    store.put("organization", organization.getOrgId(), null, null, null, null, organization);
  }
}
