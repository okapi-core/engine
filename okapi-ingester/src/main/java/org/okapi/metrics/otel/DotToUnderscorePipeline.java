/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.otel;

public class DotToUnderscorePipeline implements RewritePipeline {
  @Override
  public String rewrite(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.replace('.', '_');
  }
}
