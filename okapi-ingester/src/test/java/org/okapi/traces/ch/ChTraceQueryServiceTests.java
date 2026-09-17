/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.okapi.otelshorthand.OtelShortHands.keyValue;
import static org.okapi.otelshorthand.OtelShortHands.utf8Bytes;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.otel.OtelAnyValueDecoder;
import org.okapi.otelshorthand.OtelShortHands;
import org.okapi.rest.traces.*;
import org.okapi.rest.traces.SpanStatus;
import org.okapi.testmodules.guice.TestChTracesModule;
import org.okapi.traces.core.FakeTracesEventEmitter;
import org.okapi.traces.core.TracesEvent;

@TestInstance(Lifecycle.PER_CLASS)
public class ChTraceQueryServiceTests {

  private Injector injector;
  private Client client;

  private String traceIdAHex;
  private String traceIdBHex;
  private String traceIdCHex;
  private String traceIdDHex;
  private String kindServer;

  ByteString traceIdA = utf8Bytes("trace-id-0000001");
  ByteString spanIdA = utf8Bytes("span0001");
  ByteString traceIdA2 = utf8Bytes("trace-id-0000001");
  ByteString spanIdA2 = utf8Bytes("span0002");
  ByteString traceIdB = utf8Bytes("trace-id-0000002");
  ByteString spanIdB = utf8Bytes("span0003");
  ByteString traceIdC = utf8Bytes("trace-id-0000003");
  ByteString spanIdC = utf8Bytes("span0004");
  ByteString traceIdD = utf8Bytes("trace-id-0000004");
  ByteString spanIdD = utf8Bytes("span0005");

