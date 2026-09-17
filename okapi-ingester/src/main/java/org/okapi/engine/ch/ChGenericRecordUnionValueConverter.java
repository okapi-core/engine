/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import com.clickhouse.client.api.query.GenericRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.okapi.engine.ch.ChQueryModel.ScalarType;
import org.okapi.engine.ch.ChQueryModel.SelectItem;
import org.okapi.rest.common.UNION_TYPE;
import org.okapi.rest.common.UnionValue;
import org.springframework.stereotype.Component;

@Component
public class ChGenericRecordUnionValueConverter {

  public Map<String, UnionValue> convert(GenericRecord record, List<SelectItem> selectItems) {
    var row = new LinkedHashMap<String, UnionValue>();
    for (var item : selectItems) {
      var alias = item.getAlias();
      if (alias == null || alias.isBlank()) {
        continue;
      }
      row.put(alias, unionValue(record, alias, item.getExpr().getType()));
    }
    return row;
  }

  private UnionValue unionValue(GenericRecord record, String alias, ScalarType type) {
    if (!record.hasValue(alias)) {
      return UnionValue.emptyValue();
    }
    return switch (type) {
      case STRING ->
          UnionValue.builder().type(UNION_TYPE.STRING).stringValue(record.getString(alias)).build();
      case BOOLEAN ->
          UnionValue.builder()
              .type(UNION_TYPE.BOOLEAN)
              .booleanValue(record.getBoolean(alias))
              .build();
      case INTEGER ->
          UnionValue.builder()
              .type(UNION_TYPE.INTEGER)
              .integerValue(record.getInteger(alias))
              .build();
      case LONG ->
          UnionValue.builder().type(UNION_TYPE.LONG).longValue(record.getLong(alias)).build();
      case DOUBLE, NUMBER ->
          UnionValue.builder().type(UNION_TYPE.DOUBLE).doubleValue(record.getDouble(alias)).build();
    };
  }
}
