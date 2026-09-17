/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.okapi.rest.metrics.payloads.Gauge;

public class ExportMetricsRequestTest {

  private static Map<String, String> unsortedTags() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("b", "2");
    m.put("a", "1");
    m.put("c", "3");
    return m;
  }

  private static List<String> keys(Map<String, String> m) {
    return new ArrayList<>(m.keySet());
  }

  @Test
  public void builderSortsTags() {
    ExportMetricsRequest r =
        ExportMetricsRequest.builder()
            .metricName("m")
            .type(MetricType.GAUGE)
            .tags(unsortedTags())
            .gauge(Gauge.builder().ts(List.of(1L)).value(List.of(1.0f)).build())
            .build();

    assertNotNull(r.getTags());
    assertTrue(r.getTags() instanceof TreeMap, "tags should be a TreeMap");
    assertEquals(List.of("a", "b", "c"), keys(r.getTags()));
  }

  @Test
  public void setterSortsTags() {
    ExportMetricsRequest r = new ExportMetricsRequest();
    r.setMetricName("m");
    r.setType(MetricType.GAUGE);
    r.setTags(unsortedTags());

    assertNotNull(r.getTags());
    assertTrue(r.getTags() instanceof TreeMap);
    assertEquals(List.of("a", "b", "c"), keys(r.getTags()));
  }

  @Test
  public void allArgsConstructorSortsTags() {
    ExportMetricsRequest r =
        new ExportMetricsRequest("m", unsortedTags(), MetricType.GAUGE, null, null, null);
    assertNotNull(r.getTags());
    assertTrue(r.getTags() instanceof TreeMap);
    assertEquals(List.of("a", "b", "c"), keys(r.getTags()));
  }

  @Test
  public void jsonDeserializationUsesSortedTags() throws Exception {
    String json =
        "{"
            + "\"metricName\":\"m\","
            + "\"type\":\"GAUGE\","
            + "\"tags\":{\"b\":\"2\",\"a\":\"1\",\"c\":\"3\"}"
            + "}";
    ObjectMapper mapper = new ObjectMapper();
    ExportMetricsRequest r = mapper.readValue(json, ExportMetricsRequest.class);

    assertNotNull(r.getTags());
    assertTrue(r.getTags() instanceof TreeMap);
    assertEquals(List.of("a", "b", "c"), keys(r.getTags()));
  }
}
