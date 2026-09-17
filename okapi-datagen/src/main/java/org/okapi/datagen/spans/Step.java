/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span.SpanKind;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class Step {
  String spanName;
  String component;
  @Builder.Default SpanKind kind = SpanKind.SPAN_KIND_INTERNAL;
  @Singular List<KeyValue> attributes;
  @Singular List<Step> children;
}
