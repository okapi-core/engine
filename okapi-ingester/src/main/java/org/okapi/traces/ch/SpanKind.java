/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

public enum SpanKind {
  CLIENT,
  SERVER,
  PRODUCER,
  CONSUMER,
  UNK,
  INTERNAL
}
