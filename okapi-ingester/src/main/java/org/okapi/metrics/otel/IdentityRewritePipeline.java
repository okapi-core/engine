/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.otel;

public class IdentityRewritePipeline implements RewritePipeline {
  @Override
  public String rewrite(String s) {
    return s;
  }
}
