package org.okapi.nullity;

public class NullHandler {
  public static <T> T ifNullThen(T obj, T default_) {
    if (obj == null) {
      return default_;
    } else return obj;
  }

  public static String ifNullThenEmpty(String s) {
    return (s == null) ? "" : s;
  }
}
