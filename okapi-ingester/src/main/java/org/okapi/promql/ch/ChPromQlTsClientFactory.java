/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import com.clickhouse.client.api.Client;
import java.util.Optional;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.promql.eval.ts.TsClient;
import org.okapi.promql.runtime.TsClientFactory;

public class ChPromQlTsClientFactory implements TsClientFactory {
  private final Client client;
  private final ChMetricTemplateEngine templateEngine;

  public ChPromQlTsClientFactory(Client client, ChMetricTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  @Override
  public Optional<TsClient> getClient() {
    return Optional.of(new ChPromQlTsClient(client, templateEngine));
  }
}
