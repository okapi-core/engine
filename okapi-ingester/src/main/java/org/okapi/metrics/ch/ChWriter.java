/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.data.ClickHouseFormat;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import org.okapi.futures.OkapiFutures;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;

public class ChWriter {
  private final Client client;
  private final Gson gson = new Gson();
  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

  public ChWriter(Client client) {
    this.client = client;
  }

  public String toJsonEachRow(Collection<String> rows) {
    var sb = new StringBuilder();
    for (var r : rows) {
      sb.append(r).append('\n');
    }
    return sb.toString();
  }

  public Future<InsertResponse> writeRows(String table, Collection<String> jsonRows) {
    var rows = toJsonEachRow(jsonRows).getBytes();
    var bis = new ByteArrayInputStream(rows);
    return client.insert(table, bis, ClickHouseFormat.JSONEachRow);
  }

  public List<Future<InsertResponse>> writeAll(Multimap<String, String> writeLoad) {
    var futures = new ArrayList<Future<InsertResponse>>();
    for (var k : writeLoad.keySet()) {
      var vals = writeLoad.get(k);
      futures.add(writeRows(k, vals));
    }
    return futures;
  }

  public void writeSyncWithBestEffort(Multimap<String, String> writeLoad) {
    var futures = writeAll(writeLoad);
    OkapiFutures.fireAndForgetWait(futures);
  }

  public Future<InsertResponse> writeHistoSamplesBinary(List<ChHistoSample> rows) {
    var jsonRows =
        rows.stream()
            .map(
                r -> {
                  var map = new HashMap<String, Object>();
                  map.put("metric_name", r.getMetric());
                  map.put("tags", r.getTags());
                  map.put("ts_start", TS_FMT.format(Instant.ofEpochMilli(r.getTsStart())));
                  map.put("ts_end", TS_FMT.format(Instant.ofEpochMilli(r.getTsEnd())));
                  map.put("buckets", r.getBuckets());
                  map.put("counts", r.getCounts());
                  map.put("sum", r.getSum());
                  map.put("count", r.getCount() != null ? r.getCount() : totalCount(r.getCounts()));
                  map.put("histo_type", r.getHistoType());
                  return gson.toJson(map);
                })
            .toList();
    var data = toJsonEachRow(jsonRows).getBytes();
    var bis = new ByteArrayInputStream(data);
    return client.insert(ChConstants.TBL_HISTOS, bis, ClickHouseFormat.JSONEachRow);
  }

  private static long totalCount(long[] counts) {
    long total = 0L;
    if (counts != null) {
      for (long count : counts) total += count;
    }
    return total;
  }
}
