/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch.reds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Status;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.chtest.ChTestOnlyUtils;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.rest.traces.TimestampFilter;
import org.okapi.rest.traces.red.ListServicesRequest;
import org.okapi.rest.traces.red.ServiceRedRequest;
import org.okapi.rest.traces.red.ServiceRedResponse;
import org.okapi.testmodules.guice.TestChTracesModule;
import org.okapi.timeutils.TimeUtils;
import org.okapi.traces.OtelTestFactory;
import org.okapi.traces.ch.ChTracesWalConsumerDriver;
import org.okapi.traces.core.FakeTracesEventEmitter;
import org.okapi.traces.core.TracesEvent;

public class ChRedIntegrationAdditionalTests {
  private static final double FLOAT_TOLERANCE = 1e-12;

  @TempDir Path tempDir;

  private Injector injector;
  private Client client;
  private ChRedQueryService redQueryService;
  private long baseMs;
  private OtelTestFactory otelTestFactory;

  @BeforeEach
  void setup() throws Exception {
    injector = Guice.createInjector(new TestChTracesModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_SERVICE_RED_EVENTS);
    redQueryService = injector.getInstance(ChRedQueryService.class);
    baseMs = TimeUtils.roundToNearestHour(1_700_000_000_000L);
    otelTestFactory = new OtelTestFactory();
  }

  @Test
  void nullResTypeDefaultsToSecondly() throws Exception {
    ingest(
        List.of(
            otelTestFactory.resourceSpans(
                "svc-default",
                List.of(
                    otelTestFactory.span(
                        "op.alpha",
                        "svc-p1",
                        baseMs + 1_200L,
                        100L,
                        Status.StatusCode.STATUS_CODE_OK),
                    otelTestFactory.span(
                        "op.alpha",
                        "svc-p1",
                        baseMs + 1_800L,
                        200L,
                        Status.StatusCode.STATUS_CODE_ERROR),
                    otelTestFactory.span(
                        "op.beta",
                        "svc-p2",
                        baseMs + 2_200L,
                        50L,
                        Status.StatusCode.STATUS_CODE_OK)))));

    var response = queryRed("svc-default", null, baseMs, baseMs + 5_000L);
    assertNotNull(response);
    var serviceRed = response.getServiceRed();
    assertEquals(List.of(baseMs + 1_000L, baseMs + 2_000L), serviceRed.getTs());
    assertEquals(List.of(2L, 1L), serviceRed.getCounts());
    assertEquals(List.of(2.0, 1.0), serviceRed.getRps());
    assertEquals(List.of(120.0, 60.0), serviceRed.getRpm());
    assertEquals(List.of(0.5, 0.0), serviceRed.getErrorRates());
    assertEquals(List.of(1L, 0L), serviceRed.getErrors());
    assertEquals(3L, serviceRed.getTotalRequests());
    assertEquals(1L, serviceRed.getTotalErrors());
    assertEquals(2.0 / 3.0, serviceRed.getAvailability(), FLOAT_TOLERANCE);
  }

  @Test
  void timestampFilterHonorsStartAndEnd() throws Exception {
    ingest(
        List.of(
            otelTestFactory.resourceSpans(
                "svc-filter",
                List.of(
                    otelTestFactory.span(
                        "op.filtered",
                        "svc-peer",
                        baseMs + 1_000L,
                        500L,
                        Status.StatusCode.STATUS_CODE_OK),
                    otelTestFactory.span(
                        "op.filtered",
                        "svc-peer",
                        baseMs + 1_500L,
                        1_000L,
                        Status.StatusCode.STATUS_CODE_OK),
                    otelTestFactory.span(
                        "op.filtered",
                        "svc-peer",
                        baseMs + 900L,
                        50L,
                        Status.StatusCode.STATUS_CODE_OK)))));

    var response = queryRed("svc-filter", RES_TYPE.SECONDLY, baseMs + 1_000L, baseMs + 2_000L);
    assertNotNull(response);
    var serviceRed = response.getServiceRed();
    assertEquals(List.of(baseMs + 1_000L), serviceRed.getTs());
    assertEquals(List.of(1L), serviceRed.getCounts());
    assertEquals(List.of(0L), serviceRed.getErrors());
  }

  @Test
  void emptyNamesExcludedFromOpsAndPeers() throws Exception {
    ingest(
        List.of(
            otelTestFactory.resourceSpans(
                "svc-blank",
                List.of(
                    otelTestFactory.span(
                        "", "svc-p1", baseMs + 1_000L, 100L, Status.StatusCode.STATUS_CODE_OK),
                    otelTestFactory.span(
                        "op.valid", "", baseMs + 1_000L, 120L, Status.StatusCode.STATUS_CODE_OK),
                    otelTestFactory.span(
                        "", "", baseMs + 1_000L, 130L, Status.StatusCode.STATUS_CODE_ERROR)))));

    var response = queryRed("svc-blank", RES_TYPE.SECONDLY, baseMs, baseMs + 2_000L);
    assertNotNull(response);
    assertEquals(1, response.getServiceOpReds().size());
    assertEquals("op.valid", response.getServiceOpReds().getFirst().getOp());
    assertEquals(1, response.getPeerReds().size());
    assertEquals("svc-p1", response.getPeerReds().getFirst().getPeerService());
    assertEquals(1, response.getTotalDetectedOps());

    var serviceRed = response.getServiceRed();
    assertEquals(List.of(baseMs + 1_000L), serviceRed.getTs());
    assertEquals(List.of(3L), serviceRed.getCounts());
    assertEquals(List.of(3.0), serviceRed.getRps());
    assertEquals(List.of(180.0), serviceRed.getRpm());
    assertEquals(List.of(1.0 / 3.0), serviceRed.getErrorRates());
    assertEquals(List.of(1L), serviceRed.getErrors());
    assertEquals(3L, serviceRed.getTotalRequests());
    assertEquals(1L, serviceRed.getTotalErrors());
    assertEquals(2.0 / 3.0, serviceRed.getAvailability(), FLOAT_TOLERANCE);
  }

