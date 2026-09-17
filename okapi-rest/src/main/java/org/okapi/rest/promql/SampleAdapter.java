/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.*;
import java.lang.reflect.Type;

public class SampleAdapter implements JsonSerializer<Sample>, JsonDeserializer<Sample> {
  @Override
  public JsonElement serialize(Sample src, Type typeOfSrc, JsonSerializationContext ctx) {
    JsonArray arr = new JsonArray(2);
    arr.add(src.getTimestamp());
    arr.add(src.getValue());
    return arr;
  }

  @Override
  public Sample deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx)
      throws JsonParseException {
    JsonArray arr = json.getAsJsonArray();
    double ts = arr.get(0).getAsDouble();
    String v = arr.get(1).getAsString();
    return new Sample(ts, v);
  }
}
