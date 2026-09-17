/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.otel;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@NoArgsConstructor
@Validated
@Component
@ConfigurationProperties(prefix = "okapi.metrics.otel.converter")
public class OtelConverterConfig {

  private Set<String> excludeTags = new LinkedHashSet<>();
  private Set<String> excludeTagPrefixes = new LinkedHashSet<>();

  @PostConstruct
  public void validate() {
    excludeTags = normalize("excludeTags", excludeTags);
    excludeTagPrefixes = normalize("excludeTagPrefixes", excludeTagPrefixes);
  }

  public static OtelConverterConfig defaultConfig() {
    OtelConverterConfig cfg = new OtelConverterConfig();
    cfg.validate();
    return cfg;
  }

  private static Set<String> normalize(String fieldName, Set<String> values) {
    if (values == null) {
      return Collections.emptySet();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String raw : values) {
      if (raw == null) {
        throw new IllegalArgumentException("Null entry in " + fieldName);
      }
      String trimmed = raw.trim();
      if (trimmed.isEmpty()) {
        throw new IllegalArgumentException("Blank entry in " + fieldName);
      }
      if (trimmed.indexOf('*') >= 0) {
        throw new IllegalArgumentException(
            "Wildcard '*' is not supported in " + fieldName + ": " + trimmed);
      }
      out.add(trimmed);
    }
    return Collections.unmodifiableSet(out);
  }
}
