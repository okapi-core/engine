/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.pojos.results;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Builder
public class GaugeScan extends Scan {

  @Getter public final String universalPath;

  @Getter public final List<Long> timestamps;

  @Getter public final List<Float> values;
}
