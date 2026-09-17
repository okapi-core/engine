/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
public class DashboardVariable {
  public enum Type {
    METRIC,
    TAG
  }

  private String varName;
  private String tag;
  private Type varType;
}
