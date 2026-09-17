/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clickhouse.client.api.query.GenericRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.engine.ch.ChQueryModel.ColumnExpr;
import org.okapi.engine.ch.ChQueryModel.ScalarType;
import org.okapi.engine.ch.ChQueryModel.SelectItem;
import org.okapi.rest.common.UNION_TYPE;
import org.okapi.rest.common.UnionValue;

class ChGenericRecordUnionValueConverterTests {
  private final ChGenericRecordUnionValueConverter converter =
      new ChGenericRecordUnionValueConverter();

  @Test
  void convertsSelectedAliasesToUnionValues() {
    var record = mock(GenericRecord.class);
    when(record.hasValue("service")).thenReturn(true);
    when(record.getString("service")).thenReturn("checkout");
    when(record.hasValue("duration")).thenReturn(true);
    when(record.getLong("duration")).thenReturn(123_456_789L);
    when(record.hasValue("status_code")).thenReturn(true);
    when(record.getInteger("status_code")).thenReturn(200);
    when(record.hasValue("ratio")).thenReturn(true);
    when(record.getDouble("ratio")).thenReturn(0.75d);
    when(record.hasValue("error")).thenReturn(true);
    when(record.getBoolean("error")).thenReturn(false);

    var row =
        converter.convert(
            record,
            List.of(
                item("service", ScalarType.STRING),
                item("duration", ScalarType.LONG),
                item("status_code", ScalarType.INTEGER),
                item("ratio", ScalarType.DOUBLE),
                item("error", ScalarType.BOOLEAN),
                item("", ScalarType.STRING)));

    assertEquals(5, row.size());
    assertEquals(UNION_TYPE.STRING, row.get("service").getType());
    assertEquals("checkout", row.get("service").getStringValue());
    assertEquals(UNION_TYPE.LONG, row.get("duration").getType());
    assertEquals(123_456_789L, row.get("duration").getLongValue());
    assertEquals(UNION_TYPE.INTEGER, row.get("status_code").getType());
    assertEquals(200, row.get("status_code").getIntegerValue());
    assertEquals(UNION_TYPE.DOUBLE, row.get("ratio").getType());
    assertEquals(0.75d, row.get("ratio").getDoubleValue());
    assertEquals(UNION_TYPE.BOOLEAN, row.get("error").getType());
    assertFalse(row.get("error").getBooleanValue());
  }

  @Test
  void usesCachedEmptyValueWhenAliasIsMissing() {
    var record = mock(GenericRecord.class);
    when(record.hasValue("missing")).thenReturn(false);

    var row = converter.convert(record, List.of(item("missing", ScalarType.STRING)));

    assertSame(UnionValue.emptyValue(), row.get("missing"));
  }

  private SelectItem item(String alias, ScalarType type) {
    return new SelectItem(new ColumnExpr(alias, type), alias);
  }
}
