/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import lombok.*;
import org.springframework.ai.tool.annotation.ToolParam;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class DbFilters {
  @ToolParam(
      description =
"""
Database system (e.g. postgresql, redis, mongodb). Maps to the db.system span attribute.
""",
      required = false)
  String system;

  @ToolParam(
      description =
"""
Database collection or table name. Maps to the db.mongodb.collection or equivalent span attribute.
""",
      required = false)
  String collection;

  @ToolParam(
      description =
"""
Database namespace or schema. Maps to the db.name span attribute.
""",
      required = false)
  String namespace;

  @ToolParam(
      description =
"""
Database operation type (e.g. SELECT, INSERT, find). Maps to the db.operation span attribute.
""",
      required = false)
  String operation;
}
