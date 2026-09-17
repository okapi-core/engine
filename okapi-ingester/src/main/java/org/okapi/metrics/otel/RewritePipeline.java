/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.otel;

public interface RewritePipeline {
  String rewrite(String s);
}
