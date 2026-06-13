/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.primitives;

import java.util.List;

public class UnmodifiableDoubleListTest extends AbstractUnmodifiableListTest<Float> {

  @Override
  List<Float> newList(int size) {
    float[] arr = new float[size];
    for (int i = 0; i < size; i++) arr[i] = val(i);
    return new UnmodifiableDoubleList(arr);
  }

  @Override
  List<Float> newSubList(int totalSize, int start, int end) {
    float[] arr = new float[totalSize];
    for (int i = 0; i < totalSize; i++) arr[i] = val(i);
    return new UnmodifiableDoubleList(arr, start, end);
  }

  @Override
  List<Float> newListOf(Float v0, Float v1, Float v2, Float v3, Float v4) {
    return new UnmodifiableDoubleList(new float[]{v0, v1, v2, v3, v4});
  }

  @Override
  Float val(int i) {
    return (float) i;
  }

  @Override
  Float[] emptyArray(int size) {
    return new Float[size];
  }
}
