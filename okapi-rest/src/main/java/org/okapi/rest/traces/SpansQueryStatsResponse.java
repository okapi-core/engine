/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import java.util.List;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SpansQueryStatsResponse {
  long count;
  List<AttributeNumericSeries> numericSeries;
  List<AttributeDistributionSummary> distributionSummaries;
}
