/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.primitives;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import org.jetbrains.annotations.NotNull;

public class UnmodifiableIntegerList extends AbstractUnmodifiableList<Integer> {

  int[] array;
  int start;
  int end;

  public UnmodifiableIntegerList(int[] array) {
    this.array = array;
    this.start = 0;
    this.end = array.length;
  }

  public UnmodifiableIntegerList(int[] array, int start, int end) {
    this.array = array;
    this.start = start;
    this.end = end;
  }

  @Override
  public int size() {
    return end - start;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean contains(Object o) {
    return indexOf(o) != -1;
  }

  @NotNull
  @Override
  public Iterator<Integer> iterator() {
    return new Iterator<Integer>() {
      private int cursor = 0;

      @Override
      public boolean hasNext() {
        return cursor < size();
      }

      @Override
      public Integer next() {
        if (!hasNext()) {
          throw new IndexOutOfBoundsException();
        }
        return get(cursor++);
      }
    };
  }

  @NotNull
  @Override
  public Object[] toArray() {
    int n = size();
    Object[] out = new Object[n];
    for (int i = 0; i < n; i++) {
      out[i] = get(i);
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    int size = size();
    if (a.length < size) {
      throw new IllegalArgumentException("Array too small: " + a.length + " < " + size);
    }
    for (int i = 0; i < size; i++) {
      a[i] = (T) get(i);
    }
    if (a.length > size) {
      a[size] = null;
    }
    return a;
  }

  @Override
  public Integer get(int index) {
    if (index < 0 || index >= size()) {
      throw new IndexOutOfBoundsException();
    }
    return array[start + index];
  }

  @Override
  public int indexOf(Object o) {
    if (!(o instanceof Integer)) {
      return -1;
    }
    for (int i = start; i < end; i++) {
      if (array[i] == (Integer) o) {
        return i - start;
      }
    }
    return -1;
  }

  @Override
  public int lastIndexOf(Object o) {
    if (!(o instanceof Integer)) {
      return -1;
    }
    for (int i = end - 1; i >= start; i--) {
      if (array[i] == (Integer) o) {
        return i - start;
      }
    }
    return -1;
  }

  @NotNull
  @Override
  public ListIterator<Integer> listIterator() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public ListIterator<Integer> listIterator(int index) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<Integer> subList(int fromIndex, int toIndex) {
    if (fromIndex < 0 || toIndex > size() || fromIndex > toIndex) {
      throw new IndexOutOfBoundsException();
    }
    return new org.okapi.primitives.UnmodifiableIntegerList(
        array, start + fromIndex, start + toIndex);
  }
}
