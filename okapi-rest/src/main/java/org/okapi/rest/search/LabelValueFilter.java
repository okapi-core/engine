/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.search;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@ToString
public class LabelValueFilter {
  @JsonPropertyDescription("The label to which this value must be assigned.")
  String label;

  @JsonPropertyDescription("Value that should be assigned to this label.")
  String value;
}
