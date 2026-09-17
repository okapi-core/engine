/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ch;

import com.clickhouse.client.api.query.GenericRecord;
import java.util.Map;
import java.util.function.Function;

public class GenericRecordReader {
  public static <T, V> void applyMappers(
      GenericRecord record, Function<String, T> extractor, Map<String, Function<T, V>> mappers) {
    for (var mapper : mappers.entrySet()) {
      var attrib = mapper.getKey();
      if (record.hasValue(attrib)) {
        mapper.getValue().apply(extractor.apply(attrib));
      }
    }
  }

  public static Map<String, String> getMap(String key, Map<String, Object> vals) {
    var valInMap = vals.get(key);
    if (valInMap instanceof Map) {
      return (Map<String, String>) valInMap;
    } else return null;
  }
}
