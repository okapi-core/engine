/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.otel;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.metrics.otel.OtelConverter;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.MetricType;
import org.okapi.rest.metrics.payloads.SUM_TEMPORALITY;

public class OtelConverterSumTest {

  @Test
  void sumConversion_delta() {
    // Resource tag
    KeyValue resTag =
        KeyValue.newBuilder()
            .setKey("service.name")
            .setValue(AnyValue.newBuilder().setStringValue("svc").build())
            .build();

    NumberDataPoint p1 =
        NumberDataPoint.newBuilder()
            .setStartTimeUnixNano(1_000_000L)
            .setTimeUnixNano(2_000_000L)
            .setAsInt(3)
            .addExemplars(
                Exemplar.newBuilder()
                    .setTimeUnixNano(1_500_000L)
                    .setAsDouble(3.0)
                    .setTraceId(ByteString.copyFrom(new byte[16]))
                    .setSpanId(ByteString.copyFrom(new byte[8]))
                    .build())
            .build();
    NumberDataPoint p2 =
        NumberDataPoint.newBuilder()
            .setStartTimeUnixNano(2_000_000L)
            .setTimeUnixNano(3_000_000L)
            .setAsDouble(4.2)
            .build();

    Sum sum =
        Sum.newBuilder()
            .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA)
            .addAllDataPoints(List.of(p1, p2))
            .build();

    Metric m = Metric.newBuilder().setName("requests").setSum(sum).build();
    ScopeMetrics sm = ScopeMetrics.newBuilder().addMetrics(m).build();
    ResourceMetrics rm =
        ResourceMetrics.newBuilder()
            .setResource(Resource.newBuilder().addAttributes(resTag).build())
            .addScopeMetrics(sm)
            .build();

    ExportMetricsServiceRequest req =
        ExportMetricsServiceRequest.newBuilder().addResourceMetrics(rm).build();

    OtelConverter c = new OtelConverter();
    List<ExportMetricsRequest> out = c.toOkapiRequests(req);

    assertEquals(1, out.size());
    ExportMetricsRequest r = out.get(0);
    assertEquals(MetricType.COUNTER, r.getType());
    assertEquals("requests", r.getMetricName());
    assertNotNull(r.getSum());
    assertEquals(SUM_TEMPORALITY.DELTA, r.getSum().getTemporality());
    assertEquals(2, r.getSum().getSumPoints().size());

    var sp1 = r.getSum().getSumPoints().get(0);
    assertEquals(1L, sp1.getStart());
    assertEquals(2L, sp1.getEnd());
    assertEquals(3d, sp1.getSum());

    var sp2 = r.getSum().getSumPoints().get(1);
    assertEquals(2L, sp2.getStart());
    assertEquals(3L, sp2.getEnd());
    assertEquals(4.2d, sp2.getSum());
    assertEquals(1, r.getSum().getExemplars().size());
    assertEquals(1_500_000L, r.getSum().getExemplars().get(0).getTsNanos());
  }
}
