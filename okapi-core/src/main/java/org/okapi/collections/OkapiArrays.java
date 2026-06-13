package org.okapi.collections;

public class OkapiArrays {
  public static long[] fromIntegerToLong(int[] ints) {
    var asLongs = new long[ints.length];
    for (int i = 0; i < ints.length; i++) {
      asLongs[i] = ints[i];
    }
    return asLongs;
  }
}
