/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.okapi.overview.OverviewController;
import org.okapi.spring.configs.Profiles;
import org.okapi.spring.configs.ch.ChConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = OkapiIngester.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(Profiles.PROFILE_CH)
class SmokeTests {
  private static final Path WAL_ROOT = createTempWalRoot();

  @Autowired ApplicationContext context;
  @Autowired OverviewController overviewController;
  @Autowired ChConfig chConfig;

  @DynamicPropertySource
  static void walProperties(DynamicPropertyRegistry registry) {
    registry.add("okapi.clickhouse.chMetricsWal", () -> WAL_ROOT.resolve("metrics").toString());
    registry.add("okapi.clickhouse.chLogsWal", () -> WAL_ROOT.resolve("logs").toString());
    registry.add("okapi.clickhouse.chTracesWal", () -> WAL_ROOT.resolve("traces").toString());
  }

  @Test
  void contextLoads() {
    assertNotNull(context);
    assertNotNull(overviewController);
    assertNotNull(chConfig);
  }

  private static Path createTempWalRoot() {
    try {
      return Files.createTempDirectory("okapi-ingester-smoke-");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }
}