  @BeforeAll
  void setup() throws Exception {
    Path tempDir = Files.createTempDirectory("okapi-traces-query-");
    injector = Guice.createInjector(new TestChTracesModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    truncateTable();
    ingestCorpus();
  }

  private void ingestCorpus() throws Exception {
    var emitter = injector.getInstance(FakeTracesEventEmitter.class);
    var driver = injector.getInstance(ChTracesWalConsumerDriver.class);

    var request =
        ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(buildHttpResourceSpans(traceIdA, spanIdA))
            .addResourceSpans(buildHttpResourceSpansAlt(traceIdA2, spanIdA2))
            .addResourceSpans(buildDbResourceSpans(traceIdB, spanIdB))
            .addResourceSpans(buildRpcResourceSpans(traceIdC, spanIdC))
            .addResourceSpans(buildBoundaryResourceSpans(traceIdD, spanIdD))
            .build();

    emitter.add(new TracesEvent(request.toByteArray()));
    driver.onTick();

    traceIdAHex = OtelAnyValueDecoder.bytesToHex(traceIdA.toByteArray());
    traceIdBHex = OtelAnyValueDecoder.bytesToHex(traceIdB.toByteArray());
    traceIdCHex = OtelAnyValueDecoder.bytesToHex(traceIdC.toByteArray());
    traceIdDHex = OtelAnyValueDecoder.bytesToHex(traceIdD.toByteArray());
    kindServer = Span.SpanKind.SPAN_KIND_SERVER.name();
  }

  @Test
  public void checkMatchingFilters() {
    var queryService = injector.getInstance(ChTraceQueryService.class);

    var byTraceId =
        SpanQueryV2Request.builder()
            .traceId(traceIdAHex)
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    var respByTrace = queryService.getSpans(byTraceId);
    assertEquals(2, respByTrace.getItems().size());

    var byKind =
        SpanQueryV2Request.builder()
            .kind(kindServer)
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    var respByKind = queryService.getSpans(byKind);
    assertEquals(3, respByKind.getItems().size());

    var byServiceAndPeer =
        SpanQueryV2Request.builder()
            .serviceFilter(ServiceFilter.builder().service("svc-http").peer("peer-svc").build())
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    var respByServicePeer = queryService.getSpans(byServiceAndPeer);
    assertEquals(1, respByServicePeer.getItems().size());

    var byHttpFilters =
        SpanQueryV2Request.builder()
            .httpFilters(
                HttpFilters.builder()
                    .httpMethod("GET")
                    .statusCode(200)
                    .origin("example.com")
                    .host("example.org")
                    .build())
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    var respByHttp = queryService.getSpans(byHttpFilters);
    assertSingleMatch(respByHttp, "svc-http", traceIdAHex, kindServer);

    var byDbFilters =
        SpanQueryV2Request.builder()
            .dbFilters(
                DbFilters.builder()
                    .system("mysql")
                    .namespace("db-main")
                    .collection("orders")
                    .operation("select")
                    .build())
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    var respByDb = queryService.getSpans(byDbFilters);
    assertSingleMatch(respByDb, "svc-db", traceIdBHex, Span.SpanKind.SPAN_KIND_CLIENT.name());

    var byTimestamp =
        SpanQueryV2Request.builder()
            .timestampFilter(
                TimestampFilter.builder()
                    .tsStartNanos(2_000_000_000L)
                    .tsEndNanos(3_000_000_000L)
                    .build())
            .build();
    var respByTs = queryService.getSpans(byTimestamp);
    assertSingleMatch(respByTs, "svc-rpc", traceIdCHex, kindServer);

    var byDuration =
        SpanQueryV2Request.builder()
            .durationFilter(DurationFilter.builder().durMinMillis(200).durMaxMillis(300).build())
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    var respByDuration = queryService.getSpans(byDuration);
    assertSingleMatch(respByDuration, "svc-db", traceIdBHex, Span.SpanKind.SPAN_KIND_CLIENT.name());
  }

  @Test
  public void checkNullAttributeHandles() {
    var queryService = injector.getInstance(ChTraceQueryService.class);
    var byTraceId =
        SpanQueryV2Request.builder()
            .traceId(traceIdCHex)
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    var resp = queryService.getSpans(byTraceId);
    assertEquals(1, resp.getItems().size());
    var row = resp.getItems().get(0);
    assertNotNull(row);
    assertEquals("svc-rpc", row.getServiceName());
    assertEquals(traceIdCHex, row.getTraceId());
    assertEquals(kindServer, row.getKind());
    assertEquals("", row.getHttpMethod());
    assertEquals("", row.getDbSystemName());
    assertEquals(org.okapi.rest.traces.SpanStatus.UNK, row.getSpanStatus());
  }

  @Test
  public void checkSpanStatusMapping() {
    var queryService = injector.getInstance(ChTraceQueryService.class);
    var tsFilter = TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build();

    var okReq =
        SpanQueryV2Request.builder()
            .spanId(OtelAnyValueDecoder.bytesToHex(spanIdA.toByteArray()))
            .timestampFilter(tsFilter)
            .build();
    var okResp = queryService.getSpans(okReq);
    assertEquals(1, okResp.getItems().size());
    assertEquals(org.okapi.rest.traces.SpanStatus.OK, okResp.getItems().get(0).getSpanStatus());

    var okDbReq =
        SpanQueryV2Request.builder()
            .spanId(OtelAnyValueDecoder.bytesToHex(spanIdB.toByteArray()))
            .timestampFilter(tsFilter)
            .build();
    var okDbResp = queryService.getSpans(okDbReq);
    assertEquals(1, okDbResp.getItems().size());
    assertEquals(org.okapi.rest.traces.SpanStatus.OK, okDbResp.getItems().get(0).getSpanStatus());

    var errReq =
        SpanQueryV2Request.builder()
            .spanId(OtelAnyValueDecoder.bytesToHex(spanIdA2.toByteArray()))
            .timestampFilter(tsFilter)
            .build();
    var errResp = queryService.getSpans(errReq);
    assertEquals(1, errResp.getItems().size());
    assertEquals(org.okapi.rest.traces.SpanStatus.ERROR, errResp.getItems().get(0).getSpanStatus());

    var unkReq =
        SpanQueryV2Request.builder()
            .spanId(OtelAnyValueDecoder.bytesToHex(spanIdC.toByteArray()))
            .timestampFilter(tsFilter)
            .build();
    var unkResp = queryService.getSpans(unkReq);
    assertEquals(1, unkResp.getItems().size());
    assertEquals(SpanStatus.UNK, unkResp.getItems().get(0).getSpanStatus());
  }

  @Test
  public void checkTypeStrictness() {
    var queryService = injector.getInstance(ChTraceQueryService.class);
    var byHttpFilters =
        SpanQueryV2Request.builder()
            .httpFilters(HttpFilters.builder().host("true").build())
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    var resp = queryService.getSpans(byHttpFilters);
    assertEquals(0, resp.getItems().size());
  }

  @Test
  public void checkPartitionBoundary() {
    var queryService = injector.getInstance(ChTraceQueryService.class);
    var byTraceId =
        SpanQueryV2Request.builder()
            .traceId(traceIdDHex)
            .timestampFilter(
                TimestampFilter.builder()
                    .tsStartNanos(3_590_000_000_000L)
                    .tsEndNanos(3_610_000_000_000L)
                    .build())
            .build();
    var resp = queryService.getSpans(byTraceId);
    assertEquals(1, resp.getItems().size());
  }

  @Test
  public void checkMultiSpanTrace() {
    var queryService = injector.getInstance(ChTraceQueryService.class);
    var byTraceId =
        SpanQueryV2Request.builder()
            .traceId(traceIdAHex)
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    var resp = queryService.getSpans(byTraceId);
    assertEquals(2, resp.getItems().size());
  }

  @Test
  public void checkAttributeFilters() {
    var queryService = injector.getInstance(ChTraceQueryService.class);
    var tsFilter = TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build();

    var byStringAttr =
        SpanQueryV2Request.builder()
            .stringAttributesFilter(
                List.of(
                    StringAttributeFilter.builder().key("custom.string").value("alpha").build()))
            .timestampFilter(tsFilter)
            .build();
    var respByString = queryService.getSpans(byStringAttr);
    assertSingleMatch(respByString, "svc-http", traceIdAHex, kindServer);

    var byNumberAttr =
        SpanQueryV2Request.builder()
            .numberAttributesFilter(
                List.of(NumberAttributeFilter.builder().key("custom.number").value(42.0).build()))
            .timestampFilter(tsFilter)
            .build();
    var respByNumber = queryService.getSpans(byNumberAttr);
    assertSingleMatch(respByNumber, "svc-http", traceIdAHex, kindServer);
  }

  @Test
  public void checkMultipleCustomStringFilters() {
    var queryService = injector.getInstance(ChTraceQueryService.class);
    var tsFilter = TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build();

    var matching =
        SpanQueryV2Request.builder()
            .stringAttributesFilter(
                List.of(
                    StringAttributeFilter.builder().key("custom.string").value("alpha").build(),
                    StringAttributeFilter.builder().key("custom.env").value("prod").build()))
            .timestampFilter(tsFilter)
            .build();
    var respMatching = queryService.getSpans(matching);
    assertSingleMatch(respMatching, "svc-http", traceIdAHex, kindServer);

    var nonMatching =
        SpanQueryV2Request.builder()
            .stringAttributesFilter(
                List.of(
                    StringAttributeFilter.builder().key("custom.string").value("alpha").build(),
                    StringAttributeFilter.builder().key("custom.env").value("stage").build()))
            .timestampFilter(tsFilter)
            .build();
    var respNonMatching = queryService.getSpans(nonMatching);
    assertEquals(0, respNonMatching.getItems().size());
  }

  @Test
  public void checkWildcardStringFilters() {
    var queryService = injector.getInstance(ChTraceQueryService.class);
    var tsFilter = TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build();

    var baseline = SpanQueryV2Request.builder().timestampFilter(tsFilter).build();
    var respBaseline = queryService.getSpans(baseline);
    assertEquals(4, respBaseline.getItems().size());

    var wildcardTraceId =
        SpanQueryV2Request.builder().traceId("*").timestampFilter(tsFilter).build();
    var respWildcardTraceId = queryService.getSpans(wildcardTraceId);
    assertEquals(4, respWildcardTraceId.getItems().size());

    var wildcardPeer =
        SpanQueryV2Request.builder()
            .serviceFilter(ServiceFilter.builder().service("svc-http").peer("*").build())
            .timestampFilter(tsFilter)
            .build();
    var respWildcardPeer = queryService.getSpans(wildcardPeer);
    assertEquals(2, respWildcardPeer.getItems().size());

    var wildcardCustomAttr =
        SpanQueryV2Request.builder()
            .serviceFilter(ServiceFilter.builder().service("svc-http").build())
            .stringAttributesFilter(
                List.of(StringAttributeFilter.builder().key("custom.string").value("*").build()))
            .timestampFilter(tsFilter)
            .build();
    var respWildcardCustomAttr = queryService.getSpans(wildcardCustomAttr);
    assertEquals(2, respWildcardCustomAttr.getItems().size());

    var wildcardHttpMethod =
        SpanQueryV2Request.builder()
            .httpFilters(HttpFilters.builder().httpMethod("*").statusCode(200).build())
            .timestampFilter(tsFilter)
            .build();
    var respWildcardHttpMethod = queryService.getSpans(wildcardHttpMethod);
    assertEquals(1, respWildcardHttpMethod.getItems().size());
  }

  @Test
  public void checkSpanIdQuery() {
    var queryService = injector.getInstance(ChTraceQueryService.class);
    var tsFilter = TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build();

    var matching =
        SpanQueryV2Request.builder()
            .spanId(OtelAnyValueDecoder.bytesToHex(spanIdA.toByteArray()))
            .timestampFilter(tsFilter)
            .build();
    var respMatching = queryService.getSpans(matching);
    assertEquals(1, respMatching.getItems().size());
  }

  @Test
  public void checkSummaryStats() {
    var queryService = injector.getInstance(ChTraceQueryService.class);
    var svcFilter = ServiceFilter.builder().service("svc-http").build();
    var summary =
        queryService.getSpansSummary(SpanQueryV2Request.builder().serviceFilter(svcFilter).build());
    assertEquals(2, summary.getCount());

    var dbFilter = ServiceFilter.builder().service("svc-db").build();
    var dbSummary =
        queryService.getSpansSummary(SpanQueryV2Request.builder().serviceFilter(dbFilter).build());
    assertEquals(1, dbSummary.getCount());

    var methodFilter = HttpFilters.builder().httpMethod("GET").build();
    var methodSummary =
        queryService.getSpansSummary(
            SpanQueryV2Request.builder().httpFilters(methodFilter).build());
    assertEquals(1, methodSummary.getCount());
  }

  private void truncateTable() {
    client.queryAll("TRUNCATE TABLE okapi_traces.spans_table_v1");
    client.queryAll("TRUNCATE TABLE okapi_traces.spans_ingested_attribs");
  }

  private ResourceSpans buildHttpResourceSpans(ByteString traceId, ByteString spanId) {
    var resource =
        Resource.newBuilder().addAttributes(keyValue("service.name", "svc-http")).build();
    var span =
        Span.newBuilder()
            .setTraceId(traceId)
            .setSpanId(spanId)
            .setName("http-span")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(1_000_000_000L)
            .setEndTimeUnixNano(1_100_000_000L)
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
            .addAttributes(keyValue("http.request.method", "GET"))
            .addAttributes(OtelShortHands.keyValue("http.response.status_code", 200))
            .addAttributes(OtelShortHands.keyValue("http.request.body.size", 64))
            .addAttributes(OtelShortHands.keyValue("http.response.body.size", 256))
            .addAttributes(keyValue("http.origin", "example.com"))
            .addAttributes(keyValue("http.host", "example.org"))
            .addAttributes(keyValue("service.peer.name", "peer-svc"))
            .addAttributes(keyValue("custom.string", "alpha"))
            .addAttributes(keyValue("custom.env", "prod"))
            .addAttributes(OtelShortHands.keyValue("custom.number", 42))
            .build();
    var scope = ScopeSpans.newBuilder().addSpans(span).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope).build();
  }

