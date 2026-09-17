/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.payloads;

import java.util.Collections;
import java.util.List;
import lombok.*;
import org.okapi.rest.metrics.Exemplar;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@Builder
public class Histo {
  List<HistoPoint> histoPoints;
  List<Exemplar> exemplars;

  public Histo(List<HistoPoint> histoPoints) {
    this.histoPoints = histoPoints;
    this.exemplars = Collections.emptyList();
  }
}
