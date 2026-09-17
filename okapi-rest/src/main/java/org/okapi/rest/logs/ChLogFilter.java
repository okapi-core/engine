/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.okapi.rest.common.UnionValue;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
@JsonClassDescription("Filter applied to a log search.")
public class ChLogFilter {
  @JsonPropertyDescription(
      "Field key. Core keys include service.name, log.stream, severity.number, severity.text, trace.id, span.id, body, and ts.")
  @NotBlank(message = "filter key is required")
  String key;

  @JsonPropertyDescription("Filter operation.")
  @NotNull(message = "filter operation is required")
  ChLogFilterOp op;

  @JsonPropertyDescription(
      "Typed comparison value. Not required for EXISTS and NOT_EXISTS filters.")
  UnionValue value;

  @AssertTrue(message = "filter value is required unless operation is EXISTS or NOT_EXISTS")
  public boolean isValueValidForOperation() {
    return op == ChLogFilterOp.EXISTS || op == ChLogFilterOp.NOT_EXISTS || value != null;
  }
}
