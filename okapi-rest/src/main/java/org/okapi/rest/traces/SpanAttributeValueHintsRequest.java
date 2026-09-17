/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SpanAttributeValueHintsRequest {
  TimestampMillisFilter timestampFilter;
  String attributeName;
}