  @Test
  void unknownServiceReturnsEmptyMetricsWithNoAvailability() {
    var response = queryRed("svc-missing", RES_TYPE.SECONDLY, baseMs, baseMs + 2_000L);
    assertNotNull(response);
    assertEquals(List.of(), response.getServiceRed().getTs());
    assertEquals(List.of(), response.getServiceRed().getCounts());
    assertEquals(List.of(), response.getServiceRed().getRps());
    assertEquals(List.of(), response.getServiceRed().getRpm());
    assertEquals(List.of(), response.getServiceRed().getErrorRates());
    assertEquals(List.of(), response.getServiceRed().getErrors());
    assertEquals(0L, response.getServiceRed().getTotalRequests());
    assertEquals(0L, response.getServiceRed().getTotalErrors());
    assertNull(response.getServiceRed().getAvailability());
    assertEquals(List.of(), response.getServiceOpReds());
    assertEquals(List.of(), response.getPeerReds());
    assertEquals(0, response.getTotalDetectedOps());
  }

  @Test
  void allErrorsProduceZeroAvailability() throws Exception {
    ingest(
        List.of(
            otelTestFactory.resourceSpans(
                "svc-errors",
                List.of(
                    otelTestFactory.span(
                        "op.fail",
                        "svc-peer",
                        baseMs + 1_000L,
                        100L,
                        Status.StatusCode.STATUS_CODE_ERROR),
                    otelTestFactory.span(
                        "op.fail",
                        "svc-peer",
                        baseMs + 1_500L,
                        200L,
                        Status.StatusCode.STATUS_CODE_ERROR)))));

    var response = queryRed("svc-errors", RES_TYPE.SECONDLY, baseMs, baseMs + 2_000L);
    assertNotNull(response);
    var serviceRed = response.getServiceRed();
    assertEquals(List.of(baseMs + 1_000L), serviceRed.getTs());
    assertEquals(List.of(2L), serviceRed.getCounts());
    assertEquals(List.of(2.0), serviceRed.getRps());
    assertEquals(List.of(120.0), serviceRed.getRpm());
    assertEquals(List.of(1.0), serviceRed.getErrorRates());
    assertEquals(List.of(2L), serviceRed.getErrors());
    assertEquals(2L, serviceRed.getTotalRequests());
    assertEquals(2L, serviceRed.getTotalErrors());
    assertEquals(0.0, serviceRed.getAvailability());
  }

  @Test
  void serviceListFilteredByTimeWindow() throws Exception {
    ingest(
        List.of(
            otelTestFactory.resourceSpans(
                "svc-in-window",
                List.of(
                    otelTestFactory.span(
                        "op.list",
                        "svc-peer",
                        baseMs + 1_000L,
                        100L,
                        Status.StatusCode.STATUS_CODE_OK))),
            otelTestFactory.resourceSpans(
                "svc-outside",
                List.of(
                    otelTestFactory.span(
                        "op.list",
                        "svc-peer",
                        baseMs - 3_600_000L,
                        100L,
                        Status.StatusCode.STATUS_CODE_OK)))));

    var filter =
        TimestampFilter.builder()
            .tsStartNanos(TimeUtils.millisToNanos(baseMs))
            .tsEndNanos(TimeUtils.millisToNanos(baseMs + 2_000L))
            .build();
    var request = ListServicesRequest.builder().timestampFilter(filter).build();
    var response = redQueryService.queryServiceList(request);
    assertNotNull(response);
    assertEquals(List.of("svc-in-window"), response.getServices());
  }

  private ServiceRedResponse queryRed(String service, RES_TYPE resType, long startMs, long endMs) {
    var filter =
        TimestampFilter.builder()
            .tsStartNanos(TimeUtils.millisToNanos(startMs))
            .tsEndNanos(TimeUtils.millisToNanos(endMs))
            .build();
    var request =
        ServiceRedRequest.builder()
            .service(service)
            .resType(resType)
            .timestampFilter(filter)
            .build();
    return redQueryService.queryRed(request);
  }

  private void ingest(List<ResourceSpans> resources) throws Exception {
    var emitter = injector.getInstance(FakeTracesEventEmitter.class);
    var driver = injector.getInstance(ChTracesWalConsumerDriver.class);
    var request = otelTestFactory.buildRequest(resources);
    emitter.add(new TracesEvent(request.toByteArray()));
    driver.onTick();
  }
}
