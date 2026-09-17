/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.testing;

import java.util.List;
import lombok.NonNull;
import lombok.Value;

public final class OkapiQlTestAst {
  private OkapiQlTestAst() {}

  public sealed interface TestCase permits ParseCase, FailCase {
    String getName();

    String getQuery();
  }

  @Value
  public static class TestFile {
    @NonNull List<TestCase> cases;
  }

  @Value
  public static class ParseCase implements TestCase {
    @NonNull String name;
    @NonNull String query;
  }

  @Value
  public static class FailCase implements TestCase {
    @NonNull String name;
    @NonNull String query;
  }
}
