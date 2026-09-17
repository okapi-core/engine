/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import java.util.List;
import lombok.*;
import org.okapi.grammar.GRAMMAR;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PanelQueryConfig {
  private GRAMMAR grammar;
  private List<LabelledQuery> queryConfigs;
}
