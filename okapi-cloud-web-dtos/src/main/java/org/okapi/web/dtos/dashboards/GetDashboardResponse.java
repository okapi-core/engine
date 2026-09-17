/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards;

import com.google.gson.annotations.SerializedName;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class GetDashboardResponse {
  @NotNull String dashboardId;
  String title;
  String description;
  List<String> tags;
  List<GetDashboardRowResponse> rows;
  List<String> rowOrder;
  Instant created;
  Instant viewed;
  String activeVersion;

  PersonalName createdBy;
  PersonalName lastEditedBy;

  @SerializedName(value = "favorite", alternate = "isFavorite")
  boolean isFavorite;
}