  private ResourceSpans buildHttpResourceSpansAlt(ByteString traceId, ByteString spanId) {
    var resource =
        Resource.newBuilder().addAttributes(keyValue("service.name", "svc-http")).build();
    var span =
        Span.newBuilder()
            .setTraceId(traceId)
            .setSpanId(spanId)
            .setName("http-span-2")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(1_200_000_000L)
            .setEndTimeUnixNano(1_250_000_000L)
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR).build())
            .addAttributes(keyValue("http.request.method", "POST"))
            .addAttributes(OtelShortHands.keyValue("http.response.status_code", 201))
            .addAttributes(keyValue("http.origin", "example.com"))
            .addAttributes(OtelShortHands.keyValue("http.host", true))
            .build();
    var scope = ScopeSpans.newBuilder().addSpans(span).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope).build();
  }

  private ResourceSpans buildDbResourceSpans(ByteString traceId, ByteString spanId) {
    var resource = Resource.newBuilder().addAttributes(keyValue("service.name", "svc-db")).build();
    var span =
        Span.newBuilder()
            .setTraceId(traceId)
            .setSpanId(spanId)
            .setName("db-span")
            .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
            .setStartTimeUnixNano(1_500_000_000L)
            .setEndTimeUnixNano(1_750_000_000L)
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
            .addAttributes(keyValue("db.system", "mysql"))
            .addAttributes(keyValue("db.namespace", "db-main"))
            .addAttributes(keyValue("db.collection.name", "orders"))
            .addAttributes(keyValue("db.operation", "select"))
            .addAttributes(OtelShortHands.keyValue("db.response.status_code", 0))
            .addAttributes(OtelShortHands.keyValue("db.response.returned_rows", 3))
            .build();
    var scope = ScopeSpans.newBuilder().addSpans(span).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope).build();
  }

  private ResourceSpans buildRpcResourceSpans(ByteString traceId, ByteString spanId) {
    var resource = Resource.newBuilder().addAttributes(keyValue("service.name", "svc-rpc")).build();
    var span =
        Span.newBuilder()
            .setTraceId(traceId)
            .setSpanId(spanId)
            .setName("rpc-span")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(2_500_000_000L)
            .setEndTimeUnixNano(2_550_000_000L)
            .addAttributes(keyValue("rpc.method", "List"))
            .addAttributes(keyValue("rpc.method.original", "ListV1"))
            .addAttributes(OtelShortHands.keyValue("rpc.response.status_code", 0))
            .build();
    var scope = ScopeSpans.newBuilder().addSpans(span).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope).build();
  }

  private ResourceSpans buildBoundaryResourceSpans(ByteString traceId, ByteString spanId) {
    var resource =
        Resource.newBuilder().addAttributes(keyValue("service.name", "svc-boundary")).build();
    var span1 =
        Span.newBuilder()
            .setTraceId(traceId)
            .setSpanId(spanId)
            .setName("boundary-1")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(3_590_000_000_000L)
            .setEndTimeUnixNano(3_590_100_000_000L)
            .build();
    var span2 =
        Span.newBuilder()
            .setTraceId(traceId)
            .setSpanId(utf8Bytes("span0006"))
            .setName("boundary-2")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(3_610_000_000_000L)
            .setEndTimeUnixNano(3_610_100_000_000L)
            .build();
    var scope = ScopeSpans.newBuilder().addSpans(span1).addSpans(span2).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope).build();
  }

  private void assertSingleMatch(
      SpanQueryV2Response resp, String svc, String traceId, String kind) {
    assertNotNull(resp);
    assertEquals(1, resp.getItems().size());
    SpanRowV2 row = resp.getItems().get(0);
    assertEquals(svc, row.getServiceName());
    assertEquals(traceId, row.getTraceId());
    assertEquals(kind, row.getKind());
  }
}
