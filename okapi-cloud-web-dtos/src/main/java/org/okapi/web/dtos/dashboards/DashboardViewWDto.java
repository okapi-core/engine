/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class DashboardViewWDto {
  String id;
  String title;
  String author;
  Instant lastEditedAt;
  Instant viewedAt;
  boolean isFavorite;
  List<String> tags;
}
