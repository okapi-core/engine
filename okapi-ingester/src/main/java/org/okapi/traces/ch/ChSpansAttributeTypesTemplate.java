/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChSpansAttributeTypesTemplate {
  List<String> attributes;
}
