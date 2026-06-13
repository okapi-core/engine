/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.primitives;

import java.util.List;

public class UnmodifiableLongListTest extends AbstractUnmodifiableListTest<Long> {

  @Override
  List<Long> newList(int size) {
    long[] arr = new long[size];
    for (int i = 0; i < size; i++) arr[i] = val(i);
    return new UnmodifiableLongList(arr);
  }

  @Override
  List<Long> newSubList(int totalSize, int start, int end) {
    long[] arr = new long[totalSize];
    for (int i = 0; i < totalSize; i++) arr[i] = val(i);
    return new UnmodifiableLongList(arr, start, end);
  }

  @Override
  List<Long> newListOf(Long v0, Long v1, Long v2, Long v3, Long v4) {
    return new UnmodifiableLongList(new long[]{v0, v1, v2, v3, v4});
  }

  @Override
  Long val(int i) {
    return (long) i;
  }

  @Override
  Long[] emptyArray(int size) {
    return new Long[size];
  }
}
