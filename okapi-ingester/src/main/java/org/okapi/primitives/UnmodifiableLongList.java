/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.primitives;

import org.jetbrains.annotations.NotNull;
import org.springframework.lang.NonNull;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class UnmodifiableLongList extends AbstractUnmodifiableList<Long> {

  long[] array;
  int start;
  int end;

  public UnmodifiableLongList(long[] array) {
    this.array = array;
    this.start = 0;
    this.end = array.length;
  }

  public UnmodifiableLongList(long[] array, int start, int end) {
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
  public Iterator<Long> iterator() {
    return new Iterator<>() {
      private int cursor = 0;

      @Override
      public boolean hasNext() {
        return cursor < size();
      }

      @Override
      public Long next() {
        if (!hasNext()) {
          throw new IndexOutOfBoundsException();
        }
        return get(cursor++);
      }
    };
  }

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
  @NonNull
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
  public Long get(int index) {
    if (index < 0 || index >= size()) {
      throw new IndexOutOfBoundsException();
    }
    return array[start + index];
  }

  @Override
  public int indexOf(Object o) {
    if (!(o instanceof Long)) {
      return -1;
    }
    for (int i = start; i < end; i++) {
      if (array[i] == (Long) o) {
        return i - start;
      }
    }
    return -1;
  }

  @Override
  public int lastIndexOf(Object o) {
    if (!(o instanceof Long)) {
      return -1;
    }
    for (int i = end - 1; i >= start; i--) {
      if (array[i] == (Long) o) {
        return i - start;
      }
    }
    return -1;
  }

  @NotNull
  @Override
  public ListIterator<Long> listIterator() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public ListIterator<Long> listIterator(int index) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<Long> subList(int fromIndex, int toIndex) {
    if (fromIndex < 0 || toIndex > size() || fromIndex > toIndex) {
      throw new IndexOutOfBoundsException();
    }
    return new UnmodifiableLongList(array, start + fromIndex, start + toIndex);
  }
}
