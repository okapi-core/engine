/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class UnionValue {
  UNION_TYPE type;
  String stringValue;
  Double doubleValue;
  Integer integerValue;
  Long longValue;
  Boolean booleanValue;

  private static final UnionValue EMPTY = UnionValue.builder().build();

  public static UnionValue emptyValue() {
    return EMPTY;
  }

  public static UnionValue stringValue(String value) {
    return UnionValue.builder().type(UNION_TYPE.STRING).stringValue(value).build();
  }

  public static UnionValue doubleValue(Double value) {
    return UnionValue.builder().type(UNION_TYPE.DOUBLE).doubleValue(value).build();
  }

  public static UnionValue integerValue(Integer value) {
    return UnionValue.builder().type(UNION_TYPE.INTEGER).integerValue(value).build();
  }

  public static UnionValue longValue(Long value) {
    return UnionValue.builder().type(UNION_TYPE.LONG).longValue(value).build();
  }

  public static UnionValue booleanValue(Boolean value) {
    return UnionValue.builder().type(UNION_TYPE.BOOLEAN).booleanValue(value).build();
  }
}
