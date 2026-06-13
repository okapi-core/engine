/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch.reds;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.trace.v1.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.chtest.ChTestOnlyUtils;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.rest.traces.TimestampFilter;
import org.okapi.rest.traces.red.*;
import org.okapi.testmodules.guice.TestChTracesModule;
import org.okapi.timeutils.TimeUtils;
import org.okapi.traces.OtelTestFactory;
import org.okapi.traces.ch.ChTracesIngester;
import org.okapi.traces.ch.ChTracesWalConsumerDriver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChRedIntegrationNormalTests {

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
    ingestCorpus();
  }

  @Test
  void testServiceRedSecondly() {
    var response = queryRed(RES_TYPE.SECONDLY);
    assertEquals(serviceRedSecondly(), response.getServiceRed());
    assertOpRed(response.getServiceOpReds(), "op.login", opLoginSecondly());
    assertOpRed(response.getServiceOpReds(), "op.search", opSearchSecondly());
    assertPeerRed(response.getPeerReds(), "svc-B", opLoginSecondly());
    assertPeerRed(response.getPeerReds(), "svc-C", opSearchSecondly());
  }

  @Test
  void testServiceRedMinutely() {
    var response = queryRed(RES_TYPE.MINUTELY);
    assertEquals(serviceRedMinutely(), response.getServiceRed());
    assertOpRed(response.getServiceOpReds(), "op.login", opLoginMinutely());
    assertOpRed(response.getServiceOpReds(), "op.search", opSearchMinutely());
    assertPeerRed(response.getPeerReds(), "svc-B", opLoginMinutely());
    assertPeerRed(response.getPeerReds(), "svc-C", opSearchMinutely());
  }

  @Test
  void testServiceRedHourly() {
    var response = queryRed(RES_TYPE.HOURLY);
    assertEquals(serviceRedHourly(), response.getServiceRed());
    assertOpRed(response.getServiceOpReds(), "op.login", opLoginHourly());
    assertOpRed(response.getServiceOpReds(), "op.search", opSearchHourly());
    assertPeerRed(response.getPeerReds(), "svc-B", opLoginHourly());
    assertPeerRed(response.getPeerReds(), "svc-C", opSearchHourly());
  }

  @Test
  void serviceListReturnsServices() {
    var filter =
        TimestampFilter.builder()
            .tsStartNanos(TimeUtils.millisToNanos(baseMs))
            .tsEndNanos(TimeUtils.millisToNanos(baseMs + 3_600_000L + 1_000L))
            .build();
    var request = ListServicesRequest.builder().timestampFilter(filter).build();
    ServiceListResponse response = redQueryService.queryServiceList(request);
    assertNotNull(response);
    assertEquals(List.of("svc-A", "svc-Other"), response.getServices());
  }

  private ServiceRedResponse queryRed(RES_TYPE resType) {
    var filter =
        TimestampFilter.builder()
            .tsStartNanos(TimeUtils.millisToNanos(baseMs))
            .tsEndNanos(TimeUtils.millisToNanos(baseMs + 3_600_000L + 1_000L))
            .build();
    var request =
        ServiceRedRequest.builder()
            .service("svc-A")
            .resType(resType)
            .timestampFilter(filter)
            .build();
    return redQueryService.queryRed(request);
  }

  private void ingestCorpus() throws Exception {
    var ingester = injector.getInstance(ChTracesIngester.class);
    var driver = injector.getInstance(ChTracesWalConsumerDriver.class);

    var svcASpans = new ArrayList<io.opentelemetry.proto.trace.v1.Span>();
    svcASpans.add(
        otelTestFactory.span(
            "op.login", "svc-B", baseMs + 1_000L, 100L, Status.StatusCode.STATUS_CODE_OK));
    svcASpans.add(
        otelTestFactory.span(
            "op.search", "svc-C", baseMs + 1_000L, 100L, Status.StatusCode.STATUS_CODE_OK));
    svcASpans.add(
        otelTestFactory.span(
            "op.login", "svc-B", baseMs + 2_000L, 100L, Status.StatusCode.STATUS_CODE_OK));
    svcASpans.add(
        otelTestFactory.span(
            "op.search", "svc-C", baseMs + 2_000L, 100L, Status.StatusCode.STATUS_CODE_OK));
    svcASpans.add(
        otelTestFactory.span(
            "op.login", "svc-B", baseMs + 60_000L, 100L, Status.StatusCode.STATUS_CODE_OK));
    svcASpans.add(
        otelTestFactory.span(
            "op.search", "svc-C", baseMs + 60_000L, 100L, Status.StatusCode.STATUS_CODE_OK));
    svcASpans.add(
        otelTestFactory.span(
            "op.login", "svc-B", baseMs + 60_000L, 999L, Status.StatusCode.STATUS_CODE_ERROR));
    svcASpans.add(
        otelTestFactory.span(
            "op.login", "svc-B", baseMs + 3_600_000L, 400L, Status.StatusCode.STATUS_CODE_OK));
    svcASpans.add(
        otelTestFactory.span(
            "op.search", "svc-C", baseMs + 3_600_000L, 400L, Status.StatusCode.STATUS_CODE_OK));
    svcASpans.add(
        otelTestFactory.span(
            "op.search", "svc-C", baseMs + 3_600_000L, 888L, Status.StatusCode.STATUS_CODE_ERROR));

    var otherSpans = new ArrayList<io.opentelemetry.proto.trace.v1.Span>();
    otherSpans.add(
        otelTestFactory.span(
            "op.login", "svc-B", baseMs + 1_000L, 150L, Status.StatusCode.STATUS_CODE_OK));

    var request =
        otelTestFactory.buildRequest(
            List.of(
                otelTestFactory.resourceSpans("svc-A", svcASpans),
                otelTestFactory.resourceSpans("svc-Other", otherSpans)));
    ingester.ingest(request);
    driver.onTick();
  }

  private void assertOpRed(List<ServiceOpRed> reds, String op, RedMetrics expected) {
    var found = reds.stream().filter(r -> op.equals(r.getOp())).findFirst();
    found.ifPresentOrElse(
        red -> assertEquals(expected, red.getRedMetrics()), () -> fail("Missing op red for " + op));
  }

  private void assertPeerRed(List<ServiceEdgeRed> reds, String peer, RedMetrics expected) {
    var found = reds.stream().filter(r -> peer.equals(r.getPeerService())).findFirst();
    found.ifPresentOrElse(
        red -> assertEquals(expected, red.getRedMetrics()),
        () -> fail("Missing peer red for " + peer));
  }

  private RedMetrics serviceRedSecondly() {
    return RedMetrics.of(
        List.of(baseMs + 1_000L, baseMs + 2_000L, baseMs + 60_000L, baseMs + 3_600_000L),
        List.of(2L, 2L, 3L, 3L),
        List.of(0L, 0L, 1L, 1L),
        List.of(100.0, 100.0, 100.0, 400.0));
  }

  private RedMetrics serviceRedMinutely() {
    return RedMetrics.of(
        List.of(baseMs, baseMs + 60_000L, baseMs + 3_600_000L),
        List.of(4L, 3L, 3L),
        List.of(0L, 1L, 1L),
        List.of(100.0, 100.0, 400.0));
  }

  private RedMetrics serviceRedHourly() {
    return RedMetrics.of(
        List.of(baseMs, baseMs + 3_600_000L),
        List.of(7L, 3L),
        List.of(1L, 1L),
        List.of(100.0, 400.0));
  }

  private RedMetrics opLoginSecondly() {
    return RedMetrics.of(
        List.of(baseMs + 1_000L, baseMs + 2_000L, baseMs + 60_000L, baseMs + 3_600_000L),
        List.of(1L, 1L, 2L, 1L),
        List.of(0L, 0L, 1L, 0L),
        List.of(100.0, 100.0, 100.0, 400.0));
  }

  private RedMetrics opLoginMinutely() {
    return RedMetrics.of(
        List.of(baseMs, baseMs + 60_000L, baseMs + 3_600_000L),
        List.of(2L, 2L, 1L),
        List.of(0L, 1L, 0L),
        List.of(100.0, 100.0, 400.0));
  }

  private RedMetrics opLoginHourly() {
    return RedMetrics.of(
        List.of(baseMs, baseMs + 3_600_000L),
        List.of(4L, 1L),
        List.of(1L, 0L),
        List.of(100.0, 400.0));
  }

  private RedMetrics opSearchSecondly() {
    return RedMetrics.of(
        List.of(baseMs + 1_000L, baseMs + 2_000L, baseMs + 60_000L, baseMs + 3_600_000L),
        List.of(1L, 1L, 1L, 2L),
        List.of(0L, 0L, 0L, 1L),
        List.of(100.0, 100.0, 100.0, 400.0));
  }

  private RedMetrics opSearchMinutely() {
    return RedMetrics.of(
        List.of(baseMs, baseMs + 60_000L, baseMs + 3_600_000L),
        List.of(2L, 1L, 2L),
        List.of(0L, 0L, 1L),
        List.of(100.0, 100.0, 400.0));
  }

  private RedMetrics opSearchHourly() {
    return RedMetrics.of(
        List.of(baseMs, baseMs + 3_600_000L),
        List.of(3L, 2L),
        List.of(0L, 1L),
        List.of(100.0, 400.0));
  }
}
