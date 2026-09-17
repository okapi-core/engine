/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import com.clickhouse.client.api.Client;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.promql.eval.ts.SeriesDiscovery;
import org.okapi.promql.runtime.SeriesDiscoveryFactory;

public class ChPromQlSeriesDiscoveryFactory implements SeriesDiscoveryFactory {
  private final Client client;
  private final ChMetricTemplateEngine templateEngine;

  public ChPromQlSeriesDiscoveryFactory(Client client, ChMetricTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  @Override
  public SeriesDiscovery get() {
    return new ChPromQlSeriesDiscovery(client, templateEngine);
  }
}
