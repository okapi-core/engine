/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SystemState {
  String name;
  double weight;
  @Builder.Default Map<String, ComponentState> components = Map.of();

  public ComponentState componentState(String component, ComponentState fallback) {
    return components.getOrDefault(component, fallback);
  }

  public static SystemState defaultState() {
    return SystemState.builder().name("default").weight(1.0).components(Map.of()).build();
  }
}
