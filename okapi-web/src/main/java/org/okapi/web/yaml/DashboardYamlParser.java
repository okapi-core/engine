/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class DashboardYamlParser {
  private final ObjectMapper mapper;

  public DashboardYamlParser() {
    this.mapper = new ObjectMapper(new YAMLFactory());
  }

  public DashboardYaml parse(String yaml) {
    try {
      return mapper.readValue(yaml, DashboardYaml.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid YAML: " + e.getMessage(), e);
    }
  }
}
