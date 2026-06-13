/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.primitives;


import org.jetbrains.annotations.NotNull;
import java.util.Collection;
import java.util.List;

public abstract class AbstractUnmodifiableList<T> implements List<T> {
  @Override
  public boolean add(T t) {
    throw new UnsupportedOperationException("This list is unmodifiable");
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException("This list is unmodifiable");
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    return false;
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends T> c) {
    throw new UnsupportedOperationException("This list is unmodifiable");
  }

  @Override
  public boolean addAll(int index, @NotNull Collection<? extends T> c) {
    throw new UnsupportedOperationException("This list is unmodifiable");
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException("This list is unmodifiable");
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException("This list is unmodifiable");
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException("This list is unmodifiable");
  }

  @Override
  public T set(int index, T element) {
    throw new UnsupportedOperationException("This list is unmodifiable");
  }

  @Override
  public void add(int index, T element) {
    throw new UnsupportedOperationException("This list is unmodifiable");
  }

  @Override
  public T remove(int index) {
    throw new UnsupportedOperationException("This list is unmodifiable");
  }
}
