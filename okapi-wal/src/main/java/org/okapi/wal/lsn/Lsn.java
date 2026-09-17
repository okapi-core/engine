/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.lsn;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Log sequence number used to order frames across segments. */
@Getter
@ToString
@EqualsAndHashCode
public class Lsn implements Comparable<Lsn> {
  public static final Lsn START = Lsn.fromNumber(0L);
  private final long number;

  public Lsn(long number) {
    if (number < 0) {
      throw new IllegalArgumentException("LSN values must be non-negative");
    }
    this.number = number;
  }

  public static Lsn fromNumber(long number) {
    return new Lsn(number);
  }

  @Override
  public int compareTo(Lsn other) {
    return Long.compare(number, other.number);
  }

  public static Lsn getStart() {
    return START;
  }

  public static Lsn max(Lsn a, Lsn b) {
    var cmp = a.compareTo(b);
    return cmp <= 0 ? b : a;
  }

  public static Lsn min(Lsn a, Lsn b) {
    var cmp = a.compareTo(b);
    return cmp <= 0 ? a : b;
  }

  public static Lsn zeroLsn() {
    return START;
  }
}
