/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.Optional;
import org.okapi.data.model.OrgSummary;
import org.okapi.data.model.Organization;

public interface OrgDao {
  Optional<Organization> findById(String orgId);

  Optional<OrgSummary> getSummary(String orgId);

  void save(Organization organization);

  boolean createIfNotExists(Organization organization);
}
