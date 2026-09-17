/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.metrics.otel.DotToUnderscorePipeline;
import org.okapi.metrics.otel.RewritePostProcessor;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.MetricType;

public class RewritePostProcessorTest {

  @Test
  void rewritesMetricResourceAndTagKeys() {
    var req =
        ExportMetricsRequest.builder()
            .metricName("metric.name")
            .tags(Map.of("tag.one", "v1", "tag.two", "v2"))
            .type(MetricType.GAUGE)
            .build();

    var processor = new RewritePostProcessor(new DotToUnderscorePipeline());
    var out = processor.process(java.util.List.of(req));

    assertNotNull(out);
    assertEquals(1, out.size());
    var rewritten = out.get(0);
    assertEquals("metric_name", rewritten.getMetricName());
    assertEquals("v1", rewritten.getTags().get("tag_one"));
    assertEquals("v2", rewritten.getTags().get("tag_two"));
  }

  @Test
  void doesNotRetainOriginalDottedTagKeys() {
    var req =
        ExportMetricsRequest.builder()
            .metricName("metric")
            .tags(Map.of("service.instance.id", "i1", "env", "dev"))
            .type(MetricType.GAUGE)
            .build();

    var processor = new RewritePostProcessor(new DotToUnderscorePipeline());
    var out = processor.process(java.util.List.of(req));

    assertNotNull(out);
    var rewritten = out.get(0);
    assertEquals("i1", rewritten.getTags().get("service_instance_id"));
    assertEquals(false, rewritten.getTags().containsKey("service.instance.id"));
  }

  @Test
  void collapsesDottedAndUnderscoreKeysToSingleTag() {
    var tags = new java.util.LinkedHashMap<String, String>();
    tags.put("service.instance.id", "i1");
    tags.put("service_instance_id", "i2");

    var req =
        ExportMetricsRequest.builder()
            .metricName("metric")
            .tags(tags)
            .type(MetricType.GAUGE)
            .build();

    var processor = new RewritePostProcessor(new DotToUnderscorePipeline());
    var out = processor.process(java.util.List.of(req));

    assertNotNull(out);
    var rewritten = out.get(0);
    assertEquals(1, rewritten.getTags().size());
    assertEquals("i2", rewritten.getTags().get("service_instance_id"));
    assertEquals(false, rewritten.getTags().containsKey("service.instance.id"));
  }
}
