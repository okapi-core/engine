/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.okapi.engine.api.TracesEngine;
import org.okapi.traces.ch.ChTracesIngester;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OtelTracesControllerConfigTests {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(OtelTracesController.class)
          .withBean(TracesEngine.class, () -> mock(TracesEngine.class))
          .withBean(ChTracesIngester.class, () -> mock(ChTracesIngester.class));

  @Test
  void enablesControllerByDefault() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(OtelTracesController.class));
  }

  @Test
  void disablesControllerInKafkaMode() {
    contextRunner
        .withPropertyValues("okapi.traces.consumptionType=kafka")
        .run(context -> assertThat(context).doesNotHaveBean(OtelTracesController.class));
  }
}
