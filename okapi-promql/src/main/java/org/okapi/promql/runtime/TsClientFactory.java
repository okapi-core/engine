/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.runtime;

import java.util.Optional;
import org.okapi.promql.eval.ts.TsClient;

public interface TsClientFactory {
  Optional<TsClient> getClient();
}
