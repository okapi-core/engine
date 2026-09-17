/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch.reds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.trace.v1.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.chtest.ChTestOnlyUtils;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.rest.traces.TimestampFilter;
import org.okapi.rest.traces.red.ServiceRedRequest;
import org.okapi.rest.traces.red.ServiceRedResponse;
import org.okapi.testmodules.guice.TestChTracesModule;
import org.okapi.timeutils.TimeUtils;
import org.okapi.traces.OtelTestFactory;
import org.okapi.traces.ch.ChTracesWalConsumerDriver;
import org.okapi.traces.core.FakeTracesEventEmitter;
import org.okapi.traces.core.TracesEvent;

@TestInstance(Lifecycle.PER_CLASS)
public class ChRedIntegrationEdgeTests {

  private Injector injector;
  private Client client;
  private ChRedQueryService redQueryService;
  private long baseMs;
  private OtelTestFactory otelTestFactory;

  @BeforeAll
  void setup() throws Exception {
    Path tempDir = Files.createTempDirectory("okapi-red-edge-");
    injector = Guice.createInjector(new TestChTracesModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_SERVICE_RED_EVENTS);
    redQueryService = injector.getInstance(ChRedQueryService.class);
    baseMs = TimeUtils.roundToNearestHour(1_700_000_000_000L);
    otelTestFactory = new OtelTestFactory();
    ingestCorpus();
  }

  @Test
  void opRedsAreTruncated() {
    var response = queryRed();
    assertNotNull(response);
    assertEquals(20, response.getServiceOpReds().size());
    var ops = response.getServiceOpReds().stream().map(r -> r.getOp()).toList();
    assertEquals(20, new HashSet<>(ops).size());
  }

  @Test
  void totalDetectedOpsReported() {
    var response = queryRed();
    assertNotNull(response);
    assertEquals(25, response.getTotalDetectedOps());
  }

  private void ingestCorpus() throws Exception {
    var emitter = injector.getInstance(FakeTracesEventEmitter.class);
    var driver = injector.getInstance(ChTracesWalConsumerDriver.class);

    var spans = new ArrayList<io.opentelemetry.proto.trace.v1.Span>();
    for (int i = 1; i <= 25; i++) {
      spans.add(
          otelTestFactory.span(
              "op-" + i, "svc-peer", baseMs + 1_000L, 50L, Status.StatusCode.STATUS_CODE_OK));
    }
    var request =
        otelTestFactory.buildRequest(List.of(otelTestFactory.resourceSpans("svc-edge", spans)));
    emitter.add(new TracesEvent(request.toByteArray()));
    driver.onTick();
  }

  private ServiceRedResponse queryRed() {
    var filter =
        TimestampFilter.builder()
            .tsStartNanos(TimeUtils.millisToNanos(baseMs))
            .tsEndNanos(TimeUtils.millisToNanos(baseMs + 10_000L))
            .build();
    var request =
        ServiceRedRequest.builder()
            .service("svc-edge")
            .resType(RES_TYPE.SECONDLY)
            .timestampFilter(filter)
            .build();
    return redQueryService.queryRed(request);
  }
}
