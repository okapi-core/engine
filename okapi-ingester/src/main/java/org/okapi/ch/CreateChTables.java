/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ch;

import com.clickhouse.client.api.Client;
import lombok.extern.slf4j.Slf4j;
import org.okapi.ch.tables.ChTableSpec;
import org.okapi.spring.configs.Profiles;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile(Profiles.PROFILE_CH)
@ConditionalOnProperty(name = "okapi.clickhouse.migrateOnStartup", havingValue = "true")
public class CreateChTables implements CommandLineRunner {
  private final Client client;

  public CreateChTables(Client client) {
    this.client = client;
  }

  @Override
  public void run(String... args) {
    log.info("Creating ClickHouse database/tables via ChTableSpec.");
    migrate(client);
    log.info("ClickHouse schema migration completed.");
  }

  public static void migrate(Client client) {
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_metrics");
    client.queryAll(ChTableSpec.getGaugeTableSpec());
    client.queryAll(ChTableSpec.getHistoTableSpec());
    client.queryAll(ChTableSpec.getExponentialHistoTableSpec());
    client.queryAll(ChTableSpec.getSumTableSpec());
    client.queryAll(ChTableSpec.getMetricEventsMetaTableSpec());
    client.queryAll(ChTableSpec.getExemplarsTableSpec());
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_traces");
    client.queryAll(ChTableSpec.getTracesTableSpec());
    client.queryAll(ChTableSpec.getSpansIngestedAttribsTableSpec());
    client.queryAll(ChTableSpec.getServiceRedEventsTableSpec());
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_logs");
    client.queryAll(ChTableSpec.getLogsTableSpec());
  }
}
