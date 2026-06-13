/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.primitives;

import java.util.List;

public class UnmodifiableIntegerListTest extends AbstractUnmodifiableListTest<Integer> {

  @Override
  List<Integer> newList(int size) {
    int[] arr = new int[size];
    for (int i = 0; i < size; i++) arr[i] = val(i);
    return new UnmodifiableIntegerList(arr);
  }

  @Override
  List<Integer> newSubList(int totalSize, int start, int end) {
    int[] arr = new int[totalSize];
    for (int i = 0; i < totalSize; i++) arr[i] = val(i);
    return new UnmodifiableIntegerList(arr, start, end);
  }

  @Override
  List<Integer> newListOf(Integer v0, Integer v1, Integer v2, Integer v3, Integer v4) {
    return new UnmodifiableIntegerList(new int[]{v0, v1, v2, v3, v4});
  }

  @Override
  Integer val(int i) {
    return i;
  }

  @Override
  Integer[] emptyArray(int size) {
    return new Integer[size];
  }
}
