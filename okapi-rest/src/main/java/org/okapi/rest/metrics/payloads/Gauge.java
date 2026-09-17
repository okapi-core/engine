/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.payloads;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.okapi.rest.metrics.Exemplar;

@AllArgsConstructor
@Builder
@Getter
@ToString
public class Gauge {
  private List<Long> ts;
  private List<? extends Number> value;
  private List<Exemplar> exemplars;

  public Gauge(List<Long> ts, List<? extends Number> value) {
    this.ts = ts;
    this.value = value;
    this.exemplars = Collections.emptyList();
  }
}
