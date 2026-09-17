/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.okapi.promql.eval.VectorData.*;

public class TestFixtures {
  /** Builds a consistent minute-bucket dataset for tests. */
  public static CommonMocks buildCommonMocks() {
    var client = new MockTimeSeriesClient();

    long step = 60_000L;
    long t0 = 1_700_000_000_000L;
    long t1 = t0 + step;
    long t2 = t1 + step;
    long t3 = t2 + step;

    // http_requests_counter{job="api"}
    var httpTags = Map.of("job", "api");
    var httpSeries = new SeriesId("http_requests_counter", new Labels(httpTags));
    // Per-bucket event counts (not cumulative): 60, 120, 180, 240
    client.put("http_requests_counter", httpTags, t0, 60f);
    client.put("http_requests_counter", httpTags, t1, 120f);
    client.put("http_requests_counter", httpTags, t2, 180f);
    client.put("http_requests_counter", httpTags, t3, 240f);

    // cpu_usage{job="api",instance="i1"} — gauge
    var cpuI1Tags = Map.of("job", "api", "instance", "i1");
    var cpuI1 = new SeriesId("cpu_usage", new Labels(cpuI1Tags));
    client.put("cpu_usage", cpuI1Tags, t0, 10f);
    client.put("cpu_usage", cpuI1Tags, t1, 20f);
    client.put("cpu_usage", cpuI1Tags, t2, 30f);
    client.put("cpu_usage", cpuI1Tags, t3, 40f);

    // cpu_usage{job="api",instance="i2"} — gauge
    var cpuI2Tags = Map.of("job", "api", "instance", "i2");
    var cpuI2 = new SeriesId("cpu_usage", new Labels(cpuI2Tags));
    client.put("cpu_usage", cpuI2Tags, t0, 40f);
    client.put("cpu_usage", cpuI2Tags, t1, 60f);
    client.put("cpu_usage", cpuI2Tags, t2, 80f);
    client.put("cpu_usage", cpuI2Tags, t3, 100f);

    // mem_usage present only for i1
    var memI1Tags = Map.of("job", "api", "instance", "i1");
    var memI1 = new SeriesId("mem_usage", new Labels(memI1Tags));
    client.put("mem_usage", memI1Tags, t0, 50f);
    client.put("mem_usage", memI1Tags, t1, 60f);
    client.put("mem_usage", memI1Tags, t2, 70f);
    client.put("mem_usage", memI1Tags, t3, 80f);

    // pod_replicas per job (single series)
    var repTags = Map.of("job", "api");
    var replicas = new SeriesId("pod_replicas", new Labels(repTags));
    client.put("pod_replicas", repTags, t0, 3f);
    client.put("pod_replicas", repTags, t1, 3f);
    client.put("pod_replicas", repTags, t2, 3f);
    client.put("pod_replicas", repTags, t3, 3f);

    // Discovery knows about all three series
    var discovery = new MockSeriesDiscovery(List.of(httpSeries, cpuI1, cpuI2, memI1, replicas));

    return new CommonMocks(
        client,
        discovery,
        t0,
        t1,
        t2,
        t3,
        step,
        httpSeries,
        httpTags,
        cpuI1,
        cpuI1Tags,
        cpuI2,
        cpuI2Tags,
        memI1,
        memI1Tags,
        replicas,
        repTags);
  }

  @AllArgsConstructor
  public static final class CommonMocks {
    public final MockTimeSeriesClient client;
    public final MockSeriesDiscovery discovery;

    // Time anchors (1-minute step)
    public final long t0, t1, t2, t3;
    public final long step;

    // Series
    public final SeriesId httpRequestsCounterApi;
    public final Map<String, String> httpRequestsCounterApiTags;

    // cpu usage
    public final SeriesId cpuUsageApiI1;
    public final Map<String, String> cpuUsageApiI1Tags;

    public final SeriesId cpuUsageApiI2;
    public final Map<String, String> cpuUsageApiI2Tags;

    // memory usage
    public final SeriesId memI1;
    public final Map<String, String> memI1Tags;

    // replicas
    public final SeriesId replicas;
    public final Map<String, String> repTags;
  }
}
