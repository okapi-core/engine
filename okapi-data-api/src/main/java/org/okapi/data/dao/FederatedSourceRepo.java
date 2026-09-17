/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import java.util.Optional;
import org.okapi.data.model.FederatedSource;

public interface FederatedSourceRepo {

  Optional<FederatedSource> getSource(String tenantId, String sourceName);

  List<FederatedSource> getAllSources(String tenantId);

  void createSource(FederatedSource source);

  void deleteSource(String tenantId, String sourceName);
}
