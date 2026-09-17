/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromQlMetadataItem {
  String type;
  String help;
  String unit;
}
