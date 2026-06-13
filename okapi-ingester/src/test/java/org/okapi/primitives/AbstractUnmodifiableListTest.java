/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.primitives;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

abstract class AbstractUnmodifiableListTest<E extends Number> {

  /** Creates a list [val(0), val(1), ..., val(size-1)]. */
  abstract List<E> newList(int size);

  /** Creates a list backed by [val(0)..val(totalSize-1)], viewed as [start, end). */
  abstract List<E> newSubList(int totalSize, int start, int end);

  /** Creates a 5-element list with the given values (for duplicate-value tests). */
  abstract List<E> newListOf(E v0, E v1, E v2, E v3, E v4);

  /** Returns a canonical test value for index i. */
  abstract E val(int i);

  /** Returns an empty boxed array of the given size. */
  abstract E[] emptyArray(int size);

  // --- size / isEmpty ---

  @Test
  void size_emptyList() {
    assertEquals(0, newList(0).size());
  }

  @Test
  void size_nonEmptyList() {
    assertEquals(5, newList(5).size());
  }

  @Test
  void size_subList() {
    assertEquals(3, newSubList(5, 1, 4).size());
  }

  @Test
  void isEmpty_emptyList() {
    assertTrue(newList(0).isEmpty());
  }

  @Test
  void isEmpty_nonEmptyList() {
    assertFalse(newList(3).isEmpty());
  }

  // --- get ---

  @Test
  void get_firstElement() {
    assertEquals(val(0), newList(3).get(0));
  }

  @Test
  void get_lastElement() {
    assertEquals(val(4), newList(5).get(4));
  }

  @Test
  void get_negativeIndex_throws() {
    assertThrows(IndexOutOfBoundsException.class, () -> newList(3).get(-1));
  }

  @Test
  void get_indexEqualToSize_throws() {
    assertThrows(IndexOutOfBoundsException.class, () -> newList(3).get(3));
  }

  @Test
  void get_subList_indexZeroMapsToStart() {
    var sub = newSubList(5, 2, 5);
    assertEquals(val(2), sub.get(0));
    assertEquals(val(4), sub.get(2));
  }

  // --- contains / indexOf / lastIndexOf ---

  @Test
  void contains_presentElement_returnsTrue() {
    assertTrue(newList(5).contains(val(3)));
  }

  @Test
  void contains_absentElement_returnsFalse() {
    assertFalse(newList(3).contains(val(9)));
  }

  @Test
  void contains_wrongType_returnsFalse() {
    assertFalse(newList(3).contains("notANumber"));
  }

  @Test
  void indexOf_presentElement_returnsCorrectIndex() {
    assertEquals(2, newList(5).indexOf(val(2)));
  }

  @Test
  void indexOf_absentElement_returnsMinusOne() {
    assertEquals(-1, newList(3).indexOf(val(9)));
  }

  @Test
  void indexOf_wrongType_returnsMinusOne() {
    assertEquals(-1, newList(3).indexOf("notANumber"));
  }

  @Test
  void indexOf_duplicates_returnsFirst() {
    var list = newListOf(val(1), val(2), val(1), val(3), val(1));
    assertEquals(0, list.indexOf(val(1)));
  }

  @Test
  void lastIndexOf_presentElement_returnsCorrectIndex() {
    assertEquals(2, newList(5).lastIndexOf(val(2)));
  }

  @Test
  void lastIndexOf_absentElement_returnsMinusOne() {
    assertEquals(-1, newList(3).lastIndexOf(val(9)));
  }

  @Test
  void lastIndexOf_wrongType_returnsMinusOne() {
    assertEquals(-1, newList(3).lastIndexOf("notANumber"));
  }

  @Test
  void lastIndexOf_duplicates_returnsLast() {
    var list = newListOf(val(1), val(2), val(1), val(3), val(1));
    assertEquals(4, list.lastIndexOf(val(1)));
  }

  // --- iterator ---

  @Test
  void iterator_traversesAllElementsInOrder() {
    var list = newList(3);
    var iter = list.iterator();
    for (int i = 0; i < 3; i++) {
      assertTrue(iter.hasNext());
      assertEquals(val(i), iter.next());
    }
    assertFalse(iter.hasNext());
  }

  @Test
  void iterator_nextOnExhausted_throws() {
    var iter = newList(1).iterator();
    iter.next();
    assertThrows(IndexOutOfBoundsException.class, iter::next);
  }

  // --- toArray() ---

  @Test
  void toArray_noArg_returnsCorrectValues() {
    var list = newList(3);
    var arr = list.toArray();
    assertEquals(3, arr.length);
    for (int i = 0; i < 3; i++) {
      assertEquals(val(i), arr[i]);
    }
  }

  // --- toArray(T[] a) ---

  @Test
  void toArray_exactSize_fillsAndReturnsSameArray() {
    var list = newList(3);
    E[] a = emptyArray(3);
    E[] result = list.toArray(a);
    assertSame(a, result);
    for (int i = 0; i < 3; i++) {
      assertEquals(val(i), result[i]);
    }
  }

  @Test
  void toArray_largerArray_setsNullTerminatorAndReturnsSameArray() {
    var list = newList(3);
    E[] a = emptyArray(5);
    E[] result = list.toArray(a);
    assertSame(a, result);
    for (int i = 0; i < 3; i++) {
      assertEquals(val(i), result[i]);
    }
    assertNull(result[3]);
  }

  @Test
  void toArray_tooSmallArray_throws() {
    assertThrows(IllegalArgumentException.class, () -> newList(5).toArray(emptyArray(3)));
  }

  // --- subList ---

  @Test
  void subList_validRange_correctSizeAndValues() {
    var sub = newList(5).subList(1, 4);
    assertEquals(3, sub.size());
    assertEquals(val(1), sub.get(0));
    assertEquals(val(3), sub.get(2));
  }

  @Test
  void subList_fullRange_matchesOriginal() {
    var list = newList(3);
    var sub = list.subList(0, 3);
    assertEquals(list.size(), sub.size());
    for (int i = 0; i < 3; i++) {
      assertEquals(list.get(i), sub.get(i));
    }
  }

  @Test
  void subList_emptyRange_sizeIsZero() {
    assertEquals(0, newList(3).subList(1, 1).size());
  }

  @Test
  void subList_negativeFromIndex_throws() {
    assertThrows(IndexOutOfBoundsException.class, () -> newList(3).subList(-1, 2));
  }

  @Test
  void subList_toIndexBeyondSize_throws() {
    assertThrows(IndexOutOfBoundsException.class, () -> newList(3).subList(0, 4));
  }

  @Test
  void subList_fromIndexGreaterThanToIndex_throws() {
    assertThrows(IndexOutOfBoundsException.class, () -> newList(3).subList(2, 1));
  }

  // --- mutation methods ---

  @Test
  void add_throws() {
    assertThrows(UnsupportedOperationException.class, () -> newList(3).add(val(0)));
  }

  @Test
  void remove_throws() {
    assertThrows(UnsupportedOperationException.class, () -> newList(3).remove(val(0)));
  }

  @Test
  void set_throws() {
    assertThrows(UnsupportedOperationException.class, () -> newList(3).set(0, val(0)));
  }

  @Test
  void clear_throws() {
    assertThrows(UnsupportedOperationException.class, () -> newList(3).clear());
  }

  // --- listIterator ---

  @Test
  void listIterator_throws() {
    assertThrows(UnsupportedOperationException.class, () -> newList(3).listIterator());
  }

  @Test
  void listIterator_withIndex_throws() {
    assertThrows(UnsupportedOperationException.class, () -> newList(3).listIterator(0));
  }
}
