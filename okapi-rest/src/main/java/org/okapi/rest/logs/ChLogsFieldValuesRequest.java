/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.*;
import org.okapi.rest.common.UNION_TYPE;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
public class ChLogsFieldValuesRequest {
  Long tsStartNanos;
  Long tsEndNanos;

  @NotBlank(message = "field key is required")
  String key;

  @NotNull(message = "field type is required")
  UNION_TYPE type;

  String valuePrefix;
  @Valid List<ChLogFilter> filters;

  @Min(value = 1, message = "limit must be greater than zero")
  Integer limit;
}
