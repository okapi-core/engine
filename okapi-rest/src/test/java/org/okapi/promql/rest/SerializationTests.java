/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.rest.promql.*;

public class SerializationTests {

  @Test
  public void testPromQlSerialization() {
    var result = new PromQlVectorResponse();
    var series = new VectorSeries();
    var now = System.currentTimeMillis() / 1000;
    var data = new PromQlVectorData();
    series.setMetric(Map.of("__name__", "new-metric", "service", "api"));
    series.setValue(new Sample(now, Float.toString(0.1f)));
    data.setResult(List.of(series));
    result.setData(data);
    result.setStatus("success");
    Gson gson = new GsonBuilder().registerTypeAdapter(Sample.class, new SampleAdapter()).create();
    var asJson = gson.toJson(result);
    var json = JsonParser.parseString(asJson).getAsJsonObject();
    assertEquals("success", json.get("status").getAsString());
    var resultData = json.getAsJsonObject("data");
    assertEquals("vector", resultData.get("resultType").getAsString());
    var resultSeries = resultData.getAsJsonArray("result").get(0).getAsJsonObject();
    assertEquals(
        "new-metric", resultSeries.getAsJsonObject("metric").get("__name__").getAsString());
    assertEquals("api", resultSeries.getAsJsonObject("metric").get("service").getAsString());
    assertEquals(Float.toString(0.1f), resultSeries.getAsJsonArray("value").get(1).getAsString());
  }
}
